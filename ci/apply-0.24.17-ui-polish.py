from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text)


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# 1) Keep streamed search preparation and execution in one timeline event.
tool_streaming = "app/src/main/java/app/xylune/chat/generation/ToolCallStreaming.kt"
replace_once(
    tool_streaming,
    "import kotlinx.serialization.json.Json\n",
    "import app.xylune.chat.agent.MessageTimelineEvent\nimport kotlinx.serialization.json.Json\n",
)
replace_once(
    tool_streaming,
    "private val ToolPreviewJson = Json { ignoreUnknownKeys = true }\n",
    '''private val ToolPreviewJson = Json { ignoreUnknownKeys = true }\n\ninternal fun preparedToolCallMatches(\n    candidate: MessageTimelineEvent,\n    providerCallId: String,\n    argumentsJson: String,\n    presentation: ToolCallPresentation,\n): Boolean {\n    if (candidate.status !in setOf("preparing", "prepared") || candidate.kind != presentation.kind) return false\n\n    val sameProviderCall = providerCallId.isNotBlank() && candidate.providerCallId == providerCallId\n    val sameArguments = argumentsJson.isNotBlank() && candidate.argumentsJson.isNotBlank() &&\n        candidate.argumentsJson == argumentsJson\n    // Some providers only assign the final call id after streaming the arguments,\n    // and may normalize the final JSON. The visible tool input is the stable\n    // identity in that case, so preparation should morph into execution rather\n    // than becoming a second card.\n    val sameInput = presentation.input.isNotBlank() && candidate.input.isNotBlank() &&\n        candidate.input.trim() == presentation.input.trim()\n    return sameProviderCall || sameArguments || sameInput\n}\n''',
)

generation = "app/src/main/java/app/xylune/chat/generation/GenerationWorker.kt"
replace_once(
    generation,
    '''            val preparedIndex = timeline.indexOfLast { candidate ->\n                candidate.status in setOf("preparing", "prepared") &&\n                    ((providerCallId.isNotBlank() && candidate.providerCallId == providerCallId) ||\n                        (providerCallId.isBlank() && candidate.argumentsJson == argumentsJson && candidate.kind == presentation.kind))\n            }\n''',
    '''            val preparedIndex = timeline.indexOfLast { candidate ->\n                preparedToolCallMatches(candidate, providerCallId, argumentsJson, presentation)\n            }\n''',
)
replace_once(
    generation,
    '''            val existingIndex = timeline.indexOfLast { candidate ->\n                candidate.status in setOf("preparing", "prepared") &&\n                    ((call.id.isNotBlank() && candidate.providerCallId == call.id) ||\n                        (candidate.argumentsJson == call.argumentsJson && candidate.kind == presentation.kind))\n            }\n''',
    '''            val existingIndex = timeline.indexOfLast { candidate ->\n                preparedToolCallMatches(candidate, call.id, call.argumentsJson, presentation)\n            }\n''',
)

# 2, 4) Images chrome: keep content behind the blur so appearance opacity/softness
# actually changes what the user sees, and center the compact composer controls.
image = "app/src/main/java/app/xylune/chat/ui/ImageGenerationScreen.kt"
replace_once(
    image,
    '''                            modifier = Modifier.fillMaxWidth(),\n                            verticalAlignment = Alignment.Bottom,\n                        ) {\n''',
    '''                            modifier = Modifier.fillMaxWidth(),\n                            verticalAlignment = Alignment.CenterVertically,\n                        ) {\n''',
)
replace_once(
    image,
    '''                modifier = Modifier.fillMaxSize().padding(padding),\n                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),\n''',
    '''                modifier = Modifier.fillMaxSize(),\n                contentPadding = PaddingValues(\n                    start = 14.dp,\n                    end = 14.dp,\n                    top = padding.calculateTopPadding() + 14.dp,\n                    bottom = padding.calculateBottomPadding() + 14.dp,\n                ),\n''',
)

# 3) Anchor each message overflow menu to its own button instead of the footer row.
usage = "app/src/main/java/app/xylune/chat/ui/UsageDetailsUi.kt"
replace_once(
    usage,
    "import androidx.compose.foundation.layout.Column\n",
    "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Column\n",
)
replace_once(
    usage,
    '''    IconButton(onClick = { open = true }, modifier = Modifier.size(34.dp)) {\n        Icon(Icons.Outlined.MoreVert, "Message actions", Modifier.size(18.dp))\n    }\n    XyluneDropdownMenu(expanded = open, onDismissRequest = { open = false }) {\n        if (message.role == MessageRole.ASSISTANT) {\n            DropdownMenuItem(\n                text = { Text("Usage details") },\n                leadingIcon = { Icon(Icons.Outlined.DataUsage, null) },\n                onClick = {\n                    open = false\n                    showUsage = true\n                },\n            )\n        }\n        DropdownMenuItem(\n            text = { Text("Share message") },\n            leadingIcon = { Icon(Icons.Outlined.Share, null) },\n            onClick = {\n                open = false\n                scope.launch {\n                    val attachments = withContext(Dispatchers.IO) {\n                        container.database.attachmentDao().forMessage(message.nodeId)\n                    }\n                    shareMessage(context, message, attachments)\n                }\n            },\n        )\n    }\n''',
    '''    Box {\n        IconButton(onClick = { open = true }, modifier = Modifier.size(34.dp)) {\n            Icon(Icons.Outlined.MoreVert, "Message actions", Modifier.size(18.dp))\n        }\n        XyluneDropdownMenu(expanded = open, onDismissRequest = { open = false }) {\n            if (message.role == MessageRole.ASSISTANT) {\n                DropdownMenuItem(\n                    text = { Text("Usage details") },\n                    leadingIcon = { Icon(Icons.Outlined.DataUsage, null) },\n                    onClick = {\n                        open = false\n                        showUsage = true\n                    },\n                )\n            }\n            DropdownMenuItem(\n                text = { Text("Share message") },\n                leadingIcon = { Icon(Icons.Outlined.Share, null) },\n                onClick = {\n                    open = false\n                    scope.launch {\n                        val attachments = withContext(Dispatchers.IO) {\n                            container.database.attachmentDao().forMessage(message.nodeId)\n                        }\n                        shareMessage(context, message, attachments)\n                    }\n                },\n            )\n        }\n    }\n''',
)

# 4, 5) Restore Thinking + Search above the prompt, center the prompt row, and
# put the actual tool toggles directly in the + sheet.
chat = "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt"
replace_once(
    chat,
    '''            Row(verticalAlignment = Alignment.Bottom) {\n''',
    '''            if (providerConfigured && !generating && !imageGenerationMode) conversation?.let { current ->\n                LazyRow(\n                    modifier = Modifier\n                        .fillMaxWidth()\n                        .padding(bottom = 6.dp),\n                    verticalAlignment = Alignment.CenterVertically,\n                    horizontalArrangement = Arrangement.spacedBy(8.dp),\n                    contentPadding = androidx.compose.foundation.layout.PaddingValues(\n                        start = 36.dp,\n                        end = 56.dp,\n                    ),\n                ) {\n                    item {\n                        ThinkingComposerChip(\n                            enabled = current.thinkingEnabled,\n                            effort = current.thinkingEffort,\n                            provider = provider,\n                            model = model,\n                            onSelection = { enabled, effort ->\n                                viewModel.updateConversation {\n                                    it.copy(\n                                        thinkingEnabled = enabled,\n                                        thinkingEffort = effort ?: it.thinkingEffort,\n                                    )\n                                }\n                            },\n                        )\n                    }\n                    item {\n                        SearchComposerChip(\n                            webEnabled = current.webSearchEnabled,\n                            deepResearchEnabled = current.deepResearchEnabled,\n                            onSelection = { webEnabled, deepResearchEnabled ->\n                                viewModel.updateConversation {\n                                    it.copy(\n                                        webSearchEnabled = webEnabled,\n                                        deepResearchEnabled = deepResearchEnabled,\n                                    )\n                                }\n                            },\n                        )\n                    }\n                }\n            }\n\n            Row(verticalAlignment = Alignment.CenterVertically) {\n''',
)
replace_once(chat, 'else "Attachments, modes, and tools",', 'else "Attachments and tools",')

old_plus_tools = '''                if (!generating) conversation?.let { current ->\n                    Text(\n                        "Modes & tools",\n                        style = MaterialTheme.typography.titleMedium,\n                        fontWeight = FontWeight.SemiBold,\n                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),\n                    )\n                    Column(\n                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),\n                        verticalArrangement = Arrangement.spacedBy(8.dp),\n                    ) {\n                        ThinkingComposerChip(\n                            enabled = current.thinkingEnabled,\n                            effort = current.thinkingEffort,\n                            provider = provider,\n                            model = model,\n                            onSelection = { enabled, effort ->\n                                viewModel.updateConversation {\n                                    it.copy(\n                                        thinkingEnabled = enabled,\n                                        thinkingEffort = effort ?: it.thinkingEffort,\n                                    )\n                                }\n                            },\n                        )\n                        SearchComposerChip(\n                            webEnabled = current.webSearchEnabled,\n                            deepResearchEnabled = current.deepResearchEnabled,\n                            onSelection = { webEnabled, deepResearchEnabled ->\n                                viewModel.updateConversation {\n                                    it.copy(\n                                        webSearchEnabled = webEnabled,\n                                        deepResearchEnabled = deepResearchEnabled,\n                                    )\n                                }\n                            },\n                        )\n                        ToolComposerChip(\n                            pythonEnabled = current.agentPythonEnabled,\n                            linuxEnabled = current.agentUbuntuEnabled,\n                            linuxInstalled = linuxInstalled,\n                            linuxDistributionName = linuxDistributionName,\n                            onOpenLinuxSetup = onOpenLinuxSetup,\n                            onPythonEnabled = { enabled ->\n                                viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) }\n                            },\n                            onLinuxEnabled = { enabled ->\n                                viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) }\n                            },\n                        )\n                    }\n                }\n'''
new_plus_tools = '''                if (!generating) conversation?.let { current ->\n                    Text(\n                        "Tools",\n                        style = MaterialTheme.typography.titleMedium,\n                        fontWeight = FontWeight.SemiBold,\n                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),\n                    )\n                    ComposerToggleRow(\n                        icon = Icons.Outlined.Code,\n                        title = "Local Code Execution",\n                        subtitle = "Run Python in this chat's persistent workspace",\n                        checked = current.agentPythonEnabled,\n                        onCheckedChange = { enabled ->\n                            viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) }\n                        },\n                    )\n                    ComposerToggleRow(\n                        icon = Icons.Outlined.Terminal,\n                        title = "Linux",\n                        subtitle = if (linuxInstalled) {\n                            "Use the $linuxDistributionName tooling workspace"\n                        } else {\n                            "Install a Linux workspace before enabling"\n                        },\n                        checked = current.agentUbuntuEnabled && linuxInstalled,\n                        enabled = linuxInstalled,\n                        onCheckedChange = { enabled ->\n                            viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) }\n                        },\n                    )\n                    if (!linuxInstalled) {\n                        TextButton(\n                            onClick = {\n                                plusMenu = false\n                                onOpenLinuxSetup()\n                            },\n                            modifier = Modifier.padding(horizontal = 12.dp),\n                        ) {\n                            Text("Manage Linux workspace")\n                        }\n                    }\n                }\n'''
replace_once(chat, old_plus_tools, new_plus_tools)

# Regression coverage.
tool_test = "app/src/test/java/app/xylune/chat/generation/ToolCallStreamingTest.kt"
replace_once(
    tool_test,
    "package app.xylune.chat.generation\n\n",
    "package app.xylune.chat.generation\n\nimport app.xylune.chat.agent.MessageTimelineEvent\n",
)
replace_once(
    tool_test,
    '''    @Test fun unknownToolsStillExposeBoundedRawArguments() {\n        val presentation = toolCallPresentation("custom_tool", "{" + "x".repeat(5_000))\n        assertEquals("tool_call", presentation.kind)\n        assertEquals(4_000, presentation.input.length)\n        assertTrue(presentation.preparingLabel.contains("custom_tool"))\n    }\n''',
    '''    @Test fun unknownToolsStillExposeBoundedRawArguments() {\n        val presentation = toolCallPresentation("custom_tool", "{" + "x".repeat(5_000))\n        assertEquals("tool_call", presentation.kind)\n        assertEquals(4_000, presentation.input.length)\n        assertTrue(presentation.preparingLabel.contains("custom_tool"))\n    }\n\n    @Test fun streamedSearchPreparationMergesWithFinalSearchCall() {\n        val query = "RMX1921_11_F.06 firmware changelog security patch May 2022"\n        val prepared = MessageTimelineEvent(\n            kind = "search",\n            label = "Prepared web search",\n            status = "prepared",\n            input = query,\n            providerCallId = "",\n            argumentsJson = """{\"query\":\"$query\"}""",\n            startedAt = 1L,\n        )\n        val finalPresentation = ToolCallPresentation(\n            kind = "search",\n            preparingLabel = "Preparing DuckDuckGo search",\n            runningLabel = "Searching with DuckDuckGo",\n            completedLabel = "DuckDuckGo search",\n            input = query,\n        )\n\n        assertTrue(\n            preparedToolCallMatches(\n                prepared,\n                providerCallId = "provider-call-assigned-late",\n                argumentsJson = """{\"query\":\"$query\",\"source\":\"auto\"}""",\n                presentation = finalPresentation,\n            ),\n        )\n    }\n''',
)

image_test = "app/src/test/java/app/xylune/chat/ui/ImageWorkspaceRegressionTest.kt"
replace_once(
    image_test,
    '''        assertTrue(screen.contains("expandToMeasuredHeight = true"))\n''',
    '''        assertTrue(screen.contains("expandToMeasuredHeight = true"))\n        assertTrue(screen.contains("edgeSoftness = chromeEdgeSoftness"))\n        assertTrue(screen.contains("overlayOpacity = chromeOverlayOpacity"))\n        assertTrue(screen.contains("top = padding.calculateTopPadding() + 14.dp"))\n        assertTrue(screen.contains("bottom = padding.calculateBottomPadding() + 14.dp"))\n        assertFalse(screen.contains("Modifier.fillMaxSize().padding(padding)"))\n''',
)

slider_test = "app/src/test/java/app/xylune/chat/ui/XyluneSliderTest.kt"
text = read(slider_test)
pattern = re.compile(
    r'''    @Test\n    fun composerModesAndToolsLiveInThePlusMenuWithShortLabels\(\) \{.*?\n    \}\n\n    @Test\n    fun opaqueTintExplainsWhyBlurCannotBeVisible''',
    re.S,
)
replacement = '''    @Test\n    fun thinkingAndSearchStayAbovePromptWhileToolsLiveInPlusMenu() {\n        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()\n        val composer = chat.substringAfter("private fun Composer(").substringBefore("private fun StagedAttachmentPreview")\n        val promptArea = composer.substringBefore("if (plusMenu)")\n        val plusMenu = composer.substringAfter("if (plusMenu)").substringBefore("if (sendMenu)")\n\n        assertTrue(promptArea.contains("ThinkingComposerChip("))\n        assertTrue(promptArea.contains("SearchComposerChip("))\n        assertTrue(promptArea.contains("Row(verticalAlignment = Alignment.CenterVertically)"))\n        assertFalse(promptArea.contains("ToolComposerChip("))\n        assertFalse(promptArea.contains("Row(verticalAlignment = Alignment.Bottom)"))\n        assertTrue(plusMenu.contains("\\\"Tools\\\""))\n        assertTrue(plusMenu.contains("title = \\\"Local Code Execution\\\""))\n        assertTrue(plusMenu.contains("title = \\\"Linux\\\""))\n        assertTrue(plusMenu.contains("ComposerToggleRow("))\n        assertFalse(plusMenu.contains("ThinkingComposerChip("))\n        assertFalse(plusMenu.contains("SearchComposerChip("))\n        assertFalse(chat.contains("Think ·"))\n        assertFalse(chat.contains("searchSettings.activeLabel"))\n        assertFalse(chat.contains("Tools ·"))\n    }\n\n    @Test\n    fun opaqueTintExplainsWhyBlurCannotBeVisible'''
text2, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"{slider_test}: failed to replace composer placement regression test ({count})")
write(slider_test, text2)

message_test = "app/src/test/java/app/xylune/chat/ui/MessageUsageMenuRegressionTest.kt"
replace_once(
    message_test,
    '''        assertTrue(chat.contains("MessageContextMenu(message)"))\n''',
    '''        assertTrue(chat.contains("MessageContextMenu(message)"))\n        assertTrue(usageUi.contains("Box {\\n        IconButton(onClick = { open = true }"))\n''',
)

# Release version.
build = "app/build.gradle.kts"
replace_once(build, 'versionCode = 205\n        versionName = "0.24.16"', 'versionCode = 206\n        versionName = "0.24.17"')

notes = ROOT / "docs/releases/RELEASE_NOTES_0.24.17.md"
notes.write_text('''# Xylune 0.24.17\n\n- Web-search preparation now morphs into the executing search card instead of leaving a duplicate prepared-search card above it.\n- Thinking and Search controls are back above the prompt with their compact labels, while Python and Linux toggles live directly under the + menu.\n- Prompt, +, and send controls are vertically centered in chat and the Images workspace.\n- Images now keeps workspace content behind the bottom chrome, so Appearance overlay opacity and edge softness visibly apply there too.\n- Message overflow menus are anchored to the message action button instead of the wider footer layout.\n''')

print("Applied Xylune 0.24.17 UI polish patch")
