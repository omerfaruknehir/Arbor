from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old!r}")
    file.write_text(text.replace(old, new, 1))


Path("app/src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").write_text(r'''package app.arbor.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbor.chat.R
import app.arbor.chat.settings.ThemeMode

private enum class OnboardingStep { WELCOME, APPEARANCE, PROVIDER }

internal fun shouldBlockForProviderCatalog(catalogReady: Boolean, graceExpired: Boolean): Boolean =
    !catalogReady && !graceExpired

internal fun shouldShowProviderOnboarding(
    catalogReady: Boolean,
    hasConfiguredProvider: Boolean,
    dismissedForSession: Boolean,
): Boolean = catalogReady && !hasConfiguredProvider && !dismissedForSession

@Composable
internal fun ArborStartupScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Preparing Arbor…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun OnboardingScreen(
    currentThemeMode: ThemeMode,
    providerCatalogDelayed: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onOpenProviderSetup: () -> Unit,
    onExplore: () -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = OnboardingStep.values()[stepIndex.coerceIn(0, OnboardingStep.values().lastIndex)]
    val haptics = rememberArborHaptics()

    BackHandler(enabled = stepIndex > 0) {
        haptics.selection()
        stepIndex--
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            OnboardingProgressHeader(
                stepIndex = stepIndex,
                showBack = stepIndex > 0,
                onBack = {
                    haptics.selection()
                    stepIndex--
                },
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 18.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.APPEARANCE -> AppearanceStep(
                        currentThemeMode = currentThemeMode,
                        onThemeModeChanged = {
                            haptics.selection()
                            onThemeModeChanged(it)
                        },
                    )
                    OnboardingStep.PROVIDER -> ProviderStep(providerCatalogDelayed)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            when (step) {
                OnboardingStep.WELCOME -> {
                    Button(
                        onClick = {
                            haptics.confirm()
                            stepIndex = 1
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Get started") }
                    TextButton(
                        onClick = {
                            haptics.selection()
                            onExplore()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Skip for now") }
                }
                OnboardingStep.APPEARANCE -> {
                    Button(
                        onClick = {
                            haptics.confirm()
                            stepIndex = 2
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue") }
                    OutlinedButton(
                        onClick = {
                            haptics.selection()
                            onExplore()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Skip setup") }
                }
                OnboardingStep.PROVIDER -> {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onOpenProviderSetup()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open provider setup") }
                    OutlinedButton(
                        onClick = {
                            haptics.selection()
                            onExplore()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Continue without a provider") }
                }
            }
            Text(
                "Setup is never permanent. You can change the theme or add providers later in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun OnboardingProgressHeader(
    stepIndex: Int,
    showBack: Boolean,
    onBack: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Previous setup step")
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Step ${stepIndex + 1} of 3",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { index ->
                    Surface(
                        modifier = Modifier.weight(1f).height(4.dp),
                        color = if (index <= stepIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.small,
                    ) {}
                }
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(8.dp))
    Image(
        painter = painterResource(R.drawable.ic_arbor_mark),
        contentDescription = "Arbor",
        modifier = Modifier.size(96.dp),
    )
    Text(
        "Welcome to Arbor",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Text(
        "A private, native workspace for AI chat and agent work.",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OnboardingValueRow(Icons.Outlined.Lock, "Private by design", "Chats and credentials stay on this device.")
            OnboardingValueRow(Icons.Outlined.Cloud, "Your providers", "Connect directly to ChatGPT, an API, or a local server.")
            OnboardingValueRow(Icons.Outlined.Code, "Tools when you need them", "Search, files, Python, and optional Linux workspaces.")
        }
    }
}

@Composable
private fun AppearanceStep(
    currentThemeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
) {
    Text(
        "Choose your theme",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "The preview applies immediately. System follows your phone; Light and Dark stay fixed until you change them.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeModeChoice(
            icon = Icons.Outlined.SettingsBrightness,
            title = "System",
            subtitle = "Match Android automatically",
            selected = currentThemeMode == ThemeMode.SYSTEM,
            onClick = { onThemeModeChanged(ThemeMode.SYSTEM) },
        )
        ThemeModeChoice(
            icon = Icons.Outlined.LightMode,
            title = "Light",
            subtitle = "Always use the light theme",
            selected = currentThemeMode == ThemeMode.LIGHT,
            onClick = { onThemeModeChanged(ThemeMode.LIGHT) },
        )
        ThemeModeChoice(
            icon = Icons.Outlined.DarkMode,
            title = "Dark",
            subtitle = "Always use the dark theme",
            selected = currentThemeMode == ThemeMode.DARK,
            onClick = { onThemeModeChanged(ThemeMode.DARK) },
        )
    }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Live preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Every setup screen uses the selected color scheme, including status-bar icon contrast.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ThemeModeChoice(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun ProviderStep(providerCatalogDelayed: Boolean) {
    Text(
        "Connect a model",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        "A provider is required only when you are ready to send messages. You can safely enter the app first and return to setup later.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    if (providerCatalogDelayed) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "The built-in provider catalog is taking longer than expected. Setup remains usable, and Arbor will keep loading it in the background.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OnboardingValueRow(Icons.Outlined.AccountCircle, "ChatGPT account", "Sign in without pasting an API key.")
            OnboardingValueRow(Icons.Outlined.Cloud, "API provider", "Use OpenAI, Anthropic, Gemini, DeepSeek, or another compatible endpoint.")
            OnboardingValueRow(Icons.Outlined.Storage, "Local server", "Connect to Ollama, llama.cpp, or LM Studio on this device.")
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Lock, null)
            Text(
                "API keys are encrypted with Android Keystore and sent only to the provider you choose.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OnboardingValueRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.size(42.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(22.dp))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
''')

arbor = "app/src/main/java/app/arbor/chat/ui/ArborApp.kt"
replace_once(
    arbor,
    "import app.arbor.chat.settings.PerformanceOverlayPosition\n",
    "import app.arbor.chat.settings.PerformanceOverlayPosition\nimport kotlinx.coroutines.delay\n",
)
replace_once(
    arbor,
    "    val providerCatalogReady by viewModel.providerCatalogReady.collectAsState()\n",
    "    val providerCatalogReady by viewModel.providerCatalogReady.collectAsState()\n    val themeMode by viewModel.themeMode.collectAsState()\n",
)
replace_once(
    arbor,
    "    var onboardingDismissedForSession by rememberSaveable { mutableStateOf(false) }\n",
    "    var onboardingDismissedForSession by rememberSaveable { mutableStateOf(false) }\n    var providerCatalogGraceExpired by rememberSaveable { mutableStateOf(false) }\n",
)
replace_once(
    arbor,
    '''    if (!providerCatalogReady) {
        ArborStartupScreen()
        return
    }
    if (
        shouldShowProviderOnboarding(
            catalogReady = providerCatalogReady,
            hasConfiguredProvider = configuredProviders.isNotEmpty(),
            dismissedForSession = onboardingDismissedForSession,
        )
    ) {
        OnboardingScreen(
            onOpenProviderSetup = {
                onboardingDismissedForSession = true
                viewModel.openProviderSetup()
            },
            onExplore = { onboardingDismissedForSession = true },
        )
        return
    }
''',
    '''    LaunchedEffect(providerCatalogReady) {
        if (providerCatalogReady) {
            providerCatalogGraceExpired = false
        } else {
            delay(8_000)
            providerCatalogGraceExpired = true
        }
    }
    if (shouldBlockForProviderCatalog(providerCatalogReady, providerCatalogGraceExpired)) {
        ArborStartupScreen()
        return
    }
    val onboardingCatalogUsable = providerCatalogReady || providerCatalogGraceExpired
    if (
        shouldShowProviderOnboarding(
            catalogReady = onboardingCatalogUsable,
            hasConfiguredProvider = configuredProviders.isNotEmpty(),
            dismissedForSession = onboardingDismissedForSession,
        )
    ) {
        OnboardingScreen(
            currentThemeMode = themeMode,
            providerCatalogDelayed = !providerCatalogReady,
            onThemeModeChanged = viewModel::setThemeMode,
            onOpenProviderSetup = {
                onboardingDismissedForSession = true
                viewModel.openProviderSetup()
            },
            onExplore = { onboardingDismissedForSession = true },
        )
        return
    }
''',
)

Path("app/src/test/java/app/arbor/chat/ui/OnboardingFlowTest.kt").write_text(r'''package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {
    @Test
    fun `startup wait has a bounded escape path`() {
        assertTrue(shouldBlockForProviderCatalog(catalogReady = false, graceExpired = false))
        assertFalse(shouldBlockForProviderCatalog(catalogReady = false, graceExpired = true))
        assertFalse(shouldBlockForProviderCatalog(catalogReady = true, graceExpired = false))
    }

    @Test
    fun `setup appears only after the provider catalog is usable`() {
        assertFalse(shouldShowProviderOnboarding(false, false, false))
        assertTrue(shouldShowProviderOnboarding(true, false, false))
    }

    @Test
    fun `configured or session-dismissed users reach the app`() {
        assertFalse(shouldShowProviderOnboarding(true, true, false))
        assertFalse(shouldShowProviderOnboarding(true, false, true))
    }

    @Test
    fun `onboarding has contrast-safe root live theme selection and escape actions`() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        assertTrue(source.contains("contentColor = MaterialTheme.colorScheme.onBackground"))
        assertTrue(source.contains("ThemeMode.SYSTEM"))
        assertTrue(source.contains("ThemeMode.LIGHT"))
        assertTrue(source.contains("ThemeMode.DARK"))
        assertTrue(source.contains("Skip for now"))
        assertTrue(source.contains("Continue without a provider"))
        assertTrue(source.contains("BackHandler(enabled = stepIndex > 0)"))
    }

    @Test
    fun `chat exposes provider and Linux setup states`() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        assertTrue(chat.contains("Connect a model provider"))
        assertTrue(chat.contains("Set up a provider to start"))
        assertTrue(chat.contains("Linux workspace not installed"))
        assertTrue(chat.contains("Manage Linux workspace"))
    }

    @Test
    fun `Linux management has one owner`() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val workspace = java.io.File("src/main/java/app/arbor/chat/ui/SandboxScreen.kt").readText()
        val terminal = java.io.File("src/main/java/app/arbor/chat/ui/LinuxTerminalScreen.kt").readText()
        assertTrue(settings.contains("Manage tool workspace"))
        assertTrue(workspace.contains("Install ${ubuntuStatus.distribution.displayName}"))
        assertTrue(workspace.contains("Remove Linux workspace"))
        assertFalse(terminal.contains("selectLinuxDistribution"))
        assertFalse(terminal.contains("installUbuntu"))
        assertFalse(terminal.contains("removeUbuntu"))
    }
}
''')

replace_once("app/build.gradle.kts", "versionCode = 139", "versionCode = 140")
replace_once("app/build.gradle.kts", 'versionName = "0.20.13"', 'versionName = "0.20.14"')

readme = Path("README.md")
readme.write_text(readme.read_text().replace("0.20.13", "0.20.14"))

changelog = Path("CHANGELOG.md")
text = changelog.read_text()
marker = "# Changelog\n"
section = """

## 0.20.14 — 2026-07-31

- Fix onboarding text contrast by giving the full setup surface an explicit theme-aware content color.
- Add live System, Light, and Dark theme selection to initial setup.
- Keep setup actions fixed and reachable while the page body scrolls on small screens.
- Add Back handling, skip paths on every step, and clear recovery language so setup cannot trap the user.
- Bound the provider-catalog startup wait to eight seconds and continue with a recoverable delayed-catalog state instead of an infinite spinner.
- Preserve the existing provider-based onboarding rule; skipping never permanently hides setup when no provider is configured.
"""
if "## 0.20.14 — 2026-07-31" not in text:
    if marker not in text:
        raise SystemExit("CHANGELOG heading not found")
    changelog.write_text(text.replace(marker, marker + section, 1))

Path("docs/releases/RELEASE_NOTES_0.20.14.md").write_text(r'''# Arbor 0.20.14

## Setup and onboarding

- Fixed the dark-theme welcome title and other inherited content colors.
- Added live System, Light, and Dark theme selection during setup.
- Reworked setup into three clear steps with progress, predictable Back behavior, and fixed bottom actions.
- Added a reachable skip/continue path at every stage. Skipping is session-only when no provider exists, so setup is never permanently lost.
- Replaced the unbounded provider-catalog startup spinner with an eight-second grace period and a recoverable delayed-catalog state.
- Improved small-screen behavior by scrolling only the page body while keeping primary actions visible.

## Compatibility

- Existing chats, providers, credentials, settings, workspaces, package ID, Room schema, and signing/update compatibility are unchanged.
- Developer settings and the performance overlay remain available in the optimized release build.

## Assets

- Optimized release APK
- Release AAB
- Versioned source ZIP and TAR.GZ archives
- SHA-256 checksums for all attached assets
''')
