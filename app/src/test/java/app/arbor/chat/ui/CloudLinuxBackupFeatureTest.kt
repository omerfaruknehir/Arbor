package app.arbor.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLinuxBackupFeatureTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun driveAuthorizationRequestsOnlyAppDataScope() {
        val source = source("src/main/java/app/arbor/chat/ui/CloudBackupUi.kt")
        assertTrue(source.contains("Scope(Scopes.DRIVE_APPFOLDER)"))
        assertFalse(source.contains("Scopes.DRIVE_FILE"))
        assertFalse(source.contains("Scopes.DRIVE_READONLY"))
    }

    @Test
    fun portableBackupCanIncludeLinuxEnvironments() {
        val archive = source("src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt")
        val linux = source("src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt")
        assertTrue(archive.contains("includeLinuxEnvironments: Boolean = false"))
        assertTrue(archive.contains("linuxEnvironments.prepareSnapshots()"))
        assertTrue(linux.contains(".restore-"))
        assertTrue(linux.contains("runtime.properties"))
    }

    @Test
    fun androidBackupIncludesOnlySmallNonSecretPreferences() {
        val manifest = source("src/main/AndroidManifest.xml")
        val rules = source("src/main/res/xml/data_extraction_rules.xml")
        assertTrue(manifest.contains("android:allowBackup"))
        assertTrue(manifest.contains("@xml/backup_rules"))
        assertTrue(rules.contains("arbor_app_settings.xml"))
        assertTrue(rules.contains("arbor_ui_session.xml"))
        assertTrue(rules.contains("arbor_linux_runtime.xml"))
        assertFalse(rules.contains("arbor_secrets.xml"))
        assertFalse(rules.contains("domain=\"database\""))
        assertFalse(rules.contains("domain=\"file\""))
    }
}
