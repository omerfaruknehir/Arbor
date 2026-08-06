package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebsiteSwitchPopupRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `switch geometry has one final cache busted override and stays inside its track`() {
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        val css = repositoryFile("docs/assets/css/interaction-fix.css").readText()

        assertTrue(layout.contains("appearance.css' | relative_url }}?v=77"))
        assertTrue(layout.contains("motion.css' | relative_url }}?v=77"))
        assertTrue(layout.contains("interaction-fix.css' | relative_url }}?v=77"))
        assertTrue(layout.indexOf("interaction-fix.css") > layout.indexOf("motion.css"))
        assertTrue(layout.contains("data-xylune-appearance"))

        assertTrue(css.contains("width: 52px !important"))
        assertTrue(css.contains("height: 32px !important"))
        assertTrue(css.contains("overflow: hidden !important"))
        assertTrue(css.contains("left: 6px !important"))
        assertTrue(css.contains("left: 22px !important"))
        assertTrue(css.contains("width: 24px !important"))
        assertTrue(css.contains("left: calc(6px + var(--xylune-switch-drag-x, 0px)) !important"))
        assertTrue(!css.contains("translate3d(var(--xylune-switch-travel)"))
    }

    @Test
    fun `appearance popup animates open backdrop escape and close`() {
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        val css = repositoryFile("docs/assets/css/interaction-fix.css").readText()
        val js = repositoryFile("docs/assets/js/popup-motion.js").readText()

        assertTrue(layout.contains("popup-motion.js' | relative_url }}?v=77"))
        assertTrue(css.contains(".appearance-dialog.is-visible"))
        assertTrue(css.contains(".appearance-dialog.is-closing"))
        assertTrue(css.contains(".appearance-dialog.is-visible::backdrop"))
        assertTrue(css.contains("translateY(18px) scale(0.96)"))
        assertTrue(css.contains("prefers-reduced-motion: reduce"))

        assertTrue(js.contains("const closeAnimated = () =>"))
        assertTrue(js.contains("data-theme-close"))
        assertTrue(js.contains("event.target === dialog"))
        assertTrue(js.contains("dialog.addEventListener('cancel'"))
        assertTrue(js.contains("MutationObserver"))
        assertTrue(js.contains("dialog.classList.add('is-visible')"))
    }
}
