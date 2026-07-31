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
    fun `launcher aliases use palette themed trampolines instead of the running activity`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        LauncherIconManager.allAliases.forEach { alias ->
            assertTrue(manifest.contains(".${alias.substringAfterLast('.')}"))
        }

        val mainActivity = manifest.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")
        assertFalse(mainActivity.contains("android.intent.category.LAUNCHER"))
        assertTrue(mainActivity.contains("android:launchMode=\"singleTask\""))

        val expectedTargets = listOf(
            ".LauncherArborActivity",
            ".LauncherSystemActivity",
            ".LauncherGraphiteActivity",
            ".LauncherOceanActivity",
            ".LauncherVioletActivity",
            ".LauncherSunsetActivity",
        )
        val aliasBlocks = Regex("<activity-alias[\\s\\S]*?</activity-alias>")
            .findAll(manifest)
            .map { it.value }
            .toList()
        assertEquals(LauncherIconManager.allAliases.size, aliasBlocks.size)
        expectedTargets.zip(aliasBlocks).forEach { (target, block) ->
            assertTrue(block.contains("android:targetActivity=\"$target\""))
            assertFalse(block.contains("android:targetActivity=\".MainActivity\""))
        }

        val trampoline = File("src/main/java/app/arbor/chat/LauncherActivity.kt").readText()
        expectedTargets.forEach { target -> assertTrue(trampoline.contains("class ${target.removePrefix(".")}")) }
        assertTrue(trampoline.contains("Intent(this, MainActivity::class.java)"))
        assertTrue(trampoline.contains("finish()"))
    }

    @Test
    fun `icon changes use an intentional stateful restart`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        assertTrue(source.contains("requestStatefulRestart"))
        assertTrue(source.contains("PendingIntent"))
        assertFalse(source.contains("KEY_PENDING_ALIAS"))
        assertFalse(source.contains("flushPending"))

        val mainActivity = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        assertTrue(mainActivity.contains("viewModel.flushPersistentState()"))
        assertTrue(mainActivity.contains("finishAffinity()"))
        assertTrue(mainActivity.contains("ComponentName(packageName, desiredAlias)"))

        val application = File("src/main/java/app/arbor/chat/ArborApplication.kt").readText()
        assertFalse(application.contains("TRIM_MEMORY_UI_HIDDEN"))
        assertFalse(application.contains("flushPending"))
    }

    @Test
    fun `isolated icon switching is atomic and relaunches the saved session`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        assertTrue(source.contains("setComponentEnabledSettings"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertTrue(source.contains("applyEnableFirst"))

        val receiver = File("src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt").readText()
        assertTrue(receiver.contains("BroadcastReceiver"))
        assertTrue(receiver.contains("LauncherIconManager.applyDirect"))
        assertTrue(receiver.contains("relaunch.send()"))
        assertTrue(receiver.contains("AlarmManager.ELAPSED_REALTIME_WAKEUP"))
        assertTrue(receiver.contains("goAsync()"))
    }


    @Test
    fun `dynamic launcher artwork uses actual Android system palette resources`() {
        val background = File("src/main/res/drawable-v31/ic_arbor_background_system.xml").readText()
        val foreground = File("src/main/res/drawable-v31/ic_arbor_foreground_system.xml").readText()
        assertTrue(background.contains("@android:color/system_accent1_800"))
        assertTrue(background.contains("@android:color/system_accent2_700"))
        assertTrue(foreground.contains("@android:color/system_accent1_200"))
        assertTrue(foreground.contains("@android:color/system_accent3_200"))
        val splash = File("src/main/res/values-v31/styles.xml").readText()
        assertTrue(splash.contains("Theme.Arbor.Launcher.System"))
        assertTrue(splash.contains("@mipmap/ic_launcher_system"))
        assertTrue(splash.contains("@color/arbor_splash_system"))
    }
}
