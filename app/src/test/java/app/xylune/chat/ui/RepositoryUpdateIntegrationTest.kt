package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryUpdateIntegrationTest {
    @Test
    fun androidWorkflowEmbedsRepositoryAndSignedManifest() {
        val workflow = java.io.File("../.github/workflows/android.yml").readText()
        assertTrue(workflow.contains("XYLUNE_SOURCE_REPOSITORY: \${{ github.repository }}"))
        assertTrue(workflow.contains("signingCertificateSha256"))
        assertTrue(workflow.contains("release.json"))
    }

    @Test
    fun aboutPageUsesEmbeddedBuildSourceAndAutomaticCheckOption() {
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        val preferences = java.io.File("src/main/java/app/xylune/chat/settings/AppPreferences.kt").readText()
        val viewModel = java.io.File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        val archive = java.io.File("src/main/java/app/xylune/chat/transfer/AppSettingsArchiveStore.kt").readText()
        assertTrue(settings.contains("BuildConfig.SOURCE_REPOSITORY"))
        assertTrue(settings.contains("Check for updates"))
        assertTrue(settings.contains("Check automatically"))
        assertTrue(settings.contains("onCheckedChange = viewModel::setAutomaticUpdateChecks"))
        assertTrue(preferences.contains("KEY_AUTOMATIC_UPDATE_CHECKS"))
        assertTrue(preferences.contains("preferences.getBoolean(KEY_AUTOMATIC_UPDATE_CHECKS, true)"))
        assertTrue(viewModel.contains("if (container.appPreferences.automaticUpdateChecks.value)"))
        assertTrue(archive.contains("automaticUpdateChecks: Boolean = true"))
    }
}
