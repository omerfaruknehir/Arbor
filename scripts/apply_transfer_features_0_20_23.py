from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match in {path}, found {count}")
    file.write_text(text.replace(old, new, 1))


def insert_after(path: str, anchor: str, addition: str, label: str) -> None:
    replace_once(path, anchor, anchor + addition, label)


# Room query needed by full portable backups.
insert_after(
    "app/src/main/java/app/arbor/chat/data/Daos.kt",
    '    @Query("SELECT * FROM conversations WHERE id = :id")\n    suspend fun get(id: String): ConversationEntity?\n',
    '\n    @Query("SELECT * FROM conversations ORDER BY createdAt, id")\n    suspend fun all(): List<ConversationEntity>\n',
    "conversation backup query",
)

# Register the archive manager in the application container.
insert_after(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    "import app.arbor.chat.settings.PersistentUiStateStore\n",
    "import app.arbor.chat.transfer.ArborArchiveManager\n",
    "archive manager import",
)
insert_after(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    "    val attachmentStore = AttachmentStore(application, database.attachmentDao())\n",
    "    val archiveManager = ArborArchiveManager(application, database)\n",
    "archive manager container",
)

# Add backup route.
replace_once(
    "app/src/main/java/app/arbor/chat/ui/SettingsRoute.kt",
    '    PRIVACY("Privacy & safety"),\n    LOCAL_EXECUTION("Local tools"),',
    '    PRIVACY("Privacy & safety"),\n    BACKUP("Backup & transfer"),\n    LOCAL_EXECUTION("Local tools"),',
    "backup settings route",
)

# Settings page wiring and provider model multi-select.
settings = "app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt"
insert_after(settings, "import androidx.compose.material3.Button\n", "import androidx.compose.material3.Checkbox\n", "checkbox import")
replace_once(
    settings,
    "                        SettingsRoute.PRIVACY -> PrivacySettingsPage(renderSafeMode, generatedRepairMaxAttempts, viewModel)\n                        SettingsRoute.LOCAL_EXECUTION -> LocalCodeExecutionSettingsPage(defaults, automation, configuredProviders, viewModel)",
    "                        SettingsRoute.PRIVACY -> PrivacySettingsPage(renderSafeMode, generatedRepairMaxAttempts, viewModel)\n                        SettingsRoute.BACKUP -> BackupSettingsPage(viewModel)\n                        SettingsRoute.LOCAL_EXECUTION -> LocalCodeExecutionSettingsPage(defaults, automation, configuredProviders, viewModel)",
    "backup page route wiring",
)
replace_once(
    settings,
    '''        SettingsDestination(
            icon = Icons.Outlined.PrivacyTip,
            title = "Privacy & safety",
            subtitle = "Generated UI safety and local-data behavior",
            onClick = { onOpen(SettingsRoute.PRIVACY) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Code,''',
    '''        SettingsDestination(
            icon = Icons.Outlined.PrivacyTip,
            title = "Privacy & safety",
            subtitle = "Generated UI safety and local-data behavior",
            onClick = { onOpen(SettingsRoute.PRIVACY) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Cloud,
            title = "Backup & transfer",
            subtitle = "Cloud/file backups and portable chat archives",
            onClick = { onOpen(SettingsRoute.BACKUP) },
        )
        SettingsDestination(
            icon = Icons.Outlined.Code,''',
    "backup settings destination",
)

replace_once(
    settings,
    '''private data class ProviderDraft(
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
)''',
    '''private data class ProviderDraft(
    val templateProviderId: String?,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String,
    val apiKeyRequired: Boolean,
    val headers: String,
    val selectedModels: List<DiscoveredModel>,
)''',
    "provider draft selected models",
)
replace_once(
    settings,
    '''    var modelId by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var discoveredModels by remember { mutableStateOf<List<DiscoveredModel>>(emptyList()) }''',
    '''    var manualModelId by remember { mutableStateOf("") }
    var manualModelName by remember { mutableStateOf("") }
    var discoveredModels by remember { mutableStateOf<List<DiscoveredModel>>(emptyList()) }
    var selectedModelIds by remember { mutableStateOf<Set<String>>(emptySet()) }''',
    "provider multi-select state",
)
replace_once(
    settings,
    '''    val connectionReady = baseUrl.isNotBlank() && (!apiKeyRequired || apiKey.isNotBlank())
    val valid = name.isNotBlank() && connectionReady && modelId.isNotBlank() && modelName.isNotBlank()''',
    '''    val connectionReady = baseUrl.isNotBlank() && (!apiKeyRequired || apiKey.isNotBlank())
    val manualModelReady = showManualModel && manualModelId.isNotBlank() && manualModelName.isNotBlank()
    val valid = name.isNotBlank() && connectionReady && (selectedModelIds.isNotEmpty() || manualModelReady)''',
    "provider multi-select validity",
)
replace_once(
    settings,
    '''        modelId = ""
        modelName = ""''',
    '''        selectedModelIds = emptySet()
        manualModelId = ""
        manualModelName = ""''',
    "provider discovery invalidation",
)
replace_once(
    settings,
    '''                                    val preferred = models.firstOrNull { candidate ->
                                        DefaultCatalog.models.any { it.providerId == templateId && it.modelId == candidate.id }
                                    } ?: models.firstOrNull()
                                    modelId = preferred?.id.orEmpty()
                                    modelName = preferred?.displayName.orEmpty()
                                    showManualModel = false''',
    '''                                    selectedModelIds = models.mapTo(linkedSetOf()) { it.id }
                                    manualModelId = ""
                                    manualModelName = ""
                                    showManualModel = false''',
    "select all discovered models",
)
replace_once(
    settings,
    '''                    Column(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
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
                    }''',
    '''                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${selectedModelIds.size} of ${discoveredModels.size} selected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Row {
                            TextButton(onClick = { selectedModelIds = discoveredModels.mapTo(linkedSetOf()) { it.id } }) { Text("Select all") }
                            TextButton(onClick = { selectedModelIds = emptySet() }) { Text("Clear") }
                        }
                    }
                    Column(Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
                        visibleModels.forEach { model ->
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
                                }
                            }
                        }
                    }''',
    "provider model checkbox list",
)
replace_once(
    settings,
    '''                        bundled.forEach { model ->
                            AssistChip(onClick = { modelId = model.modelId; modelName = model.displayName }, label = { Text(model.displayName) })
                        }
                    }
                    OutlinedTextField(modelId, { modelId = it.trim() }, label = { Text("API model ID") }, placeholder = { Text("deepseek-chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(modelName, { modelName = it }, label = { Text("Model display name") }, placeholder = { Text("DeepSeek Chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (modelId.isNotBlank()) Text("Selected: $modelName", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)''',
    '''                        bundled.forEach { model ->
                            AssistChip(onClick = { manualModelId = model.modelId; manualModelName = model.displayName }, label = { Text(model.displayName) })
                        }
                    }
                    OutlinedTextField(manualModelId, { manualModelId = it.trim() }, label = { Text("API model ID") }, placeholder = { Text("deepseek-chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(manualModelName, { manualModelName = it }, label = { Text("Model display name") }, placeholder = { Text("DeepSeek Chat") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (manualModelReady) Text("Manual model will also be included", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (selectedModelIds.isNotEmpty()) Text("Only the selected provider models will be saved.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)''',
    "manual model and selection summary",
)
replace_once(
    settings,
    '''                onClick = {
                    onAdd(ProviderDraft(templateId, name.trim(), kind, baseUrl.trim(), apiKey, apiKeyRequired, headers.ifBlank { "{}" }, modelId, modelName.trim(), discoveredModels))
                },''',
    '''                onClick = {
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
                },''',
    "provider draft creation",
)
replace_once(
    settings,
    '''            val discovered = draft.discoveredModels.ifEmpty { listOf(DiscoveredModel(draft.modelId, draft.modelName)) }
            val models = discovered.map { candidate ->''',
    '''            val models = draft.selectedModels.map { candidate ->''',
    "save only selected models",
)

# ViewModel transfer state and operations.
vm = "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt"
insert_after(
    vm,
    "import app.arbor.chat.settings.PersistentUiStateStore\n",
    "import app.arbor.chat.transfer.ArchiveOptions\nimport app.arbor.chat.transfer.ArchivePasswordRequiredException\nimport app.arbor.chat.transfer.IncomingArchiveState\n",
    "transfer viewmodel imports",
)
insert_after(
    vm,
    "    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)\n",
    "    val shareConversationId = MutableStateFlow<String?>(null)\n    val incomingArchive = MutableStateFlow<IncomingArchiveState?>(null)\n",
    "transfer viewmodel state",
)
insert_after(
    vm,
    "    fun setRenderSafeMode(enabled: Boolean) = container.crashReporter.setRenderSafeMode(enabled)\n",
    '''

    fun postNotice(message: String) {
        notices.tryEmit(message)
    }

    fun requestShareConversation(conversationId: String) {
        shareConversationId.value = conversationId
    }

    fun dismissShareConversation() {
        shareConversationId.value = null
    }

    suspend fun createPortableChatShare(
        conversationId: String,
        options: ArchiveOptions,
        password: String,
    ): Uri = container.archiveManager.writeChatToCache(conversationId, options, password)

    suspend fun writePortableBackup(uri: Uri, options: ArchiveOptions, password: String) {
        container.archiveManager.writeBackup(uri, options, password)
    }

    fun receivePortableArchive(uri: Uri) {
        incomingArchive.value = IncomingArchiveState(uri = uri)
        viewModelScope.launch {
            runCatching { container.archiveManager.inspect(uri) }
                .onSuccess { preview -> incomingArchive.value = IncomingArchiveState(uri = uri, preview = preview) }
                .onFailure { error ->
                    incomingArchive.value = if (error is ArchivePasswordRequiredException) {
                        IncomingArchiveState(uri = uri, passwordRequired = true)
                    } else IncomingArchiveState(uri = uri, error = error.message ?: "Could not inspect archive")
                }
        }
    }

    fun unlockIncomingArchive(password: String) {
        val state = incomingArchive.value ?: return
        incomingArchive.value = state.copy(importing = true, error = null)
        viewModelScope.launch {
            runCatching { container.archiveManager.inspect(state.uri, password) }
                .onSuccess { preview -> incomingArchive.value = state.copy(preview = preview, passwordRequired = true, importing = false, error = null) }
                .onFailure { error -> incomingArchive.value = state.copy(importing = false, error = error.message ?: "Could not unlock archive") }
        }
    }

    fun importIncomingArchive(password: String) {
        val state = incomingArchive.value ?: return
        incomingArchive.value = state.copy(importing = true, error = null)
        viewModelScope.launch {
            runCatching { container.archiveManager.importArchive(state.uri, password) }
                .onSuccess { conversationIds ->
                    incomingArchive.value = null
                    conversationIds.firstOrNull()?.let(::selectConversation)
                    screen.value = Screen.CHAT
                    notices.tryEmit("Imported ${conversationIds.size} chat${if (conversationIds.size == 1) "" else "s"}")
                }
                .onFailure { error -> incomingArchive.value = state.copy(importing = false, error = error.message ?: "Import failed") }
        }
    }

    fun dismissIncomingArchive() {
        incomingArchive.value = null
    }
''',
    "transfer viewmodel operations",
)

# Sidebar share action.
sidebar = "app/src/main/java/app/arbor/chat/ui/ConversationSidebar.kt"
insert_after(sidebar, "import androidx.compose.material.icons.outlined.Search\n", "import androidx.compose.material.icons.outlined.Share\n", "share icon import")
replace_once(
    sidebar,
    "    onMove: (String, String?) -> Unit,\n    onDelete: (String) -> Unit,",
    "    onMove: (String, String?) -> Unit,\n    onShare: (String) -> Unit,\n    onDelete: (String) -> Unit,",
    "sidebar share callback",
)
replace_once(
    sidebar,
    '''            SheetAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move to project") { movingTarget = item; actionTarget = null }
            SheetAction(if (conversation.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,''',
    '''            SheetAction(Icons.AutoMirrored.Filled.DriveFileMove, "Move to project") { movingTarget = item; actionTarget = null }
            SheetAction(Icons.Outlined.Share, "Share portable chat") { onShare(conversation.id); actionTarget = null }
            SheetAction(if (conversation.archived) Icons.Filled.Unarchive else Icons.Filled.Archive,''',
    "sidebar share action",
)

# Root app state, callbacks, and dialogs.
app = "app/src/main/java/app/arbor/chat/ui/ArborApp.kt"
insert_after(
    app,
    "    val setupDismissed by viewModel.setupDismissed.collectAsState()\n",
    "    val shareConversationId by viewModel.shareConversationId.collectAsState()\n    val incomingArchive by viewModel.incomingArchive.collectAsState()\n",
    "root transfer states",
)
app_text = Path(app).read_text()
needle = "                    onMove = viewModel::moveConversation,\n                    onDelete = viewModel::deleteConversation,"
count = app_text.count(needle)
if count != 1:
    raise RuntimeError(f"wide sidebar callback: expected 1 match, found {count}")
app_text = app_text.replace(needle, "                    onMove = viewModel::moveConversation,\n                    onShare = viewModel::requestShareConversation,\n                    onDelete = viewModel::deleteConversation,", 1)
needle = "                            onMove = viewModel::moveConversation,\n                            onDelete = viewModel::deleteConversation,"
count = app_text.count(needle)
if count != 1:
    raise RuntimeError(f"compact sidebar callback: expected 1 match, found {count}")
app_text = app_text.replace(needle, "                            onMove = viewModel::moveConversation,\n                            onShare = viewModel::requestShareConversation,\n                            onDelete = viewModel::deleteConversation,", 1)
Path(app).write_text(app_text)
replace_once(
    app,
    "        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))\n",
    '''        shareConversationId?.let { conversationId ->
            ChatShareDialog(
                viewModel = viewModel,
                conversationId = conversationId,
                onDismiss = viewModel::dismissShareConversation,
            )
        }
        incomingArchive?.let { state -> IncomingArchiveDialog(viewModel, state) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
''',
    "root transfer dialogs",
)

# Incoming archive intents.
activity = "app/src/main/java/app/arbor/chat/MainActivity.kt"
insert_after(
    activity,
    "import app.arbor.chat.settings.LauncherIconManager\n",
    "import app.arbor.chat.transfer.ARBOR_BACKUP_EXTENSION\nimport app.arbor.chat.transfer.ARBOR_BACKUP_MIME\nimport app.arbor.chat.transfer.ARBOR_CHAT_EXTENSION\nimport app.arbor.chat.transfer.ARBOR_CHAT_MIME\n",
    "archive intent imports",
)
replace_once(
    activity,
    '''    private fun handleIntent(intent: Intent) {
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        viewModel.receiveIntent(intent.getStringExtra(EXTRA_CONVERSATION_ID), uris)
    }
''',
    '''    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let {
                viewModel.receivePortableArchive(it)
                return
            }
        }
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            else -> emptyList()
        }
        if (uris.size == 1 && isPortableArchiveIntent(intent, uris.single())) {
            viewModel.receivePortableArchive(uris.single())
            return
        }
        viewModel.receiveIntent(intent.getStringExtra(EXTRA_CONVERSATION_ID), uris)
    }

    private fun isPortableArchiveIntent(intent: Intent, uri: Uri): Boolean {
        if (intent.type == ARBOR_CHAT_MIME || intent.type == ARBOR_BACKUP_MIME) return true
        val name = uri.lastPathSegment?.lowercase().orEmpty()
        return name.endsWith(ARBOR_CHAT_EXTENSION) || name.endsWith(ARBOR_BACKUP_EXTENSION)
    }
''',
    "incoming archive intent handling",
)

# Open custom files from Android and expose temporary share files.
manifest = "app/src/main/AndroidManifest.xml"
replace_once(
    manifest,
    '''            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="arbor" android:host="oauth-complete" />
            </intent-filter>
        </activity>''',
    '''            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="arbor" android:host="oauth-complete" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="application/vnd.arbor.chat" />
                <data android:mimeType="application/vnd.arbor.backup" />
            </intent-filter>
        </activity>''',
    "archive view intent filters",
)
replace_once(
    "app/src/main/res/xml/file_paths.xml",
    '    <cache-path name="previews" path="previews/" />\n',
    '    <cache-path name="previews" path="previews/" />\n    <cache-path name="shares" path="shares/" />\n',
    "share cache file provider path",
)

# Version and documentation.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 148\n        versionName = "0.20.22"',
    '        versionCode = 149\n        versionName = "0.20.23"',
    "version bump",
)
insert_after(
    "CHANGELOG.md",
    "# Changelog\n\n",
    '''## 0.20.23 — 2026-07-31

- Replace the single, mostly useless model choice in API-provider setup with a searchable multi-select list. Every discovered model starts selected, and Arbor stores only the models left selected.
- Add Android-native cloud/file backups through the Storage Access Framework, including Google Drive, OneDrive, Dropbox, Nextcloud, USB, and local destinations exposed by the device.
- Make archive passwords genuinely optional. Unencrypted backups and chat files remain allowed with a prominent disclosure instead of an artificial password requirement.
- Add a portable `.arborchat` format with configurable attachments, reasoning, tool traces, system prompts, and request metadata; safe fields remain excluded by default.
- Let Arbor open shared chat and backup files, show a content/privacy preview, unlock encrypted archives, import non-destructive copies, and immediately continue an imported chat.
- Keep API keys and OAuth sessions out of portable files by design.

''',
    "changelog entry",
)
Path("docs/releases/RELEASE_NOTES_0.20.23.md").write_text('''# Arbor 0.20.23

## Provider models are a real multi-select

After Arbor fetches a provider's model catalog, every model is selected by default. You can search, clear, select all, or choose any subset. Only that subset is stored for the provider.

## Cloud backups without forced passwords

**Backup & transfer** uses Android's document picker, so any installed cloud document provider can be the destination. Password encryption is optional. Leaving it blank is permitted and shows a clear exposure warning; credentials and OAuth sessions are never included.

## Portable chat sharing

Chats can be shared as `.arborchat` files. Visible messages and attachments are the safe default, while reasoning, tool traces, custom prompts, and request metadata are separate opt-in controls. Arbor previews incoming files before importing them as non-destructive copies and can immediately continue the imported chat.
''')

# Focused structural regression coverage.
Path("app/src/test/java/app/arbor/chat/ui/TransferFeatureTest.kt").write_text('''package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransferFeatureTest {
    @Test
    fun `provider setup selects all discovered models and stores only checked models`() {
        val settings = File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertTrue(settings.contains("selectedModelIds = models.mapTo(linkedSetOf())"))
        assertTrue(settings.contains("Checkbox(checked = checked"))
        assertTrue(settings.contains("draft.selectedModels.map"))
        assertFalse(settings.contains("RadioButton(selected = modelId == model.id"))
    }

    @Test
    fun `portable archives allow explicit unencrypted output and safe chat defaults`() {
        val ui = File("src/main/java/app/arbor/chat/ui/TransferUi.kt").readText()
        val archive = File("src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt").readText()
        assertTrue(ui.contains("leave blank for none"))
        assertTrue(ui.contains("includeReasoning by remember { mutableStateOf(false) }"))
        assertTrue(ui.contains("includeToolData by remember { mutableStateOf(false) }"))
        assertTrue(archive.contains("val encrypted = password.isNotEmpty()"))
        assertTrue(archive.contains("PBKDF2WithHmacSHA256"))
        assertTrue(archive.contains("AES/GCM/NoPadding"))
    }

    @Test
    fun `incoming archives preview and import as copies`() {
        val ui = File("src/main/java/app/arbor/chat/ui/TransferUi.kt").readText()
        val activity = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(ui.contains("Import creates separate local copies"))
        assertTrue(ui.contains("Import and continue"))
        assertTrue(activity.contains("receivePortableArchive"))
        assertTrue(manifest.contains("application/vnd.arbor.chat"))
    }
}
''')

print("Applied Arbor 0.20.23 provider multi-select, cloud backup, and portable chat transfer changes.")
