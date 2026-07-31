package app.arbor.chat.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LauncherIconManagerTest {
    @Test
    fun `disabled matching always uses classic Arbor icon`() {
        ColorPalette.entries.forEach { palette ->
            assertEquals(
                LauncherIconManager.ARBOR_ALIAS,
                LauncherIconManager.aliasClassName(matchPalette = false, palette = palette),
            )
        }
    }

    @Test
    fun `every palette has a stable unique launcher alias`() {
        val aliases = ColorPalette.entries.map {
            LauncherIconManager.aliasClassName(matchPalette = true, palette = it)
        }
        assertEquals(ColorPalette.entries.size, aliases.distinct().size)
        assertEquals(LauncherIconManager.allAliases.toSet(), aliases.toSet())
    }

    @Test
    fun `manifest exposes exactly one default launcher alias`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        LauncherIconManager.allAliases.forEach { alias ->
            assertTrue(manifest.contains(".${alias.substringAfterLast('.')}"))
        }

        val mainActivity = manifest.substringAfter(".MainActivity")
            .substringBefore("</activity>")
        assertFalse(mainActivity.contains("android.intent.category.LAUNCHER"))

        val arborAlias = manifest.substringAfter(".LauncherArbor")
            .substringBefore("</activity-alias>")
        assertTrue(arborAlias.contains("android:enabled"))
        assertTrue(arborAlias.contains("true"))
    }
}
