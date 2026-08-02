package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
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

class AnthropicProvider(
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        withContext(Dispatchers.IO) {
        val body = buildRequestBody(request)
        val httpRequest = Request.Builder()
            .url(request.provider.baseUrl.trimEnd('/') + "/messages")
            .header("x-api-key", request.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .also { builder -> request.customHeaders.forEach(builder::header) }
            .build()
        val state = AnthropicStreamState()
        client.newCall(httpRequest).useCancellable { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                parseChunk(payload, state)?.let { emit(it) }
            }
        }
        state.finalChunk()?.let { emit(it) }
        }
    }

    internal fun buildRequestBody(request: ChatRequest): JsonObject {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        return buildJsonObject {
            put("model", JsonPrimitive(request.model.modelId))
            put("max_tokens", JsonPrimitive(request.maxOutputTokens))
            put("stream", JsonPrimitive(true))
            if (system.isNotBlank()) put("system", JsonPrimitive(system))
            if (request.model.supportsThinking) {
                val adaptive = supportsAdaptiveThinking(request.model.modelId)
                when {
                    request.thinkingEnabled && adaptive -> {
                        put("thinking", buildJsonObject { put("type", JsonPrimitive("adaptive")) })
                        put("output_config", buildJsonObject {
                            put("effort", JsonPrimitive(request.thinkingEffort.anthropicValue))
                        })
                    }
                    request.thinkingEnabled && request.maxOutputTokens > 1_024 -> put("thinking", buildJsonObject {
                        put("type", JsonPrimitive("enabled"))
                        put("budget_tokens", JsonPrimitive(legacyThinkingBudget(request.maxOutputTokens, request.thinkingEffort)))
                    })
                    !request.thinkingEnabled && adaptive -> put("thinking", buildJsonObject {
                        put("type", JsonPrimitive("disabled"))
                    })
                }
            }
            if (request.tools.isNotEmpty() && request.model.supportsTools) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", JsonPrimitive(tool.name))
                            put("description", JsonPrimitive(tool.description))
                            put("input_schema", ProviderJson.parseToJsonElement(tool.parametersJson))
                        })
                    }
                })
                put("tool_choice", buildJsonObject {
                    put("type", JsonPrimitive("auto"))
                    put("disable_parallel_tool_use", JsonPrimitive(true))
                })
            }
            put("messages", buildJsonArray {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                    if (message.role == MessageRole.TOOL && message.nativeToolResults.isNotEmpty()) {
                        add(buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("content", buildJsonArray {
                                message.nativeToolResults.forEach { result ->
                                    add(buildJsonObject {
                                        put("type", JsonPrimitive("tool_result"))
                                        put("tool_use_id", JsonPrimitive(result.callId))
                                        put("content", JsonPrimitive(result.output))
                                        if (result.isError) put("is_error", JsonPrimitive(true))
                                    })
                                }
                            })
                        })
                        return@forEach
                    }
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == MessageRole.ASSISTANT) "assistant" else "user"))
                        if (message.role == MessageRole.ASSISTANT && message.nativeProviderPayloadJson.isNotBlank()) {
                            put("content", ProviderJson.parseToJsonElement(message.nativeProviderPayloadJson))
                        } else {
                            put("content", buildJsonArray {
                                if (message.content.isNotBlank() || message.nativeToolCalls.isEmpty()) {
                                    add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(message.content)) })
                                }
                                if (message.role == MessageRole.ASSISTANT) {
                                    message.nativeToolCalls.forEach { call ->
                                        add(buildJsonObject {
                                            put("type", JsonPrimitive("tool_use"))
                                            put("id", JsonPrimitive(call.id))
                                            put("name", JsonPrimitive(call.name))
                                            put("input", ProviderJson.parseToJsonElement(call.argumentsJson))
                                        })
                                    }
                                }
                                message.attachments.forEach { attachment ->
                                    val url = imageDataUrl(attachment)
                                    if (url != null && request.model.supportsVision) {
                                        val base64 = url.substringAfter("base64,")
                                        add(buildJsonObject {
                                            put("type", JsonPrimitive("image"))
                                            put("source", buildJsonObject {
                                                put("type", JsonPrimitive("base64"))
                                                put("media_type", JsonPrimitive(dataUrlMime(url, attachment.mimeType)))
                                                put("data", JsonPrimitive(base64))
                                            })
                                        })
                                    } else {
                                        val fileUrl = fileDataUrl(attachment)
                                        if (fileUrl != null && request.model.supportsFiles && attachment.mimeType == "application/pdf") {
                                            add(buildJsonObject {
                                                put("type", JsonPrimitive("document"))
                                                put("source", buildJsonObject {
                                                    put("type", JsonPrimitive("base64"))
                                                    put("media_type", JsonPrimitive(attachment.mimeType))
                                                    put("data", JsonPrimitive(fileUrl.substringAfter("base64,")))
                                                })
                                            })
                                        } else add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(attachmentContext(attachment))) })
                                    }
                                }
                            })
                        }
                    })
                }
            })
        }
    }

    internal fun parseChunk(payload: String, state: AnthropicStreamState): StreamChunk? {
        val root = try {
            ProviderJson.parseToJsonElement(payload).jsonObject
        } catch (error: Throwable) {
            throw ProviderProtocolException("Malformed Anthropic stream event", error)
        }
        val type = root.string("type")
        if (type == "error") throw ProviderProtocolException(root.obj("error")?.string("message") ?: "Anthropic returned a stream error")
        val usage = (root.obj("message")?.obj("usage")) ?: root.obj("usage")
        return when (type) {
            "content_block_start" -> {
                val index = root.long("index")?.toInt() ?: state.nextIndex()
                root.obj("content_block")?.let { state.start(index, it) }
                    ?.let { StreamChunk(toolCallProgress = listOf(it)) }
            }
            "content_block_delta" -> {
                val index = root.long("index")?.toInt() ?: return null
                val delta = root.obj("delta") ?: return null
                when (delta.string("type")) {
                    "thinking_delta" -> delta.string("thinking").orEmpty().also { state.appendThinking(index, it) }
                        .takeIf(String::isNotEmpty)?.let { StreamChunk(reasoning = it) }
                    "signature_delta" -> {
                        state.appendSignature(index, delta.string("signature").orEmpty())
                        null
                    }
                    "input_json_delta" -> state.appendInput(index, delta.string("partial_json").orEmpty())
                        ?.let { StreamChunk(toolCallProgress = listOf(it)) }
                    else -> delta.string("text").orEmpty().also { state.appendText(index, it) }
                        .takeIf(String::isNotEmpty)?.let { StreamChunk(text = it) }
                }
            }
            "content_block_stop" -> root.long("index")?.toInt()?.let(state::stop)
                ?.let { StreamChunk(toolCallProgress = listOf(it)) }
            "message_start", "message_delta" -> StreamChunk(
                inputTokens = usage?.long("input_tokens"),
                outputTokens = usage?.long("output_tokens"),
                cachedInputTokens = usage?.long("cache_read_input_tokens"),
                finishReason = root.obj("delta")?.string("stop_reason"),
            )
            else -> null
        }
    }

    private fun supportsAdaptiveThinking(modelId: String): Boolean {
        val id = modelId.lowercase()
        return id.contains("sonnet-5") || id.contains("sonnet-4-6") ||
            id.contains("opus-4-6") || id.contains("opus-4-7") || id.contains("opus-4-8") ||
            id.contains("fable") || id.contains("mythos")
    }

    private val app.arbor.chat.data.ThinkingEffort.anthropicValue: String
        get() = when (this) {
            app.arbor.chat.data.ThinkingEffort.MINIMAL,
            app.arbor.chat.data.ThinkingEffort.LOW -> "low"
            app.arbor.chat.data.ThinkingEffort.MEDIUM -> "medium"
            app.arbor.chat.data.ThinkingEffort.HIGH -> "high"
            app.arbor.chat.data.ThinkingEffort.XHIGH -> "xhigh"
            app.arbor.chat.data.ThinkingEffort.MAX -> "max"
        }

    private fun legacyThinkingBudget(maxOutputTokens: Int, effort: app.arbor.chat.data.ThinkingEffort): Int {
        val target = when (effort) {
            app.arbor.chat.data.ThinkingEffort.MINIMAL -> 1_024
            app.arbor.chat.data.ThinkingEffort.LOW -> 4_096
            app.arbor.chat.data.ThinkingEffort.MEDIUM -> 8_192
            app.arbor.chat.data.ThinkingEffort.HIGH -> 16_000
            app.arbor.chat.data.ThinkingEffort.XHIGH -> 24_000
            app.arbor.chat.data.ThinkingEffort.MAX -> 32_000
        }
        return target.coerceAtMost(maxOutputTokens - 1).coerceAtLeast(1_024)
    }

    internal class AnthropicStreamState {
        private val active = linkedMapOf<Int, BlockAccumulator>()
        private val completed = sortedMapOf<Int, JsonObject>()
        private val calls = mutableListOf<NativeToolCall>()
        private var emitted = false

        fun nextIndex(): Int = ((active.keys + completed.keys).maxOrNull() ?: -1) + 1
        fun start(index: Int, block: JsonObject): NativeToolCallProgress? {
            val accumulator = BlockAccumulator(block)
            active[index] = accumulator
            return accumulator.progress(index)
        }
        fun appendText(index: Int, value: String) { active[index]?.text?.append(value) }
        fun appendThinking(index: Int, value: String) { active[index]?.thinking?.append(value) }
        fun appendSignature(index: Int, value: String) { active[index]?.signature?.append(value) }
        fun appendInput(index: Int, value: String): NativeToolCallProgress? {
            val block = active[index] ?: return null
            block.input.append(value)
            return block.progress(index)
        }

        fun stop(index: Int): NativeToolCallProgress? {
            val block = active.remove(index) ?: return null
            val final = block.complete()
            completed[index] = final
            if (final.string("type") == "tool_use") {
                calls += NativeToolCall(
                    id = final.string("id").orEmpty(),
                    name = final.string("name").orEmpty(),
                    argumentsJson = final["input"]?.toString() ?: "{}",
                )
                return block.progress(index, complete = true)
            }
            return null
        }

        fun finalChunk(): StreamChunk? {
            if (emitted) return null
            active.keys.toList().forEach(::stop)
            if (calls.isEmpty()) return null
            emitted = true
            return StreamChunk(
                toolCalls = calls.toList(),
                nativeProviderPayloadJson = JsonArray(completed.values.toList()).toString(),
            )
        }
    }

    internal class BlockAccumulator(private val base: JsonObject) {
        val text = StringBuilder(base.string("text").orEmpty())
        val thinking = StringBuilder(base.string("thinking").orEmpty())
        val signature = StringBuilder(base.string("signature").orEmpty())
        val input = StringBuilder()

        fun progress(index: Int, complete: Boolean = false): NativeToolCallProgress? {
            if (base.string("type") != "tool_use") return null
            return NativeToolCallProgress(
                index = index,
                id = base.string("id").orEmpty(),
                name = base.string("name").orEmpty(),
                argumentsJson = input.toString().ifBlank { base["input"]?.toString().orEmpty() },
                complete = complete,
            )
        }

        fun complete(): JsonObject = when (base.string("type")) {
            "text" -> JsonObject(base + ("text" to JsonPrimitive(text.toString())))
            "thinking" -> JsonObject(base + mapOf(
                "thinking" to JsonPrimitive(thinking.toString()),
                "signature" to JsonPrimitive(signature.toString()),
            ))
            "tool_use" -> JsonObject(base + ("input" to runCatching {
                ProviderJson.parseToJsonElement(input.toString().ifBlank { base["input"]?.toString() ?: "{}" })
            }.getOrElse { JsonObject(emptyMap()) }))
            else -> base
        }
    }
}
