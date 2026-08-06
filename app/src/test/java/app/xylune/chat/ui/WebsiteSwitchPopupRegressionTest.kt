package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebsiteSwitchPopupRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `switch state and hover use aria checked as the position source`() {
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        val css = repositoryFile("docs/assets/css/interaction-fix.css").readText()
        val motion = repositoryFile("docs/assets/js/motion.js").readText()

        assertTrue(layout.contains("appearance.css' | relative_url }}?v=79"))
        assertTrue(layout.contains("motion.css' | relative_url }}?v=79"))
        assertTrue(layout.contains("interaction-fix.css' | relative_url }}?v=79"))
        assertTrue(layout.indexOf("interaction-fix.css") > layout.indexOf("motion.css"))

        assertTrue(css.contains("width: 52px !important"))
        assertTrue(css.contains("height: 32px !important"))
        assertTrue(css.contains("overflow: hidden !important"))
        assertTrue(css.contains("left: 4px !important"))
        assertTrue(css.contains("inset-inline-start: 4px !important"))
        assertTrue(!css.contains("inset-inline-start: auto !important"))
        assertTrue(css.contains("width: 20px !important"))
        assertTrue(css.contains(".material-switch[aria-checked='true']:hover > .material-switch__handle"))
        assertTrue(css.contains("translate3d(20px, -50%, 0) !important"))
        assertTrue(css.contains(".material-switch[aria-checked='false']:hover > .material-switch__handle"))
        assertTrue(css.contains("translate3d(0, -50%, 0) !important"))
        assertTrue(!css.contains(".material-switch.is-checked:hover > .material-switch__handle"))
        assertTrue(!css.contains("left: 22px"))

        assertTrue(css.contains("--xylune-switch-progress: 0%"))
        assertTrue(css.contains("var(--xylune-switch-progress)"))
        assertTrue(css.contains("color-mix("))
        assertTrue(css.contains("transform: translate3d(var(--xylune-switch-drag-x), -50%, 0) !important"))
        assertTrue(motion.contains("const progress = dragX / travel"))
        assertTrue(motion.contains("--xylune-switch-progress"))
        assertTrue(motion.contains("control.getBoundingClientRect().width - 32"))
    }

    @Test
    fun `appearance popup animates open backdrop escape and close`() {
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        val css = repositoryFile("docs/assets/css/interaction-fix.css").readText()
        val js = repositoryFile("docs/assets/js/popup-motion.js").readText()

        assertTrue(layout.contains("popup-motion.js' | relative_url }}?v=79"))
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
