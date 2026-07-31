package app.arbor.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.arbor.chat.sandbox.UbuntuRuntimeStatus
import app.arbor.chat.sandbox.UbuntuStage
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.settings.ThemeMode
import app.arbor.chat.ui.theme.palettePreviewColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class OnboardingStep { WELCOME, APPEARANCE, PROVIDER, TOOLS, READY }

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnboardingScreen(
    viewModel: ChatViewModel,
    currentThemeMode: ThemeMode,
    currentPalette: ColorPalette,
    matchLauncherIconToPalette: Boolean,
    amoled: Boolean,
    providerCatalogDelayed: Boolean,
    configuredProviderCount: Int,
    pythonEnabled: Boolean,
    linuxEnabled: Boolean,
    linuxStatus: UbuntuRuntimeStatus,
    stepIndex: Int,
    stepOffsetFraction: Float,
    scrollOffsetForStep: (Int) -> Int,
    onPagerPositionChanged: (Int, Float) -> Unit,
    onStepScrollChanged: (Int, Int) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onPaletteChanged: (ColorPalette) -> Unit,
    onMatchLauncherIconToPaletteChanged: (Boolean) -> Unit,
    onAmoledChanged: (Boolean) -> Unit,
    onPythonEnabledChanged: (Boolean) -> Unit,
    onLinuxEnabledChanged: (Boolean) -> Unit,
    onOpenProviderSetup: () -> Unit,
    onOpenLinuxSetup: () -> Unit,
    onExplore: () -> Unit,
) {
    val steps = OnboardingStep.entries
    val initialPage = stepIndex.coerceIn(0, steps.lastIndex)
    val initialOffset = stepOffsetFraction.coerceIn(-0.499f, 0.499f)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { steps.size })
    val pageScrollStates = listOf(
        rememberScrollState(initial = scrollOffsetForStep(0)),
        rememberScrollState(initial = scrollOffsetForStep(1)),
        rememberScrollState(initial = scrollOffsetForStep(2)),
        rememberScrollState(initial = scrollOffsetForStep(3)),
        rememberScrollState(initial = scrollOffsetForStep(4)),
    )
    val scope = rememberCoroutineScope()
    val haptics = rememberArborHaptics()
    val currentPage = pagerState.currentPage.coerceIn(0, steps.lastIndex)
    val pagePosition = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, steps.lastIndex.toFloat())
    val step = steps[currentPage]

    fun moveTo(page: Int) {
        val target = page.coerceIn(0, steps.lastIndex)
        scope.launch { pagerState.animateScrollToPage(target) }
    }

    LaunchedEffect(pagerState) {
        pagerState.scrollToPage(initialPage, initialOffset)
        snapshotFlow { pagerState.currentPage to pagerState.currentPageOffsetFraction }
            .distinctUntilChanged()
            .collect { (page, offset) -> onPagerPositionChanged(page, offset) }
    }
    pageScrollStates.forEachIndexed { page, state ->
        SetupScrollReporter(page, state, onStepScrollChanged)
    }

    BackHandler(enabled = currentPage > 0) {
        haptics.selection()
        moveTo(currentPage - 1)
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
                pagePosition = pagePosition,
                currentStepIndex = currentPage,
                stepCount = steps.size,
                showBack = currentPage > 0,
                onBack = {
                    haptics.selection()
                    moveTo(currentPage - 1)
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { steps[it].name },
                beyondViewportPageCount = 1,
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(pageScrollStates[page])
                        .padding(top = 18.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    when (steps[page]) {
                        OnboardingStep.WELCOME -> WelcomeStep(viewModel)
                        OnboardingStep.APPEARANCE -> AppearanceStep(
                            currentThemeMode = currentThemeMode,
                            currentPalette = currentPalette,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,
                            amoled = amoled,
                            onThemeModeChanged = {
                                haptics.selection()
                                onThemeModeChanged(it)
                            },
                            onPaletteChanged = {
                                haptics.selection()
                                onPaletteChanged(it)
                            },
                            onMatchLauncherIconToPaletteChanged = onMatchLauncherIconToPaletteChanged,
                            onAmoledChanged = onAmoledChanged,
                        )
                        OnboardingStep.PROVIDER -> ProviderStep(
                            providerCatalogDelayed = providerCatalogDelayed,
                            configuredProviderCount = configuredProviderCount,
                        )
                        OnboardingStep.TOOLS -> ToolsStep(
                            pythonEnabled = pythonEnabled,
                            linuxEnabled = linuxEnabled,
                            linuxStatus = linuxStatus,
                            onPythonEnabledChanged = onPythonEnabledChanged,
                            onLinuxEnabledChanged = onLinuxEnabledChanged,
                        )
                        OnboardingStep.READY -> ReadyStep(
                            themeMode = currentThemeMode,
                            palette = currentPalette,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,
                            configuredProviderCount = configuredProviderCount,
                            pythonEnabled = pythonEnabled,
                            linuxEnabled = linuxEnabled,
                            linuxStatus = linuxStatus,
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            when (step) {
                OnboardingStep.WELCOME -> PrimaryNextButton("Set up Arbor") { moveTo(1) }
                OnboardingStep.APPEARANCE -> PrimaryNextButton("Continue") { moveTo(2) }
                OnboardingStep.PROVIDER -> ProviderStepActions(
                    configuredProviderCount = configuredProviderCount,
                    onContinue = { moveTo(3) },
                    onOpenProviderSetup = onOpenProviderSetup,
                )
                OnboardingStep.TOOLS -> ToolsStepActions(
                    linuxEnabled = linuxEnabled,
                    linuxStatus = linuxStatus,
                    onContinue = { moveTo(4) },
                    onOpenLinuxSetup = onOpenLinuxSetup,
                )
                OnboardingStep.READY -> {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onExplore()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Enter Arbor") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenProviderSetup, modifier = Modifier.weight(1f)) {
                            Text("Provider")
                        }
                        OutlinedButton(onClick = onOpenLinuxSetup, modifier = Modifier.weight(1f)) {
                            Text("Linux")
                        }
                    }
                }
            }
            if (step != OnboardingStep.READY) {
                TextButton(
                    onClick = {
                        haptics.selection()
                        onExplore()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Exit setup for now") }
            }
            Text(
                "Nothing here locks you in. Theme, providers, defaults, and local tools remain editable in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ProviderStepActions(
    configuredProviderCount: Int,
    onContinue: () -> Unit,
    onOpenProviderSetup: () -> Unit,
) {
    val haptics = rememberArborHaptics()
    if (configuredProviderCount > 0) {
        Button(
            onClick = {
                haptics.confirm()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue") }
        OutlinedButton(
            onClick = {
                haptics.selection()
                onOpenProviderSetup()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Manage providers") }
    } else {
        Button(
            onClick = {
                haptics.confirm()
                onOpenProviderSetup()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect a provider now") }
        OutlinedButton(
            onClick = {
                haptics.selection()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue and connect later") }
    }
}

@Composable
private fun ToolsStepActions(
    linuxEnabled: Boolean,
    linuxStatus: UbuntuRuntimeStatus,
    onContinue: () -> Unit,
    onOpenLinuxSetup: () -> Unit,
) {
    val haptics = rememberArborHaptics()
    val installationRequired = linuxEnabled && !linuxStatus.installed
    if (installationRequired) {
        Button(
            onClick = {
                haptics.confirm()
                onOpenLinuxSetup()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(linuxSetupActionLabel(linuxStatus)) }
        OutlinedButton(
            onClick = {
                haptics.selection()
                onContinue()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue without Linux") }
    } else {
        PrimaryNextButton("Continue", onContinue)
        OutlinedButton(
            onClick = {
                haptics.selection()
                onOpenLinuxSetup()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    linuxStatus.installed -> "Manage ${linuxStatus.distribution.displayName}"
                    linuxStatus.stage.isInstalling -> "View Linux installation progress"
                    else -> "Choose a Linux distribution"
                },
            )
        }
    }
}

@Composable
private fun PrimaryNextButton(label: String, onClick: () -> Unit) {
    val haptics = rememberArborHaptics()
    Button(
        onClick = {
            haptics.confirm()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(label) }
}


internal fun setupProgressForSegment(pagePosition: Float, segmentIndex: Int): Float =
    (pagePosition - segmentIndex + 1f).coerceIn(0f, 1f)

@Composable
private fun SetupScrollReporter(
    stepIndex: Int,
    scrollState: ScrollState,
    onStepScrollChanged: (Int, Int) -> Unit,
) {
    LaunchedEffect(stepIndex, scrollState) {
        snapshotFlow { scrollState.value }
            .distinctUntilChanged()
            .collect { onStepScrollChanged(stepIndex, it) }
    }
}

@Composable
private fun OnboardingProgressHeader(
    pagePosition: Float,
    currentStepIndex: Int,
    stepCount: Int,
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
                "Step ${currentStepIndex + 1} of $stepCount",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(stepCount) { index ->
                    val fill = setupProgressForSegment(pagePosition, index)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fill)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun WelcomeStep(viewModel: ChatViewModel) {
    Spacer(Modifier.height(8.dp))
    ArborMark(modifier = Modifier.size(96.dp), contentDescription = "Arbor")
    Text(
        "Welcome to Arbor",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
    )
    Text(
        "Set up only what you need. Arbor works as a private native client, and every choice can be changed later.",
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
            OnboardingValueRow(Icons.Outlined.Lock, "Private by design", "Chats, credentials, and tool workspaces stay on this device.")
            OnboardingValueRow(Icons.Outlined.Cloud, "Bring your own models", "Use a ChatGPT account, API provider, or local server.")
            OnboardingValueRow(Icons.Outlined.Code, "Local tools are optional", "Bundled Python works immediately; Linux requires a separate distribution install.")
        }
    }
    SetupRestoreActions(viewModel)
}

@Composable
private fun AppearanceStep(
    currentThemeMode: ThemeMode,
    currentPalette: ColorPalette,
    matchLauncherIconToPalette: Boolean,
    amoled: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onPaletteChanged: (ColorPalette) -> Unit,
    onMatchLauncherIconToPaletteChanged: (Boolean) -> Unit,
    onAmoledChanged: (Boolean) -> Unit,
) {
    SetupHeading("Make Arbor yours", "Every choice previews immediately across the entire setup flow.")
    Text("Brightness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemeModeChoice(
            icon = Icons.Outlined.SettingsBrightness,
            title = "Follow device",
            subtitle = "Switch with Android automatically",
            selected = currentThemeMode == ThemeMode.SYSTEM,
            onClick = { onThemeModeChanged(ThemeMode.SYSTEM) },
        )
        ThemeModeChoice(
            icon = Icons.Outlined.LightMode,
            title = "Light",
            subtitle = "Keep Arbor light",
            selected = currentThemeMode == ThemeMode.LIGHT,
            onClick = { onThemeModeChanged(ThemeMode.LIGHT) },
        )
        ThemeModeChoice(
            icon = Icons.Outlined.DarkMode,
            title = "Dark",
            subtitle = "Keep Arbor dark",
            selected = currentThemeMode == ThemeMode.DARK,
            onClick = { onThemeModeChanged(ThemeMode.DARK) },
        )
    }
    Text("Color palette", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ColorPalette.entries.forEach { palette ->
            PaletteChoice(
                palette = palette,
                preview = palettePreviewColors(palette, currentThemeMode),
                selected = currentPalette == palette,
                onClick = { onPaletteChanged(palette) },
            )
        }
    }
    Surface(
        color = if (matchLauncherIconToPalette) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (matchLauncherIconToPalette) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().clickable {
            onMatchLauncherIconToPaletteChanged(!matchLauncherIconToPalette)
        },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LauncherIconPreview(if (matchLauncherIconToPalette) currentPalette else ColorPalette.ARBOR)
            Column(Modifier.weight(1f)) {
                Text("Match launcher icon", fontWeight = FontWeight.SemiBold)
                Text(
                    if (matchLauncherIconToPalette) "Use the ${currentPalette.setupName} launcher icon" else "Keep the classic Arbor green icon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = matchLauncherIconToPalette,
                onCheckedChange = onMatchLauncherIconToPaletteChanged,
            )
        }
    }
    Text(
        "The Android dynamic icon uses the real system accent palette and remains multicolored. Changing launcher aliases briefly restarts Arbor after saving the current page, drafts, files, and scroll position.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("AMOLED black", fontWeight = FontWeight.SemiBold)
                Text(
                    if (currentThemeMode == ThemeMode.LIGHT) "Available in dark mode" else "Use true black for the darkest surfaces",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = amoled,
                onCheckedChange = onAmoledChanged,
                enabled = currentThemeMode != ThemeMode.LIGHT,
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
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
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
private fun PaletteChoice(
     palette: ColorPalette,
    preview: app.arbor.chat.ui.theme.PalettePreviewColors,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PaletteSwatch(preview, Modifier.size(width = 58.dp, height = 24.dp))
            Column(Modifier.weight(1f)) {
                Text(palette.setupName, fontWeight = FontWeight.SemiBold)
                Text(palette.setupDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

@Composable
private fun ProviderStep(
    providerCatalogDelayed: Boolean,
    configuredProviderCount: Int,
) {
    SetupHeading(
        if (configuredProviderCount > 0) "Model access connected" else "Connect a model",
        if (configuredProviderCount > 0) {
            "Arbor detected ${providerCountLabel(configuredProviderCount)}. Continue setup or open the provider manager to make changes."
        } else {
            "A provider is required only when you send a message. You can enter Arbor first and connect one later."
        },
    )
    if (configuredProviderCount > 0) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(28.dp))
                Column(Modifier.weight(1f)) {
                    Text("${providerCountLabel(configuredProviderCount)} ready", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Credentials were found and this step is complete.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (providerCatalogDelayed) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "The built-in provider catalog is delayed. Setup remains usable and Arbor will keep retrying without trapping you on a spinner.",
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
            OnboardingValueRow(Icons.Outlined.Storage, "Local server", "Connect to Ollama, llama.cpp, or LM Studio.")
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null)
            Text("Credentials are encrypted with Android Keystore and sent only to the provider you choose.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolsStep(
    pythonEnabled: Boolean,
    linuxEnabled: Boolean,
    linuxStatus: UbuntuRuntimeStatus,
    onPythonEnabledChanged: (Boolean) -> Unit,
    onLinuxEnabledChanged: (Boolean) -> Unit,
) {
    SetupHeading(
        "Local tools",
        "Choose defaults for new chats. Installing Linux is a separate step; this switch does not download a distribution.",
    )
    SetupToggleCard(
        icon = Icons.Outlined.Code,
        title = "Local Python",
        subtitle = "Bundled and ready immediately. Each chat gets a persistent isolated environment.",
        checked = pythonEnabled,
        onCheckedChange = onPythonEnabledChanged,
    )
    SetupToggleCard(
        icon = Icons.Outlined.Storage,
        title = "Enable Linux for new chats",
        subtitle = when {
            linuxStatus.installed -> "${linuxStatus.distribution.displayName} ${linuxStatus.release} is installed and ready."
            linuxEnabled -> "Enabled as a default, but a distribution still needs to be chosen and installed."
            else -> "Optional. Choose Ubuntu, Debian, or Alpine and install it before Linux tools can run."
        },
        checked = linuxEnabled,
        onCheckedChange = onLinuxEnabledChanged,
    )
    LinuxInstallationStatusCard(linuxStatus)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How Linux setup works", fontWeight = FontWeight.SemiBold)
            Text("1. Open the Linux manager and choose Ubuntu, Debian, or Alpine.", style = MaterialTheme.typography.bodySmall)
            Text("2. Review the download, then explicitly start installation.", style = MaterialTheme.typography.bodySmall)
            Text("3. Arbor verifies and extracts it into app-private storage.", style = MaterialTheme.typography.bodySmall)
            Text("4. Packages and terminal access are managed from the same workspace screen.", style = MaterialTheme.typography.bodySmall)
            Text("Chat files remain in /workspace even if the Linux distribution is removed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LinuxInstallationStatusCard(status: UbuntuRuntimeStatus) {
    val installed = status.installed
    val installing = status.stage.isInstalling
    val failed = status.stage == UbuntuStage.ERROR || status.stage == UbuntuStage.UNSUPPORTED
    val containerColor = when {
        installed -> MaterialTheme.colorScheme.primaryContainer
        failed -> MaterialTheme.colorScheme.errorContainer
        installing -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        installed -> MaterialTheme.colorScheme.onPrimaryContainer
        failed -> MaterialTheme.colorScheme.onErrorContainer
        installing -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    when {
                        installed -> Icons.Outlined.CheckCircle
                        failed -> Icons.Outlined.ErrorOutline
                        installing -> Icons.Outlined.Download
                        else -> Icons.Outlined.Storage
                    },
                    null,
                    Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(linuxStatusTitle(status), fontWeight = FontWeight.SemiBold)
                    Text(linuxStatusDetail(status), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (installing) {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun SetupToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (checked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun ReadyStep(
    themeMode: ThemeMode,
    palette: ColorPalette,
    matchLauncherIconToPalette: Boolean,
    configuredProviderCount: Int,
    pythonEnabled: Boolean,
    linuxEnabled: Boolean,
    linuxStatus: UbuntuRuntimeStatus,
) {
    SetupHeading(
        "Arbor is ready",
        "Review the actual setup state below. Optional missing pieces remain actionable instead of blocking entry.",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            OnboardingValueRow(Icons.Outlined.CheckCircle, "Appearance", "${themeMode.setupName} · ${palette.setupName}${if (themeMode != ThemeMode.LIGHT) " · dark surfaces available" else ""}")
            OnboardingValueRow(Icons.Outlined.CheckCircle, "Launcher icon", if (matchLauncherIconToPalette) "Matches ${palette.setupName}" else "Classic Arbor green")
            OnboardingValueRow(
                if (configuredProviderCount > 0) Icons.Outlined.CheckCircle else Icons.Outlined.Cloud,
                "Model provider",
                if (configuredProviderCount > 0) "${providerCountLabel(configuredProviderCount)} connected" else "Not connected yet",
            )
            OnboardingValueRow(Icons.Outlined.CheckCircle, "Local Python", if (pythonEnabled) "Enabled for new chats" else "Off by default")
            OnboardingValueRow(
                if (linuxStatus.installed) Icons.Outlined.CheckCircle else Icons.Outlined.Storage,
                "Linux tools",
                linuxReadySummary(linuxEnabled, linuxStatus),
            )
        }
    }
    Text(
        "Provider and Linux manager buttons remain available below. Neither is required to inspect Arbor or change settings.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SetupHeading(title: String, subtitle: String) {
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun OnboardingValueRow(icon: ImageVector, title: String, subtitle: String) {
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
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun providerCountLabel(count: Int): String =
    if (count == 1) "1 configured provider" else "$count configured providers"

private val UbuntuStage.isInstalling: Boolean
    get() = this == UbuntuStage.DOWNLOADING ||
        this == UbuntuStage.VERIFYING ||
        this == UbuntuStage.EXTRACTING ||
        this == UbuntuStage.CONFIGURING

private fun linuxSetupActionLabel(status: UbuntuRuntimeStatus): String = when {
    status.stage.isInstalling -> "View Linux installation progress"
    status.stage == UbuntuStage.ERROR -> "Fix Linux installation"
    status.stage == UbuntuStage.UNSUPPORTED -> "Review Linux compatibility"
    else -> "Choose and install a distribution"
}

private fun linuxStatusTitle(status: UbuntuRuntimeStatus): String = when {
    status.installed -> "${status.distribution.displayName} ${status.release} installed"
    status.stage.isInstalling -> "Installing ${status.distribution.displayName}"
    status.stage == UbuntuStage.ERROR -> "Linux installation failed"
    status.stage == UbuntuStage.UNSUPPORTED -> "Linux is not supported on this device"
    else -> "No Linux distribution installed"
}

private fun linuxStatusDetail(status: UbuntuRuntimeStatus): String = when {
    status.installed -> status.detail.ifBlank { "The distribution is ready for terminals, packages, and native tools." }
    status.stage.isInstalling -> status.detail.ifBlank { "Keep the Linux manager open to review progress." }
    status.stage == UbuntuStage.ERROR -> status.detail.ifBlank { "Open the Linux manager to retry or choose another distribution." }
    status.stage == UbuntuStage.UNSUPPORTED -> status.detail.ifBlank { "Open the Linux manager for architecture details." }
    else -> "Choose Ubuntu, Debian, or Alpine, review the download, and explicitly install it."
}

private fun linuxReadySummary(enabled: Boolean, status: UbuntuRuntimeStatus): String = when {
    status.installed && enabled -> "${status.distribution.displayName} ${status.release} installed · enabled for new chats"
    status.installed -> "${status.distribution.displayName} ${status.release} installed · off by default"
    enabled -> "Enabled for new chats, but no distribution is installed"
    else -> "Not installed · optional and off by default"
}

private val ThemeMode.setupName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Follow device"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val ColorPalette.setupName: String
    get() = when (this) {
        ColorPalette.ARBOR -> "Arbor"
        ColorPalette.SYSTEM -> "Android dynamic"
        ColorPalette.GRAPHITE -> "Graphite"
        ColorPalette.OCEAN -> "Ocean"
        ColorPalette.VIOLET -> "Violet"
        ColorPalette.SUNSET -> "Sunset"
    }

private val ColorPalette.setupDescription: String
    get() = when (this) {
        ColorPalette.ARBOR -> "Natural Arbor green"
        ColorPalette.SYSTEM -> "Generated from your wallpaper on Android 12+"
        ColorPalette.GRAPHITE -> "Restrained blue-gray"
        ColorPalette.OCEAN -> "Cool teal and cyan"
        ColorPalette.VIOLET -> "Deep purple accents"
        ColorPalette.SUNSET -> "Warm orange and rose"
    }
