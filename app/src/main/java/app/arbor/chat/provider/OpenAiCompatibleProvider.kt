package app.arbor.chat.provider

import app.arbor.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class OpenAiCompatibleProvider(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) = withContext(Dispatchers.IO) {
        if (ModelRequestPolicy.requestType(request.provider, request.model) == ModelRequestType.IMAGE_GENERATION) {
            generateImage(request, emit)
            return@withContext
        }

        val endpoint = endpointFor(request)
        var emptyAttempt = 0
        var deepSeekCorrectionAttempt = 0
        var discardedInputTokens = 0L
        var discardedOutputTokens = 0L
        var discardedCachedTokens = 0L

        while (true) {
            val attemptRequest = deepSeekToolGuardedRequest(request, deepSeekCorrectionAttempt)
            val bodyJson = buildRequestBody(attemptRequest)
            val builder = Request.Builder()
                .url(endpoint)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            if (attemptRequest.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${attemptRequest.apiKey}")
            attemptRequest.customHeaders.forEach(builder::header)
            val httpRequest = builder.build()

            val calls = linkedMapOf<Int, ToolCallAccumulator>()
            val exposedTools = attemptRequest.tools.mapTo(linkedSetOf()) { it.name.lowercase() }
            val protocolTools = linkedSetOf<String>().apply {
                addAll(exposedTools)
                attemptRequest.toolProtocolNames.mapTo(this) { it.lowercase() }
            }
            val dsmlChannels = protocolTools.takeIf { it.isNotEmpty() }?.let(::DsmlChannelsAdapter)
            val rawText = StringBuilder()
            val rawReasoning = StringBuilder()
            val bufferedVisibleText = StringBuilder()
            val bufferedVisibleReasoning = StringBuilder()
            // DeepSeek tool turns and no-tool finalization turns are held until EOF.
            // The latter is the critical case: after the execution budget is exhausted,
            // a stale DSML request must never be streamed into the conversation.
            val quarantineToolText = attemptRequest.isDeepSeekFamily() ||
                (exposedTools.isEmpty() && protocolTools.isNotEmpty())
            var meaningfulPayloadReceived = false
            var finishReason: String? = null
            var attemptInputTokens: Long? = null
            var attemptOutputTokens: Long? = null
            var attemptCachedTokens: Long? = null

            client.newCall(httpRequest).useCancellable { response ->
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
                    parseChunk(payload, calls)?.let { chunk ->
                        rawText.append(chunk.text)
                        rawReasoning.append(chunk.reasoning)
                        val adapted = dsmlChannels?.accept(chunk.text, chunk.reasoning)
                            ?: DsmlChannelDelta(chunk.text, chunk.reasoning)
                        val outgoing = if (quarantineToolText) {
                            bufferedVisibleText.append(adapted.text)
                            bufferedVisibleReasoning.append(adapted.reasoning)
                            chunk.copy(text = "", reasoning = "")
                        } else if (adapted.text == chunk.text && adapted.reasoning == chunk.reasoning) {
                            chunk
                        } else {
                            chunk.copy(text = adapted.text, reasoning = adapted.reasoning)
                        }
                        if (outgoing.hasMeaningfulPayload()) meaningfulPayloadReceived = true
                        finishReason = outgoing.finishReason ?: finishReason
                        attemptInputTokens = outgoing.inputTokens ?: attemptInputTokens
                        attemptOutputTokens = outgoing.outputTokens ?: attemptOutputTokens
                        attemptCachedTokens = outgoing.cachedInputTokens ?: attemptCachedTokens
                        emit(outgoing)
                    }
                }
            }

            val adapted = dsmlChannels?.finish()
            if (quarantineToolText) {
                bufferedVisibleText.append(adapted?.tailText.orEmpty())
                bufferedVisibleReasoning.append(adapted?.tailReasoning.orEmpty())
            }
            val completedStructuredCalls = calls.toSortedMap()
            val recoveredProtocolCalls = adapted?.calls.orEmpty()
            val recoveredPlainTextCalls = if (completedStructuredCalls.isEmpty() && protocolTools.isNotEmpty()) {
                PlainTextToolCallDetector.extractTrailingCalls(rawText.toString(), protocolTools)
                    .ifEmpty { PlainTextToolCallDetector.extractTrailingCalls(rawReasoning.toString(), protocolTools) }
            } else {
                emptyList()
            }
            val recoveredTextCalls = recoveredProtocolCalls.ifEmpty { recoveredPlainTextCalls }
            val protocolHint = DsmlToolProtocol.containsProtocolHint(rawText) ||
                DsmlToolProtocol.containsProtocolHint(rawReasoning)
            val textEncodedToolFailure = completedStructuredCalls.isEmpty() &&
                protocolTools.isNotEmpty() &&
                (recoveredTextCalls.isNotEmpty() || adapted?.malformed == true || protocolHint)

            if (textEncodedToolFailure) {
                emit(StreamChunk(resetCurrentAttempt = true))

                if (deepSeekCorrectionAttempt < MAX_DEEPSEEK_TOOL_CORRECTION_RETRIES) {
                    discardedInputTokens += attemptInputTokens ?: 0L
                    discardedOutputTokens += attemptOutputTokens ?: 0L
                    discardedCachedTokens += attemptCachedTokens ?: 0L
                    deepSeekCorrectionAttempt++
                    delay(DEEPSEEK_TOOL_CORRECTION_RETRY_DELAY_MS)
                    continue
                }

                if (exposedTools.isEmpty()) {
                    throw ProviderProtocolException(
                        "The provider repeatedly printed a tool request after Arbor disabled tools for finalization. " +
                            "Arbor discarded the protocol instead of displaying or executing it.",
                    )
                }
                val executableRecoveredCalls = recoveredTextCalls.filter { call ->
                    call.name.lowercase() in exposedTools
                }
                if (executableRecoveredCalls.isEmpty()) {
                    throw ProviderProtocolException(
                        "The provider repeatedly serialized a tool request into assistant text or reasoning, and Arbor could not safely recover an exposed tool call.",
                    )
                }
                emit(
                    StreamChunk(
                        toolCalls = executableRecoveredCalls,
                        inputTokens = attemptInputTokens.plusUsage(discardedInputTokens),
                        outputTokens = attemptOutputTokens.plusUsage(discardedOutputTokens),
                        cachedInputTokens = attemptCachedTokens.plusUsage(discardedCachedTokens),
                        finishReason = "tool_calls",
                    ),
                )
                break
            }

            if (quarantineToolText) {
                val finalChunk = StreamChunk(
                    text = bufferedVisibleText.toString(),
                    reasoning = bufferedVisibleReasoning.toString(),
                )
                if (finalChunk.hasMeaningfulPayload()) {
                    meaningfulPayloadReceived = true
                    emit(finalChunk)
                }
            } else {
                adapted?.let { result ->
                    val finalChunk = StreamChunk(
                        text = result.tailText,
                        reasoning = result.tailReasoning,
                        toolCalls = if (completedStructuredCalls.isEmpty()) result.calls else emptyList(),
                    )
                    if (finalChunk.hasMeaningfulPayload()) {
                        meaningfulPayloadReceived = true
                        emit(finalChunk)
                    }
                }
            }

            if (completedStructuredCalls.isNotEmpty()) {
                emit(
                    StreamChunk(
                        toolCallProgress = completedStructuredCalls.map { (index, call) ->
                            call.progress(index, complete = true)
                        },
                        toolCalls = completedStructuredCalls.values.map { it.complete() },
                    ),
                )
                meaningfulPayloadReceived = true
            }

            if (discardedInputTokens > 0L || discardedOutputTokens > 0L || discardedCachedTokens > 0L) {
                emit(
                    StreamChunk(
                        inputTokens = attemptInputTokens.plusUsage(discardedInputTokens),
                        outputTokens = attemptOutputTokens.plusUsage(discardedOutputTokens),
                        cachedInputTokens = attemptCachedTokens.plusUsage(discardedCachedTokens),
                    ),
                )
            }

            if (meaningfulPayloadReceived) break
            if (emptyAttempt >= MAX_EMPTY_STREAM_RETRIES) {
                val suffix = finishReason?.takeIf(String::isNotBlank)?.let { " (finish reason: $it)" }.orEmpty()
                throw ProviderProtocolException(
                    "Provider completed without returning content after ${emptyAttempt + 1} attempts$suffix",
                )
            }
            emptyAttempt++
            delay(EMPTY_STREAM_RETRY_DELAY_MS * emptyAttempt)
        }
    }

    internal suspend fun generateImage(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        val prompt = imagePrompt(request)
        require(prompt.isNotBlank()) { "Enter a prompt for image generation" }
        val latestUser = request.messages.lastOrNull { it.role == MessageRole.USER }
        require(latestUser?.attachments.orEmpty().none { it.mimeType.startsWith("image/") }) {
            "This image model supports text-to-image generation in Arbor. Image editing is not enabled for this model yet."
        }
        val endpoint = endpointFor(request)
        val builder = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(buildImageRequestBody(request, prompt).toString().toRequestBody("application/json".toMediaType()))
        if (request.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${request.apiKey}")
        request.customHeaders.forEach(builder::header)
        client.newCall(builder.build()).useCancellable { response ->
            if (!response.isSuccessful) {
                val error = response.body?.readErrorSnippet().orEmpty()
                throw ProviderHttpException(response.code, "${response.code} ${response.message}: $error")
            }
            val body = response.body ?: throw ProviderProtocolException("Image provider returned an empty response")
            val declared = body.contentLength()
            require(declared < 0 || declared <= MAX_IMAGE_RESPONSE_BYTES) { "Image response exceeded Arbor's 96 MB safety limit" }
            val root = runCatching { ProviderJson.parseToJsonElement(body.string()).jsonObject }
                .getOrElse { throw ProviderProtocolException("Image provider returned invalid JSON", it) }
            root.obj("error")?.let { error ->
                throw ProviderProtocolException(error.string("message") ?: "Image generation failed")
            }
            val images = parseImageResponse(root)
            if (images.isEmpty()) throw ProviderProtocolException("Image provider completed without returning an image")
            val usage = root.obj("usage")
            emit(
                StreamChunk(
                    generatedImages = images,
                    inputTokens = usage?.long("input_tokens") ?: usage?.long("prompt_tokens"),
                    outputTokens = usage?.long("output_tokens") ?: usage?.long("completion_tokens"),
                    finishReason = "stop",
                ),
            )
        }
    }

    internal fun deepSeekToolGuardedRequest(
        request: ChatRequest,
        correctionAttempt: Int,
    ): ChatRequest {
        val hasExposedTools = request.tools.isNotEmpty()
        val hasProtocolGuard = request.toolProtocolNames.isNotEmpty()
        if (correctionAttempt == 0 && !(request.isDeepSeekFamily() && hasExposedTools)) return request
        if (!hasExposedTools && !hasProtocolGuard) return request
        val instruction = buildString {
            if (hasExposedTools) append(DEEPSEEK_TOOL_CALL_GUARD)
            if (correctionAttempt > 0) {
                if (isNotEmpty()) append("\n\n")
                append(
                    if (hasExposedTools) DEEPSEEK_TOOL_CALL_CORRECTION
                    else TOOL_DISABLED_PROTOCOL_CORRECTION
                )
            }
        }
        val messages = request.messages.toMutableList()
        val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
        if (systemIndex >= 0) {
            val system = messages[systemIndex]
            messages[systemIndex] = system.copy(
                content = listOf(system.content, instruction)
                    .filter(String::isNotBlank)
                    .joinToString("\n\n"),
            )
        } else {
            messages.add(0, InputMessage(MessageRole.SYSTEM, instruction))
        }
        return request.copy(messages = messages)
    }

    private fun ChatRequest.isDeepSeekFamily(): Boolean = listOf(
        provider.id,
        provider.displayName,
        provider.baseUrl,
        model.modelId,
        model.displayName,
    ).any { it.contains("deepseek", ignoreCase = true) }

    private fun Long?.plusUsage(extra: Long): Long? = when {
        this != null -> this + extra
        extra > 0L -> extra
        else -> null
    }

    internal fun endpointFor(request: ChatRequest): String =
        ModelRequestPolicy.endpoint(request.provider, request.model, request.continuation)

    internal fun buildImageRequestBody(request: ChatRequest, prompt: String = imagePrompt(request)): JsonObject = buildJsonObject {
        val modelId = request.model.modelId
        put("model", JsonPrimitive(modelId))
        put("prompt", JsonPrimitive(prompt))
        put("n", JsonPrimitive(1))
        if (modelId.lowercase().startsWith("dall-e-")) {
            put("response_format", JsonPrimitive("b64_json"))
            put("size", JsonPrimitive("1024x1024"))
        } else {
            put("size", JsonPrimitive("auto"))
            put("quality", JsonPrimitive("auto"))
            put("background", JsonPrimitive("auto"))
            put("output_format", JsonPrimitive("png"))
        }
    }

    internal fun imagePrompt(request: ChatRequest): String = request.messages
        .lastOrNull { it.role == MessageRole.USER }
        ?.content
        ?.trim()
        .orEmpty()

    internal fun parseImageResponse(root: JsonObject): List<GeneratedImageOutput> {
        val values = root["data"] as? JsonArray ?: JsonArray(listOf(root))
        return values.mapIndexedNotNull { index, element ->
            val item = element as? JsonObject ?: return@mapIndexedNotNull null
            val format = item["output_format"]?.jsonPrimitive?.contentOrNull
                ?: root["output_format"]?.jsonPrimitive?.contentOrNull
                ?: "png"
            val mime = when (format.lowercase()) {
                "jpeg", "jpg" -> "image/jpeg"
                "webp" -> "image/webp"
                else -> "image/png"
            }
            val bytes = item["b64_json"]?.jsonPrimitive?.contentOrNull
                ?.let(::decodeImageBase64)
                ?: item["image_base64"]?.jsonPrimitive?.contentOrNull?.let(::decodeImageBase64)
                ?: item["url"]?.jsonPrimitive?.contentOrNull?.let(::downloadImage)
                ?: return@mapIndexedNotNull null
            val extension = when (mime) {
                "image/jpeg" -> "jpg"
                "image/webp" -> "webp"
                else -> "png"
            }
            GeneratedImageOutput(
                bytes = bytes,
                mimeType = mime,
                displayName = "generated-image-${index + 1}.$extension",
                description = item["revised_prompt"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun decodeImageBase64(value: String): ByteArray = runCatching {
        Base64.getDecoder().decode(value.substringAfter("base64,", value))
    }.getOrElse { throw ProviderProtocolException("Image provider returned invalid base64 image data", it) }
        .also { require(it.size.toLong() <= MAX_IMAGE_BYTES) { "Generated image exceeded Arbor's 64 MB limit" } }

    private fun downloadImage(url: String): ByteArray {
        val parsed = runCatching { url.toHttpUrl() }.getOrElse {
            throw ProviderProtocolException("Image provider returned an invalid image URL", it)
        }
        require(parsed.scheme in setOf("https", "http")) { "Unsupported generated-image URL" }
        return client.newCall(Request.Builder().url(parsed).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw ProviderHttpException(response.code, "Generated-image download failed (${response.code})")
            val body = response.body ?: throw ProviderProtocolException("Generated-image download returned no data")
            val declared = body.contentLength()
            require(declared < 0 || declared <= MAX_IMAGE_BYTES) { "Generated image exceeded Arbor's 64 MB limit" }
            body.bytes().also { require(it.size.toLong() <= MAX_IMAGE_BYTES) { "Generated image exceeded Arbor's 64 MB limit" } }
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
                            // DeepSeek rejects a null assistant content field while
                            // replaying tool calls. Other OpenAI-compatible APIs use
                            // null for the same protocol shape.
                            put("content", if (isDeepSeek) JsonPrimitive("") else JsonNull)
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
        val toolProgress = mutableListOf<NativeToolCallProgress>()
        delta?.array("tool_calls")?.forEach { element ->
            val item = element.jsonObject
            val index = item.long("index")?.toInt() ?: calls.size
            val accumulator = calls.getOrPut(index) { ToolCallAccumulator() }
            item.string("id")?.let { accumulator.id = it }
            item.obj("function")?.let { function ->
                function.string("name")?.let { accumulator.name += it }
                function.string("arguments")?.let { accumulator.arguments.append(it) }
            }
            toolProgress += accumulator.progress(index)
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
            toolCallProgress = toolProgress,
        )
    }

    private fun combinedText(message: InputMessage, nativeAttachmentIds: Set<String>): String {
        if (message.attachments.isEmpty()) return message.content
        val context = message.attachments.mapNotNull { attachment ->
            if (attachment.id in nativeAttachmentIds) null else attachmentContext(attachment)
        }
        return (listOf(message.content) + context).filter(String::isNotBlank).joinToString("\n\n")
    }

    private fun StreamChunk.hasMeaningfulPayload(): Boolean =
        text.isNotEmpty() || reasoning.isNotEmpty() || toolCallProgress.isNotEmpty() ||
            toolCalls.isNotEmpty() || generatedImages.isNotEmpty()

    internal class ToolCallAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()

        fun progress(index: Int, complete: Boolean = false) = NativeToolCallProgress(
            index = index,
            id = id,
            name = name,
            argumentsJson = arguments.toString(),
            complete = complete,
        )

        fun complete(): NativeToolCall {
            val stableId = id.ifBlank { "call_${name.hashCode().toUInt().toString(16)}" }
            return NativeToolCall(stableId, name, arguments.toString().ifBlank { "{}" })
        }
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
        const val MAX_EMPTY_STREAM_RETRIES = 2
        const val EMPTY_STREAM_RETRY_DELAY_MS = 750L
        const val MAX_IMAGE_RESPONSE_BYTES = 96L * 1024 * 1024
        const val MAX_DEEPSEEK_TOOL_CORRECTION_RETRIES = 1
        const val DEEPSEEK_TOOL_CORRECTION_RETRY_DELAY_MS = 350L
        const val DEEPSEEK_TOOL_CALL_GUARD =
            "When a tool is needed, return ONLY the API's structured tool_calls field for that turn. " +
                "Never write function names, DSML tags, XML-like tool markup, or JSON tool arguments in content."
        const val DEEPSEEK_TOOL_CALL_CORRECTION =
            "Retry the current turn from scratch. Your previous attempt serialized a tool request into content. " +
                "Use structured tool_calls only, with no preamble; otherwise answer normally without tool syntax."
        const val TOOL_DISABLED_PROTOCOL_CORRECTION =
            "Retry the current turn from scratch. Tools are unavailable for this finalization turn. " +
                "Do not print DSML, XML-like tool markup, function names, or tool arguments. " +
                "Answer only from the evidence already present and state any concrete limitation."
    }

private val app.arbor.chat.data.ThinkingEffort.apiValue: String
    get() = when (this) {
        app.arbor.chat.data.ThinkingEffort.MINIMAL -> "minimal"
        app.arbor.chat.data.ThinkingEffort.LOW -> "low"
        app.arbor.chat.data.ThinkingEffort.MEDIUM -> "medium"
        app.arbor.chat.data.ThinkingEffort.HIGH -> "high"
        app.arbor.chat.data.ThinkingEffort.XHIGH,
        app.arbor.chat.data.ThinkingEffort.MAX -> "xhigh"
    }

}
