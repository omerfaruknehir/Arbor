package app.arbor.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import app.arbor.chat.provider.supportedThinkingLevels
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.settings.NewChatDefaults
import app.arbor.chat.settings.ThemeMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.util.UUID

private enum class SettingsRoute(val title: String) {
    HOME("Settings"),
    DEFAULTS("New chat defaults"),
    AUTOMATION("Automation"),
    APPEARANCE("Appearance"),
    PRIVACY("Privacy & safety"),
    LOCAL_EXECUTION("Local Code Execution"),
    SYSTEM_PROMPTS("System prompts"),
    PROVIDERS("Providers & models"),
    ABOUT("About"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val defaults by viewModel.newChatDefaults.collectAsStateWithLifecycle()
    val automation by viewModel.automationSettings.collectAsStateWithLifecycle()
    val promptProfiles by viewModel.systemPromptProfiles.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val renderSafeMode by viewModel.renderSafeMode.collectAsState()
    val registeredProviders = remember(providers, credentialRevision) { viewModel.registeredProviders(providers) }
    val configuredProviders = remember(providers, credentialRevision) { viewModel.configuredProviders(providers) }
    var route by rememberSaveable { mutableStateOf(SettingsRoute.HOME) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollapsingTranslucentTopBar(
                title = route.title,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = {
                        if (route != SettingsRoute.HOME) route = SettingsRoute.HOME
                        else if (openDrawer != null) openDrawer()
                        else viewModel.screen.value = Screen.CHAT
                    }) {
                        Icon(
                            if (route == SettingsRoute.HOME && openDrawer != null) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack,
                            "Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                SettingsRoute.HOME -> SettingsHome(
                    providerCount = registeredProviders.size,
                    onOpen = { route = it },
                )
                SettingsRoute.DEFAULTS -> NewChatDefaultsSettings(defaults, configuredProviders, viewModel)
                SettingsRoute.AUTOMATION -> AutomationSettingsPage(automation, configuredProviders, viewModel)
                SettingsRoute.APPEARANCE -> AppearanceSettingsPage(themeMode, amoled, palette, viewModel)
                SettingsRoute.PRIVACY -> PrivacySettingsPage(renderSafeMode, viewModel)
                SettingsRoute.LOCAL_EXECUTION -> LocalCodeExecutionSettingsPage(defaults, automation, configuredProviders, viewModel)
                SettingsRoute.SYSTEM_PROMPTS -> SystemPromptProfilesPage(promptProfiles, defaults.systemPromptProfileId, viewModel)
                SettingsRoute.PROVIDERS -> ProviderSettings(
                    providers = providers,
                    registeredProviders = registeredProviders,
                    conversationProviderId = null,
                    viewModel = viewModel,
                )
                SettingsRoute.ABOUT -> AboutSettingsPage()
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
            title = "System prompts",
            subtitle = "Reusable prepend and override profiles",
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
            title = "Local Code Execution",
            subtitle = "Python, Linux tooling, packages, and workspace",
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
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
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
        pythonEnabled = defaults.agentPythonEnabled,
        linuxEnabled = defaults.agentUbuntuEnabled,
        deepResearchEnabled = defaults.deepResearchEnabled,
        hybridTokenCountingEnabled = defaults.hybridTokenCountingEnabled,
        contextPairs = defaults.contextPairs,
        contextTokenLimit = defaults.contextTokenLimit,
        workingTokenLimit = defaults.workingTokenLimit,
        maxOutputTokens = defaults.maxOutputTokens,
        reasoningVisibility = defaults.reasoningVisibility,
        systemPrompt = defaults.systemPrompt,
        viewModel = viewModel,
        onModel = { providerId, modelId -> viewModel.updateNewChatDefaults { it.copy(selectedProviderId = providerId, selectedModelId = modelId) } },
        onThinkingEnabled = { enabled -> viewModel.updateNewChatDefaults { it.copy(thinkingEnabled = enabled) } },
        onThinkingEffort = { effort -> viewModel.updateNewChatDefaults { it.copy(thinkingEffort = effort) } },
        onWeb = { enabled -> viewModel.updateNewChatDefaults { it.copy(webSearchEnabled = enabled, deepResearchEnabled = it.deepResearchEnabled && enabled) } },
        onPython = { enabled -> viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) } },
        onLinux = { enabled -> viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) } },
        onDeepResearch = { enabled -> viewModel.updateNewChatDefaults { it.copy(deepResearchEnabled = enabled, webSearchEnabled = it.webSearchEnabled || enabled) } },
        onHybridTokenCounting = { enabled -> viewModel.updateNewChatDefaults { it.copy(hybridTokenCountingEnabled = enabled) } },
        onContextPairs = { value -> viewModel.updateNewChatDefaults { it.copy(contextPairs = value) } },
        onContextLimit = { value -> viewModel.updateNewChatDefaults { it.copy(contextTokenLimit = value) } },
        onWorkingLimit = { value -> viewModel.updateNewChatDefaults { it.copy(workingTokenLimit = value) } },
        onOutputLimit = { value -> viewModel.updateNewChatDefaults { it.copy(maxOutputTokens = value) } },
        onReasoningVisibility = { value -> viewModel.updateNewChatDefaults { it.copy(reasoningVisibility = value) } },
        onSystemPrompt = { value -> viewModel.updateNewChatDefaults { it.copy(systemPrompt = value) } },
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
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("Theme mode", "Choose whether Arbor follows Android or stays light or dark.")
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
    SectionTitle("Color scheme", "Use Arbor green, a neutral graphite palette, or Android dynamic colors.")
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
                        Text(option.displayName, fontWeight = FontWeight.SemiBold)
                        Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (palette == option) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    SettingsSwitch("AMOLED black", amoled, viewModel::setAmoled, enabled = themeMode != ThemeMode.LIGHT)
    Text("AMOLED black only changes dark mode surfaces.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun PrivacySettingsPage(
    renderSafeMode: Boolean,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("Generated content", "Controls how Arbor handles AI-generated interactive UI.")
    SettingsSwitch("Safe generated rendering", renderSafeMode, viewModel::setRenderSafeMode)
    Text(
        if (renderSafeMode) "Generated widgets are paused and shown as safe fallback content." else "Generated widgets may render, but Arbor still applies its capability checks and crash recovery.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        "Reusable prompts",
        "Prepend adds instructions before Arbor's built-in capability prompt. Override replaces only Arbor's default persona; runtime, tool, date, and safety instructions remain active.",
    )
    FilledTonalButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, null)
        Text("New system prompt", Modifier.padding(start = 8.dp))
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
                            if (profile.mode == SystemPromptMode.OVERRIDE) "Override Arbor persona" else "Prepend to Arbor persona",
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
        title = "New system prompt",
        initial = null,
        onDismiss = { creating = false },
        onSave = { name, prompt, mode -> viewModel.createSystemPromptProfile(name, prompt, mode); creating = false },
    )
    editing?.let { profile ->
        SystemPromptEditorDialog(
            title = "Edit system prompt",
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
        "Local execution defaults",
        "These settings apply to newly created chats. Existing chats keep their own tool permissions.",
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
        "Local Python runs from a per-chat virtual environment inside the selected Linux distribution. Arbor and the AI execute as root (uid 0) inside that PRoot distribution; Android still confines the app outside it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = { viewModel.screen.value = Screen.SANDBOX },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Code, null)
        Text("Open package manager & Python workspace", Modifier.padding(start = 8.dp))
    }
    OutlinedButton(
        onClick = { viewModel.screen.value = Screen.TERMINAL },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.Tune, null)
        Text("Open advanced root terminal", Modifier.padding(start = 8.dp))
    }
    SectionTitle(
        "Package installation",
        "Choose when Arbor may install Python or Linux packages and which sources are trusted.",
    )
    PackageApprovalEditor(automation, providers, viewModel)
}

@Composable
private fun AboutSettingsPage() = SettingsPage {
    SectionTitle("Arbor 0.16.2", "Native Android BYOK model workspace.")
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Built for long-running, tool-using chats", fontWeight = FontWeight.SemiBold)
            Text("On-device encrypted history, provider-native tool calls, persistent local code/Linux workspaces, Deep Research, and per-chat generation controls.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Debug builds use Android's debug certificate and are not production releases.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.padding(bottom = 24.dp))
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
    pythonEnabled: Boolean,
    linuxEnabled: Boolean,
    deepResearchEnabled: Boolean,
    hybridTokenCountingEnabled: Boolean,
    contextPairs: Int,
    contextTokenLimit: Int,
    workingTokenLimit: Int,
    maxOutputTokens: Int,
    reasoningVisibility: ReasoningVisibility,
    systemPrompt: String,
    viewModel: ChatViewModel,
    onModel: (String, String) -> Unit,
    onThinkingEnabled: (Boolean) -> Unit,
    onThinkingEffort: (ThinkingEffort) -> Unit,
    onWeb: (Boolean) -> Unit,
    onPython: (Boolean) -> Unit,
    onLinux: (Boolean) -> Unit,
    onDeepResearch: (Boolean) -> Unit,
    onHybridTokenCounting: (Boolean) -> Unit,
    onContextPairs: (Int) -> Unit,
    onContextLimit: (Int) -> Unit,
    onWorkingLimit: (Int) -> Unit,
    onOutputLimit: (Int) -> Unit,
    onReasoningVisibility: (ReasoningVisibility) -> Unit,
    onSystemPrompt: (String) -> Unit,
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
    SettingsSwitch("Local Code Execution", pythonEnabled, onPython)
    SettingsSwitch("Linux", linuxEnabled, onLinux)

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
    SectionTitle("System prompt", "Stored with this settings profile.")
    OutlinedTextField(
        value = systemPrompt,
        onValueChange = onSystemPrompt,
        label = { Text("System prompt") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
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
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
            DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
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
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
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
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
    viewModel: ChatViewModel,
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var addingProvider by remember { mutableStateOf(false) }
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

    SettingsPage {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Providers", "Choose a provider, then manage its connection and models.")
            }
            FilledTonalButton(onClick = { addingProvider = true }) {
                Icon(Icons.Outlined.Add, null)
                Text("Add", Modifier.padding(start = 6.dp))
            }
        }

        if (registeredProviders.isEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text("No providers yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Add DeepSeek, OpenAI, Anthropic, Gemini, OpenRouter, or a compatible local endpoint.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { addingProvider = true }) { Text("Add provider") }
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
                                Text(providerKindLabel(provider.kind), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (provider.id == conversationProviderId) Text("In use", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (provider.id == selected?.id) Icon(Icons.Outlined.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        selected?.let { provider ->
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
            text = { Text("Its saved API key will be erased and it will disappear from model selectors. Chats and usage history are kept.") },
            dismissButton = { OutlinedButton(onClick = { removingProvider = null }) { Text("Cancel") } },
            confirmButton = { Button(onClick = { viewModel.removeProvider(provider); removingProvider = null }) { Text("Remove provider") } },
        )
    }

    if (addingProvider) AddProviderDialog(
        templates = providers.filter { provider -> provider !in registeredProviders },
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
                DefaultCatalog.models.firstOrNull { it.providerId == id && it.modelId == candidate.id } ?: ModelEntity(
                    providerId = id, modelId = candidate.id, displayName = candidate.displayName,
                    contextWindow = candidate.contextWindow ?: 128_000,
                    maxOutputTokens = candidate.maxOutputTokens ?: 16_384,
                    inputCacheHitUsdPerMillion = 0.0, inputCacheMissUsdPerMillion = 0.0, outputUsdPerMillion = 0.0,
                    supportsThinking = candidate.supportsThinking ?: false,
                )
            }
            viewModel.addProvider(provider, draft.apiKey, models)
            selectedId = id
            addingProvider = false
        },
    )
}

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
        if (advanced) OutlinedTextField(headers, onHeaders, label = { Text("Custom headers JSON") }, minLines = 3, modifier = Modifier.fillMaxWidth())
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
                    DropdownMenu(expanded = templateMenu, onDismissRequest = { templateMenu = false }) {
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
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        ProviderKind.entries.forEach { option ->
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
                OutlinedTextField(headers, { headers = it; invalidateDiscovery() }, label = { Text("Custom headers JSON") }, minLines = 2, modifier = Modifier.fillMaxWidth())
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
    ProviderKind.ANTHROPIC -> "Anthropic Messages"
    ProviderKind.GEMINI -> "Google Gemini"
}

private fun defaultBaseUrl(kind: ProviderKind): String = when (kind) {
    ProviderKind.OPENAI_COMPATIBLE -> "https://api.openai.com/v1"
    ProviderKind.ANTHROPIC -> "https://api.anthropic.com/v1"
    ProviderKind.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
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
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
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
                        DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
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
                        DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
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
        initial = ModelEntity(provider.id, "", "", 128_000, 16_384, 0.0, 0.0, 0.0),
        allowIdEdit = true,
        onDismiss = { creating = false },
        onSave = { viewModel.saveModel(it); creating = false },
    )
    editing?.let { model ->
        ModelEditorSheet(
            title = "Edit model",
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
        if (!pricingConfigured) add("Cost unavailable")
    }.joinToString(" · ")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelEditorSheet(
    title: String,
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

            Text("Capabilities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = thinking, onClick = { thinking = !thinking }, label = { Text("Thinking") }, modifier = Modifier.weight(1f))
                FilterChip(selected = tools, onClick = { tools = !tools }, label = { Text("Tools") }, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = vision, onClick = { vision = !vision }, label = { Text("Vision") }, modifier = Modifier.weight(1f))
                FilterChip(selected = files, onClick = { files = !files }, label = { Text("Files") }, modifier = Modifier.weight(1f))
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
                        ))
                    },
                ) { Text("Save") }
            }
            Spacer(Modifier.size(28.dp))
        }
    }
}

