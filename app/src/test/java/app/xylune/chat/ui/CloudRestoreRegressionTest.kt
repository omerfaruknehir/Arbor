package app.xylune.chat.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRestoreRegressionTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun directProviderDownloadsAreExposedToArchiveInspector() {
        val paths = source("src/main/res/xml/file_paths.xml")
        val clients = source("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt")
        assertTrue(clients.contains("cloud-backups/\$provider"))
        assertTrue(paths.contains("name=\"cloud_backups\""))
        assertTrue(paths.contains("path=\"cloud-backups/\""))
    }

    @Test
    fun setupRestoreUsesDistinctProviderIconsAndClearAction() {
        val restore = source("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt")
        assertTrue(restore.contains("R.drawable.ic_google_drive"))
        assertTrue(restore.contains("R.drawable.ic_onedrive"))
        assertTrue(restore.contains("R.drawable.ic_dropbox"))
        assertTrue(restore.contains("R.drawable.ic_nextcloud"))
        assertTrue(restore.contains("Icons.Outlined.Storage"))
        assertTrue(restore.contains("Review & restore"))
    }
}
