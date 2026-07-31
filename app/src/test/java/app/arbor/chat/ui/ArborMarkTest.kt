package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArborMarkTest {
    @Test
    fun `in app Arbor marks use exact launcher palette artwork`() {
        val visuals = File("src/main/java/app/arbor/chat/ui/PaletteVisuals.kt").readText()
        assertTrue(visuals.contains("LocalArborColorPalette.current"))
        assertTrue(visuals.contains("Color(0xFF083A2C)"))
        assertTrue(visuals.contains("Color(0xFF00677A)"))
        assertTrue(visuals.contains("Color(0xFF67508F)"))
        assertTrue(visuals.contains("cornerRadius = CornerRadius(24f * unit"))
        assertFalse(visuals.contains("colors.primaryContainer"))

        val onboarding = File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        val sidebar = File("src/main/java/app/arbor/chat/ui/ConversationSidebar.kt").readText()
        val licenses = File("src/main/java/app/arbor/chat/ui/LicenseCatalogScreen.kt").readText()
        assertTrue(onboarding.contains("ArborMark("))
        assertTrue(sidebar.contains("ArborMark("))
        assertTrue(licenses.contains("component.id == \"arbor\""))
        assertTrue(licenses.contains("ArborMark("))
        assertFalse(onboarding.contains("R.drawable.ic_arbor_mark"))
        assertFalse(sidebar.contains("R.drawable.ic_arbor_mark"))
    }
}
