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

        assertTrue(motionJs.contains("function setupNavigationTabs()"))
        assertTrue(motionJs.contains("rail-nav__indicator"))
        assertTrue(motionJs.contains("void nav.offsetWidth"))
        assertTrue(motionJs.contains("event.preventDefault()"))
        assertTrue(motionJs.contains("location.assign(tab.href)"))
        assertTrue(motionJs.contains("theme-selector__indicator"))
        assertTrue(motionJs.contains("MutationObserver"))
        assertTrue(motionJs.contains("aria-current"))

        assertTrue(motionJs.contains("function setupDraggableSwitch(control)"))
        assertTrue(motionJs.contains("pointerdown"))
        assertTrue(motionJs.contains("pointermove"))
        assertTrue(motionJs.contains("pointerup"))
        assertTrue(motionJs.contains("setPointerCapture"))
        assertTrue(motionJs.contains("suppressNativeClick"))
        assertTrue(motionJs.contains("control.click()"))

        assertTrue(motionCss.contains(".rail-nav__indicator"))
        assertTrue(motionCss.contains("transform 240ms"))
        assertTrue(motionCss.contains(".theme-selector__indicator"))
        assertTrue(motionCss.contains(".button:active"))
        assertTrue(motionCss.contains(".icon-button:active"))
        assertTrue(motionCss.contains("html.xylune-motion-ready body"))

        assertTrue(motionCss.contains("--xylune-switch-travel: 18px"))
        assertTrue(motionCss.contains("--xylune-switch-drag-x"))
        assertTrue(motionCss.contains("translate3d(var(--xylune-switch-travel), -50%, 0)"))
        assertTrue(motionCss.contains(".material-switch.is-dragging"))
        assertTrue(motionCss.contains("touch-action: pan-y"))
        assertTrue(motionCss.contains("prefers-reduced-motion: reduce"))
    }
}
