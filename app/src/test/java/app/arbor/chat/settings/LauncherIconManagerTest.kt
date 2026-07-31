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
    fun `launcher aliases use a stable trampoline instead of the running activity`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        LauncherIconManager.allAliases.forEach { alias ->
            assertTrue(manifest.contains(".${alias.substringAfterLast('.')}"))
        }

        val mainActivity = manifest.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        assertFalse(mainActivity.contains("android.intent.category.LAUNCHER"))
        assertTrue(mainActivity.contains("android:launchMode=\"singleTask\""))
        assertTrue(manifest.contains("android:name=\".LauncherActivity\""))

        val aliasBlocks = Regex("<activity-alias[\\s\\S]*?</activity-alias>")
            .findAll(manifest)
            .map { it.value }
            .toList()
        assertEquals(LauncherIconManager.allAliases.size, aliasBlocks.size)
        aliasBlocks.forEach { block ->
            assertTrue(block.contains("android:targetActivity=\".LauncherActivity\""))
            assertFalse(block.contains("android:targetActivity=\".MainActivity\""))
        }

        val trampoline = File("src/main/java/app/arbor/chat/LauncherActivity.kt").readText()
        assertTrue(trampoline.contains("Intent(this, MainActivity::class.java)"))
        assertTrue(trampoline.contains("finish()"))
    }

    @Test
    fun `foreground settings only queue icon changes and hidden lifecycle flushes them`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        val foregroundApply = source.substringAfter("fun apply(context:")
            .substringBefore("fun flushPending")
        assertTrue(foregroundApply.contains("KEY_PENDING_ALIAS"))
        assertFalse(foregroundApply.contains("sendBroadcast"))
        assertFalse(foregroundApply.contains("setComponentEnabledSetting"))
        assertTrue(source.contains("fun flushPending"))
        assertTrue(source.contains("LauncherIconSwitchReceiver::class.java"))

        val mainActivity = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        assertTrue(mainActivity.contains("override fun onStop()"))
        assertTrue(mainActivity.contains("LauncherIconManager.flushPending"))

        val application = File("src/main/java/app/arbor/chat/ArborApplication.kt").readText()
        assertTrue(application.contains("TRIM_MEMORY_UI_HIDDEN"))
        assertTrue(application.contains("LauncherIconManager.flushPending"))
    }

    @Test
    fun `background icon switching is atomic and acknowledges only the applied alias`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        assertTrue(source.contains("setComponentEnabledSettings"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertTrue(source.contains("applyEnableFirst"))
        assertTrue(source.contains("markApplied"))

        val receiver = File("src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt").readText()
        assertTrue(receiver.contains("BroadcastReceiver"))
        assertTrue(receiver.contains("LauncherIconManager.applyDirect"))
        assertTrue(receiver.contains("LauncherIconManager.markApplied"))
    }
}
