package app.arbor.chat.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeveloperMode
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.arbor.chat.BuildConfig
import app.arbor.chat.R
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.DefaultCatalog
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.data.ThinkingEffort
import app.arbor.chat.data.AuxiliaryMode
import app.arbor.chat.data.AutomationSettingsEntity
import app.arbor.chat.data.PackageApprovalMode
import app.arbor.chat.data.SystemPromptMode
import app.arbor.chat.data.SystemPromptProfileEntity
import app.arbor.chat.provider.DiscoveredModel
import app.arbor.chat.provider.ModelRequestPolicy
import app.arbor.chat.provider.ModelRequestType
import app.arbor.chat.provider.OpenAiOAuthState
import app.arbor.chat.provider.OpenAiOAuthUsageSnapshot
import app.arbor.chat.provider.OpenAiOAuthUsageState
import app.arbor.chat.provider.OpenAiOAuthUsageWindow
import app.arbor.chat.provider.supportedThinkingLevels
import app.arbor.chat.settings.CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT
import app.arbor.chat.settings.CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.settings.DeveloperSettings
import app.arbor.chat.settings.PerformanceOverlayPosition
import app.arbor.chat.settings.NewChatDefaults
import app.arbor.chat.settings.ThemeMode
import app.arbor.chat.settings.chromeEdgeControlPositionForSoftness
import app.arbor.chat.settings.displayedChromeEdgeSoftness
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt


private val LocalSettingsScaffoldPadding = compositionLocalOf { PaddingValues() }

private enum class SettingsRoute(val title: String) {
    HOME("Settings"),
    DEFAULTS("New chat defaults"),
    AUTOMATION("Automation"),
    APPEARANCE("Appearance"),
    PRIVACY("Privacy & safety"),
    LOCAL_EXECUTION("Local tools"),
    DEVELOPER("Developer settings"),
    SYSTEM_PROMPTS("Custom instructions"),
    PROVIDERS("Providers & models"),
    ABOUT("About Arbor"),
    LICENSES("Licenses & notices"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val defaults by viewModel.newChatDefaults.collectAsStateWithLifecycle()
    val automation by viewModel.automationSettings.collectAsStateWithLifecycle()
    val promptProfiles by viewModel.systemPromptProfiles.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val openAiOAuthStates by viewModel.openAiOAuthStates.collectAsStateWithLifecycle()
    val openAiOAuthUsageStates by viewModel.openAiOAuthUsageStates.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsState()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsState()
    val renderSafeMode by viewModel.renderSafeMode.collectAsState()
    val generatedRepairMaxAttempts by viewModel.generatedRepairMaxAttempts.collectAsState()
    val developerSettings by viewModel.developerSettings.collectAsState()
    val providerSetupRequested by viewModel.providerSetupRequested.collectAsState()
    val registeredProviders = remember(providers, credentialRevision) { viewModel.registeredProviders(providers) }
    val configuredProviders = remember(providers, credentialRevision) { viewModel.configuredProviders(providers) }
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HOME) }
    val haptics = rememberArborHaptics()

    LaunchedEffect(providerSetupRequested) {
        if (providerSetupRequested) {
            route = SettingsRoute.PROVIDERS
            viewModel.consumeProviderSetupRequest()
        }
    }

    PredictiveNavigationHost(
        targetState = route,
        backTarget = when (route) {
            SettingsRoute.HOME -> null
            SettingsRoute.DEVELOPER, SettingsRoute.LICENSES -> SettingsRoute.ABOUT
            else -> SettingsRoute.HOME
        },
        onBack = { route = it },
        depth = {
            when (it) {
                SettingsRoute.HOME -> 0
                SettingsRoute.DEVELOPER, SettingsRoute.LICENSES -> 2
                else -> 1
            }
        },
        modifier = Modifier.fillMaxSize(),
        label = "SettingsPageNavigation",
    ) { currentRoute ->
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
        val blurState = rememberArborBackdropBlurState()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CollapsingTranslucentTopBar(
                    title = currentRoute.title,
                    scrollBehavior = scrollBehavior,
                    blurState = blurState,
                    blurStrength = chromeBlurStrength,
                    edgeSoftness = chromeEdgeSoftness,
                    overlayOpacity = chromeOverlayOpacity,
                    blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp,
                    navigationIcon = {
                        IconButton(onClick = {
                            haptics.selection()
                            if (currentRoute == SettingsRoute.DEVELOPER || currentRoute == SettingsRoute.LICENSES) {
                                route = SettingsRoute.ABOUT
                            }
                            else if (currentRoute != SettingsRoute.HOME) route = SettingsRoute.HOME
                            else if (openDrawer != null) openDrawer()
                            else viewModel.screen.value = Screen.CHAT
                        }) {
                            Icon(
                                if (currentRoute == SettingsRoute.HOME && openDrawer != null) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack,
                                "Back",
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().arborBackdropSource(blurState)) {
                CompositionLocalProvider(LocalSettingsScaffoldPadding provides padding) {
                    when (currentRoute) {
                        SettingsRoute.HOME -> SettingsHome(
                            providerCount = registeredProviders.size,
                            onOpen = { route = it },
                        )
                        SettingsRoute.DEFAULTS -> NewChatDefaultsSettings(defaults, configuredProviders, viewModel)
                        SettingsRoute.AUTOMATION -> AutomationSettingsPage(automation, configuredProviders, viewModel)
                        SettingsRoute.APPEARANCE -> AppearanceSettingsPage(
                            themeMode = themeMode,
                            amoled = amoled,
                            palette = palette,
                            chromeBlurStrength = chromeBlurStrength,
                            chromeEdgeSoftness = chromeEdgeSoftness,
                            chromeOverlayOpacity = chromeOverlayOpacity,
                            viewModel = viewModel,
                        )
                        SettingsRoute.PRIVACY -> PrivacySettingsPage(renderSafeMode, generatedRepairMaxAttempts, viewModel)
                        SettingsRoute.LOCAL_EXECUTION -> LocalCodeExecutionSettingsPage(defaults, automation, configuredProviders, viewModel)
                        SettingsRoute.DEVELOPER -> DeveloperSettingsPage(developerSettings, viewModel)
                        SettingsRoute.SYSTEM_PROMPTS -> SystemPromptProfilesPage(promptProfiles, defaults.systemPromptProfileId, viewModel)
                        SettingsRoute.PROVIDERS -> ProviderSettings(
                            providers = providers,
                            registeredProviders = registeredProviders,
                            conversationProviderId = null,
                            openAiOAuthStates = openAiOAuthStates,
                            openAiOAuthUsageStates = openAiOAuthUsageStates,
                            viewModel = viewModel,
                        )
                        SettingsRoute.ABOUT -> AboutSettingsPage(
                            developerEnabled = developerSettings.enabled,
                            onOpenDeveloper = { route = SettingsRoute.DEVELOPER },
                            onOpenLicenses = { route = SettingsRoute.LICENSES },
                        )
                        SettingsRoute.LICENSES -> LicenseCatalogSettingsPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(providerCount: Int, onOpen: (SettingsRoute) -> Unit) = SettingsPage {
    SettingsGroup("AI & models") {
        SettingsDestination(
            icon = Icons.Outlined.Cloud,
            title = "Providers & models",
            subtitle = if (providerCount == 0) "Add your first API provider" else "$providerCount provider${if (providerCount == 1) "" else "s"} configured",
            onClick = { onOpen(SettingsRoute.PROVIDERS) },
        )
        SettingsDestination(
            icon = Icons.Outlined.SmartToy,
            title = "New chat defaults",
            subtitle = "Model, thinking, tools, context, and output",
            onClick = { onOpen(SettingsRoute.DEFAULTS) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Tune,
            title = "Custom instructions",
            subtitle = "Reusable tone and workflow profiles",
            onClick = { onOpen(SettingsRoute.SYSTEM_PROMPTS) },
        )
        SettingsDestination(
            icon = Icons.Outlined.AutoAwesome,
            title = "Automation",
            subtitle = "Naming, compression, and package approval",
            onClick = { onOpen(SettingsRoute.AUTOMATION) },
        )
    }
    SettingsGroup("App") {
        SettingsDestination(
            icon = Icons.Outlined.Palette,
            title = "Appearance",
            subtitle = "Theme mode, colors, and AMOLED black",
            onClick = { onOpen(SettingsRoute.APPEARANCE) },
        )
        SettingsDestination(
            icon = Icons.Outlined.PrivacyTip,
            title = "Privacy & safety",
            subtitle = "Generated UI safety and local-data behavior",
            onClick = { onOpen(SettingsRoute.PRIVACY) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Code,
            title = "Local tools",
            subtitle = "Defaults, workspace, and package approvals",
            onClick = { onOpen(SettingsRoute.LOCAL_EXECUTION) },
        )
    }
    SettingsGroup("About") {
        SettingsDestination(
            icon = Icons.Outlined.Info,
            title = "About Arbor",
            subtitle = "Version, architecture, and privacy model",
            onClick = { onOpen(SettingsRoute.ABOUT) },
        )
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsDestination(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val haptics = rememberArborHaptics()
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable {
            haptics.selection()
            onClick()
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
internal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    val scaffoldPadding = LocalSettingsScaffoldPadding.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = scaffoldPadding.calculateTopPadding() + 20.dp,
                bottom = scaffoldPadding.calculateBottomPadding() + 20.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        content = content,
    )
}

@Composable
private fun NewChatDefaultsSettings(
    defaults: NewChatDefaults,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle(
        "New chat defaults",
        "New conversations copy these values once. Existing chats keep their own persistent controls.",
    )
    ChatOptionsEditor(
        providerId = defaults.selectedProviderId,
        modelId = defaults.selectedModelId,
        providers = providers,
        thinkingEnabled = defaults.thinkingEnabled,
        thinkingEffort = defaults.thinkingEffort,
        webEnabled = defaults.webSearchEnabled,
        deepResearchEnabled = defaults.deepResearchEnabled,
        hybridTokenCountingEnabled = defaults.hybridTokenCountingEnabled,
        contextPairs = defaults.contextPairs,
        contextTokenLimit = defaults.contextTokenLimit,
        workingTokenLimit = defaults.workingTokenLimit,
        maxOutputTokens = defaults.maxOutputTokens,
        reasoningVisibility = defaults.reasoningVisibility,
        viewModel = viewModel,
        onModel = { providerId, modelId -> viewModel.updateNewChatDefaults { it.copy(selectedProviderId = providerId, selectedModelId = modelId) } },
        onThinkingEnabled = { enabled -> viewModel.updateNewChatDefaults { it.copy(thinkingEnabled = enabled) } },
        onThinkingEffort = { effort -> viewModel.updateNewChatDefaults { it.copy(thinkingEffort = effort) } },
        onWeb = { enabled -> viewModel.updateNewChatDefaults { it.copy(webSearchEnabled = enabled, deepResearchEnabled = it.deepResearchEnabled && enabled) } },
        onDeepResearch = { enabled -> viewModel.updateNewChatDefaults { it.copy(deepResearchEnabled = enabled, webSearchEnabled = it.webSearchEnabled || enabled) } },
        onHybridTokenCounting = { enabled -> viewModel.updateNewChatDefaults { it.copy(hybridTokenCountingEnabled = enabled) } },
        onContextPairs = { value -> viewModel.updateNewChatDefaults { it.copy(contextPairs = value) } },
        onContextLimit = { value -> viewModel.updateNewChatDefaults { it.copy(contextTokenLimit = value) } },
        onWorkingLimit = { value -> viewModel.updateNewChatDefaults { it.copy(workingTokenLimit = value) } },
        onOutputLimit = { value -> viewModel.updateNewChatDefaults { it.copy(maxOutputTokens = value) } },
        onReasoningVisibility = { value -> viewModel.updateNewChatDefaults { it.copy(reasoningVisibility = value) } },
    )
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun AutomationSettingsPage(
    automation: AutomationSettingsEntity,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("Automation models", "Global services used for naming, context compression, and package review.")
    if (providers.isEmpty()) {
        Text("Configure a usable provider to enable model-based automation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    AutomationPolicyEditor(
        title = "Chat naming",
        subtitle = "Model mode considers newer messages whenever a name is regenerated.",
        mode = automation.titleMode,
        providerId = automation.titleProviderId,
        modelId = automation.titleModelId,
        providers = providers,
        viewModel = viewModel,
        onChange = { mode, providerId, modelId ->
            viewModel.updateAutomationSettings { it.copy(titleMode = mode, titleProviderId = providerId, titleModelId = modelId) }
        },
    )
    AutomationPolicyEditor(
        title = "Context compression",
        subtitle = "Older messages outside the active context window are merged into saved compact context.",
        mode = automation.compressionMode,
        providerId = automation.compressionProviderId,
        modelId = automation.compressionModelId,
        providers = providers,
        viewModel = viewModel,
        onChange = { mode, providerId, modelId ->
            viewModel.updateAutomationSettings { it.copy(compressionMode = mode, compressionProviderId = providerId, compressionModelId = modelId) }
        },
    )
    HorizontalDivider()
    SectionTitle("Package approval", "Global policy for pip, apt, and apk package requests.")
    PackageApprovalEditor(automation, providers, viewModel)
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun AppearanceSettingsPage(
    themeMode: ThemeMode,
    amoled: Boolean,
    palette: ColorPalette,
    chromeBlurStrength: Float,
    chromeEdgeSoftness: Float,
    chromeOverlayOpacity: Float,
    viewModel: ChatViewModel,
) = SettingsPage {
    val appName = stringResource(R.string.app_name)
    val appNamePossessive = stringResource(R.string.app_name_possessive)
    SectionTitle("Theme mode", "Choose whether $appName follows Android or stays light or dark.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { option ->
            FilterChip(
                selected = themeMode == option,
                onClick = { viewModel.setThemeMode(option) },
                label = { Text(option.displayName) },
                leadingIcon = if (themeMode == option) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }

    HorizontalDivider()
    SectionTitle("Color scheme", "Use $appName green, a neutral graphite palette, or Android dynamic colors.")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPalette.entries.forEach { option ->
            Surface(
                onClick = { viewModel.setPalette(option) },
                color = if (palette == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = when (option) {
                            ColorPalette.ARBOR -> MaterialTheme.colorScheme.primary
                            ColorPalette.GRAPHITE -> MaterialTheme.colorScheme.secondary
                            ColorPalette.SYSTEM -> MaterialTheme.colorScheme.tertiary
                        },
                        shape = androidx.compose.foundation.shape.CircleShape,
                        modifier = Modifier.size(28.dp),
                    ) {}
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(if (option == ColorPalette.ARBOR) appName else option.displayName, fontWeight = FontWeight.SemiBold)
                        Text(if (option == ColorPalette.ARBOR) "$appNamePossessive green Material palette" else option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (palette == option) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    SettingsSwitch("AMOLED black", amoled, viewModel::setAmoled, enabled = themeMode != ThemeMode.LIGHT)
    Text("AMOLED black only changes dark mode surfaces.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    HorizontalDivider()
    SectionTitle("Interface panels", "Panel shape is a choice. Blur, softness, and tint remain continuous controls.")
    val blurHiddenByTint = chromeBlurStrength > 0f && chromeOverlayOpacity >= .999f
    SettingSlider(
        label = "Blur",
        valueLabel = if (blurHiddenByTint) "Hidden by tint" else "${(chromeBlurStrength * 100).roundToInt()}%",
        value = chromeBlurStrength,
        onValueChange = viewModel::setChromeBlurStrength,
        valueRange = 0f..1f,
        supportingText = if (blurHiddenByTint) {
            "Tint is fully opaque and covers the blurred background. Lower Tint opacity to reveal blur."
        } else {
            "0% disables blur. Higher values increase the panel-local blur radius."
        },
    )

    val displayedSoftness = displayedChromeEdgeSoftness(chromeEdgeSoftness)
    val flatEdges = chromeEdgeSoftness >= CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT / 2f
    Text("Panel shape", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { viewModel.setChromeEdgeSoftness(CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT) },
            label = { Text("Rounded") },
            leadingIcon = if (!flatEdges) {
                { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }
            } else null,
        )
        AssistChip(
            onClick = {
                viewModel.setChromeEdgeSoftness(
                    chromeEdgeControlPositionForSoftness(displayedSoftness),
                )
            },
            label = { Text("Flat") },
            leadingIcon = if (flatEdges) {
                { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }
            } else null,
        )
    }

    SettingSlider(
        label = "Edge softness",
        valueLabel = if (!flatEdges || displayedSoftness <= 0f) "Hard" else "${(displayedSoftness * 100).roundToInt()}%",
        value = displayedSoftness,
        onValueChange = {
            viewModel.setChromeEdgeSoftness(chromeEdgeControlPositionForSoftness(it))
        },
        valueRange = 0f..1f,
        enabled = flatEdges,
        supportingText = if (flatEdges) {
            "Softens the boundary where flat panels merge into the page."
        } else {
            "Rounded panels use a hard, rounded boundary. Choose Flat to adjust softness."
        },
    )

    SettingSlider(
        label = "Tint opacity",
        valueLabel = "${(chromeOverlayOpacity * 100).roundToInt()}%",
        value = chromeOverlayOpacity,
        onValueChange = viewModel::setChromeOverlayOpacity,
        valueRange = 0f..1f,
        supportingText = if (chromeOverlayOpacity >= .999f) {
            "100% is fully opaque and hides background blur."
        } else {
            "0% is transparent. 100% is a fully opaque panel tint."
        },
    )

    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun PrivacySettingsPage(
    renderSafeMode: Boolean,
    generatedRepairMaxAttempts: Int,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("Generated content", "Controls how Arbor handles AI-generated interactive UI.")
    SettingsSwitch("Safe generated rendering", renderSafeMode, viewModel::setRenderSafeMode)
    Text(
        if (renderSafeMode) "Generated widgets are paused and shown as safe fallback content." else "Generated widgets may render, but Arbor still applies its capability checks and crash recovery.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingSlider(
        label = "Automatic repair attempts",
        valueLabel = generatedRepairMaxAttempts.toString(),
        value = generatedRepairMaxAttempts.toFloat(),
        onValueChange = { viewModel.setGeneratedRepairMaxAttempts(it.toInt().coerceIn(1, 5)) },
        valueRange = 1f..5f,
        steps = 3,
        supportingText = "Invalid completed widgets, charts, and diagrams are repaired in place up to this limit.",
    )
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null)
            Text(
                "No Arbor account, ads, analytics, or Arbor cloud. Chat history and API keys remain on this device; traffic goes to endpoints and web tools you explicitly enable.",
                Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun SystemPromptProfilesPage(
    profiles: List<SystemPromptProfileEntity>,
    selectedDefaultId: String?,
    viewModel: ChatViewModel,
) = SettingsPage {
    var editing by remember { mutableStateOf<SystemPromptProfileEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    SectionTitle(
        "Custom instruction profiles",
        "Arbor's versioned core prompt is built into the app and updates with Arbor. Profiles can adjust tone or add preferences, but cannot replace the core capability, tool, research, date, privacy, or safety protocol.",
    )
    FilledTonalButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, null)
        Text("New custom profile", Modifier.padding(start = 8.dp))
    }
    if (profiles.isEmpty()) {
        Text("No saved prompts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    profiles.forEach { profile ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (profile.mode == SystemPromptMode.OVERRIDE) "Override default tone/persona" else "Additional instructions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (selectedDefaultId == profile.id) Text("Default", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(profile.prompt, maxLines = 4, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.updateNewChatDefaults { it.copy(systemPromptProfileId = profile.id) } }) { Text("Use for new chats") }
                    IconButton(onClick = { editing = profile }) { Icon(Icons.Outlined.Edit, "Edit ${profile.name}") }
                    IconButton(onClick = { viewModel.deleteSystemPromptProfile(profile.id) }) { Icon(Icons.Outlined.DeleteOutline, "Delete ${profile.name}") }
                }
            }
        }
    }
    if (selectedDefaultId != null) OutlinedButton(
        onClick = { viewModel.updateNewChatDefaults { it.copy(systemPromptProfileId = null) } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Use Arbor default for new chats") }
    if (creating) SystemPromptEditorDialog(
        title = "New custom profile",
        initial = null,
        onDismiss = { creating = false },
        onSave = { name, prompt, mode -> viewModel.createSystemPromptProfile(name, prompt, mode); creating = false },
    )
    editing?.let { profile ->
        SystemPromptEditorDialog(
            title = "Edit custom profile",
            initial = profile,
            onDismiss = { editing = null },
            onSave = { name, prompt, mode -> viewModel.updateSystemPromptProfile(profile.copy(name = name, prompt = prompt, mode = mode)); editing = null },
        )
    }
}

@Composable
private fun SystemPromptEditorDialog(
    title: String,
    initial: SystemPromptProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, SystemPromptMode) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var prompt by remember(initial?.id) { mutableStateOf(initial?.prompt.orEmpty()) }
    var mode by remember(initial?.id) { mutableStateOf(initial?.mode ?: SystemPromptMode.PREPEND) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == SystemPromptMode.PREPEND, onClick = { mode = SystemPromptMode.PREPEND }, label = { Text("Prepend") })
                    FilterChip(selected = mode == SystemPromptMode.OVERRIDE, onClick = { mode = SystemPromptMode.OVERRIDE }, label = { Text("Override") })
                }
                OutlinedTextField(prompt, { prompt = it.take(64_000) }, label = { Text("Instructions") }, minLines = 8, maxLines = 16, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name, prompt, mode) }, enabled = name.isNotBlank() && prompt.isNotBlank()) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LocalCodeExecutionSettingsPage(
    defaults: NewChatDefaults,
    automation: AutomationSettingsEntity,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle(
        "New-chat tool defaults",
        "Set the starting tool state once here. Existing chats keep their own per-chat choices.",
    )
    SettingsSwitch(
        "Enable Local Code Execution for new chats",
        defaults.agentPythonEnabled,
        { enabled -> viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) } },
    )
    SettingsSwitch(
        "Enable Linux tooling for new chats",
        defaults.agentUbuntuEnabled,
        { enabled -> viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) } },
    )
    Text(
        "Local Python and Linux share each chat's private workspace. Linux setup and packages live in one manager; the terminal opens from there.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { viewModel.screen.value = Screen.SANDBOX },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Code, null)
        Text("Manage tool workspace", Modifier.padding(start = 8.dp))
    }
    SectionTitle(
        "Package installation",
        "Choose when Arbor may install Python or Linux packages and which sources are trusted.",
    )
    PackageApprovalEditor(automation, providers, viewModel)
}

@Composable
private fun DeveloperSettingsPage(
    settings: DeveloperSettings,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle(
        "Developer settings",
        "Local diagnostics for measuring Arbor's rendering and process performance. No metrics are uploaded or stored in chat history.",
    )
    SettingsSwitch(
        label = "Enable developer settings",
        checked = settings.enabled,
        onCheckedChange = { enabled -> viewModel.updateDeveloperSettings { it.copy(enabled = enabled) } },
    )

    HorizontalDivider()
    SectionTitle(
        "Tool diagnostics",
        "Shows raw tool inputs, outputs, source paths, and copyable failure diagnostics inside Working.",
    )
    SettingsSwitch(
        label = "Show tool diagnostics",
        checked = settings.toolDiagnosticsEnabled,
        onCheckedChange = { enabled ->
            viewModel.updateDeveloperSettings { it.copy(toolDiagnosticsEnabled = enabled) }
        },
        enabled = settings.enabled,
    )
    Text(
        "Off by default. Normal chats show only a concise failure summary and Retry.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HorizontalDivider()
    SectionTitle(
        "Performance counter",
        "Shows live frame timing without forcing continuous animation. The monitor observes frames already rendered by Android.",
    )
    SettingsSwitch(
        label = "Show performance overlay",
        checked = settings.performanceOverlayEnabled,
        onCheckedChange = { enabled ->
            viewModel.updateDeveloperSettings {
                it.copy(
                    performanceOverlayEnabled = enabled,
                    diagnosticProfilerEnabled = it.diagnosticProfilerEnabled && enabled,
                )
            }
        },
        enabled = settings.enabled,
    )
    SettingsSwitch(
        label = "Cause profiler",
        checked = settings.diagnosticProfilerEnabled,
        onCheckedChange = { profilerEnabled ->
            viewModel.updateDeveloperSettings {
                it.copy(
                    diagnosticProfilerEnabled = profilerEnabled,
                    performanceOverlayEnabled = it.performanceOverlayEnabled || profilerEnabled,
                    detailedPerformanceOverlay = it.detailedPerformanceOverlay || profilerEnabled,
                )
            }
        },
        enabled = settings.enabled,
    )
    Text(
        "Attributes slow frames to Android frame stages, Arbor blur work, Compose recomposition pressure, allocations, and blocking GC. It adds some diagnostic overhead, so use it while reproducing an issue.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SettingsSwitch(
        label = "Detailed metrics",
        checked = settings.detailedPerformanceOverlay,
        onCheckedChange = { detailed -> viewModel.updateDeveloperSettings { it.copy(detailedPerformanceOverlay = detailed) } },
        enabled = settings.enabled && settings.performanceOverlayEnabled,
    )

    SettingSlider(
        label = "Panel opacity",
        valueLabel = "${(settings.performanceOverlayBackgroundOpacity * 100).roundToInt()}%",
        value = settings.performanceOverlayBackgroundOpacity,
        onValueChange = { value -> viewModel.updateDeveloperSettings { it.copy(performanceOverlayBackgroundOpacity = value) } },
        valueRange = 0f..1f,
        enabled = settings.enabled && settings.performanceOverlayEnabled,
    )
    SettingSlider(
        label = "Text opacity",
        valueLabel = "${(settings.performanceOverlayTextOpacity * 100).roundToInt()}%",
        value = settings.performanceOverlayTextOpacity,
        onValueChange = { value -> viewModel.updateDeveloperSettings { it.copy(performanceOverlayTextOpacity = value) } },
        valueRange = 0f..1f,
        enabled = settings.enabled && settings.performanceOverlayEnabled,
    )
    SettingSlider(
        label = "Overlay scale",
        valueLabel = "${(settings.performanceOverlayScale * 100).roundToInt()}%",
        value = settings.performanceOverlayScale,
        onValueChange = { value -> viewModel.updateDeveloperSettings { it.copy(performanceOverlayScale = value) } },
        valueRange = 0.60f..2.00f,
        enabled = settings.enabled && settings.performanceOverlayEnabled,
    )
    Text(
        "The overlay explicitly shares pointer input with the content underneath and never consumes it. Taps, scrolling, drawer gestures, and back navigation continue through the panel.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text("Update interval", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            listOf(250 to "250 ms", 500 to "500 ms"),
            listOf(1_000 to "1 s", 2_000 to "2 s"),
        ).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (interval, label) ->
                    FilterChip(
                        selected = settings.performanceUpdateIntervalMs == interval,
                        onClick = { viewModel.updateDeveloperSettings { it.copy(performanceUpdateIntervalMs = interval) } },
                        enabled = settings.enabled && settings.performanceOverlayEnabled,
                        label = { Text(label) },
                        leadingIcon = if (settings.performanceUpdateIntervalMs == interval) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }) else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    Text("Overlay position", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceOverlayPosition.entries.take(2).forEach { position ->
                FilterChip(
                    selected = settings.performanceOverlayPosition == position,
                    onClick = { viewModel.updateDeveloperSettings { it.copy(performanceOverlayPosition = position) } },
                    enabled = settings.enabled && settings.performanceOverlayEnabled,
                    label = { Text(position.displayName) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceOverlayPosition.entries.drop(2).forEach { position ->
                FilterChip(
                    selected = settings.performanceOverlayPosition == position,
                    onClick = { viewModel.updateDeveloperSettings { it.copy(performanceOverlayPosition = position) } },
                    enabled = settings.enabled && settings.performanceOverlayEnabled,
                    label = { Text(position.displayName) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Text(
        if (settings.detailedPerformanceOverlay) {
            "Detailed mode shows Choreographer FPS, average/p95/p99 frame interval, jank against the current refresh budget, app CPU, PSS, Java heap, GPU duration when Android reports it, missed vsyncs per second, and total observed frames. Cause profiler ranks primary and secondary causes, reports confidence and severity, and shows the evidence used for attribution alongside FrameMetrics, blur, recomposition, allocation, and GC counters."
        } else {
            "Compact mode shows FPS, average frame time, and jank percentage."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HorizontalDivider()
    SectionTitle(
        "Blur boundary diagnostics",
        "Draws explicit debug guides at the top and bottom panel boundaries. Normal UI no longer draws a boundary highlight.",
    )
    SettingsSwitch(
        label = "Show blur boundary guides",
        checked = settings.blurBoundaryDebugEnabled,
        onCheckedChange = { enabled -> viewModel.updateDeveloperSettings { it.copy(blurBoundaryDebugEnabled = enabled) } },
        enabled = settings.enabled,
    )
    SettingSlider(
        label = "Guide thickness",
        valueLabel = "${settings.blurBoundaryDebugThicknessDp.roundToInt()} dp",
        value = settings.blurBoundaryDebugThicknessDp,
        onValueChange = { value -> viewModel.updateDeveloperSettings { it.copy(blurBoundaryDebugThicknessDp = value) } },
        valueRange = 1f..8f,
        enabled = settings.enabled && settings.blurBoundaryDebugEnabled,
    )
    Text(
        "Guides are bright red and diagnostic-only. They are never shown unless both Developer settings and this toggle are enabled.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.padding(bottom = 24.dp))
}

private val PerformanceOverlayPosition.displayName: String
    get() = when (this) {
        PerformanceOverlayPosition.TOP_START -> "Top left"
        PerformanceOverlayPosition.TOP_END -> "Top right"
        PerformanceOverlayPosition.BOTTOM_START -> "Bottom left"
        PerformanceOverlayPosition.BOTTOM_END -> "Bottom right"
    }

@Composable
private fun AboutSettingsPage(
    developerEnabled: Boolean,
    onOpenDeveloper: () -> Unit,
    onOpenLicenses: () -> Unit,
) = SettingsPage {
    val appName = stringResource(R.string.app_name)
    val applicationInfo = LocalContext.current.applicationInfo
    val uriHandler = LocalUriHandler.current
    SectionTitle("$appName ${BuildConfig.VERSION_NAME}", "Native Android BYOK model workspace.")

    SettingsGroup("Project") {
        ListItem(
            headlineContent = { Text("Created by @omerfaruknehir", fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text("Open the creator's GitHub profile") },
            leadingContent = { Icon(Icons.Outlined.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
            modifier = Modifier.clickable {
                uriHandler.openUri("https://github.com/omerfaruknehir")
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
        HorizontalDivider()
        SettingsDestination(
            icon = Icons.Outlined.Code,
            title = "Source code",
            subtitle = "github.com/omerfaruknehir/Arbor",
            onClick = { uriHandler.openUri("https://github.com/omerfaruknehir/Arbor") },
        )
        HorizontalDivider()
        SettingsDestination(
            icon = Icons.Outlined.Security,
            title = "Licenses & notices",
            subtitle = "Offline dependency catalog and full license texts",
            onClick = onOpenLicenses,
        )
        HorizontalDivider()
        SettingsDestination(
            icon = Icons.Outlined.Info,
            title = "Report an issue",
            subtitle = "Bugs, regressions, and feature requests",
            onClick = { uriHandler.openUri("https://github.com/omerfaruknehir/Arbor/issues") },
        )
    }

    SettingsGroup("Build information") {
        AboutInfoRow("Version", BuildConfig.VERSION_NAME)
        AboutInfoRow("Build", "${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}")
        AboutInfoRow("Package", BuildConfig.APPLICATION_ID)
        AboutInfoRow(
            "Minimum Android",
            androidVersionSummary(applicationInfo.minSdkVersion, isMinimum = true),
        )
        AboutInfoRow(
            "Target Android",
            androidVersionSummary(applicationInfo.targetSdkVersion),
        )
        AboutInfoRow("Running on", "Android ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}")
        AboutInfoRow("Device ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown")
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Private by design", fontWeight = FontWeight.SemiBold)
            Text(
                "Chats, credentials, and workspaces stay on your device. Arbor connects directly to providers you configure and has no application backend, ads, or telemetry.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                Text(
                    "This is a debug-signed development build.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    TextButton(onClick = onOpenDeveloper) {
        Icon(Icons.Outlined.DeveloperMode, null, Modifier.size(18.dp))
        Text(
            if (developerEnabled) "Developer options · enabled" else "Developer options",
            Modifier.padding(start = 8.dp),
        )
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "Follow device"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val ColorPalette.displayName: String
    get() = when (this) {
        ColorPalette.ARBOR -> "Arbor"
        ColorPalette.SYSTEM -> "Dynamic"
        ColorPalette.GRAPHITE -> "Graphite"
    }

private val ColorPalette.description: String
    get() = when (this) {
        ColorPalette.ARBOR -> "Arbor's green Material palette"
        ColorPalette.SYSTEM -> "Colors generated from your wallpaper on Android 12+"
        ColorPalette.GRAPHITE -> "Neutral blue-gray palette"
    }

@Composable
private fun ChatOptionsEditor(
    providerId: String,
    modelId: String,
    providers: List<ProviderEntity>,
    thinkingEnabled: Boolean,
    thinkingEffort: ThinkingEffort,
    webEnabled: Boolean,
    deepResearchEnabled: Boolean,
    hybridTokenCountingEnabled: Boolean,
    contextPairs: Int,
    contextTokenLimit: Int,
    workingTokenLimit: Int,
    maxOutputTokens: Int,
    reasoningVisibility: ReasoningVisibility,
    viewModel: ChatViewModel,
    onModel: (String, String) -> Unit,
    onThinkingEnabled: (Boolean) -> Unit,
    onThinkingEffort: (ThinkingEffort) -> Unit,
    onWeb: (Boolean) -> Unit,
    onDeepResearch: (Boolean) -> Unit,
    onHybridTokenCounting: (Boolean) -> Unit,
    onContextPairs: (Int) -> Unit,
    onContextLimit: (Int) -> Unit,
    onWorkingLimit: (Int) -> Unit,
    onOutputLimit: (Int) -> Unit,
    onReasoningVisibility: (ReasoningVisibility) -> Unit,
) {
    val modelFlow = remember(providerId) { viewModel.modelsFor(providerId) }
    val models by modelFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeModel = models.firstOrNull { it.modelId == modelId }
    ProviderModelSelector(providers, providerId, modelId, models, viewModel, onModel)

    HorizontalDivider()
    SectionTitle("Composer defaults", "Starting state for the controls beside the message box.")
    ThinkingDefaultsControl(
        enabled = thinkingEnabled,
        effort = thinkingEffort,
        provider = providers.firstOrNull { it.id == providerId },
        model = activeModel,
        onEnabled = onThinkingEnabled,
        onEffort = onThinkingEffort,
    )

    Text("Tools and modes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SettingsSwitch("Web search", webEnabled, onWeb)
    SettingsSwitch("Deep Research", deepResearchEnabled, onDeepResearch, enabled = webEnabled || !deepResearchEnabled)
    Text("Deep Research plans, searches iteratively, verifies sources, and produces a cited report. Enabling it also enables web search.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    HorizontalDivider()
    SectionTitle("Token counting", "Optional hybrid preflight counting. Provider count endpoints are preferred; local model-family estimates and the generic estimator are fallbacks.")
    SettingsSwitch("Hybrid token counting", hybridTokenCountingEnabled, onHybridTokenCounting)

    HorizontalDivider()
    SectionTitle("Context & output", "A pair is one request plus its answer. Working history has its own budget inside the total context ceiling.")
    NumberSetting("Last message pairs", contextPairs, 1..500, onContextPairs)
    NumberSetting("Context token ceiling", contextTokenLimit, 1_024..2_000_000, onContextLimit)
    NumberSetting("Working history token budget", workingTokenLimit, 0..2_000_000, onWorkingLimit)
    NumberSetting("Maximum output tokens", maxOutputTokens, 1..384_000, onOutputLimit)

    HorizontalDivider()
    SectionTitle("Working display", "Controls whether reasoning and tool cards expand automatically; they remain saved either way.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReasoningVisibility.entries.forEach { option ->
            AssistChip(
                onClick = { onReasoningVisibility(option) },
                label = { Text(option.shortLabel) },
                leadingIcon = if (reasoningVisibility == option) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }

    HorizontalDivider()
    SectionTitle("Arbor core prompt", "Built into this app version and updated with Arbor. It is intentionally not editable or copied into chats.")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Managed by Arbor", fontWeight = FontWeight.SemiBold)
                Text("Use Custom instruction profiles for tone and workflow preferences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThinkingDefaultsControl(
    enabled: Boolean,
    effort: ThinkingEffort,
    provider: ProviderEntity?,
    model: ModelEntity?,
    onEnabled: (Boolean) -> Unit,
    onEffort: (ThinkingEffort) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val options = remember(provider?.id, provider?.kind, model?.modelId, model?.supportsThinking) {
        supportedThinkingLevels(provider, model)
    }
    val supported = options.isNotEmpty()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Thinking", fontWeight = FontWeight.SemiBold)
                Text(
                    if (!supported) "Not supported by this model" else if (enabled) "${effort.displayName} effort" else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled && supported,
                onCheckedChange = onEnabled,
                enabled = supported && options.any { !it.enabled },
            )
            Box {
                IconButton(onClick = { menu = true }, enabled = supported) { Icon(Icons.Outlined.ExpandMore, "Thinking effort") }
                ArborDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    options.filter { it.enabled }.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            leadingIcon = if (enabled && effort == option.effort) ({ Icon(Icons.Outlined.CheckCircle, null) }) else null,
                            onClick = {
                                option.effort?.let(onEffort)
                                if (!enabled) onEnabled(true)
                                menu = false
                            },
                        )
                    }
                }
            }
        }
    }
    Text("Available levels follow the selected model. Some models cannot fully disable reasoning.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProviderModelSelector(
    providers: List<ProviderEntity>,
    providerId: String,
    modelId: String,
    models: List<ModelEntity>,
    viewModel: ChatViewModel,
    onSelect: (String, String) -> Unit,
) {
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val provider = providers.firstOrNull { it.id == providerId }
    SectionTitle("Model", "Provider and model selection for this settings profile.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { providerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(provider?.displayName ?: "Choose provider", maxLines = 1)
            }
            ArborDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                providers.forEach { candidate ->
                    DropdownMenuItem(
                        text = { Text(candidate.displayName) },
                        onClick = {
                            providerMenu = false
                            scope.launch {
                                val first = viewModel.modelsFor(candidate.id).first().firstOrNull()
                                if (first != null) onSelect(candidate.id, first.modelId)
                            }
                        },
                    )
                }
            }
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { modelMenu = true }, enabled = models.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(models.firstOrNull { it.modelId == modelId }?.displayName ?: modelId.ifBlank { "Choose model" }, maxLines = 1)
            }
            ArborDropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = { onSelect(providerId, model.modelId); modelMenu = false },
                    )
                }
            }
        }
    }
    if (providers.isEmpty()) Text("Add a usable provider in the Providers tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val haptics = rememberArborHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { next ->
                haptics.toggle(next)
                onCheckedChange(next)
            },
            enabled = enabled,
        )
    }
}

@Composable
private fun NumberSetting(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw -> raw.toIntOrNull()?.coerceIn(range)?.let(onValue) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val ThinkingEffort.displayName: String
    get() = when (this) {
        ThinkingEffort.MINIMAL -> "Minimal"
        ThinkingEffort.LOW -> "Low"
        ThinkingEffort.MEDIUM -> "Medium"
        ThinkingEffort.HIGH -> "High"
        ThinkingEffort.XHIGH -> "Extra high"
        ThinkingEffort.MAX -> "Max"
    }

private val ReasoningVisibility.shortLabel: String
    get() = when (this) {
        ReasoningVisibility.ALWAYS -> "Expanded"
        ReasoningVisibility.SHOW_WHILE_WORKING -> "While working"
        ReasoningVisibility.COLLAPSED -> "Collapsed"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSettings(
    providers: List<ProviderEntity>,
    registeredProviders: List<ProviderEntity>,
    conversationProviderId: String?,
    openAiOAuthStates: Map<String, OpenAiOAuthState>,
    openAiOAuthUsageStates: Map<String, OpenAiOAuthUsageState>,
    viewModel: ChatViewModel,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var addingProvider by remember { mutableStateOf(false) }
    var addingChatGpt by remember { mutableStateOf(false) }
    var renamingOAuth by remember { mutableStateOf<ProviderEntity?>(null) }
    var removingProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var editingConnection by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("{}") }
    var providerName by remember { mutableStateOf("") }
    var apiKeyRequired by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var syncingModels by remember { mutableStateOf(false) }
    var modelSyncStatus by remember { mutableStateOf<String?>(null) }
    val selected = registeredProviders.firstOrNull { it.id == selectedId } ?: registeredProviders.firstOrNull()

    LaunchedEffect(selected?.id) {
        selected?.let {
            selectedId = it.id
            baseUrl = it.baseUrl
            apiKey = viewModel.apiKey(it.id)
            headers = it.customHeadersJson
            providerName = it.displayName
            apiKeyRequired = it.apiKeyRequired
        }
    }
    val selectedOAuthState = selected?.takeIf { it.kind == ProviderKind.OPENAI_OAUTH }
        ?.let { openAiOAuthStates[it.id] } ?: OpenAiOAuthState.SignedOut
    val selectedOAuthUsageState = selected?.takeIf { it.kind == ProviderKind.OPENAI_OAUTH }
        ?.let { openAiOAuthUsageStates[it.id] } ?: OpenAiOAuthUsageState.SignedOut
    LaunchedEffect(selected?.id, selectedOAuthState) {
        val provider = selected?.takeIf { it.kind == ProviderKind.OPENAI_OAUTH } ?: return@LaunchedEffect
        if (selectedOAuthState is OpenAiOAuthState.SignedIn) viewModel.ensureChatGptUsage(provider.id)
    }

    SettingsPage {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Providers", "Choose a provider, then manage its connection and models.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addingChatGpt = true }) {
                    Icon(Icons.Outlined.AccountCircle, null)
                    Text(" ChatGPT")
                }
                FilledTonalButton(onClick = { addingProvider = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Text(" API", Modifier.padding(start = 2.dp))
                }
            }
        }

        if (registeredProviders.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text("No providers yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Add a ChatGPT account or configure an API-compatible provider.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { addingChatGpt = true }) { Text("Add ChatGPT") }
                        OutlinedButton(onClick = { addingProvider = true }) { Text("Add API") }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                registeredProviders.forEach { provider ->
                    Surface(
                        onClick = { selectedId = provider.id },
                        color = if (provider.id == selected?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (provider.id == selected?.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (provider.id == selected?.id) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                shape = androidx.compose.foundation.shape.CircleShape,
                                modifier = Modifier.size(38.dp),
                            ) { Box(contentAlignment = Alignment.Center) { Text(provider.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold) } }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (provider.kind == ProviderKind.OPENAI_OAUTH) {
                                        when (openAiOAuthStates[provider.id]) {
                                            is OpenAiOAuthState.SignedIn -> "ChatGPT OAuth • Connected"
                                            OpenAiOAuthState.SigningIn -> "ChatGPT OAuth • Signing in"
                                            is OpenAiOAuthState.Error -> "ChatGPT OAuth • Needs attention"
                                            else -> "ChatGPT OAuth • Disconnected"
                                        }
                                    } else providerKindLabel(provider.kind),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (provider.id == conversationProviderId) Text("In use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (provider.id == selected?.id) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        selected?.takeIf { it.kind != ProviderKind.OPENAI_OAUTH }?.let { provider ->
            HorizontalDivider()
            SectionTitle("${provider.displayName} connection", "Connection details stay out of the way until you need them.")
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.baseUrl, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (!provider.apiKeyRequired) "Keyless endpoint" else if (apiKey.isNotBlank()) "API key saved securely" else "API key missing",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (provider.apiKeyRequired && apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { editingConnection = true }) { Text("Edit") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    syncingModels = true
                                    modelSyncStatus = null
                                    runCatching { viewModel.discoverModels(provider.kind, baseUrl, apiKey, headers) }
                                        .onSuccess { discovered ->
                                            viewModel.saveDiscoveredModels(provider.id, discovered)
                                            modelSyncStatus = "Updated ${discovered.size} models"
                                        }
                                        .onFailure { modelSyncStatus = it.message?.take(1_000) ?: "Model refresh failed" }
                                    syncingModels = false
                                }
                            },
                            enabled = !syncingModels && baseUrl.isNotBlank() && (!apiKeyRequired || apiKey.isNotBlank()),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (syncingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, null)
                            Text(if (syncingModels) " Refreshing…" else " Refresh models")
                        }
                        OutlinedButton(onClick = { removingProvider = provider }) {
                            Icon(Icons.Outlined.DeleteOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    modelSyncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            ModelCatalogEditor(provider, viewModel)
        }
        selected?.takeIf { it.kind == ProviderKind.OPENAI_OAUTH }?.let { provider ->
            HorizontalDivider()
            ChatGptOAuthCard(
                providerName = provider.displayName,
                state = selectedOAuthState,
                usageState = selectedOAuthUsageState,
                onSignIn = { viewModel.signInWithChatGpt(provider.id) },
                onSignOut = { viewModel.signOutFromChatGpt(provider.id) },
                onRefreshModels = { viewModel.refreshChatGptModels(provider.id) },
                onRefreshUsage = { viewModel.refreshChatGptUsage(provider.id) },
                onCancel = { viewModel.cancelChatGptSignIn(provider.id) },
                onRename = { renamingOAuth = provider },
                onRemove = { removingProvider = provider },
            )
            if (selectedOAuthState is OpenAiOAuthState.SignedIn) {
                SectionTitle("${provider.displayName} models", "Models discovered for this ChatGPT account only.")
                ModelCatalogEditor(provider, viewModel)
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
    }

    if (editingConnection) {
        ModalBottomSheet(onDismissRequest = { editingConnection = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                Text("Edit connection", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(selected?.displayName.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(16.dp))
                selected?.let { provider ->
                    ProviderEditor(
                        provider = provider,
                        name = providerName,
                        onName = { providerName = it },
                        baseUrl = baseUrl,
                        onBaseUrl = { baseUrl = it },
                        key = apiKey,
                        onKey = { apiKey = it },
                        headers = headers,
                        onHeaders = { headers = it },
                        apiKeyRequired = apiKeyRequired,
                        onApiKeyRequired = { apiKeyRequired = it },
                    ) {
                        viewModel.saveProvider(provider.copy(displayName = providerName.trim(), baseUrl = baseUrl.trimEnd('/'), customHeadersJson = headers, apiKeyRequired = apiKeyRequired), apiKey)
                        editingConnection = false
                    }
                }
                Spacer(Modifier.size(28.dp))
            }
        }
    }

    removingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { removingProvider = null },
            title = { Text("Remove ${provider.displayName}?") },
            text = { Text(if (provider.kind == ProviderKind.OPENAI_OAUTH) "Its encrypted OAuth session and models will be disconnected. Chats and usage history are kept." else "Its saved API key will be erased and it will disappear from model selectors. Chats and usage history are kept.") },
            dismissButton = { OutlinedButton(onClick = { removingProvider = null }) { Text("Cancel") } },
            confirmButton = { Button(onClick = { viewModel.removeProvider(provider); removingProvider = null }) { Text("Remove provider") } },
        )
    }

    if (addingChatGpt) AddChatGptProviderDialog(
        existingCount = registeredProviders.count { it.kind == ProviderKind.OPENAI_OAUTH },
        onDismiss = { addingChatGpt = false },
        onAdd = { name ->
            val provider = ProviderEntity(
                id = "openai-oauth-${UUID.randomUUID()}",
                displayName = name,
                kind = ProviderKind.OPENAI_OAUTH,
                baseUrl = defaultBaseUrl(ProviderKind.OPENAI_OAUTH),
                apiKeyRequired = false,
                registered = true,
            )
            viewModel.addChatGptProvider(provider)
            selectedId = provider.id
            addingChatGpt = false
        },
    )

    renamingOAuth?.let { provider ->
        RenameChatGptProviderDialog(
            provider = provider,
            onDismiss = { renamingOAuth = null },
            onRename = { name ->
                viewModel.saveProvider(provider.copy(displayName = name), viewModel.apiKey(provider.id))
                renamingOAuth = null
            },
        )
    }

    if (addingProvider) AddProviderDialog(
        templates = providers.filter { provider -> provider.kind != ProviderKind.OPENAI_OAUTH && provider !in registeredProviders },
        onDismiss = { addingProvider = false },
        onDiscover = viewModel::discoverModels,
        onAdd = { draft ->
            val id = draft.templateProviderId ?: "provider-${UUID.randomUUID()}"
            val template = providers.firstOrNull { it.id == draft.templateProviderId }
            val provider = (template ?: ProviderEntity(
                id = id, displayName = draft.name, kind = draft.kind, baseUrl = draft.baseUrl,
            )).copy(
                displayName = draft.name,
                kind = draft.kind,
                baseUrl = draft.baseUrl.trimEnd('/'),
                customHeadersJson = draft.headers,
                registered = true,
                apiKeyRequired = draft.apiKeyRequired,
            )
            val discovered = draft.discoveredModels.ifEmpty { listOf(DiscoveredModel(draft.modelId, draft.modelName)) }
            val models = discovered.map { candidate ->
                val model = DefaultCatalog.models.firstOrNull { it.providerId == id && it.modelId == candidate.id } ?: ModelEntity(
                    providerId = id, modelId = candidate.id, displayName = candidate.displayName,
                    contextWindow = candidate.contextWindow ?: 128_000,
                    maxOutputTokens = candidate.maxOutputTokens ?: 16_384,
                    inputCacheHitUsdPerMillion = 0.0, inputCacheMissUsdPerMillion = 0.0, outputUsdPerMillion = 0.0,
                    supportsThinking = candidate.supportsThinking ?: false,
                    supportsVision = candidate.supportsVision ?: false,
                    supportsFiles = candidate.supportsFiles ?: false,
                    supportsTools = candidate.supportsTools ?: false,
                    supportsImageGeneration = candidate.supportsImageGeneration ?: false,
                )
                ModelRequestPolicy.normalize(provider, model)
            }
            viewModel.addProvider(provider, draft.apiKey, models)
            selectedId = id
            addingProvider = false
        },
    )
}

@Composable
private fun AddChatGptProviderDialog(
    existingCount: Int,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by remember { mutableStateOf(if (existingCount == 0) "ChatGPT account" else "ChatGPT account ${existingCount + 1}") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ChatGPT provider") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Each provider keeps its OAuth session, models, usage limits, and refresh state separate. Arbor requests a fresh sign-in page so you can add a different ChatGPT account.")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider name") },
                    placeholder = { Text("Work ChatGPT") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank()) { Text("Add") }
        },
    )
}

@Composable
private fun RenameChatGptProviderDialog(
    provider: ProviderEntity,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember(provider.id) { mutableStateOf(provider.displayName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ChatGPT provider") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Provider name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
    )
}

@Composable
private fun ChatGptOAuthCard(
    providerName: String,
    state: OpenAiOAuthState,
    usageState: OpenAiOAuthUsageState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRefreshModels: () -> Unit,
    onRefreshUsage: () -> Unit,
    onCancel: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val signedIn = state is OpenAiOAuthState.SignedIn
    Surface(
        color = if (signedIn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (signedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (signedIn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(42.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.AccountCircle, null) } }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(providerName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (state) {
                            OpenAiOAuthState.SignedOut -> "Use your ChatGPT plan without an API key"
                            OpenAiOAuthState.SigningIn -> "Complete sign-in in your browser…"
                            is OpenAiOAuthState.SignedIn -> state.email?.let { "Connected • $it" } ?: "Connected"
                            is OpenAiOAuthState.Error -> state.message
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state is OpenAiOAuthState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename, enabled = state !is OpenAiOAuthState.SigningIn) {
                    Icon(Icons.Outlined.Edit, "Rename provider")
                }
                if (state is OpenAiOAuthState.SigningIn) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            }
            Text(
                "One-tap native OAuth. Arbor opens the system browser, receives the localhost callback itself, encrypts the session on this device, and refreshes it automatically. No extension or local proxy is required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (signedIn) {
                ChatGptUsagePanel(usageState, onRefreshUsage)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshModels, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(" Refresh models")
                    }
                    OutlinedButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null)
                        Text(" Disconnect")
                    }
                }
            } else {
                Button(
                    onClick = onSignIn,
                    enabled = state !is OpenAiOAuthState.SigningIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Login, null)
                    Text(if (state is OpenAiOAuthState.Error) " Sign in again" else " Sign in with ChatGPT")
                }
            }
            TextButton(onClick = onRemove, enabled = state !is OpenAiOAuthState.SigningIn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(" Remove provider", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ChatGptUsagePanel(
    state: OpenAiOAuthUsageState,
    onRefresh: () -> Unit,
) {
    val snapshot = when (state) {
        is OpenAiOAuthUsageState.Loaded -> state.snapshot
        is OpenAiOAuthUsageState.Loading -> state.previous
        is OpenAiOAuthUsageState.Error -> state.previous
        OpenAiOAuthUsageState.SignedOut -> null
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = .72f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Usage & limits", fontWeight = FontWeight.SemiBold)
                    Text(
                        snapshot?.planType?.let { "${humanizeUsageName(it)} plan • reported by ChatGPT" }
                            ?: "Current account quota windows",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state is OpenAiOAuthUsageState.Loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = onRefresh, enabled = state !is OpenAiOAuthUsageState.Loading) {
                    Icon(Icons.Outlined.Refresh, "Refresh usage")
                }
            }

            if (snapshot == null) {
                when (state) {
                    is OpenAiOAuthUsageState.Error -> Text(
                        state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            } else {
                snapshot.primary?.let {
                    UsageWindowRow(usageWindowName(it, "Session"), it)
                }
                snapshot.secondary?.let {
                    UsageWindowRow(usageWindowName(it, "Weekly"), it)
                }
                snapshot.additionalLimits.forEach { limit ->
                    limit.primary?.let { UsageWindowRow(limit.name, it) }
                    limit.secondary?.let { UsageWindowRow("${limit.name} • secondary", it) }
                }
                val creditText = when {
                    snapshot.creditsUnlimited == true -> "Credits: unlimited"
                    snapshot.creditsBalance != null -> "Credits balance: ${snapshot.creditsBalance}"
                    snapshot.hasCredits == true -> "Additional credits available"
                    else -> null
                }
                creditText?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (snapshot.limitReached == true || snapshot.allowed == false) {
                    Text(
                        snapshot.rateLimitReachedType?.let { "Limit reached: ${humanizeUsageName(it)}" }
                            ?: "A ChatGPT usage limit has been reached.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state is OpenAiOAuthUsageState.Error) {
                    Text(
                        "Refresh failed • ${state.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageWindowRow(
    label: String,
    window: OpenAiOAuthUsageWindow,
) {
    val used = window.usedPercent.coerceIn(0.0, 100.0)
    val left = (100.0 - used).coerceIn(0.0, 100.0)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                "${left.roundToInt()}% left",
                style = MaterialTheme.typography.bodySmall,
                color = if (left <= 10.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { (used / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth(),
        )
        val resetAt = window.resetsAtEpochSeconds
        var nowEpochSeconds by remember(resetAt) { mutableLongStateOf(System.currentTimeMillis() / 1_000L) }
        LaunchedEffect(resetAt) {
            if (resetAt != null) {
                while (true) {
                    delay(1_000L)
                    nowEpochSeconds = System.currentTimeMillis() / 1_000L
                }
            }
        }
        val reset = resetAt?.let { usageResetText(it, nowEpochSeconds) }
        Text(
            buildString {
                append("${used.roundToInt()}% used")
                if (reset != null) append(" • $reset")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun usageWindowName(window: OpenAiOAuthUsageWindow, fallback: String): String {
    val seconds = window.windowDurationSeconds ?: return fallback
    return when (seconds) {
        in 17_700L..18_300L -> "5-hour limit"
        in 604_000L..605_600L -> "Weekly limit"
        else -> when {
            seconds % 86_400L == 0L -> "${seconds / 86_400L}-day limit"
            seconds % 3_600L == 0L -> "${seconds / 3_600L}-hour limit"
            else -> fallback
        }
    }
}

internal fun usageResetCountdown(epochSeconds: Long, nowEpochSeconds: Long): String {
    val remaining = epochSeconds - nowEpochSeconds
    if (remaining <= 0L) return "now"
    val days = remaining / 86_400L
    val hours = (remaining % 86_400L) / 3_600L
    val minutes = (remaining % 3_600L) / 60L
    val seconds = remaining % 60L
    return when {
        days > 0L -> if (hours > 0L) "in ${days}d ${hours}h" else "in ${days}d"
        hours > 0L -> if (minutes > 0L) "in ${hours}h ${minutes}m" else "in ${hours}h"
        minutes > 0L -> if (seconds > 0L) "in ${minutes}m ${seconds}s" else "in ${minutes}m"
        else -> "in ${seconds}s"
    }
}

private fun usageResetText(epochSeconds: Long, nowEpochSeconds: Long): String {
    val exact = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochSeconds * 1_000L))
    return "resets ${usageResetCountdown(epochSeconds, nowEpochSeconds)} • $exact"
}

private fun humanizeUsageName(value: String): String = value
    .replace('-', ' ')
    .replace('_', ' ')
    .split(' ')
    .filter(String::isNotBlank)
    .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

@Composable
private fun ProviderEditor(
    provider: ProviderEntity,
    name: String, onName: (String) -> Unit,
    baseUrl: String, onBaseUrl: (String) -> Unit,
    key: String, onKey: (String) -> Unit,
    headers: String, onHeaders: (String) -> Unit,
    apiKeyRequired: Boolean, onApiKeyRequired: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    var advanced by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(providerKindLabel(provider.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(name, onName, label = { Text("Provider name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(baseUrl, onBaseUrl, label = { Text("API base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            key, onKey,
            label = { Text(if (apiKeyRequired) "API key" else "API key (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Require API key", fontWeight = FontWeight.Medium)
                Text("Disable only for a trusted local or keyless endpoint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = apiKeyRequired, onCheckedChange = onApiKeyRequired)
        }
        Surface(
            onClick = { advanced = !advanced },
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Advanced headers", fontWeight = FontWeight.Medium)
                    Text("Usually unnecessary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ExpandMore, null)
            }
        }
        if (advanced) OutlinedTextField(
            headers,
            onHeaders,
            label = { Text("Custom headers JSON") },
            minLines = 3,
            visualTransformation = rememberCodeVisualTransformation("json"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSave,
            enabled = name.isNotBlank() && baseUrl.isNotBlank() && (!apiKeyRequired || key.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save connection") }
    }
}

private data class ProviderDraft(
    val templateProviderId: String?,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String,
    val apiKeyRequired: Boolean,
    val headers: String,
    val modelId: String,
    val modelName: String,
    val discoveredModels: List<DiscoveredModel>,
)

@Composable
private fun AddProviderDialog(
    templates: List<ProviderEntity>,
    onDismiss: () -> Unit,
    onDiscover: suspend (ProviderKind, String, String, String) -> List<DiscoveredModel>,
    onAdd: (ProviderDraft) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var templateId by remember { mutableStateOf<String?>(null) }
    var templateMenu by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ProviderKind.OPENAI_COMPATIBLE) }
    var typeMenu by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(defaultBaseUrl(kind)) }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyRequired by remember { mutableStateOf(true) }
    var headers by remember { mutableStateOf("{}") }
    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var discoveredModels by remember { mutableStateOf<List<DiscoveredModel>>(emptyList()) }
    var discovering by remember { mutableStateOf(false) }
    var discoveryAttempted by remember { mutableStateOf(false) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var modelSearch by remember { mutableStateOf("") }
    var showManualModel by remember { mutableStateOf(false) }
    val connectionReady = baseUrl.isNotBlank() && (!apiKeyRequired || apiKey.isNotBlank())
    val valid = name.isNotBlank() && connectionReady && modelId.isNotBlank() && modelName.isNotBlank()
    val visibleModels = remember(discoveredModels, modelSearch) {
        val query = modelSearch.trim()
        if (query.isBlank()) discoveredModels else discoveredModels.filter {
            it.id.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true)
        }
    }

    fun invalidateDiscovery() {
        discoveredModels = emptyList()
        discoveryAttempted = false
        discoveryError = null
        modelId = ""
        modelName = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add provider") },
        text = {
            Column(
                Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { templateMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(templates.firstOrNull { it.id == templateId }?.let { "Preset: ${it.displayName}" } ?: "Preset: Custom", Modifier.weight(1f))
                    }
                    ArborDropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                        DropdownMenuItem(text = { Text("Custom provider") }, onClick = {
                            templateId = null
                            name = ""
                            kind = ProviderKind.OPENAI_COMPATIBLE
                            baseUrl = defaultBaseUrl(kind)
                            apiKeyRequired = true
                            invalidateDiscovery()
                            templateMenu = false
                        })
                        templates.forEach { template ->
                            DropdownMenuItem(text = { Text(template.displayName) }, onClick = {
                                templateId = template.id
                                name = template.displayName
                                kind = template.kind
                                baseUrl = template.baseUrl
                                apiKeyRequired = template.apiKeyRequired
                                invalidateDiscovery()
                                templateMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Provider name") }, placeholder = { Text("My DeepSeek account") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Protocol: ${providerKindLabel(kind)}", Modifier.weight(1f))
                    }
                    ArborDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ProviderKind.entries.filter { it != ProviderKind.OPENAI_OAUTH }.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(providerKindLabel(option)) },
                                onClick = {
                                    kind = option
                                    baseUrl = defaultBaseUrl(option)
                                    templateId = null
                                    invalidateDiscovery()
                                    typeMenu = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(baseUrl, { baseUrl = it; invalidateDiscovery() }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiKey,
                    { apiKey = it; invalidateDiscovery() },
                    label = { Text(if (apiKeyRequired) "API key" else "API key (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Require API key")
                        Text("Disable only for your own local/keyless endpoint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = apiKeyRequired, onCheckedChange = { apiKeyRequired = it; invalidateDiscovery() })
                }
                HorizontalDivider()
                OutlinedTextField(
                    headers,
                    { headers = it; invalidateDiscovery() },
                    label = { Text("Custom headers JSON") },
                    minLines = 2,
                    visualTransformation = rememberCodeVisualTransformation("json"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = connectionReady && !discovering,
                    onClick = {
                        discovering = true
                        discoveryAttempted = true
                        discoveryError = null
                        scope.launch {
                            runCatching { onDiscover(kind, baseUrl, apiKey, headers.ifBlank { "{}" }) }
                                .onSuccess { models ->
                                    discoveredModels = models
                                    val preferred = models.firstOrNull { candidate ->
                                        DefaultCatalog.models.any { it.providerId == templateId && it.modelId == candidate.id }
                                    } ?: models.firstOrNull()
                                    modelId = preferred?.id.orEmpty()
                                    modelName = preferred?.displayName.orEmpty()
                                    showManualModel = false
                                }
                                .onFailure { error ->
                                    discoveryError = error.message?.take(1_000) ?: "Could not fetch the model list"
                                }
                            discovering = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (discovering) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp)
                    else Icon(if (discoveryAttempted) Icons.Outlined.Refresh else Icons.Outlined.Search, null)
                    Text(if (discovering) " Connecting…" else if (discoveryAttempted) " Fetch models again" else " Connect & fetch models")
                }
                discoveryError?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(message, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (discoveredModels.isNotEmpty()) {
                    Text("Models from provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        modelSearch,
                        { modelSearch = it },
                        label = { Text("Search ${discoveredModels.size} models") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        visibleModels.forEach { model ->
                            Row(
                                Modifier.fillMaxWidth().clickable { modelId = model.id; modelName = model.displayName }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = modelId == model.id, onClick = { modelId = model.id; modelName = model.displayName })
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, fontWeight = FontWeight.Medium)
                                    Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { showManualModel = !showManualModel }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ExpandMore, null)
                    Text(if (showManualModel) "Hide manual model entry" else "Provider has no model list? Enter manually")
                }
                if (showManualModel) {
                    val bundled = DefaultCatalog.models.filter { it.providerId == templateId }
                    if (bundled.isNotEmpty()) {
                        Text("Bundled suggestions", style = MaterialTheme.typography.labelLarge)
                        bundled.forEach { model ->
                            AssistChip(onClick = { modelId = model.modelId; modelName = model.displayName }, label = { Text(model.displayName) })
                        }
                    }
                    OutlinedTextField(modelId, { modelId = it.trim() }, label = { Text("API model ID") }, placeholder = { Text("deepseek-chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(modelName, { modelName = it }, label = { Text("Model display name") }, placeholder = { Text("DeepSeek Chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (modelId.isNotBlank()) Text("Selected: $modelName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onAdd(ProviderDraft(templateId, name.trim(), kind, baseUrl.trim(), apiKey, apiKeyRequired, headers.ifBlank { "{}" }, modelId, modelName.trim(), discoveredModels))
                },
            ) { Text("Add provider") }
        },
    )
}

private fun providerKindLabel(kind: ProviderKind): String = when (kind) {
    ProviderKind.OPENAI_COMPATIBLE -> "OpenAI-compatible"
    ProviderKind.OPENAI_OAUTH -> "ChatGPT OAuth"
    ProviderKind.ANTHROPIC -> "Anthropic Messages"
    ProviderKind.GEMINI -> "Google Gemini"
}

private fun defaultBaseUrl(kind: ProviderKind): String = when (kind) {
    ProviderKind.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
    ProviderKind.OPENAI_OAUTH -> "https://chatgpt.com/backend-api/codex"
    ProviderKind.ANTHROPIC -> "https://api.anthropic.com/v1"
    ProviderKind.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    steps: Int = 0,
    supportingText: String = "",
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
            )
        }
        ArborSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = steps,
        )
        if (supportingText.isNotBlank()) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AutomationPolicyEditor(
    title: String,
    subtitle: String,
    mode: AuxiliaryMode,
    providerId: String,
    modelId: String,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
    onChange: (AuxiliaryMode, String, String) -> Unit,
) {
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val effectiveProvider = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
    val modelFlow = remember(effectiveProvider?.id) {
        effectiveProvider?.id?.let(viewModel::modelsFor) ?: flowOf<List<ModelEntity>>(emptyList())
    }
    val models by modelFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val effectiveModel = models.firstOrNull { it.modelId == modelId } ?: models.firstOrNull()

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(
                AuxiliaryMode.OFF to "Off",
                AuxiliaryMode.LOCAL to "Local • no API call",
                AuxiliaryMode.MODEL to "Use selected model",
            ).forEach { (option, label) ->
                val enabled = option != AuxiliaryMode.MODEL || effectiveProvider != null
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        onChange(option, effectiveProvider?.id.orEmpty(), effectiveModel?.modelId.orEmpty())
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = mode == option,
                        enabled = enabled,
                        onClick = { onChange(option, effectiveProvider?.id.orEmpty(), effectiveModel?.modelId.orEmpty()) },
                    )
                    Text(label)
                }
            }
            if (mode == AuxiliaryMode.MODEL) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        AssistChip(onClick = { providerMenu = true }, label = { Text(effectiveProvider?.displayName ?: "Choose provider") })
                        ArborDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        onChange(mode, provider.id, "")
                                        providerMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        AssistChip(
                            onClick = { modelMenu = true },
                            label = { Text(effectiveModel?.displayName ?: "Choose model", maxLines = 1) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ArborDropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.displayName) },
                                    onClick = {
                                        onChange(mode, effectiveProvider?.id.orEmpty(), model.modelId)
                                        modelMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageApprovalEditor(
    settings: AutomationSettingsEntity,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) {
    var providerMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val effectiveProvider = providers.firstOrNull { it.id == settings.approvalProviderId } ?: providers.firstOrNull()
    val modelFlow = remember(effectiveProvider?.id) {
        effectiveProvider?.id?.let(viewModel::modelsFor) ?: flowOf<List<ModelEntity>>(emptyList())
    }
    val models by modelFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val effectiveModel = models.firstOrNull { it.modelId == settings.approvalModelId } ?: models.firstOrNull()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PackageApprovalMode.ALWAYS_ASK to ("Ask every time" to "Show the full plan and wait for you"),
                PackageApprovalMode.TRUSTED_ONLY to ("Trusted list" to "Auto-approve only package names you list"),
                PackageApprovalMode.MODEL_REVIEW to ("Approval model" to "A separately selected model allows or denies the preflight plan"),
                PackageApprovalMode.AUTO_APPROVE to ("Auto-approve" to "Install every valid preflight plan without asking"),
            ).forEach { (mode, text) ->
                val enabled = mode != PackageApprovalMode.MODEL_REVIEW || effectiveProvider != null
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        viewModel.updateAutomationSettings { current -> current.copy(
                            packageApprovalMode = mode,
                            approvalProviderId = effectiveProvider?.id.orEmpty(),
                            approvalModelId = effectiveModel?.modelId.orEmpty(),
                        ) }
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.packageApprovalMode == mode,
                        enabled = enabled,
                        onClick = { viewModel.updateAutomationSettings { current -> current.copy(
                            packageApprovalMode = mode,
                            approvalProviderId = effectiveProvider?.id.orEmpty(),
                            approvalModelId = effectiveModel?.modelId.orEmpty(),
                        ) } },
                    )
                    Column { Text(text.first); Text(text.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (settings.packageApprovalMode == PackageApprovalMode.MODEL_REVIEW) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        AssistChip(onClick = { providerMenu = true }, label = { Text(effectiveProvider?.displayName ?: "Choose provider") })
                        ArborDropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        viewModel.updateAutomationSettings { it.copy(approvalProviderId = provider.id, approvalModelId = "") }
                                        providerMenu = false
                                    },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        AssistChip(onClick = { modelMenu = true }, label = { Text(effectiveModel?.displayName ?: "Choose model", maxLines = 1) }, modifier = Modifier.fillMaxWidth())
                        ArborDropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                            models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.displayName) },
                                    onClick = {
                                        viewModel.updateAutomationSettings { it.copy(approvalProviderId = effectiveProvider?.id.orEmpty(), approvalModelId = model.modelId) }
                                        modelMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (settings.packageApprovalMode == PackageApprovalMode.TRUSTED_ONLY) {
                OutlinedTextField(
                    settings.trustedPythonPackages,
                    { value -> viewModel.updateAutomationSettings { it.copy(trustedPythonPackages = value) } },
                    label = { Text("Trusted pip packages") },
                    supportingText = { Text("Comma, space, or newline separated") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    settings.trustedUbuntuPackages,
                    { value -> viewModel.updateAutomationSettings { it.copy(trustedUbuntuPackages = value) } },
                    label = { Text("Trusted Linux packages (apt/apk)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced package sources")
                    Text("Allow pip direct references and relaxed apt names; command-line options remain blocked", style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = !settings.packageRestrictionsEnabled,
                    onCheckedChange = { enabled -> viewModel.updateAutomationSettings { it.copy(packageRestrictionsEnabled = !enabled) } },
                )
            }
            if (settings.packageApprovalMode == PackageApprovalMode.AUTO_APPROVE || !settings.packageRestrictionsEnabled) {
                Text(
                    "Packages and their installers run with Arbor's app permissions. Ubuntu is for compatibility, not containment; these settings intentionally reduce confirmation barriers.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (settings.packageApprovalMode == PackageApprovalMode.MODEL_REVIEW) Text(
                "Model review is advisory and can be wrong. Arbor records the selected model's allow/deny reason, but this is not malware analysis or a security guarantee.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelCatalogEditor(provider: ProviderEntity, viewModel: ChatViewModel) {
    val modelFlow = remember(provider.id) { viewModel.modelsFor(provider.id) }
    val models by modelFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<ModelEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val visibleModels = remember(models, search) {
        val query = search.trim()
        if (query.isBlank()) models else models.filter {
            it.displayName.contains(query, ignoreCase = true) || it.modelId.contains(query, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Models", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${models.size} available for ${provider.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, null)
                Text("Add", Modifier.padding(start = 6.dp))
            }
        }
        if (models.size > 8) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search models") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column {
                visibleModels.forEachIndexed { index, model ->
                    ListItem(
                        headlineContent = { Text(model.displayName, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Column {
                                Text(model.modelId, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                Text(model.compactSummary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                        modifier = Modifier.clickable { editing = model },
                        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                    if (index != visibleModels.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                if (visibleModels.isEmpty()) Text("No matching models.", Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (creating) ModelEditorSheet(
        title = "Add model",
        provider = provider,
        initial = ModelEntity(provider.id, "", "", 128_000, 16_384, 0.0, 0.0, 0.0),
        allowIdEdit = true,
        onDismiss = { creating = false },
        onSave = { viewModel.saveModel(it); creating = false },
    )
    editing?.let { model ->
        ModelEditorSheet(
            title = "Edit model",
            provider = provider,
            initial = model,
            allowIdEdit = false,
            onDismiss = { editing = null },
            onSave = { viewModel.saveModel(it); editing = null },
        )
    }
}

private val ModelEntity.compactSummary: String
    get() = buildList {
        add("${contextWindow / 1_000}K context")
        add("${maxOutputTokens / 1_000}K output")
        if (supportsThinking) add("Thinking")
        if (supportsVision) add("Vision")
        if (supportsTools) add("Tools")
        if (supportsImageGeneration) add("Image generation")
        if (!pricingConfigured) add("Cost unavailable")
    }.joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditorSheet(
    title: String,
    provider: ProviderEntity,
    initial: ModelEntity,
    allowIdEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (ModelEntity) -> Unit,
) {
    var id by remember(initial) { mutableStateOf(initial.modelId) }
    var name by remember(initial) { mutableStateOf(initial.displayName) }
    var context by remember(initial) { mutableStateOf(initial.contextWindow.toString()) }
    var output by remember(initial) { mutableStateOf(initial.maxOutputTokens.toString()) }
    var cacheHit by remember(initial) { mutableStateOf(initial.inputCacheHitUsdPerMillion.toString()) }
    var cacheMiss by remember(initial) { mutableStateOf(initial.inputCacheMissUsdPerMillion.toString()) }
    var outputPrice by remember(initial) { mutableStateOf(initial.outputUsdPerMillion.toString()) }
    var pricingConfigured by remember(initial) { mutableStateOf(initial.pricingConfigured) }
    var vision by remember(initial) { mutableStateOf(initial.supportsVision) }
    var files by remember(initial) { mutableStateOf(initial.supportsFiles) }
    var thinking by remember(initial) { mutableStateOf(initial.supportsThinking) }
    var tools by remember(initial) { mutableStateOf(initial.supportsTools) }
    var requestType by remember(initial, provider) {
        mutableStateOf(ModelRequestPolicy.requestType(provider, initial))
    }
    val manualRequestType = ModelRequestPolicy.usesManualRequestType(provider)
    val automaticPreset = provider.kind == ProviderKind.OPENAI_COMPATIBLE && !manualRequestType
    var showPricing by remember(initial) { mutableStateOf(initial.pricingConfigured) }
    val pricesValid = !pricingConfigured || listOf(cacheHit, cacheMiss, outputPrice).all { it.toDoubleOrNull()?.let { price -> price >= 0.0 } == true }
    val valid = id.isNotBlank() && name.isNotBlank() && context.toIntOrNull() != null && output.toIntOrNull() != null && pricesValid

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Only the essentials are shown. Pricing is optional.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(id, { id = it.trim() }, label = { Text("API model ID") }, enabled = allowIdEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(context, { context = it.filter(Char::isDigit) }, label = { Text("Context tokens") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(output, { output = it.filter(Char::isDigit) }, label = { Text("Max output") }, modifier = Modifier.weight(1f), singleLine = true)
            }

            if (manualRequestType) {
                Text("Request type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = requestType == ModelRequestType.CHAT,
                        onClick = { requestType = ModelRequestType.CHAT },
                        label = { Text("Chat") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = requestType == ModelRequestType.IMAGE_GENERATION,
                        onClick = { requestType = ModelRequestType.IMAGE_GENERATION },
                        label = { Text("Image generation") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Controls whether this custom endpoint uses chat/completions or images/generations.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (automaticPreset) {
                Text("Model capabilities and request transport are selected automatically by this provider preset.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!automaticPreset) {
                Text("Advanced compatibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = thinking, onClick = { thinking = !thinking }, label = { Text("Thinking") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = tools, onClick = { tools = !tools }, label = { Text("Tools") }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vision, onClick = { vision = !vision }, label = { Text("Vision") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = files, onClick = { files = !files }, label = { Text("Files") }, modifier = Modifier.weight(1f))
                }
            }

            Surface(
                onClick = { showPricing = !showPricing },
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pricing", fontWeight = FontWeight.SemiBold)
                        Text(if (pricingConfigured) "Configured in USD per million tokens" else "Optional · cost will show as unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ExpandMore, null)
                }
            }
            if (showPricing) {
                SettingsSwitch("Pricing configured", pricingConfigured, { pricingConfigured = it })
                OutlinedTextField(cacheHit, { cacheHit = it }, label = { Text("Cached input") }, enabled = pricingConfigured, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(cacheMiss, { cacheMiss = it }, label = { Text("Input") }, enabled = pricingConfigured, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(outputPrice, { outputPrice = it }, label = { Text("Output") }, enabled = pricingConfigured, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSave(initial.copy(
                            modelId = id,
                            displayName = name.trim(),
                            contextWindow = context.toIntOrNull()?.coerceAtLeast(1_024) ?: initial.contextWindow,
                            maxOutputTokens = output.toIntOrNull()?.coerceAtLeast(1) ?: initial.maxOutputTokens,
                            inputCacheHitUsdPerMillion = cacheHit.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            inputCacheMissUsdPerMillion = cacheMiss.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            outputUsdPerMillion = outputPrice.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                            pricingConfigured = pricingConfigured,
                            supportsVision = vision,
                            supportsFiles = files,
                            supportsThinking = thinking,
                            supportsTools = tools,
                            supportsImageGeneration = requestType == ModelRequestType.IMAGE_GENERATION,
                        ))
                    },
                ) { Text("Save") }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}
