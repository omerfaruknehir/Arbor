package app.xylune.chat.provider

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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Uses a provider's server-side web tools when the provider/model supports
 * them, while preserving Xylune's existing client-side search as a clean
 * fallback for older models, disabled accounts and incompatible endpoints.
 */
internal class NativeWebSearchProvider(
    private val delegate: ChatProvider,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
) : ChatProvider {
    override suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        val mode = NativeWebSearch.mode(request)
        if (mode == NativeWebSearchMode.NONE || mode == NativeWebSearchMode.RESPONSES && request.apiKey.isBlank()) {
            if (request.webSearchRoute == app.xylune.chat.settings.WebSearchRoute.NATIVE_ONLY &&
                NativeWebSearch.requested(request)
            ) {
                throw ProviderProtocolException(
                    "${request.provider.displayName} / ${request.model.displayName} does not expose native web search.",
                )
            }
            delegate.stream(request, emit)
            return
        }

        var emittedNativeChunk = false
        val guardedEmit: suspend (StreamChunk) -> Unit = { chunk ->
            emittedNativeChunk = true
            emit(chunk)
        }
        try {
            withContext(Dispatchers.IO) {
                when (mode) {
                    NativeWebSearchMode.RESPONSES -> ResponsesApiTransport(client).stream(request, guardedEmit)
                    NativeWebSearchMode.ANTHROPIC -> AnthropicNativeWebSearchTransport(client).stream(request, guardedEmit)
                    NativeWebSearchMode.GEMINI -> GeminiNativeWebSearchTransport(client).stream(request, guardedEmit)
                    NativeWebSearchMode.NONE -> Unit
                }
            }
        } catch (error: ProviderHttpException) {
            if (emittedNativeChunk || error.status !in FALLBACK_HTTP_CODES ||
                request.webSearchRoute == app.xylune.chat.settings.WebSearchRoute.NATIVE_ONLY
            ) throw error
            emit(StreamChunk(resetCurrentAttempt = true))
            delegate.stream(request, emit)
        }
    }

    private companion object {
        val FALLBACK_HTTP_CODES = setOf(400, 404, 405, 409, 422, 501)
    }
}

private class AnthropicNativeWebSearchTransport(private val client: OkHttpClient) {
    private val provider = AnthropicProvider(client)

    suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
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

        val providerState = AnthropicProvider.AnthropicStreamState()
        val searchState = AnthropicSearchStreamState(NativeWebSearch.nativeSourceLabel(request))
        client.newCall(httpRequest).useCancellable { response ->
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            }
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                searchState.accept(payload)?.let { emit(it) }
                provider.parseChunk(payload, providerState)?.let { emit(it) }
            }
        }
        val final = providerState.finalChunk()
        final?.let { emit(it) }
        if (final?.toolCalls.orEmpty().isEmpty()) {
            searchState.sourceTail().takeIf(String::isNotBlank)?.let { emit(StreamChunk(text = it)) }
        }
    }

    internal fun buildRequestBody(request: ChatRequest): JsonObject {
        val clientRequest = request.copy(tools = NativeWebSearch.clientTools(request))
        val base = provider.buildRequestBody(clientRequest)
        val clientTools = (base["tools"] as? JsonArray).orEmpty()
        val tools = buildJsonArray {
            add(buildJsonObject {
                put("type", JsonPrimitive("web_search_20250305"))
                put("name", JsonPrimitive("web_search"))
                put("max_uses", JsonPrimitive(request.webSearchMaxResults.coerceIn(3, 20)))
            })
            if (NativeWebSearch.requestedFetch(request)) add(buildJsonObject {
                put("type", JsonPrimitive("web_fetch_20250910"))
                put("name", JsonPrimitive("web_fetch"))
                put("max_uses", JsonPrimitive(request.webSearchMaxResults.coerceIn(3, 20)))
            })
            clientTools.forEach(::add)
        }
        return JsonObject(base + mapOf(
            "tools" to tools,
            "tool_choice" to buildJsonObject {
                put("type", JsonPrimitive("auto"))
                put("disable_parallel_tool_use", JsonPrimitive(true))
            },
        ))
    }

    private class AnthropicSearchStreamState(
        private val sourceLabel: String,
    ) {
        private val active = linkedMapOf<Int, SearchCall>()
        private val citations = linkedMapOf<String, String>()

        fun accept(payload: String): StreamChunk? {
            val root = runCatching { ProviderJson.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return null
            collectCitations(root)
            return when (root.string("type")) {
                "content_block_start" -> {
                    val index = root.long("index")?.toInt() ?: active.size
                    val block = root.obj("content_block") ?: return null
                    if (block.string("type") != "server_tool_use") return null
                    val call = SearchCall(
                        id = block.string("id").orEmpty().ifBlank { "anthropic-server-tool-$index" },
                        name = block.string("name").orEmpty().ifBlank { "web_search" },
                    )
                    block["input"]?.toString()?.takeIf { it != "{}" }?.let(call.arguments::append)
                    active[index] = call
                    StreamChunk(toolCallProgress = listOf(call.progress(index)))
                }
                "content_block_delta" -> {
                    val index = root.long("index")?.toInt() ?: return null
                    val delta = root.obj("delta") ?: return null
                    if (delta.string("type") != "input_json_delta") return null
                    val call = active[index] ?: return null
                    call.arguments.append(delta.string("partial_json").orEmpty())
                    StreamChunk(toolCallProgress = listOf(call.progress(index)))
                }
                "content_block_stop" -> {
                    val index = root.long("index")?.toInt() ?: return null
                    active[index]?.let { call ->
                        StreamChunk(toolCallProgress = listOf(call.progress(index, complete = true)))
                    }
                }
                else -> null
            }
        }

        fun sourceTail(): String = markdownSources(citations)

        private fun collectCitations(element: JsonElement?) {
            when (element) {
                is JsonArray -> element.forEach(::collectCitations)
                is JsonObject -> {
                    val type = element.string("type").orEmpty()
                    val url = element.string("url")
                    val title = element.string("title")
                    if (!url.isNullOrBlank() && url.startsWith("http") &&
                        (type.contains("search_result") || type.contains("citation") || type.contains("fetch_result"))
                    ) {
                        citations.putIfAbsent(url, title?.takeIf(String::isNotBlank) ?: url)
                    }
                    element.values.forEach(::collectCitations)
                }
                else -> Unit
            }
        }

        private data class SearchCall(
            val id: String,
            val name: String,
            val arguments: StringBuilder = StringBuilder(),
        ) {
            fun progress(index: Int, complete: Boolean = false) = NativeToolCallProgress(
                index = index,
                id = id,
                name = "native_web_search",
                argumentsJson = buildJsonObject {
                    val parsed = runCatching {
                        ProviderJson.parseToJsonElement(arguments.toString().ifBlank { "{}" }).jsonObject
                    }.getOrNull()
                    parsed?.string("query")?.takeIf(String::isNotBlank)?.let {
                        put("query", JsonPrimitive(it))
                    }
                    put("source", JsonPrimitive("Anthropic native search"))
                }.toString(),
                complete = complete,
            )
        }
    }
}

private class GeminiNativeWebSearchTransport(private val client: OkHttpClient) {
    private val provider = GeminiProvider(client)

    suspend fun stream(request: ChatRequest, emit: suspend (StreamChunk) -> Unit) {
        val body = buildRequestBody(request)
        val url = request.provider.baseUrl.trimEnd('/') + "/models/${request.model.modelId}:streamGenerateContent?alt=sse"
        val httpRequest = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .also { builder -> if (request.apiKey.isNotBlank()) builder.header("x-goog-api-key", request.apiKey) }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .also { builder -> request.customHeaders.forEach(builder::header) }
            .build()

        val providerState = GeminiProvider.GeminiStreamState()
        val searchState = GeminiSearchStreamState(NativeWebSearch.nativeSourceLabel(request))
        client.newCall(httpRequest).useCancellable { response ->
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, response.body?.readErrorSnippet().orEmpty())
            }
            val source = response.body?.source() ?: error("Provider returned an empty response")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                val root = runCatching { ProviderJson.parseToJsonElement(payload).jsonObject }.getOrNull()
                searchState.accept(root).forEach { emit(it) }
                provider.parseChunks(payload, providerState).forEach { emit(it) }
            }
        }
        val final = providerState.finalChunk()
        final?.let { emit(it) }
        if (final?.toolCalls.orEmpty().isEmpty()) {
            searchState.sourceTail().takeIf(String::isNotBlank)?.let { emit(StreamChunk(text = it)) }
        }
    }

    internal fun buildRequestBody(request: ChatRequest): JsonObject {
        val clientRequest = request.copy(tools = NativeWebSearch.clientTools(request))
        val base = provider.buildRequestBody(clientRequest)
        val existing = (base["tools"] as? JsonArray).orEmpty()
        val tools = buildJsonArray {
            add(buildJsonObject { put("googleSearch", buildJsonObject {}) })
            existing.forEach(::add)
        }
        return JsonObject(base + ("tools" to tools))
    }

    private class GeminiSearchStreamState(
        private val sourceLabel: String,
    ) {
        private val queries = linkedSetOf<String>()
        private val citations = linkedMapOf<String, String>()

        fun accept(root: JsonObject?): List<StreamChunk> {
            if (root == null) return emptyList()
            val chunks = mutableListOf<StreamChunk>()
            root.array("candidates").orEmpty().forEach { candidateElement ->
                val grounding = (candidateElement as? JsonObject)?.obj("groundingMetadata") ?: return@forEach
                grounding.array("groundingChunks").orEmpty().forEach { chunkElement ->
                    val web = (chunkElement as? JsonObject)?.obj("web") ?: return@forEach
                    val url = web.string("uri") ?: web.string("url")
                    if (!url.isNullOrBlank()) citations.putIfAbsent(
                        url,
                        web.string("title")?.takeIf(String::isNotBlank) ?: url,
                    )
                }
                grounding.array("webSearchQueries").orEmpty().forEach { queryElement ->
                    val query = runCatching { queryElement.jsonPrimitive.contentOrNull }.getOrNull()
                        ?.takeIf(String::isNotBlank) ?: return@forEach
                    if (!queries.add(query)) return@forEach
                    val index = queries.size - 1
                    chunks += StreamChunk(
                        toolCallProgress = listOf(
                            NativeToolCallProgress(
                                index = 10_000 + index,
                                id = "gemini-search-${query.hashCode().toUInt().toString(16)}",
                                name = "native_web_search",
                                argumentsJson = buildJsonObject {
                                    put("query", JsonPrimitive(query))
                                    put("source", JsonPrimitive(sourceLabel))
                                }.toString(),
                                complete = true,
                            ),
                        ),
                    )
                }
            }
            return chunks
        }

        fun sourceTail(): String = markdownSources(citations)
    }
}

private fun markdownSources(citations: Map<String, String>): String {
    if (citations.isEmpty()) return ""
    return buildString {
        append("\n\n")
        citations.entries.take(12).forEachIndexed { index, (url, rawTitle) ->
            if (index > 0) append(' ')
            val title = rawTitle.replace('\n', ' ')
                .replace('|', '·')
                .replace('[', '(')
                .replace(']', ')')
                .take(180)
            append("[[").append(title.ifBlank { url }).append('|').append(url).append("]]" )
        }
        append('\n')
    }
}
