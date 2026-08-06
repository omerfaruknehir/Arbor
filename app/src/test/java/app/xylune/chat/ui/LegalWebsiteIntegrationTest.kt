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
        assertTrue(boot.contains("const queryKeys = ['theme', 'scheme'"))
        assertTrue(boot.contains("const paletteSurfaces ="))
        assertTrue(boot.contains("'--background': '#0e1416'"))
        assertTrue(boot.contains("'--on-surface': '#e7e0e8'"))
        assertTrue(boot.contains("'--on-surface-variant': '#51443f'"))
        assertTrue(boot.contains("...(paletteSurfaces[scheme]?.[mode]"))
        assertTrue(site.contains("id=\"theme-section-title\">Theme"))
        assertTrue(site.contains("id=\"scheme-section-title\">Color scheme"))
        assertTrue(site.contains("class=\"theme-selector rail-theme-selector\""))
        assertTrue(site.contains("class=\"theme-selector dialog-theme-selector\""))
        assertTrue(site.contains("themeSegmentButton('system', 'brightness_auto', 'Auto')"))
        assertTrue(site.contains("themeSegmentButton('light', 'light_mode', 'Light')"))
        assertTrue(site.contains("themeSegmentButton('dark', 'dark_mode', 'Dark')"))
        assertTrue(site.contains("schemeButton('graphite', 'Graphite'"))
        assertTrue(site.contains("schemeButton('ocean', 'Ocean'"))
        assertTrue(site.contains("schemeButton('violet', 'Violet'"))
        assertTrue(site.contains("schemeButton('sunset', 'Sunset'"))
        assertTrue(appearance.contains(".rail-theme-selector"))
        assertTrue(appearance.contains(".dialog-theme-selector"))
        assertTrue(appearance.contains(".dialog-scheme-grid"))
    }

    @Test
    fun `website icon uses the exact Android launcher variants`() {
        val boot = repositoryFile("docs/assets/js/theme-boot.js").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val appearance = repositoryFile("docs/assets/css/appearance.css").readText()
        val home = repositoryFile("docs/index.html").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()

        assertTrue(boot.contains("dynamicLogo: params.get('dynamicLogo') === '1'"))
        assertTrue(boot.contains("const APP_THEME_STORAGE = 'xylune-app-theme-v1'"))
        assertTrue(boot.contains("localStorage.setItem(APP_THEME_STORAGE, JSON.stringify(urlAppTheme))"))
        assertTrue(boot.contains("cleanUrl.searchParams.delete(key)"))
        assertTrue(boot.contains("history.replaceState(null, '', cleanUrl)"))
        assertTrue(site.contains("localStorage.getItem('xylune-dynamic-icon')"))
        assertTrue(site.contains("const appIconPalettes ="))
        assertTrue(site.contains("function dynamicLogoDataUrl(schemePreference)"))
        assertTrue(site.contains("function iconPaletteFor(schemePreference)"))
        assertTrue(site.contains("return appPrimaryToIconPalette.get(appPrimary) || 'system'"))

        // Xylune launcher vector.
        assertTrue(site.contains("backgroundStart: '#083a2c'"))
        assertTrue(site.contains("backgroundEnd: '#0c684f'"))
        assertTrue(site.contains("markStart: '#86dfb8'"))
        assertTrue(site.contains("markEnd: '#ddfbea'"))
        assertTrue(site.contains("leaf: '#f4c761'"))
        assertTrue(site.contains("secondStroke: '#f1fff7'"))

        // Android Dynamic/System launcher vector.
        assertTrue(site.contains("backgroundStart: '#293b52'"))
        assertTrue(site.contains("backgroundEnd: '#67507e'"))
        assertTrue(site.contains("markStart: '#a9d4ff'"))
        assertTrue(site.contains("markEnd: '#e8ddff'"))
        assertTrue(site.contains("leaf: '#ffb4a9'"))

        // Built-in palette launcher vectors.
        assertTrue(site.contains("backgroundStart: '#162234'"))
        assertTrue(site.contains("backgroundStart: '#00363f'"))
        assertTrue(site.contains("backgroundStart: '#2e1d4f'"))
        assertTrue(site.contains("backgroundStart: '#5c1a07'"))
        assertTrue(site.contains("<stop offset=\"1\" stop-color=\"${'$'}{palette.backgroundEnd}\"/>"))
        assertTrue(site.contains("fill=\"${'$'}{palette.leaf}\""))
        assertTrue(!site.contains("<linearGradient id=\"leaf\""))
        assertTrue(!site.contains("function mixHex("))

        assertTrue(site.contains("data-dynamic-icon-toggle"))
        assertTrue(site.contains("role=\"switch\""))
        assertTrue(site.contains("localStorage.setItem('xylune-dynamic-icon', dynamicIconEnabled ? '1' : '0')"))
        assertTrue(site.contains("themeState.queryKeys.forEach((key) => target.searchParams.delete(key))"))
        assertTrue(!site.contains("url.searchParams.set('dynamicLogo'"))
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
        assertTrue(css.contains("grid-template-columns: 80px minmax(0, 1fr) 80px"))
        assertTrue(css.contains("text-align: center"))
        assertTrue(css.contains("translateY(var(--xylune-title-shift)) scale(var(--xylune-title-scale))"))
        assertTrue(!css.contains("scroll-snap-type:"))
        assertTrue(!css.contains("scroll-timeline-name:"))
        assertTrue(!css.contains("animation-timeline:"))
        assertTrue(appearance.contains("scroll-snap-type: none !important"))
        assertTrue(appearance.contains("scroll-behavior: auto !important"))
        assertTrue(site.contains("function setupTitleCollapse()"))
        assertTrue(site.contains("requestAnimationFrame(applyProgress)"))
        assertTrue(site.contains("--xylune-title-shift"))
        assertTrue(site.contains("--xylune-title-scale"))
        assertTrue(site.contains("getPropertyValue('--xylune-title-expanded-scale')"))
        assertTrue(css.contains(".home-shell.page-with-app-bar"))
        assertTrue(css.contains("--xylune-title-expanded-scale: 1.82"))
        assertTrue(site.contains("position <= 1 || position >= collapseDistance - 1"))
        assertTrue(site.contains("position < collapseDistance / 2 ? 0 : collapseDistance"))
        assertTrue(site.contains("behavior: reducedMotion.matches ? 'auto' : 'smooth'"))
    }

    @Test
    fun `menu shows theme switch palette launcher and external link indicator`() {
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val appearance = repositoryFile("docs/assets/css/appearance.css").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()

        assertTrue(site.contains("class=\"appearance-launcher__label\">Theme"))
        assertTrue(site.contains("class=\"theme-selector rail-theme-selector\""))
        assertTrue(site.contains(">palette</span>"))
        assertTrue(!site.contains(">tune</span>"))
        assertTrue(appearance.contains(".appearance-launcher"))
        assertTrue(appearance.contains("conic-gradient("))
        assertTrue(appearance.contains("from 270deg"))
        assertTrue(appearance.contains("var(--preview-primary) 0deg 180deg"))
        assertTrue(appearance.contains("var(--preview-secondary) 180deg 270deg"))
        assertTrue(appearance.contains("var(--preview-tertiary) 270deg 360deg"))
        assertTrue(appearance.contains(".palette-choice__swatches {"))
        assertTrue(appearance.contains("border: 0"))
        assertTrue(!appearance.contains(".palette-choice.is-selected .palette-choice__swatches"))
        assertTrue(layout.contains("nav-item__external"))
        assertTrue(layout.contains(">open_in_new</span>"))
    }

    @Test
    fun `home uses banner and release notes expand in page`() {
        val releases = repositoryFile("docs/assets/js/releases.js").readText()
        val page = repositoryFile("docs/releases/index.html").readText()
        val home = repositoryFile("docs/index.html").readText()
        val css = repositoryFile("docs/assets/css/app-bar.css").readText()
        assertTrue(releases.contains("function parseSemanticVersion(value)"))
        assertTrue(releases.contains("right.numbers[index] - left.numbers[index]"))
        assertTrue(releases.contains("const MAX_RELEASES = 10"))
        assertTrue(releases.contains(".slice(0, MAX_RELEASES)"))
        assertTrue(releases.contains("card.open = index === 0"))
        assertTrue(releases.contains("renderReleaseNotes(release.body)"))
        assertTrue(releases.contains("'Show all releases'"))
        assertTrue(releases.contains("'open_in_new'"))
        assertTrue(!releases.contains("actionLink('Release notes'"))
        assertTrue(page.contains("data-release-list"))
        assertTrue(!page.contains("sorted numerically"))
        assertTrue(!page.contains("regardless of GitHub publication timestamps"))
        assertTrue(home.contains("class=\"home-banner\""))
        assertTrue(home.contains("branding/xylune-banner.png"))
        assertTrue(home.contains("href=\"releases/\""))
        assertTrue(css.contains(".release-card__toggle"))
        assertTrue(css.contains(".release-list__footer"))
    }
}
