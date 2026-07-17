package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) = withContext(Dispatchers.IO) {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        val body = buildJsonObject {
            put("model", JsonPrimitive(request.model.modelId))
            put("max_tokens", JsonPrimitive(request.maxOutputTokens))
            put("stream", JsonPrimitive(true))
            if (system.isNotBlank()) put("system", JsonPrimitive(system))
            if (request.thinkingEnabled && request.model.supportsThinking) {
                put("thinking", buildJsonObject {
                    put("type", JsonPrimitive("enabled"))
                    put("budget_tokens", JsonPrimitive(minOf(request.maxOutputTokens / 2, 16_000)))
                })
            }
            put("messages", buildJsonArray {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == MessageRole.ASSISTANT) "assistant" else "user"))
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive(message.content)) })
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
                    })
                }
            })
        }
        val httpRequest = Request.Builder()
            .url(request.provider.baseUrl.trimEnd('/') + "/messages")
            .header("x-api-key", request.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .also { builder -> request.customHeaders.forEach(builder::header) }
            .build()
        client.newCall(httpRequest).useCancellable { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                parseChunk(payload)?.let { emit(it) }
            }
        }
    }

    private fun parseChunk(payload: String): StreamChunk? {
        val root = try {
            ProviderJson.parseToJsonElement(payload).jsonObject
        } catch (error: Throwable) {
            throw ProviderProtocolException("Malformed Anthropic stream event", error)
        }
        val type = root.string("type")
        if (type == "error") throw ProviderProtocolException(root.obj("error")?.string("message") ?: "Anthropic returned a stream error")
        val delta = root.obj("delta")
        val usage = (root.obj("message")?.obj("usage")) ?: root.obj("usage")
        return when (type) {
            "content_block_delta" -> when (delta?.string("type")) {
                "thinking_delta" -> StreamChunk(reasoning = delta.string("thinking").orEmpty())
                else -> StreamChunk(text = delta?.string("text").orEmpty())
            }
            "message_start", "message_delta" -> StreamChunk(
                inputTokens = usage?.long("input_tokens"),
                outputTokens = usage?.long("output_tokens"),
                cachedInputTokens = usage?.long("cache_read_input_tokens"),
                finishReason = delta?.string("stop_reason"),
            )
            else -> null
        }
    }
}
