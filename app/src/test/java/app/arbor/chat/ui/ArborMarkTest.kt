package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArborMarkTest {
    @Test
    fun `in app Arbor marks reuse exact launcher artwork`() {
        val visuals = File("src/main/java/app/arbor/chat/ui/PaletteVisuals.kt").readText()
        assertTrue(visuals.contains("LocalArborIconPalette"))
        assertTrue(visuals.contains("painterResource(palette.launcherPreviewDrawable)"))
        assertFalse(visuals.contains("Canvas("))
        assertFalse(visuals.contains("drawPath("))

        val main = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        assertTrue(main.contains("LocalArborIconPalette provides"))
        assertTrue(main.contains("matchLauncherIconToPalette"))
    }

    @Test
    fun `drawer onboarding and licenses share the dynamic Arbor mark`() {
        val onboarding = File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        val sidebar = File("src/main/java/app/arbor/chat/ui/ConversationSidebar.kt").readText()
        val licenses = File("src/main/java/app/arbor/chat/ui/LicenseCatalogScreen.kt").readText()
        assertTrue(onboarding.contains("ArborMark("))
        assertTrue(sidebar.contains("ArborMark("))
        assertTrue(licenses.contains("component.id == \"arbor\""))
        assertTrue(licenses.contains("ArborMark("))
    }
}
