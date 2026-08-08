package app.xylune.chat.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
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
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ColorScheme
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
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
import app.xylune.chat.BuildConfig
import app.xylune.chat.R
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.ReasoningVisibility
import app.xylune.chat.data.ThinkingEffort
import app.xylune.chat.data.AuxiliaryMode
import app.xylune.chat.data.AutomationSettingsEntity
import app.xylune.chat.data.MemoryEntity
import app.xylune.chat.data.PackageApprovalMode
import app.xylune.chat.data.SystemPromptMode
import app.xylune.chat.data.SystemPromptProfileEntity
import app.xylune.chat.provider.DiscoveredModel
import app.xylune.chat.provider.ModelRequestPolicy
import app.xylune.chat.provider.ModelRequestType
import app.xylune.chat.provider.OpenAiOAuthState
import app.xylune.chat.provider.OpenAiOAuthUsageSnapshot
import app.xylune.chat.provider.OpenAiOAuthUsageState
import app.xylune.chat.provider.OpenAiOAuthUsageWindow
import app.xylune.chat.provider.supportedThinkingLevels
import app.xylune.chat.provider.defaultThinkingEffort
import app.xylune.chat.provider.effectiveThinkingEnabled
import app.xylune.chat.settings.CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT
import app.xylune.chat.settings.CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT
import app.xylune.chat.settings.ColorPalette
import app.xylune.chat.settings.AppLanguage
import app.xylune.chat.settings.DeveloperSettings
import app.xylune.chat.settings.PerformanceOverlayPosition
import app.xylune.chat.settings.NewChatDefaults
import app.xylune.chat.settings.DEFAULT_XYLUNE_SYSTEM_PROMPT
import app.xylune.chat.settings.XYLUNE_CORE_PROMPT_REVISION
import app.xylune.chat.settings.ThemeMode
import app.xylune.chat.settings.chromeEdgeControlPositionForSoftness
import app.xylune.chat.settings.displayedChromeEdgeSoftness
import app.xylune.chat.ui.theme.palettePreviewColors
import app.xylune.chat.update.RepositoryUpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt


private val LocalSettingsScaffoldPadding = compositionLocalOf { PaddingValues() }
private val LocalSettingsRoute = compositionLocalOf { SettingsRoute.HOME }
private val LocalSettingsViewModel = compositionLocalOf<ChatViewModel?> { null }
@OptIn(ExperimentalMaterial3Api::class)
private val LocalSettingsTopAppBarState = compositionLocalOf<TopAppBarState?> { null }
private val LocalSettingsPageRevision = compositionLocalOf { 0L }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val defaults by viewModel.newChatDefaults.collectAsStateWithLifecycle()
    val automation by viewModel.automationSettings.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val promptProfiles by viewModel.systemPromptProfiles.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val openAiOAuthStates by viewModel.openAiOAuthStates.collectAsStateWithLifecycle()
    val openAiOAuthUsageStates by viewModel.openAiOAuthUsageStates.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val matchLauncherIconToPalette by viewModel.matchLauncherIconToPalette.collectAsState()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsState()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsState()
    val renderSafeMode by viewModel.renderSafeMode.collectAsState()
    val lessEmojiEnabled by viewModel.lessEmojiEnabled.collectAsState()
    val automaticUpdateChecks by viewModel.automaticUpdateChecks.collectAsState()
    val generatedRepairMaxAttempts by viewModel.generatedRepairMaxAttempts.collectAsState()
    val developerSettings by viewModel.developerSettings.collectAsState()
    val providerSetupRequested by viewModel.providerSetupRequested.collectAsState()
    val setupTemporarilyAway by viewModel.setupTemporarilyAway.collectAsState()
    val setupDismissed by viewModel.setupDismissed.collectAsState()
    val setupStepIndex by viewModel.setupStepIndex.collectAsState()
    val registeredProviders = remember(providers, credentialRevision) { viewModel.registeredProviders(providers) }
    val configuredProviders = remember(providers, credentialRevision) { viewModel.configuredProviders(providers) }
    val route by viewModel.settingsRoute.collectAsState()
    val pageRevisions by viewModel.settingsPageRevisions.collectAsState()
    val haptics = rememberXyluneHaptics()

    LaunchedEffect(providerSetupRequested) {
        if (providerSetupRequested) {
            viewModel.openSettingsRoute(SettingsRoute.PROVIDERS)
            viewModel.consumeProviderSetupRequest()
        }
    }

    PredictiveNavigationHost(
        targetState = route,
        backTarget = if (setupTemporarilyAway && route == SettingsRoute.PROVIDERS) {
            // The app-level host owns this Back so it can slide the complete
            // Settings page back to the preserved setup page.
            null
        } else when (route) {
            SettingsRoute.HOME -> null
            SettingsRoute.DEVELOPER, SettingsRoute.LICENSES -> SettingsRoute.ABOUT
            else -> SettingsRoute.HOME
        },
        onBack = { target -> viewModel.settingsRoute.value = target },
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
        val blurState = rememberXyluneBackdropBlurState()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0),
            topBar = {
                CollapsingTranslucentTopBar(
                    title = uiText(currentRoute.title),
                    scrollBehavior = scrollBehavior,
                    blurState = blurState,
                    blurStrength = chromeBlurStrength,
                    edgeSoftness = chromeEdgeSoftness,
                    overlayOpacity = chromeOverlayOpacity,
                    blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp,
                    navigationIcon = {
                        IconButton(onClick = {
                            haptics.selection()
                            if (setupTemporarilyAway && currentRoute == SettingsRoute.PROVIDERS) {
                                viewModel.screen.value = Screen.CHAT
                            } else if (currentRoute == SettingsRoute.DEVELOPER || currentRoute == SettingsRoute.LICENSES) {
                                viewModel.settingsRoute.value = SettingsRoute.ABOUT
                            } else if (currentRoute != SettingsRoute.HOME) {
                                viewModel.settingsRoute.value = SettingsRoute.HOME
                            } else if (openDrawer != null) openDrawer()
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
            Box(Modifier.fillMaxSize().xyluneBackdropSource(blurState)) {
                CompositionLocalProvider(
                    LocalSettingsScaffoldPadding provides padding,
                    LocalSettingsRoute provides currentRoute,
                    LocalSettingsViewModel provides viewModel,
                    LocalSettingsTopAppBarState provides scrollBehavior.state,
                    LocalSettingsPageRevision provides (pageRevisions[currentRoute] ?: 0L),
                ) {
                    when (currentRoute) {
                        SettingsRoute.HOME -> SettingsHome(
                            providerCount = registeredProviders.size,
                            setupDeferred = setupDismissed && setupStepIndex < 2,
                            setupStepIndex = setupStepIndex,
                            onFinishSetup = { viewModel.startSetup(setupStepIndex) },
                            appLanguage = appLanguage,
                            onOpen = viewModel::openSettingsRoute,
                        )
                        SettingsRoute.DEFAULTS -> NewChatDefaultsSettings(defaults, configuredProviders, viewModel)
                        SettingsRoute.RESPONSE_STYLE -> ResponseStyleSettingsPage(lessEmojiEnabled, viewModel)
                        SettingsRoute.SEARCH -> SearchSettingsPage()
                        SettingsRoute.AUTOMATION -> AutomationSettingsPage(automation, configuredProviders, viewModel)
                        SettingsRoute.MEMORY -> MemorySettingsPage(automation, memories, viewModel)
                        SettingsRoute.LANGUAGE -> LanguageSettingsPage(appLanguage, viewModel)
                        SettingsRoute.APPEARANCE -> AppearanceSettingsPage(
                            themeMode = themeMode,
                            amoled = amoled,
                            palette = palette,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,
                            chromeBlurStrength = chromeBlurStrength,
                            chromeEdgeSoftness = chromeEdgeSoftness,
                            chromeOverlayOpacity = chromeOverlayOpacity,
                            viewModel = viewModel,
                        )
                        SettingsRoute.PRIVACY -> PrivacySettingsPage(
                            renderSafeMode = renderSafeMode,
                            generatedRepairMaxAttempts = generatedRepairMaxAttempts,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,
                            viewModel = viewModel,
                        )
                        SettingsRoute.BACKUP -> BackupSettingsPage(viewModel)
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
                            viewModel = viewModel,
                            developerEnabled = developerSettings.enabled,
                            matchLauncherIconToPalette = matchLauncherIconToPalette,
                            automaticUpdateChecks = automaticUpdateChecks,
                            onOpenDeveloper = { viewModel.openSettingsRoute(SettingsRoute.DEVELOPER) },
                            onOpenLicenses = { viewModel.openSettingsRoute(SettingsRoute.LICENSES) },
                        )
                        SettingsRoute.LICENSES -> LicenseCatalogSettingsPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHome(
    providerCount: Int,
    setupDeferred: Boolean,
    setupStepIndex: Int,
    onFinishSetup: () -> Unit,
    appLanguage: AppLanguage,
    onOpen: (SettingsRoute) -> Unit,
) = SettingsPage {
    if (setupDeferred) {
        SettingsGroup("Setup") {
            SettingsDestination(
                icon = Icons.Outlined.CheckCircle,
                title = "Finish setup",
                subtitle = "Continue from step ${setupStepIndex + 1} of 3",
                onClick = onFinishSetup,
            )
        }
    }
    SettingsGroup("Setup & connections") {
        SettingsDestination(
            icon = Icons.Outlined.Cloud,
            title = "Providers & models",
            subtitle = if (providerCount == 0) "Add your first API provider" else "$providerCount provider${if (providerCount == 1) "" else "s"} configured",
            onClick = { onOpen(SettingsRoute.PROVIDERS) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Cloud,
            title = "Backup & transfer",
            subtitle = "Cloud backups, local archives, and restore",
            onClick = { onOpen(SettingsRoute.BACKUP) },
        )
    }
    SettingsGroup("Chat behavior") {
        SettingsDestination(
            icon = Icons.Outlined.SmartToy,
            title = "New chat defaults",
            subtitle = "Model, thinking, context, and output limits",
            onClick = { onOpen(SettingsRoute.DEFAULTS) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Tune,
            title = "Response style",
            subtitle = "Emoji use and global answer presentation",
            onClick = { onOpen(SettingsRoute.RESPONSE_STYLE) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Edit,
            title = "Custom instructions",
            subtitle = "Reusable tone and workflow profiles",
            onClick = { onOpen(SettingsRoute.SYSTEM_PROMPTS) },
        )
    }
    SettingsGroup("Intelligence") {
        SettingsDestination(
            icon = Icons.Outlined.Psychology,
            title = "Memory",
            subtitle = "Cross-chat facts and preferences stored locally",
            onClick = { onOpen(SettingsRoute.MEMORY) },
        )
        SettingsDestination(
            icon = Icons.Outlined.AutoAwesome,
            title = "Background tasks",
            subtitle = "Chat naming and context compression models",
            onClick = { onOpen(SettingsRoute.AUTOMATION) },
        )
    }
    SettingsGroup("Tools & safety") {
        SettingsDestination(
            icon = Icons.Outlined.Search,
            title = "Search & web",
            subtitle = "Native routing, search engines, credentials, and page fetching",
            onClick = { onOpen(SettingsRoute.SEARCH) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Code,
            title = "Local execution",
            subtitle = "Python, Linux, packages, and approval policy",
            onClick = { onOpen(SettingsRoute.LOCAL_EXECUTION) },
        )
        SettingsDestination(
            icon = Icons.Outlined.PrivacyTip,
            title = "Privacy & safety",
            subtitle = "Generated UI safety and local-data behavior",
            onClick = { onOpen(SettingsRoute.PRIVACY) },
        )
    }
    SettingsGroup("Personalization") {
        SettingsDestination(
            icon = Icons.Outlined.Palette,
            title = "Appearance",
            subtitle = "Theme, palette, launcher icon, and AMOLED black",
            onClick = { onOpen(SettingsRoute.APPEARANCE) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Language,
            title = "Language",
            subtitle = when (appLanguage) {
                AppLanguage.SYSTEM -> "System default"
                AppLanguage.ENGLISH -> "English"
                AppLanguage.TURKISH -> "Türkçe"
            },
            onClick = { onOpen(SettingsRoute.LANGUAGE) },
        )
    }
    SettingsGroup("About") {
        SettingsDestination(
            icon = Icons.Outlined.Info,
            title = "About Xylune",
            subtitle = "Version, architecture, and privacy model",
            onClick = { onOpen(SettingsRoute.ABOUT) },
        )
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(uiText(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 6.dp))
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
    val haptics = rememberXyluneHaptics()
    ListItem(
        headlineContent = { Text(uiText(title), fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(uiText(subtitle)) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable {
            haptics.selection()
            onClick()
        },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

internal fun settingsTopBarHeightOffset(scrollOffset: Int, heightOffsetLimit: Float): Float =
    if (heightOffsetLimit >= 0f) 0f
    else (-scrollOffset.coerceAtLeast(0).toFloat()).coerceIn(heightOffsetLimit, 0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    val scaffoldPadding = LocalSettingsScaffoldPadding.current
    val route = LocalSettingsRoute.current
    val viewModel = LocalSettingsViewModel.current
    val topAppBarState = LocalSettingsTopAppBarState.current
    val revision = LocalSettingsPageRevision.current
    key(revision) {
        val scrollState = rememberScrollState(initial = viewModel?.settingsScrollOffset(route) ?: 0)
        LaunchedEffect(route, scrollState, viewModel) {
            val target = viewModel ?: return@LaunchedEffect
            snapshotFlow { scrollState.value }
                .distinctUntilChanged()
                .collect { target.saveSettingsScrollOffset(route, it) }
        }
        LaunchedEffect(route, revision, scrollState, topAppBarState) {
            val state = topAppBarState ?: return@LaunchedEffect
            // A restored ScrollState has already consumed its historical offset,
            // so initialize the title once after measurement. Live gesture deltas
            // are then owned only by Material's nested-scroll connection.
            val limit = snapshotFlow { state.heightOffsetLimit }.first { it < 0f }
            state.heightOffset = settingsTopBarHeightOffset(scrollState.value, limit)
            state.contentOffset = -scrollState.value.coerceAtLeast(0).toFloat()
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
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
}


@Composable
private fun LanguageSettingsPage(
    selected: AppLanguage,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle(
        "Language",
        "Choose the language Xylune uses. System default follows Android.",
    )
    SettingsGroup("App language") {
        listOf(
            AppLanguage.SYSTEM to "System default",
            AppLanguage.ENGLISH to "English",
            AppLanguage.TURKISH to "Türkçe",
        ).forEach { (language, label) ->
            ListItem(
                headlineContent = { Text(uiText(label), fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    if (language == AppLanguage.SYSTEM) Text(uiText("Follow Android language"))
                },
                leadingContent = {
                    RadioButton(selected = selected == language, onClick = null)
                },
                modifier = Modifier.clickable {
                    viewModel.setAppLanguage(language)
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        }
    }
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
        onModel = viewModel::selectDefaultModel,
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
private fun ResponseStyleSettingsPage(
    lessEmojiEnabled: Boolean,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle(
        "Response style",
        "Global answer preferences. Changes apply to existing chats and new chats on their next response.",
    )
    SettingsGroup("Assistant responses") {
        ListItem(
            headlineContent = { Text(uiText("Less emoji"), fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                Text(uiText("Avoid decorative emoji and use them only when they add meaning"))
            },
            trailingContent = {
                Switch(
                    checked = lessEmojiEnabled,
                    onCheckedChange = viewModel::setLessEmojiEnabled,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
    Text(
        uiText("Enabled by default. Technical symbols and emoji requested by the user are not blocked."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun AutomationSettingsPage(
    automation: AutomationSettingsEntity,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("Background task models", "Choose how Xylune names chats and compresses older context.")
    if (providers.isEmpty()) {
        Text(uiText("Configure a usable provider to enable model-based automation."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
    Spacer(Modifier.padding(bottom = 24.dp))
}

private enum class MemoryStatusFilter(val label: String) {
    ALL("All"), ENABLED("Enabled"), DISABLED("Disabled")
}

private enum class MemorySortOrder(val label: String) {
    UPDATED("Recently updated"), CREATED("Recently created"), CATEGORY("Category")
}

@Composable
private fun MemorySettingsPage(
    automation: AutomationSettingsEntity,
    memories: List<MemoryEntity>,
    viewModel: ChatViewModel,
) = SettingsPage {
    var draft by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("general") }
    var memorySearch by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    var editCategory by rememberSaveable { mutableStateOf("general") }
    var statusFilter by remember { mutableStateOf(MemoryStatusFilter.ALL) }
    var categoryFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortOrder by remember { mutableStateOf(MemorySortOrder.UPDATED) }
    var categoryMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteIds by remember { mutableStateOf<Set<String>?>(null) }
    var deleteDisabledPending by remember { mutableStateOf(false) }
    val categories = remember(memories) { memories.map(MemoryEntity::category).distinct().sortedBy(String::lowercase) }
    val visibleMemories = remember(memories, memorySearch, statusFilter, categoryFilter, sortOrder) {
        val query = memorySearch.trim()
        memories.asSequence()
            .filter { memory -> query.isBlank() || memory.content.contains(query, true) || memory.category.contains(query, true) }
            .filter { memory ->
                when (statusFilter) {
                    MemoryStatusFilter.ALL -> true
                    MemoryStatusFilter.ENABLED -> memory.enabled
                    MemoryStatusFilter.DISABLED -> !memory.enabled
                }
            }
            .filter { memory -> categoryFilter == null || memory.category == categoryFilter }
            .let { sequence ->
                when (sortOrder) {
                    MemorySortOrder.UPDATED -> sequence.sortedByDescending(MemoryEntity::updatedAt)
                    MemorySortOrder.CREATED -> sequence.sortedByDescending(MemoryEntity::createdAt)
                    MemorySortOrder.CATEGORY -> sequence.sortedWith(
                        compareBy<MemoryEntity> { it.category.lowercase() }.thenBy { it.content.lowercase() },
                    )
                }
            }
            .toList()
    }
    LaunchedEffect(memories) {
        val existing = memories.mapTo(mutableSetOf(), MemoryEntity::id)
        selectedIds = selectedIds.intersect(existing)
        if (categoryFilter != null && categoryFilter !in categories) categoryFilter = null
    }

    SectionTitle(
        "Memory",
        "Xylune stores memories in its encrypted local database and selects only relevant items under a strict context budget. Disabled memories remain stored but are not supplied to models.",
    )
    ListItem(
        headlineContent = { Text(uiText("Use memory")) },
        supportingContent = { Text(uiText("Expose selected enabled memories to chats and allow memory tools")) },
        trailingContent = {
            Switch(
                checked = automation.memoryEnabled,
                onCheckedChange = { enabled -> viewModel.updateAutomationSettings { it.copy(memoryEnabled = enabled) } },
            )
        },
    )
    ListItem(
        headlineContent = { Text(uiText("Automatic memory")) },
        supportingContent = { Text(uiText("Allow models to save stable, non-sensitive details; duplicate items are merged")) },
        trailingContent = {
            Switch(
                checked = automation.memoryAutoSave,
                enabled = automation.memoryEnabled,
                onCheckedChange = { enabled -> viewModel.updateAutomationSettings { it.copy(memoryAutoSave = enabled) } },
            )
        },
    )
    HorizontalDivider()
    SectionTitle("Add memory", "Manual memories are available immediately and use the same deduplication rules.")
    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        label = { Text(uiText("Memory")) },
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = category,
        onValueChange = { category = it },
        label = { Text(uiText("Category")) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        enabled = draft.isNotBlank(),
        onClick = {
            viewModel.addMemory(draft, category)
            draft = ""
        },
    ) { Text(uiText("Save memory")) }

    HorizontalDivider()
    SectionTitle(
        "Saved memories",
        if (memories.isEmpty()) "Nothing is stored yet."
        else "${memories.size} saved item${if (memories.size == 1) "" else "s"}; ${memories.count { it.enabled }} enabled.",
    )
    OutlinedTextField(
        value = memorySearch,
        onValueChange = { memorySearch = it },
        label = { Text(uiText("Search memories")) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MemoryStatusFilter.entries.forEach { option ->
            FilterChip(
                selected = statusFilter == option,
                onClick = { statusFilter = option },
                label = { Text(uiText(option.label)) },
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { categoryMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(categoryFilter ?: "All categories"), maxLines = 1)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            XyluneDropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                DropdownMenuItem(text = { Text(uiText("All categories")) }, onClick = { categoryFilter = null; categoryMenu = false })
                categories.forEach { value ->
                    DropdownMenuItem(text = { Text(value) }, onClick = { categoryFilter = value; categoryMenu = false })
                }
            }
        }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(uiText(sortOrder.label), maxLines = 1)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
            }
            XyluneDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                MemorySortOrder.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(uiText(option.label)) }, onClick = { sortOrder = option; sortMenu = false })
                }
            }
        }
    }

    if (selectedIds.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText("${selectedIds.size} selected"), fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { viewModel.setMemoriesEnabled(selectedIds, true); selectedIds = emptySet() }) { Text(uiText("Enable")) }
                    TextButton(onClick = { viewModel.setMemoriesEnabled(selectedIds, false); selectedIds = emptySet() }) { Text(uiText("Disable")) }
                    TextButton(onClick = { pendingDeleteIds = selectedIds }) { Text(uiText("Delete")) }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text(uiText("Clear")) }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = visibleMemories.isNotEmpty(),
                onClick = { selectedIds = visibleMemories.mapTo(linkedSetOf(), MemoryEntity::id) },
            ) { Text(uiText("Select shown")) }
            TextButton(
                enabled = memories.any { !it.enabled },
                onClick = { deleteDisabledPending = true },
            ) { Text(uiText("Delete disabled")) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(enabled = memories.any { !it.enabled }, onClick = { viewModel.setAllMemoriesEnabled(true) }) { Text(uiText("Enable all")) }
            TextButton(enabled = memories.any { it.enabled }, onClick = { viewModel.setAllMemoriesEnabled(false) }) { Text(uiText("Disable all")) }
        }
    }

    if (visibleMemories.isEmpty()) {
        Text(
            uiText(if (memories.isEmpty()) "No memories saved yet." else "No memories match the current filters."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    visibleMemories.forEach { memory ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (editingId == memory.id) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text(uiText("Memory")) },
                        minLines = 2,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = editCategory,
                        onValueChange = { editCategory = it },
                        label = { Text(uiText("Category")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = editText.isNotBlank(),
                            onClick = {
                                viewModel.updateMemory(memory.id, editText, editCategory)
                                editingId = null
                            },
                        ) { Text(uiText("Save")) }
                        TextButton(onClick = { editingId = null }) { Text(uiText("Cancel")) }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(memory.content, style = MaterialTheme.typography.bodyLarge)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = memory.id in selectedIds,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + memory.id else selectedIds - memory.id
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                memory.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Text(
                                uiText("${if (memory.sourceConversationId == null) "Manual" else "From chat"} · ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(memory.updatedAt))}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        IconButton(onClick = {
                            editingId = memory.id
                            editText = memory.content
                            editCategory = memory.category
                        }) { Icon(Icons.Outlined.Edit, uiText("Edit memory")) }
                        Switch(
                            checked = memory.enabled,
                            onCheckedChange = { viewModel.setMemoryEnabled(memory.id, it) },
                        )
                        IconButton(onClick = { pendingDeleteIds = setOf(memory.id) }) {
                            Icon(Icons.Outlined.DeleteOutline, uiText("Delete memory"))
                        }
                    }
                }
            }
        }
    }

    pendingDeleteIds?.let { ids ->
        XyluneAlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text(uiText(if (ids.size == 1) "Delete memory?" else "Delete ${ids.size} memories?")) },
            text = { Text(uiText("This permanently removes the selected memory data from Xylune.")) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteMemories(ids)
                    selectedIds = selectedIds - ids
                    pendingDeleteIds = null
                }) { Text(uiText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteIds = null }) { Text(uiText("Cancel")) } },
        )
    }
    if (deleteDisabledPending) {
        val count = memories.count { !it.enabled }
        XyluneAlertDialog(
            onDismissRequest = { deleteDisabledPending = false },
            title = { Text(uiText("Delete $count disabled memor${if (count == 1) "y" else "ies"}?")) },
            text = { Text(uiText("Disabled memories are currently excluded from chats. This cleanup permanently removes them.")) },
            confirmButton = {
                Button(onClick = { viewModel.deleteDisabledMemories(); deleteDisabledPending = false }) { Text(uiText("Delete")) }
            },
            dismissButton = { TextButton(onClick = { deleteDisabledPending = false }) { Text(uiText("Cancel")) } },
        )
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun AppearanceSettingsPage(
    themeMode: ThemeMode,
    amoled: Boolean,
    palette: ColorPalette,
    matchLauncherIconToPalette: Boolean,
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
                label = { Text(uiText(option.displayName)) },
                leadingIcon = if (themeMode == option) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }

    HorizontalDivider()
    SectionTitle("Color scheme", "Choose a restrained built-in palette or Android dynamic colors. Every swatch is rendered from that palette, not the currently selected one.")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPalette.entries.forEach { option ->
            val preview = palettePreviewColors(option, themeMode)
            Surface(
                onClick = { viewModel.setPalette(option) },
                color = if (palette == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PaletteSwatch(preview, Modifier.width(58.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (option == ColorPalette.XYLUNE) appName else uiText(option.displayName), fontWeight = FontWeight.SemiBold)
                        Text(uiText(if (option == ColorPalette.XYLUNE) "$appNamePossessive green Material palette" else option.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (palette == option) Icon(Icons.Outlined.CheckCircle, uiText("Selected"), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    Surface(
        color = if (matchLauncherIconToPalette) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (matchLauncherIconToPalette) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth().clickable {
            viewModel.setMatchLauncherIconToPalette(!matchLauncherIconToPalette)
        },
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LauncherIconPreview(if (matchLauncherIconToPalette) palette else ColorPalette.XYLUNE)
            Column(Modifier.weight(1f)) {
                Text(uiText("Match launcher icon to palette"), fontWeight = FontWeight.SemiBold)
                Text(
                    uiText(if (matchLauncherIconToPalette) {
                        "Changing the launcher icon briefly restarts Xylune after saving the open page, chat drafts and files, and current scroll positions. Android themed icons can still override app-selected colors."
                    } else {
                        "Keep the classic Xylune green icon regardless of the selected palette."
                    }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = matchLauncherIconToPalette,
                onCheckedChange = viewModel::setMatchLauncherIconToPalette,
            )
        }
    }
    Text(
        uiText("Android themed icons can recolor Xylune's monochrome layer. Dynamic uses the live wallpaper-derived Material You palette when themed icons are off."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    SettingsSwitch("AMOLED black", amoled, viewModel::setAmoled, enabled = themeMode != ThemeMode.LIGHT)
    Text(uiText("AMOLED black only changes dark mode surfaces."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
    Text(uiText("Panel shape"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = { viewModel.setChromeEdgeSoftness(CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT) },
            label = { Text(uiText("Rounded")) },
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
            label = { Text(uiText("Flat")) },
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
    matchLauncherIconToPalette: Boolean,
    viewModel: ChatViewModel,
) = SettingsPage {
    val uriHandler = LocalUriHandler.current
    val siteColors = MaterialTheme.colorScheme
    val privacyUrl = remember(siteColors, matchLauncherIconToPalette) {
        xyluneWebsiteUrl("privacy/", siteColors, dynamicLogo = matchLauncherIconToPalette)
    }
    val termsUrl = remember(siteColors, matchLauncherIconToPalette) {
        xyluneWebsiteUrl("terms/", siteColors, dynamicLogo = matchLauncherIconToPalette)
    }
    val deletionUrl = remember(siteColors, matchLauncherIconToPalette) {
        xyluneWebsiteUrl("data-deletion/", siteColors, dynamicLogo = matchLauncherIconToPalette)
    }
    SectionTitle("Generated content", "Controls how Xylune handles AI-generated interactive UI.")
    SettingsSwitch("Safe generated rendering", renderSafeMode, viewModel::setRenderSafeMode)
    Text(
        uiText(if (renderSafeMode) "Generated widgets are paused and shown as safe fallback content." else "Generated widgets may render, but Xylune still applies its capability checks and crash recovery."),
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

    HorizontalDivider()
    SectionTitle(
        "Third-party AI and services",
        "Xylune is a client, not an AI model host. Responses come from the provider or local server selected by the user.",
    )
    Text(
        uiText("The Xylune maintainer does not create, train, host, pre-review, or endorse individual model outputs. AI output can be wrong, unsafe, biased, or unsuitable; verify it before relying on it. Provider terms, fees, retention, and content rules apply independently."),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { uriHandler.openUri(privacyUrl) },
            modifier = Modifier.weight(1f),
        ) { Text(uiText("Privacy")) }
        OutlinedButton(
            onClick = { uriHandler.openUri(termsUrl) },
            modifier = Modifier.weight(1f),
        ) { Text(uiText("Terms")) }
    }
    OutlinedButton(
        onClick = { uriHandler.openUri(deletionUrl) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(uiText("Data deletion")) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null)
            Text(
                uiText("No Xylune account, ads, analytics, or Xylune cloud. Chat history and API keys remain on this device; traffic goes to endpoints and web tools you explicitly enable."),
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
        "Xylune's versioned core prompt is built into the app and updates with Xylune. Profiles can adjust tone or add preferences, but cannot replace the core capability, tool, research, date, privacy, or safety protocol.",
    )
    FilledTonalButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, null)
        Text(uiText("New custom profile"), Modifier.padding(start = 8.dp))
    }
    if (profiles.isEmpty()) {
        Text(uiText("No saved prompts yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    profiles.forEach { profile ->
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            uiText(if (profile.mode == SystemPromptMode.OVERRIDE) "Override default tone/persona" else "Additional instructions"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (selectedDefaultId == profile.id) Text(uiText("Default"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(profile.prompt, maxLines = 4, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { viewModel.updateNewChatDefaults { it.copy(systemPromptProfileId = profile.id) } }) { Text(uiText("Use for new chats")) }
                    IconButton(onClick = { editing = profile }) { Icon(Icons.Outlined.Edit, uiText("Edit ${profile.name}")) }
                    IconButton(onClick = { viewModel.deleteSystemPromptProfile(profile.id) }) { Icon(Icons.Outlined.DeleteOutline, uiText("Delete ${profile.name}")) }
                }
            }
        }
    }
    if (selectedDefaultId != null) OutlinedButton(
        onClick = { viewModel.updateNewChatDefaults { it.copy(systemPromptProfileId = null) } },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(uiText("Use Xylune default for new chats")) }
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
    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text(uiText("Name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == SystemPromptMode.PREPEND, onClick = { mode = SystemPromptMode.PREPEND }, label = { Text(uiText("Prepend")) })
                    FilterChip(selected = mode == SystemPromptMode.OVERRIDE, onClick = { mode = SystemPromptMode.OVERRIDE }, label = { Text(uiText("Override")) })
                }
                OutlinedTextField(prompt, { prompt = it.take(64_000) }, label = { Text(uiText("Instructions")) }, minLines = 8, maxLines = 16, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name, prompt, mode) }, enabled = name.isNotBlank() && prompt.isNotBlank()) { Text(uiText("Save")) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
    )
}

@Composable
private fun LocalCodeExecutionSettingsPage(
    defaults: NewChatDefaults,
    automation: AutomationSettingsEntity,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) {
    val linuxStatus by viewModel.ubuntuStatus.collectAsStateWithLifecycle()
    SettingsPage {
        SectionTitle(
            "Availability in new chats",
            "Local execution is opt-in for fresh installs. Existing chats keep their own tool choices.",
        )
        SettingsGroup("Tool defaults") {
            ListItem(
                headlineContent = { Text(uiText("Python"), fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(uiText("Bundled Python 3.12 · no Linux download required")) },
                leadingContent = { Icon(Icons.Outlined.Code, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Switch(
                        checked = defaults.agentPythonEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) }
                        },
                    )
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
            ListItem(
                headlineContent = { Text(uiText("Linux commands"), fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Text(uiText(if (linuxStatus.installed) "${linuxStatus.distribution.displayName} ${linuxStatus.release} installed" else "Requires a separate distribution download"))
                },
                leadingContent = { Icon(Icons.Outlined.Terminal, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = {
                    Switch(
                        checked = defaults.agentUbuntuEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) }
                        },
                        enabled = linuxStatus.installed || defaults.agentUbuntuEnabled,
                    )
                },
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            )
        }
        SectionTitle(
            "Runtime manager",
            "Inspect environments, install packages, run a test, or add/remove the optional Linux distribution.",
        )
        Button(
            onClick = { viewModel.screen.value = Screen.SANDBOX },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Code, null)
            Text(uiText("Open runtime manager"), Modifier.padding(start = 8.dp))
        }
        SectionTitle(
            "Package approval",
            "Choose when Xylune may install Python or Linux packages and which sources are trusted.",
        )
        PackageApprovalEditor(automation, providers, viewModel)
        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun DeveloperSettingsPage(
    settings: DeveloperSettings,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle(
        "Developer settings",
        "Local diagnostics for measuring Xylune's rendering and process performance. No metrics are uploaded or stored in chat history.",
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
        uiText("Off by default. Normal chats show only a concise failure summary and Retry."),
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
        uiText("Attributes slow frames to Android frame stages, Xylune blur work, Compose recomposition pressure, allocations, and blocking GC. It adds some diagnostic overhead, so use it while reproducing an issue."),
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
        uiText("The overlay explicitly shares pointer input with the content underneath and never consumes it. Taps, scrolling, drawer gestures, and back navigation continue through the panel."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(uiText("Update interval"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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

    Text(uiText("Overlay position"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceOverlayPosition.entries.take(2).forEach { position ->
                FilterChip(
                    selected = settings.performanceOverlayPosition == position,
                    onClick = { viewModel.updateDeveloperSettings { it.copy(performanceOverlayPosition = position) } },
                    enabled = settings.enabled && settings.performanceOverlayEnabled,
                    label = { Text(uiText(position.displayName)) },
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
                    label = { Text(uiText(position.displayName)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    Text(
        uiText(if (settings.detailedPerformanceOverlay) {
            "Detailed mode shows Choreographer FPS, average/p95/p99 frame interval, jank against the current refresh budget, app CPU, PSS, Java heap, GPU duration when Android reports it, missed vsyncs per second, and total observed frames. Cause profiler ranks primary and secondary causes, reports confidence and severity, and shows the evidence used for attribution alongside FrameMetrics, blur, recomposition, allocation, and GC counters."
        } else {
            "Compact mode shows FPS, average frame time, and jank percentage."
        }),
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
        uiText("Guides are bright red and diagnostic-only. They are never shown unless both Developer settings and this toggle are enabled."),
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
    viewModel: ChatViewModel,
    developerEnabled: Boolean,
    matchLauncherIconToPalette: Boolean,
    automaticUpdateChecks: Boolean,
    onOpenDeveloper: () -> Unit,
    onOpenLicenses: () -> Unit,
) = SettingsPage {
    val appName = stringResource(R.string.app_name)
    val applicationInfo = LocalContext.current.applicationInfo
    val uriHandler = LocalUriHandler.current
    val updateState by viewModel.repositoryUpdateState.collectAsState()
    val sourceRepository = BuildConfig.SOURCE_REPOSITORY.takeIf(String::isNotBlank)
    val sourceUrl = sourceRepository?.let { "https://github.com/$it" }
    val siteColors = MaterialTheme.colorScheme
    val privacyUrl = remember(siteColors, matchLauncherIconToPalette) {
        xyluneWebsiteUrl("privacy/", siteColors, dynamicLogo = matchLauncherIconToPalette)
    }
    val termsUrl = remember(siteColors, matchLauncherIconToPalette) {
        xyluneWebsiteUrl("terms/", siteColors, dynamicLogo = matchLauncherIconToPalette)
    }
    val deletionUrl = remember(siteColors, matchLauncherIconToPalette) {
        xyluneWebsiteUrl("data-deletion/", siteColors, dynamicLogo = matchLauncherIconToPalette)
    }
    SectionTitle("$appName ${BuildConfig.VERSION_NAME}", "Native Android BYOK model workspace.")

    SettingsGroup("Project") {
        ListItem(
            headlineContent = { Text(uiText("Created by @omerfaruknehir"), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(uiText("Open the creator's GitHub profile")) },
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
            title = "Build source",
            subtitle = sourceRepository ?: "No GitHub source was embedded in this build",
            onClick = {
                if (sourceUrl != null) uriHandler.openUri(sourceUrl)
                else viewModel.postNotice("This build has no GitHub source provenance")
            },
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
            onClick = { uriHandler.openUri("https://github.com/omerfaruknehir/Xylune/issues") },
        )
    }

    SettingsGroup("Legal") {
        SettingsDestination(
            icon = Icons.Outlined.PrivacyTip,
            title = "Privacy policy",
            subtitle = "Privacy, local data, providers, and KVKK/GDPR boundaries",
            onClick = { uriHandler.openUri(privacyUrl) },
        )
        HorizontalDivider()
        SettingsDestination(
            icon = Icons.Outlined.Security,
            title = "Terms & disclaimer",
            subtitle = "Use terms, third-party AI limits, warranty, and liability",
            onClick = { uriHandler.openUri(termsUrl) },
        )
        HorizontalDivider()
        SettingsDestination(
            icon = Icons.Outlined.DeleteOutline,
            title = "Data deletion",
            subtitle = "Delete local data and provider-held copies",
            onClick = { uriHandler.openUri(deletionUrl) },
        )
    }

    SettingsGroup("Updates") {
        ListItem(
            headlineContent = { Text(uiText("Check automatically"), fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(uiText("Check the source repository once per day when Xylune starts")) },
            leadingContent = { Icon(Icons.Outlined.Refresh, null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = {
                Switch(
                    checked = automaticUpdateChecks,
                    onCheckedChange = viewModel::setAutomaticUpdateChecks,
                    enabled = sourceRepository != null,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val state = updateState) {
                RepositoryUpdateState.Unsupported -> {
                    Text(uiText("Automatic checks are unavailable because this build has no embedded GitHub repository origin."))
                    Text(
                        uiText("GitHub release workflows embed their own owner/repository. Fork builds therefore follow the fork they came from."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RepositoryUpdateState.Idle -> {
                    Text(uiText("Updates are checked against ${sourceRepository ?: "the build repository"}."))
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(uiText(" Check for updates"))
                    }
                }
                RepositoryUpdateState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(uiText("Checking ${sourceRepository ?: "the source repository"}…"))
                    }
                }
                is RepositoryUpdateState.UpToDate -> {
                    Text(uiText("Xylune is up to date"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Latest release: ${state.latestVersion} · checked ${DateFormat.getDateTimeInstance().format(Date(state.checkedAt))}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(uiText(" Check again"))
                    }
                }
                is RepositoryUpdateState.Available -> {
                    val release = state.release
                    Text(uiText("Xylune ${release.versionName} is available"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Source: ${release.repository}" + (release.publishedAt?.let { " · $it" } ?: "")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    release.compatibilityMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            val target = if (release.directInstallCompatible && release.apkDownloadUrl != null) {
                                release.apkDownloadUrl
                            } else release.releasePageUrl
                            uriHandler.openUri(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Cloud, null)
                        Text(uiText(if (release.directInstallCompatible) " Download update" else " Open release page"))
                    }
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(uiText(" Check again"))
                    }
                }
                is RepositoryUpdateState.Failed -> {
                    Text(uiText("Update check failed"), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(uiText(" Retry"))
                    }
                }
            }
        }
    }

    SettingsGroup("Build information") {
        AboutInfoRow("Version", BuildConfig.VERSION_NAME)
        AboutInfoRow("Build", "${BuildConfig.VERSION_CODE} · ${BuildConfig.BUILD_TYPE}")
        AboutInfoRow("Package", BuildConfig.APPLICATION_ID)
        AboutInfoRow("Source repository", sourceRepository ?: "Not embedded")
        if (BuildConfig.SOURCE_COMMIT.isNotBlank()) AboutInfoRow("Source commit", BuildConfig.SOURCE_COMMIT.take(12))
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
            Text(uiText("Private by design"), fontWeight = FontWeight.SemiBold)
            Text(
                uiText("Chats, credentials, and workspaces stay on your device. Xylune connects directly to providers you configure and has no application backend, ads, or telemetry."),
                style = MaterialTheme.typography.bodySmall,
            )
            if (BuildConfig.DEBUG) {
                HorizontalDivider()
                Text(
                    uiText("This is a debug-signed development build."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    TextButton(onClick = onOpenDeveloper) {
        Icon(Icons.Outlined.DeveloperMode, null, Modifier.size(18.dp))
        Text(
            uiText(if (developerEnabled) "Developer options · enabled" else "Developer options"),
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
            uiText(label),
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

internal fun xyluneWebsiteUrl(
    path: String,
    colors: ColorScheme,
    dynamicLogo: Boolean = false,
): String {
    fun Color.webHex(): String = "%06x".format(toArgb() and 0x00ffffff)
    val normalizedPath = path.trim('/').takeIf(String::isNotBlank)?.plus('/').orEmpty()
    val parameters = linkedMapOf(
        "theme" to "app",
        "dark" to if (colors.background.luminance() < 0.5f) "1" else "0",
        "primary" to colors.primary.webHex(),
        "onPrimary" to colors.onPrimary.webHex(),
        "primaryContainer" to colors.primaryContainer.webHex(),
        "onPrimaryContainer" to colors.onPrimaryContainer.webHex(),
        "secondary" to colors.secondary.webHex(),
        "onSecondary" to colors.onSecondary.webHex(),
        "secondaryContainer" to colors.secondaryContainer.webHex(),
        "onSecondaryContainer" to colors.onSecondaryContainer.webHex(),
        "tertiary" to colors.tertiary.webHex(),
        "onTertiary" to colors.onTertiary.webHex(),
        "tertiaryContainer" to colors.tertiaryContainer.webHex(),
        "onTertiaryContainer" to colors.onTertiaryContainer.webHex(),
        "background" to colors.background.webHex(),
        "surface" to colors.surface.webHex(),
        "surfaceLow" to colors.surfaceContainerLow.webHex(),
        "surfaceContainer" to colors.surfaceContainer.webHex(),
        "onSurface" to colors.onSurface.webHex(),
        "onSurfaceVariant" to colors.onSurfaceVariant.webHex(),
        "outline" to colors.outline.webHex(),
        "outlineVariant" to colors.outlineVariant.webHex(),
        "rail" to colors.surfaceContainerLowest.webHex(),
        "dynamicLogo" to if (dynamicLogo) "1" else "0",
    )
    return "https://omerfaruknehir.github.io/Xylune/$normalizedPath?" +
        parameters.entries.joinToString("&") { (name, value) -> "$name=$value" }
}

private val ColorPalette.displayName: String
    get() = when (this) {
        ColorPalette.XYLUNE -> "Xylune"
        ColorPalette.SYSTEM -> "Dynamic"
        ColorPalette.GRAPHITE -> "Graphite"
        ColorPalette.OCEAN -> "Ocean"
        ColorPalette.VIOLET -> "Violet"
        ColorPalette.SUNSET -> "Sunset"
    }

private val ColorPalette.description: String
    get() = when (this) {
        ColorPalette.XYLUNE -> "Xylune's natural green Material palette"
        ColorPalette.SYSTEM -> "Colors generated from your wallpaper on Android 12+"
        ColorPalette.GRAPHITE -> "Restrained blue-gray palette"
        ColorPalette.OCEAN -> "Cool teal and cyan accents"
        ColorPalette.VIOLET -> "Deep purple with soft rose accents"
        ColorPalette.SUNSET -> "Warm orange, rose, and gold accents"
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

    Text(uiText("Tools and modes"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    SettingsSwitch("Web search", webEnabled, onWeb)
    SettingsSwitch("Deep Research", deepResearchEnabled, onDeepResearch, enabled = webEnabled || !deepResearchEnabled)
    Text(uiText("Deep Research plans, searches iteratively, verifies sources, and produces a cited report. Enabling it also enables web search."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
    SectionTitle(
        "Xylune core prompt",
        "The exact prompt bundled with this app version is shown below. It is selectable for inspection and intentionally read-only.",
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(uiText("Managed by Xylune · revision $XYLUNE_CORE_PROMPT_REVISION"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Use custom instruction profiles for additional tone and workflow preferences."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = DEFAULT_XYLUNE_SYSTEM_PROMPT,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Text(
                uiText("Xylune adds request-specific date, enabled-tool, research, memory, attachment, and generated-content instructions at runtime. Those dynamic layers are not editable either and are not presented as one misleading static block."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val options = remember(
        provider?.id,
        provider?.kind,
        model?.modelId,
        model?.supportsThinking,
        model?.reasoningMetadataAvailable,
        model?.reasoningEffortsCsv,
        model?.reasoningMandatory,
    ) {
        supportedThinkingLevels(provider, model)
    }
    val supported = options.isNotEmpty()
    val effectiveEnabled = effectiveThinkingEnabled(model, enabled)
    val effectiveEffort = defaultThinkingEffort(model, effort)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SmartToy, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(uiText("Thinking"), fontWeight = FontWeight.SemiBold)
                Text(
                    uiText(if (!supported) "Not supported by this model" else if (effectiveEnabled) "${effectiveEffort.displayName} effort" else "Off"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = effectiveEnabled && supported,
                onCheckedChange = onEnabled,
                enabled = supported && options.any { !it.enabled },
            )
            Box {
                IconButton(onClick = { menu = true }, enabled = supported) { Icon(Icons.Outlined.ExpandMore, uiText("Thinking effort")) }
                XyluneDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    options.filter { it.enabled }.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(uiText(option.label)) },
                            leadingIcon = if (effectiveEnabled && effectiveEffort == option.effort) ({ Icon(Icons.Outlined.CheckCircle, null) }) else null,
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
    Text(uiText("Available levels follow the selected model. Some models cannot fully disable reasoning."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var showPicker by remember { mutableStateOf(false) }
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val provider = providers.firstOrNull { it.id == providerId }
    val selectedModel = models.firstOrNull { it.modelId == modelId }
        ?: allModels.firstOrNull { it.providerId == providerId && it.modelId == modelId }
    SectionTitle("Model", "One searchable catalog is used everywhere in Xylune.")
    OutlinedButton(
        onClick = { showPicker = true },
        enabled = providers.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(selectedModel?.displayName ?: modelId.ifBlank { uiText("Choose a model") }, maxLines = 1)
            Text(
                uiText(provider?.displayName ?: "No provider selected"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
    if (showPicker) {
        ModelPickerSheet(
            providers = providers,
            models = allModels,
            selectedProviderId = providerId,
            selectedModelId = modelId,
            favoriteKeys = favoriteModels,
            recentKeys = recentModels,
            onToggleFavorite = viewModel::toggleFavoriteModel,
            onSelect = onSelect,
            onDismiss = { showPicker = false },
        )
    }
    if (providers.isEmpty()) Text(uiText("Add a usable provider in the Providers tab."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
private fun SettingsSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val haptics = rememberXyluneHaptics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(uiText(label), Modifier.weight(1f))
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
        label = { Text(uiText(label)) },
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
    var automaticMetadataAttemptedFor by remember { mutableStateOf<String?>(null) }
    val selected = registeredProviders.firstOrNull { it.id == selectedId } ?: registeredProviders.firstOrNull()
    val selectedModelFlow = remember(selected?.id) {
        selected?.id?.let(viewModel::modelsFor) ?: flowOf<List<ModelEntity>>(emptyList())
    }
    val selectedModels by selectedModelFlow.collectAsStateWithLifecycle(initialValue = emptyList())

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
    LaunchedEffect(selected?.id, selectedModels, apiKey, headers, baseUrl) {
        val provider = selected ?: return@LaunchedEffect
        if (!ModelRequestPolicy.isOpenRouter(provider) || provider.kind == ProviderKind.OPENAI_OAUTH) return@LaunchedEffect
        val newestMetadata = selectedModels.maxOfOrNull(ModelEntity::metadataUpdatedAt) ?: 0L
        val metadataFresh = selectedModels.any { it.metadataSource == "OpenRouter" } &&
            System.currentTimeMillis() - newestMetadata < OPENROUTER_METADATA_REFRESH_INTERVAL_MS
        if (metadataFresh) return@LaunchedEffect
        if (automaticMetadataAttemptedFor == provider.id || (provider.apiKeyRequired && apiKey.isBlank())) return@LaunchedEffect
        automaticMetadataAttemptedFor = provider.id
        syncingModels = true
        modelSyncStatus = "Fetching OpenRouter capabilities, reasoning modes, limits, and pricing…"
        runCatching { viewModel.discoverModels(provider.kind, baseUrl, apiKey, headers) }
            .onSuccess { discovered ->
                viewModel.saveDiscoveredModels(provider.id, discovered)
                modelSyncStatus = "Automatically updated metadata for ${discovered.size} models"
            }
            .onFailure { modelSyncStatus = "Automatic metadata refresh failed: ${it.message?.take(600).orEmpty()}" }
        syncingModels = false
    }

    SettingsPage {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Providers", "Choose a provider, then manage its connection and models.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { addingChatGpt = true }) {
                    Icon(Icons.Outlined.AccountCircle, null)
                    Text(uiText(" ChatGPT"))
                }
                FilledTonalButton(onClick = { addingProvider = true }) {
                    Icon(Icons.Outlined.Add, null)
                    Text(uiText(" API"), Modifier.padding(start = 2.dp))
                }
            }
        }

        if (registeredProviders.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text(uiText("No providers yet"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(uiText("Add a ChatGPT account or configure an API-compatible provider."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { addingChatGpt = true }) { Text(uiText("Add ChatGPT")) }
                        OutlinedButton(onClick = { addingProvider = true }) { Text(uiText("Add API")) }
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
                                    uiText(if (provider.kind == ProviderKind.OPENAI_OAUTH) {
                                        when (openAiOAuthStates[provider.id]) {
                                            is OpenAiOAuthState.SignedIn -> "ChatGPT OAuth • Connected"
                                            OpenAiOAuthState.SigningIn -> "ChatGPT OAuth • Signing in"
                                            is OpenAiOAuthState.Error -> "ChatGPT OAuth • Needs attention"
                                            else -> "ChatGPT OAuth • Disconnected"
                                        }
                                    } else providerKindLabel(provider.kind)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (provider.id == conversationProviderId) Text(uiText("In use"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (provider.id == selected?.id) Icon(Icons.Outlined.CheckCircle, uiText("Selected"), tint = MaterialTheme.colorScheme.primary)
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
                                uiText(if (!provider.apiKeyRequired) "Keyless endpoint" else if (apiKey.isNotBlank()) "API key saved securely" else "API key missing"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (provider.apiKeyRequired && apiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = { editingConnection = true }) { Text(uiText("Edit")) }
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
                            Text(uiText(if (syncingModels) " Refreshing…" else " Refresh models"))
                        }
                        OutlinedButton(onClick = { removingProvider = provider }) {
                            Icon(Icons.Outlined.DeleteOutline, uiText("Remove"), tint = MaterialTheme.colorScheme.error)
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
                Text(uiText("Edit connection"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
        XyluneAlertDialog(
            onDismissRequest = { removingProvider = null },
            title = { Text(uiText("Remove ${provider.displayName}?")) },
            text = { Text(uiText(if (provider.kind == ProviderKind.OPENAI_OAUTH) "Its encrypted OAuth session and models will be disconnected. Chats and usage history are kept." else "Its saved API key will be erased and it will disappear from model selectors. Chats and usage history are kept.")) },
            dismissButton = { OutlinedButton(onClick = { removingProvider = null }) { Text(uiText("Cancel")) } },
            confirmButton = { Button(onClick = { viewModel.removeProvider(provider); removingProvider = null }) { Text(uiText("Remove provider")) } },
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
            val models = draft.selectedModels.map { candidate ->
                val bundled = DefaultCatalog.models.firstOrNull { it.providerId == id && it.modelId == candidate.id }
                val base = bundled ?: ModelEntity(
                    providerId = id, modelId = candidate.id, displayName = candidate.displayName,
                    contextWindow = 128_000, maxOutputTokens = 16_384,
                    inputCacheHitUsdPerMillion = 0.0, inputCacheMissUsdPerMillion = 0.0,
                    outputUsdPerMillion = 0.0,
                )
                val model = base.copy(
                    displayName = candidate.displayName,
                    contextWindow = candidate.contextWindow ?: base.contextWindow,
                    maxOutputTokens = candidate.maxOutputTokens ?: base.maxOutputTokens,
                    inputCacheHitUsdPerMillion = candidate.inputCacheHitUsdPerMillion
                        ?: candidate.inputCacheMissUsdPerMillion ?: base.inputCacheHitUsdPerMillion,
                    inputCacheMissUsdPerMillion = candidate.inputCacheMissUsdPerMillion ?: base.inputCacheMissUsdPerMillion,
                    outputUsdPerMillion = candidate.outputUsdPerMillion ?: base.outputUsdPerMillion,
                    pricingConfigured = candidate.inputCacheMissUsdPerMillion != null && candidate.outputUsdPerMillion != null || base.pricingConfigured,
                    supportsThinking = candidate.supportsThinking ?: base.supportsThinking,
                    supportsVision = candidate.supportsVision ?: base.supportsVision,
                    supportsFiles = candidate.supportsFiles ?: base.supportsFiles,
                    supportsTools = candidate.supportsTools ?: base.supportsTools,
                    supportsImageGeneration = candidate.supportsImageGeneration ?: base.supportsImageGeneration,
                    description = candidate.description,
                    createdAtEpochSeconds = candidate.createdAtEpochSeconds,
                    reasoningMetadataAvailable = candidate.reasoningMetadataAvailable,
                    reasoningEffortsCsv = candidate.reasoningEfforts.joinToString(",") { it.name },
                    reasoningDefaultEffort = candidate.reasoningDefaultEffort?.name.orEmpty(),
                    reasoningDefaultEnabled = candidate.reasoningDefaultEnabled,
                    reasoningMandatory = candidate.reasoningMandatory,
                    reasoningSupportsMaxTokens = candidate.reasoningSupportsMaxTokens,
                    metadataSource = candidate.metadataSource,
                    metadataUpdatedAt = if (candidate.metadataSource.isNotBlank()) System.currentTimeMillis() else 0L,
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
    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Add ChatGPT provider")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(uiText("Each provider keeps its OAuth session, models, usage limits, and refresh state separate. Xylune requests a fresh sign-in page so you can add a different ChatGPT account."))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(uiText("Provider name")) },
                    placeholder = { Text(uiText("Work ChatGPT")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank()) { Text(uiText("Add")) }
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
    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Rename ChatGPT provider")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(uiText("Provider name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        confirmButton = { Button(onClick = { onRename(name.trim()) }, enabled = name.isNotBlank()) { Text(uiText("Save")) } },
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
                        uiText(when (state) {
                            OpenAiOAuthState.SignedOut -> "Use your ChatGPT plan without an API key"
                            OpenAiOAuthState.SigningIn -> "Complete sign-in in your browser…"
                            is OpenAiOAuthState.SignedIn -> state.email?.let { "Connected • $it" } ?: "Connected"
                            is OpenAiOAuthState.Error -> state.message
                        }),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state is OpenAiOAuthState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRename, enabled = state !is OpenAiOAuthState.SigningIn) {
                    Icon(Icons.Outlined.Edit, uiText("Rename provider"))
                }
                if (state is OpenAiOAuthState.SigningIn) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onCancel) { Text(uiText("Cancel")) }
                }
            }
            Text(
                uiText("One-tap native OAuth. Xylune opens the system browser, receives the localhost callback itself, encrypts the session on this device, and refreshes it automatically. No extension or local proxy is required."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (signedIn) {
                ChatGptUsagePanel(usageState, onRefreshUsage)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRefreshModels, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(uiText(" Refresh models"))
                    }
                    OutlinedButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Outlined.Logout, null)
                        Text(uiText(" Disconnect"))
                    }
                }
            } else {
                Button(
                    onClick = onSignIn,
                    enabled = state !is OpenAiOAuthState.SigningIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Login, null)
                    Text(uiText(if (state is OpenAiOAuthState.Error) " Sign in again" else " Sign in with ChatGPT"))
                }
            }
            TextButton(onClick = onRemove, enabled = state !is OpenAiOAuthState.SigningIn, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Text(uiText(" Remove provider"), color = MaterialTheme.colorScheme.error)
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
                    Text(uiText("Usage & limits"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText(snapshot?.planType?.let { "${humanizeUsageName(it)} plan • reported by ChatGPT" }
                            ?: "Current account quota windows"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state is OpenAiOAuthUsageState.Loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = onRefresh, enabled = state !is OpenAiOAuthUsageState.Loading) {
                    Icon(Icons.Outlined.Refresh, uiText("Refresh usage"))
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
                        uiText(snapshot.rateLimitReachedType?.let { "Limit reached: ${humanizeUsageName(it)}" }
                            ?: "A ChatGPT usage limit has been reached."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state is OpenAiOAuthUsageState.Error) {
                    Text(
                        uiText("Refresh failed • ${state.message}"),
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
                uiText("${left.roundToInt()}% left"),
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
            uiText(buildString {
                append("${used.roundToInt()}% used")
                if (reset != null) append(" • $reset")
            }),
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
        OutlinedTextField(name, onName, label = { Text(uiText("Provider name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(baseUrl, onBaseUrl, label = { Text(uiText("API base URL")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            key, onKey,
            label = { Text(uiText(if (apiKeyRequired) "API key" else "API key (optional)")) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("Require API key"), fontWeight = FontWeight.Medium)
                Text(uiText("Disable only for a trusted local or keyless endpoint"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(uiText("Advanced headers"), fontWeight = FontWeight.Medium)
                    Text(uiText("Usually unnecessary"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ExpandMore, null)
            }
        }
        if (advanced) OutlinedTextField(
            headers,
            onHeaders,
            label = { Text(uiText("Custom headers JSON")) },
            minLines = 3,
            visualTransformation = rememberCodeVisualTransformation("json"),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onSave,
            enabled = name.isNotBlank() && baseUrl.isNotBlank() && (!apiKeyRequired || key.isNotBlank()),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(uiText("Save connection")) }
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
    val selectedModels: List<DiscoveredModel>,
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
    var manualModelId by remember { mutableStateOf("") }
    var manualModelName by remember { mutableStateOf("") }
    var discoveredModels by remember { mutableStateOf<List<DiscoveredModel>>(emptyList()) }
    var selectedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var discovering by remember { mutableStateOf(false) }
    var discoveryAttempted by remember { mutableStateOf(false) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var modelSearch by remember { mutableStateOf("") }
    var showManualModel by remember { mutableStateOf(false) }
    val connectionReady = baseUrl.isNotBlank() && (!apiKeyRequired || apiKey.isNotBlank())
    val manualModelReady = showManualModel && manualModelId.isNotBlank() && manualModelName.isNotBlank()
    val valid = name.isNotBlank() && connectionReady && (selectedModelIds.isNotEmpty() || manualModelReady)
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
        selectedModelIds = emptySet()
        manualModelId = ""
        manualModelName = ""
    }

    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Add provider")) },
        text = {
            Column(
                Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(onClick = { templateMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(uiText(templates.firstOrNull { it.id == templateId }?.let { "Preset: ${it.displayName}" } ?: "Preset: Custom"), Modifier.weight(1f))
                    }
                    XyluneDropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
                        DropdownMenuItem(text = { Text(uiText("Custom provider")) }, onClick = {
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
                OutlinedTextField(name, { name = it }, label = { Text(uiText("Provider name")) }, placeholder = { Text(uiText("My DeepSeek account")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(uiText("Protocol: ${providerKindLabel(kind)}"), Modifier.weight(1f))
                    }
                    XyluneDropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
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
                OutlinedTextField(baseUrl, { baseUrl = it; invalidateDiscovery() }, label = { Text(uiText("Base URL")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiKey,
                    { apiKey = it; invalidateDiscovery() },
                    label = { Text(uiText(if (apiKeyRequired) "API key" else "API key (optional)")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("Require API key"))
                        Text(uiText("Disable only for your own local/keyless endpoint"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = apiKeyRequired, onCheckedChange = { apiKeyRequired = it; invalidateDiscovery() })
                }
                HorizontalDivider()
                OutlinedTextField(
                    headers,
                    { headers = it; invalidateDiscovery() },
                    label = { Text(uiText("Custom headers JSON")) },
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
                                    selectedModelIds = models.mapTo(linkedSetOf()) { it.id }
                                    manualModelId = ""
                                    manualModelName = ""
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
                    Text(uiText(if (discovering) " Connecting…" else if (discoveryAttempted) " Fetch models again" else " Connect & fetch models"))
                }
                discoveryError?.let { message ->
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(message, Modifier.fillMaxWidth().padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (discoveredModels.isNotEmpty()) {
                    Text(uiText("Models from provider"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        modelSearch,
                        { modelSearch = it },
                        label = { Text(uiText("Search ${discoveredModels.size} models")) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(uiText("${selectedModelIds.size} of ${discoveredModels.size} selected"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row {
                            TextButton(onClick = { selectedModelIds = discoveredModels.mapTo(linkedSetOf()) { it.id } }) { Text(uiText("Select all")) }
                            TextButton(onClick = { selectedModelIds = emptySet() }) { Text(uiText("Clear")) }
                        }
                    }
                    if (visibleModels.size > MODEL_MANAGER_RENDER_LIMIT) {
                        Text(
                            uiText("Showing the first $MODEL_MANAGER_RENDER_LIMIT matches. Search to find a specific model; all selected models will still be saved."),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        visibleModels.take(MODEL_MANAGER_RENDER_LIMIT).forEach { model ->
                            val checked = model.id in selectedModelIds
                            val toggle = {
                                selectedModelIds = if (checked) selectedModelIds - model.id else selectedModelIds + model.id
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = toggle).padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { toggle() })
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, fontWeight = FontWeight.Medium)
                                    Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        uiText(buildList {
                                            model.contextWindow?.let { add("${it / 1_000}K context") }
                                            if (model.supportsThinking == true) add("Thinking")
                                            if (model.supportsTools == true) add("Tools")
                                            if (model.supportsVision == true) add("Vision")
                                        }.joinToString(" · ")),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { showManualModel = !showManualModel }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.ExpandMore, null)
                    Text(uiText(if (showManualModel) "Hide manual model entry" else "Provider has no model list? Enter manually"))
                }
                if (showManualModel) {
                    val bundled = DefaultCatalog.models.filter { it.providerId == templateId }
                    if (bundled.isNotEmpty()) {
                        Text(uiText("Bundled suggestions"), style = MaterialTheme.typography.labelLarge)
                        bundled.forEach { model ->
                            AssistChip(onClick = { manualModelId = model.modelId; manualModelName = model.displayName }, label = { Text(model.displayName) })
                        }
                    }
                    OutlinedTextField(manualModelId, { manualModelId = it.trim() }, label = { Text(uiText("API model ID")) }, placeholder = { Text(uiText("deepseek-chat")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(manualModelName, { manualModelName = it }, label = { Text(uiText("Model display name")) }, placeholder = { Text(uiText("DeepSeek Chat")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (manualModelReady) Text(uiText("Manual model will also be included"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (selectedModelIds.isNotEmpty()) Text(uiText("Only the selected provider models will be saved."), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val selected = discoveredModels.filter { it.id in selectedModelIds }
                    val manual = if (manualModelReady) listOf(DiscoveredModel(manualModelId, manualModelName.trim())) else emptyList()
                    onAdd(
                        ProviderDraft(
                            templateProviderId = templateId,
                            name = name.trim(),
                            kind = kind,
                            baseUrl = baseUrl.trim(),
                            apiKey = apiKey,
                            apiKeyRequired = apiKeyRequired,
                            headers = headers.ifBlank { "{}" },
                            selectedModels = (selected + manual).distinctBy { it.id },
                        ),
                    )
                },
            ) { Text(uiText("Add provider")) }
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
    Column { Text(uiText(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(uiText(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
        XyluneSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            steps = steps,
        )
        if (supportingText.isNotBlank()) {
            Text(
                uiText(supportingText),
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
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val effectiveProvider = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
    val providerModels = allModels.filter { it.providerId == effectiveProvider?.id }
    val effectiveModel = providerModels.firstOrNull { it.modelId == modelId } ?: providerModels.firstOrNull()

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(uiText(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                SettingsModelPickerButton(
                    providers = providers,
                    providerId = effectiveProvider?.id.orEmpty(),
                    modelId = effectiveModel?.modelId.orEmpty(),
                    viewModel = viewModel,
                    emptyLabel = "Choose automation model",
                    onSelect = { selectedProvider, selectedModel -> onChange(mode, selectedProvider, selectedModel) },
                )
            }
        }
    }
}

@Composable
private fun SettingsModelPickerButton(
    providers: List<ProviderEntity>,
    providerId: String,
    modelId: String,
    viewModel: ChatViewModel,
    emptyLabel: String,
    onSelect: (String, String) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val favoriteModels by viewModel.favoriteModels.collectAsStateWithLifecycle()
    val recentModels by viewModel.recentModels.collectAsStateWithLifecycle()
    val provider = providers.firstOrNull { it.id == providerId }
    val model = allModels.firstOrNull { it.providerId == providerId && it.modelId == modelId }
    OutlinedButton(onClick = { showPicker = true }, enabled = providers.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(model?.displayName ?: uiText(emptyLabel), maxLines = 1)
            provider?.let { Text(it.displayName, style = MaterialTheme.typography.labelSmall) }
        }
        Icon(Icons.Outlined.ChevronRight, null)
    }
    if (showPicker) ModelPickerSheet(
        providers = providers,
        models = allModels,
        selectedProviderId = providerId,
        selectedModelId = modelId,
        favoriteKeys = favoriteModels,
        recentKeys = recentModels,
        onToggleFavorite = viewModel::toggleFavoriteModel,
        onSelect = onSelect,
        onDismiss = { showPicker = false },
    )
}

@Composable
private fun PackageApprovalEditor(
    settings: AutomationSettingsEntity,
    providers: List<ProviderEntity>,
    viewModel: ChatViewModel,
) {
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val effectiveProvider = providers.firstOrNull { it.id == settings.approvalProviderId } ?: providers.firstOrNull()
    val providerModels = allModels.filter { it.providerId == effectiveProvider?.id }
    val effectiveModel = providerModels.firstOrNull { it.modelId == settings.approvalModelId } ?: providerModels.firstOrNull()
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
                SettingsModelPickerButton(
                    providers = providers,
                    providerId = effectiveProvider?.id.orEmpty(),
                    modelId = effectiveModel?.modelId.orEmpty(),
                    viewModel = viewModel,
                    emptyLabel = "Choose approval model",
                    onSelect = { providerId, modelId ->
                        viewModel.updateAutomationSettings {
                            it.copy(approvalProviderId = providerId, approvalModelId = modelId)
                        }
                    },
                )
            }
            if (settings.packageApprovalMode == PackageApprovalMode.TRUSTED_ONLY) {
                OutlinedTextField(
                    settings.trustedPythonPackages,
                    { value -> viewModel.updateAutomationSettings { it.copy(trustedPythonPackages = value) } },
                    label = { Text(uiText("Trusted pip packages")) },
                    supportingText = { Text(uiText("Comma, space, or newline separated")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    settings.trustedUbuntuPackages,
                    { value -> viewModel.updateAutomationSettings { it.copy(trustedUbuntuPackages = value) } },
                    label = { Text(uiText("Trusted Linux packages (apt/apk)")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(uiText("Advanced package sources"))
                    Text(uiText("Allow pip direct references and relaxed apt names; command-line options remain blocked"), style = MaterialTheme.typography.labelSmall)
                }
                Switch(
                    checked = !settings.packageRestrictionsEnabled,
                    onCheckedChange = { enabled -> viewModel.updateAutomationSettings { it.copy(packageRestrictionsEnabled = !enabled) } },
                )
            }
            if (settings.packageApprovalMode == PackageApprovalMode.AUTO_APPROVE || !settings.packageRestrictionsEnabled) {
                Text(
                    uiText("Packages and their installers run with Xylune's app permissions. Ubuntu is for compatibility, not containment; these settings intentionally reduce confirmation barriers."),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (settings.packageApprovalMode == PackageApprovalMode.MODEL_REVIEW) Text(
                uiText("Model review is advisory and can be wrong. Xylune records the selected model's allow/deny reason, but this is not malware analysis or a security guarantee."),
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
    var capabilityFilter by remember { mutableStateOf(ModelPickerFilter.ALL) }
    val visibleModels = remember(models, search) {
        val query = search.trim()
        if (query.isBlank()) models else models.filter {
            it.displayName.contains(query, ignoreCase = true) || it.modelId.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
        }
    }.filter { model ->
        when (capabilityFilter) {
            ModelPickerFilter.ALL -> true
            ModelPickerFilter.THINKING -> model.supportsThinking
            ModelPickerFilter.TOOLS -> model.supportsTools
            ModelPickerFilter.VISION -> model.supportsVision
            ModelPickerFilter.FILES -> model.supportsFiles
            ModelPickerFilter.IMAGE -> model.supportsImageGeneration
            ModelPickerFilter.FREE -> model.isActuallyFree
            ModelPickerFilter.FAVORITES, ModelPickerFilter.RECENT -> true
        }
    }
    val displayedModels = visibleModels.take(MODEL_MANAGER_RENDER_LIMIT)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(uiText("Models"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(uiText("${models.size} available for ${provider.displayName} · metadata refreshes with the catalog"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { creating = true }) {
                Icon(Icons.Outlined.Add, null)
                Text(uiText("Add"), Modifier.padding(start = 6.dp))
            }
        }
        if (models.size > 8) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(uiText("Search models")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ModelPickerFilter.ALL,
                ModelPickerFilter.THINKING,
                ModelPickerFilter.TOOLS,
                ModelPickerFilter.VISION,
                ModelPickerFilter.FILES,
                ModelPickerFilter.IMAGE,
                ModelPickerFilter.FREE,
            ).forEach { option ->
                FilterChip(selected = capabilityFilter == option, onClick = { capabilityFilter = option }, label = { Text(uiText(option.label)) })
            }
        }
        if (visibleModels.size > displayedModels.size) {
            Text(
                uiText("Showing ${displayedModels.size} of ${visibleModels.size}. Search or filter to narrow the catalog."),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
            Column {
                displayedModels.forEachIndexed { index, model ->
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
                    if (index != displayedModels.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
                if (displayedModels.isEmpty()) Text(uiText("No matching models."), Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        if (supportsThinking) add(if (reasoningMandatory) "Thinking always on" else "Thinking")
        if (supportsVision) add("Vision")
        if (supportsTools) add("Tools")
        if (supportsImageGeneration) add("Image generation")
        if (!pricingConfigured) add("Cost unavailable")
        if (metadataSource.isNotBlank()) add("$metadataSource metadata")
    }.joinToString(" · ")

private const val MODEL_MANAGER_RENDER_LIMIT = 60
private const val OPENROUTER_METADATA_REFRESH_INTERVAL_MS = 24L * 60 * 60 * 1_000

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
            Text(uiText("Only the essentials are shown. Pricing is optional."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(id, { id = it.trim() }, label = { Text(uiText("API model ID")) }, enabled = allowIdEdit, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(name, { name = it }, label = { Text(uiText("Display name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(context, { context = it.filter(Char::isDigit) }, label = { Text(uiText("Context tokens")) }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(output, { output = it.filter(Char::isDigit) }, label = { Text(uiText("Max output")) }, modifier = Modifier.weight(1f), singleLine = true)
            }

            if (manualRequestType) {
                Text(uiText("Request type"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = requestType == ModelRequestType.CHAT,
                        onClick = { requestType = ModelRequestType.CHAT },
                        label = { Text(uiText("Chat")) },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = requestType == ModelRequestType.IMAGE_GENERATION,
                        onClick = { requestType = ModelRequestType.IMAGE_GENERATION },
                        label = { Text(uiText("Image generation")) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(uiText("Controls whether this custom endpoint uses chat/completions or images/generations."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (automaticPreset) {
                Text(uiText("Model capabilities and request transport are selected automatically by this provider preset."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (!automaticPreset) {
                Text(uiText("Advanced compatibility"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = thinking, onClick = { thinking = !thinking }, label = { Text(uiText("Thinking")) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = tools, onClick = { tools = !tools }, label = { Text(uiText("Tools")) }, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vision, onClick = { vision = !vision }, label = { Text(uiText("Vision")) }, modifier = Modifier.weight(1f))
                    FilterChip(selected = files, onClick = { files = !files }, label = { Text(uiText("Files")) }, modifier = Modifier.weight(1f))
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
                        Text(uiText("Pricing"), fontWeight = FontWeight.SemiBold)
                        Text(uiText(if (pricingConfigured) "Configured in USD per million tokens" else "Optional · cost will show as unavailable"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ExpandMore, null)
                }
            }
            if (showPricing) {
                SettingsSwitch("Pricing configured", pricingConfigured, { pricingConfigured = it })
                OutlinedTextField(cacheHit, { cacheHit = it }, label = { Text(uiText("Cached input")) }, enabled = pricingConfigured, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(cacheMiss, { cacheMiss = it }, label = { Text(uiText("Input")) }, enabled = pricingConfigured, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(outputPrice, { outputPrice = it }, label = { Text(uiText("Output")) }, enabled = pricingConfigured, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text(uiText("Cancel")) }
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
                ) { Text(uiText("Save")) }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}


@Composable
private fun SearchSettingsPage() = SettingsPage {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as app.xylune.chat.XyluneApplication).container
    }
    val settings by container.appPreferences.webSearchSettings.collectAsState()
    var apiKey by remember(settings.engine) {
        mutableStateOf(container.secureStore.searchApiKey(settings.engine.name))
    }
    var keySaved by remember(settings.engine) { mutableStateOf(false) }

    SettingsGroup("Routing") {
        app.xylune.chat.settings.WebSearchRoute.entries.forEach { route ->
            ListItem(
                headlineContent = { Text(uiText(route.title)) },
                supportingContent = { Text(uiText(route.description)) },
                leadingContent = {
                    RadioButton(
                        selected = settings.route == route,
                        onClick = null,
                    )
                },
                modifier = Modifier.clickable {
                    container.appPreferences.updateWebSearchSettings { it.copy(route = route) }
                },
            )
        }
    }

    SettingsGroup("Fallback search engine") {
        app.xylune.chat.settings.WebSearchEngine.entries.forEach { engine ->
            ListItem(
                headlineContent = { Text(uiText(engine.title)) },
                supportingContent = { Text(uiText(engine.description)) },
                leadingContent = {
                    RadioButton(
                        selected = settings.engine == engine,
                        onClick = null,
                    )
                },
                modifier = Modifier.clickable {
                    keySaved = false
                    container.appPreferences.updateWebSearchSettings { it.copy(engine = engine) }
                },
            )
        }
    }

    if (settings.engine.requiresApiKey) {
        SettingsGroup("${settings.engine.title} credential") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    keySaved = false
                },
                label = { Text(uiText("API key")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = {
                    container.secureStore.setSearchApiKey(settings.engine.name, apiKey)
                    keySaved = true
                }) {
                    Text(uiText(if (keySaved) "Saved" else "Save key"))
                }
            }
        }
    }

    if (settings.engine == app.xylune.chat.settings.WebSearchEngine.SEARXNG) {
        SettingsGroup("SearXNG endpoint") {
            OutlinedTextField(
                value = settings.searxngEndpoint,
                onValueChange = { value ->
                    container.appPreferences.updateWebSearchSettings {
                        it.copy(searxngEndpoint = value)
                    }
                },
                label = { Text(uiText("Public HTTPS base URL")) },
                supportingText = { Text(uiText("The instance must enable JSON search output.")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    SettingsGroup("Tool behavior") {
        var maxResultsText by remember(settings.maxResults) {
            mutableStateOf(settings.maxResults.toString())
        }
        OutlinedTextField(
            value = maxResultsText,
            onValueChange = { raw ->
                maxResultsText = raw.filter(Char::isDigit).take(2)
                maxResultsText.toIntOrNull()?.let { value ->
                    container.appPreferences.updateWebSearchSettings {
                        it.copy(maxResults = value)
                    }
                }
            },
            label = { Text(uiText("Maximum search results")) },
            supportingText = { Text(uiText("3–20 results per search call")) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        ListItem(
            headlineContent = { Text(uiText("Allow page fetching")) },
            supportingContent = {
                Text(uiText("Expose web_fetch so the model can read public HTTPS pages after searching."))
            },
            trailingContent = {
                Switch(
                    checked = settings.pageFetchEnabled,
                    onCheckedChange = { enabled ->
                        container.appPreferences.updateWebSearchSettings {
                            it.copy(pageFetchEnabled = enabled)
                        }
                    },
                )
            },
        )
    }

    Text(
        uiText("API keys are stored in Android encrypted preferences. Native-only mode never silently switches to a Xylune engine; Automatic mode does."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}
