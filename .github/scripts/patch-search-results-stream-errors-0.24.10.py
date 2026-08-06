from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


chat_path = Path("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")
chat = chat_path.read_text()

chat = replace_once(
    chat,
    "import app.xylune.chat.agent.WebSearchResponse\n",
    "import app.xylune.chat.agent.WebSearchResponse\nimport app.xylune.chat.agent.WebSearchResult\n",
    "WebSearchResult import",
)

helper_anchor = '''internal fun workEventStateLabel(event: MessageTimelineEvent): String = when (event.status) {'''
helper = r'''internal data class TimelineSourceLink(
    val title: String,
    val url: String,
)

private val TimelineLegacySourceLink = Regex(
    """\[\[source\|([^|\]\n]{1,240})\|(https?://[^\]\s]+)]]""",
    RegexOption.IGNORE_CASE,
)
private val TimelineCompactSourceLink = Regex(
    """\[\[([^|\]\n]{1,240})\|(https?://[^\]\s]+)]]""",
    RegexOption.IGNORE_CASE,
)
private val TimelineMarkdownSourceLink = Regex(
    """\[([^\]\n]{1,240})]\((https?://[^)\s]+)\)""",
    RegexOption.IGNORE_CASE,
)
private val TimelineRawUrl = Regex("""https?://[^\s<>()\[\]]+""", RegexOption.IGNORE_CASE)

internal fun extractTimelineSourceLinks(text: String): List<TimelineSourceLink> {
    data class LocatedLink(val offset: Int, val link: TimelineSourceLink)

    val located = mutableListOf<LocatedLink>()
    fun collect(regex: Regex) {
        regex.findAll(text).forEach { match ->
            val title = match.groupValues[1]
                .replace("\\[", "[")
                .replace("\\]", "]")
                .replace('|', '·')
                .trim()
            val url = match.groupValues[2].trim().trimEnd('.', ',', ';')
            if (url.startsWith("http://") || url.startsWith("https://")) {
                located += LocatedLink(
                    offset = match.range.first,
                    link = TimelineSourceLink(title.ifBlank { url }, url),
                )
            }
        }
    }
    collect(TimelineLegacySourceLink)
    collect(TimelineCompactSourceLink)
    collect(TimelineMarkdownSourceLink)

    val alreadyLocated = located.mapTo(linkedSetOf()) { it.link.url }
    TimelineRawUrl.findAll(text).forEach { match ->
        val url = match.value.trimEnd('.', ',', ';')
        if (alreadyLocated.add(url)) {
            val host = runCatching { url.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
            located += LocatedLink(match.range.first, TimelineSourceLink(host.ifBlank { url }, url))
        }
    }

    val seen = linkedSetOf<String>()
    return located.sortedBy(LocatedLink::offset).mapNotNull { candidate ->
        candidate.link.takeIf { seen.add(it.url) }
    }
}

internal fun recoveryNoticeKey(message: MessageEntity): String =
    "${message.nodeId}:${message.updatedAt}:${message.status}:${message.error.orEmpty()}"

internal fun recoveryErrorSummary(message: MessageEntity): String = message.error
    ?.lineSequence()
    ?.map(String::trim)
    ?.filter(String::isNotBlank)
    ?.joinToString(" ")
    ?.take(360)
    ?.takeIf(String::isNotBlank)
    ?: if (message.status == MessageStatus.ERROR) {
        "The provider stream failed without returning additional diagnostic text."
    } else {
        "The response stopped before it completed."
    }

'''
if helper_anchor not in chat:
    raise SystemExit("workEventStateLabel anchor missing")
chat = chat.replace(helper_anchor, helper + helper_anchor, 1)

chat = replace_once(
    chat,
    '''    var showChatConfiguration by remember { mutableStateOf(false) }
    val messageListState = rememberLazyListState()
''',
    '''    var showChatConfiguration by remember { mutableStateOf(false) }
    var dismissedRecoveryNoticeKey by rememberSaveable(conversation?.id) { mutableStateOf<String?>(null) }
    var recoveryDetailsMessage by remember(conversation?.id) { mutableStateOf<MessageEntity?>(null) }
    val messageListState = rememberLazyListState()
''',
    "recovery state",
)

chat = replace_once(
    chat,
    '''        showModelPicker = false
        chatMenu = false
        followMode = ChatFollowMode.FOLLOWING
''',
    '''        showModelPicker = false
        chatMenu = false
        recoveryDetailsMessage = null
        dismissedRecoveryNoticeKey = null
        followMode = ChatFollowMode.FOLLOWING
''',
    "conversation recovery reset",
)

old_banner = '''            val interrupted = recoverable.firstOrNull { candidate ->
                candidate.status == MessageStatus.ERROR ||
                    (candidate.status == MessageStatus.INTERRUPTED &&
                        candidate.error !in setOf("Steered by user", "Replaced by an edited message"))
            }
            AnimatedVisibility(
                visible = interrupted != null && !generating,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 8.dp, start = 12.dp, end = 12.dp),
            ) {
                interrupted?.let { message ->
                    val failed = message.status == MessageStatus.ERROR
                    Surface(
                        color = if (failed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp))
                            Text(
                                if (failed) "Request failed" else "Response paused",
                                Modifier.padding(start = 9.dp).weight(1f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            TextButton(onClick = {
                                if (failed) viewModel.retryMessage(message) else viewModel.resume(message)
                            }) {
                                Text(if (failed) "Retry" else "Continue")
                            }
                        }
                    }
                }
            }
'''
new_banner = '''            val interrupted = recoverable.firstOrNull { candidate ->
                val recoverableStatus = candidate.status == MessageStatus.ERROR ||
                    (candidate.status == MessageStatus.INTERRUPTED &&
                        candidate.error !in setOf("Steered by user", "Replaced by an edited message"))
                recoverableStatus && recoveryNoticeKey(candidate) != dismissedRecoveryNoticeKey
            }
            AnimatedVisibility(
                visible = interrupted != null && !generating,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = padding.calculateTopPadding() + 8.dp, start = 12.dp, end = 12.dp),
            ) {
                interrupted?.let { message ->
                    val failed = message.status == MessageStatus.ERROR
                    Surface(
                        color = if (failed) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, null, Modifier.size(18.dp))
                                Text(
                                    if (failed) "Request failed" else "Response paused",
                                    Modifier.padding(start = 9.dp).weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                IconButton(
                                    onClick = { dismissedRecoveryNoticeKey = recoveryNoticeKey(message) },
                                    modifier = Modifier.size(34.dp),
                                ) {
                                    Icon(Icons.Outlined.Close, "Dismiss error", Modifier.size(18.dp))
                                }
                            }
                            Text(
                                recoveryErrorSummary(message),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { recoveryDetailsMessage = message }) {
                                    Text("Details")
                                }
                                TextButton(onClick = {
                                    dismissedRecoveryNoticeKey = recoveryNoticeKey(message)
                                    if (failed) viewModel.retryMessage(message) else viewModel.resume(message)
                                }) {
                                    Text(if (failed) "Retry" else "Continue")
                                }
                            }
                        }
                    }
                }
            }
'''
chat = replace_once(chat, old_banner, new_banner, "recoverable banner")

chat = replace_once(
    chat,
    '''    }
    if (showChatConfiguration) {
''',
    '''    }
    recoveryDetailsMessage?.let { message ->
        val dialogContext = LocalContext.current
        val fullError = message.error?.trim().orEmpty().ifBlank {
            "No additional diagnostic text was returned by the provider."
        }
        XyluneAlertDialog(
            onDismissRequest = { recoveryDetailsMessage = null },
            title = {
                Text(if (message.status == MessageStatus.ERROR) "Request error" else "Interrupted response")
            },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        listOfNotNull(message.providerId, message.modelId).joinToString(" · ")
                            .ifBlank { "Provider details unavailable" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CodeSourcePanel(
                        language = "text",
                        code = fullError,
                        label = if (message.status == MessageStatus.ERROR) "ERROR" else "DETAILS",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialogContext.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Xylune stream error", fullError))
                }) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Copy")
                }
            },
            confirmButton = {
                TextButton(onClick = { recoveryDetailsMessage = null }) { Text("Close") }
            },
        )
    }
    if (showChatConfiguration) {
''',
    "error details dialog",
)

# Source-link extraction for provider-native search results.
chat = replace_once(
    chat,
    '''    val usedSourceUrls = remember(orderedEvents) {
        orderedEvents.filter { it.kind == "fetch" && it.status == "complete" }.mapNotNull { event ->
            runCatching { ChatMessageJson.decodeFromString<WebFetchResponse>(event.output).url }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: event.input.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }.toSet()
    }
''',
    '''    val usedSourceUrls = remember(orderedEvents) {
        orderedEvents.filter { it.kind == "fetch" && it.status == "complete" }.mapNotNull { event ->
            runCatching { ChatMessageJson.decodeFromString<WebFetchResponse>(event.output).url }.getOrNull()
                ?.takeIf(String::isNotBlank)
                ?: event.input.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }.toSet()
    }
    val sourceLinks = remember(orderedEvents) {
        buildList {
            orderedEvents.forEach { event ->
                addAll(extractTimelineSourceLinks(event.content))
                if (event.kind == "fetch" && event.status == "complete") {
                    val url = runCatching {
                        ChatMessageJson.decodeFromString<WebFetchResponse>(event.output).url
                    }.getOrNull()?.takeIf(String::isNotBlank)
                        ?: event.input.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                    url?.let { target ->
                        val host = runCatching { target.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
                        add(TimelineSourceLink(host.ifBlank { target }, target))
                    }
                }
            }
        }.distinctBy(TimelineSourceLink::url)
    }
''',
    "source link extraction",
)

chat = replace_once(
    chat,
    '''                    usedSourceUrls = usedSourceUrls,
                    viewModel = viewModel,
''',
    '''                    usedSourceUrls = usedSourceUrls,
                    sourceLinks = sourceLinks,
                    viewModel = viewModel,
''',
    "timeline working source links call",
)

chat = replace_once(
    chat,
    '''    usedSourceUrls: Set<String>,
    viewModel: ChatViewModel,
''',
    '''    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    viewModel: ChatViewModel,
''',
    "timeline working source links signature",
)

chat = replace_once(
    chat,
    '''                                usedSourceUrls = usedSourceUrls,
                                viewModel = viewModel,
''',
    '''                                usedSourceUrls = usedSourceUrls,
                                sourceLinks = sourceLinks,
                                viewModel = viewModel,
''',
    "timeline step source links call",
)

chat = replace_once(
    chat,
    '''    usedSourceUrls: Set<String>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    val hasDetails = event.content.isNotBlank() || event.input.isNotBlank() ||
''',
    '''    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
    val hasDetails = event.content.isNotBlank() || event.input.isNotBlank() ||
''',
    "timeline step source links signature",
)

chat = replace_once(
    chat,
    '''    var expanded by rememberSaveable("work-step-$stateKey") {
        mutableStateOf(active)
    }
    var previouslyActive by rememberSaveable("work-step-active-$stateKey") {
        mutableStateOf(active)
    }
    LaunchedEffect(active) {
        if (active != previouslyActive) {
            expanded = active
            previouslyActive = active
        }
    }
''',
    '''    val keepExpanded = event.kind in setOf("search", "native_search")
    var expanded by rememberSaveable("work-step-$stateKey") {
        mutableStateOf(active || keepExpanded)
    }
    var previouslyActive by rememberSaveable("work-step-active-$stateKey") {
        mutableStateOf(active)
    }
    LaunchedEffect(active, keepExpanded) {
        if (active != previouslyActive) {
            expanded = active || keepExpanded
            previouslyActive = active
        }
    }
''',
    "search auto expansion",
)

chat = replace_once(
    chat,
    '''                            event.kind in setOf("script", "python", "ubuntu", "search", "fetch") ->
''',
    '''                            event.kind in setOf("script", "python", "ubuntu", "search", "native_search", "fetch") ->
''',
    "native search detail routing",
)

chat = replace_once(
    chat,
    '''                                    usedSourceUrls,
                                    viewModel,
''',
    '''                                    usedSourceUrls,
                                    sourceLinks,
                                    viewModel,
''',
    "tool step source links call",
)

chat = replace_once(
    chat,
    '''    usedSourceUrls: Set<String>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
''',
    '''    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    viewModel: ChatViewModel,
    workingCardViewport: WorkingCardViewportController,
) {
''',
    "tool details source links signature",
)

chat = replace_once(
    chat,
    '''    when (kind) {
        "search" -> CompactSearchToolCard(input, output, status, usedSourceUrls)
        "fetch" -> CompactFetchToolCard(input, output, status)
''',
    '''    when (kind) {
        "search", "native_search" -> CompactSearchToolCard(
            query = input,
            output = output,
            status = status,
            usedSourceUrls = usedSourceUrls,
            sourceLinks = sourceLinks,
            nativeSearch = kind == "native_search",
        )
        "fetch" -> CompactFetchToolCard(input, output, status)
''',
    "search card routing",
)

start = chat.index("@Composable\nprivate fun CompactSearchToolCard(")
end = chat.index("@Composable\nprivate fun CompactFetchToolCard", start)
new_search_card = '''@Composable
private fun CompactSearchToolCard(
    query: String,
    output: String,
    status: String,
    usedSourceUrls: Set<String>,
    sourceLinks: List<TimelineSourceLink>,
    nativeSearch: Boolean,
) {
    val parsed = remember(output) {
        runCatching { ChatMessageJson.decodeFromString<WebSearchResponse>(output) }.getOrNull()
    }
    val results = remember(parsed, sourceLinks, nativeSearch) {
        val structured = parsed?.results.orEmpty()
        val providerResults = if (nativeSearch) {
            sourceLinks.map { source ->
                WebSearchResult(
                    title = source.title,
                    url = source.url,
                    snippet = "Result exposed by the model provider's search response.",
                )
            }
        } else emptyList()
        (structured.ifEmpty { providerResults })
            .filter { it.url.startsWith("http://") || it.url.startsWith("https://") }
            .distinctBy(WebSearchResult::url)
            .take(12)
    }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    val visibleQuery = parsed?.query?.takeIf(String::isNotBlank)
        ?: query.takeIf(String::isNotBlank)
        ?: "Query unavailable"
    val engine = parsed?.engine?.takeIf(String::isNotBlank)
        ?: if (nativeSearch) "Provider search" else "Web search"

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Search, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 7.dp).weight(1f)) {
                    Text(engine, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (status) {
                            "preparing" -> "Preparing query"
                            "prepared" -> "Query ready"
                            "running" -> "Searching"
                            "error" -> "Search failed"
                            else -> if (results.isEmpty()) "No result details" else "${results.size} results"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (status == "error") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    Text(
                        "QUERY",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        visibleQuery,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (results.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results.size) { index ->
                        val result = results[index]
                        val host = runCatching { result.url.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
                        val used = result.url in usedSourceUrls
                        Box {
                            Surface(
                                onClick = { selectedUrl = result.url },
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.width(260.dp),
                            ) {
                                Column(
                                    Modifier.padding(11.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Text(
                                        "${index + 1}. ${result.title.ifBlank { host.ifBlank { result.url } }}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        host.ifBlank { result.url },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (result.snippet.isNotBlank()) {
                                        Text(
                                            result.snippet,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (used) {
                                        Text(
                                            "Opened by Xylune",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                            XyluneDropdownMenu(
                                expanded = selectedUrl == result.url,
                                onDismissRequest = { selectedUrl = null },
                                modifier = Modifier.width(330.dp),
                            ) {
                                LinkPreviewDetails(
                                    reference = LinkReferencePreview(
                                        kind = LinkReferenceKind.SOURCE,
                                        label = result.title,
                                        target = result.url,
                                        description = result.snippet,
                                    ),
                                    onDismiss = { selectedUrl = null },
                                    modifier = Modifier.padding(14.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    when {
                        status == "error" && output.isNotBlank() -> output.take(700)
                        status in setOf("preparing", "prepared", "running") -> "Waiting for search results…"
                        nativeSearch -> "The provider exposed the query but did not return result metadata or citations."
                        else -> "No search results were returned."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == "error") MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

'''
chat = chat[:start] + new_search_card + chat[end:]
chat_path.write_text(chat)

# Emit provider-native citations in Xylune's native source notation so the
# answer footer and the search timeline can consume the same structured links.
native_path = Path("app/src/main/java/app/xylune/chat/provider/NativeWebSearchProvider.kt")
native = native_path.read_text()
old_native_sources = '''private fun markdownSources(citations: Map<String, String>): String {
    if (citations.isEmpty()) return ""
    return buildString {
        append("\\n\\n### Sources\\n")
        citations.entries.take(12).forEach { (url, rawTitle) ->
            val title = rawTitle.replace('\\n', ' ').replace("[", "\\\\[").replace("]", "\\\\]").take(180)
            append("- [").append(title).append("](").append(url).append(")\\n")
        }
    }
}
'''
new_native_sources = '''private fun markdownSources(citations: Map<String, String>): String {
    if (citations.isEmpty()) return ""
    return buildString {
        append("\\n\\n")
        citations.entries.take(12).forEachIndexed { index, (url, rawTitle) ->
            if (index > 0) append(' ')
            val title = rawTitle.replace('\\n', ' ')
                .replace('|', '·')
                .replace('[', '(')
                .replace(']', ')')
                .take(180)
            append("[[").append(title.ifBlank { url }).append('|').append(url).append("]]" )
        }
        append('\\n')
    }
}
'''
native = replace_once(native, old_native_sources, new_native_sources, "native provider source notation")
native_path.write_text(native)

responses_path = Path("app/src/main/java/app/xylune/chat/provider/ResponsesApiTransport.kt")
responses = responses_path.read_text()
old_responses_sources = '''        return buildString {
            append("\\n\\n### Sources\\n")
            entries.forEach { (url, rawTitle) ->
                val title = rawTitle.replace('\\n', ' ').replace("[", "\\\\[").replace("]", "\\\\]").take(180)
                append("- [").append(title).append("](").append(url).append(")\\n")
            }
        }
'''
new_responses_sources = '''        return buildString {
            append("\\n\\n")
            entries.forEachIndexed { index, (url, rawTitle) ->
                if (index > 0) append(' ')
                val title = rawTitle.replace('\\n', ' ')
                    .replace('|', '·')
                    .replace('[', '(')
                    .replace(']', ')')
                    .take(180)
                append("[[").append(title.ifBlank { url }).append('|').append(url).append("]]" )
            }
            append('\\n')
        }
'''
responses = replace_once(responses, old_responses_sources, new_responses_sources, "Responses source notation")
responses_path.write_text(responses)

# Version and release notes.
build_path = Path("app/build.gradle.kts")
build = build_path.read_text()
if build.count("versionCode = 198") != 1 or build.count('versionName = "0.24.9"') != 1:
    raise SystemExit("Unexpected current app version")
build = build.replace("versionCode = 198", "versionCode = 199", 1)
build = build.replace('versionName = "0.24.9"', 'versionName = "0.24.10"', 1)
build_path.write_text(build)

changelog_path = Path("CHANGELOG.md")
changelog = changelog_path.read_text()
changelog_path.write_text('''## 0.24.10 — 2026-08-06

- Show provider-native and Xylune-managed search queries directly in the work timeline.
- Show all returned search results in horizontally scrollable result cards instead of hiding results that were not followed by a page-fetch call.
- Keep provider citations in Xylune source notation so native search results also feed inline pills and the response Sources bar.
- Replace the opaque, non-dismissible stream failure notice with a closable summary, full error details, provider/model context, copy support, and Retry or Continue actions.

''' + changelog)

release_notes = Path("docs/releases/RELEASE_NOTES_0.24.10.md")
release_notes.write_text('''# Xylune 0.24.10

## Visible search activity

Provider-native and Xylune-managed searches now keep their query visible in the work timeline. Completed search steps remain expanded and show every returned result in a horizontally swipeable set of cards with title, domain, snippet, preview, and open controls.

Provider citations are emitted through Xylune's source notation, allowing native-search results to appear in the response source pills and bottom Sources bar as well as the search activity card.

## Useful stream errors

A failed or interrupted stream now shows the actual error summary instead of only **Request failed**. The notice can be dismissed, opened for complete diagnostics, copied, retried, or continued. The details view also identifies the provider and model used for the failed request.
''')

# Focused regression tests for pure extraction/error helpers and UI wiring.
test_path = Path("app/src/test/java/app/xylune/chat/ui/SearchAndStreamErrorVisibilityTest.kt")
test_path.write_text('''package app.xylune.chat.ui

import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchAndStreamErrorVisibilityTest {
    @Test
    fun `provider markdown and Xylune source notation expose titles and urls`() {
        val links = extractTimelineSourceLinks(
            """Results [[PNA|https://www.pna.gov.ph/a]] and [Android docs](https://developer.android.com/b). """ +
                "Duplicate https://www.pna.gov.ph/a",
        )
        assertEquals(2, links.size)
        assertEquals("PNA", links[0].title)
        assertEquals("https://www.pna.gov.ph/a", links[0].url)
        assertEquals("Android docs", links[1].title)
    }

    @Test
    fun `recovery notice identity changes for a new error revision`() {
        val first = failedMessage(updatedAt = 10, error = "HTTP 429: rate limited")
        val updated = first.copy(updatedAt = 11, error = "HTTP 503: unavailable")
        assertNotEquals(recoveryNoticeKey(first), recoveryNoticeKey(updated))
        assertTrue(recoveryErrorSummary(first).contains("429"))
    }

    private fun failedMessage(updatedAt: Long, error: String) = MessageEntity(
        nodeId = "assistant-1",
        conversationId = "conversation-1",
        parentNodeId = "user-1",
        branchId = "branch-1",
        role = MessageRole.ASSISTANT,
        content = "",
        status = MessageStatus.ERROR,
        providerId = "openai",
        modelId = "gpt-test",
        createdAt = 1,
        updatedAt = updatedAt,
        error = error,
    )
}
''')
