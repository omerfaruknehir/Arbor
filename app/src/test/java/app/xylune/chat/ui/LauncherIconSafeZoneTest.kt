package app.xylune.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherIconSafeZoneTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun adaptiveLauncherArtworkUsesAnInsetSafeZoneAcrossEveryPalette() {
        val launchers = mapOf(
            "ic_launcher.xml" to "ic_launcher_foreground_safe",
            "ic_launcher_round.xml" to "ic_launcher_foreground_safe",
            "ic_launcher_graphite.xml" to "ic_launcher_foreground_graphite_safe",
            "ic_launcher_ocean.xml" to "ic_launcher_foreground_ocean_safe",
            "ic_launcher_sunset.xml" to "ic_launcher_foreground_sunset_safe",
            "ic_launcher_system.xml" to "ic_launcher_foreground_system_safe",
            "ic_launcher_violet.xml" to "ic_launcher_foreground_violet_safe",
        )
        launchers.forEach { (name, foreground) ->
            val xml = source("src/main/res/mipmap-anydpi/$name")
            assertTrue(name, xml.contains("@drawable/$foreground"))
            if (name == "ic_launcher_system.xml") {
                assertFalse(name, xml.contains("<monochrome"))
            } else {
                assertTrue(name, xml.contains("@drawable/ic_xylune_monochrome_safe"))
            }
        }

        val wrappers = listOf(
            "ic_launcher_foreground_safe.xml",
            "ic_launcher_foreground_graphite_safe.xml",
            "ic_launcher_foreground_ocean_safe.xml",
            "ic_launcher_foreground_sunset_safe.xml",
            "ic_launcher_foreground_system_safe.xml",
            "ic_launcher_foreground_violet_safe.xml",
            "ic_xylune_monochrome_safe.xml",
        )
        wrappers.forEach { name ->
            val xml = source("src/main/res/drawable/$name")
            listOf("Left", "Top", "Right", "Bottom").forEach { edge ->
                assertTrue(name, xml.contains("android:inset$edge=\"10dp\""))
            }
        }
    }

    @Test
    fun android12SplashUsesDedicatedFittedArtworkInsteadOfTheAdaptiveIcon() {
        val styles = source("src/main/res/values-v31/styles.xml")
        assertFalse(styles.contains("android:windowSplashScreenAnimatedIcon\">@mipmap/"))

        val variants = mapOf(
            "ic_splash_xylune.xml" to "ic_xylune_mark",
            "ic_splash_graphite.xml" to "ic_xylune_mark_graphite",
            "ic_splash_ocean.xml" to "ic_xylune_mark_ocean",
            "ic_splash_sunset.xml" to "ic_xylune_mark_sunset",
            "ic_splash_system.xml" to "ic_xylune_mark_system",
            "ic_splash_violet.xml" to "ic_xylune_mark_violet",
        )
        variants.forEach { (name, mark) ->
            assertTrue(styles, styles.contains("@drawable/${name.removeSuffix(".xml")}"))
            val xml = source("src/main/res/drawable/$name")
            assertTrue(name, xml.contains("android:drawable=\"@drawable/$mark\""))
            listOf("Left", "Top", "Right", "Bottom").forEach { edge ->
                assertTrue(name, xml.contains("android:inset$edge=\"18dp\""))
            }
        }
    }
}
