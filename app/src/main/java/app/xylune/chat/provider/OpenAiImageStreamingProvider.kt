package app.xylune.chat.provider

import app.xylune.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Official OpenAI image transport with native image editing and partial-image SSE.
 * Generic compatible endpoints deliberately stay on [OpenAiCompatibleProvider]:
 * an Images-compatible generation endpoint does not imply edits or streaming.
 */
internal class OpenAiImageStreamingProvider(
    private val delegate: ChatProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.MINUTES)
        .writeTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        if (
            !ModelRequestPolicy.isOfficialOpenAi(request.provider) ||
            !request.model.supportsImageGeneration
        ) {
            delegate.stream(request, emit)
            return
        }
        generateOrEdit(request, emit)
    }

    private suspend fun generateOrEdit(
        request: ChatRequest,
        emit: suspend (StreamChunk) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val prompt = request.messages.lastOrNull { it.role == MessageRole.USER }?.content?.trim().orEmpty()
        require(prompt.isNotBlank()) { "Describe the image you want to generate or edit" }

        val latestUser = request.messages.lastOrNull { it.role == MessageRole.USER }
        val attachments = latestUser?.attachments.orEmpty()
        require(attachments.all { it.mimeType.startsWith("image/") && it.mimeType != "image/svg+xml" }) {
            "OpenAI image requests accept raster image references only. Remove non-image attachments first."
        }

        val capabilities = requireNotNull(imageModelCapabilities(request.provider, request.model))
        require(attachments.size <= capabilities.maxInputImages) {
            "${request.model.displayName} accepts at most ${capabilities.maxInputImages} reference images in Xylune."
        }
        require(attachments.isEmpty() || capabilities.supportsEditing) {
            "${request.model.displayName} supports generation but not image editing."
        }

        val isEdit = attachments.isNotEmpty()
        val httpRequest = if (isEdit) buildEditRequest(request, prompt, attachments) else buildGenerationRequest(request, prompt)
        executeStream(httpRequest, isEdit, emit)
    }

    internal fun buildGenerationRequest(request: ChatRequest, prompt: String): Request {
        val body = buildJsonObject {
            put("model", JsonPrimitive(request.model.modelId))
            put("prompt", JsonPrimitive(prompt))
            put("n", JsonPrimitive(1))
            put("size", JsonPrimitive("auto"))
            put("quality", JsonPrimitive("auto"))
            put("background", JsonPrimitive("auto"))
            put("output_format", JsonPrimitive("png"))
            put("stream", JsonPrimitive(true))
            put("partial_images", JsonPrimitive(PARTIAL_IMAGE_COUNT))
        }
        return authenticatedBuilder(request, "${request.provider.baseUrl.trimEnd('/')}/images/generations")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
    }

    internal fun buildEditRequest(
        request: ChatRequest,
        prompt: String,
        attachments: List<app.xylune.chat.data.AttachmentEntity>,
    ): Request {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", request.model.modelId)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", "1")
            .addFormDataPart("size", "auto")
            .addFormDataPart("quality", "auto")
            .addFormDataPart("background", "auto")
            .addFormDataPart("output_format", "png")
            .addFormDataPart("stream", "true")
            .addFormDataPart("partial_images", PARTIAL_IMAGE_COUNT.toString())

        attachments.forEach { attachment ->
            val mime = normalizedMime(attachment.mimeType)
            require(mime in SUPPORTED_EDIT_MIME_TYPES) {
                "OpenAI image editing does not support ${attachment.mimeType.ifBlank { "this image format" }} in Xylune."
            }
            val file = File(attachment.localPath)
            require(file.isFile) { "Could not read attached image: ${attachment.displayName}" }
            require(file.length() in 1..MAX_INPUT_IMAGE_BYTES) {
                "${attachment.displayName} is too large for image editing. Keep each reference image under 25 MB."
            }
            multipart.addFormDataPart(
                "image[]",
                attachment.displayName.ifBlank { file.name },
                file.asRequestBody(mime.toMediaType()),
            )
        }

        return authenticatedBuilder(request, "${request.provider.baseUrl.trimEnd('/')}/images/edits")
            .header("Accept", "text/event-stream")
            .post(multipart.build())
            .build()
    }

    private fun authenticatedBuilder(request: ChatRequest, endpoint: String): Request.Builder =
        Request.Builder().url(endpoint).apply {
            if (request.apiKey.isNotBlank()) header("Authorization", "Bearer ${request.apiKey}")
            request.customHeaders.forEach(::header)
        }

    private suspend fun executeStream(
        request: Request,
        edit: Boolean,
        emit: suspend (StreamChunk) -> Unit,
    ) {
        client.newCall(request).useCancellable { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.readErrorSnippet().orEmpty()
                throw ProviderHttpException(response.code, "${response.code} ${response.message}: $detail")
            }
            val body = response.body ?: throw ProviderProtocolException("OpenAI image request returned an empty response")
            val source = body.source()
            var completed = false
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isBlank() || payload == "[DONE]") continue
                val root = runCatching { ProviderJson.parseToJsonElement(payload) as? JsonObject }.getOrNull()
                    ?: continue
                when (root["type"]?.jsonPrimitive?.contentOrNull) {
                    "image_generation.partial_image", "image_edit.partial_image" -> {
                        val bytes = decode(root["b64_json"]?.jsonPrimitive?.contentOrNull)
                        val index = root["partial_image_index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        emit(
                            StreamChunk(
                                generatedImagePreview = GeneratedImageOutput(
                                    bytes = bytes,
                                    mimeType = imageMime(root),
                                    displayName = "image-preview-${index + 1}.${imageExtension(root)}",
                                ),
                                generatedImagePreviewIndex = index,
                                generatedImagePreviewCount = PARTIAL_IMAGE_COUNT,
                            ),
                        )
                    }
                    "image_generation.completed", "image_edit.completed" -> {
                        val bytes = decode(root["b64_json"]?.jsonPrimitive?.contentOrNull)
                        val usage = root["usage"] as? JsonObject
                        emit(
                            StreamChunk(
                                generatedImages = listOf(
                                    GeneratedImageOutput(
                                        bytes = bytes,
                                        mimeType = imageMime(root),
                                        displayName = if (edit) "edited-image.${imageExtension(root)}" else "generated-image.${imageExtension(root)}",
                                    ),
                                ),
                                inputTokens = usage.long("input_tokens"),
                                outputTokens = usage.long("output_tokens"),
                                finishReason = "stop",
                            ),
                        )
                        completed = true
                    }
                }
            }
            if (!completed) {
                throw ProviderProtocolException("OpenAI image stream ended before returning the completed image")
            }
        }
    }

    private fun decode(raw: String?): ByteArray {
        val value = raw?.takeIf(String::isNotBlank)
            ?: throw ProviderProtocolException("OpenAI image stream returned an empty image")
        return runCatching { Base64.getDecoder().decode(value.substringAfter("base64,", value)) }
            .getOrElse { throw ProviderProtocolException("OpenAI image stream returned invalid base64 image data", it) }
            .also { bytes ->
                require(bytes.size.toLong() <= MAX_OUTPUT_IMAGE_BYTES) {
                    "Generated image exceeded Xylune's 64 MB limit"
                }
            }
    }

    private fun imageMime(root: JsonObject): String = when (
        root["output_format"]?.jsonPrimitive?.contentOrNull?.lowercase()
    ) {
        "jpeg", "jpg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    private fun imageExtension(root: JsonObject): String = when (imageMime(root)) {
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        else -> "png"
    }

    private fun normalizedMime(raw: String): String = when (raw.lowercase()) {
        "image/jpg" -> "image/jpeg"
        else -> raw.lowercase()
    }

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val SUPPORTED_EDIT_MIME_TYPES = setOf("image/png", "image/jpeg", "image/webp")
        const val PARTIAL_IMAGE_COUNT = 3
        const val MAX_INPUT_IMAGE_BYTES = 25L * 1024 * 1024
        const val MAX_OUTPUT_IMAGE_BYTES = 64L * 1024 * 1024
    }
}
