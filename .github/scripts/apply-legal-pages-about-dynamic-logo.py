from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(relative_path: str, old: str, new: str, expected: int = 1) -> None:
    path = ROOT / relative_path
    text = path.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != expected:
        raise RuntimeError(
            f"{relative_path}: expected {expected} occurrence(s), found {actual}: {old[:120]!r}"
        )
    path.write_text(text.replace(old, new), encoding="utf-8")


def write_new(relative_path: str, content: str) -> None:
    path = ROOT / relative_path
    if path.exists():
        raise RuntimeError(f"Refusing to overwrite existing file: {relative_path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


# Android: carry the launcher-logo preference into every legal-page URL.
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "SettingsRoute.PRIVACY -> PrivacySettingsPage(renderSafeMode, generatedRepairMaxAttempts, viewModel)",
    "SettingsRoute.PRIVACY -> PrivacySettingsPage(\n"
    "                            renderSafeMode = renderSafeMode,\n"
    "                            generatedRepairMaxAttempts = generatedRepairMaxAttempts,\n"
    "                            matchLauncherIconToPalette = matchLauncherIconToPalette,\n"
    "                            viewModel = viewModel,\n"
    "                        )",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "                        SettingsRoute.ABOUT -> AboutSettingsPage(\n"
    "                            viewModel = viewModel,\n"
    "                            developerEnabled = developerSettings.enabled,",
    "                        SettingsRoute.ABOUT -> AboutSettingsPage(\n"
    "                            viewModel = viewModel,\n"
    "                            developerEnabled = developerSettings.enabled,\n"
    "                            matchLauncherIconToPalette = matchLauncherIconToPalette,",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "private fun PrivacySettingsPage(\n"
    "    renderSafeMode: Boolean,\n"
    "    generatedRepairMaxAttempts: Int,\n"
    "    viewModel: ChatViewModel,\n"
    ") = SettingsPage {\n"
    "    val uriHandler = LocalUriHandler.current\n"
    "    val siteColors = MaterialTheme.colorScheme\n"
    "    val privacyUrl = remember(siteColors) { xyluneWebsiteUrl(\"privacy/\", siteColors) }\n"
    "    val termsUrl = remember(siteColors) { xyluneWebsiteUrl(\"terms/\", siteColors) }\n"
    "    val deletionUrl = remember(siteColors) { xyluneWebsiteUrl(\"data-deletion/\", siteColors) }",
    "private fun PrivacySettingsPage(\n"
    "    renderSafeMode: Boolean,\n"
    "    generatedRepairMaxAttempts: Int,\n"
    "    matchLauncherIconToPalette: Boolean,\n"
    "    viewModel: ChatViewModel,\n"
    ") = SettingsPage {\n"
    "    val uriHandler = LocalUriHandler.current\n"
    "    val siteColors = MaterialTheme.colorScheme\n"
    "    val privacyUrl = remember(siteColors, matchLauncherIconToPalette) {\n"
    "        xyluneWebsiteUrl(\"privacy/\", siteColors, dynamicLogo = matchLauncherIconToPalette)\n"
    "    }\n"
    "    val termsUrl = remember(siteColors, matchLauncherIconToPalette) {\n"
    "        xyluneWebsiteUrl(\"terms/\", siteColors, dynamicLogo = matchLauncherIconToPalette)\n"
    "    }\n"
    "    val deletionUrl = remember(siteColors, matchLauncherIconToPalette) {\n"
    "        xyluneWebsiteUrl(\"data-deletion/\", siteColors, dynamicLogo = matchLauncherIconToPalette)\n"
    "    }",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "private fun AboutSettingsPage(\n"
    "    viewModel: ChatViewModel,\n"
    "    developerEnabled: Boolean,\n"
    "    onOpenDeveloper: () -> Unit,",
    "private fun AboutSettingsPage(\n"
    "    viewModel: ChatViewModel,\n"
    "    developerEnabled: Boolean,\n"
    "    matchLauncherIconToPalette: Boolean,\n"
    "    onOpenDeveloper: () -> Unit,",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "    val sourceRepository = BuildConfig.SOURCE_REPOSITORY.takeIf(String::isNotBlank)\n"
    "    val sourceUrl = sourceRepository?.let { \"https://github.com/$it\" }\n"
    "    SectionTitle(\"$appName ${BuildConfig.VERSION_NAME}\", \"Native Android BYOK model workspace.\")",
    "    val sourceRepository = BuildConfig.SOURCE_REPOSITORY.takeIf(String::isNotBlank)\n"
    "    val sourceUrl = sourceRepository?.let { \"https://github.com/$it\" }\n"
    "    val siteColors = MaterialTheme.colorScheme\n"
    "    val privacyUrl = remember(siteColors, matchLauncherIconToPalette) {\n"
    "        xyluneWebsiteUrl(\"privacy/\", siteColors, dynamicLogo = matchLauncherIconToPalette)\n"
    "    }\n"
    "    val termsUrl = remember(siteColors, matchLauncherIconToPalette) {\n"
    "        xyluneWebsiteUrl(\"terms/\", siteColors, dynamicLogo = matchLauncherIconToPalette)\n"
    "    }\n"
    "    val deletionUrl = remember(siteColors, matchLauncherIconToPalette) {\n"
    "        xyluneWebsiteUrl(\"data-deletion/\", siteColors, dynamicLogo = matchLauncherIconToPalette)\n"
    "    }\n"
    "    SectionTitle(\"$appName ${BuildConfig.VERSION_NAME}\", \"Native Android BYOK model workspace.\")",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "    SettingsGroup(\"Updates\") {",
    "    SettingsGroup(\"Legal\") {\n"
    "        SettingsDestination(\n"
    "            icon = Icons.Outlined.PrivacyTip,\n"
    "            title = \"Privacy policy\",\n"
    "            subtitle = \"Privacy, local data, providers, and KVKK/GDPR boundaries\",\n"
    "            onClick = { uriHandler.openUri(privacyUrl) },\n"
    "        )\n"
    "        HorizontalDivider()\n"
    "        SettingsDestination(\n"
    "            icon = Icons.Outlined.Security,\n"
    "            title = \"Terms & disclaimer\",\n"
    "            subtitle = \"Use terms, third-party AI limits, warranty, and liability\",\n"
    "            onClick = { uriHandler.openUri(termsUrl) },\n"
    "        )\n"
    "        HorizontalDivider()\n"
    "        SettingsDestination(\n"
    "            icon = Icons.Outlined.DeleteOutline,\n"
    "            title = \"Data deletion\",\n"
    "            subtitle = \"Delete local data and provider-held copies\",\n"
    "            onClick = { uriHandler.openUri(deletionUrl) },\n"
    "        )\n"
    "    }\n\n"
    "    SettingsGroup(\"Updates\") {",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "internal fun xyluneWebsiteUrl(path: String, colors: ColorScheme): String {",
    "internal fun xyluneWebsiteUrl(\n"
    "    path: String,\n"
    "    colors: ColorScheme,\n"
    "    dynamicLogo: Boolean = false,\n"
    "): String {",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "        \"primaryContainer\" to colors.primaryContainer.webHex(),\n"
    "        \"onPrimaryContainer\" to colors.onPrimaryContainer.webHex(),\n"
    "        \"background\" to colors.background.webHex(),",
    "        \"primaryContainer\" to colors.primaryContainer.webHex(),\n"
    "        \"onPrimaryContainer\" to colors.onPrimaryContainer.webHex(),\n"
    "        \"secondary\" to colors.secondary.webHex(),\n"
    "        \"onSecondary\" to colors.onSecondary.webHex(),\n"
    "        \"secondaryContainer\" to colors.secondaryContainer.webHex(),\n"
    "        \"onSecondaryContainer\" to colors.onSecondaryContainer.webHex(),\n"
    "        \"tertiary\" to colors.tertiary.webHex(),\n"
    "        \"onTertiary\" to colors.onTertiary.webHex(),\n"
    "        \"tertiaryContainer\" to colors.tertiaryContainer.webHex(),\n"
    "        \"onTertiaryContainer\" to colors.onTertiaryContainer.webHex(),\n"
    "        \"background\" to colors.background.webHex(),",
)
replace_exact(
    "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt",
    "        \"rail\" to colors.surfaceContainerLowest.webHex(),\n"
    "    )",
    "        \"rail\" to colors.surfaceContainerLowest.webHex(),\n"
    "        \"dynamicLogo\" to if (dynamicLogo) \"1\" else \"0\",\n"
    "    )",
)

# Website: accept the extra Material roles and expose whether logo recoloring is enabled.
replace_exact(
    "docs/assets/js/theme-boot.js",
    "    onPrimaryContainer: '--on-primary-container',\n"
    "    background: '--background',",
    "    onPrimaryContainer: '--on-primary-container',\n"
    "    secondary: '--secondary',\n"
    "    onSecondary: '--on-secondary',\n"
    "    secondaryContainer: '--secondary-container',\n"
    "    onSecondaryContainer: '--on-secondary-container',\n"
    "    tertiary: '--tertiary',\n"
    "    onTertiary: '--on-tertiary',\n"
    "    tertiaryContainer: '--tertiary-container',\n"
    "    onTertiaryContainer: '--on-tertiary-container',\n"
    "    background: '--background',",
)
replace_exact(
    "docs/assets/js/theme-boot.js",
    "  const appTheme = required.every((name) => colors[name]) ? {\n"
    "    colors,\n"
    "    dark: params.get('dark') === '1',\n"
    "  } : null;",
    "  const appTheme = required.every((name) => colors[name]) ? {\n"
    "    colors,\n"
    "    dark: params.get('dark') === '1',\n"
    "    dynamicLogo: params.get('dynamicLogo') === '1',\n"
    "  } : null;",
)
replace_exact(
    "docs/assets/js/theme-boot.js",
    "    queryKeys: ['theme', 'dark', ...Object.keys(names)],",
    "    queryKeys: ['theme', 'dark', 'dynamicLogo', ...Object.keys(names)],",
)

site_js = ROOT / "docs/assets/js/site.js"
site_text = site_js.read_text(encoding="utf-8")
anchor = "  function syncThemeLinks() {\n"
if site_text.count(anchor) != 1:
    raise RuntimeError("docs/assets/js/site.js: syncThemeLinks anchor changed")
logo_functions = r'''  function dynamicLogoDataUrl() {
    const appTheme = themeState.appTheme;
    if (!appTheme?.dynamicLogo) return null;
    const colors = appTheme.colors;
    const backgroundStart = colors['--primary-container'] || colors['--surface-container'];
    const backgroundEnd = colors['--primary'];
    const firstStroke = colors['--on-primary-container'] || colors['--on-surface'];
    const secondStroke = colors['--on-primary'] || colors['--background'];
    const leaf = colors['--tertiary'] || colors['--secondary'] || colors['--primary'];
    if (![backgroundStart, backgroundEnd, firstStroke, secondStroke, leaf].every(Boolean)) return null;
    const svg = `<svg width="512" height="512" viewBox="0 0 108 108" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="bg" x1="15" y1="8" x2="96" y2="101" gradientUnits="userSpaceOnUse"><stop stop-color="${backgroundStart}"/><stop offset="1" stop-color="${backgroundEnd}"/></linearGradient>
    <linearGradient id="mark" x1="27" y1="84" x2="55" y2="25" gradientUnits="userSpaceOnUse" gradientTransform="matrix(1.014377,0.27180148,-0.27180148,1.014377,27.43436,-9.9261354)"><stop stop-color="${firstStroke}"/><stop offset="1" stop-color="${secondStroke}"/></linearGradient>
  </defs>
  <rect width="108" height="108" rx="24" fill="url(#bg)"/>
  <path d="M 33.549193,80.863216 C 45.542258,64.507039 58.821502,47.408289 73.585895,32.881898" fill="none" stroke="url(#mark)" stroke-width="11.5517" stroke-linecap="round"/>
  <path d="M 39.107895,30.166046 C 43.79571,20.768808 52.715523,17.003434 60.890902,20.847009 59.491039,30.710867 51.981892,36.353531 40.896179,34.109428 Z" fill="${leaf}"/>
  <path d="M 33.99223,32.881898 C 48.756623,47.408289 62.035867,64.507039 74.028932,80.863216" fill="none" stroke="${secondStroke}" stroke-width="11.5517" stroke-linecap="round"/>
</svg>`;
    return `data:image/svg+xml,${encodeURIComponent(svg)}`;
  }

  function syncBrandLogo(preference) {
    const dynamicSource = preference === 'app' ? dynamicLogoDataUrl() : null;
    document.querySelectorAll('[data-xylune-logo]').forEach((image) => {
      image.dataset.staticSrc ||= image.getAttribute('src') || '';
      image.setAttribute('src', dynamicSource || image.dataset.staticSrc);
    });
    const favicon = document.querySelector('link[data-xylune-favicon]');
    if (favicon) {
      favicon.dataset.staticHref ||= favicon.getAttribute('href') || '';
      favicon.setAttribute('href', dynamicSource || favicon.dataset.staticHref);
    }
  }

'''
site_text = site_text.replace(anchor, logo_functions + anchor)
old_apply = "    document.documentElement.style.colorScheme = resolved;\n    document.querySelector('meta[name=\"theme-color\"]')?.setAttribute("
new_apply = "    document.documentElement.style.colorScheme = resolved;\n    syncBrandLogo(preference);\n    document.querySelector('meta[name=\"theme-color\"]')?.setAttribute("
if site_text.count(old_apply) != 1:
    raise RuntimeError("docs/assets/js/site.js: applyTheme anchor changed")
site_js.write_text(site_text.replace(old_apply, new_apply), encoding="utf-8")

# Mark every visible logo and favicon as dynamically replaceable.
replace_exact(
    "docs/index.html",
    "    <link rel=\"icon\" href=\"assets/images/xylune-logo.svg\" type=\"image/svg+xml\">",
    "    <link rel=\"icon\" href=\"assets/images/xylune-logo.svg\" type=\"image/svg+xml\" data-xylune-favicon>",
)
replace_exact(
    "docs/index.html",
    "<img src=\"assets/images/xylune-logo.svg\" alt=\"\"",
    "<img src=\"assets/images/xylune-logo.svg\" alt=\"\" data-xylune-logo",
    expected=2,
)
replace_exact(
    "docs/_layouts/default.html",
    "    <link rel=\"icon\" href=\"{{ '/assets/images/xylune-logo.svg' | relative_url }}\" type=\"image/svg+xml\">",
    "    <link rel=\"icon\" href=\"{{ '/assets/images/xylune-logo.svg' | relative_url }}\" type=\"image/svg+xml\" data-xylune-favicon>",
)
replace_exact(
    "docs/_layouts/default.html",
    "<img src=\"{{ '/assets/images/xylune-logo.svg' | relative_url }}\" alt=\"\"",
    "<img src=\"{{ '/assets/images/xylune-logo.svg' | relative_url }}\" alt=\"\" data-xylune-logo",
    expected=2,
)

# Repository-facing links should point to the rendered legal website, not raw Markdown.
replace_exact(
    "README.md",
    "  <a href=\"PRIVACY.md\">Privacy</a>\n  ·\n  <a href=\"TERMS.md\">Terms</a>",
    "  <a href=\"https://omerfaruknehir.github.io/Xylune/privacy/\">Privacy</a>\n"
    "  ·\n"
    "  <a href=\"https://omerfaruknehir.github.io/Xylune/terms/\">Terms</a>\n"
    "  ·\n"
    "  <a href=\"https://omerfaruknehir.github.io/Xylune/data-deletion/\">Data deletion</a>",
)
replace_exact(
    "README.md",
    "[Terms and Disclaimer](TERMS.md)",
    "[Terms and Disclaimer](https://omerfaruknehir.github.io/Xylune/terms/)",
)
replace_exact(
    "README.md",
    "[Privacy Policy](PRIVACY.md)",
    "[Privacy Policy](https://omerfaruknehir.github.io/Xylune/privacy/)",
)

write_new(
    "app/src/test/java/app/xylune/chat/ui/LegalWebsiteIntegrationTest.kt",
    '''package app.xylune.chat.ui

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
        assertTrue(settings.contains("SettingsGroup(\\\"Legal\\\")"))
        assertTrue(settings.contains("title = \\\"Privacy policy\\\""))
        assertTrue(settings.contains("title = \\\"Terms & disclaimer\\\""))
        assertTrue(settings.contains("title = \\\"Data deletion\\\""))
        assertTrue(settings.contains("dynamicLogo = matchLauncherIconToPalette"))
        assertTrue(settings.contains("\\\"dynamicLogo\\\" to if (dynamicLogo) \\\"1\\\" else \\\"0\\\""))
    }

    @Test
    fun `website exposes dynamic logo hooks`() {
        val boot = repositoryFile("docs/assets/js/theme-boot.js").readText()
        val site = repositoryFile("docs/assets/js/site.js").readText()
        val home = repositoryFile("docs/index.html").readText()
        val layout = repositoryFile("docs/_layouts/default.html").readText()
        assertTrue(boot.contains("dynamicLogo: params.get('dynamicLogo') === '1'"))
        assertTrue(site.contains("function syncBrandLogo(preference)"))
        assertTrue(site.contains("[data-xylune-logo]"))
        assertTrue(home.contains("data-xylune-favicon"))
        assertTrue(layout.contains("data-xylune-logo"))
    }
}
''',
)

# Final sanity checks before the workflow commits anything.
settings = (ROOT / "app/src/main/java/app/xylune/chat/ui/SettingsScreen.kt").read_text(encoding="utf-8")
assert settings.count("SettingsGroup(\"Legal\")") == 1
assert settings.count("dynamicLogo = matchLauncherIconToPalette") >= 6
assert "https://omerfaruknehir.github.io/Xylune/privacy/" in (ROOT / "README.md").read_text(encoding="utf-8")
assert (ROOT / "docs/assets/js/site.js").read_text(encoding="utf-8").count("function syncBrandLogo") == 1
print("Legal-page, About-page, and dynamic-logo patch applied successfully.")
