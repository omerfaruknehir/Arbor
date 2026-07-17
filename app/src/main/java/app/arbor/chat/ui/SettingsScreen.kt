package app.arbor.chat.ui

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import app.arbor.chat.provider.DiscoveredModel
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.settings.NewChatDefaults
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import java.util.UUID

private enum class SettingsTab(val label: String) {
    CHAT("Chat"), GLOBAL("Global"), PROVIDERS("Providers")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val defaults by viewModel.newChatDefaults.collectAsStateWithLifecycle()
    val automation by viewModel.automationSettings.collectAsStateWithLifecycle()
    val contextSummary by viewModel.contextSummary.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val amoled by viewModel.amoled.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val renderSafeMode by viewModel.renderSafeMode.collectAsState()
    val registeredProviders = remember(providers, credentialRevision) { viewModel.registeredProviders(providers) }
    val configuredProviders = remember(providers, credentialRevision) { viewModel.configuredProviders(providers) }
    var tab by rememberSaveable { mutableStateOf(SettingsTab.CHAT) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { if (openDrawer != null) openDrawer() else viewModel.screen.value = Screen.CHAT }) {
                        Icon(if (openDrawer != null) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 12.dp) {
                SettingsTab.entries.forEach { option ->
                    Tab(
                        selected = tab == option,
                        onClick = { tab = option },
                        text = { Text(option.label) },
                    )
                }
            }
            when (tab) {
                SettingsTab.CHAT -> CurrentChatSettings(
                    conversation = conversation,
                    providers = configuredProviders,
                    contextSummary = contextSummary,
                    viewModel = viewModel,
                )
                SettingsTab.GLOBAL -> GlobalSettings(
                    defaults = defaults,
                    providers = configuredProviders,
                    automation = automation,
                    amoled = amoled,
                    palette = palette,
                    renderSafeMode = renderSafeMode,
                    viewModel = viewModel,
                )
                SettingsTab.PROVIDERS -> ProviderSettings(
                    providers = providers,
                    registeredProviders = registeredProviders,
                    conversationProviderId = conversation?.selectedProviderId,
                    viewModel = viewModel,
                )
            }
        }
    }
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
private fun CurrentChatSettings(
    conversation: app.arbor.chat.data.ConversationEntity?,
    providers: List<ProviderEntity>,
    contextSummary: app.arbor.chat.data.ContextSummaryEntity?,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("Current chat", "These values are stored on this chat. Your latest selections also become the starting defaults for future chats.")
    if (conversation == null) {
        Text("Open or create a conversation to edit chat settings.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return@SettingsPage
    }

    ChatOptionsEditor(
        providerId = conversation.selectedProviderId,
        modelId = conversation.selectedModelId,
        providers = providers,
        thinkingEnabled = conversation.thinkingEnabled,
        thinkingEffort = conversation.thinkingEffort,
        webEnabled = conversation.webSearchEnabled,
        pythonEnabled = conversation.agentPythonEnabled,
        linuxEnabled = conversation.agentUbuntuEnabled,
        contextPairs = conversation.contextPairs,
        contextTokenLimit = conversation.contextTokenLimit,
        workingTokenLimit = conversation.workingTokenLimit,
        maxOutputTokens = conversation.maxOutputTokens,
        reasoningVisibility = conversation.reasoningVisibility,
        systemPrompt = conversation.systemPrompt,
        viewModel = viewModel,
        onModel = { providerId, modelId -> viewModel.selectModel(providerId, modelId) },
        onThinkingEnabled = { enabled -> viewModel.updateConversation { it.copy(thinkingEnabled = enabled) } },
        onThinkingEffort = { effort -> viewModel.updateConversation { it.copy(thinkingEffort = effort) } },
        onWeb = { enabled -> viewModel.updateConversation { it.copy(webSearchEnabled = enabled) } },
        onPython = { enabled -> viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) } },
        onLinux = { enabled -> viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) } },
        onContextPairs = { value -> viewModel.updateConversation { it.copy(contextPairs = value) } },
        onContextLimit = { value -> viewModel.updateConversation { it.copy(contextTokenLimit = value) } },
        onWorkingLimit = { value -> viewModel.updateConversation { it.copy(workingTokenLimit = value) } },
        onOutputLimit = { value -> viewModel.updateConversation { it.copy(maxOutputTokens = value) } },
        onReasoningVisibility = { value -> viewModel.updateConversation { it.copy(reasoningVisibility = value) } },
        onSystemPrompt = { value -> viewModel.updateConversation { it.copy(systemPrompt = value) } },
    )

    HorizontalDivider()
    SectionTitle("Compressed context", "Saved summaries apply only to this chat.")
    contextSummary?.let { summary ->
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Compressed context active", fontWeight = FontWeight.SemiBold)
                Text("${summary.sourceMessageCount} older messages • about ${summary.tokenEstimate} tokens${summary.modelId?.let { " • $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = viewModel::compressContextNow, modifier = Modifier.weight(1f)) { Text("Compress now") }
        OutlinedButton(onClick = viewModel::clearContextSummary, modifier = Modifier.weight(1f)) { Text("Clear") }
    }
    OutlinedButton(onClick = viewModel::applyNewChatDefaultsToCurrent, modifier = Modifier.fillMaxWidth()) {
        Text("Reset this chat to new-chat defaults")
    }
    Spacer(Modifier.padding(bottom = 24.dp))
}

@Composable
private fun GlobalSettings(
    defaults: NewChatDefaults,
    providers: List<ProviderEntity>,
    automation: AutomationSettingsEntity,
    amoled: Boolean,
    palette: ColorPalette,
    renderSafeMode: Boolean,
    viewModel: ChatViewModel,
) = SettingsPage {
    SectionTitle("New chat defaults", "Used only when a new chat is created. Existing chats keep their own values.")
    ChatOptionsEditor(
        providerId = defaults.selectedProviderId,
        modelId = defaults.selectedModelId,
        providers = providers,
        thinkingEnabled = defaults.thinkingEnabled,
        thinkingEffort = defaults.thinkingEffort,
        webEnabled = defaults.webSearchEnabled,
        pythonEnabled = defaults.agentPythonEnabled,
        linuxEnabled = defaults.agentUbuntuEnabled,
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
        onWeb = { enabled -> viewModel.updateNewChatDefaults { it.copy(webSearchEnabled = enabled) } },
        onPython = { enabled -> viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) } },
        onLinux = { enabled -> viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) } },
        onContextPairs = { value -> viewModel.updateNewChatDefaults { it.copy(contextPairs = value) } },
        onContextLimit = { value -> viewModel.updateNewChatDefaults { it.copy(contextTokenLimit = value) } },
        onWorkingLimit = { value -> viewModel.updateNewChatDefaults { it.copy(workingTokenLimit = value) } },
        onOutputLimit = { value -> viewModel.updateNewChatDefaults { it.copy(maxOutputTokens = value) } },
        onReasoningVisibility = { value -> viewModel.updateNewChatDefaults { it.copy(reasoningVisibility = value) } },
        onSystemPrompt = { value -> viewModel.updateNewChatDefaults { it.copy(systemPrompt = value) } },
    )

    HorizontalDivider()
    SectionTitle("Automation models", "Chat naming and context compression are global services, separate from the active chat model.")
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
        subtitle = "Older messages outside the active pair/token window are merged into saved compact context.",
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

    HorizontalDivider()
    SectionTitle("Appearance & privacy", "App-wide display and renderer safety settings.")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPalette.entries.forEach { option ->
            AssistChip(
                onClick = { viewModel.setPalette(option) },
                label = { Text(when (option) { ColorPalette.ARBOR -> "Arbor"; ColorPalette.SYSTEM -> "System"; ColorPalette.GRAPHITE -> "Graphite" }) },
                leadingIcon = if (palette == option) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
    SettingsSwitch("AMOLED black", amoled, viewModel::setAmoled)
    SettingsSwitch("Safe generated rendering", renderSafeMode, viewModel::setRenderSafeMode)
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Security, null)
            Text("No account, ads, analytics, or Arbor cloud. Network traffic goes only to endpoints you configure.", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.padding(bottom = 24.dp))
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
    SectionTitle("Thinking", "The switch and effort level are sent with each request when the selected model supports them.")
    SettingsSwitch(
        label = "Thinking",
        checked = thinkingEnabled,
        onCheckedChange = onThinkingEnabled,
        enabled = activeModel?.supportsThinking != false,
    )
    if (activeModel?.supportsThinking == false) {
        Text("This model does not advertise thinking support.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (thinkingEnabled) {
        Text("Effort: ${thinkingEffort.displayName}", fontWeight = FontWeight.SemiBold)
        Slider(
            value = thinkingEffort.ordinal.toFloat(),
            onValueChange = { raw -> onThinkingEffort(ThinkingEffort.entries[raw.toInt().coerceIn(0, ThinkingEffort.entries.lastIndex)]) },
            valueRange = 0f..ThinkingEffort.entries.lastIndex.toFloat(),
            steps = ThinkingEffort.entries.size - 2,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Minimal", style = MaterialTheme.typography.labelSmall)
            Text("High", style = MaterialTheme.typography.labelSmall)
        }
        Text("Some providers do not allow thinking to be fully disabled on every model; Arbor requests off where the API supports it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    HorizontalDivider()
    SectionTitle("Tools", "Simple per-chat permissions. Disabled tools are not offered to the model.")
    SettingsSwitch("Web", webEnabled, onWeb)
    SettingsSwitch("Python", pythonEnabled, onPython)
    SettingsSwitch("Linux", linuxEnabled, onLinux)

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
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private val ReasoningVisibility.shortLabel: String
    get() = when (this) {
        ReasoningVisibility.ALWAYS -> "Expanded"
        ReasoningVisibility.SHOW_WHILE_WORKING -> "While working"
        ReasoningVisibility.COLLAPSED -> "Collapsed"
    }

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
            Column(Modifier.weight(1f)) { SectionTitle("Providers & BYOK", "Keys stay encrypted by Android Keystore.") }
            FilledTonalButton(onClick = { addingProvider = true }) {
                Icon(Icons.Outlined.Add, null)
                Text("Add", Modifier.padding(start = 6.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            registeredProviders.forEach { provider ->
                Surface(
                    onClick = { selectedId = provider.id },
                    color = if (provider.id == selected?.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                            Text("${providerKindLabel(provider.kind)} • ${provider.baseUrl}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (provider.id == conversationProviderId) Icon(Icons.Outlined.CheckCircle, "In use", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (registeredProviders.isEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No providers added", fontWeight = FontWeight.SemiBold)
                        Text("Tap Add, choose the API protocol, and let Arbor fetch the provider's model catalog.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
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
            }
            OutlinedButton(onClick = { viewModel.useProvider(provider.id) }, modifier = Modifier.fillMaxWidth()) { Text("Use ${provider.displayName} in current chat") }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        syncingModels = true
                        modelSyncStatus = null
                        runCatching { viewModel.discoverModels(provider.kind, baseUrl, apiKey, headers) }
                            .onSuccess { discovered ->
                                viewModel.saveDiscoveredModels(provider.id, discovered)
                                modelSyncStatus = "Updated ${discovered.size} models from ${provider.displayName}"
                            }
                            .onFailure { modelSyncStatus = it.message?.take(1_000) ?: "Model refresh failed" }
                        syncingModels = false
                    }
                },
                enabled = !syncingModels && baseUrl.isNotBlank() && (!apiKeyRequired || apiKey.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (syncingModels) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Outlined.Refresh, null)
                Text(if (syncingModels) " Refreshing…" else " Refresh model list")
            }
            modelSyncStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            ModelCatalogEditor(provider, viewModel)
            OutlinedButton(onClick = { removingProvider = provider }, modifier = Modifier.fillMaxWidth()) {
                Text("Remove provider from Arbor", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Provider details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(providerKindLabel(provider.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(name, onName, label = { Text("Provider name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(baseUrl, onBaseUrl, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            key, onKey,
            label = { Text(if (apiKeyRequired) "API key" else "API key (optional)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Require API key")
                Text("Turn off for a trusted local or keyless endpoint", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = apiKeyRequired, onCheckedChange = onApiKeyRequired)
        }
        OutlinedTextField(headers, onHeaders, label = { Text("Custom headers JSON") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Button(onClick = onSave, enabled = name.isNotBlank() && baseUrl.isNotBlank() && (!apiKeyRequired || key.isNotBlank()), modifier = Modifier.align(Alignment.End)) { Text("Save provider") }
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

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Context, output limits, capabilities, and pricing are editable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { creating = true }) { Icon(Icons.Outlined.Add, "Add model") }
            }
            models.forEach { model ->
                Surface(onClick = { editing = model }, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model.displayName, fontWeight = FontWeight.Medium)
                            Text("${model.modelId} • ${model.contextWindow / 1_000}K context • ${model.maxOutputTokens / 1_000}K output • ${if (model.pricingConfigured) "pricing configured" else "cost unavailable"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.Edit, null, Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (creating) ModelEditorDialog(
        title = "Add model",
        initial = ModelEntity(provider.id, "", "", 128_000, 16_384, 0.0, 0.0, 0.0),
        allowIdEdit = true,
        onDismiss = { creating = false },
        onSave = { viewModel.saveModel(it); creating = false },
    )
    editing?.let { model ->
        ModelEditorDialog(
            title = "Edit model",
            initial = model,
            allowIdEdit = false,
            onDismiss = { editing = null },
            onSave = { viewModel.saveModel(it); editing = null },
        )
    }
}

@Composable
private fun ModelEditorDialog(
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
    val pricesValid = !pricingConfigured || listOf(cacheHit, cacheMiss, outputPrice).all { it.toDoubleOrNull()?.let { price -> price >= 0.0 } == true }
    val valid = id.isNotBlank() && name.isNotBlank() && context.toIntOrNull() != null && output.toIntOrNull() != null && pricesValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it.trim() }, label = { Text("API model ID") }, enabled = allowIdEdit, singleLine = true)
                OutlinedTextField(name, { name = it }, label = { Text("Display name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(context, { context = it.filter(Char::isDigit) }, label = { Text("Context") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(output, { output = it.filter(Char::isDigit) }, label = { Text("Max output") }, modifier = Modifier.weight(1f))
                }
                CapabilitySwitch("Pricing configured", pricingConfigured) { pricingConfigured = it }
                Text(if (pricingConfigured) "USD per million tokens" else "Arbor will show cost as unavailable instead of treating this model as free.", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(cacheHit, { cacheHit = it }, label = { Text("Cache-hit input") }, enabled = pricingConfigured)
                OutlinedTextField(cacheMiss, { cacheMiss = it }, label = { Text("Cache-miss input") }, enabled = pricingConfigured)
                OutlinedTextField(outputPrice, { outputPrice = it }, label = { Text("Output") }, enabled = pricingConfigured)
                CapabilitySwitch("Vision", vision) { vision = it }
                CapabilitySwitch("Files", files) { files = it }
                CapabilitySwitch("Thinking", thinking) { thinking = it }
                CapabilitySwitch("Tools", tools) { tools = it }
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(
                enabled = valid,
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
        },
    )
}

@Composable
private fun CapabilitySwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
