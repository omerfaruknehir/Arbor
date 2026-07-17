package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class OpenAiCompatibleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) = withContext(Dispatchers.IO) {
        val bodyJson = buildRequestBody(request)
        val isDeepSeek = request.provider.id == "deepseek"
        val root = request.provider.baseUrl.trimEnd('/')
        val endpoint = if (isDeepSeek && request.continuation) "$root/beta/chat/completions" else "$root/chat/completions"
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
        if (request.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${request.apiKey}")
        request.customHeaders.forEach(builder::header)

        val calls = linkedMapOf<Int, ToolCallAccumulator>()
        client.newCall(builder.build()).useCancellable { response ->
            if (!response.isSuccessful) {
                val error = response.body?.readErrorSnippet().orEmpty()
                throw ProviderHttpException(response.code, "${response.code} ${response.message}: $error")
            }
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                parseChunk(payload, calls)?.let { emit(it) }
            }
        }
        if (calls.isNotEmpty()) {
            emit(StreamChunk(toolCalls = calls.toSortedMap().values.map { it.complete() }))
        }
    }

    internal fun buildRequestBody(request: ChatRequest): JsonObject {
        val isDeepSeek = request.provider.id == "deepseek"
        return buildJsonObject {
            put("model", JsonPrimitive(request.model.modelId))
            put("stream", JsonPrimitive(true))
            put("max_tokens", JsonPrimitive(request.maxOutputTokens))
            if (request.provider.id in setOf("openai", "deepseek", "openrouter", "xai")) {
                put("stream_options", buildJsonObject { put("include_usage", JsonPrimitive(true)) })
            }
            if (request.tools.isNotEmpty() && request.model.supportsTools) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("function"))
                            put("function", buildJsonObject {
                                put("name", JsonPrimitive(tool.name))
                                put("description", JsonPrimitive(tool.description))
                                put("parameters", ProviderJson.parseToJsonElement(tool.parametersJson))
                            })
                        })
                    }
                })
                // Arbor executes one side effect at a time so interruption and replay remain deterministic.
                put("parallel_tool_calls", JsonPrimitive(false))
            }
            if (request.model.supportsThinking) {
                if (isDeepSeek) {
                    put("thinking", buildJsonObject { put("type", JsonPrimitive(if (request.thinkingEnabled) "enabled" else "disabled")) })
                }
                if (request.thinkingEnabled) put("reasoning_effort", JsonPrimitive(request.thinkingEffort.apiValue))
            }
            put("messages", buildJsonArray {
                request.messages.forEachIndexed { index, message ->
                    if (message.role == MessageRole.TOOL && message.nativeToolResults.isNotEmpty()) {
                        message.nativeToolResults.forEach { result ->
                            add(buildJsonObject {
                                put("role", JsonPrimitive("tool"))
                                put("tool_call_id", JsonPrimitive(result.callId))
                                put("content", JsonPrimitive(result.output))
                            })
                        }
                        return@forEachIndexed
                    }
                    add(buildJsonObject {
                        put("role", JsonPrimitive(message.role.name.lowercase()))
                        val imageParts = if (request.model.supportsVision) message.attachments.mapNotNull { attachment ->
                            imageDataUrl(attachment)?.let { attachment.id to it }
                        } else emptyList()
                        val fileParts = if (request.model.supportsFiles) message.attachments.filterNot { it.mimeType.startsWith("image/") }.mapNotNull { attachment ->
                            fileDataUrl(attachment)?.let { attachment to it }
                        } else emptyList()
                        if (message.role == MessageRole.USER && (imageParts.isNotEmpty() || fileParts.isNotEmpty())) {
                            put("content", buildJsonArray {
                                val nativeIds = imageParts.mapTo(HashSet()) { it.first } + fileParts.map { it.first.id }
                                add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(combinedText(message, nativeIds))) })
                                imageParts.forEach { (_, url) ->
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("image_url"))
                                        put("image_url", buildJsonObject { put("url", JsonPrimitive(url)) })
                                    })
                                }
                                fileParts.forEach { (attachment, url) ->
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("file"))
                                        put("file", buildJsonObject {
                                            put("filename", JsonPrimitive(attachment.displayName))
                                            put("file_data", JsonPrimitive(url))
                                        })
                                    })
                                }
                            })
                        } else if (message.role == MessageRole.ASSISTANT && message.nativeToolCalls.isNotEmpty() && message.content.isBlank()) {
                            put("content", JsonNull)
                        } else {
                            put("content", JsonPrimitive(combinedText(message, emptySet())))
                        }
                        if (message.role == MessageRole.ASSISTANT && message.nativeToolCalls.isNotEmpty()) {
                            put("tool_calls", buildJsonArray {
                                message.nativeToolCalls.forEach { call ->
                                    add(buildJsonObject {
                                        put("id", JsonPrimitive(call.id))
                                        put("type", JsonPrimitive("function"))
                                        put("function", buildJsonObject {
                                            put("name", JsonPrimitive(call.name))
                                            put("arguments", JsonPrimitive(call.argumentsJson))
                                        })
                                    })
                                }
                            })
                        }
                        if (isDeepSeek && message.role == MessageRole.ASSISTANT && message.reasoning.isNotBlank()) {
                            put("reasoning_content", JsonPrimitive(message.reasoning))
                        }
                        if (isDeepSeek && request.continuation && index == request.messages.lastIndex && message.role == MessageRole.ASSISTANT) {
                            put("prefix", JsonPrimitive(true))
                        }
                    })
                }
            })
        }
    }

    internal fun parseChunk(payload: String, calls: MutableMap<Int, ToolCallAccumulator>): StreamChunk? {
        val root = try {
            ProviderJson.parseToJsonElement(payload).jsonObject
        } catch (error: Throwable) {
            throw ProviderProtocolException("Malformed OpenAI-compatible stream event", error)
        }
        root.obj("error")?.let { error -> throw ProviderProtocolException(error.string("message") ?: "Provider returned a stream error") }
        val choice = root.array("choices")?.firstOrNull()?.jsonObject
        val delta = choice?.obj("delta")
        delta?.array("tool_calls")?.forEach { element ->
            val item = element.jsonObject
            val index = item.long("index")?.toInt() ?: calls.size
            val accumulator = calls.getOrPut(index) { ToolCallAccumulator() }
            item.string("id")?.let { accumulator.id = it }
            item.obj("function")?.let { function ->
                function.string("name")?.let { accumulator.name += it }
                function.string("arguments")?.let { accumulator.arguments.append(it) }
            }
        }
        val usage = root.obj("usage")
        val details = usage?.obj("prompt_tokens_details")
        if (choice == null && usage == null) return null
        return StreamChunk(
            text = delta?.string("content").orEmpty(),
            reasoning = delta?.string("reasoning_content").orEmpty(),
            inputTokens = usage?.long("prompt_tokens"),
            outputTokens = usage?.long("completion_tokens"),
            cachedInputTokens = details?.long("cached_tokens"),
            finishReason = choice?.string("finish_reason"),
        )
    }

    private fun combinedText(message: InputMessage, nativeAttachmentIds: Set<String>): String {
        if (message.attachments.isEmpty()) return message.content
        val context = message.attachments.mapNotNull { attachment ->
            if (attachment.id in nativeAttachmentIds) null else attachmentContext(attachment)
        }
        return (listOf(message.content) + context).filter(String::isNotBlank).joinToString("\n\n")
    }

    internal class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun complete(): NativeToolCall {
            val stableId = id.ifBlank { "call_${name.hashCode().toUInt().toString(16)}" }
            return NativeToolCall(stableId, name, arguments.toString().ifBlank { "{}" })
        }
    }

private val app.arbor.chat.data.ThinkingEffort.apiValue: String
    get() = when (this) {
        app.arbor.chat.data.ThinkingEffort.MINIMAL -> "minimal"
        app.arbor.chat.data.ThinkingEffort.LOW -> "low"
        app.arbor.chat.data.ThinkingEffort.MEDIUM -> "medium"
        app.arbor.chat.data.ThinkingEffort.HIGH -> "high"
    }

}
