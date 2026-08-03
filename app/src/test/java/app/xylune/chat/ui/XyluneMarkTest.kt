package app.xylune.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class XyluneMarkTest {
    @Test
    fun `in app Xylune marks reuse exact launcher artwork`() {
        val visuals = File("src/main/java/app/xylune/chat/ui/PaletteVisuals.kt").readText()
        assertTrue(visuals.contains("LocalXyluneIconPalette"))
        assertTrue(visuals.contains("painterResource(palette.launcherPreviewDrawable)"))
        assertFalse(visuals.contains("Canvas("))
        assertFalse(visuals.contains("drawPath("))

        val main = File("src/main/java/app/xylune/chat/MainActivity.kt").readText()
        assertTrue(main.contains("LocalXyluneIconPalette provides"))
        assertTrue(main.contains("matchLauncherIconToPalette"))
    }

    @Test
    fun `drawer onboarding and licenses share the dynamic Xylune mark`() {
        val onboarding = File("src/main/java/app/xylune/chat/ui/OnboardingScreen.kt").readText()
        val sidebar = File("src/main/java/app/xylune/chat/ui/ConversationSidebar.kt").readText()
        val licenses = File("src/main/java/app/xylune/chat/ui/LicenseCatalogScreen.kt").readText()
        assertTrue(onboarding.contains("XyluneMark("))
        assertTrue(sidebar.contains("XyluneMark("))
        assertTrue(licenses.contains("component.id == \"xylune\""))
        assertTrue(licenses.contains("XyluneMark("))
    }
}
