package app.xylune.chat.provider

import app.xylune.chat.data.AttachmentEntity
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
import java.io.File
import java.util.Base64
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
            require(prompt.isNotBlank()) { "Enter a prompt for image generation or editing" }

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
    ): JsonObject {
        val inputImages = encodeInputImages(request)
        return buildJsonObject {
            put("model", JsonPrimitive(request.model.modelId))
            put("input", buildJsonObject {
                put("messages", buildJsonArray {
                    // Qwen-Image's native API accepts exactly one user message. Fused/edit models
                    // place up to three images in this same content array before the one text prompt.
                    add(buildJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", buildJsonArray {
                            inputImages.forEach { imageData ->
                                add(buildJsonObject { put("image", JsonPrimitive(imageData)) })
                            }
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

    private fun encodeInputImages(request: ChatRequest): List<String> {
        val latestUser = request.messages.lastOrNull { it.role == MessageRole.USER }
        val attachments = latestUser?.attachments.orEmpty()
        require(attachments.all { it.mimeType.startsWith("image/") }) {
            "Qwen-Image accepts image attachments only. Remove non-image attachments first."
        }
        require(attachments.size <= MAX_INPUT_IMAGES) {
            "Qwen-Image accepts at most $MAX_INPUT_IMAGES input images per request."
        }
        val acceptsImages = ModelRequestPolicy.qwenCloudImageAcceptsInputImages(request.model)
        if (!acceptsImages) {
            require(attachments.isEmpty()) {
                "${request.model.displayName} supports text-to-image generation but not image editing."
            }
            return emptyList()
        }
        if (ModelRequestPolicy.qwenCloudImageRequiresInputImage(request.model)) {
            require(attachments.isNotEmpty()) {
                "${request.model.displayName} is an image-editing model. Attach at least one image."
            }
        }
        return attachments.map(::encodeAttachment)
    }

    private fun encodeAttachment(attachment: AttachmentEntity): String {
        val mimeType = normalizedImageMimeType(attachment.mimeType)
        require(mimeType in SUPPORTED_INPUT_MIME_TYPES) {
            "Qwen-Image does not support ${attachment.mimeType.ifBlank { "this image format" }}."
        }
        val file = File(attachment.localPath)
        require(file.isFile) { "Could not read attached image: ${attachment.displayName}" }
        val size = file.length()
        require(size in 1..MAX_LOCAL_BASE64_IMAGE_BYTES) {
            "${attachment.displayName} is too large for Qwen-Image's Base64 HTTP input. Keep local images under 7 MB."
        }
        val bytes = file.readBytes()
        require(bytes.size.toLong() <= MAX_LOCAL_BASE64_IMAGE_BYTES) {
            "${attachment.displayName} is too large for Qwen-Image's Base64 HTTP input. Keep local images under 7 MB."
        }
        return "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}"
    }

    private fun normalizedImageMimeType(raw: String): String = when (raw.lowercase()) {
        "image/jpg" -> "image/jpeg"
        "image/x-ms-bmp" -> "image/bmp"
        "image/x-tiff" -> "image/tiff"
        else -> raw.lowercase()
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
        val SUPPORTED_INPUT_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/bmp",
            "image/tiff",
            "image/webp",
            "image/gif",
        )
        const val MAX_INPUT_IMAGES = 3
        const val MAX_LOCAL_BASE64_IMAGE_BYTES = 7L * 1024 * 1024
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        const val MAX_IMAGES = 6
    }
}
