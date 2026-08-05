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
        assertTrue(restore.contains("Restore a backup"))
        assertTrue(restore.contains("ActivityResultContracts.OpenDocument()"))
        assertTrue(restore.contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(restore.contains("Scope(Scopes.DRIVE_APPFOLDER)"))
        assertFalse(restore.contains("Scopes.DRIVE_FILE"))
        assertFalse(restore.contains("Scopes.DRIVE_READONLY"))
    }

    @Test
    fun setupCloudRestoreAlwaysExposesProgressAndOutcome() {
        val restore = source("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt")
        assertTrue(restore.contains("operationMessage"))
        assertTrue(restore.contains("Google Drive sign-in was cancelled"))
        assertTrue(restore.contains("Folder selection was cancelled"))
        assertTrue(restore.contains("Waiting for \${provider.displayName} sign-in to finish"))
        assertTrue(restore.contains("no Xylune backups were found"))
        assertTrue(restore.contains("Unavailable in this build • tap for details"))
        assertTrue(restore.contains("viewModel.postNotice(\"Backup downloaded. Opening preview…\")"))
    }

    @Test
    fun setupCloudProviderArtworkHasExplicitCompactBounds() {
        val restore = source("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt")
        assertTrue(restore.contains("modifier = Modifier.size(28.dp)"))
        assertTrue(restore.contains("modifier = Modifier.size(26.dp)"))
        assertTrue(restore.contains("SetupCloudAction("))
        assertFalse(restore.contains("SetupProviderIcon(R.drawable.ic_google_drive, \"Google Drive\")\n                            Text"))
    }

    @Test
    fun webDavAndS3ConfigurationDoNotStackDialogs() {
        val restore = source("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt")
        assertTrue(restore.contains("cloudDialogOpen = false\n                                    webDavDialogOpen = true"))
        assertTrue(restore.contains("cloudDialogOpen = false\n                                    s3DialogOpen = true"))
        assertTrue(restore.contains("webDavDialogOpen = false\n                cloudDialogOpen = true"))
        assertTrue(restore.contains("s3DialogOpen = false\n                cloudDialogOpen = true"))
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
    fun restoredSettingsPauseSetupButKeepItResumable() {
        val viewModel = source("src/main/java/app/xylune/chat/ui/ChatViewModel.kt")
        val settings = source("src/main/java/app/xylune/chat/ui/SettingsScreen.kt")
        assertTrue(viewModel.contains("if (result.settingsRestored)"))
        assertTrue(viewModel.contains("setupActive.value = false"))
        assertTrue(viewModel.contains("setupStepIndex.value = 1"))
        assertTrue(settings.contains("Finish setup"))
    }

    @Test
    fun setupCanPreviewArchiveWithoutLeavingOnboarding() {
        val app = source("src/main/java/app/xylune/chat/ui/XyluneApp.kt")
        assertTrue(app.contains("IncomingArchiveDialog(viewModel, state)"))
        assertTrue(app.contains("OnboardingScreen("))
        assertTrue(app.contains("SnackbarHost(snackbar"))
    }
}
