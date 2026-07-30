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
    fun `onboarding has contrast-safe root live theme selection and escape actions`() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        assertTrue(source.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
        assertTrue(source.contains("ThemeMode.SYSTEM"))
        assertTrue(source.contains("ThemeMode.LIGHT"))
        assertTrue(source.contains("ThemeMode.DARK"))
        assertTrue(source.contains("Skip for now"))
        assertTrue(source.contains("Continue without a provider"))
        assertTrue(source.contains("BackHandler(enabled = stepIndex > 0)"))
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
    fun `Linux management has one owner`() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val workspace = java.io.File("src/main/java/app/arbor/chat/ui/SandboxScreen.kt").readText()
        val terminal = java.io.File("src/main/java/app/arbor/chat/ui/LinuxTerminalScreen.kt").readText()
        assertTrue(settings.contains("Manage tool workspace"))
        assertTrue(workspace.contains("Install \${ubuntuStatus.distribution.displayName}"))
        assertTrue(workspace.contains("Remove Linux workspace"))
        assertFalse(terminal.contains("selectLinuxDistribution"))
        assertFalse(terminal.contains("installUbuntu"))
        assertFalse(terminal.contains("removeUbuntu"))
    }
}
