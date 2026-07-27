package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.ThinkingEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Direct Codex Responses transport authenticated by a user's ChatGPT OAuth session. */
class OpenAiOAuthProvider(
    private val oauth: OpenAiOAuthManager? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) = withContext(Dispatchers.IO) {
        val manager = requireNotNull(oauth) { "OpenAI OAuth manager is unavailable" }
        val modelInfo = runCatching { manager.modelInfo(request.provider.id, request.model.modelId) }.getOrNull()
        val body = buildRequestBody(request, modelInfo)
        var session = manager.validSession(request.provider.id)
        var refreshed = false

        while (true) {
            val endpoint = request.provider.baseUrl.trimEnd('/') + "/responses"
            val builder = Request.Builder()
                .url(endpoint)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Arbor/0.19.3 openai-oauth-android")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
            request.customHeaders.forEach(builder::header)
            // OAuth transport headers are authoritative and must not be overridden by provider metadata.
            builder.header("Authorization", "Bearer ${session.accessToken}")
            builder.header("chatgpt-account-id", session.accountId)
            if (session.isFedRamp) builder.header("X-OpenAI-Fedramp", "true") else builder.removeHeader("X-OpenAI-Fedramp")
            if (modelInfo?.useResponsesLite == true) builder.header(RESPONSES_LITE_HEADER, "true") else builder.removeHeader(RESPONSES_LITE_HEADER)

            val state = OpenAiOAuthStreamState()
            var retryWithFreshToken = false
            client.newCall(builder.build()).useCancellable { response ->
                if (response.code == 401 && !refreshed) {
                    retryWithFreshToken = true
                    return@useCancellable
                }
                if (!response.isSuccessful) {
                    val error = response.body?.readErrorSnippet().orEmpty()
                    throw ProviderHttpException(response.code, "${response.code} ${response.message}: $error")
                }
                val source = response.body?.source() ?: throw ProviderProtocolException("ChatGPT returned an empty response")
                while (!source.exhausted()) {
                    coroutineContext.ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isBlank() || payload == "[DONE]") continue
                    val chunk = state.accept(payload)
                    if (chunk != null) emit(chunk)
                }
            }
            if (retryWithFreshToken) {
                refreshed = true
                session = manager.validSession(request.provider.id, forceRefresh = true)
                continue
            }
            state.finalChunk()?.let { emit(it) }
            break
        }
    }

    internal fun buildRequestBody(
        request: ChatRequest,
        modelInfo: OpenAiOAuthModelInfo? = null,
    ): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(request.model.modelId))
        put("stream", JsonPrimitive(true))
        put("store", JsonPrimitive(false))
        put("instructions", JsonPrimitive(""))
        put("include", buildJsonArray { add(JsonPrimitive("reasoning.encrypted_content")) })
        put("parallel_tool_calls", JsonPrimitive(false))

        val reasoning = buildJsonObject {
            put("effort", JsonPrimitive(if (request.thinkingEnabled) request.thinkingEffort.oauthValue else "minimal"))
            if (request.thinkingEnabled) put("summary", JsonPrimitive("auto"))
            if (modelInfo?.useResponsesLite == true) put("context", JsonPrimitive("all_turns"))
        }
        put("reasoning", reasoning)

        val tools = buildJsonArray {
            if (request.model.supportsTools) request.tools.forEach { tool ->
                add(buildJsonObject {
                    put("type", JsonPrimitive("function"))
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("parameters", ProviderJson.parseToJsonElement(tool.parametersJson))
                    put("strict", JsonPrimitive(false))
                })
            }
        }

        val input = buildInput(request).toMutableList()
        if (tools.isNotEmpty()) {
            if (modelInfo?.useResponsesLite == true) {
                input.add(0, buildJsonObject {
                    put("type", JsonPrimitive("additional_tools"))
                    put("role", JsonPrimitive("developer"))
                    put("tools", tools)
                })
            } else {
                put("tools", tools)
            }
        }
        put("input", JsonArray(input))
    }

    private fun buildInput(request: ChatRequest): List<JsonElement> = buildList {
        request.messages.forEach { message ->
            if (message.role == MessageRole.ASSISTANT && message.nativeProviderPayloadJson.isNotBlank()) {
                val payload = runCatching { ProviderJson.parseToJsonElement(message.nativeProviderPayloadJson) }.getOrNull()
                when (payload) {
                    is JsonArray -> addAll(payload)
                    is JsonObject -> add(payload)
                    else -> Unit
                }
                return@forEach
            }

            if (message.role == MessageRole.TOOL && message.nativeToolResults.isNotEmpty()) {
                message.nativeToolResults.forEach { result ->
                    add(buildJsonObject {
                        put("type", JsonPrimitive("function_call_output"))
                        put("call_id", JsonPrimitive(result.callId))
                        put("output", JsonPrimitive(result.output))
                    })
                }
                return@forEach
            }

            when (message.role) {
                MessageRole.SYSTEM -> add(buildJsonObject {
                    put("role", JsonPrimitive("developer"))
                    put("content", JsonPrimitive(message.content))
                })
                MessageRole.USER -> add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        val nativeIds = mutableSetOf<String>()
                        var nativePartCount = 0
                        message.attachments.forEach { attachment ->
                            val image = if (request.model.supportsVision) imageDataUrl(attachment) else null
                            val file = if (!attachment.mimeType.startsWith("image/") && request.model.supportsFiles) fileDataUrl(attachment) else null
                            when {
                                image != null -> {
                                    nativeIds += attachment.id
                                    nativePartCount++
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("input_image"))
                                        put("image_url", JsonPrimitive(image))
                                    })
                                }
                                file != null -> {
                                    nativeIds += attachment.id
                                    nativePartCount++
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("input_file"))
                                        put("filename", JsonPrimitive(attachment.displayName))
                                        put("file_data", JsonPrimitive(file))
                                    })
                                }
                            }
                        }
                        val text = (listOf(message.content) + message.attachments
                            .filterNot { it.id in nativeIds }
                            .map(::attachmentContext))
                            .filter(String::isNotBlank)
                            .joinToString("\n\n")
                        if (text.isNotBlank() || nativePartCount == 0) add(buildJsonObject {
                            put("type", JsonPrimitive("input_text"))
                            put("text", JsonPrimitive(text))
                        })
                    })
                })
                MessageRole.ASSISTANT -> {
                    if (message.content.isNotBlank() || message.nativeToolCalls.isEmpty()) {
                        add(buildJsonObject {
                            put("role", JsonPrimitive("assistant"))
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", JsonPrimitive("output_text"))
                                    put("text", JsonPrimitive(message.content))
                                })
                            })
                        })
                    }
                    message.nativeToolCalls.forEach { call ->
                        add(buildJsonObject {
                            put("type", JsonPrimitive("function_call"))
                            put("call_id", JsonPrimitive(call.id))
                            put("name", JsonPrimitive(call.name))
                            put("arguments", JsonPrimitive(call.argumentsJson))
                        })
                    }
                }
                MessageRole.TOOL -> Unit
            }
        }
    }

    internal class OpenAiOAuthStreamState {
        private val outputItems = sortedMapOf<Int, JsonObject>()
        private val calls = sortedMapOf<Int, CallAccumulator>()
        private var inputTokens: Long? = null
        private var outputTokens: Long? = null
        private var cachedTokens: Long? = null
        private var finishReason: String? = null
        private var emittedFinal = false

        fun accept(payload: String): StreamChunk? {
            val root = runCatching { ProviderJson.parseToJsonElement(payload).jsonObject }
                .getOrElse { throw ProviderProtocolException("ChatGPT returned invalid streaming JSON", it) }
            return when (root.string("type")) {
                "response.output_text.delta" -> StreamChunk(text = root.string("delta").orEmpty())
                "response.reasoning_summary_text.delta",
                "response.reasoning_text.delta" -> StreamChunk(reasoning = root.string("delta").orEmpty())
                "response.output_item.added" -> {
                    val index = root.long("output_index")?.toInt() ?: outputItems.size
                    val item = root.obj("item") ?: return null
                    outputItems[index] = item
                    if (item.string("type") == "function_call") {
                        val call = calls.getOrPut(index) { CallAccumulator() }
                        call.read(item)
                        StreamChunk(toolCallProgress = listOf(call.progress(index)))
                    } else null
                }
                "response.function_call_arguments.delta" -> {
                    val index = root.long("output_index")?.toInt() ?: calls.size
                    val call = calls.getOrPut(index) { CallAccumulator() }
                    call.arguments.append(root.string("delta").orEmpty())
                    StreamChunk(toolCallProgress = listOf(call.progress(index)))
                }
                "response.function_call_arguments.done" -> {
                    val index = root.long("output_index")?.toInt() ?: calls.size
                    val call = calls.getOrPut(index) { CallAccumulator() }
                    root.string("arguments")?.let { call.replaceArguments(it) }
                    StreamChunk(toolCallProgress = listOf(call.progress(index, complete = true)))
                }
                "response.output_item.done" -> {
                    val index = root.long("output_index")?.toInt() ?: outputItems.size
                    val item = root.obj("item") ?: return null
                    outputItems[index] = item
                    if (item.string("type") == "function_call") {
                        val call = calls.getOrPut(index) { CallAccumulator() }
                        call.read(item)
                        StreamChunk(toolCallProgress = listOf(call.progress(index, complete = true)))
                    } else null
                }
                "response.completed", "response.incomplete" -> {
                    readCompleted(root.obj("response"))
                    StreamChunk(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        cachedInputTokens = cachedTokens,
                        finishReason = finishReason,
                    )
                }
                "response.failed" -> {
                    val response = root.obj("response")
                    val message = response?.obj("error")?.string("message")
                        ?: response?.string("status_details")
                        ?: "ChatGPT response failed"
                    throw ProviderProtocolException(message)
                }
                "error" -> throw ProviderProtocolException(
                    root.obj("error")?.string("message") ?: root.string("message") ?: "ChatGPT streaming error",
                )
                else -> null
            }
        }

        private fun readCompleted(response: JsonObject?) {
            if (response == null) return
            response.array("output")?.forEachIndexed { index, element ->
                val item = element as? JsonObject ?: return@forEachIndexed
                outputItems[index] = item
                if (item.string("type") == "function_call") calls.getOrPut(index) { CallAccumulator() }.read(item)
            }
            val usage = response.obj("usage")
            inputTokens = usage?.long("input_tokens") ?: inputTokens
            outputTokens = usage?.long("output_tokens") ?: outputTokens
            cachedTokens = usage?.obj("input_tokens_details")?.long("cached_tokens") ?: cachedTokens
            finishReason = when (response.string("status")) {
                "completed" -> "stop"
                "incomplete" -> response.obj("incomplete_details")?.string("reason") ?: "incomplete"
                "failed" -> "error"
                else -> response.string("status") ?: finishReason
            }
        }

        fun finalChunk(): StreamChunk? {
            if (emittedFinal || (calls.isEmpty() && outputItems.isEmpty())) return null
            emittedFinal = true
            return StreamChunk(
                toolCallProgress = calls.map { (index, call) -> call.progress(index, complete = true) },
                toolCalls = calls.values.map(CallAccumulator::complete),
                nativeProviderPayloadJson = JsonArray(outputItems.values.toList()).toString(),
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cachedInputTokens = cachedTokens,
                finishReason = finishReason,
            )
        }

        internal class CallAccumulator {
            var itemId = ""
            var callId = ""
            var name = ""
            val arguments = StringBuilder()

            fun read(item: JsonObject) {
                item.string("id")?.let { itemId = it }
                item.string("call_id")?.let { callId = it }
                item.string("name")?.let { name = it }
                item.string("arguments")?.let(::replaceArguments)
            }

            fun replaceArguments(value: String) {
                arguments.setLength(0)
                arguments.append(value)
            }

            fun progress(index: Int, complete: Boolean = false) = NativeToolCallProgress(
                index = index,
                id = callId.ifBlank { itemId },
                name = name,
                argumentsJson = arguments.toString(),
                complete = complete,
            )

            fun complete() = NativeToolCall(
                id = callId.ifBlank { itemId.ifBlank { "call_${name.hashCode().toUInt().toString(16)}" } },
                name = name,
                argumentsJson = arguments.toString().ifBlank { "{}" },
            )
        }
    }

    private val ThinkingEffort.oauthValue: String
        get() = when (this) {
            ThinkingEffort.MINIMAL -> "minimal"
            ThinkingEffort.LOW -> "low"
            ThinkingEffort.MEDIUM -> "medium"
            ThinkingEffort.HIGH -> "high"
            ThinkingEffort.XHIGH, ThinkingEffort.MAX -> "xhigh"
        }

    private companion object {
        const val RESPONSES_LITE_HEADER = "x-openai-internal-codex-responses-lite"
    }
}
