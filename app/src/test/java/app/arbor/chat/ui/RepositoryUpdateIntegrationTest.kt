package app.arbor.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryUpdateIntegrationTest {
    @Test
    fun releaseWorkflowEmbedsRepositoryAndSignedManifest() {
        val workflow = java.io.File("../.github/workflows/release.yml").readText()
        assertTrue(workflow.contains("ARBOR_SOURCE_REPOSITORY: \${{ github.repository }}"))
        assertTrue(workflow.contains("signingCertificateSha256"))
        assertTrue(workflow.contains("release.json"))
    }

    @Test
    fun aboutPageUsesEmbeddedBuildSource() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertTrue(settings.contains("BuildConfig.SOURCE_REPOSITORY"))
        assertTrue(settings.contains("Check for updates"))
    }
}
