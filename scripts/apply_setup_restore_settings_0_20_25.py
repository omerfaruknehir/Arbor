from pathlib import Path


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    if new in text:
        return
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor in {path}, found {count}")
    file.write_text(text.replace(old, new, 1))


# DAO snapshot/restore accessors. No schema migration is required.
replace_once(
    "app/src/main/java/app/arbor/chat/data/Daos.kt",
    '''interface SystemPromptProfileDao {
    @Query("SELECT * FROM system_prompt_profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SystemPromptProfileEntity>>

    @Query("SELECT * FROM system_prompt_profiles WHERE id = :id")
''',
    '''interface SystemPromptProfileDao {
    @Query("SELECT * FROM system_prompt_profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SystemPromptProfileEntity>>

    @Query("SELECT * FROM system_prompt_profiles ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<SystemPromptProfileEntity>

    @Query("SELECT * FROM system_prompt_profiles WHERE id = :id")
''',
    "system prompt profile snapshot query",
)
replace_once(
    "app/src/main/java/app/arbor/chat/data/Daos.kt",
    '''interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
''',
    '''interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
''',
    "project snapshot query",
)
replace_once(
    "app/src/main/java/app/arbor/chat/data/Daos.kt",
    '''interface CatalogDao {
    @Query("SELECT * FROM providers WHERE enabled = 1 ORDER BY displayName")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
''',
    '''interface CatalogDao {
    @Query("SELECT * FROM providers WHERE enabled = 1 ORDER BY displayName")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY displayName")
    suspend fun allProviders(): List<ProviderEntity>

    @Query("SELECT * FROM models ORDER BY providerId, displayName")
    suspend fun allModels(): List<ModelEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
''',
    "catalog snapshot queries",
)
replace_once(
    "app/src/main/java/app/arbor/chat/data/Daos.kt",
    '''    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(value: ModelEntity)
}

@Dao
interface PendingDao''',
    '''    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(value: ModelEntity)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteModels(providerId: String)
}

@Dao
interface PendingDao''',
    "catalog model replacement query",
)

# Register portable settings service in the application container.
replace_once(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    "import app.arbor.chat.transfer.ArborArchiveManager\n",
    '''import app.arbor.chat.transfer.AppSettingsArchiveStore
import app.arbor.chat.transfer.ArborArchiveManager
''',
    "settings archive import",
)
replace_once(
    "app/src/main/java/app/arbor/chat/ArborApplication.kt",
    '''    val pythonSandbox = PythonSandbox(application)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
''',
    '''    val pythonSandbox = PythonSandbox(application)
    val linuxEnvironmentArchives = LinuxEnvironmentArchiveStore(application, pythonSandbox)
    val appSettingsArchives = AppSettingsArchiveStore(application, appPreferences, database)
    val archiveManager = ArborArchiveManager(application, database, linuxEnvironmentArchives, appSettingsArchives)
    val scopedCloudFolder = ScopedCloudFolderStore(application)
''',
    "settings archive service",
)

archive = "app/src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt"
replace_once(
    archive,
    '''    val includeRequestMetadata: Boolean = false,
    val includeLinuxEnvironments: Boolean = false,
)''',
    '''    val includeRequestMetadata: Boolean = false,
    val includeLinuxEnvironments: Boolean = false,
    val includeAppSettings: Boolean = false,
)''',
    "archive settings option",
)
replace_once(
    archive,
    '''    val conversations: List<PortableConversationBundle>,
    val linuxEnvironments: List<PortableLinuxEnvironment> = emptyList(),
)''',
    '''    val conversations: List<PortableConversationBundle>,
    val linuxEnvironments: List<PortableLinuxEnvironment> = emptyList(),
    val appSettings: PortableAppSettings? = null,
)''',
    "archive settings manifest",
)
replace_once(
    archive,
    '''    val maxOutputTokens: Int,
    val systemPrompt: String,
    val totalInputTokens: Long,
''',
    '''    val maxOutputTokens: Int,
    val systemPrompt: String,
    val systemPromptProfileId: String? = null,
    val projectId: String? = null,
    val totalInputTokens: Long,
''',
    "conversation organization fields",
)
replace_once(
    archive,
    '''    val linuxEnvironmentCount: Int,
    val linuxEnvironmentBytes: Long,
    val encrypted: Boolean,
''',
    '''    val linuxEnvironmentCount: Int,
    val linuxEnvironmentBytes: Long,
    val appSettingsIncluded: Boolean,
    val encrypted: Boolean,
''',
    "settings preview flag",
)
replace_once(
    archive,
    '''data class ArchiveImportResult(
    val conversationIds: List<String>,
    val linuxEnvironmentCount: Int,
)''',
    '''data class ArchiveImportResult(
    val conversationIds: List<String>,
    val linuxEnvironmentCount: Int,
    val settingsRestored: Boolean,
)''',
    "settings import result",
)
replace_once(
    archive,
    '''class ArborArchiveManager(
    private val context: Context,
    private val database: ArborDatabase,
    private val linuxEnvironments: LinuxEnvironmentArchiveStore,
)''',
    '''class ArborArchiveManager(
    private val context: Context,
    private val database: ArborDatabase,
    private val linuxEnvironments: LinuxEnvironmentArchiveStore,
    private val appSettings: AppSettingsArchiveStore,
)''',
    "settings archive dependency",
)
replace_once(
    archive,
    '''                linuxEnvironmentCount = manifest.linuxEnvironments.size,
                linuxEnvironmentBytes = manifest.linuxEnvironments.sumOf { it.sizeBytes },
                encrypted = decoded.header.encrypted,
''',
    '''                linuxEnvironmentCount = manifest.linuxEnvironments.size,
                linuxEnvironmentBytes = manifest.linuxEnvironments.sumOf { it.sizeBytes },
                appSettingsIncluded = manifest.appSettings != null,
                encrypted = decoded.header.encrypted,
''',
    "settings preview value",
)
replace_once(
    archive,
    '''    suspend fun importArchive(uri: Uri, password: String = ""): ArchiveImportResult = withContext(Dispatchers.IO) {
        val decoded = decodePayloadToTemp(uri, password)
        try {
            val manifest = readManifest(decoded.file)
            ZipFile(decoded.file).use { zip ->
                val conversationIds = manifest.conversations.map { bundle ->
                    importConversation(zip, bundle, preserveArchiveState = manifest.kind == ArchiveKind.BACKUP)
                }
                val restoredLinux = linuxEnvironments.restore(zip, manifest.linuxEnvironments)
                ArchiveImportResult(conversationIds, restoredLinux)
            }
        } finally {
            decoded.file.delete()
        }
    }
''',
    '''    suspend fun importArchive(uri: Uri, password: String = ""): ArchiveImportResult = withContext(Dispatchers.IO) {
        val decoded = decodePayloadToTemp(uri, password)
        try {
            val manifest = readManifest(decoded.file)
            ZipFile(decoded.file).use { zip ->
                val settingsRestore = manifest.appSettings?.let { appSettings.restore(it) }
                    ?: AppSettingsRestoreResult()
                val conversationIds = manifest.conversations.map { bundle ->
                    importConversation(
                        zip = zip,
                        bundle = bundle,
                        preserveArchiveState = manifest.kind == ArchiveKind.BACKUP,
                        projectIds = settingsRestore.projectIds,
                        systemPromptProfileIds = settingsRestore.systemPromptProfileIds,
                    )
                }
                val restoredLinux = linuxEnvironments.restore(zip, manifest.linuxEnvironments)
                ArchiveImportResult(conversationIds, restoredLinux, settingsRestore.restored)
            }
        } finally {
            decoded.file.delete()
        }
    }
''',
    "settings restore transaction",
)
replace_once(
    archive,
    '''        val preparedLinux = if (kind == ArchiveKind.BACKUP && options.includeLinuxEnvironments) {
            linuxEnvironments.prepareSnapshots()
        } else emptyList()
        try {
            require(bundles.isNotEmpty() || preparedLinux.isNotEmpty()) {
                if (options.includeLinuxEnvironments) "There are no chats or installed Linux environments to back up"
                else "There are no chats to back up"
            }
''',
    '''        val preparedLinux = if (kind == ArchiveKind.BACKUP && options.includeLinuxEnvironments) {
            linuxEnvironments.prepareSnapshots()
        } else emptyList()
        val portableSettings = if (kind == ArchiveKind.BACKUP && options.includeAppSettings) {
            appSettings.snapshot()
        } else null
        try {
            require(bundles.isNotEmpty() || preparedLinux.isNotEmpty() || portableSettings != null) {
                if (options.includeLinuxEnvironments || options.includeAppSettings) {
                    "There are no chats, app settings, or installed Linux environments to back up"
                } else "There are no chats to back up"
            }
''',
    "settings archive snapshot",
)
replace_once(
    archive,
    '''                title = when {
                    kind == ArchiveKind.CHAT -> bundles.single().conversation.title
                    bundles.isEmpty() -> "Arbor Linux backup"
                    else -> "Arbor backup"
                },
                options = options,
                conversations = bundles,
                linuxEnvironments = preparedLinux.map(PreparedLinuxEnvironment::metadata),
            )''',
    '''                title = when {
                    kind == ArchiveKind.CHAT -> bundles.single().conversation.title
                    bundles.isEmpty() && preparedLinux.isNotEmpty() -> "Arbor Linux backup"
                    bundles.isEmpty() -> "Arbor settings backup"
                    else -> "Arbor backup"
                },
                options = options,
                conversations = bundles,
                linuxEnvironments = preparedLinux.map(PreparedLinuxEnvironment::metadata),
                appSettings = portableSettings,
            )''',
    "settings manifest payload",
)
replace_once(
    archive,
    '''                maxOutputTokens = conversation.maxOutputTokens,
                systemPrompt = if (options.includeSystemPrompt) conversation.systemPrompt else "",
                totalInputTokens = conversation.totalInputTokens,
''',
    '''                maxOutputTokens = conversation.maxOutputTokens,
                systemPrompt = if (options.includeSystemPrompt) conversation.systemPrompt else "",
                systemPromptProfileId = if (options.includeAppSettings) conversation.systemPromptProfileId else null,
                projectId = if (options.includeAppSettings) conversation.projectId else null,
                totalInputTokens = conversation.totalInputTokens,
''',
    "conversation settings links snapshot",
)
replace_once(
    archive,
    '''    private suspend fun importConversation(
        zip: ZipFile,
        bundle: PortableConversationBundle,
        preserveArchiveState: Boolean,
    ): String {''',
    '''    private suspend fun importConversation(
        zip: ZipFile,
        bundle: PortableConversationBundle,
        preserveArchiveState: Boolean,
        projectIds: Map<String, String>,
        systemPromptProfileIds: Map<String, String>,
    ): String {''',
    "conversation settings link mappings",
)
replace_once(
    archive,
    '''                        systemPrompt = source.systemPrompt,
                        systemPromptProfileId = null,
''',
    '''                        systemPrompt = source.systemPrompt,
                        systemPromptProfileId = source.systemPromptProfileId?.let(systemPromptProfileIds::get),
''',
    "prompt profile remap",
)
replace_once(
    archive,
    '''                        pinned = preserveArchiveState && source.pinned,
                        projectId = null,
                        archivedAt = if (preserveArchiveState) source.archivedAt else null,
''',
    '''                        pinned = preserveArchiveState && source.pinned,
                        projectId = source.projectId?.let(projectIds::get),
                        archivedAt = if (preserveArchiveState) source.archivedAt else null,
''',
    "project remap",
)

# Backup page and archive preview now expose app settings explicitly.
transfer = "app/src/main/java/app/arbor/chat/ui/TransferUi.kt"
replace_once(
    transfer,
    '''    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var includeAttachments by remember { mutableStateOf(true) }
''',
    '''    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var includeAppSettings by remember { mutableStateOf(true) }
    var includeAttachments by remember { mutableStateOf(true) }
''',
    "backup settings state",
)
replace_once(
    transfer,
    '''        includeRequestMetadata = includePrivateData,
        includeLinuxEnvironments = includeLinuxEnvironments,
    )''',
    '''        includeRequestMetadata = includePrivateData,
        includeLinuxEnvironments = includeLinuxEnvironments,
        includeAppSettings = includeAppSettings,
    )''',
    "backup settings option binding",
)
replace_once(
    transfer,
    '''                        "Chats, branches, per-chat settings, metadata, and optional attachments. API keys and OAuth sessions are deliberately excluded.",''',
    '''                        "Chats, branches, app configuration, organization, metadata, and optional attachments. API keys and OAuth sessions are deliberately excluded.",''',
    "portable backup description",
)
replace_once(
    transfer,
    '''            TransferSwitch("Include attachments", includeAttachments) { includeAttachments = it }
            TransferSwitch("Include reasoning, tool traces, and request metadata", includePrivateData) { includePrivateData = it }
''',
    '''            TransferSwitch("Include app settings and configuration", includeAppSettings) { includeAppSettings = it }
            if (includeAppSettings) {
                Text(
                    "Includes theme, UI behavior, new-chat defaults, provider endpoints/models, projects, prompt profiles, and automation settings. Credentials, OAuth sessions, provider authorization headers, cloud grants, drafts, and navigation state stay excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TransferSwitch("Include attachments", includeAttachments) { includeAttachments = it }
            TransferSwitch("Include reasoning, tool traces, and request metadata", includePrivateData) { includePrivateData = it }
''',
    "backup settings switch",
)
replace_once(
    transfer,
    '''                    IncludedRow("Request metadata", value.options.includeRequestMetadata)
                    IncludedRow("Installed Linux environments", value.options.includeLinuxEnvironments)
''',
    '''                    IncludedRow("Request metadata", value.options.includeRequestMetadata)
                    IncludedRow("App settings and configuration", value.appSettingsIncluded)
                    IncludedRow("Installed Linux environments", value.options.includeLinuxEnvironments)
''',
    "settings preview row",
)
replace_once(
    transfer,
    '''                            "Import creates separate local copies. It never replaces an existing chat and does not import API keys or OAuth sessions.",''',
    '''                            if (value.appSettingsIncluded) {
                                "Chats are imported as separate copies. Included app settings and organization are applied, but API keys, OAuth sessions, provider authorization headers, and cloud grants are never imported."
                            } else {
                                "Import creates separate local chat copies. It never replaces an existing chat and does not import API keys or OAuth sessions."
                            },''',
    "settings import disclosure",
)

# Setup first page contains local/cloud restore actions and can host the preview dialog.
onboarding = "app/src/main/java/app/arbor/chat/ui/OnboardingScreen.kt"
replace_once(
    onboarding,
    '''internal fun OnboardingScreen(
    currentThemeMode: ThemeMode,''',
    '''internal fun OnboardingScreen(
    viewModel: ChatViewModel,
    currentThemeMode: ThemeMode,''',
    "onboarding restore dependency",
)
replace_once(
    onboarding,
    '''                        OnboardingStep.WELCOME -> WelcomeStep()
''',
    '''                        OnboardingStep.WELCOME -> WelcomeStep(viewModel)
''',
    "welcome restore call",
)
replace_once(
    onboarding,
    '''@Composable
private fun WelcomeStep() {''',
    '''@Composable
private fun WelcomeStep(viewModel: ChatViewModel) {''',
    "welcome restore parameter",
)
replace_once(
    onboarding,
    '''            OnboardingValueRow(Icons.Outlined.Code, "Local tools are optional", "Bundled Python works immediately; Linux requires a separate distribution install.")
        }
    }
}

@Composable
private fun AppearanceStep''',
    '''            OnboardingValueRow(Icons.Outlined.Code, "Local tools are optional", "Bundled Python works immediately; Linux requires a separate distribution install.")
        }
    }
    SetupRestoreActions(viewModel)
}

@Composable
private fun AppearanceStep''',
    "welcome restore panel",
)

arbor_app = "app/src/main/java/app/arbor/chat/ui/ArborApp.kt"
replace_once(
    arbor_app,
    '''    val drawerState = rememberInteractiveDrawerState()
    val snackbar = remember { SnackbarHostState() }
    val openDrawer = remember(drawerState) { { drawerState.open(); Unit } }

    LaunchedEffect(providerCatalogReady) {''',
    '''    val drawerState = rememberInteractiveDrawerState()
    val snackbar = remember { SnackbarHostState() }
    val openDrawer = remember(drawerState) { { drawerState.open(); Unit } }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(providerCatalogReady) {''',
    "setup snackbar collector",
)
old_onboarding = '''    if (onboardingCatalogUsable && setupActive && !setupTemporarilyAway) {
        OnboardingScreen(
            currentThemeMode = themeMode,
            currentPalette = palette,
            matchLauncherIconToPalette = matchLauncherIconToPalette,
            amoled = amoled,
            providerCatalogDelayed = !providerCatalogReady,
            configuredProviderCount = configuredProviders.size,
            pythonEnabled = newChatDefaults.agentPythonEnabled,
            stepIndex = setupStepIndex,
            stepOffsetFraction = setupPageOffsetFraction,
            scrollOffsetForStep = viewModel::setupScrollOffset,
            onPagerPositionChanged = viewModel::updateSetupPagerPosition,
            onStepScrollChanged = viewModel::saveSetupScrollOffset,
            linuxEnabled = newChatDefaults.agentUbuntuEnabled,
            linuxStatus = ubuntuStatus,
            onThemeModeChanged = viewModel::setThemeMode,
            onPaletteChanged = viewModel::setPalette,
            onMatchLauncherIconToPaletteChanged = viewModel::setMatchLauncherIconToPalette,
            onAmoledChanged = viewModel::setAmoled,
            onPythonEnabledChanged = { enabled ->
                viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) }
            },
            onLinuxEnabledChanged = { enabled ->
                viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) }
            },
            onOpenProviderSetup = viewModel::openProviderSetupFromSetup,
            onOpenLinuxSetup = viewModel::openLinuxSetupFromSetup,
            onExplore = viewModel::finishSetup,
        )
        return
    }
'''
new_onboarding = '''    if (onboardingCatalogUsable && setupActive && !setupTemporarilyAway) {
        Box(Modifier.fillMaxSize()) {
            OnboardingScreen(
                viewModel = viewModel,
                currentThemeMode = themeMode,
                currentPalette = palette,
                matchLauncherIconToPalette = matchLauncherIconToPalette,
                amoled = amoled,
                providerCatalogDelayed = !providerCatalogReady,
                configuredProviderCount = configuredProviders.size,
                pythonEnabled = newChatDefaults.agentPythonEnabled,
                stepIndex = setupStepIndex,
                stepOffsetFraction = setupPageOffsetFraction,
                scrollOffsetForStep = viewModel::setupScrollOffset,
                onPagerPositionChanged = viewModel::updateSetupPagerPosition,
                onStepScrollChanged = viewModel::saveSetupScrollOffset,
                linuxEnabled = newChatDefaults.agentUbuntuEnabled,
                linuxStatus = ubuntuStatus,
                onThemeModeChanged = viewModel::setThemeMode,
                onPaletteChanged = viewModel::setPalette,
                onMatchLauncherIconToPaletteChanged = viewModel::setMatchLauncherIconToPalette,
                onAmoledChanged = viewModel::setAmoled,
                onPythonEnabledChanged = { enabled ->
                    viewModel.updateNewChatDefaults { it.copy(agentPythonEnabled = enabled) }
                },
                onLinuxEnabledChanged = { enabled ->
                    viewModel.updateNewChatDefaults { it.copy(agentUbuntuEnabled = enabled) }
                },
                onOpenProviderSetup = viewModel::openProviderSetupFromSetup,
                onOpenLinuxSetup = viewModel::openLinuxSetupFromSetup,
                onExplore = viewModel::finishSetup,
            )
            incomingArchive?.let { state -> IncomingArchiveDialog(viewModel, state) }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
        return
    }
'''
replace_once(arbor_app, old_onboarding, new_onboarding, "setup restore preview host")
replace_once(
    arbor_app,
    '''    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(pythonRun?.startedAt, pythonRun?.running, linuxRun?.startedAt, linuxRun?.running) {''',
    '''    LaunchedEffect(pythonRun?.startedAt, pythonRun?.running, linuxRun?.startedAt, linuxRun?.running) {''',
    "deduplicate snackbar collector",
)

# After a setup restore, continue at provider connection because credentials are excluded.
view_model = "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt"
replace_once(
    view_model,
    '''                .onSuccess { result ->
                    incomingArchive.value = null
                    result.conversationIds.firstOrNull()?.let(::selectConversation)
                    if (result.conversationIds.isNotEmpty()) screen.value = Screen.CHAT
                    val parts = buildList {
                        if (result.conversationIds.isNotEmpty()) {
                            add("${result.conversationIds.size} chat${if (result.conversationIds.size == 1) "" else "s"}")
                        }
                        if (result.linuxEnvironmentCount > 0) {
                            add("${result.linuxEnvironmentCount} Linux environment${if (result.linuxEnvironmentCount == 1) "" else "s"}")
                        }
                    }
                    notices.tryEmit("Imported ${parts.joinToString(" and ")}")
                    if (result.linuxEnvironmentCount > 0) container.ubuntuRuntime.refresh()
                }
''',
    '''                .onSuccess { result ->
                    val restoredDuringSetup = setupActive.value
                    incomingArchive.value = null
                    result.conversationIds.firstOrNull()?.let(::selectConversation)
                    if (result.conversationIds.isNotEmpty()) screen.value = Screen.CHAT
                    val parts = buildList {
                        if (result.conversationIds.isNotEmpty()) {
                            add("${result.conversationIds.size} chat${if (result.conversationIds.size == 1) "" else "s"}")
                        }
                        if (result.settingsRestored) add("app settings")
                        if (result.linuxEnvironmentCount > 0) {
                            add("${result.linuxEnvironmentCount} Linux environment${if (result.linuxEnvironmentCount == 1) "" else "s"}")
                        }
                    }
                    val credentialNote = if (result.settingsRestored) {
                        ". Provider credentials and OAuth sessions were excluded; reconnect them in the next setup step"
                    } else ""
                    notices.tryEmit("Imported ${parts.joinToString(" and ")}$credentialNote")
                    if (result.linuxEnvironmentCount > 0) container.ubuntuRuntime.refresh()
                    if (result.settingsRestored) reconcileLauncherIcon()
                    if (restoredDuringSetup) {
                        setupActive.value = true
                        setupStepIndex.value = 2
                        setupPageOffsetFraction.value = 0f
                        setupTemporarilyAway.value = false
                        setupDismissed.value = false
                        settingsRoute.value = SettingsRoute.HOME
                        screen.value = Screen.CHAT
                    }
                }
''',
    "setup restore continuation",
)

# Cloud page copy reflects portable settings support.
replace_once(
    "app/src/main/java/app/arbor/chat/ui/CloudBackupUi.kt",
    '''            "Android/Google One app backup is enabled for small, non-secret Arbor preferences. Chats, attachments, and Linux root filesystems use the portable backup targets above because Android's standard app backup is limited to 25 MB. API keys, OAuth sessions, and database encryption keys are excluded everywhere.",''',
    '''            "Portable cloud backups can include chats, app settings, organization, and optional Linux root filesystems. Android/Google One app backup remains limited to small non-secret preferences. API keys, OAuth sessions, provider authorization headers, cloud grants, and database encryption keys are excluded everywhere.",''',
    "cloud settings disclosure",
)

# Version and release documentation.
replace_once(
    "app/build.gradle.kts",
    '''        versionCode = 150
        versionName = "0.20.24"''',
    '''        versionCode = 151
        versionName = "0.20.25"''',
    "version 0.20.25",
)
replace_once(
    "CHANGELOG.md",
    "# Changelog\n\n## 0.20.24 — 2026-07-31\n",
    '''# Changelog

## 0.20.25 — 2026-07-31

- Add local and least-privilege cloud restore actions directly to the first setup page, including Google Drive app storage and one explicitly selected document-provider folder.
- Let portable backups include theme/UI settings, new-chat defaults, developer settings, provider/model configuration, projects, system-prompt profiles, automation policy, and selected Linux distribution.
- Remap project and prompt-profile links while importing chats, without overwriting existing chats.
- Keep API keys, OAuth sessions, provider authorization headers, database encryption keys, cloud grants, drafts, and transient navigation state out of portable settings.
- Continue setup at provider connection after restore so credentials can be reconnected deliberately.

## 0.20.24 — 2026-07-31
''',
    "0.20.25 changelog",
)
