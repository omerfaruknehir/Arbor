from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one patch target for {label}, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.MULTILINE)
    if count != 1:
        raise SystemExit(f"Expected one regex patch target for {label}, found {count}")
    return updated


# Route XML-style thinking blocks into the existing reasoning channel, including
# tags split across arbitrary streaming chunk boundaries.
provider_path = Path("app/src/main/java/app/xylune/chat/provider/OpenAiCompatibleProvider.kt")
provider = provider_path.read_text()
provider = replace_once(
    provider,
    """            val dsmlChannels = protocolTools.takeIf { it.isNotEmpty() }?.let(::DsmlChannelsAdapter)
            val rawText = StringBuilder()""",
    """            val dsmlChannels = protocolTools.takeIf { it.isNotEmpty() }?.let(::DsmlChannelsAdapter)
            val thinkingTags = ThinkingTagStreamParser()
            val rawText = StringBuilder()""",
    "thinking tag parser initialization",
)
provider = replace_once(
    provider,
    """                    parseChunk(payload, calls)?.let { chunk ->
                        rawText.append(chunk.text)
                        rawReasoning.append(chunk.reasoning)
                        val adapted = dsmlChannels?.accept(chunk.text, chunk.reasoning)
                            ?: DsmlChannelDelta(chunk.text, chunk.reasoning)
                        val outgoing = if (
                            adapted.text == chunk.text && adapted.reasoning == chunk.reasoning
                        ) {
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
                    }""",
    """                    parseChunk(payload, calls)?.let { chunk ->
                        val tagged = thinkingTags.accept(chunk.text, chunk.reasoning)
                        rawText.append(tagged.text)
                        rawReasoning.append(tagged.reasoning)
                        val adapted = dsmlChannels?.accept(tagged.text, tagged.reasoning) ?: tagged
                        val outgoing = if (
                            adapted.text == chunk.text && adapted.reasoning == chunk.reasoning
                        ) {
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
                    }""",
    "stream thinking tag routing",
)
provider = replace_once(
    provider,
    """            val adapted = dsmlChannels?.finish()
            val completedStructuredCalls = calls.toSortedMap()""",
    """            val thinkingTail = thinkingTags.finish()
            if (thinkingTail.text.isNotEmpty() || thinkingTail.reasoning.isNotEmpty()) {
                rawText.append(thinkingTail.text)
                rawReasoning.append(thinkingTail.reasoning)
                val routedTail = dsmlChannels?.accept(thinkingTail.text, thinkingTail.reasoning) ?: thinkingTail
                val outgoingTail = StreamChunk(text = routedTail.text, reasoning = routedTail.reasoning)
                if (outgoingTail.hasMeaningfulPayload()) {
                    meaningfulPayloadReceived = true
                    emit(outgoingTail)
                }
            }

            val adapted = dsmlChannels?.finish()
            val completedStructuredCalls = calls.toSortedMap()""",
    "thinking tag tail flush",
)
parser_class = r'''    internal class ThinkingTagStreamParser {
        private data class Tag(val value: String, val entersThinking: Boolean)

        private val tags = listOf(
            Tag("<thinking>", true),
            Tag("</thinking>", false),
            Tag("<think>", true),
            Tag("</think>", false),
        )
        private val pending = StringBuilder()
        private var inThinking = false

        fun accept(text: String, explicitReasoning: String = ""): DsmlChannelDelta {
            val combined = buildString {
                append(pending)
                append(text)
            }
            pending.clear()
            val visible = StringBuilder()
            val reasoning = StringBuilder(explicitReasoning)

            fun appendCurrent(value: String) {
                if (value.isEmpty()) return
                if (inThinking) reasoning.append(value) else visible.append(value)
            }

            var index = 0
            while (index < combined.length) {
                val marker = combined.indexOf('<', index)
                if (marker < 0) {
                    appendCurrent(combined.substring(index))
                    break
                }
                appendCurrent(combined.substring(index, marker))
                val remaining = combined.substring(marker)
                val complete = tags.firstOrNull { tag ->
                    remaining.length >= tag.value.length &&
                        remaining.regionMatches(0, tag.value, 0, tag.value.length, ignoreCase = true)
                }
                if (complete != null) {
                    inThinking = complete.entersThinking
                    index = marker + complete.value.length
                    continue
                }
                if (tags.any { tag -> tag.value.startsWith(remaining, ignoreCase = true) }) {
                    pending.append(remaining)
                    break
                }
                appendCurrent("<")
                index = marker + 1
            }
            return DsmlChannelDelta(visible.toString(), reasoning.toString())
        }

        fun finish(): DsmlChannelDelta {
            val remainder = pending.toString()
            pending.clear()
            return if (inThinking) {
                DsmlChannelDelta(text = "", reasoning = remainder)
            } else {
                DsmlChannelDelta(text = remainder, reasoning = "")
            }
        }
    }

'''
provider = replace_once(
    provider,
    "    internal class ToolCallAccumulator {",
    parser_class + "    internal class ToolCallAccumulator {",
    "thinking tag parser class",
)
provider_path.write_text(provider)

# Provider regression coverage for both complete and arbitrarily split tags.
protocol_test_path = Path("app/src/test/java/app/xylune/chat/provider/NativeProviderProtocolTest.kt")
protocol_test = protocol_test_path.read_text()
protocol_test = replace_once(
    protocol_test,
    """    @Test
    fun anthropicPreservesThinkingSignatureAndToolUseBlocks() {""",
    '''    @Test
    fun openAiCompatibleRoutesThinkingTagsAcrossStreamingChunks() {
        val parser = OpenAiCompatibleProvider.ThinkingTagStreamParser()

        val first = parser.accept("Visible before <thin")
        assertEquals("Visible before ", first.text)
        assertEquals("", first.reasoning)

        val second = parser.accept("king>hidden step</think")
        assertEquals("", second.text)
        assertEquals("hidden step", second.reasoning)

        val third = parser.accept("ing> visible after <think>more")
        assertEquals(" visible after ", third.text)
        assertEquals("more", third.reasoning)

        val fourth = parser.accept(" thought</think> done", explicitReasoning = "native reasoning")
        assertEquals(" done", fourth.text)
        assertEquals("native reasoning thought", fourth.reasoning)
        assertEquals(DsmlChannelDelta("", ""), parser.finish())
    }

    @Test
    fun anthropicPreservesThinkingSignatureAndToolUseBlocks() {''',
    "thinking tag parser regression test",
)
protocol_test_path.write_text(protocol_test)

# Add a persisted, default-on automatic update-check preference.
preferences_path = Path("app/src/main/java/app/xylune/chat/settings/AppPreferences.kt")
preferences = preferences_path.read_text()
preferences = replace_once(
    preferences,
    """    private val _lessEmojiEnabled = MutableStateFlow(preferences.getBoolean(KEY_LESS_EMOJI_ENABLED, true))
    private val _newChatDefaults""",
    """    private val _lessEmojiEnabled = MutableStateFlow(preferences.getBoolean(KEY_LESS_EMOJI_ENABLED, true))
    private val _automaticUpdateChecks = MutableStateFlow(preferences.getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true))
    private val _newChatDefaults""",
    "automatic update state",
)
preferences = replace_once(
    preferences,
    """    val lessEmojiEnabled: StateFlow<Boolean> = _lessEmojiEnabled.asStateFlow()
    val newChatDefaults""",
    """    val lessEmojiEnabled: StateFlow<Boolean> = _lessEmojiEnabled.asStateFlow()
    val automaticUpdateChecks: StateFlow<Boolean> = _automaticUpdateChecks.asStateFlow()
    val newChatDefaults""",
    "automatic update state flow",
)
preferences = replace_once(
    preferences,
    """    fun setLessEmojiEnabled(enabled: Boolean) {
        _lessEmojiEnabled.value = enabled
        preferences.edit { putBoolean(KEY_LESS_EMOJI_ENABLED, enabled) }
    }

    fun setGeneratedRepairMaxAttempts""",
    """    fun setLessEmojiEnabled(enabled: Boolean) {
        _lessEmojiEnabled.value = enabled
        preferences.edit { putBoolean(KEY_LESS_EMOJI_ENABLED, enabled) }
    }

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        _automaticUpdateChecks.value = enabled
        preferences.edit { putBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, enabled) }
    }

    fun setGeneratedRepairMaxAttempts""",
    "automatic update setter",
)
preferences = replace_once(
    preferences,
    """        const val KEY_LESS_EMOJI_ENABLED = "less_emoji_enabled"
        const val CHROME_EDGE_CONTROL_REVISION""",
    """        const val KEY_LESS_EMOJI_ENABLED = "less_emoji_enabled"
        const val KEY_AUTOMATIC_UPDATE_CHECKS = "automatic_update_checks"
        const val CHROME_EDGE_CONTROL_REVISION""",
    "automatic update preference key",
)
preferences_path.write_text(preferences)

# Start checks only when enabled; turning the option on also performs the due check.
view_model_path = Path("app/src/main/java/app/xylune/chat/ui/ChatViewModel.kt")
view_model = view_model_path.read_text()
view_model = replace_once(
    view_model,
    """    val lessEmojiEnabled: StateFlow<Boolean> = container.appPreferences.lessEmojiEnabled
    val generatedRepairMaxAttempts""",
    """    val lessEmojiEnabled: StateFlow<Boolean> = container.appPreferences.lessEmojiEnabled
    val automaticUpdateChecks: StateFlow<Boolean> = container.appPreferences.automaticUpdateChecks
    val generatedRepairMaxAttempts""",
    "view model automatic update flow",
)
view_model = replace_once(
    view_model,
    """        viewModelScope.launch {
            container.repositoryUpdates.checkIfDue()
        }""",
    """        viewModelScope.launch {
            if (container.appPreferences.automaticUpdateChecks.value) {
                container.repositoryUpdates.checkIfDue()
            }
        }""",
    "conditional startup update check",
)
view_model = replace_once(
    view_model,
    """    fun setLessEmojiEnabled(enabled: Boolean) = container.appPreferences.setLessEmojiEnabled(enabled)

    fun postNotice""",
    """    fun setLessEmojiEnabled(enabled: Boolean) = container.appPreferences.setLessEmojiEnabled(enabled)

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        container.appPreferences.setAutomaticUpdateChecks(enabled)
        if (enabled) viewModelScope.launch { container.repositoryUpdates.checkIfDue() }
    }

    fun postNotice""",
    "view model automatic update setter",
)
view_model_path.write_text(view_model)

# Expose the option in About > Updates.
settings_path = Path("app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt")
settings = settings_path.read_text()
settings = replace_once(
    settings,
    """    val lessEmojiEnabled by viewModel.lessEmojiEnabled.collectAsState()
    val generatedRepairMaxAttempts""",
    """    val lessEmojiEnabled by viewModel.lessEmojiEnabled.collectAsState()
    val automaticUpdateChecks by viewModel.automaticUpdateChecks.collectAsState()
    val generatedRepairMaxAttempts""",
    "settings automatic update state",
)
settings = replace_once(
    settings,
    """                            developerEnabled = developerSettings.enabled,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,""",
    """                            developerEnabled = developerSettings.enabled,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,
                            automaticUpdateChecks = automaticUpdateChecks,""",
    "about page automatic update argument",
)
settings = replace_once(
    settings,
    """    developerEnabled: Boolean,
    matchLauncherIconToPalette: Boolean,
    onOpenDeveloper""",
    """    developerEnabled: Boolean,
    matchLauncherIconToPalette: Boolean,
    automaticUpdateChecks: Boolean,
    onOpenDeveloper""",
    "about page automatic update parameter",
)
settings = replace_once(
    settings,
    """    SettingsGroup("Updates") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {""",
    """    SettingsGroup("Updates") {
        ListItem(
            headlineContent = { Text("Check automatically", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Check the source repository once per day when Xylune starts") },
            leadingContent = { Icon(Icons.Outlined.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                Switch(
                    checked = automaticUpdateChecks,
                    onCheckedChange = viewModel::setAutomaticUpdateChecks,
                    enabled = sourceRepository != null,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {""",
    "automatic update switch UI",
)
settings = settings.replace(
    "Automatic checks are disabled because this build has no embedded GitHub repository origin.",
    "Automatic checks are unavailable because this build has no embedded GitHub repository origin.",
)
settings_path.write_text(settings)

# Include the option in portable settings backups without breaking older archives.
archive_path = Path("app/src/main/java/app/xylune/chat/transfer/AppSettingsArchiveStore.kt")
archive = archive_path.read_text()
archive = replace_once(
    archive,
    """    val lessEmojiEnabled: Boolean = true,
    val newChatDefaults""",
    """    val lessEmojiEnabled: Boolean = true,
    val automaticUpdateChecks: Boolean = true,
    val newChatDefaults""",
    "portable automatic update field",
)
archive = replace_once(
    archive,
    """                lessEmojiEnabled = preferences.lessEmojiEnabled.value,
                newChatDefaults""",
    """                lessEmojiEnabled = preferences.lessEmojiEnabled.value,
                automaticUpdateChecks = preferences.automaticUpdateChecks.value,
                newChatDefaults""",
    "portable automatic update snapshot",
)
archive = replace_once(
    archive,
    """        preferences.setLessEmojiEnabled(value.lessEmojiEnabled)
        val defaults""",
    """        preferences.setLessEmojiEnabled(value.lessEmojiEnabled)
        preferences.setAutomaticUpdateChecks(value.automaticUpdateChecks)
        val defaults""",
    "portable automatic update restore",
)
archive_path.write_text(archive)

# Update-check integration coverage.
update_test_path = Path("app/src/test/java/app/xylune/chat/ui/RepositoryUpdateIntegrationTest.kt")
update_test = update_test_path.read_text()
update_test = replace_once(
    update_test,
    """    fun aboutPageUsesEmbeddedBuildSource() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertTrue(settings.contains("BuildConfig.SOURCE_REPOSITORY"))
        assertTrue(settings.contains("Check for updates"))
    }""",
    """    fun aboutPageUsesEmbeddedBuildSourceAndAutomaticCheckOption() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        val preferences = java.io.File("src/main/java/app/xylune/chat/settings/AppPreferences.kt").readText()
        val viewModel = java.io.File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        val archive = java.io.File("src/main/java/app/xylune/chat/transfer/AppSettingsArchiveStore.kt").readText()
        assertTrue(settings.contains("BuildConfig.SOURCE_REPOSITORY"))
        assertTrue(settings.contains("Check for updates"))
        assertTrue(settings.contains("Check automatically"))
        assertTrue(settings.contains("onCheckedChange = viewModel::setAutomaticUpdateChecks"))
        assertTrue(preferences.contains("KEY_AUTOMATIC_UPDATE_CHECKS"))
        assertTrue(preferences.contains("preferences.getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true)"))
        assertTrue(viewModel.contains("if (container.appPreferences.automaticUpdateChecks.value)"))
        assertTrue(archive.contains("automaticUpdateChecks: Boolean = true"))
    }""",
    "automatic update integration test",
)
update_test_path.write_text(update_test)

# Version and release notes.
build_path = Path("app/build.gradle.kts")
build = build_path.read_text()
build = regex_once(build, r"versionCode = 193\b", "versionCode = 194", "version code")
build = regex_once(build, r'versionName = "0\.24\.4"', 'versionName = "0.24.5"', "version name")
build_path.write_text(build)

changelog_path = Path("CHANGELOG.md")
changelog = changelog_path.read_text()
entry = """## 0.24.5 — 2026-08-06

- Show visible reasoning returned through OpenAI-compatible reasoning fields and route streamed `<thinking>` or `<think>` blocks into Xylune's Working UI, even when tags are split across network chunks.
- Add a persistent, default-on automatic update-check option under About, checking the build's source repository at most once per day while preserving manual checks.
- Preserve the automatic-update preference in portable settings backups and restore it safely from older archives.
- Complete the GitHub Pages appearance overhaul with full Material surface/text tinting, app-like collapsing titles, unrestricted legal-document scrolling, compact appearance controls, and launcher-icon parity.

"""
if changelog.startswith("## 0.24.5"):
    raise SystemExit("CHANGELOG already contains 0.24.5")
changelog_path.write_text(entry + changelog)

notes_path = Path("docs/releases/RELEASE_NOTES_0.24.5.md")
if notes_path.exists():
    raise SystemExit("0.24.5 release notes already exist")
notes_path.write_text("""# Xylune 0.24.5

## Thinking display

- Recognizes visible reasoning delivered through OpenAI-compatible `reasoning`, `reasoning_content`, `thinking`, `analysis`, and textual `reasoning_details` fields.
- Routes streamed `<thinking>...</thinking>` and `<think>...</think>` content into the Working card instead of exposing the tags in the final answer.
- Handles opening and closing tags split across arbitrary provider chunks without delaying ordinary answer text.

## Updates

- Adds **Check automatically** under **About Xylune → Updates**.
- The option is enabled by default and checks the build's embedded GitHub repository at most once per day when Xylune starts.
- Manual checks remain available, and the preference is included in portable settings backups.

## Website and legal pages

- Applies the selected Material palette to backgrounds, surfaces, ordinary text, bold text, outlines, and navigation.
- Uses app-like expanded and collapsed page titles while keeping Privacy Policy and Terms scrolling unrestricted.
- Keeps theme controls compact and matches website branding to Xylune's launcher-icon variants.
""")
