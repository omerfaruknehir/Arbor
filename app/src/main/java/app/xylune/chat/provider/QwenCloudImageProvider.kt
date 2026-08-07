package app.xylune.chat.provider

import app.xylune.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Alibaba Cloud exposes Qwen-Image through DashScope's native multimodal API,
 * not through the OpenAI-compatible /images endpoint. Keep this adapter in
 * front of the generic OpenAI-compatible provider so the rest of Model Studio
 * continues to use the normal chat/Responses transports.
 */
internal class QwenCloudImageProvider(
    private val delegate: ChatProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        if (!ModelRequestPolicy.isQwenCloudImageModel(request.provider, request.model)) {
            delegate.stream(request, emit)
            return
        }
        generateImage(request, emit)
    }

    private suspend fun generateImage(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) =
        withContext(Dispatchers.IO) {
            val prompt = imagePrompt(request)
            require(prompt.isNotBlank()) { "Enter a prompt for image generation" }
            val latestUser = request.messages.lastOrNull { it.role == MessageRole.USER }
            require(latestUser?.attachments.orEmpty().none { it.mimeType.startsWith("image/") }) {
                "Qwen-Image supports image editing, but Xylune currently enables text-to-image generation only. Remove image attachments first."
            }

            val httpRequest = Request.Builder()
                .url(endpointFor(request))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(buildRequestBody(request, prompt).toString().toRequestBody(JSON_MEDIA_TYPE))
                .apply {
                    if (request.apiKey.isNotBlank()) header("Authorization", "Bearer ${request.apiKey}")
                    request.customHeaders.forEach(::header)
                }
                .build()

            client.newCall(httpRequest).useCancellable { response ->
                if (!response.isSuccessful) {
                    val detail = response.body?.string().orEmpty().take(2_000)
                    throw ProviderHttpException(
                        response.code,
                        "${response.code} ${response.message}${detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}",
                    )
                }
                val raw = response.body?.string()
                    ?: throw ProviderProtocolException("Qwen-Image returned an empty response")
                val root = runCatching { ProviderJson.parseToJsonElement(raw).jsonObject }
                    .getOrElse { throw ProviderProtocolException("Qwen-Image returned invalid JSON", it) }
                val providerCode = root["code"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (providerCode.isNotBlank()) {
                    val providerMessage = root["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    throw ProviderProtocolException(
                        listOf(providerCode, providerMessage).filter(String::isNotBlank).joinToString(": "),
                    )
                }

                val urls = imageUrls(root)
                if (urls.isEmpty()) {
                    throw ProviderProtocolException("Qwen-Image completed without returning an image URL")
                }
                val images = urls.take(MAX_IMAGES).mapIndexed { index, url ->
                    GeneratedImageOutput(
                        bytes = downloadImage(url),
                        mimeType = "image/png",
                        displayName = "qwen-image-${index + 1}.png",
                    )
                }
                emit(StreamChunk(generatedImages = images, finishReason = "stop"))
            }
        }

    internal fun endpointFor(request: ChatRequest): String =
        ModelRequestPolicy.qwenCloudImageEndpoint(request.provider)

    internal fun buildRequestBody(
        request: ChatRequest,
        prompt: String = imagePrompt(request),
    ): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(request.model.modelId))
        put("input", buildJsonObject {
            put("messages", buildJsonArray {
                // Qwen-Image's synchronous native API accepts exactly one message.
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(prompt)) })
                    })
                })
            })
        })
        put("parameters", buildJsonObject {
            put("n", JsonPrimitive(1))
            put("prompt_extend", JsonPrimitive(true))
            put("watermark", JsonPrimitive(false))
        })
    }

    internal fun imageUrls(root: JsonObject): List<String> {
        val output = root["output"] as? JsonObject ?: return emptyList()
        val choices = output["choices"] as? JsonArray ?: return emptyList()
        return choices.flatMap { choiceElement ->
            val choice = choiceElement as? JsonObject ?: return@flatMap emptyList()
            val message = choice["message"] as? JsonObject ?: return@flatMap emptyList()
            val content = message["content"] as? JsonArray ?: return@flatMap emptyList()
            content.mapNotNull { partElement ->
                val part = partElement as? JsonObject ?: return@mapNotNull null
                part["image"]?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            }
        }.distinct()
    }

    private fun imagePrompt(request: ChatRequest): String = request.messages
        .lastOrNull { it.role == MessageRole.USER }
        ?.content
        ?.trim()
        .orEmpty()

    private suspend fun downloadImage(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        return client.newCall(request).useCancellable { response ->
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, "Generated-image download failed (${response.code})")
            }
            val body = response.body ?: throw ProviderProtocolException("Generated-image download returned no data")
            val declared = body.contentLength()
            require(declared < 0 || declared <= MAX_IMAGE_BYTES) {
                "Generated image exceeded Xylune's 64 MB limit"
            }
            body.bytes().also { bytes ->
                require(bytes.size.toLong() <= MAX_IMAGE_BYTES) {
                    "Generated image exceeded Xylune's 64 MB limit"
                }
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        const val MAX_IMAGES = 6
    }
}
