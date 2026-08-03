package app.xylune.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupRestoreSettingsFeatureTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun setupWelcomeOffersLocalAndLeastPrivilegeCloudRestore() {
        val onboarding = source("src/main/java/app/xylune/chat/ui/OnboardingScreen.kt")
        val restore = source("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt")
        assertTrue(onboarding.contains("SetupRestoreActions(viewModel)"))
        assertTrue(restore.contains("Restore from cloud"))
        assertTrue(restore.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(restore.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(restore.contains("Scope(Scopes.DRIVE_APPFOLDER)"))
        assertFalse(restore.contains("Scopes.DRIVE_FILE"))
        assertFalse(restore.contains("Scopes.DRIVE_READONLY"))
    }

    @Test
    fun portableBackupCarriesNonSecretSettingsAndOrganization() {
        val archive = source("src/main/java/app/xylune/chat/transfer/XyluneArchiveManager.kt")
        val settings = source("src/main/java/app/xylune/chat/transfer/AppSettingsArchiveStore.kt")
        assertTrue(archive.contains("includeAppSettings: Boolean = false"))
        assertTrue(archive.contains("appSettings.snapshot()"))
        assertTrue(settings.contains("PortablePreferenceSettings"))
        assertTrue(settings.contains("PortableProviderSettings"))
        assertTrue(settings.contains("PortableProjectSettings"))
        assertTrue(settings.contains("PortableSystemPromptSettings"))
        assertTrue(settings.contains("customHeadersJson = \"{}\""))
        assertFalse(settings.contains("SecureStore"))
        assertFalse(settings.contains("setApiKey"))
        assertFalse(settings.contains("accessToken"))
    }

    @Test
    fun setupCanPreviewArchiveWithoutLeavingOnboarding() {
        val app = source("src/main/java/app/xylune/chat/ui/XyluneApp.kt")
        assertTrue(app.contains("IncomingArchiveDialog(viewModel, state)"))
        assertTrue(app.contains("OnboardingScreen("))
        assertTrue(app.contains("SnackbarHost(snackbar"))
    }
}
