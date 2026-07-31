package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArborMarkTest {
    @Test
    fun `in app Arbor marks follow the active Material colors`() {
        val visuals = File("src/main/java/app/arbor/chat/ui/PaletteVisuals.kt").readText()
        assertTrue(visuals.contains("MaterialTheme.colorScheme"))
        assertTrue(visuals.contains("internal fun ArborMark"))
        assertTrue(visuals.contains("colors.primaryContainer"))
        assertTrue(visuals.contains("colors.tertiary"))

        val onboarding = File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        val sidebar = File("src/main/java/app/arbor/chat/ui/ConversationSidebar.kt").readText()
        assertTrue(onboarding.contains("ArborMark("))
        assertTrue(sidebar.contains("ArborMark("))
        assertFalse(onboarding.contains("R.drawable.ic_arbor_mark"))
        assertFalse(sidebar.contains("R.drawable.ic_arbor_mark"))
    }
}
