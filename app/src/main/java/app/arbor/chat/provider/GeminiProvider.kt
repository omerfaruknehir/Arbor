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
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        withContext(Dispatchers.IO) {
        val body = buildRequestBody(request)
        val url = request.provider.baseUrl.trimEnd('/') + "/models/${request.model.modelId}:streamGenerateContent?alt=sse"
        val httpRequest = Request.Builder().url(url).header("Accept", "text/event-stream")
            .also { builder -> if (request.apiKey.isNotBlank()) builder.header("x-goog-api-key", request.apiKey) }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .also { builder -> request.customHeaders.forEach(builder::header) }.build()
        val state = GeminiStreamState()
        client.newCall(httpRequest).useCancellable { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                parseChunks(line.removePrefix("data:").trim(), state).forEach { emit(it) }
            }
        }
        state.finalChunk()?.let { emit(it) }
        }
    }

    internal fun buildRequestBody(request: ChatRequest): JsonObject {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        return buildJsonObject {
            if (system.isNotBlank()) put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(system)) }) })
            })
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", JsonPrimitive(request.maxOutputTokens))
                if (request.model.supportsThinking) put("thinkingConfig", buildJsonObject {
                    put("includeThoughts", JsonPrimitive(request.thinkingEnabled))
                    val modelId = request.model.modelId.lowercase()
                    if (modelId.contains("gemini-3")) {
                        if (request.thinkingEnabled) put("thinkingLevel", JsonPrimitive(request.thinkingEffort.geminiLevel(modelId)))
                    } else {
                        put("thinkingBudget", JsonPrimitive(if (request.thinkingEnabled) request.thinkingEffort.gemini25Budget else 0))
                    }
                })
            })
            if (request.tools.isNotEmpty() && request.model.supportsTools) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            request.tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", JsonPrimitive(tool.name))
                                    put("description", JsonPrimitive(tool.description))
                                    put("parameters", ProviderJson.parseToJsonElement(tool.parametersJson))
                                })
                            }
                        })
                    })
                })
                put("toolConfig", buildJsonObject {
                    put("functionCallingConfig", buildJsonObject { put("mode", JsonPrimitive("AUTO")) })
                })
            }
            put("contents", buildJsonArray {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                    if (message.role == MessageRole.TOOL && message.nativeToolResults.isNotEmpty()) {
                        add(buildJsonObject {
                            put("role", JsonPrimitive("user"))
                            put("parts", buildJsonArray {
                                message.nativeToolResults.forEach { result ->
                                    add(buildJsonObject {
                                        put("functionResponse", buildJsonObject {
                                            put("name", JsonPrimitive(result.name))
                                            if (result.callId.isNotBlank()) put("id", JsonPrimitive(result.callId))
                                            put("response", buildJsonObject {
                                                put("output", JsonPrimitive(result.output))
                                                if (result.isError) put("isError", JsonPrimitive(true))
                                            })
                                        })
                                    })
                                }
                            })
                        })
                        return@forEach
                    }
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == MessageRole.ASSISTANT) "model" else "user"))
                        if (message.role == MessageRole.ASSISTANT && message.nativeProviderPayloadJson.isNotBlank()) {
                            put("parts", ProviderJson.parseToJsonElement(message.nativeProviderPayloadJson))
                        } else {
                            put("parts", buildJsonArray {
                                if (message.content.isNotBlank() || message.nativeToolCalls.isEmpty()) {
                                    add(buildJsonObject { put("text", JsonPrimitive(message.content)) })
                                }
                                if (message.role == MessageRole.ASSISTANT) {
                                    message.nativeToolCalls.forEach { call ->
                                        add(buildJsonObject {
                                            put("functionCall", buildJsonObject {
                                                put("name", JsonPrimitive(call.name))
                                                if (call.id.isNotBlank()) put("id", JsonPrimitive(call.id))
                                                put("args", ProviderJson.parseToJsonElement(call.argumentsJson))
                                            })
                                        })
                                    }
                                }
                                message.attachments.forEach { attachment ->
                                    val url = if (attachment.mimeType.startsWith("image/")) imageDataUrl(attachment) else fileDataUrl(attachment)
                                    val nativeSupported = if (attachment.mimeType.startsWith("image/")) request.model.supportsVision else request.model.supportsFiles
                                    if (url != null && nativeSupported) add(buildJsonObject {
                                        put("inlineData", buildJsonObject {
                                            put("mimeType", JsonPrimitive(dataUrlMime(url, attachment.mimeType)))
                                            put("data", JsonPrimitive(url.substringAfter("base64,")))
                                        })
                                    }) else add(buildJsonObject { put("text", JsonPrimitive(attachmentContext(attachment))) })
                                }
                            })
                        }
                    })
                }
            })
        }
    }

    internal fun parseChunks(payload: String, state: GeminiStreamState): List<StreamChunk> {
        val root = try {
            ProviderJson.parseToJsonElement(payload).jsonObject
        } catch (error: Throwable) {
            throw ProviderProtocolException("Malformed Gemini stream event", error)
        }
        root.obj("error")?.let { error -> throw ProviderProtocolException(error.string("message") ?: "Gemini returned a stream error") }
        root.obj("promptFeedback")?.string("blockReason")?.let { reason ->
            if (reason.isNotBlank()) throw ProviderProtocolException("Gemini blocked the prompt: $reason")
        }
        val parts = root.array("candidates")?.firstOrNull()?.jsonObject?.obj("content")?.array("parts").orEmpty()
        val chunks = parts.mapNotNull { element ->
            val part = element.jsonObject
            state.addPart(part)
            val functionCall = part.obj("functionCall")
            if (functionCall != null) {
                StreamChunk(toolCallProgress = listOf(state.addCall(part, functionCall)))
            } else {
                val text = part.string("text").orEmpty()
                if (text.isEmpty()) null
                else if (part["thought"]?.jsonPrimitive?.content == "true") StreamChunk(reasoning = text)
                else StreamChunk(text = text)
            }
        }.toMutableList()
        val usage = root.obj("usageMetadata")
        val usageChunk = StreamChunk(
            inputTokens = usage?.long("promptTokenCount"),
            outputTokens = usage?.long("candidatesTokenCount"),
            cachedInputTokens = usage?.long("cachedContentTokenCount"),
            finishReason = root.array("candidates")?.firstOrNull()?.jsonObject?.string("finishReason"),
        )
        if (chunks.isEmpty()) chunks += usageChunk
        else chunks[chunks.lastIndex] = chunks.last().copy(
            inputTokens = usageChunk.inputTokens,
            outputTokens = usageChunk.outputTokens,
            cachedInputTokens = usageChunk.cachedInputTokens,
            finishReason = usageChunk.finishReason,
        )
        return chunks
    }

    private fun app.arbor.chat.data.ThinkingEffort.geminiLevel(modelId: String): String = when (this) {
        app.arbor.chat.data.ThinkingEffort.MINIMAL -> if (modelId.contains("pro")) "low" else "minimal"
        app.arbor.chat.data.ThinkingEffort.LOW -> "low"
        app.arbor.chat.data.ThinkingEffort.MEDIUM -> "medium"
        app.arbor.chat.data.ThinkingEffort.HIGH,
        app.arbor.chat.data.ThinkingEffort.XHIGH,
        app.arbor.chat.data.ThinkingEffort.MAX -> "high"
    }

    private val app.arbor.chat.data.ThinkingEffort.gemini25Budget: Int
        get() = when (this) {
            app.arbor.chat.data.ThinkingEffort.MINIMAL,
            app.arbor.chat.data.ThinkingEffort.LOW -> 1_024
            app.arbor.chat.data.ThinkingEffort.MEDIUM -> 8_192
            app.arbor.chat.data.ThinkingEffort.HIGH,
            app.arbor.chat.data.ThinkingEffort.XHIGH,
            app.arbor.chat.data.ThinkingEffort.MAX -> 24_576
        }

    internal class GeminiStreamState {
        private val parts = mutableListOf<JsonObject>()
        private val calls = linkedMapOf<String, NativeToolCall>()
        private var emitted = false

        fun addPart(part: JsonObject) { parts += part }

        fun addCall(part: JsonObject, functionCall: JsonObject): NativeToolCallProgress {
            val name = functionCall.string("name").orEmpty()
            val arguments = functionCall["args"]?.toString() ?: "{}"
            val id = functionCall.string("id").orEmpty().ifBlank {
                "call_${(name + arguments + part["thoughtSignature"].toString()).hashCode().toUInt().toString(16)}"
            }
            calls.putIfAbsent(id, NativeToolCall(id, name, arguments))
            return NativeToolCallProgress(
                index = calls.keys.indexOf(id),
                id = id,
                name = name,
                argumentsJson = arguments,
                complete = true,
            )
        }

        fun finalChunk(): StreamChunk? {
            if (emitted || calls.isEmpty()) return null
            emitted = true
            return StreamChunk(
                toolCalls = calls.values.toList(),
                nativeProviderPayloadJson = JsonArray(parts).toString(),
            )
        }
    }
}
