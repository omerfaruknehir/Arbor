package app.xylune.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageWorkspaceRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `image models use a dedicated workspace with shared chat chrome`() {
        val app = repositoryFile("app/src/main/java/app/xylune/chat/ui/XyluneApp.kt").readText()
        val screen = repositoryFile("app/src/main/java/app/xylune/chat/ui/ImageGenerationScreen.kt").readText()

        assertTrue(app.contains("latestImageWorkspaceActive.value"))
        assertTrue(app.contains("ImageGenerationScreen(viewModel, compactOpenDrawer)"))
        assertTrue(screen.contains("ChatCollapsingTranslucentTopBar("))
        assertTrue(screen.contains("xyluneBackdropBlur("))
        assertTrue(screen.contains("panelHeight = 88.dp"))
        assertTrue(screen.contains("expandToMeasuredHeight = true"))
        assertFalse(screen.contains("panelHeight = 240.dp"))
        assertFalse(screen.contains("panelHeight = CHAT_COMPOSER_MIN_PANEL_HEIGHT_DP.dp"))
        assertTrue(screen.contains("ImageGenerationProgressCard("))
        assertTrue(screen.contains("SendMode.QUEUE"))
        assertTrue(screen.contains("PickMultipleVisualMedia(16)"))
        assertTrue(screen.contains("TakePicture()"))
        assertFalse(screen.contains("ImageRequestModeCard("))
        assertFalse(screen.contains("SearchComposerChip"))
        assertFalse(screen.contains("ThinkingComposerChip"))
    }

    @Test
    fun `release notes can never fall back to the entire changelog`() {
        val resolver = repositoryFile("ci/resolve-release-notes.sh").readText()
        val workflow = repositoryFile(".github/workflows/android.yml").readText()

        assertTrue(resolver.contains("RELEASE_NOTES_${'$'}{version}.md"))
        assertTrue(resolver.contains("Refusing to publish the entire changelog as one release"))
        assertFalse(workflow.contains("|| notes=\"CHANGELOG.md\""))
        assertTrue(workflow.contains("bash ci/resolve-release-notes.sh"))
        assertTrue(workflow.contains("gh release edit \"${'$'}tag\""))
    }
}
