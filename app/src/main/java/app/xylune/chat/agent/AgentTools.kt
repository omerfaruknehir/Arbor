package app.xylune.chat.agent

import android.text.Html
import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.chat.ChatRepository
import app.xylune.chat.sandbox.PythonSandbox
import app.xylune.chat.sandbox.UbuntuRuntime
import app.xylune.chat.sandbox.RunRecordStore
import app.xylune.chat.sandbox.ScriptRuntime
import app.xylune.chat.sandbox.ExecutionProgress
import app.xylune.chat.files.AttachmentStore
import app.xylune.chat.generated.GeneratedBlockCompiler
import app.xylune.chat.generated.GeneratedBlockType
import app.xylune.chat.generated.WidgetCompilerToolProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
    val source: String? = null,
    val url: String? = null,
    val command: String? = null,
    val path: String? = null,
    val caption: String? = null,
    val timeoutSeconds: Int? = null,
    val startLine: Int? = null,
    val endLine: Int? = null,
    val maxBytes: Int? = null,
    val unifiedDiff: String? = null,
    val expectedSha256: String? = null,
    val runId: String? = null,
    val memoryId: String? = null,
    val memoryText: String? = null,
    val memoryCategory: String? = null,
    val memoryQuery: String? = null,
    val memoryIncludeDisabled: Boolean? = null,
    val memoryLimit: Int? = null,
    val args: List<String> = emptyList(),
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
    /**
     * Text/reasoning events created by current Xylune builds reference the
     * aggregate message field instead of duplicating a growing string inside
     * timelineJson. A null sourceEnd marks the currently streaming segment.
     */
    val sourceStart: Int = -1,
    val sourceEnd: Int? = null,
)

fun materializeTimelineContent(
    events: List<MessageTimelineEvent>,
    content: String,
    reasoning: String,
): List<MessageTimelineEvent> {
    val materialized = events.mapIndexed { index, event ->
        if (event.content.isNotEmpty() || event.sourceStart < 0 || event.kind !in setOf("text", "reasoning")) {
            event
        } else {
            val source = if (event.kind == "reasoning") reasoning else content
            val start = event.sourceStart.coerceIn(0, source.length)
            val nextStart = events.asSequence()
                .drop(index + 1)
                .firstOrNull { it.kind == event.kind && it.sourceStart >= 0 }
                ?.sourceStart
            val end = (event.sourceEnd ?: nextStart ?: source.length).coerceIn(start, source.length)
            event.copy(content = source.substring(start, end))
        }
    }
    return coalesceStreamingTextFragments(materialized)
}

/**
 * Providers may emit reasoning and visible text in the same SSE event. Older
 * timeline code treated every field switch as a new visual block, so a provider
 * which repeated both fields produced one Markdown block per token. Within each
 * tool-free run, text and reasoning are aggregate streams: keep one event per
 * kind and concatenate the exact fragments without inserting whitespace.
 */
internal fun coalesceStreamingTextFragments(events: List<MessageTimelineEvent>): List<MessageTimelineEvent> {
    if (events.size < 2) return events
    val result = mutableListOf<MessageTimelineEvent>()
    val streamRun = mutableListOf<MessageTimelineEvent>()

    fun flushStreamRun() {
        if (streamRun.isEmpty()) return
        val byKind = linkedMapOf<String, MutableList<MessageTimelineEvent>>()
        streamRun.forEach { event -> byKind.getOrPut(event.kind) { mutableListOf() } += event }
        byKind.values.forEach { fragments ->
            val first = fragments.first()
            result += first.copy(
                content = buildString { fragments.forEach { append(it.content) } },
                finishedAt = if (fragments.any { it.finishedAt == null }) null else fragments.maxOfOrNull { it.finishedAt ?: it.startedAt },
                sourceStart = -1,
                sourceEnd = null,
            )
        }
        streamRun.clear()
    }

    events.forEach { event ->
        if (event.kind in setOf("text", "reasoning")) {
            streamRun += event
        } else {
            flushStreamRun()
            result += event
        }
    }
    flushStreamRun()
    return result
}

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


data class AgentToolOutcome(
    val output: String,
    val files: List<String> = emptyList(),
    val isError: Boolean = false,
)

@Serializable
private data class SentFileResult(val path: String, val name: String, val sizeBytes: Long, val caption: String)

class AgentTools(
    private val python: PythonSandbox,
    private val ubuntu: UbuntuRuntime,
    private val repository: ChatRepository,
    private val generatedBlockCompiler: GeneratedBlockCompiler,
    val runRecords: RunRecordStore = RunRecordStore(ubuntu::workspace),
    private val webSearchClient: WebSearchClient,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(PublicOnlyDns)
        .build(),
) {
    private val json = Json { encodeDefaults = true }

    suspend fun execute(
        conversationId: String,
        request: AgentToolRequest,
        onProgress: suspend (ExecutionProgress) -> Unit = {},
    ): AgentToolOutcome {
        // Permissions are intentionally re-read immediately before every side effect.
        val conversation = requireNotNull(repository.conversationNow(conversationId)) { "Conversation no longer exists" }
        return when (request.type.lowercase()) {
        "compile_widget", "widget_compile" -> {
            val source = requireNotNull(request.source) { "Widget source is missing" }
            val compilation = generatedBlockCompiler.compile(GeneratedBlockType.HOME_WIDGET, source)
            val result = WidgetCompilerToolProtocol.result(source, compilation)
            AgentToolOutcome(json.encodeToString(result), isError = !result.success)
        }
        "web_search", "search" -> {
            check(conversation.webSearchEnabled) { "Web search is disabled for this conversation." }
            AgentToolOutcome(webSearchClient.search(requireNotNull(request.query) { "Search query is missing" }))
        }
        "web_fetch", "fetch" -> {
            check(conversation.webSearchEnabled) { "Web access is disabled for this conversation." }
            AgentToolOutcome(fetch(requireNotNull(request.url) { "Fetch URL is missing" }))
        }
        "python", "python_exec" -> {
            check(conversation.agentPythonEnabled) { "Agent Python is disabled for this conversation." }
            val code = requireNotNull(request.code) { "Python code is missing" }
            val timeout = (request.timeoutSeconds ?: DEFAULT_PYTHON_SECONDS).coerceIn(1, 600)
            var metadata = runRecords.create(
                conversation.id, ScriptRuntime.PYTHON, code, "python", emptyList(), timeout,
                mapOf("python" to "bundled 3.12", "packages" to ".packages", "executionMode" to "embedded app process"),
            )
            metadata = runRecords.markStarted(metadata, timeout, emptyList())
            val result = executeStored(metadata, emptyList(), timeout, onProgress)
            AgentToolOutcome(json.encodeToString(result), isError = result.exitCode != 0 || result.timedOut || result.cancelled)
        }
        "ubuntu", "ubuntu_exec", "linux", "linux_exec", "shell" -> {
            check(conversation.agentUbuntuEnabled) { "Agent Linux tools are disabled for this conversation." }
            val command = (request.command ?: request.code).orEmpty().trim()
            require(command.isNotBlank()) { "Linux command is missing" }
            require(!PACKAGE_COMMAND.containsMatchIn(command)) {
                "Package-manager commands require a visible ubuntu-packages request and approval."
            }
            val timeout = (request.timeoutSeconds ?: DEFAULT_LINUX_SECONDS).coerceIn(1, 900)
            var metadata = runRecords.create(
                conversation.id, ScriptRuntime.LINUX, command, "sh", emptyList(), timeout,
                mapOf("distribution" to ubuntu.distribution.value.displayName, "executionMode" to "PRoot root"),
            )
            metadata = runRecords.markStarted(metadata, timeout, emptyList())
            val result = executeStored(metadata, emptyList(), timeout, onProgress)
            AgentToolOutcome(json.encodeToString(result), isError = result.exitCode != 0 || result.timedOut)
        }
        "workspace_read" -> {
            check(conversation.agentPythonEnabled || conversation.agentUbuntuEnabled) { "Workspace tools are disabled for this conversation." }
            AgentToolOutcome(json.encodeToString(runRecords.readWorkspace(
                conversation.id,
                requireNotNull(request.path) { "Workspace path is missing" },
                request.startLine,
                request.endLine,
                request.maxBytes,
            )))
        }
        "apply_patch" -> {
            check(conversation.agentPythonEnabled || conversation.agentUbuntuEnabled) { "Workspace tools are disabled for this conversation." }
            AgentToolOutcome(json.encodeToString(runRecords.applyPatch(
                conversation.id,
                requireNotNull(request.path) { "Workspace path is missing" },
                requireNotNull(request.unifiedDiff) { "unifiedDiff is missing" },
                requireNotNull(request.expectedSha256) { "expectedSha256 is missing" },
            )))
        }
        "rerun_script" -> {
            check(conversation.agentPythonEnabled || conversation.agentUbuntuEnabled) { "Workspace tools are disabled for this conversation." }
            var metadata = runRecords.load(conversation.id, requireNotNull(request.runId) { "runId is missing" })
            if (metadata.runtime == ScriptRuntime.PYTHON) check(conversation.agentPythonEnabled) { "Agent Python is disabled for this conversation." }
            if (metadata.runtime == ScriptRuntime.LINUX) check(conversation.agentUbuntuEnabled) { "Agent Linux tools are disabled for this conversation." }
            val maximum = if (metadata.runtime == ScriptRuntime.PYTHON) 600 else 900
            val timeout = (request.timeoutSeconds ?: metadata.timeoutSeconds).coerceIn(1, maximum)
            val args = request.args.ifEmpty { metadata.originalArgs }
            metadata = runRecords.markStarted(metadata, timeout, args)
            val result = executeStored(metadata, args, timeout, onProgress)
            AgentToolOutcome(json.encodeToString(result), isError = result.exitCode != 0 || result.timedOut || result.cancelled)
        }
        "memory_save" -> {
            val settings = repository.automationSettingsNow()
            check(settings.memoryEnabled) { "Memory is disabled in Xylune settings." }
            val result = repository.saveMemoryManaged(
                content = requireNotNull(request.memoryText) { "Memory text is missing" },
                category = request.memoryCategory.orEmpty().ifBlank { "general" },
                sourceConversationId = conversation.id,
            )
            AgentToolOutcome(json.encodeToString(MemorySaveToolResult(
                saved = true,
                created = result.created,
                updated = !result.created,
                id = result.memory.id,
                category = result.memory.category,
                content = result.memory.content,
                mergedMemoryId = result.mergedMemoryId,
            )))
        }
        "memory_list", "memory_search" -> {
            val settings = repository.automationSettingsNow()
            check(settings.memoryEnabled) { "Memory is disabled in Xylune settings." }
            val query = request.memoryQuery.orEmpty().trim()
            if (request.type.equals("memory_search", ignoreCase = true)) {
                require(query.isNotBlank()) { "Memory search query is missing" }
            }
            val memories = repository.searchMemories(
                query = query,
                includeDisabled = request.memoryIncludeDisabled ?: false,
                limit = (request.memoryLimit ?: 100).coerceIn(1, 200),
            ).map { memory ->
                MemoryToolItem(
                    id = memory.id,
                    content = memory.content,
                    category = memory.category,
                    enabled = memory.enabled,
                    updatedAt = memory.updatedAt,
                )
            }
            AgentToolOutcome(json.encodeToString(memories))
        }
        "memory_update" -> {
            val settings = repository.automationSettingsNow()
            check(settings.memoryEnabled) { "Memory is disabled in Xylune settings." }
            val result = repository.updateMemory(
                id = requireNotNull(request.memoryId) { "Memory id is missing" },
                content = requireNotNull(request.memoryText) { "Memory text is missing" },
                category = request.memoryCategory.orEmpty().ifBlank { "general" },
            )
            AgentToolOutcome(json.encodeToString(MemorySaveToolResult(
                saved = true,
                created = false,
                updated = true,
                id = result.memory.id,
                category = result.memory.category,
                content = result.memory.content,
                mergedMemoryId = result.mergedMemoryId,
            )))
        }
        "memory_forget" -> {
            val settings = repository.automationSettingsNow()
            check(settings.memoryEnabled) { "Memory is disabled in Xylune settings." }
            val id = requireNotNull(request.memoryId) { "Memory id is missing" }
            AgentToolOutcome(json.encodeToString(MemoryForgetToolResult(
                forgotten = repository.deleteMemory(id),
                id = id,
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
        else -> error("Unknown Xylune tool: ${request.type}")
        }
    }

    private suspend fun executeStored(
        metadata: app.xylune.chat.sandbox.ScriptRunMetadata,
        args: List<String>,
        timeout: Int,
        onProgress: suspend (ExecutionProgress) -> Unit,
    ): app.xylune.chat.sandbox.ScriptRunResult {
        val started = System.currentTimeMillis()
        return try {
            val raw = if (metadata.runtime == ScriptRuntime.PYTHON) {
                python.executeFile(metadata.conversationId, metadata.scriptPath, args, timeout).let { result ->
                    onProgress(ExecutionProgress(result.stdout.takeLast(12_000), result.stderr.takeLast(12_000), result.elapsedMs))
                    StoredExecution(
                        stdout = result.stdout,
                        stderr = result.stderr,
                        exitCode = result.exitCode,
                        files = result.files,
                        elapsedMs = result.elapsedMs,
                        timedOut = result.timedOut,
                        cancelled = result.cancelled,
                    )
                }
            } else {
                ubuntu.executeShellFile(metadata.conversationId, metadata.scriptPath, args, timeout, onProgress).let { result ->
                    StoredExecution(
                        stdout = result.stdout,
                        stderr = result.stderr,
                        exitCode = result.exitCode,
                        files = result.files,
                        elapsedMs = result.elapsedMs,
                        timedOut = result.timedOut,
                    )
                }
            }
            runRecords.finish(metadata, raw.stdout, raw.stderr, raw.exitCode, raw.timedOut, raw.cancelled, raw.elapsedMs, raw.files)
        } catch (cancelled: CancellationException) {
            runRecords.finish(
                metadata = metadata,
                stdout = "",
                stderr = "Execution cancelled; the process tree was terminated.",
                exitCode = 130,
                timedOut = false,
                cancelled = true,
                elapsedMs = System.currentTimeMillis() - started,
                changedFiles = emptyList(),
            )
            throw cancelled
        }
    }

    private data class StoredExecution(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val files: List<String>,
        val elapsedMs: Long,
        val timedOut: Boolean,
        val cancelled: Boolean = false,
    )

    private suspend fun search(rawQuery: String): String = withContext(Dispatchers.IO) {
        val query = rawQuery.trim().take(500)
        require(query.isNotBlank()) { "Search query is empty" }
        val url = "https://html.duckduckgo.com/html/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) Xylune/0.12.0")
            .header("Accept", "text/html")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Search failed with HTTP ${response.code}" }
            val html = response.body?.readLimited(2_000_000).orEmpty()
            val results = parseDuckDuckGo(html).take(8)
            if (results.isEmpty()) "No search results were returned for: $query"
            else json.encodeToString(WebSearchResponse(query = query, engine = "DuckDuckGo", results = results))
        }
    }

    private suspend fun fetch(rawUrl: String): String = withContext(Dispatchers.IO) {
        var url = validatePublicUrl(rawUrl)
        repeat(4) { redirectCount ->
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) Xylune/0.12.0")
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
private data class MemorySaveToolResult(
    val saved: Boolean,
    val created: Boolean,
    val updated: Boolean,
    val id: String,
    val category: String,
    val content: String,
    val mergedMemoryId: String? = null,
)

@Serializable
private data class MemoryToolItem(
    val id: String,
    val content: String,
    val category: String,
    val enabled: Boolean,
    val updatedAt: Long,
)

@Serializable
private data class MemoryForgetToolResult(
    val forgotten: Boolean,
    val id: String,
)

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
internal data class WebSearchResponse(
    val query: String,
    val engine: String = "DuckDuckGo",
    val results: List<WebSearchResult>,
)

@Serializable
internal data class WebSearchResult(val title: String, val url: String, val snippet: String)

@Serializable
internal data class WebFetchResponse(val url: String, val contentType: String, val text: String)
