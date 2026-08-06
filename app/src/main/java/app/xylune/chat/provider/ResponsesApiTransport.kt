package app.xylune.chat.provider

import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ProviderKind
import kotlinx.coroutines.ensureActive
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
import kotlin.coroutines.coroutineContext

internal enum class NativeWebSearchMode {
    NONE,
    RESPONSES,
    ANTHROPIC,
    GEMINI,
}

/**
 * Selects a provider-owned search implementation only when Xylune's existing
 * Web search switch exposed the web_search tool for the current request.
 * Unsupported models keep using Xylune's client-side search tools.
 */
internal object NativeWebSearch {
    private val replaceableToolNames = setOf("web_search", "web_fetch")

    fun mode(request: ChatRequest): NativeWebSearchMode {
        if (!request.model.supportsTools || !requested(request)) return NativeWebSearchMode.NONE
        val providerId = request.provider.id.lowercase()
        val baseUrl = request.provider.baseUrl.lowercase()
        val modelId = request.model.modelId.lowercase()
        return when {
            request.provider.kind == ProviderKind.OPENAI_OAUTH -> NativeWebSearchMode.RESPONSES
            request.provider.kind == ProviderKind.ANTHROPIC -> NativeWebSearchMode.ANTHROPIC
            request.provider.kind == ProviderKind.GEMINI && modelId.startsWith("gemini-3") -> NativeWebSearchMode.GEMINI
            providerId == "deepseek" || baseUrl.contains("api.deepseek.com") -> {
                if (modelId == "deepseek-v4-flash") NativeWebSearchMode.RESPONSES else NativeWebSearchMode.NONE
            }
            providerId in setOf("openai", "openrouter", "xai") -> NativeWebSearchMode.RESPONSES
            baseUrl.contains("api.openai.com") ||
                baseUrl.contains("openrouter.ai") ||
                baseUrl.contains("api.x.ai") ||
                baseUrl.contains("api.perplexity.ai") -> NativeWebSearchMode.RESPONSES
            else -> NativeWebSearchMode.NONE
        }
    }

    fun requested(request: ChatRequest): Boolean = request.tools.any {
        it.name.equals("web_search", ignoreCase = true)
    }

    fun requestedFetch(request: ChatRequest): Boolean = request.tools.any {
        it.name.equals("web_fetch", ignoreCase = true)
    }

    fun clientTools(request: ChatRequest): List<NativeToolDefinition> =
        if (mode(request) == NativeWebSearchMode.NONE) request.tools
        else request.tools.filterNot { it.name.lowercase() in replaceableToolNames }

    fun responsesServerToolType(request: ChatRequest): String =
        if (request.provider.id.equals("openrouter", ignoreCase = true) ||
            request.provider.baseUrl.contains("openrouter.ai", ignoreCase = true)
        ) {
            "openrouter:web_search"
        } else {
            "web_search"
        }
}

/** OpenAI Responses-compatible transport shared by DeepSeek, OpenAI, xAI, OpenRouter and Perplexity. */
internal class ResponsesApiTransport(private val client: OkHttpClient) {
    suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        val builder = Request.Builder()
            .url(endpoint(request))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(buildRequestBody(request).toString().toRequestBody("application/json".toMediaType()))
        if (request.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${request.apiKey}")
        request.customHeaders.forEach(builder::header)

        val state = ResponsesApiStreamState()
        client.newCall(builder.build()).useCancellable { response ->
            if (!response.isSuccessful) {
                val error = response.body?.readErrorSnippet().orEmpty()
                throw ProviderHttpException(response.code, "${response.code} ${response.message}: $error")
            }
            val source = response.body?.source() ?: throw ProviderProtocolException("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isBlank() || payload == "[DONE]") continue
                state.accept(payload)?.let { emit(it) }
            }
        }
        state.finalChunk()?.let { emit(it) }
    }

    internal fun endpoint(request: ChatRequest): String {
        val base = request.provider.baseUrl.trimEnd('/')
        return if (base.contains("api.perplexity.ai", ignoreCase = true) && !base.endsWith("/v1")) {
            "$base/v1/responses"
        } else {
            "$base/responses"
        }
    }

    internal fun buildRequestBody(request: ChatRequest): JsonObject = buildJsonObject {
        put("model", JsonPrimitive(request.model.modelId))
        put("stream", JsonPrimitive(true))
        put("store", JsonPrimitive(false))
        put("max_output_tokens", JsonPrimitive(request.maxOutputTokens))
        put("parallel_tool_calls", JsonPrimitive(false))
        if (request.model.supportsThinking) {
            put("reasoning", buildJsonObject {
                put(
                    "effort",
                    JsonPrimitive(if (request.thinkingEnabled) request.thinkingEffort.responsesValue else "none"),
                )
            })
        }

        val clientTools = NativeWebSearch.clientTools(request)
        put("tools", buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive(NativeWebSearch.responsesServerToolType(request)))
                if (NativeWebSearch.responsesServerToolType(request) == "openrouter:web_search") {
                    put("parameters", buildJsonObject {
                        put("engine", JsonPrimitive("auto"))
                        put("max_uses", JsonPrimitive(8))
                        put("max_total_results", JsonPrimitive(30))
                    })
                }
            })
            clientTools.forEach { tool ->
                add(buildJsonObject {
                    put("type", JsonPrimitive("function"))
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("parameters", ProviderJson.parseToJsonElement(tool.parametersJson))
                    put("strict", JsonPrimitive(false))
                })
            }
        })
        put("tool_choice", JsonPrimitive("auto"))
        put("input", JsonArray(buildInput(request)))
    }

    private fun buildInput(request: ChatRequest): List<JsonElement> = buildList {
        request.messages.forEach { message ->
            if (message.role == MessageRole.ASSISTANT && message.nativeProviderPayloadJson.isNotBlank()) {
                when (val payload = runCatching {
                    ProviderJson.parseToJsonElement(message.nativeProviderPayloadJson)
                }.getOrNull()) {
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
                            val file = if (!attachment.mimeType.startsWith("image/") && request.model.supportsFiles) {
                                fileDataUrl(attachment)
                            } else null
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
}

/** Stateful parser for semantic Responses SSE events, including server-side search activity and citations. */
internal class ResponsesApiStreamState {
    private val outputItems = sortedMapOf<Int, JsonObject>()
    private val calls = sortedMapOf<Int, CallAccumulator>()
    private val searchIndexes = linkedMapOf<String, Int>()
    private val citations = linkedMapOf<String, String>()
    private val visibleText = StringBuilder()
    private var inputTokens: Long? = null
    private var outputTokens: Long? = null
    private var cachedTokens: Long? = null
    private var finishReason: String? = null
    private var emittedFinal = false

    fun accept(payload: String): StreamChunk? {
        val root = runCatching { ProviderJson.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw ProviderProtocolException("Provider returned invalid Responses streaming JSON", it) }
        return when (val type = root.string("type")) {
            "response.output_text.delta" -> root.string("delta").orEmpty().let { delta ->
                visibleText.append(delta)
                StreamChunk(text = delta)
            }
            "response.reasoning_summary_text.delta",
            "response.reasoning_text.delta" -> StreamChunk(reasoning = root.string("delta").orEmpty())
            "response.output_text.annotation.added" -> {
                collectCitations(root.obj("annotation"))
                null
            }
            "response.output_item.added", "response.output_item.done" -> {
                val index = root.long("output_index")?.toInt() ?: outputItems.size
                val item = root.obj("item") ?: return null
                outputItems[index] = item
                collectCitations(item)
                when (item.string("type")) {
                    "function_call" -> {
                        val call = calls.getOrPut(index) { CallAccumulator() }
                        call.read(item)
                        StreamChunk(toolCallProgress = listOf(call.progress(index, complete = type.endsWith(".done"))))
                    }
                    "web_search_call", "x_search_call" -> StreamChunk(
                        toolCallProgress = listOf(searchProgress(root, item, index, complete = type.endsWith(".done"))),
                    )
                    else -> null
                }
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
                root.string("arguments")?.let(call::replaceArguments)
                StreamChunk(toolCallProgress = listOf(call.progress(index, complete = true)))
            }
            "response.web_search_call.in_progress",
            "response.web_search_call.searching",
            "response.web_search_call.completed",
            "response.x_search_call.in_progress",
            "response.x_search_call.searching",
            "response.x_search_call.completed" -> {
                val index = root.long("output_index")?.toInt() ?: searchIndex(root.string("item_id").orEmpty())
                StreamChunk(
                    toolCallProgress = listOf(
                        searchProgress(root, root.obj("item"), index, complete = type.endsWith(".completed")),
                    ),
                )
            }
            "response.completed", "response.incomplete" -> {
                readCompleted(root.obj("response"))
                completeChunk()
            }
            "response.failed" -> {
                val response = root.obj("response")
                val message = response?.obj("error")?.string("message")
                    ?: response?.string("status_details")
                    ?: "Provider response failed"
                throw ProviderProtocolException(message)
            }
            "error" -> throw ProviderProtocolException(
                root.obj("error")?.string("message") ?: root.string("message") ?: "Provider streaming error",
            )
            else -> null
        }
    }

    private fun searchProgress(
        event: JsonObject,
        item: JsonObject?,
        index: Int,
        complete: Boolean,
    ): NativeToolCallProgress {
        val id = event.string("item_id")
            ?: item?.string("id")
            ?: "server-web-search-$index"
        val action = event.obj("action") ?: item?.obj("action")
        val query = action?.string("query")
            ?: event.string("query")
            ?: item?.obj("input")?.string("query")
        return NativeToolCallProgress(
            index = index,
            id = id,
            name = "web_search",
            argumentsJson = buildJsonObject {
                if (!query.isNullOrBlank()) put("query", JsonPrimitive(query))
            }.toString(),
            complete = complete,
        )
    }

    private fun searchIndex(id: String): Int = searchIndexes.getOrPut(id.ifBlank { "search-${searchIndexes.size}" }) {
        (outputItems.keys + calls.keys + searchIndexes.values).maxOrNull()?.plus(1) ?: 0
    }

    private fun readCompleted(response: JsonObject?) {
        if (response == null) return
        response.array("output")?.forEachIndexed { index, element ->
            val item = element as? JsonObject ?: return@forEachIndexed
            outputItems[index] = item
            collectCitations(item)
            if (item.string("type") == "function_call") {
                calls.getOrPut(index) { CallAccumulator() }.read(item)
            }
        }
        collectCitations(response)
        response.array("citations")?.forEach { citation ->
            val url = citation.jsonPrimitive.contentOrNull
            if (!url.isNullOrBlank()) citations.putIfAbsent(url, url)
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

    private fun collectCitations(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach(::collectCitations)
            is JsonObject -> {
                val nested = element.obj("url_citation")
                val type = element.string("type").orEmpty()
                val url = nested?.string("url") ?: element.string("url")
                val title = nested?.string("title") ?: element.string("title")
                if (!url.isNullOrBlank() && url.startsWith("http") &&
                    (type.contains("citation") || type.contains("search_result") || nested != null)
                ) {
                    citations.putIfAbsent(url, title?.takeIf(String::isNotBlank) ?: url)
                }
                element.values.forEach(::collectCitations)
            }
            else -> Unit
        }
    }

    private fun completeChunk(): StreamChunk {
        emittedFinal = true
        return StreamChunk(
            text = if (calls.isEmpty()) sourceTail() else "",
            toolCallProgress = calls.map { (index, call) -> call.progress(index, complete = true) },
            toolCalls = calls.values.map(CallAccumulator::complete),
            nativeProviderPayloadJson = JsonArray(outputItems.values.toList()).toString(),
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cachedInputTokens = cachedTokens,
            finishReason = finishReason,
        )
    }

    private fun sourceTail(): String {
        val visible = visibleText.toString()
        val entries = citations.entries
            .filterNot { (url, _) -> visible.contains(url) }
            .take(MAX_VISIBLE_SOURCES)
        if (entries.isEmpty()) return ""
        return buildString {
            append("\n\n### Sources\n")
            entries.forEach { (url, rawTitle) ->
                val title = rawTitle.replace('\n', ' ').replace("[", "\\[").replace("]", "\\]").take(180)
                append("- [").append(title).append("](").append(url).append(")\n")
            }
        }
    }

    fun finalChunk(): StreamChunk? {
        if (emittedFinal || (outputItems.isEmpty() && calls.isEmpty() && citations.isEmpty())) return null
        return completeChunk()
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
            arguments.clear()
            arguments.append(value)
        }

        fun progress(index: Int, complete: Boolean = false) = NativeToolCallProgress(
            index = index,
            id = callId.ifBlank { itemId },
            name = name,
            argumentsJson = arguments.toString(),
            complete = complete,
        )

        fun complete(): NativeToolCall = NativeToolCall(
            id = callId.ifBlank { itemId.ifBlank { "call_${name.hashCode().toUInt().toString(16)}" } },
            name = name,
            argumentsJson = arguments.toString().ifBlank { "{}" },
        )
    }

    private companion object {
        const val MAX_VISIBLE_SOURCES = 12
    }
}

private val app.xylune.chat.data.ThinkingEffort.responsesValue: String
    get() = when (this) {
        app.xylune.chat.data.ThinkingEffort.MINIMAL -> "minimal"
        app.xylune.chat.data.ThinkingEffort.LOW -> "low"
        app.xylune.chat.data.ThinkingEffort.MEDIUM -> "medium"
        app.xylune.chat.data.ThinkingEffort.HIGH -> "high"
        app.xylune.chat.data.ThinkingEffort.XHIGH,
        app.xylune.chat.data.ThinkingEffort.MAX -> "xhigh"
    }
