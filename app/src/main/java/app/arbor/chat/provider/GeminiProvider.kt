package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class GeminiProvider(
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(120, TimeUnit.SECONDS).build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) = withContext(Dispatchers.IO) {
        val system = request.messages.filter { it.role == MessageRole.SYSTEM }.joinToString("\n\n") { it.content }
        val body = buildJsonObject {
            if (system.isNotBlank()) put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", JsonPrimitive(system)) }) })
            })
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", JsonPrimitive(request.maxOutputTokens))
                if (request.model.supportsThinking) put("thinkingConfig", buildJsonObject {
                    put("includeThoughts", JsonPrimitive(request.thinkingEnabled))
                })
            })
            put("contents", buildJsonArray {
                request.messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                    add(buildJsonObject {
                        put("role", JsonPrimitive(if (message.role == MessageRole.ASSISTANT) "model" else "user"))
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", JsonPrimitive(message.content)) })
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
                    })
                }
            })
        }
        val url = request.provider.baseUrl.trimEnd('/') + "/models/${request.model.modelId}:streamGenerateContent?alt=sse"
        val httpRequest = Request.Builder().url(url).header("Accept", "text/event-stream")
            .also { builder -> if (request.apiKey.isNotBlank()) builder.header("x-goog-api-key", request.apiKey) }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .also { builder -> request.customHeaders.forEach(builder::header) }.build()
        client.newCall(httpRequest).useCancellable { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                parseChunks(line.removePrefix("data:").trim()).forEach { emit(it) }
            }
        }
    }

    private fun parseChunks(payload: String): List<StreamChunk> {
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
            val text = part.string("text").orEmpty()
            if (text.isEmpty()) null
            else if (part["thought"]?.jsonPrimitive?.content == "true") StreamChunk(reasoning = text)
            else StreamChunk(text = text)
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
}
