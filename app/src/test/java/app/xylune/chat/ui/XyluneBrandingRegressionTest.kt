package app.xylune.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XyluneBrandingRegressionTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun everyXyluneVectorUsesApprovedGeometry() {
        val roots = listOf(File("src/main/res/drawable"), File("src/main/res/drawable-v31"))
        val vectors = roots.flatMap { root ->
            root.listFiles().orEmpty().filter { it.name.startsWith("ic_xylune_foreground") || it.name.startsWith("ic_xylune_mark") }
        }
        assertTrue(vectors.isNotEmpty())
        vectors.forEach { file ->
            val value = file.readText()
            assertTrue(file.path, value.contains("M33.549193,80.863216C45.542258,64.507039 58.821502,47.408289 73.585895,32.881898"))
            assertTrue(file.path, value.contains("M33.99223,32.881898C48.756623,47.408289 62.035867,64.507039 74.028932,80.863216"))
            assertTrue(file.path, value.contains("M39.107895,30.166046C43.79571,20.768808 52.715523,17.003434 60.890902,20.847009C59.491039,30.710867 51.981892,36.353531 40.896179,34.109428Z"))
            assertFalse(file.path, value.contains("M40,64C49,60 59,60 68,64"))
            assertTrue(file.path, value.contains("android:viewportWidth=\"108\""))
            assertTrue(file.path, value.contains("android:viewportHeight=\"108\""))
        }
    }

    @Test
    fun launcherVariantsShareMonochromeAndPreviewScale() {
        File("src/main/res/mipmap-anydpi").listFiles().orEmpty()
            .filter { it.name.startsWith("ic_launcher") && it.extension == "xml" }
            .forEach { file ->
      val hasMonochrome = file.readText().contains("ic_xylune_monochrome")
      if (file.name == "ic_launcher_system.xml") assertFalse(file.path, hasMonochrome)
      else assertTrue(file.path, hasMonochrome)
  }
        assertTrue(source("src/main/java/app/xylune/chat/ui/PaletteVisuals.kt").contains("ContentScale.Fit"))
    }

    @Test
    fun cloudProvidersUseGeneratedOfficialArtwork() {
        val nodpi = File("src/main/res/drawable-nodpi")
        listOf("ic_google_drive.png", "ic_onedrive.png", "ic_dropbox.png", "ic_nextcloud.png")
            .forEach { name -> assertTrue(name, File(nodpi, name).length() > 0L) }
        val legacy = File("src/main/res/drawable")
        listOf("ic_google_drive.xml", "ic_onedrive.xml", "ic_dropbox.xml", "ic_nextcloud.xml")
            .forEach { name -> assertFalse(name, File(legacy, name).exists()) }
    }
}
