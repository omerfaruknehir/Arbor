package app.arbor.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ArborHapticsIntegrationTest {
    @Test fun highSignalInteractionsUseTheSharedHapticVocabulary() {
        val chat = source("ChatScreen.kt")
        val settings = source("SettingsScreen.kt")
        val drawer = source("InteractiveNavigationDrawer.kt")
        val sidebar = source("ConversationSidebar.kt")

        assertTrue(chat.contains("haptics.confirm()"))
        assertTrue(chat.contains("haptics.reject()"))
        assertTrue(chat.contains("haptics.longPress()"))
        assertTrue(settings.contains("haptics.toggle(next)"))
        assertTrue(settings.contains("haptics.selection()"))
        assertTrue(drawer.contains("haptics.gestureStart()"))
        assertTrue(drawer.contains("haptics.snap()"))
        assertTrue(sidebar.contains("haptics.confirm()"))
        assertTrue(sidebar.contains("haptics.selection()"))
        assertTrue(chat.contains("streamHaptics.streamTick()"))
        assertTrue(chat.contains("streamHaptics.streamComplete()"))
        assertTrue(source("ArborHaptics.kt").contains("fun streamTick()"))
        assertTrue(source("ArborHaptics.kt").contains("fun streamComplete()"))
        assertTrue(chat.contains("STREAM_HAPTIC_CHARACTER_INTERVAL = 32"))
    }

    private fun source(name: String) = java.io.File("src/main/java/app/arbor/chat/ui/$name").readText()
}
