from pathlib import Path


chat_path = Path("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")
chat = chat_path.read_text()

# Remove the persistent thinking/search/tools strip above the message field.
start_marker = "                if (!imageGenerationMode) LazyRow(\n"
end_marker = "            }\n\n            Row(verticalAlignment = Alignment.Bottom) {"
start = chat.index(start_marker)
end = chat.index(end_marker, start)
chat = chat[:start] + chat[end:]

# Put the same controls in the existing + sheet instead.
plus_start = chat.index("    if (plusMenu) {\n")
plus_end = chat.index("    if (sendMenu) {\n", plus_start)
new_plus = '''    if (plusMenu) {
        ModalBottomSheet(onDismissRequest = { plusMenu = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text("Add to chat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                ComposerActionRow(Icons.Outlined.AttachFile, "Files", "Documents, archives, code, audio, and other supported files") {
                    plusMenu = false
                    filePicker.launch(arrayOf("*/*"))
                }
                ComposerActionRow(Icons.Outlined.Image, "Photos", "Choose one or more images") {
                    plusMenu = false
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                ComposerActionRow(Icons.Outlined.CameraAlt, "Camera", "Take a photo and attach it") {
                    plusMenu = false
                    takePhoto()
                }
                if (!generating) conversation?.let { current ->
                    Text(
                        "Modes & tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
                    )
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThinkingComposerChip(
                            enabled = current.thinkingEnabled,
                            effort = current.thinkingEffort,
                            provider = provider,
                            model = model,
                            onSelection = { enabled, effort ->
                                viewModel.updateConversation {
                                    it.copy(
                                        thinkingEnabled = enabled,
                                        thinkingEffort = effort ?: it.thinkingEffort,
                                    )
                                }
                            },
                        )
                        SearchComposerChip(
                            webEnabled = current.webSearchEnabled,
                            deepResearchEnabled = current.deepResearchEnabled,
                            onSelection = { webEnabled, deepResearchEnabled ->
                                viewModel.updateConversation {
                                    it.copy(
                                        webSearchEnabled = webEnabled,
                                        deepResearchEnabled = deepResearchEnabled,
                                    )
                                }
                            },
                        )
                        ToolComposerChip(
                            pythonEnabled = current.agentPythonEnabled,
                            linuxEnabled = current.agentUbuntuEnabled,
                            linuxInstalled = linuxInstalled,
                            linuxDistributionName = linuxDistributionName,
                            onOpenLinuxSetup = onOpenLinuxSetup,
                            onPythonEnabled = { enabled ->
                                viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) }
                            },
                            onLinuxEnabled = { enabled ->
                                viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) }
                            },
                        )
                    }
                }
            }
        }
    }

'''
chat = chat[:plus_start] + new_plus + chat[plus_end:]

old = '''                    if (options.isEmpty()) "Thinking unavailable"
                    else "Think · ${selected?.label ?: if (effectiveEnabled) effort.composerName else "Off"}",
'''
new = '''                    when {
                        options.isEmpty() -> "Unavailable"
                        !effectiveEnabled -> "Off"
                        else -> effectiveEffort.composerName
                    },
'''
if chat.count(old) != 1:
    raise SystemExit(f"thinking label match count={chat.count(old)}")
chat = chat.replace(old, new, 1)

old = '''    val context = LocalContext.current
    val searchSettings by remember(context) {
        (context.applicationContext as app.xylune.chat.XyluneApplication)
            .container.appPreferences.webSearchSettings
    }.collectAsState()
    val label = when {
        deepResearchEnabled -> "Research · ${searchSettings.activeLabel}"
        webEnabled -> "Search · ${searchSettings.activeLabel}"
        else -> "Search off"
    }
'''
new = '''    val label = when {
        deepResearchEnabled -> "Research"
        webEnabled -> "Search"
        else -> "Off"
    }
'''
if chat.count(old) != 1:
    raise SystemExit(f"search label match count={chat.count(old)}")
chat = chat.replace(old, new, 1)

old = '''    val label = when {
        pythonEnabled && effectiveLinuxEnabled -> "Tools · 2"
        pythonEnabled -> "Tools · Code"
        effectiveLinuxEnabled -> "Tools · Linux"
        else -> "Tools off"
    }
'''
new = '''    val label = when {
        pythonEnabled && effectiveLinuxEnabled -> "2 on"
        pythonEnabled -> "Code"
        effectiveLinuxEnabled -> "Linux"
        else -> "Off"
    }
'''
if chat.count(old) != 1:
    raise SystemExit(f"tool label match count={chat.count(old)}")
chat = chat.replace(old, new, 1)
chat = chat.replace(
    'else "Attach files, images, or a photo",',
    'else "Attachments, modes, and tools",',
    1,
)
chat_path.write_text(chat)

slider_path = Path("app/src/test/java/app/xylune/chat/ui/XyluneSliderTest.kt")
slider = slider_path.read_text()
test_start = slider.index(
    "    @Test\n    fun composerPillsUseTheFullWidthAsTheirHorizontalGestureViewport() {"
)
test_end = slider.index(
    "    @Test\n    fun opaqueTintExplainsWhyBlurCannotBeVisible()",
    test_start,
)
replacement = '''    @Test
    fun composerModesAndToolsLiveInThePlusMenuWithShortLabels() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val composer = chat.substringAfter("private fun Composer(").substringBefore("private fun StagedAttachmentPreview")
        val promptArea = composer.substringBefore("if (plusMenu)")
        val plusMenu = composer.substringAfter("if (plusMenu)").substringBefore("if (sendMenu)")

        assertFalse(promptArea.contains("ThinkingComposerChip("))
        assertFalse(promptArea.contains("SearchComposerChip("))
        assertFalse(promptArea.contains("ToolComposerChip("))
        assertTrue(plusMenu.contains("Text(\"Modes & tools\""))
        assertTrue(plusMenu.contains("ThinkingComposerChip("))
        assertTrue(plusMenu.contains("SearchComposerChip("))
        assertTrue(plusMenu.contains("ToolComposerChip("))
        assertFalse(chat.contains("Think ·"))
        assertFalse(chat.contains("searchSettings.activeLabel"))
        assertFalse(chat.contains("Tools ·"))
    }

'''
slider_path.write_text(slider[:test_start] + replacement + slider[test_end:])

onboarding_path = Path("app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt")
onboarding = onboarding_path.read_text()
old = '''    @Test
    fun `popup back hides keyboard and ignores system edge origins`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        assertTrue(source.contains("WindowInsets.ime.getBottom"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("startedInBackEdge"))
        assertTrue(source.contains("dismissOnClickOutside = false"))
        assertTrue(source.contains("fun XyluneAlertDialog"))
    }
'''
new = '''    @Test
    fun `popup back remains keyboard safe while ordinary outside taps dismiss`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        val alert = source.substringAfter("fun XyluneAlertDialog").substringBefore("/** Dropdown menu")
        val dropdown = source.substringAfter("internal fun XyluneDropdownMenu")
        assertTrue(source.contains("WindowInsets.ime.getBottom"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(alert.contains("onDismissRequest = onDismissRequest"))
        assertTrue(alert.contains("dismissOnClickOutside = true"))
        assertTrue(dropdown.contains("dismissOnClickOutside: Boolean = true"))
        assertTrue(dropdown.contains("focusable = true"))
    }
'''
if onboarding.count(old) != 1:
    raise SystemExit(f"onboarding popup test match count={onboarding.count(old)}")
onboarding_path.write_text(onboarding.replace(old, new, 1))

notes_path = Path("docs/releases/RELEASE_NOTES_0.24.16.md")
notes = notes_path.read_text().rstrip()
if "## Cleaner composer controls" not in notes:
    notes += '''

## Cleaner composer controls

Thinking, search, and execution controls now live under the + menu instead of permanently consuming a row above the prompt. Their visible labels are compact: thinking shows only the current effort, search shows Search or Research, and tools show the active tool state without redundant prefixes.

## Reliable popup dismissal

Thinking/search/tool menus, Xylune dialogs, and anchored link/source previews now use native focusable outside-tap dismissal. Tapping outside closes the popup instead of leaving it stuck on screen, while predictive Back remains keyboard-aware for dialogs.
'''
notes_path.write_text(notes + "\n")
