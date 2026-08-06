package app.xylune.chat.agent

import android.text.Html
import app.xylune.chat.security.SecureStore
import app.xylune.chat.settings.AppPreferences
import app.xylune.chat.settings.WebSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class WebSearchClient(
    private val preferences: AppPreferences,
    private val secureStore: SecureStore,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .dns(SearchPublicOnlyDns)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun search(rawQuery: String): String = withContext(Dispatchers.IO) {
        val query = rawQuery.trim().take(500)
        require(query.isNotBlank()) { "Search query is empty" }
        val settings = preferences.webSearchSettings.value.normalized()
        val results = when (settings.engine) {
            WebSearchEngine.DUCKDUCKGO -> duckDuckGo(query, settings.maxResults)
            WebSearchEngine.BRAVE -> brave(query, settings.maxResults)
            WebSearchEngine.TAVILY -> tavily(query, settings.maxResults)
            WebSearchEngine.SERPER -> serper(query, settings.maxResults)
            WebSearchEngine.SEARXNG -> searxng(query, settings.maxResults, settings.searxngEndpoint)
        }
        json.encodeToString(
            WebSearchResponse(
                query = query,
                engine = settings.engine.title,
                results = results.distinctBy(WebSearchResult::url).take(settings.maxResults),
            ),
        )
    }

    private fun duckDuckGo(query: String, limit: Int): List<WebSearchResult> {
        val url = "https://html.duckduckgo.com/html/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val html = executeText(
            Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html")
                .build(),
        )
        val anchor = Regex(
    """<a[^>]*class="[^"]*result__a[^"]*"[^>]*href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
    RegexOption.IGNORE_CASE,
)
val snippet = Regex(
    """class="[^"]*result__snippet[^"]*"[^>]*>([\s\S]*?)</(?:a|div)>""",
    RegexOption.IGNORE_CASE,
)
        return anchor.findAll(html).map { match ->
            val windowEnd = minOf(html.length, match.range.last + 4_000)
            val nearby = html.substring(match.range.last + 1, windowEnd)
            WebSearchResult(
                title = plain(match.groupValues[2]),
                url = cleanDuckDuckGoUrl(match.groupValues[1]),
                snippet = snippet.find(nearby)?.groupValues?.get(1)?.let(::plain).orEmpty(),
            )
        }.filter { it.title.isNotBlank() && it.url.startsWith("https://") }
            .distinctBy(WebSearchResult::url)
            .take(limit)
            .toList()
    }

    private fun brave(query: String, limit: Int): List<WebSearchResult> {
        val key = requiredKey(WebSearchEngine.BRAVE)
        val url = "https://api.search.brave.com/res/v1/web/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("count", limit.toString())
            .addQueryParameter("safesearch", "moderate")
            .build()
        val root = parseObject(
            executeText(
                Request.Builder().url(url)
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", key)
                    .header("User-Agent", USER_AGENT)
                    .build(),
            ),
        )
        return root.obj("web")?.array("results").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            result(item.string("title"), item.string("url"), item.string("description"))
        }.take(limit)
    }

    private fun tavily(query: String, limit: Int): List<WebSearchResult> {
        val key = requiredKey(WebSearchEngine.TAVILY)
        val body = buildJsonObject {
            put("api_key", JsonPrimitive(key))
            put("query", JsonPrimitive(query))
            put("search_depth", JsonPrimitive("advanced"))
            put("max_results", JsonPrimitive(limit))
            put("include_answer", JsonPrimitive(false))
            put("include_raw_content", JsonPrimitive(false))
        }
        val root = parseObject(
            executeText(
                Request.Builder().url("https://api.tavily.com/search")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            ),
        )
        return root.array("results").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            result(item.string("title"), item.string("url"), item.string("content"))
        }.take(limit)
    }

    private fun serper(query: String, limit: Int): List<WebSearchResult> {
        val key = requiredKey(WebSearchEngine.SERPER)
        val body = buildJsonObject {
            put("q", JsonPrimitive(query))
            put("num", JsonPrimitive(limit))
        }
        val root = parseObject(
            executeText(
                Request.Builder().url("https://google.serper.dev/search")
                    .header("X-API-KEY", key)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            ),
        )
        return root.array("organic").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            result(item.string("title"), item.string("link"), item.string("snippet"))
        }.take(limit)
    }

    private fun searxng(query: String, limit: Int, endpoint: String): List<WebSearchResult> {
        val base = endpoint.trim().trimEnd('/')
        require(base.isNotBlank()) { "Add a SearXNG endpoint in Settings → Search & web." }
        validatePublicHttps(base)
        val url = (if (base.endsWith("/search")) base else "$base/search").toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "json")
            .build()
        val root = parseObject(
            executeText(
                Request.Builder().url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .build(),
            ),
        )
        return root.array("results").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            result(item.string("title"), item.string("url"), item.string("content"))
        }.take(limit)
    }

    private fun requiredKey(engine: WebSearchEngine): String =
        secureStore.searchApiKey(engine.name).also {
            require(it.isNotBlank()) { "Add a ${engine.title} API key in Settings → Search & web." }
        }

    private fun executeText(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.readLimited(2_000_000).orEmpty()
            check(response.isSuccessful) {
                "Search failed with HTTP ${response.code}${body.take(300).takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
            }
            return body
        }
    }

    private fun parseObject(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw).jsonObject }
            .getOrElse { error("Search engine returned invalid JSON: ${it.message}") }

    private fun result(title: String?, url: String?, snippet: String?): WebSearchResult? {
        val safeUrl = url?.trim().orEmpty()
        if (!safeUrl.startsWith("https://")) return null
        return WebSearchResult(
            title = title.orEmpty().trim().ifBlank { safeUrl },
            url = safeUrl,
            snippet = snippet.orEmpty().trim(),
        )
    }

    private fun cleanDuckDuckGoUrl(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        val target = Regex("""[?&]uddg=([^&]+)""").find(decoded)?.groupValues?.get(1)
        return if (target == null) decoded else runCatching {
            URLDecoder.decode(target, Charsets.UTF_8.name())
        }.getOrDefault(decoded)
    }

    private fun plain(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("""\s+"""), " ").trim()

    private fun validatePublicHttps(raw: String) {
        val uri = URI(raw)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) {
            "SearXNG must use an absolute public HTTPS URL."
        }
        require(InetAddress.getAllByName(uri.host).none(::isPrivateSearchAddress)) {
            "Local and private SearXNG addresses are blocked."
        }
    }

    private companion object {
        const val USER_AGENT = "Mozilla/5.0 (Android) Xylune/0.24.7"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private object SearchPublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).also { addresses ->
            require(addresses.isNotEmpty() && addresses.none(::isPrivateSearchAddress)) {
                "Local and private search endpoints are blocked."
            }
        }
}

private fun isPrivateSearchAddress(address: InetAddress): Boolean =
    address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
        address.isSiteLocalAddress || address.isMulticastAddress

private fun ResponseBody.readLimited(limit: Long): String {
    val source = source()
    source.request(limit + 1)
    return source.buffer.readUtf8(minOf(source.buffer.size, limit))
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun JsonObject.obj(name: String): JsonObject? =
    this[name] as? JsonObject

private fun JsonObject.array(name: String): JsonArray? =
    this[name] as? JsonArray
