package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseWebsitePerformanceTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `release cards animate and cached data renders before refresh`() {
        val releases = repositoryFile("docs/assets/js/releases.js").readText()
        val page = repositoryFile("docs/releases/index.html").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()

        assertTrue(releases.contains("per_page=${'$'}{MAX_RELEASES}"))
        assertTrue(releases.contains("xylune-release-list-v2"))
        assertTrue(releases.contains("localStorage.getItem(cacheKey)"))
        assertTrue(releases.contains("localStorage.setItem(cacheKey"))
        assertTrue(releases.contains("requestIdleCallback"))
        assertTrue(releases.contains("function setReleaseOpen(card, shouldOpen)"))
        assertTrue(releases.contains("content.animate(["))
        assertTrue(releases.contains("prefers-reduced-motion: reduce"))
        assertTrue(releases.contains("release-card__content"))
        assertTrue(!releases.contains("X-GitHub-Api-Version"))
        assertTrue(page.contains("preconnect_releases: true"))
        assertTrue(page.contains("aria-busy=\"true\""))
        assertTrue(layout.contains("rel=\"preconnect\" href=\"https://api.github.com\""))
    }
}
