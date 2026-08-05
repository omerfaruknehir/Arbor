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
    fun `website exposes app-only dynamic logo favicon and visible scheme controls`() {
        val boot = repositoryFile("docs/assets/js/theme-boot.js").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val css = repositoryFile("docs/assets/css/app-bar.css").readText()
        val home = repositoryFile("docs/index.html").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        assertTrue(boot.contains("dynamicLogo: params.get('dynamicLogo') === '1'"))
        assertTrue(site.contains("function syncBrandLogo(preference)"))
        assertTrue(site.contains("function dynamicLogoDataUrl(preference)"))
        assertTrue(site.contains("if (preference !== 'app' || !themeState.appTheme?.dynamicLogo) return null"))
        assertTrue(site.contains("root.dataset.brandLogo = dynamicSource ? 'app' : 'static'"))
        assertTrue(site.contains("document.querySelectorAll('link[data-xylune-favicon]')"))
        assertTrue(home.contains("rel=\"apple-touch-icon\""))
        assertTrue(layout.contains("data-xylune-logo"))
        assertTrue(layout.contains("class=\"scheme-selector\""))
        assertTrue(layout.contains("class=\"scheme-selector__label\">Dark"))
        assertTrue(layout.contains("class=\"scheme-selector__label\">Auto"))
        assertTrue(css.contains(".scheme-selector {\n  display: flex"))
        assertTrue(!css.contains("grid-template-columns: repeat(4"))
    }

    @Test
    fun `page title motion mirrors the Android bar and snapping is local`() {
        val css = repositoryFile("docs/assets/css/app-bar.css").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        assertTrue(css.contains("position: sticky"))
        assertTrue(css.contains("scroll-timeline-name: --xylune-page-scroll"))
        assertTrue(css.contains("animation-timeline: --xylune-page-scroll"))
        assertTrue(css.contains("transform: translate(-40px, 58px) scale(1.18)"))
        assertTrue(css.contains("transform: translateY(88px)"))
        assertTrue(css.contains(".page-with-app-bar,"))
        assertTrue(css.contains("scroll-snap-type: y proximity"))
        assertTrue(css.contains("scroll-snap-stop: always"))
        assertTrue(!Regex("html\\.collapsing-title-page\\s*\\{[^}]*scroll-snap-type", RegexOption.DOT_MATCHES_ALL).containsMatchIn(css))
        assertTrue(layout.contains("document-app-bar__row"))
        assertTrue(layout.indexOf("</header>") < layout.indexOf("document-title-collapse-snap"))
        assertTrue(!site.contains("addEventListener('scroll'"))
        assertTrue(!site.contains("onscroll"))
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
