package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegalWebsiteIntegrationTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `about and privacy screens use themed legal website links`() {
        val settings = repositoryFile("app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertTrue(settings.contains("SettingsGroup(\"Legal\")"))
        assertTrue(settings.contains("title = \"Privacy policy\""))
        assertTrue(settings.contains("title = \"Terms & disclaimer\""))
        assertTrue(settings.contains("title = \"Data deletion\""))
        assertTrue(settings.contains("dynamicLogo = matchLauncherIconToPalette"))
        assertTrue(settings.contains("\"dynamicLogo\" to if (dynamicLogo) \"1\" else \"0\""))
    }

    @Test
    fun `website separates brightness theme from material color scheme`() {
        val boot = repositoryFile("docs/assets/js/theme-boot.js").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val appearance = repositoryFile("docs/assets/css/appearance.css").readText()

        assertTrue(boot.contains("supportedThemes = ['app', 'dark', 'light', 'system']"))
        assertTrue(boot.contains("supportedSchemes = ['app', 'xylune', 'graphite', 'ocean', 'violet', 'sunset']"))
        assertTrue(boot.contains("localStorage.getItem('xylune-scheme')"))
        assertTrue(boot.contains("queryKeys: ['theme', 'scheme'"))
        assertTrue(site.contains("id=\"theme-section-title\">Theme"))
        assertTrue(site.contains("id=\"scheme-section-title\">Color scheme"))
        assertTrue(site.contains("class=\"theme-selector dialog-theme-selector\""))
        assertTrue(site.contains("themeSegmentButton('system', 'brightness_auto', 'Auto')"))
        assertTrue(site.contains("themeSegmentButton('light', 'light_mode', 'Light')"))
        assertTrue(site.contains("themeSegmentButton('dark', 'dark_mode', 'Dark')"))
        assertTrue(site.contains("schemeButton('graphite', 'Graphite'"))
        assertTrue(site.contains("schemeButton('ocean', 'Ocean'"))
        assertTrue(site.contains("schemeButton('violet', 'Violet'"))
        assertTrue(site.contains("schemeButton('sunset', 'Sunset'"))
        assertTrue(appearance.contains(".dialog-theme-selector"))
        assertTrue(appearance.contains(".dialog-scheme-grid"))
    }

    @Test
    fun `appearance dialog has dynamic icon switch and real gradients`() {
        val boot = repositoryFile("docs/assets/js/theme-boot.js").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val appearance = repositoryFile("docs/assets/css/appearance.css").readText()
        val home = repositoryFile("docs/index.html").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()

        assertTrue(boot.contains("dynamicLogo: params.get('dynamicLogo') === '1'"))
        assertTrue(site.contains("localStorage.getItem('xylune-dynamic-icon')"))
        assertTrue(site.contains("function dynamicLogoDataUrl(colors)"))
        assertTrue(site.contains("if (!dynamicIconEnabled) return null"))
        assertTrue(site.contains("<linearGradient id=\"bg\""))
        assertTrue(site.contains("<stop offset=\"0.52\""))
        assertTrue(site.contains("<linearGradient id=\"mark\""))
        assertTrue(site.contains("<stop offset=\"0.55\""))
        assertTrue(site.contains("<linearGradient id=\"leaf\""))
        assertTrue(site.contains("data-dynamic-icon-toggle"))
        assertTrue(site.contains("role=\"switch\""))
        assertTrue(site.contains("url.searchParams.set('dynamicLogo', dynamicIconEnabled ? '1' : '0')"))
        assertTrue(site.contains("document.querySelectorAll('link[data-xylune-favicon]')"))
        assertTrue(appearance.contains(".material-switch"))
        assertTrue(appearance.contains(".material-switch.is-checked"))
        assertTrue(home.contains("rel=\"apple-touch-icon\""))
        assertTrue(layout.contains("data-xylune-logo"))
    }

    @Test
    fun `ordinary document scrolling never snaps and only partial title state settles`() {
        val css = repositoryFile("docs/assets/css/app-bar.css").readText()
        val appearance = repositoryFile("docs/assets/css/appearance.css").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()

        assertTrue(css.contains("position: sticky"))
        assertTrue(css.contains("scroll-timeline-name: --xylune-page-scroll"))
        assertTrue(css.contains("animation-timeline: --xylune-page-scroll"))
        assertTrue(css.contains("transform: translate(-40px, 58px) scale(1.18)"))
        assertTrue(css.contains("transform: translateY(88px)"))
        assertTrue(appearance.contains("scroll-snap-type: none !important"))
        assertTrue(appearance.contains("scroll-behavior: auto !important"))
        assertTrue(appearance.contains("scroll-snap-align: none !important"))
        assertTrue(appearance.contains("display: none !important"))
        assertTrue(site.contains("function setupTitleSettle()"))
        assertTrue(site.contains("position <= 1 || position >= collapseDistance - 1"))
        assertTrue(site.contains("position < collapseDistance / 2 ? 0 : collapseDistance"))
        assertTrue(site.contains("behavior: reducedMotion.matches ? 'auto' : 'smooth'"))
    }

    @Test
    fun `sidebar has one palette launcher and dialog uses material accent circles`() {
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val appearance = repositoryFile("docs/assets/css/appearance.css").readText()

        assertTrue(site.contains("class=\"appearance-launcher\""))
        assertTrue(site.contains("class=\"appearance-launcher__label\">Appearance"))
        assertTrue(site.contains(">palette</span>"))
        assertTrue(!site.contains(">tune</span>"))
        assertTrue(appearance.contains(".appearance-launcher"))
        assertTrue(appearance.contains("conic-gradient("))
        assertTrue(appearance.contains("from 270deg"))
        assertTrue(appearance.contains("var(--preview-primary) 0deg 180deg"))
        assertTrue(appearance.contains("var(--preview-secondary) 180deg 270deg"))
        assertTrue(appearance.contains("var(--preview-tertiary) 270deg 360deg"))
    }

    @Test
    fun `release page orders versions numerically`() {
        val releases = repositoryFile("docs/assets/js/releases.js").readText()
        val page = repositoryFile("docs/releases/index.html").readText()
        val home = repositoryFile("docs/index.html").readText()
        assertTrue(releases.contains("function parseSemanticVersion(value)"))
        assertTrue(releases.contains("right.numbers[index] - left.numbers[index]"))
        assertTrue(releases.contains(".sort(compareSemanticVersionsDescending)"))
        assertTrue(page.contains("data-release-list"))
        assertTrue(home.contains("href=\"releases/\""))
    }
}
