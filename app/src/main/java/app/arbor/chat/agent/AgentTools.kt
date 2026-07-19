package app.arbor.chat.agent

import android.text.Html
import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.chat.ChatRepository
import app.arbor.chat.sandbox.PythonSandbox
import app.arbor.chat.sandbox.UbuntuRuntime
import app.arbor.chat.files.AttachmentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns
import okhttp3.ResponseBody
import java.net.URLDecoder
import java.net.URI
import java.net.InetAddress
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable
data class AgentToolRequest(
    val type: String,
    val query: String? = null,
    val code: String? = null,
    val url: String? = null,
    val command: String? = null,
    val path: String? = null,
    val caption: String? = null,
    val timeoutSeconds: Int? = null,
)

@Serializable
data class ToolTraceEvent(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val label: String,
    val status: String,
    val input: String = "",
    val output: String = "",
    val providerCallId: String = "",
    val argumentsJson: String = "",
    val startedAt: Long,
    val finishedAt: Long? = null,
)

@Serializable
data class MessageTimelineEvent(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val content: String = "",
    val label: String = "",
    val status: String = "complete",
    val input: String = "",
    val output: String = "",
    val providerCallId: String = "",
    val argumentsJson: String = "",
    val startedAt: Long,
    val finishedAt: Long? = null,
)

data class TimelineRun(val working: Boolean, val events: List<MessageTimelineEvent>)

fun groupOrderedTimeline(events: List<MessageTimelineEvent>): List<TimelineRun> {
    if (events.isEmpty()) return emptyList()
    val result = mutableListOf<TimelineRun>()
    var currentWorking = events.first().kind !in setOf("text", "file")
    var current = mutableListOf<MessageTimelineEvent>()
    events.forEach { event ->
        val working = event.kind !in setOf("text", "file")
        if (current.isNotEmpty() && working != currentWorking) {
            result += TimelineRun(currentWorking, current.toList())
            current = mutableListOf()
        }
        currentWorking = working
        current += event
    }
    if (current.isNotEmpty()) result += TimelineRun(currentWorking, current.toList())
    return result
}


data class AgentToolOutcome(val output: String, val files: List<String> = emptyList())

@Serializable
private data class SentFileResult(val path: String, val name: String, val sizeBytes: Long, val caption: String)

class AgentTools(
    private val python: PythonSandbox,
    private val ubuntu: UbuntuRuntime,
    private val repository: ChatRepository,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(PublicOnlyDns)
        .build(),
) {
    private val json = Json { encodeDefaults = true }

    suspend fun execute(conversationId: String, request: AgentToolRequest): AgentToolOutcome {
        // Permissions are intentionally re-read immediately before every side effect.
        val conversation = requireNotNull(repository.conversationNow(conversationId)) { "Conversation no longer exists" }
        return when (request.type.lowercase()) {
        "web_search", "search" -> {
            check(conversation.webSearchEnabled) { "Web search is disabled for this conversation." }
            AgentToolOutcome(search(requireNotNull(request.query) { "Search query is missing" }))
        }
        "web_fetch", "fetch" -> {
            check(conversation.webSearchEnabled) { "Web access is disabled for this conversation." }
            AgentToolOutcome(fetch(requireNotNull(request.url) { "Fetch URL is missing" }))
        }
        "python", "python_exec" -> {
            check(conversation.agentPythonEnabled) { "Agent Python is disabled for this conversation." }
            val code = requireNotNull(request.code) { "Python code is missing" }
            val lint = ubuntu.lintPython(conversation.id, code)
            require(!lint.hasErrors) { "Python lint failed: ${lint.diagnostics.joinToString { it.message }}" }
            val result = ubuntu.executePython(conversation.id, code, (request.timeoutSeconds ?: DEFAULT_PYTHON_SECONDS).coerceIn(1, 600))
            AgentToolOutcome(json.encodeToString(result))
        }
        "ubuntu", "ubuntu_exec", "linux", "linux_exec", "shell" -> {
            check(conversation.agentUbuntuEnabled) { "Agent Linux tools are disabled for this conversation." }
            val command = (request.command ?: request.code).orEmpty().trim()
            require(command.isNotBlank()) { "Linux command is missing" }
            require(!PACKAGE_COMMAND.containsMatchIn(command)) {
                "Package-manager commands require a visible ubuntu-packages request and approval."
            }
            val lint = ubuntu.lintShell(conversation.id, command)
            require(!lint.hasErrors) { "Shell lint failed: ${lint.diagnostics.joinToString { it.message }}" }
            val result = ubuntu.execute(conversation.id, command, (request.timeoutSeconds ?: DEFAULT_LINUX_SECONDS).coerceIn(1, 900))
            AgentToolOutcome(json.encodeToString(UbuntuToolResult(
                result.stdout, result.stderr, result.exitCode, result.files, result.elapsedMs, result.timedOut,
            )))
        }
        "send_file", "file_send" -> {
            val relative = requireNotNull(request.path) { "File path is missing" }.trim().removePrefix("/workspace/")
            require(relative.isNotBlank() && !File(relative).isAbsolute) { "Use a path inside the conversation workspace" }
            val workspace = ubuntu.workspace(conversation.id).canonicalFile
            val source = File(workspace, relative).canonicalFile
            require(source.isFile && source.path.startsWith(workspace.path + File.separator)) {
                "The requested file does not exist in this conversation workspace"
            }
            require(source.length() <= AttachmentStore.MAX_FILE_BYTES) { "Returned files are limited to 64 MB" }
            AgentToolOutcome(
                json.encodeToString(SentFileResult(relative, source.name, source.length(), request.caption?.take(500).orEmpty())),
                listOf(relative),
            )
        }
        else -> error("Unknown Arbor tool: ${request.type}")
        }
    }

    private suspend fun search(rawQuery: String): String = withContext(Dispatchers.IO) {
        val query = rawQuery.trim().take(500)
        require(query.isNotBlank()) { "Search query is empty" }
        val url = "https://html.duckduckgo.com/html/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) Arbor/0.12.0")
            .header("Accept", "text/html")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Search failed with HTTP ${response.code}" }
            val html = response.body?.readLimited(2_000_000).orEmpty()
            val results = parseDuckDuckGo(html).take(8)
            if (results.isEmpty()) "No search results were returned for: $query"
            else json.encodeToString(WebSearchResponse(query, results))
        }
    }

    private suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        var url = validatePublicUrl(rawUrl)
        repeat(4) { redirectCount ->
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Arbor/0.12.0")
                .header("Accept", "text/html,text/plain,application/json;q=0.9,*/*;q=0.2")
                .build()
            client.newBuilder().followRedirects(false).build().newCall(request).execute().use { response ->
                if (response.code in 300..399) {
                    val location = response.header("Location") ?: error("Redirect has no Location header")
                    url = validatePublicUrl(response.request.url.resolve(location)?.toString() ?: location)
                    return@repeat
                }
                check(response.isSuccessful) { "Fetch failed with HTTP ${response.code}" }
                val contentType = response.header("Content-Type").orEmpty()
                val raw = response.body?.readLimited(2_000_000).orEmpty()
                val text = if ("html" in contentType || raw.contains("<html", ignoreCase = true)) plain(raw) else raw
                return@withContext json.encodeToString(WebFetchResponse(url, contentType, text.take(60_000)))
            }
            if (redirectCount == 3) error("Too many redirects")
        }
        error("Unable to fetch URL")
    }

    private fun validatePublicUrl(raw: String): String {
        val uri = URI(raw.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Only absolute HTTPS URLs can be fetched" }
        val addresses = InetAddress.getAllByName(uri.host)
        require(addresses.isNotEmpty() && addresses.none { address ->
            address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
                address.isSiteLocalAddress || address.isMulticastAddress
        }) { "Local and private network addresses are blocked from web fetch" }
        return uri.toString()
    }

    private fun parseDuckDuckGo(html: String): List<WebSearchResult> {
        val anchor = Regex("<a[^>]*class=\\\"[^\\\"]*result__a[^\\\"]*\\\"[^>]*href=\\\"([^\\\"]+)\\\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)
        val snippet = Regex("class=\\\"[^\\\"]*result__snippet[^\\\"]*\\\"[^>]*>([\\s\\S]*?)</(?:a|div)>", RegexOption.IGNORE_CASE)
        return anchor.findAll(html).map { match ->
            val windowEnd = minOf(html.length, match.range.last + 4_000)
            val nearby = html.substring(match.range.last + 1, windowEnd)
            WebSearchResult(
                title = plain(match.groupValues[2]),
                url = cleanUrl(match.groupValues[1]),
                snippet = snippet.find(nearby)?.groupValues?.get(1)?.let(::plain).orEmpty(),
            )
        }.filter { it.title.isNotBlank() && it.url.startsWith("http") }.distinctBy { it.url }.toList()
    }

    private fun cleanUrl(value: String): String {
        val decoded = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        val target = Regex("[?&]uddg=([^&]+)").find(decoded)?.groupValues?.get(1)
        return if (target == null) decoded else runCatching { URLDecoder.decode(target, "UTF-8") }.getOrDefault(decoded)
    }

    private fun plain(value: String): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("\\s+"), " ").trim()

    companion object {
        private const val DEFAULT_PYTHON_SECONDS = 45
        private const val DEFAULT_LINUX_SECONDS = 60
        private val PACKAGE_COMMAND = Regex("(?i)(^|[;&|()\\n]\\s*|\\bsudo\\s+)(apt|apt-get|aptitude|dpkg|snap|apk|rpm|dnf|yum|pacman|zypper|pip3?|python(?:3)?\\s+-m\\s+pip)\\b")
    }
}

private object PublicOnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        require(addresses.isNotEmpty() && addresses.none(::isPrivateAddress)) { "Local and private network addresses are blocked" }
        return addresses
    }
}

private fun isPrivateAddress(address: InetAddress): Boolean = address.isAnyLocalAddress || address.isLoopbackAddress ||
    address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress

private fun ResponseBody.readLimited(limit: Long): String {
    val source = source()
    source.request(limit + 1)
    val count = minOf(source.buffer.size, limit)
    return source.buffer.readUtf8(count)
}

@Serializable
private data class UbuntuToolResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val files: List<String>,
    val elapsedMs: Long,
    val timedOut: Boolean,
)

@Serializable
internal data class WebSearchResponse(val query: String, val results: List<WebSearchResult>)

@Serializable
internal data class WebSearchResult(val title: String, val url: String, val snippet: String)

@Serializable
internal data class WebFetchResponse(val url: String, val contentType: String, val text: String)
