package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebsiteMotionIntegrationTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `navigation themes buttons and switches use the motion layer`() {
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        val motionCss = repositoryFile("docs/assets/css/motion.css").readText()
        val motionJs = repositoryFile("docs/assets/js/motion.js").readText()

        assertTrue(layout.contains("assets/css/motion.css"))
        assertTrue(layout.contains("assets/js/motion.js"))

        assertTrue(motionJs.contains("xylune-navigation-tab-from-v1"))
        assertTrue(motionJs.contains("function setupNavigationTabs()"))
        assertTrue(motionJs.contains("rail-nav__indicator"))
        assertTrue(motionJs.contains("theme-selector__indicator"))
        assertTrue(motionJs.contains("sessionStorage.setItem"))
        assertTrue(motionJs.contains("requestAnimationFrame"))
        assertTrue(motionJs.contains("MutationObserver"))
        assertTrue(motionJs.contains("aria-current"))

        assertTrue(motionCss.contains(".rail-nav__indicator"))
        assertTrue(motionCss.contains(".theme-selector__indicator"))
        assertTrue(motionCss.contains("transform 300ms"))
        assertTrue(motionCss.contains("transform 280ms"))
        assertTrue(motionCss.contains(".button:active"))
        assertTrue(motionCss.contains(".icon-button:active"))
        assertTrue(motionCss.contains("html.xylune-motion-ready body"))

        assertTrue(motionCss.contains("padding: 0 !important"))
        assertTrue(motionCss.contains("inset-inline-start: 24px"))
        assertTrue(motionCss.contains("inset-inline-start: 26px"))
        assertTrue(motionCss.contains("prefers-reduced-motion: reduce"))
    }
}
