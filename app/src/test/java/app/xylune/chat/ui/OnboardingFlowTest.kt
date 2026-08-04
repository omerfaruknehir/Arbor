package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    @Test
    fun `startup wait has a bounded escape path`() {
        assertTrue(shouldBlockForProviderCatalog(catalogReady = false, graceExpired = false))
        assertFalse(shouldBlockForProviderCatalog(catalogReady = false, graceExpired = true))
        assertFalse(shouldBlockForProviderCatalog(catalogReady = true, graceExpired = false))
    }

    @Test
    fun `setup appears only after the provider catalog is usable`() {
        assertFalse(shouldShowProviderOnboarding(false, false, false))
        assertTrue(shouldShowProviderOnboarding(true, false, false))
    }

    @Test
    fun `configured or session-dismissed users reach the app`() {
        assertFalse(shouldShowProviderOnboarding(true, true, false))
        assertFalse(shouldShowProviderOnboarding(true, false, true))
    }

    @Test
    fun `onboarding covers appearance providers tools and safe exits`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/OnboardingScreen.kt").readText()
        assertTrue(source.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
        assertTrue(source.contains("OnboardingStep.APPEARANCE"))
        assertTrue(source.contains("OnboardingStep.PROVIDER"))
        assertTrue(source.contains("OnboardingStep.TOOLS"))
        assertTrue(source.contains("OnboardingStep.READY"))
        assertTrue(source.contains("ColorPalette.OCEAN"))
        assertTrue(source.contains("ColorPalette.VIOLET"))
        assertTrue(source.contains("ColorPalette.SUNSET"))
        assertTrue(source.contains("Match launcher icon"))
        assertTrue(source.contains("palettePreviewColors(palette, currentThemeMode)"))
        assertFalse(source.contains("ColorPalette.SYSTEM -> MaterialTheme.colorScheme.tertiary"))
        assertTrue(source.contains("Choose a Linux distribution"))
        assertTrue(source.contains("Choose and install a distribution"))
        assertTrue(source.contains("does not download anything"))
        assertTrue(source.contains("TextButton(onClick = onSkipForNow)"))
        assertTrue(source.contains("Skip for now"))
        assertTrue(source.contains("HorizontalPager("))
        assertTrue(source.contains("rememberPagerState("))
        assertTrue(source.contains("userScrollEnabled = true"))
        assertTrue(source.contains("pageSpacing = 16.dp"))
        assertTrue(source.contains("BackHandler(enabled = visiblePage > 0)"))
        assertTrue(source.contains("background(MaterialTheme.colorScheme.background)"))
        assertTrue(source.contains("pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction"))
        assertTrue(source.contains("verticalScroll(pageScrollStates[page])"))
        assertTrue(source.contains("setupProgressForSegment(pagePosition, index)"))
        assertFalse(source.contains("PredictiveNavigationHost("))
        assertFalse(source.contains("AnimatedContent("))
        assertFalse(source.contains("fadeIn("))
        assertFalse(source.contains("fadeOut("))
    }

    @Test
    fun `provider step acknowledges configured credentials`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/OnboardingScreen.kt").readText()
        val app = java.io.File("src/main/java/app/xylune/chat/ui/XyluneApp.kt").readText()
        assertTrue(source.contains("configuredProviderCount"))
        assertTrue(source.contains("Model access connected"))
        assertTrue(source.contains("Credentials were found and this step is complete"))
        assertTrue(source.contains("Manage providers"))
        assertTrue(app.contains("configuredProviderCount = configuredProviders.size"))
    }

    @Test
    fun `Linux setup distinguishes enablement from installation`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/OnboardingScreen.kt").readText()
        val app = java.io.File("src/main/java/app/xylune/chat/ui/XyluneApp.kt").readText()
        assertTrue(source.contains("No Linux distribution installed"))
        assertTrue(source.contains("review the download, and explicitly install it"))
        assertTrue(source.contains("Continue without Linux"))
        assertTrue(source.contains("linuxStatus.installed"))
        assertTrue(app.contains("val ubuntuStatus by viewModel.ubuntuStatus.collectAsState()"))
        assertTrue(app.contains("linuxStatus = ubuntuStatus"))
    }

    @Test
    fun `setup progress maps continuously`() {
        assertEquals(1f, setupProgressForSegment(0f, 0), 0f)
        assertEquals(0f, setupProgressForSegment(0f, 1), 0f)
        assertEquals(0.35f, setupProgressForSegment(0.35f, 1), 0.0001f)
        assertEquals(1f, setupProgressForSegment(1f, 1), 0f)
        assertEquals(0.5f, setupProgressForSegment(1.5f, 2), 0.0001f)
    }

    @Test
    fun `popup back hides keyboard and ignores system edge origins`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        assertTrue(source.contains("WindowInsets.ime.getBottom"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("startedInBackEdge"))
        assertTrue(source.contains("dismissOnClickOutside = false"))
        assertTrue(source.contains("fun XyluneAlertDialog"))
    }

    @Test
    fun `chat exposes provider and Linux setup states`() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val viewModel = java.io.File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        assertTrue(chat.contains("Connect a model provider"))
        assertTrue(chat.contains("Set up a provider to start"))
        assertTrue(chat.contains("onSetUpProvider = viewModel::openProviderSetup"))
        assertTrue(chat.contains("modifier = Modifier.zIndex(1f).padding("))
        assertTrue(viewModel.contains("fun openProviderSetup()"))
        assertTrue(viewModel.contains("openSettingsRoute(SettingsRoute.PROVIDERS)"))
        assertTrue(viewModel.contains("screen.value = Screen.SETTINGS"))
        assertTrue(chat.contains("Linux workspace not installed"))
        assertTrue(chat.contains("Manage Linux workspace"))
    }

    @Test
    fun `Linux management has one owner and a first install checklist`() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        val workspace = java.io.File("src/main/java/app/xylune/chat/ui/SandboxScreen.kt").readText()
        val terminal = java.io.File("src/main/java/app/xylune/chat/ui/LinuxTerminalScreen.kt").readText()
        assertTrue(settings.contains("Manage tool workspace"))
        assertTrue(workspace.contains("Before the first install"))
        assertTrue(workspace.contains("Install \${ubuntuStatus.distribution.displayName}"))
        assertTrue(workspace.contains("Step \$step of \$total"))
        assertTrue(workspace.contains("Elapsed \${formatSetupDuration(elapsedMs)}"))
        assertTrue(workspace.contains(".height(10.dp)"))
        assertTrue(workspace.contains("repeat(total)"))
        assertTrue(workspace.contains("Linux data on disk:"))
        assertTrue(workspace.contains("Remove Linux workspace"))
        assertFalse(terminal.contains("selectLinuxDistribution"))
        assertFalse(terminal.contains("installUbuntu"))
        assertFalse(terminal.contains("removeUbuntu"))
        val runtime = java.io.File("src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt").readText()
        assertTrue(runtime.contains("val currentStep: Int = 0"))
        assertTrue(runtime.contains("val totalSteps: Int = 0"))
        assertTrue(runtime.contains("Os.lstat(file.absolutePath)"))
        assertTrue(runtime.contains("countedInodes.add"))
        assertTrue(runtime.contains("builder.redirectOutput(stdoutLog)"))
        assertTrue(runtime.contains("builder.redirectError(stderrLog)"))
        assertTrue(runtime.contains("readLogTail(stdoutLog"))
        assertTrue(runtime.contains("APT::Status-Fd=3"))
        assertTrue(runtime.contains("additionalProgressFiles = listOf(statusFile)"))
        assertFalse(runtime.contains("APT::Status-Fd=1"))
        assertFalse(runtime.contains("root.walkTopDown().filter(File::isFile).sumOf(File::length)"))
    }

    @Test
    fun `provider detours return to the persisted setup step`() {
        val app = java.io.File("src/main/java/app/xylune/chat/ui/XyluneApp.kt").readText()
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        val viewModel = java.io.File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        assertTrue(app.contains("setupTemporarilyAway"))
        assertTrue(settings.contains("viewModel.returnToSetup()"))
        assertTrue(settings.contains("title = \"Finish setup\""))
        assertTrue(settings.contains("viewModel.startSetup(setupStepIndex)"))
        assertTrue(viewModel.contains("fun skipSetup()"))
        assertTrue(viewModel.contains("Setup was paused"))
        assertTrue(viewModel.contains("openProviderSetupFromSetup"))
        assertTrue(viewModel.contains("setupStepIndex.value = 2"))
    }
}
