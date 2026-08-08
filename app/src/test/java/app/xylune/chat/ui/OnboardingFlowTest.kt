package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    @Test
    fun `setup page count matches progress segments`() {
        assertEquals(5, SETUP_PAGE_COUNT)
        assertEquals(4, SETUP_PROGRESS_SEGMENTS)
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
    fun `popup back remains keyboard safe while ordinary outside taps dismiss`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/ReleaseDismissPopup.kt").readText()
        val alert = source.substringAfter("fun XyluneAlertDialog").substringBefore("/** Dropdown menu")
        val beforeMaterialDialog = alert.substringBefore("MaterialAlertDialog(")
        val dropdown = source.substringAfter("internal fun XyluneDropdownMenu")

        assertTrue(source.contains("val imeInsets = WindowInsets.ime"))
        assertTrue(source.contains("val imeVisibleAtGestureStart = imeInsets.getBottom(density) > 0"))
        assertTrue(source.contains("keyboard?.hide()"))
        assertTrue(source.contains("focusManager.clearFocus(force = true)"))
        assertFalse(beforeMaterialDialog.contains("XylunePopupBackHandler("))
        assertTrue(alert.contains("confirmButton = {"))
        assertTrue(alert.contains("XylunePopupBackHandler("))
        assertTrue(alert.contains("dismissOnBackPress = false"))
        assertTrue(alert.contains("dismissOnClickOutside = true"))
        assertTrue(dropdown.contains("dismissOnClickOutside: Boolean = true"))
        assertTrue(dropdown.contains("focusable = true"))
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
    }

    @Test
    fun `local execution setup is managed outside onboarding`() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/OnboardingScreen.kt").readText()
        val app = java.io.File("src/main/java/app/xylune/chat/ui/XyluneApp.kt").readText()
        assertTrue(source.contains("Python and Linux are managed later from Settings → Local execution"))
        assertFalse(source.contains("Choose local tools"))
        assertFalse(source.contains("onOpenLinuxSetup"))
        assertFalse(app.contains("viewModel.ubuntuStatus.collectAsState()"))
        assertFalse(app.contains("linuxStatus = ubuntuStatus"))
    }
}
