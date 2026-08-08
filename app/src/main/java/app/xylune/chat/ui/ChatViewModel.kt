package app.xylune.chat.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.xylune.chat.AppContainer
import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.AutomationSettingsEntity
import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageStatus
import app.xylune.chat.data.MemoryEntity
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ProjectEntity
import app.xylune.chat.data.SystemPromptMode
import app.xylune.chat.data.SystemPromptProfileEntity
import app.xylune.chat.data.SendMode
import app.xylune.chat.data.AuxiliaryMode
import app.xylune.chat.data.PackageApprovalMode
import app.xylune.chat.data.PackageTransactionEntity
import app.xylune.chat.provider.ProviderCredentialPolicy
import app.xylune.chat.provider.ProviderEndpointPolicy
import app.xylune.chat.provider.ModelRequestPolicy
import app.xylune.chat.provider.defaultThinkingEffort
import app.xylune.chat.provider.effectiveThinkingEnabled
import app.xylune.chat.provider.OpenAiOAuthManager
import app.xylune.chat.provider.OpenAiOAuthState
import app.xylune.chat.provider.OpenAiOAuthUsageState
import app.xylune.chat.provider.parseHeaders
import app.xylune.chat.sandbox.ExecutionResult
import app.xylune.chat.sandbox.ExecutionProgress
import app.xylune.chat.sandbox.PackageInstallProgress
import app.xylune.chat.sandbox.PackageInstallResult
import app.xylune.chat.sandbox.PythonEnvironmentInfo
import app.xylune.chat.sandbox.PackageReview
import app.xylune.chat.sandbox.PackagePlan
import app.xylune.chat.sandbox.PackageEcosystem
import app.xylune.chat.sandbox.PackageApprovalState
import app.xylune.chat.sandbox.fingerprint
import app.xylune.chat.sandbox.LinuxDistribution
import app.xylune.chat.sandbox.UbuntuExecutionResult
import app.xylune.chat.sandbox.UbuntuPackageInstallResult
import app.xylune.chat.sandbox.UbuntuRuntimeStatus
import app.xylune.chat.sandbox.ScriptRunMetadata
import app.xylune.chat.sandbox.ScriptRunResult
import app.xylune.chat.sandbox.WorkspaceReadResult
import app.xylune.chat.agent.AgentToolRequest
import app.xylune.chat.settings.NewChatDefaults
import app.xylune.chat.settings.LauncherIconManager
import app.xylune.chat.settings.PersistentUiStateStore
import app.xylune.chat.transfer.ArchiveOptions
import app.xylune.chat.transfer.ArchivePasswordRequiredException
import app.xylune.chat.transfer.CloudBackupEntry
import app.xylune.chat.transfer.CloudOAuthProvider
import app.xylune.chat.transfer.CloudOAuthState
import app.xylune.chat.transfer.DirectCloudProvider
import app.xylune.chat.transfer.DirectCloudConfigurationSnapshot
import app.xylune.chat.transfer.WebDavCloudConfig
import app.xylune.chat.transfer.S3CloudConfig
import app.xylune.chat.transfer.IncomingArchiveState
import app.xylune.chat.update.RepositoryUpdateState
import app.xylune.chat.generated.GeneratedBlockRepairState
import app.xylune.chat.generated.GeneratedBlockType
import app.xylune.chat.generated.GeneratedValidationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PythonRunState(
    val startedAt: Long,
    val code: String,
    val timeoutSeconds: Int,
    val running: Boolean = true,
    val progress: ExecutionProgress = ExecutionProgress(),
    val result: ExecutionResult? = null,
    val error: String? = null,
)

data class LinuxRunState(
    val startedAt: Long,
    val command: String,
    val distribution: LinuxDistribution,
    val timeoutSeconds: Int,
    val running: Boolean = true,
    val progress: ExecutionProgress = ExecutionProgress(),
    val result: UbuntuExecutionResult? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ChatViewModel(private val container: AppContainer, savedStateHandle: SavedStateHandle) : ViewModel() {
    private val toolResultJson = Json { ignoreUnknownKeys = true }
    private val restoredUiState = container.persistentUiState.restore()
    val conversations = container.repository.conversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedConversations = container.repository.archivedConversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = container.repository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val systemPromptProfiles = container.repository.systemPromptProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val memories = container.repository.memories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allModels = container.repository.observeAllModels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val automationSettings = container.repository.automationSettings
        .map { it ?: AutomationSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationSettingsEntity())
    val ubuntuStatus: StateFlow<UbuntuRuntimeStatus> = container.ubuntuRuntime.status
    val linuxDistribution: StateFlow<LinuxDistribution> = container.ubuntuRuntime.distribution
    val selectedConversationId = savedStateHandle.getMutableStateFlow<String?>(
        "selected_conversation",
        restoredUiState.selectedConversationId,
    )
    private val newDraftConversationId = savedStateHandle.getMutableStateFlow<String?>(
        "new_draft_conversation",
        restoredUiState.newDraftConversationId,
    )
    private val draftConversation = MutableStateFlow<ConversationEntity?>(null)
    val showArchived = savedStateHandle.getMutableStateFlow("show_archived", restoredUiState.showArchived)
    val selectedProjectId = savedStateHandle.getMutableStateFlow<String?>(
        "selected_project",
        restoredUiState.selectedProjectId,
    )
    private val activeDraftConversationId = MutableStateFlow(
        restoredUiState.selectedConversationId ?: restoredUiState.newDraftConversationId,
    )
    val draft = MutableStateFlow(
        activeDraftConversationId.value?.let(container.composerDrafts::read).orEmpty(),
    )
    val stagedAttachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    val importing = MutableStateFlow(false)
    val screen = savedStateHandle.getMutableStateFlow("screen", restoredUiState.screen)
    val settingsRoute = savedStateHandle.getMutableStateFlow("settings_route", restoredUiState.settingsRoute)
    val settingsPageRevisions = MutableStateFlow<Map<SettingsRoute, Long>>(emptyMap())
    val searchQuery = savedStateHandle.getMutableStateFlow("search_query", restoredUiState.searchQuery)
    val focusedMessageNodeId = savedStateHandle.getMutableStateFlow<String?>(
        "focused_message_node",
        restoredUiState.focusedMessageNodeId,
    )
    private val focusedMessageIndex = savedStateHandle.getMutableStateFlow<Int?>("focused_message_index", null)
    val setupActive = savedStateHandle.getMutableStateFlow("setup_active", restoredUiState.setupActive)
    val setupStepIndex = savedStateHandle.getMutableStateFlow("setup_step", restoredUiState.setupStepIndex)
    val setupPageOffsetFraction = savedStateHandle.getMutableStateFlow(
        "setup_page_offset_fraction",
        restoredUiState.setupPageOffsetFraction,
    )
    val setupTemporarilyAway = savedStateHandle.getMutableStateFlow(
        "setup_temporarily_away",
        restoredUiState.setupTemporarilyAway,
    )
    val setupDismissed = savedStateHandle.getMutableStateFlow("setup_dismissed", restoredUiState.setupDismissed)
    val launcherRestartRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val latestChatScrollSnapshots = mutableMapOf<String, PersistentUiStateStore.ChatScrollSnapshot>()
    private val chatScrollUpdates = MutableSharedFlow<Pair<String, PersistentUiStateStore.ChatScrollSnapshot>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val latestSettingsScrollOffsets = mutableMapOf<SettingsRoute, Int>()
    private val settingsScrollUpdates = MutableSharedFlow<Pair<SettingsRoute, Int>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val latestSetupScrollOffsets = mutableMapOf<Int, Int>()
    private val setupScrollUpdates = MutableSharedFlow<Pair<Int, Int>>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val amoled: StateFlow<Boolean> = container.appPreferences.amoled
    val chromeBlurStrength: StateFlow<Float> = container.appPreferences.chromeBlurStrength
    val chromeEdgeSoftness: StateFlow<Float> = container.appPreferences.chromeEdgeSoftness
    val chromeOverlayOpacity: StateFlow<Float> = container.appPreferences.chromeOverlayOpacity
    val lessEmojiEnabled: StateFlow<Boolean> = container.appPreferences.lessEmojiEnabled
    val automaticUpdateChecks: StateFlow<Boolean> = container.appPreferences.automaticUpdateChecks
    val generatedRepairMaxAttempts: StateFlow<Int> = container.appPreferences.generatedRepairMaxAttempts
    val developerSettings: StateFlow<app.xylune.chat.settings.DeveloperSettings> = container.appPreferences.developerSettings
    val palette = container.appPreferences.palette
    val themeMode = container.appPreferences.themeMode
    val appLanguage = container.appPreferences.appLanguage
    val matchLauncherIconToPalette = container.appPreferences.matchLauncherIconToPalette
    val newChatDefaults: StateFlow<NewChatDefaults> = container.appPreferences.newChatDefaults
    val favoriteModels: StateFlow<Set<String>> = container.appPreferences.favoriteModels
    val recentModels: StateFlow<List<String>> = container.appPreferences.recentModels
    val renderSafeMode = container.crashReporter.renderSafeMode
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val repositoryUpdateState: StateFlow<RepositoryUpdateState> = container.repositoryUpdates.state
    val cloudOAuthStates: StateFlow<Map<CloudOAuthProvider, CloudOAuthState>> = container.cloudOAuth.states
    val directCloudConfigurations: StateFlow<DirectCloudConfigurationSnapshot> = container.directCloudConfigs.state
    val shareConversationId = MutableStateFlow<String?>(null)
    val incomingArchive = MutableStateFlow<IncomingArchiveState?>(null)
    private val _credentialRevision = MutableStateFlow(0L)
    val credentialRevision: StateFlow<Long> = _credentialRevision
    val openAiOAuthStates: StateFlow<Map<String, OpenAiOAuthState>> = container.openAiOAuth.accountStates
    val openAiOAuthUsageStates: StateFlow<Map<String, OpenAiOAuthUsageState>> = container.openAiOAuth.usageStates
    private val conversationSettingsMutex = Mutex()
    private val automationSettingsMutex = Mutex()
    private val initializationMutex = Mutex()
    private val _pythonRun = MutableStateFlow<PythonRunState?>(null)
    val pythonRun: StateFlow<PythonRunState?> = _pythonRun
    private val _linuxRun = MutableStateFlow<LinuxRunState?>(null)
    val linuxRun: StateFlow<LinuxRunState?> = _linuxRun
    private var pythonRunJob: Job? = null
    private var linuxRunJob: Job? = null
    @Volatile private var initialized = false

    init {
        viewModelScope.launch {
            combine(activeDraftConversationId.filterNotNull(), draft) { conversationId, text ->
                conversationId to text
            }
                .debounce(180)
                .collect { (conversationId, text) ->
                    withContext(Dispatchers.IO) { container.composerDrafts.write(conversationId, text) }
                }
        }
        viewModelScope.launch {
            chatScrollUpdates
                .debounce(180)
                .collect { (conversationId, snapshot) ->
                    container.persistentUiState.saveChatScroll(conversationId, snapshot)
                }
        }
        viewModelScope.launch {
            settingsScrollUpdates
                .debounce(180)
                .collect { (route, offset) ->
                    container.persistentUiState.saveSettingsScroll(route, offset)
                }
        }
        viewModelScope.launch {
            setupScrollUpdates
                .debounce(120)
                .collect { (stepIndex, offset) ->
                    container.persistentUiState.saveSetupScroll(stepIndex, offset)
                }
        }
        viewModelScope.launch {
            if (container.appPreferences.automaticUpdateChecks.value) {
                container.repositoryUpdates.checkIfDue()
            }
        }
        viewModelScope.launch {
            merge(
                selectedConversationId.map { Unit },
                newDraftConversationId.map { Unit },
                screen.map { Unit },
                settingsRoute.map { Unit },
                selectedProjectId.map { Unit },
                showArchived.map { Unit },
                searchQuery.map { Unit },
                focusedMessageNodeId.map { Unit },
                setupActive.map { Unit },
                setupStepIndex.map { Unit },
                setupPageOffsetFraction.map { Unit },
                setupTemporarilyAway.map { Unit },
                setupDismissed.map { Unit },
            )
                .debounce(120)
                .collect { persistSessionNow() }
        }
    }

    fun setRenderSafeMode(enabled: Boolean) = container.crashReporter.setRenderSafeMode(enabled)

    fun setLessEmojiEnabled(enabled: Boolean) = container.appPreferences.setLessEmojiEnabled(enabled)

    fun setAppLanguage(value: app.xylune.chat.settings.AppLanguage) = container.appPreferences.setAppLanguage(value)

    fun setAutomaticUpdateChecks(enabled: Boolean) {
        container.appPreferences.setAutomaticUpdateChecks(enabled)
        if (enabled) viewModelScope.launch { container.repositoryUpdates.checkIfDue() }
    }

    fun postNotice(message: String) {
        notices.tryEmit(message)
    }

    fun checkForUpdates() {
        viewModelScope.launch { container.repositoryUpdates.check() }
    }

    fun shouldPromptRepositoryUpdate(tagName: String): Boolean =
        container.repositoryUpdates.shouldPrompt(tagName)

    fun markRepositoryUpdatePrompted(tagName: String) =
        container.repositoryUpdates.markPrompted(tagName)

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

    fun connectedCloudFolderUri(): Uri? = container.scopedCloudFolder.connectedUri()

    fun connectedCloudFolderLabel(): String? = container.scopedCloudFolder.connectedLabel()

    fun connectCloudFolder(uri: Uri) = container.scopedCloudFolder.connect(uri)

    fun disconnectCloudFolder() = container.scopedCloudFolder.disconnect()

    suspend fun writeConnectedFolderBackup(options: ArchiveOptions, password: String): Uri {
        val file = container.archiveManager.writeBackupToCache(options, password)
        return try {
            container.scopedCloudFolder.saveBackup(file, file.name)
        } finally {
            file.delete()
        }
    }

    suspend fun listConnectedFolderBackups(): List<CloudBackupEntry> =
        container.scopedCloudFolder.listBackups()

    fun openConnectedFolderBackup(entry: CloudBackupEntry): Uri =
        container.scopedCloudFolder.open(entry)

    suspend fun deleteConnectedFolderBackup(entry: CloudBackupEntry) =
        container.scopedCloudFolder.deleteBackup(entry)

    suspend fun writeGoogleDriveBackup(
        accessToken: String,
        options: ArchiveOptions,
        password: String,
    ): CloudBackupEntry {
        val file = container.archiveManager.writeBackupToCache(options, password)
        return try {
            container.googleDriveAppData.uploadBackup(accessToken, file, file.name)
        } finally {
            file.delete()
        }
    }

    suspend fun listGoogleDriveBackups(accessToken: String): List<CloudBackupEntry> =
        container.googleDriveAppData.listBackups(accessToken)

    suspend fun downloadGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry): Uri =
        container.googleDriveAppData.downloadBackup(accessToken, entry)

    suspend fun deleteGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry) =
        container.googleDriveAppData.deleteBackup(accessToken, entry)

    fun directCloudBuildConfigured(provider: CloudOAuthProvider): Boolean =
        container.cloudOAuth.isBuildConfigured(provider)

    fun directCloudConfigurationReason(provider: CloudOAuthProvider): String? =
        container.cloudOAuth.configurationReason(provider)

    fun directCloudRedirectUri(provider: CloudOAuthProvider): String =
        container.cloudOAuth.redirectUri(provider)

    fun beginDirectCloudOAuth(provider: CloudOAuthProvider): Uri =
        container.cloudOAuth.beginAuthorization(provider)

    fun handleCloudOAuthRedirect(uri: Uri): Boolean {
        if (!container.cloudOAuth.canHandleRedirect(uri)) return false
        viewModelScope.launch {
            runCatching { container.cloudOAuth.completeRedirect(uri) }
                .onSuccess { session -> notices.emit("Connected ${session.provider.displayName}") }
                .onFailure { error -> notices.emit(error.message ?: "Cloud account connection failed") }
        }
        return true
    }

    fun saveWebDavCloud(config: WebDavCloudConfig) = container.directCloudConfigs.saveWebDav(config)
    fun saveS3Cloud(config: S3CloudConfig) = container.directCloudConfigs.saveS3(config)

    fun disconnectDirectCloud(provider: DirectCloudProvider) = container.directCloud.disconnect(provider)

    suspend fun testDirectCloud(provider: DirectCloudProvider): String = container.directCloud.test(provider)

    suspend fun writeDirectCloudBackup(
        provider: DirectCloudProvider,
        options: ArchiveOptions,
        password: String,
    ): CloudBackupEntry {
        val file = container.archiveManager.writeBackupToCache(options, password)
        return try {
            container.directCloud.upload(provider, file, file.name)
        } finally {
            file.delete()
        }
    }

    suspend fun listDirectCloudBackups(provider: DirectCloudProvider): List<CloudBackupEntry> =
        container.directCloud.list(provider)

    suspend fun downloadDirectCloudBackup(provider: DirectCloudProvider, entry: CloudBackupEntry): Uri =
        container.directCloud.download(provider, entry)

    suspend fun deleteDirectCloudBackup(provider: DirectCloudProvider, entry: CloudBackupEntry) =
        container.directCloud.delete(provider, entry)

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
                .onSuccess { result ->
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
                        ". Provider credentials and OAuth sessions were excluded; reconnect them from Settings > Finish setup or Providers & models"
                    } else ""
                    notices.tryEmit("Imported ${parts.joinToString(" and ")}$credentialNote")
                    if (result.linuxEnvironmentCount > 0) container.ubuntuRuntime.refresh()
                    if (result.settingsRestored) reconcileLauncherIcon()
                    if (restoredDuringSetup) {
                        setupPageOffsetFraction.value = 0f
                        setupTemporarilyAway.value = false
                        settingsRoute.value = SettingsRoute.HOME
                        screen.value = Screen.CHAT
                        if (result.settingsRestored) {
                            setupActive.value = false
                            setupStepIndex.value = 1
                            setupDismissed.value = true
                            notices.tryEmit("Backup restored. Setup was paused; finish provider access later from Settings.")
                        } else {
                            setupActive.value = true
                            setupDismissed.value = false
                        }
                    }
                }
                .onFailure { error -> incomingArchive.value = state.copy(importing = false, error = error.message ?: "Import failed") }
        }
    }

    fun dismissIncomingArchive() {
        incomingArchive.value = null
    }

    fun setDraft(value: String) {
        draft.value = value
    }

    suspend fun flushPersistentState() {
        persistCurrentDraft()
        persistSessionNow()
        synchronized(latestChatScrollSnapshots) {
            latestChatScrollSnapshots.forEach { (conversationId, snapshot) ->
                container.persistentUiState.saveChatScroll(conversationId, snapshot, immediate = true)
            }
        }
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets.forEach { (route, offset) ->
                container.persistentUiState.saveSettingsScroll(route, offset, immediate = true)
            }
        }
        synchronized(latestSetupScrollOffsets) {
            latestSetupScrollOffsets.forEach { (stepIndex, offset) ->
                container.persistentUiState.saveSetupScroll(stepIndex, offset, immediate = true)
            }
        }
    }

    fun chatScrollSnapshot(conversationId: String): PersistentUiStateStore.ChatScrollSnapshot? =
        container.persistentUiState.chatScroll(conversationId)

    fun saveChatScrollSnapshot(
        conversationId: String,
        anchorNodeId: String?,
        firstVisibleItemIndex: Int,
        firstVisibleItemOffset: Int,
        atLatest: Boolean,
        topBarHeightOffset: Float,
    ) {
        val snapshot = PersistentUiStateStore.ChatScrollSnapshot(
            anchorNodeId = anchorNodeId,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemOffset = firstVisibleItemOffset,
            atLatest = atLatest,
            topBarHeightOffset = topBarHeightOffset,
        )
        synchronized(latestChatScrollSnapshots) {
            latestChatScrollSnapshots[conversationId] = snapshot
        }
        chatScrollUpdates.tryEmit(conversationId to snapshot)
    }

    fun settingsScrollOffset(route: SettingsRoute): Int =
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets[route]
        } ?: container.persistentUiState.settingsScroll(route)

    fun openSettingsRoute(route: SettingsRoute) {
        settingsPageRevisions.update { revisions ->
            revisions + (route to ((revisions[route] ?: 0L) + 1L))
        }
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets[route] = 0
        }
        container.persistentUiState.saveSettingsScroll(route, 0)
        settingsRoute.value = route
    }

    fun openSettingsHome() {
        openSettingsRoute(SettingsRoute.HOME)
        screen.value = Screen.SETTINGS
    }

    fun saveSettingsScrollOffset(route: SettingsRoute, offset: Int) {
        val normalized = offset.coerceAtLeast(0)
        synchronized(latestSettingsScrollOffsets) {
            latestSettingsScrollOffsets[route] = normalized
        }
        settingsScrollUpdates.tryEmit(route to normalized)
    }

    fun setupScrollOffset(stepIndex: Int): Int =
        synchronized(latestSetupScrollOffsets) {
            latestSetupScrollOffsets[stepIndex]
        } ?: container.persistentUiState.setupScroll(stepIndex)

    fun saveSetupScrollOffset(stepIndex: Int, offset: Int) {
        val normalizedStep = stepIndex.coerceIn(0, 2)
        val normalizedOffset = offset.coerceAtLeast(0)
        synchronized(latestSetupScrollOffsets) {
            latestSetupScrollOffsets[normalizedStep] = normalizedOffset
        }
        setupScrollUpdates.tryEmit(normalizedStep to normalizedOffset)
    }

    fun updateSetupPagerPosition(stepIndex: Int, offsetFraction: Float) {
        setupStepIndex.value = stepIndex.coerceIn(0, 2)
        setupPageOffsetFraction.value = offsetFraction.coerceIn(-0.499f, 0.499f)
    }

    private suspend fun persistCurrentDraft() {
        val conversationId = activeDraftConversationId.value ?: return
        withContext(Dispatchers.IO) {
            container.composerDrafts.write(conversationId, draft.value)
        }
    }

    private suspend fun switchDraftContext(conversationId: String?) {
        persistCurrentDraft()
        activeDraftConversationId.value = conversationId
        draft.value = conversationId?.let(container.composerDrafts::read).orEmpty()
    }

    private fun persistSessionNow() {
        container.persistentUiState.saveSession(
            selectedConversationId = selectedConversationId.value,
            newDraftConversationId = newDraftConversationId.value,
            screen = screen.value,
            settingsRoute = settingsRoute.value,
            selectedProjectId = selectedProjectId.value,
            showArchived = showArchived.value,
            searchQuery = searchQuery.value,
            focusedMessageNodeId = focusedMessageNodeId.value,
            setupActive = setupActive.value,
            setupStepIndex = setupStepIndex.value,
            setupPageOffsetFraction = setupPageOffsetFraction.value,
            setupTemporarilyAway = setupTemporarilyAway.value,
            setupDismissed = setupDismissed.value,
        )
    }

    fun startSetup(stepIndex: Int = 0) {
        setupStepIndex.value = stepIndex.coerceIn(0, 2)
        setupPageOffsetFraction.value = 0f
        setupActive.value = true
        setupDismissed.value = false
        setupTemporarilyAway.value = false
        settingsRoute.value = SettingsRoute.HOME
        screen.value = Screen.CHAT
    }

    fun skipSetup() {
        setupActive.value = false
        setupPageOffsetFraction.value = 0f
        setupDismissed.value = true
        setupTemporarilyAway.value = false
        settingsRoute.value = SettingsRoute.HOME
        screen.value = Screen.CHAT
    }

    fun finishSetup() {
        setupActive.value = false
        setupStepIndex.value = 2
        setupPageOffsetFraction.value = 0f
        setupDismissed.value = true
        setupTemporarilyAway.value = false
        settingsRoute.value = SettingsRoute.HOME
        screen.value = Screen.CHAT
    }

    fun openProviderSetupFromSetup() {
        setupActive.value = true
        setupStepIndex.value = 1
        setupPageOffsetFraction.value = 0f
        setupTemporarilyAway.value = true
        settingsRoute.value = SettingsRoute.PROVIDERS
        providerSetupRequested.value = true
        screen.value = Screen.SETTINGS
    }

    fun returnToSetup() {
        setupTemporarilyAway.value = false
        settingsRoute.value = SettingsRoute.HOME
        screen.value = Screen.CHAT
    }


    fun startPythonRun(code: String, timeoutSeconds: Int) {
        if (_pythonRun.value?.running == true || code.isBlank()) return
        val conversationId = selectedConversationId.value ?: draftConversation.value?.id ?: return
        val started = System.currentTimeMillis()
        _pythonRun.value = PythonRunState(started, code, timeoutSeconds)
        pythonRunJob = viewModelScope.launch {
            try {
                val result = container.pythonSandbox.execute(conversationId, code, timeoutSeconds)
                _pythonRun.value = _pythonRun.value?.copy(running = false, result = result)
            } catch (cancelled: CancellationException) {
                _pythonRun.value = _pythonRun.value?.copy(running = false, error = "Stopped by user")
            } catch (error: Throwable) {
                _pythonRun.value = _pythonRun.value?.copy(running = false, error = error.stackTraceToString())
            }
        }
    }

    fun stopPythonRun() {
        val conversationId = selectedConversationId.value ?: draftConversation.value?.id
        conversationId?.let(container.pythonSandbox::requestCancel)
        pythonRunJob?.cancel()
    }

    fun clearPythonRun() {
        if (_pythonRun.value?.running != true) _pythonRun.value = null
    }

    private fun localWorkspaceConversationId(): String =
        selectedConversationId.value ?: draftConversation.value?.id ?: error("No chat workspace is available")

    fun startLinuxRun(command: String, timeoutSeconds: Int) {
        if (_linuxRun.value?.running == true || command.isBlank()) return
        val conversationId = selectedConversationId.value ?: draftConversation.value?.id ?: return
        val started = System.currentTimeMillis()
        _linuxRun.value = LinuxRunState(started, command, container.ubuntuRuntime.distribution.value, timeoutSeconds)
        linuxRunJob = viewModelScope.launch {
            try {
                val result = container.ubuntuRuntime.execute(conversationId, command, timeoutSeconds) { progress ->
                    _linuxRun.value = _linuxRun.value?.copy(progress = progress)
                }
                _linuxRun.value = _linuxRun.value?.copy(running = false, result = result)
            } catch (cancelled: CancellationException) {
                _linuxRun.value = _linuxRun.value?.copy(running = false, error = "Stopped by user")
            } catch (error: Throwable) {
                _linuxRun.value = _linuxRun.value?.copy(running = false, error = error.stackTraceToString())
            }
        }
    }

    fun stopLinuxRun() {
        linuxRunJob?.cancel()
    }

    fun clearLinuxRun() {
        if (_linuxRun.value?.running != true) _linuxRun.value = null
    }

    val conversation: StateFlow<ConversationEntity?> = selectedConversationId
        .flatMapLatest { id -> id?.let(container.repository::conversation) ?: draftConversation }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages = combine(selectedConversationId, focusedMessageIndex) { id, index -> id to index }
        .flatMapLatest { (id, index) -> id?.let { container.repository.messages(it, index) } ?: flowOf(PagingData.empty()) }
        .cachedIn(viewModelScope)

    val recoverable = selectedConversationId
        .flatMapLatest { id -> id?.let(container.repository::recoverable) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val revisionHistory = selectedConversationId
        .flatMapLatest { id -> id?.let(container.repository::history) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contextSummary = selectedConversationId
        .flatMapLatest { id -> id?.let(container.repository::observeContextSummary) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pending = selectedConversationId
        .flatMapLatest { id -> id?.let(container.repository::pending) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val providers = container.repository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _providerCatalogReady = MutableStateFlow(false)
    val providerCatalogReady: StateFlow<Boolean> = _providerCatalogReady
    val providerSetupRequested = savedStateHandle.getMutableStateFlow("provider_setup_requested", false)

    init {
        viewModelScope.launch {
            container.repository.observeProviders().first { it.isNotEmpty() }
            _providerCatalogReady.value = true
        }
    }

    val models = conversation.flatMapLatest { current ->
        current?.selectedProviderId?.let(container.repository::observeModels) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchResults = searchQuery.flatMapLatest { query ->
        if (query.isBlank()) flowOf(emptyList()) else container.repository.search(query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isGenerating = recoverable.map { rows -> rows.any { it.status == MessageStatus.STREAMING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun receiveIntent(preferredConversationId: String?, uris: List<Uri>) = launchAction {
        val existingTarget = ensureInitialized(preferredConversationId)
        if (uris.isNotEmpty()) {
            val target = existingTarget ?: materializeDraft()
            importing.value = true
            try {
                val imported = uris.map { container.attachmentStore.import(target, it) }
                stagedAttachments.value = container.database.attachmentDao().stagedForConversation(target)
                notices.emit("Attached ${imported.size} file${if (imported.size == 1) "" else "s"}")
            } finally {
                importing.value = false
            }
        }
    }

    private suspend fun ensureInitialized(preferredConversationId: String?): String? = initializationMutex.withLock {
        val preferred = preferredConversationId?.let { container.repository.conversationNow(it) }
        val restored = selectedConversationId.value?.let { container.repository.conversationNow(it) }
        val restoredNewDraft = if (preferred == null && restored == null) {
            newDraftConversationId.value?.let { container.repository.conversationNow(it) }
        } else null
        val fallback = if (preferred == null && restored == null && restoredNewDraft == null) {
            container.repository.conversations.first().firstOrNull()?.conversation
        } else null
        val target = preferred ?: restored ?: fallback

        if (target != null && !container.appPreferences.hasNewChatDefaults) {
            container.appPreferences.setNewChatDefaults(NewChatDefaults.from(target))
        }
        if (target == null) {
            val value = restoredNewDraft ?: container.repository.newConversationDraft(
                projectId = selectedProjectId.value.takeUnless { showArchived.value },
                defaults = newChatDefaults.value,
            ).also { container.repository.persistConversationDraft(it) }
            selectedConversationId.value = null
            newDraftConversationId.value = value.id
            draftConversation.value = value
            switchDraftContext(value.id)
            stagedAttachments.value = container.database.attachmentDao().stagedForConversation(value.id)
        } else {
            container.repository.repairActiveMessagePath(target.id)
            draftConversation.value = null
            selectedConversationId.value = target.id
            switchDraftContext(target.id)
            stagedAttachments.value = container.database.attachmentDao().stagedForConversation(target.id)
            container.repository.markRead(target.id)
            val savedScroll = container.persistentUiState.chatScroll(target.id)
            if (focusedMessageNodeId.value == null && savedScroll?.atLatest == false) {
                focusedMessageIndex.value = savedScroll.anchorNodeId?.let {
                    container.repository.messageIndexFromLatest(target.id, it)
                }
            }
        }
        initialized = true
        persistSessionNow()
        target?.id
    }

    private suspend fun materializeDraft(): String {
        selectedConversationId.value?.let { return it }
        val value = draftConversation.value ?: container.repository.newConversationDraft(
            projectId = selectedProjectId.value.takeUnless { showArchived.value },
            defaults = newChatDefaults.value,
        )
        container.repository.persistConversationDraft(value)
        persistCurrentDraft()
        draftConversation.value = null
        newDraftConversationId.value = null
        selectedConversationId.value = value.id
        activeDraftConversationId.value = value.id
        return value.id
    }

    private suspend fun openEmptyDraft() {
        persistCurrentDraft()
        val existing = newDraftConversationId.value?.let { container.repository.conversationNow(it) }
        val value = existing ?: container.repository.newConversationDraft(
            projectId = selectedProjectId.value.takeUnless { showArchived.value },
            defaults = newChatDefaults.value,
        ).also { container.repository.persistConversationDraft(it) }
        selectedConversationId.value = null
        newDraftConversationId.value = value.id
        draftConversation.value = value
        switchDraftContext(value.id)
        stagedAttachments.value = container.database.attachmentDao().stagedForConversation(value.id)
        focusedMessageNodeId.value = null
        focusedMessageIndex.value = null
        screen.value = Screen.CHAT
    }

    fun selectConversation(id: String) = launchAction {
        container.repository.repairActiveMessagePath(id)
        switchDraftContext(id)
        draftConversation.value = null
        focusedMessageNodeId.value = null
        focusedMessageIndex.value = null
        selectedConversationId.value = id
        screen.value = Screen.CHAT
        container.repository.markRead(id)
        stagedAttachments.value = container.database.attachmentDao().stagedForConversation(id)
    }

    fun openSearchResult(conversationId: String, nodeId: String) = launchAction {
        container.repository.repairActiveMessagePath(conversationId)
        switchDraftContext(conversationId)
        focusedMessageNodeId.value = nodeId
        focusedMessageIndex.value = container.repository.messageIndexFromLatest(conversationId, nodeId)
        selectedConversationId.value = conversationId
        screen.value = Screen.CHAT
        container.repository.markRead(conversationId)
        stagedAttachments.value = container.database.attachmentDao().stagedForConversation(conversationId)
    }

    fun newConversation() = launchAction {
        showArchived.value = false
        openEmptyDraft()
    }

    fun deleteConversation(id: String) = launchAction {
        container.scheduler.stopConversation(id)
        container.repository.activeStreams(id).forEach { container.repository.markInterrupted(it.nodeId, "Conversation deleted") }
        container.attachmentStore.deleteConversationFiles(id)
        container.pythonSandbox.deleteWorkspace(id)
        container.repository.deleteConversation(id)
        container.composerDrafts.remove(id)
        container.persistentUiState.clearChatScroll(id)
        if (newDraftConversationId.value == id) newDraftConversationId.value = null
        if (selectedConversationId.value == id) {
            val fallback = container.repository.conversations.first().firstOrNull()?.conversation?.id
            if (fallback != null) selectConversation(fallback) else openEmptyDraft()
        }
    }

    fun renameConversation(id: String, title: String) = launchAction {
        container.repository.renameConversation(id, title)
    }

    fun archiveConversation(id: String, archived: Boolean) = launchAction {
        container.repository.archiveConversation(id, archived)
        if (archived && selectedConversationId.value == id) {
            val fallback = container.repository.conversations.first().firstOrNull()?.conversation?.id
            if (fallback != null) selectConversation(fallback) else openEmptyDraft()
        }
    }

    fun pinConversation(id: String, pinned: Boolean) = launchAction {
        container.repository.pinConversation(id, pinned)
    }

    fun moveConversation(id: String, projectId: String?) = launchAction {
        container.repository.moveConversation(id, projectId)
    }

    fun createProject(name: String, moveConversationId: String? = null) = launchAction {
        val project = container.repository.createProject(name)
        if (moveConversationId != null) container.repository.moveConversation(moveConversationId, project.id)
    }

    fun renameProject(id: String, name: String) = launchAction { container.repository.renameProject(id, name) }
    fun deleteProject(id: String) = launchAction {
        container.repository.deleteProject(id)
        if (selectedProjectId.value == id) selectedProjectId.value = null
    }

    fun import(uri: Uri) = launchAction {
        val id = selectedConversationId.value ?: materializeDraft()
        importing.value = true
        try {
            stagedAttachments.value += container.attachmentStore.import(id, uri)
        } finally {
            importing.value = false
        }
    }

    fun enableOcr(attachment: AttachmentEntity) = launchAction {
        importing.value = true
        try {
            val analyzed = container.ocrEngine.analyze(attachment)
            stagedAttachments.value = stagedAttachments.value.map { if (it.id == analyzed.id) analyzed else it }
            notices.emit("OCR fallback is ready for ${attachment.displayName}")
        } finally {
            importing.value = false
        }
    }

    fun removeStaged(id: String) {
        stagedAttachments.value = stagedAttachments.value.filterNot { it.id == id }
        launchAction { container.attachmentStore.removeStaged(id) }
    }

    fun send(mode: SendMode? = null) = viewModelScope.launch {
        if (importing.value) return@launch
        val text = draft.value.trim()
        val originalAttachments = stagedAttachments.value
        if (text.isBlank() && originalAttachments.isEmpty()) return@launch
        val id = selectedConversationId.value ?: materializeDraft()
        val selectedModel = conversation.value?.selectedModelId?.let { modelId -> models.value.firstOrNull { it.modelId == modelId } }
        var attachments = originalAttachments
        val needsFallback = selectedModel != null && attachments.any { attachment ->
            (attachment.mimeType.startsWith("image/") && attachment.mimeType != "image/svg+xml" && !selectedModel.supportsVision && attachment.ocrJson == null) ||
                (attachment.mimeType == "application/pdf" && !selectedModel.supportsFiles && attachment.ocrJson == null)
        }
        if (needsFallback) {
            importing.value = true
            attachments = attachments.map { attachment ->
                val needsOcr = (attachment.mimeType.startsWith("image/") && attachment.mimeType != "image/svg+xml" && selectedModel?.supportsVision == false) ||
                    (attachment.mimeType == "application/pdf" && selectedModel?.supportsFiles == false)
                if (needsOcr && attachment.ocrJson == null) runCatching { container.ocrEngine.analyze(attachment) }
                    .onFailure { notices.emit("OCR could not read ${attachment.displayName}; the original file is still attached") }
                    .getOrDefault(attachment)
                else attachment
            }
            importing.value = false
        }
        val effectiveMode = mode ?: if (container.repository.activeStream(id) != null) SendMode.QUEUE else SendMode.SEND_NOW
        runCatching { container.scheduler.submit(id, text, attachments.map { it.id }, effectiveMode) }
            .onSuccess {
                setDraft("")
                withContext(Dispatchers.IO) { container.composerDrafts.remove(id) }
                stagedAttachments.value = emptyList()
            }
            .onFailure { error ->
                setDraft(text)
                stagedAttachments.value = attachments
                notices.emit("Could not send: ${error.readableMessage()}")
            }
    }

    fun resume(message: MessageEntity) = launchAction {
        container.scheduler.resume(message.conversationId, message.nodeId)
    }

    fun stop() = launchAction {
        val id = selectedConversationId.value ?: return@launchAction
        container.scheduler.stopConversation(id)
        container.repository.activeStreams(id).forEach { container.repository.markInterrupted(it.nodeId) }
    }

    fun editMessage(message: MessageEntity, content: String) = launchAction {
        val revised = content.trim()
        if (revised.isBlank() || revised == message.content) return@launchAction
        container.scheduler.stopConversation(message.conversationId)
        container.repository.activeStreams(message.conversationId).forEach { active ->
            container.repository.markInterrupted(active.nodeId, "Replaced by an edited message")
        }
        val assistantId = container.repository.editUserMessage(message.nodeId, revised)
        container.scheduler.start(message.conversationId, assistantId, continuation = false)
    }

    fun activateBranch(message: MessageEntity) = launchAction {
        container.scheduler.stopConversation(message.conversationId)
        container.repository.activeStreams(message.conversationId).forEach { active ->
            container.repository.markInterrupted(active.nodeId, "Switched to another branch")
        }
        container.repository.activateBranch(message.nodeId)
        focusedMessageNodeId.value = message.nodeId
        focusedMessageIndex.value = container.repository.messageIndexFromLatest(message.conversationId, message.nodeId)
        notices.emit("Switched branch")
    }

    fun retryMessage(message: MessageEntity) = launchAction {
        container.scheduler.stopConversation(message.conversationId)
        container.repository.activeStreams(message.conversationId).forEach { active ->
            container.repository.markInterrupted(active.nodeId, "Replaced by retry")
        }
        val assistantId = container.repository.retryAssistant(message.nodeId)
        container.scheduler.start(message.conversationId, assistantId, continuation = false)
    }

    fun submitWidgetResponse(text: String) {
        setDraft(text)
        send()
    }

    suspend fun reviewWidgetSecurity(source: String): String {
        val id = selectedConversationId.value ?: error("No conversation")
        return container.auxiliaryModels.reviewWidgetSecurity(id, source)
    }

    fun selectModel(providerId: String, modelId: String) {
        container.appPreferences.recordRecentModel(providerId, modelId)
        viewModelScope.launch {
            val model = container.repository.model(providerId, modelId)
            updateConversation { current ->
                current.copy(
                    selectedProviderId = providerId,
                    selectedModelId = modelId,
                    thinkingEnabled = model?.let {
                        if (it.reasoningMetadataAvailable) effectiveThinkingEnabled(it, it.reasoningDefaultEnabled)
                        else if (!it.supportsThinking) false else current.thinkingEnabled
                    } ?: current.thinkingEnabled,
                    thinkingEffort = model?.takeIf { it.reasoningMetadataAvailable }
                        ?.let { defaultThinkingEffort(it, current.thinkingEffort) } ?: current.thinkingEffort,
                )
            }
        }
    }

    fun selectDefaultModel(providerId: String, modelId: String) {
        container.appPreferences.recordRecentModel(providerId, modelId)
        viewModelScope.launch {
            val model = container.repository.model(providerId, modelId)
            updateNewChatDefaults { current ->
                current.copy(
                    selectedProviderId = providerId,
                    selectedModelId = modelId,
                    thinkingEnabled = model?.let {
                        if (it.reasoningMetadataAvailable) effectiveThinkingEnabled(it, it.reasoningDefaultEnabled)
                        else if (!it.supportsThinking) false else current.thinkingEnabled
                    } ?: current.thinkingEnabled,
                    thinkingEffort = model?.takeIf { it.reasoningMetadataAvailable }
                        ?.let { defaultThinkingEffort(it, current.thinkingEffort) } ?: current.thinkingEffort,
                )
            }
        }
    }

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        container.appPreferences.toggleFavoriteModel(providerId, modelId)

    fun updateConversation(transform: (ConversationEntity) -> ConversationEntity) {
        val id = selectedConversationId.value
        if (id == null) {
            val updated = draftConversation.value?.let(transform)?.copy(updatedAt = System.currentTimeMillis())
            draftConversation.value = updated
            updated?.let { value ->
                container.appPreferences.setNewChatDefaults(NewChatDefaults.from(value))
                viewModelScope.launch(Dispatchers.IO) { container.repository.persistConversationDraft(value) }
            }
            return
        }
        launchAction {
            conversationSettingsMutex.withLock {
                val current = container.repository.conversationNow(id) ?: return@withLock
                val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
                container.repository.saveConversation(updated)
                container.appPreferences.setNewChatDefaults(NewChatDefaults.from(updated))
            }
        }
    }

    fun updateNewChatDefaults(transform: (NewChatDefaults) -> NewChatDefaults) {
        container.appPreferences.updateNewChatDefaults(transform)
    }

    fun applyNewChatDefaultsToCurrent() = updateConversation { defaults ->
        newChatDefaults.value.applyTo(defaults)
    }

    fun saveProvider(provider: ProviderEntity, apiKey: String) = launchAction {
        val validatedUrl = ProviderEndpointPolicy.validate(provider.baseUrl)
        parseHeaders(provider.customHeadersJson)
        container.secureStore.setApiKey(provider.id, apiKey)
        container.repository.saveProvider(provider.copy(baseUrl = validatedUrl, registered = true))
        _credentialRevision.value++
    }

    fun removeProvider(provider: ProviderEntity) = launchAction {
        if (provider.kind == ProviderKind.OPENAI_OAUTH) container.openAiOAuth.signOut(provider.id)
        container.secureStore.setApiKey(provider.id, "")
        container.repository.saveProvider(provider.copy(registered = false))
        _credentialRevision.value++
        notices.emit("Removed ${provider.displayName} credentials")
    }

    fun observeAttachments(nodeId: String) = container.repository.observeAttachments(nodeId)

    fun useProvider(providerId: String) = launchAction {
        val firstModel = container.repository.observeModels(providerId).first().firstOrNull() ?: return@launchAction
        selectModel(providerId, firstModel.modelId)
    }

    fun apiKey(providerId: String): String = container.secureStore.apiKey(providerId)

    fun addChatGptProvider(provider: ProviderEntity) = launchAction {
        require(provider.kind == ProviderKind.OPENAI_OAUTH)
        require(provider.displayName.isNotBlank()) { "Provider name is required" }
        val saved = provider.copy(
            baseUrl = OpenAiOAuthManager.CODEX_BASE_URL,
            registered = true,
            apiKeyRequired = false,
        )
        container.repository.saveProvider(saved)
        _credentialRevision.value++
        notices.emit("Added ${saved.displayName}. Opening ChatGPT sign-in…")
        connectChatGptProvider(saved)
    }

    fun signInWithChatGpt(providerId: String) = launchAction {
        val provider = requireNotNull(container.repository.provider(providerId))
        require(provider.kind == ProviderKind.OPENAI_OAUTH)
        connectChatGptProvider(provider)
    }

    private suspend fun connectChatGptProvider(provider: ProviderEntity) {
        val providerId = provider.id
        container.openAiOAuth.signIn(providerId) ?: return
        container.secureStore.setApiKey(providerId, OAUTH_CREDENTIAL_MARKER)
        container.repository.saveProvider(provider.copy(registered = true, apiKeyRequired = false))
        val discovered = container.openAiOAuth.modelCatalog(providerId, forceRefresh = true).map { model ->
            app.xylune.chat.provider.DiscoveredModel(
                id = model.id,
                displayName = model.displayName,
                contextWindow = model.contextWindow,
                maxOutputTokens = model.maxOutputTokens,
                supportsThinking = model.supportsThinking,
                supportsVision = true,
                supportsFiles = false,
                supportsTools = true,
                supportsImageGeneration = model.supportsImageGeneration,
            )
        }
        saveDiscoveredModels(providerId, discovered)
        _credentialRevision.value++
        runCatching { container.openAiOAuth.usage(providerId, forceRefresh = true) }
        notices.emit("Connected ${provider.displayName} • ${discovered.size} models available")
        if (setupTemporarilyAway.value) screen.value = Screen.CHAT
    }

    fun cancelChatGptSignIn(providerId: String) {
        container.openAiOAuth.cancelSignIn(providerId)
    }

    fun signOutFromChatGpt(providerId: String) = launchAction {
        val name = container.repository.provider(providerId)?.displayName ?: "ChatGPT account"
        container.openAiOAuth.signOut(providerId)
        container.secureStore.setApiKey(providerId, "")
        _credentialRevision.value++
        notices.emit("Disconnected $name")
    }

    fun refreshChatGptModels(providerId: String) = launchAction {
        val provider = requireNotNull(container.repository.provider(providerId))
        val discovered = container.openAiOAuth.modelCatalog(providerId, forceRefresh = true).map { model ->
            app.xylune.chat.provider.DiscoveredModel(
                id = model.id,
                displayName = model.displayName,
                contextWindow = model.contextWindow,
                maxOutputTokens = model.maxOutputTokens,
                supportsThinking = model.supportsThinking,
                supportsVision = true,
                supportsFiles = false,
                supportsTools = true,
                supportsImageGeneration = model.supportsImageGeneration,
            )
        }
        saveDiscoveredModels(providerId, discovered)
        notices.emit("Updated ${discovered.size} models for ${provider.displayName}")
    }

    fun ensureChatGptUsage(providerId: String) {
        viewModelScope.launch {
            runCatching { container.openAiOAuth.usage(providerId, forceRefresh = false) }
        }
    }

    fun refreshChatGptUsage(providerId: String) {
        viewModelScope.launch {
            runCatching { container.openAiOAuth.usage(providerId, forceRefresh = true) }
                .onFailure { notices.emit(it.message ?: "ChatGPT usage could not be refreshed") }
        }
    }

    fun registeredProviders(values: List<ProviderEntity>): List<ProviderEntity> =
        values.filter { ProviderCredentialPolicy.isRegistered(it, container.secureStore.apiKey(it.id)) }

    fun configuredProviders(values: List<ProviderEntity>): List<ProviderEntity> =
        values.filter { provider ->
            if (provider.kind == ProviderKind.OPENAI_OAUTH) {
                provider.registered && provider.enabled && container.openAiOAuth.signedInAccountId(provider.id) != null
            } else {
                ProviderCredentialPolicy.isUsable(provider, container.secureStore.apiKey(provider.id))
            }
        }

    fun openProviderSetup() {
        openSettingsRoute(SettingsRoute.PROVIDERS)
        providerSetupRequested.value = true
        screen.value = Screen.SETTINGS
    }

    fun consumeProviderSetupRequest() {
        providerSetupRequested.value = false
    }

    suspend fun discoverModels(kind: ProviderKind, baseUrl: String, apiKey: String, headers: String) =
        container.modelDiscovery.discover(kind, baseUrl, apiKey, headers)

    fun addProvider(provider: ProviderEntity, apiKey: String, initialModels: List<ModelEntity>) = launchAction {
        require(provider.displayName.isNotBlank()) { "Provider name is required" }
        val validatedUrl = ProviderEndpointPolicy.validate(provider.baseUrl)
        parseHeaders(provider.customHeadersJson)
        require(initialModels.isNotEmpty() && initialModels.all { it.modelId.isNotBlank() }) { "At least one model is required" }
        if (provider.apiKeyRequired) require(apiKey.isNotBlank()) { "API key is required" }
        container.secureStore.setApiKey(provider.id, apiKey)
        container.repository.saveProvider(provider.copy(baseUrl = validatedUrl, registered = true))
        initialModels.distinctBy { it.modelId }.forEach { container.repository.saveModel(ModelRequestPolicy.normalize(provider, it)) }
        _credentialRevision.value++
        notices.emit("Added ${provider.displayName}")
        if (setupTemporarilyAway.value) screen.value = Screen.CHAT
    }

    suspend fun saveDiscoveredModels(providerId: String, discovered: List<app.xylune.chat.provider.DiscoveredModel>) {
        require(discovered.isNotEmpty()) { "The provider returned no models" }
        val provider = requireNotNull(container.repository.provider(providerId)) { "Provider is missing" }
        val metadataUpdatedAt = System.currentTimeMillis()
        val models = discovered.map { candidate ->
            val bundled = DefaultCatalog.models.firstOrNull { it.providerId == providerId && it.modelId == candidate.id }
            val existing = container.repository.model(providerId, candidate.id)
            val base = existing ?: bundled ?: ModelEntity(
                providerId = providerId,
                modelId = candidate.id,
                displayName = candidate.displayName,
                contextWindow = candidate.contextWindow ?: 128_000,
                maxOutputTokens = candidate.maxOutputTokens ?: 16_384,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
                pricingConfigured = false,
            )
            ModelRequestPolicy.normalize(provider, base.copy(
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
                description = candidate.description.ifBlank { base.description },
                createdAtEpochSeconds = candidate.createdAtEpochSeconds.takeIf { it > 0 } ?: base.createdAtEpochSeconds,
                reasoningMetadataAvailable = candidate.reasoningMetadataAvailable || base.reasoningMetadataAvailable,
                reasoningEffortsCsv = if (candidate.reasoningMetadataAvailable) {
                    candidate.reasoningEfforts.joinToString(",") { it.name }
                } else base.reasoningEffortsCsv,
                reasoningDefaultEffort = candidate.reasoningDefaultEffort?.name ?: base.reasoningDefaultEffort,
                reasoningDefaultEnabled = if (candidate.reasoningMetadataAvailable) candidate.reasoningDefaultEnabled else base.reasoningDefaultEnabled,
                reasoningMandatory = if (candidate.reasoningMetadataAvailable) candidate.reasoningMandatory else base.reasoningMandatory,
                reasoningSupportsMaxTokens = if (candidate.reasoningMetadataAvailable) candidate.reasoningSupportsMaxTokens else base.reasoningSupportsMaxTokens,
                metadataSource = candidate.metadataSource.ifBlank { base.metadataSource },
                metadataUpdatedAt = if (candidate.metadataSource.isNotBlank()) metadataUpdatedAt else base.metadataUpdatedAt,
            ))
        }
        container.repository.mergeModels(models.distinctBy { it.modelId })
    }

    fun saveModel(model: ModelEntity) = launchAction {
        val provider = requireNotNull(container.repository.provider(model.providerId)) { "Provider is missing" }
        container.repository.saveModel(ModelRequestPolicy.normalize(provider, model))
    }

    fun modelsFor(providerId: String) = container.repository.observeModels(providerId)

    fun createSystemPromptProfile(name: String, prompt: String, mode: SystemPromptMode, selectForNewChats: Boolean = true) = launchAction {
        val profile = container.repository.createSystemPromptProfile(name, prompt, mode)
        if (selectForNewChats) updateNewChatDefaults { it.copy(systemPromptProfileId = profile.id) }
        notices.emit("Saved system prompt “${profile.name}”")
    }

    fun updateSystemPromptProfile(value: SystemPromptProfileEntity) = launchAction {
        container.repository.updateSystemPromptProfile(value)
        notices.emit("Updated system prompt “${value.name}”")
    }

    fun deleteSystemPromptProfile(id: String) = launchAction {
        container.repository.deleteSystemPromptProfile(id)
        if (newChatDefaults.value.systemPromptProfileId == id) updateNewChatDefaults { it.copy(systemPromptProfileId = null) }
        notices.emit("Deleted system prompt")
    }

    fun selectSystemPromptProfileForCurrent(id: String?) = updateConversation { it.copy(systemPromptProfileId = id) }

    fun addMemory(content: String, category: String = "general") = launchAction {
        container.repository.saveMemory(content, category, selectedConversationId.value)
    }

    fun updateMemory(id: String, content: String, category: String) = launchAction {
        container.repository.updateMemory(id, content, category)
    }

    fun deleteMemory(id: String) = launchAction { container.repository.deleteMemory(id) }
    fun setMemoryEnabled(id: String, enabled: Boolean) = launchAction {
        container.repository.setMemoryEnabled(id, enabled)
    }
    fun setAllMemoriesEnabled(enabled: Boolean) = launchAction {
        container.repository.setAllMemoriesEnabled(enabled)
    }
    fun setMemoriesEnabled(ids: Set<String>, enabled: Boolean) = launchAction {
        container.repository.setMemoriesEnabled(ids, enabled)
    }
    fun deleteMemories(ids: Set<String>) = launchAction {
        container.repository.deleteMemories(ids)
    }
    fun deleteDisabledMemories() = launchAction {
        container.repository.deleteDisabledMemories()
    }

    fun updateAutomationSettings(transform: (AutomationSettingsEntity) -> AutomationSettingsEntity) = launchAction {
        automationSettingsMutex.withLock {
            container.repository.saveAutomationSettings(transform(container.repository.automationSettingsNow()))
        }
    }

    fun setAmoled(enabled: Boolean) = container.appPreferences.setAmoled(enabled)

    fun setPalette(value: app.xylune.chat.settings.ColorPalette) {
        container.appPreferences.setPalette(value)
        requestLauncherRestartIfNeeded()
    }

    fun setMatchLauncherIconToPalette(enabled: Boolean) {
        container.appPreferences.setMatchLauncherIconToPalette(enabled)
        requestLauncherRestartIfNeeded()
    }

    fun setThemeMode(value: app.xylune.chat.settings.ThemeMode) = container.appPreferences.setThemeMode(value)

    fun reconcileLauncherIcon() = requestLauncherRestartIfNeeded()

    private fun requestLauncherRestartIfNeeded() {
        val match = matchLauncherIconToPalette.value
        val currentPalette = palette.value
        if (!LauncherIconManager.needsChange(container.application, match, currentPalette)) return
        launcherRestartRequests.tryEmit(LauncherIconManager.aliasClassName(match, currentPalette))
    }
    fun setChromeBlurStrength(value: Float) = container.appPreferences.setChromeBlurStrength(value)
    fun setChromeEdgeSoftness(value: Float) = container.appPreferences.setChromeEdgeSoftness(value)
    fun setChromeOverlayOpacity(value: Float) = container.appPreferences.setChromeOverlayOpacity(value)
    fun setGeneratedRepairMaxAttempts(value: Int) = container.appPreferences.setGeneratedRepairMaxAttempts(value)
    fun updateDeveloperSettings(transform: (app.xylune.chat.settings.DeveloperSettings) -> app.xylune.chat.settings.DeveloperSettings) =
        container.appPreferences.updateDeveloperSettings(transform)

    fun clearContextSummary() = launchAction {
        val id = selectedConversationId.value
        if (id != null) container.repository.clearContextSummary(id)
        notices.emit("Compressed context cleared")
    }

    fun compressContextNow() = launchAction {
        val id = selectedConversationId.value ?: return@launchAction
        val current = container.repository.conversationNow(id) ?: return@launchAction
        val summary = container.auxiliaryModels.prepareContextSummary(current, container.repository.recent(id))
        notices.emit(
            if (summary == null) "No context was compressed"
            else "Compressed ${summary.sourceMessageCount} older messages",
        )
    }

    fun regenerateTitle() = launchAction {
        val id = selectedConversationId.value ?: return@launchAction
        val title = container.auxiliaryModels.regenerateTitle(id)
        notices.emit("Chat renamed to “$title”")
    }

    fun markCurrentRead() {
        val id = selectedConversationId.value ?: return
        launchAction { container.repository.markRead(id) }
    }

    suspend fun executePython(code: String): ExecutionResult = executePython(code, 90)

    suspend fun executePython(code: String, timeoutSeconds: Int): ExecutionResult {
        val id = localWorkspaceConversationId()
        return container.pythonSandbox.execute(id, code, timeoutSeconds)
    }

    suspend fun executePython(code: String, onProgress: suspend (ExecutionProgress) -> Unit): ExecutionResult {
        val id = localWorkspaceConversationId()
        val result = container.pythonSandbox.execute(id, code, 90)
        onProgress(ExecutionProgress(result.stdout.takeLast(12_000), result.stderr.takeLast(12_000), result.elapsedMs))
        return result
    }

    suspend fun installPythonPackages(requirements: String, approvedPlan: PackagePlan? = null): PackageInstallResult {
        val id = localWorkspaceConversationId()
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val result = container.pythonSandbox.install(id, requirements, restrictions, approvedPlan)
        if (result.success) {
            val imports = result.importNames.entries.joinToString("; ") { (distribution, names) ->
                "$distribution imports as ${names.joinToString().ifBlank { "(no top-level module reported)" }}"
            }
            container.repository.recordSystemEvent(
                id,
                "The user approved and Xylune installed these Python packages in this conversation workspace: ${result.packages.joinToString()}. ${imports.ifBlank { "Import metadata was unavailable." }}" +
                    if (result.importErrors.isEmpty()) " Import verification passed." else " Import verification warnings: ${result.importErrors.entries.joinToString { "${it.key}: ${it.value}" }}",
            )
        }
        return result
    }

    suspend fun installPythonPackagesAndContinue(operationKey: String, requirements: String, approvedPlan: PackagePlan): PackageInstallResult {
        val id = localWorkspaceConversationId()
        val previous = container.repository.packageTransaction(operationKey)
        if (previous?.status == PACKAGE_SUCCEEDED && previous.requirements == requirements) {
            return PackageInstallResult(success = true, packages = approvedPlan.items.map { it.name })
        }
        savePackageTransaction(operationKey, id, PackageEcosystem.PIP, requirements, approvedPlan, PACKAGE_INSTALLING, "Installation started")
        val result = try {
            installPythonPackages(requirements, approvedPlan)
        } catch (error: Throwable) {
            savePackageTransaction(operationKey, id, PackageEcosystem.PIP, requirements, approvedPlan, PACKAGE_FAILED, error.message.orEmpty())
            throw error
        }
        savePackageTransaction(
            operationKey, id, PackageEcosystem.PIP, requirements, approvedPlan,
            if (result.success && result.importErrors.isEmpty()) PACKAGE_SUCCEEDED else PACKAGE_FAILED,
            if (result.success) "Installed ${result.packages.joinToString()}" else result.stderr.takeLast(1_000),
        )
        if (result.success) schedulePackageContinuation(id)
        return result
    }

    suspend fun reviewPythonPackages(requirements: String): PackageReview {
        val id = localWorkspaceConversationId()
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        return container.packageApprovals.review(id, container.pythonSandbox.preflight(id, requirements, restrictions))
    }

    suspend fun reviewPythonPackages(operationKey: String, requirements: String): PackageReview {
        val id = localWorkspaceConversationId()
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val plan = container.pythonSandbox.preflight(id, requirements, restrictions)
        return reviewDurablePackage(operationKey, id, requirements, plan)
    }

    suspend fun refreshUbuntu(): UbuntuRuntimeStatus = container.ubuntuRuntime.refresh()

    fun selectLinuxDistribution(value: LinuxDistribution) = container.ubuntuRuntime.selectDistribution(value)

    suspend fun installUbuntu(): UbuntuRuntimeStatus = container.ubuntuRuntime.install()

    suspend fun removeUbuntu(): UbuntuRuntimeStatus = container.ubuntuRuntime.remove()

    suspend fun executeUbuntu(command: String, timeoutSeconds: Int = 180): UbuntuExecutionResult {
        val id = localWorkspaceConversationId()
        return container.ubuntuRuntime.execute(id, command, timeoutSeconds)
    }

    suspend fun executeUbuntu(command: String, onProgress: suspend (ExecutionProgress) -> Unit): UbuntuExecutionResult {
        val id = localWorkspaceConversationId()
        return container.ubuntuRuntime.execute(id, command, 180, onProgress)
    }

    suspend fun repairGeneratedBlock(
        blockId: String,
        messageId: String,
        type: GeneratedBlockType,
        source: String,
        errors: List<GeneratedValidationError>,
        newCycle: Boolean = false,
        progress: (GeneratedBlockRepairState) -> Unit = {},
    ): GeneratedBlockRepairState {
        val conversationId = selectedConversationId.value ?: error("No conversation")
        return container.generatedBlockRepairs.repair(
            conversationId = conversationId,
            messageId = messageId,
            blockId = blockId,
            type = type,
            originalSource = source,
            initialErrors = errors,
            maxAttempts = generatedRepairMaxAttempts.value,
            newCycle = newCycle,
            progress = progress,
        )
    }

    suspend fun acceptGeneratedBlockEdit(state: GeneratedBlockRepairState, source: String): GeneratedBlockRepairState =
        container.generatedBlockRepairs.acceptManualEdit(state, source)

    suspend fun rerunRecordedScript(runId: String, timeoutSeconds: Int? = null): ScriptRunResult {
        val conversationId = localWorkspaceConversationId()
        val outcome = container.agentTools.execute(conversationId, AgentToolRequest("rerun_script", runId = runId, timeoutSeconds = timeoutSeconds))
        return toolResultJson.decodeFromString(outcome.output)
    }

    suspend fun scriptRunMetadata(runId: String): ScriptRunMetadata {
        val conversationId = localWorkspaceConversationId()
        return withContext(Dispatchers.IO) { container.runRecords.load(conversationId, runId) }
    }

    suspend fun readScriptSource(path: String): WorkspaceReadResult {
        val conversationId = localWorkspaceConversationId()
        return withContext(Dispatchers.IO) { container.runRecords.readWorkspace(conversationId, path, 1, 500, 64_000) }
    }

    suspend fun reviewUbuntuPackages(packages: String): PackageReview {
        val id = localWorkspaceConversationId()
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        return container.packageApprovals.review(id, container.ubuntuRuntime.preflightPackages(id, packages, restrictions))
    }

    suspend fun installUbuntuPackages(
        packages: String,
        approvedPlan: PackagePlan? = null,
        onProgress: suspend (PackageInstallProgress) -> Unit = {},
    ): UbuntuPackageInstallResult {
        val id = localWorkspaceConversationId()
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val result = container.ubuntuRuntime.installPackages(id, packages, restrictions, approvedPlan, onProgress)
        if (result.success) container.repository.recordSystemEvent(
            id,
            "Xylune's configured package approval policy allowed and installed these ${container.ubuntuRuntime.distribution.value.displayName} packages: ${result.packages.joinToString()}.",
        )
        return result
    }

    suspend fun installUbuntuPackagesAndContinue(
        operationKey: String,
        packages: String,
        approvedPlan: PackagePlan,
        onProgress: suspend (PackageInstallProgress) -> Unit,
    ): UbuntuPackageInstallResult {
        val id = localWorkspaceConversationId()
        val previous = container.repository.packageTransaction(operationKey)
        if (previous?.status == PACKAGE_SUCCEEDED && previous.requirements == packages) {
            return UbuntuPackageInstallResult(true, packages = approvedPlan.items.map { it.name })
        }
        savePackageTransaction(operationKey, id, approvedPlan.ecosystem, packages, approvedPlan, PACKAGE_INSTALLING, "Installation started")
        val result = try {
            installUbuntuPackages(packages, approvedPlan, onProgress)
        } catch (error: Throwable) {
            savePackageTransaction(operationKey, id, approvedPlan.ecosystem, packages, approvedPlan, PACKAGE_FAILED, error.message.orEmpty())
            throw error
        }
        savePackageTransaction(
            operationKey, id, approvedPlan.ecosystem, packages, approvedPlan,
            if (result.success) PACKAGE_SUCCEEDED else PACKAGE_FAILED,
            if (result.success) "Installed ${result.packages.joinToString()}" else result.stderr.takeLast(1_000),
        )
        if (result.success) schedulePackageContinuation(id)
        return result
    }

    suspend fun reviewUbuntuPackages(operationKey: String, packages: String): PackageReview {
        val id = localWorkspaceConversationId()
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val plan = container.ubuntuRuntime.preflightPackages(id, packages, restrictions)
        return reviewDurablePackage(operationKey, id, packages, plan)
    }

    private suspend fun reviewDurablePackage(
        operationKey: String,
        conversationId: String,
        requirements: String,
        plan: PackagePlan,
    ): PackageReview {
        val reviewed = container.packageApprovals.review(conversationId, plan)
        val prior = container.repository.packageTransaction(operationKey)
            ?.takeIf { it.requirements == requirements && it.planFingerprint == plan.fingerprint() }
        if (reviewed.state == PackageApprovalState.NOT_NEEDED) {
            savePackageTransaction(operationKey, conversationId, plan.ecosystem, requirements, plan, PACKAGE_SUCCEEDED, reviewed.reason)
            ensurePackageContinuation(conversationId)
            return reviewed
        }
        if (prior?.status == PACKAGE_SUCCEEDED) {
            ensurePackageContinuation(conversationId)
            return PackageReview(plan, PackageApprovalState.NOT_NEEDED, "This saved package request already completed successfully.", "saved history")
        }
        if (prior?.status == PACKAGE_FAILED || prior?.status == PACKAGE_INSTALLING) {
            return PackageReview(
                plan,
                PackageApprovalState.REQUIRED,
                if (prior.status == PACKAGE_INSTALLING) "The previous install was interrupted. Review before retrying." else "The previous install failed. Review before retrying.",
                "recovery guard",
            )
        }
        savePackageTransaction(operationKey, conversationId, plan.ecosystem, requirements, plan, PACKAGE_REVIEWED, reviewed.reason)
        return reviewed
    }

    private suspend fun savePackageTransaction(
        operationKey: String,
        conversationId: String,
        ecosystem: PackageEcosystem,
        requirements: String,
        plan: PackagePlan,
        status: String,
        summary: String,
    ) {
        container.repository.savePackageTransaction(PackageTransactionEntity(
            operationKey = operationKey,
            conversationId = conversationId,
            ecosystem = ecosystem.name,
            requirements = requirements,
            planJson = Json.encodeToString(plan),
            planFingerprint = plan.fingerprint(),
            status = status,
            resultSummary = summary.takeLast(2_000),
            updatedAt = System.currentTimeMillis(),
        ))
    }

    suspend fun searchPythonPackages(query: String): List<app.xylune.chat.sandbox.PythonPackageSearchResult> =
        container.ubuntuRuntime.searchPythonPackages(query)

    suspend fun pythonEnvironment(): PythonEnvironmentInfo {
        val id = localWorkspaceConversationId()
        return container.pythonSandbox.environment(id)
    }

    suspend fun removePythonPackages(names: List<String>): PythonEnvironmentInfo {
        val id = localWorkspaceConversationId()
        val result = container.pythonSandbox.remove(id, names)
        container.repository.recordSystemEvent(id, "The user removed these Python packages from this conversation environment: ${names.joinToString()}.")
        return result
    }

    suspend fun repairPythonEnvironment(): PythonEnvironmentInfo {
        val id = localWorkspaceConversationId()
        return container.pythonSandbox.repair(id)
    }

    suspend fun resetPythonSession() {
        val id = localWorkspaceConversationId()
        container.pythonSandbox.resetSession(id)
    }

    fun openConversationFromIntent(id: String?) = launchAction { ensureInitialized(id) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ChatViewModel(container, createSavedStateHandle()) }
        }
    }

    private fun launchAction(block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }.onFailure { notices.emit(it.readableMessage()) }
    }

    private fun schedulePackageContinuation(conversationId: String) = viewModelScope.launch {
        ensurePackageContinuation(conversationId)
    }

    private suspend fun ensurePackageContinuation(conversationId: String) {
        if (container.repository.activeStream(conversationId) != null) return
        val conversation = container.repository.conversationNow(conversationId) ?: return
        val leaf = conversation.activeLeafNodeId?.let { container.repository.message(it) }
        if (leaf?.role == app.xylune.chat.data.MessageRole.SYSTEM && leaf.content.contains("package", ignoreCase = true)) {
            val assistantId = container.repository.createAssistantAfterSystemEvent(conversationId)
            container.scheduler.start(conversationId, assistantId, continuation = false)
        }
    }
}

private fun Throwable.readableMessage(): String = message?.takeIf(String::isNotBlank)
    ?: this::class.java.simpleName

enum class Screen { CHAT, SEARCH, SETTINGS, SANDBOX, TERMINAL }

private const val PACKAGE_REVIEWED = "REVIEWED"
private const val PACKAGE_INSTALLING = "INSTALLING"
private const val PACKAGE_SUCCEEDED = "SUCCEEDED"
private const val PACKAGE_FAILED = "FAILED"
private const val OAUTH_CREDENTIAL_MARKER = "oauth-session"
