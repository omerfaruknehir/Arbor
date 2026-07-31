package app.arbor.chat.ui

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
        val source = java.io.File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
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
        assertTrue(source.contains("Open Linux environment manager"))
        assertTrue(source.contains("Exit setup for now"))
        assertTrue(source.contains("BackHandler(enabled = stepIndex > 0)"))
    }

    @Test
    fun `popup back hides keyboard and ignores system edge origins`() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/ReleaseDismissPopup.kt").readText()
        assertTrue(source.contains("WindowInsets.ime.getBottom"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("startedInBackEdge"))
        assertTrue(source.contains("dismissOnClickOutside = false"))
        assertTrue(source.contains("fun ArborAlertDialog"))
    }

    @Test
    fun `chat exposes provider and Linux setup states`() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        assertTrue(chat.contains("Connect a model provider"))
        assertTrue(chat.contains("Set up a provider to start"))
        assertTrue(chat.contains("Linux workspace not installed"))
        assertTrue(chat.contains("Manage Linux workspace"))
    }

    @Test
    fun `Linux management has one owner and a first install checklist`() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val workspace = java.io.File("src/main/java/app/arbor/chat/ui/SandboxScreen.kt").readText()
        val terminal = java.io.File("src/main/java/app/arbor/chat/ui/LinuxTerminalScreen.kt").readText()
        assertTrue(settings.contains("Manage tool workspace"))
        assertTrue(workspace.contains("Before the first install"))
        assertTrue(workspace.contains("Install \${ubuntuStatus.distribution.displayName}"))
        assertTrue(workspace.contains("Remove Linux workspace"))
        assertFalse(terminal.contains("selectLinuxDistribution"))
        assertFalse(terminal.contains("installUbuntu"))
        assertFalse(terminal.contains("removeUbuntu"))
    }
}
