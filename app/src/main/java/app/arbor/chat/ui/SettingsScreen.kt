package app.arbor.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.arbor.chat.data.AuxiliaryMode
import app.arbor.chat.data.AutomationSettingsEntity
import app.arbor.chat.data.PackageApprovalMode
import app.arbor.chat.provider.DiscoveredModel
import app.arbor.chat.settings.ColorPalette
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<String?>(null) }
    var addingProvider by remember { mutableStateOf(false) }
    var removingProvider by remember { mutableStateOf<ProviderEntity?>(null) }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("{}") }
    var providerName by remember { mutableStateOf("") }
    var apiKeyRequired by remember { mutableStateOf(true) }
    val amoled by viewModel.amoled.collectAsState()
    val palette by viewModel.palette.collectAsState()
    val renderSafeMode by viewModel.renderSafeMode.collectAsState()
    val automation by viewModel.automationSettings.collectAsStateWithLifecycle()
    val contextSummary by viewModel.contextSummary.collectAsStateWithLifecycle()
    val credentialRevision by viewModel.credentialRevision.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var syncingModels by remember { mutableStateOf(false) }
    var modelSyncStatus by remember { mutableStateOf<String?>(null) }
    val registeredProviders = remember(providers, credentialRevision) { viewModel.registeredProviders(providers) }
    val configuredProviders = remember(providers, credentialRevision) { viewModel.configuredProviders(providers) }
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
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Providers & BYOK", "Add only the services you actually use. Keys stay encrypted by Android Keystore.")
                }
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
                            if (provider.id == conversation?.selectedProviderId) Icon(Icons.Outlined.CheckCircle, "In use", tint = MaterialTheme.colorScheme.primary)
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
                OutlinedButton(onClick = { viewModel.useProvider(provider.id) }, modifier = Modifier.fillMaxWidth()) { Text("Use ${provider.displayName} in this conversation") }
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

            HorizontalDivider()
            SectionTitle("Automation models", "Only registered providers which are currently usable appear here. The main chat model is unaffected.")
            if (configuredProviders.isEmpty()) {
                Text("Add a provider with valid credentials, or explicitly register a keyless local endpoint, to enable model-based automation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            AutomationPolicyEditor(
                title = "Chat naming",
                subtitle = "Model mode considers newer messages whenever a name is regenerated.",
                mode = automation.titleMode,
                providerId = automation.titleProviderId,
                modelId = automation.titleModelId,
                providers = configuredProviders,
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
                providers = configuredProviders,
                viewModel = viewModel,
                onChange = { mode, providerId, modelId ->
                    viewModel.updateAutomationSettings { it.copy(compressionMode = mode, compressionProviderId = providerId, compressionModelId = modelId) }
                },
            )
            contextSummary?.let { summary ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Compressed context active", fontWeight = FontWeight.SemiBold)
                        Text("${summary.sourceMessageCount} older messages • about ${summary.tokenEstimate} tokens${summary.modelId?.let { " • $it" }.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::compressContextNow, modifier = Modifier.weight(1f)) {
                    Text("Compress now")
                }
                OutlinedButton(onClick = viewModel::clearContextSummary, modifier = Modifier.weight(1f)) {
                    Text("Clear summary")
                }
            }

            HorizontalDivider()
            SectionTitle("Agent tools · current chat", "Web search, fast embedded Python, and Linux-tool permissions are saved for this chat and inherited by new chats.")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Allow web search"); Text("The model may send search queries to DuckDuckGo", style = MaterialTheme.typography.bodySmall) }
                Switch(
                    checked = conversation?.webSearchEnabled == true,
                    onCheckedChange = { enabled -> viewModel.updateConversation { it.copy(webSearchEnabled = enabled) } },
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Allow agent Linux tools"); Text("The model may use the selected installed distribution in /workspace", style = MaterialTheme.typography.bodySmall) }
                Switch(
                    checked = conversation?.agentUbuntuEnabled == true,
                    onCheckedChange = { enabled -> viewModel.updateConversation { it.copy(agentUbuntuEnabled = enabled) } },
                )
            }

            HorizontalDivider()
            SectionTitle("Package approval", "One policy controls pip, apt, and apk. Every request is preflighted first, and already-satisfied packages are never offered for installation.")
            PackageApprovalEditor(automation, configuredProviders, viewModel)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Allow agent Python"); Text("The model may execute code in the app process", style = MaterialTheme.typography.bodySmall) }
                Switch(
                    checked = conversation?.agentPythonEnabled == true,
                    onCheckedChange = { enabled -> viewModel.updateConversation { it.copy(agentPythonEnabled = enabled) } },
                )
            }

            HorizontalDivider()
            SectionTitle("Working & reasoning", "Working cards always stay in the transcript. Choose when they expand automatically.")
            ReasoningVisibility.entries.forEach { option ->
                val label = when (option) {
                    ReasoningVisibility.ALWAYS -> "Always expanded"
                    ReasoningVisibility.SHOW_WHILE_WORKING -> "Expanded while working"
                    ReasoningVisibility.COLLAPSED -> "Always collapsed"
                }
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.updateConversation { it.copy(reasoningVisibility = option) } },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = conversation?.reasoningVisibility == option,
                        onClick = { viewModel.updateConversation { it.copy(reasoningVisibility = option) } },
                    )
                    Text(label)
                }
            }

            HorizontalDivider()
            SectionTitle("Context & output · current chat", "Saved for this chat and inherited by new chats. A pair is one request plus its answer. Working history has an independent budget but still fits inside the overall context ceiling.")
            OutlinedTextField(
                value = conversation?.contextPairs?.toString().orEmpty(),
                onValueChange = { raw -> raw.toIntOrNull()?.coerceIn(1, 500)?.let { value -> viewModel.updateConversation { it.copy(contextPairs = value) } } },
                label = { Text("Last message pairs") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = conversation?.contextTokenLimit?.toString().orEmpty(),
                onValueChange = { raw -> raw.toIntOrNull()?.coerceIn(1_024, 2_000_000)?.let { value -> viewModel.updateConversation { it.copy(contextTokenLimit = value) } } },
                label = { Text("Context token ceiling") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = conversation?.workingTokenLimit?.toString().orEmpty(),
                onValueChange = { raw -> raw.toIntOrNull()?.coerceIn(0, 2_000_000)?.let { value -> viewModel.updateConversation { it.copy(workingTokenLimit = value) } } },
                label = { Text("Working history token budget") },
                supportingText = { Text("Older reasoning and tool traces only; interrupted responses are preserved for resume") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = conversation?.maxOutputTokens?.toString().orEmpty(),
                onValueChange = { raw -> raw.toIntOrNull()?.coerceIn(1, 384_000)?.let { value -> viewModel.updateConversation { it.copy(maxOutputTokens = value) } } },
                label = { Text("Maximum output tokens") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = conversation?.systemPrompt.orEmpty(),
                onValueChange = { value -> viewModel.updateConversation { it.copy(systemPrompt = value) } },
                label = { Text("Conversation system prompt") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionTitle("Appearance & privacy", "Choose Arbor's calm palette, your phone's Material You colors, or a neutral graphite theme.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorPalette.entries.forEach { option ->
                    AssistChip(
                        onClick = { viewModel.setPalette(option) },
                        label = { Text(when (option) { ColorPalette.ARBOR -> "Arbor"; ColorPalette.SYSTEM -> "System"; ColorPalette.GRAPHITE -> "Graphite" }) },
                        leadingIcon = if (palette == option) ({ Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("AMOLED black"); Text("Pure black surfaces in dark mode", style = MaterialTheme.typography.bodySmall) }
                Switch(checked = amoled, onCheckedChange = viewModel::setAmoled)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Safe generated rendering")
                    Text("Pause AI-generated widgets after a renderer crash without deleting the chat", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = renderSafeMode, onCheckedChange = viewModel::setRenderSafeMode)
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, null)
                    Text("No account, ads, analytics, or Arbor cloud. Network traffic goes only to endpoints you configure.", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
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
