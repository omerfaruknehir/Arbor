package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransferFeatureTest {
    @Test
    fun `provider setup selects all discovered models and stores only checked models`() {
        val settings = File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertTrue(settings.contains("selectedModelIds = models.mapTo(linkedSetOf())"))
        assertTrue(settings.contains("Checkbox(checked = checked"))
        assertTrue(settings.contains("draft.selectedModels.map"))
        assertFalse(settings.contains("RadioButton(selected = modelId == model.id"))
    }

    @Test
    fun `portable archives allow explicit unencrypted output and safe chat defaults`() {
        val ui = File("src/main/java/app/arbor/chat/ui/TransferUi.kt").readText()
        val archive = File("src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt").readText()
        assertTrue(ui.contains("leave blank for none"))
        assertTrue(ui.contains("includeReasoning by remember { mutableStateOf(false) }"))
        assertTrue(ui.contains("includeToolData by remember { mutableStateOf(false) }"))
        assertTrue(archive.contains("val encrypted = password.isNotEmpty()"))
        assertTrue(archive.contains("PBKDF2WithHmacSHA256"))
        assertTrue(archive.contains("AES/GCM/NoPadding"))
    }

    @Test
    fun `incoming archives preview and import as copies`() {
        val ui = File("src/main/java/app/arbor/chat/ui/TransferUi.kt").readText()
        val activity = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(ui.contains("Import creates separate local copies"))
        assertTrue(ui.contains("Import and continue"))
        assertTrue(activity.contains("receivePortableArchive"))
        assertTrue(manifest.contains("application/vnd.arbor.chat"))
    }
}
