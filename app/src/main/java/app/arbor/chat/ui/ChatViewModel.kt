package app.arbor.chat.ui

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
import app.arbor.chat.AppContainer
import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.AutomationSettingsEntity
import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.DefaultCatalog
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageStatus
import app.arbor.chat.data.ModelEntity
import app.arbor.chat.data.ProviderEntity
import app.arbor.chat.data.ProviderKind
import app.arbor.chat.data.ProjectEntity
import app.arbor.chat.data.SystemPromptMode
import app.arbor.chat.data.SystemPromptProfileEntity
import app.arbor.chat.data.SendMode
import app.arbor.chat.data.AuxiliaryMode
import app.arbor.chat.data.PackageApprovalMode
import app.arbor.chat.data.PackageTransactionEntity
import app.arbor.chat.provider.ProviderCredentialPolicy
import app.arbor.chat.provider.ProviderEndpointPolicy
import app.arbor.chat.provider.parseHeaders
import app.arbor.chat.sandbox.ExecutionResult
import app.arbor.chat.sandbox.PackageInstallResult
import app.arbor.chat.sandbox.PythonEnvironmentInfo
import app.arbor.chat.sandbox.PackageReview
import app.arbor.chat.sandbox.PackagePlan
import app.arbor.chat.sandbox.PackageEcosystem
import app.arbor.chat.sandbox.PackageApprovalState
import app.arbor.chat.sandbox.fingerprint
import app.arbor.chat.sandbox.LinuxDistribution
import app.arbor.chat.sandbox.UbuntuExecutionResult
import app.arbor.chat.sandbox.UbuntuPackageInstallResult
import app.arbor.chat.sandbox.UbuntuRuntimeStatus
import app.arbor.chat.settings.NewChatDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class PythonRunState(
    val startedAt: Long,
    val code: String,
    val timeoutSeconds: Int,
    val running: Boolean = true,
    val result: ExecutionResult? = null,
    val error: String? = null,
)

data class LinuxRunState(
    val startedAt: Long,
    val command: String,
    val distribution: LinuxDistribution,
    val timeoutSeconds: Int,
    val running: Boolean = true,
    val result: UbuntuExecutionResult? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val container: AppContainer, savedStateHandle: SavedStateHandle) : ViewModel() {
    val conversations = container.repository.conversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val archivedConversations = container.repository.archivedConversations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects = container.repository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val systemPromptProfiles = container.repository.systemPromptProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val automationSettings = container.repository.automationSettings
        .map { it ?: AutomationSettingsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationSettingsEntity())
    val ubuntuStatus: StateFlow<UbuntuRuntimeStatus> = container.ubuntuRuntime.status
    val linuxDistribution: StateFlow<LinuxDistribution> = container.ubuntuRuntime.distribution
    val selectedConversationId = savedStateHandle.getMutableStateFlow<String?>("selected_conversation", null)
    private val draftConversation = MutableStateFlow<ConversationEntity?>(null)
    val showArchived = savedStateHandle.getMutableStateFlow("show_archived", false)
    val selectedProjectId = savedStateHandle.getMutableStateFlow<String?>("selected_project", null)
    val draft = savedStateHandle.getMutableStateFlow("draft", "")
    val stagedAttachments = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    val importing = MutableStateFlow(false)
    val screen = savedStateHandle.getMutableStateFlow("screen", Screen.CHAT)
    val searchQuery = savedStateHandle.getMutableStateFlow("search_query", "")
    val focusedMessageNodeId = savedStateHandle.getMutableStateFlow<String?>("focused_message_node", null)
    private val focusedMessageIndex = savedStateHandle.getMutableStateFlow<Int?>("focused_message_index", null)
    val amoled: StateFlow<Boolean> = container.appPreferences.amoled
    val chromeBlurEnabled: StateFlow<Boolean> = container.appPreferences.chromeBlurEnabled
    val chromeBlurStrength: StateFlow<Float> = container.appPreferences.chromeBlurStrength
    val palette = container.appPreferences.palette
    val themeMode = container.appPreferences.themeMode
    val newChatDefaults: StateFlow<NewChatDefaults> = container.appPreferences.newChatDefaults
    val renderSafeMode = container.crashReporter.renderSafeMode
    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    private val _credentialRevision = MutableStateFlow(0L)
    val credentialRevision: StateFlow<Long> = _credentialRevision
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

    fun setRenderSafeMode(enabled: Boolean) = container.crashReporter.setRenderSafeMode(enabled)

    fun startPythonRun(code: String, timeoutSeconds: Int) {
        if (_pythonRun.value?.running == true || code.isBlank()) return
        val conversationId = selectedConversationId.value ?: draftConversation.value?.id ?: return
        val started = System.currentTimeMillis()
        _pythonRun.value = PythonRunState(started, code, timeoutSeconds)
        pythonRunJob = viewModelScope.launch {
            try {
                val result = container.ubuntuRuntime.executePython(conversationId, code, timeoutSeconds)
                _pythonRun.value = _pythonRun.value?.copy(running = false, result = result)
            } catch (cancelled: CancellationException) {
                _pythonRun.value = _pythonRun.value?.copy(running = false, error = "Stopped by user")
            } catch (error: Throwable) {
                _pythonRun.value = _pythonRun.value?.copy(running = false, error = error.stackTraceToString())
            }
        }
    }

    fun stopPythonRun() {
        pythonRunJob?.cancel()
    }

    fun clearPythonRun() {
        if (_pythonRun.value?.running != true) _pythonRun.value = null
    }

    fun startLinuxRun(command: String, timeoutSeconds: Int) {
        if (_linuxRun.value?.running == true || command.isBlank()) return
        val conversationId = selectedConversationId.value ?: draftConversation.value?.id ?: return
        val started = System.currentTimeMillis()
        _linuxRun.value = LinuxRunState(started, command, container.ubuntuRuntime.distribution.value, timeoutSeconds)
        linuxRunJob = viewModelScope.launch {
            try {
                val result = container.ubuntuRuntime.execute(conversationId, command, timeoutSeconds)
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
        val target = preferred ?: restored ?: container.repository.conversations.first().firstOrNull()?.conversation
        if (target != null && !container.appPreferences.hasNewChatDefaults) {
            container.appPreferences.setNewChatDefaults(NewChatDefaults.from(target))
        }
        if (target == null) {
            selectedConversationId.value = null
            if (draftConversation.value == null) draftConversation.value = container.repository.newConversationDraft(defaults = newChatDefaults.value)
            stagedAttachments.value = emptyList()
        } else {
            container.repository.repairActiveMessagePath(target.id)
            draftConversation.value = null
            selectedConversationId.value = target.id
            stagedAttachments.value = container.database.attachmentDao().stagedForConversation(target.id)
            container.repository.markRead(target.id)
        }
        initialized = true
        target?.id
    }

    private suspend fun materializeDraft(): String {
        selectedConversationId.value?.let { return it }
        val value = draftConversation.value ?: container.repository.newConversationDraft(
            projectId = selectedProjectId.value.takeUnless { showArchived.value },
            defaults = newChatDefaults.value,
        )
        container.repository.persistConversationDraft(value)
        draftConversation.value = null
        selectedConversationId.value = value.id
        return value.id
    }

    private fun openEmptyDraft() {
        selectedConversationId.value = null
        draftConversation.value = container.repository.newConversationDraft(
            projectId = selectedProjectId.value.takeUnless { showArchived.value },
            defaults = newChatDefaults.value,
        )
        stagedAttachments.value = emptyList()
        focusedMessageNodeId.value = null
        focusedMessageIndex.value = null
        screen.value = Screen.CHAT
    }

    fun selectConversation(id: String) = launchAction {
        container.repository.repairActiveMessagePath(id)
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
        draft.value = ""
        stagedAttachments.value = emptyList()
        val effectiveMode = mode ?: if (container.repository.activeStream(id) != null) SendMode.QUEUE else SendMode.SEND_NOW
        runCatching { container.scheduler.submit(id, text, attachments.map { it.id }, effectiveMode) }
            .onFailure { error ->
                draft.value = text
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
        draft.value = text
        send()
    }

    suspend fun reviewWidgetSecurity(source: String): String {
        val id = selectedConversationId.value ?: error("No conversation")
        return container.auxiliaryModels.reviewWidgetSecurity(id, source)
    }

    fun selectModel(providerId: String, modelId: String) = updateConversation {
        it.copy(selectedProviderId = providerId, selectedModelId = modelId)
    }

    fun updateConversation(transform: (ConversationEntity) -> ConversationEntity) {
        val id = selectedConversationId.value
        if (id == null) {
            draftConversation.value = draftConversation.value?.let(transform)?.copy(updatedAt = System.currentTimeMillis())
            draftConversation.value?.let { container.appPreferences.setNewChatDefaults(NewChatDefaults.from(it)) }
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

    fun registeredProviders(values: List<ProviderEntity>): List<ProviderEntity> =
        values.filter { ProviderCredentialPolicy.isRegistered(it, container.secureStore.apiKey(it.id)) }

    fun configuredProviders(values: List<ProviderEntity>): List<ProviderEntity> =
        values.filter { ProviderCredentialPolicy.isUsable(it, container.secureStore.apiKey(it.id)) }

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
        initialModels.distinctBy { it.modelId }.forEach { container.repository.saveModel(it) }
        _credentialRevision.value++
        notices.emit("Added ${provider.displayName}")
    }

    suspend fun saveDiscoveredModels(providerId: String, discovered: List<app.arbor.chat.provider.DiscoveredModel>) {
        require(discovered.isNotEmpty()) { "The provider returned no models" }
        discovered.forEach { candidate ->
            val bundled = DefaultCatalog.models.firstOrNull { it.providerId == providerId && it.modelId == candidate.id }
            container.repository.saveModel(bundled ?: ModelEntity(
                providerId = providerId,
                modelId = candidate.id,
                displayName = candidate.displayName,
                contextWindow = candidate.contextWindow ?: 128_000,
                maxOutputTokens = candidate.maxOutputTokens ?: 16_384,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
                pricingConfigured = false,
                supportsThinking = candidate.supportsThinking ?: false,
            ))
        }
    }

    fun saveModel(model: ModelEntity) = launchAction { container.repository.saveModel(model) }

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

    fun updateAutomationSettings(transform: (AutomationSettingsEntity) -> AutomationSettingsEntity) = launchAction {
        automationSettingsMutex.withLock {
            container.repository.saveAutomationSettings(transform(container.repository.automationSettingsNow()))
        }
    }

    fun setAmoled(enabled: Boolean) = container.appPreferences.setAmoled(enabled)
    fun setPalette(value: app.arbor.chat.settings.ColorPalette) = container.appPreferences.setPalette(value)
    fun setThemeMode(value: app.arbor.chat.settings.ThemeMode) = container.appPreferences.setThemeMode(value)
    fun setChromeBlurEnabled(enabled: Boolean) = container.appPreferences.setChromeBlurEnabled(enabled)
    fun setChromeBlurStrength(value: Float) = container.appPreferences.setChromeBlurStrength(value)

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
        val id = selectedConversationId.value ?: error("No conversation")
        return container.ubuntuRuntime.executePython(id, code, timeoutSeconds)
    }

    suspend fun installPythonPackages(requirements: String, approvedPlan: PackagePlan? = null): PackageInstallResult {
        val id = selectedConversationId.value ?: error("No conversation")
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val result = container.ubuntuRuntime.installPythonPackages(id, requirements, restrictions, approvedPlan)
        if (result.success) {
            val imports = result.importNames.entries.joinToString("; ") { (distribution, names) ->
                "$distribution imports as ${names.joinToString().ifBlank { "(no top-level module reported)" }}"
            }
            container.repository.recordSystemEvent(
                id,
                "The user approved and Arbor installed these Python packages in this conversation workspace: ${result.packages.joinToString()}. ${imports.ifBlank { "Import metadata was unavailable." }}" +
                    if (result.importErrors.isEmpty()) " Import verification passed." else " Import verification warnings: ${result.importErrors.entries.joinToString { "${it.key}: ${it.value}" }}",
            )
        }
        return result
    }

    suspend fun installPythonPackagesAndContinue(operationKey: String, requirements: String, approvedPlan: PackagePlan): PackageInstallResult {
        val id = selectedConversationId.value ?: error("No conversation")
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
        val id = selectedConversationId.value ?: error("No conversation")
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        return container.packageApprovals.review(id, container.ubuntuRuntime.preflightPythonPackages(id, requirements, restrictions))
    }

    suspend fun reviewPythonPackages(operationKey: String, requirements: String): PackageReview {
        val id = selectedConversationId.value ?: error("No conversation")
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val plan = container.ubuntuRuntime.preflightPythonPackages(id, requirements, restrictions)
        return reviewDurablePackage(operationKey, id, requirements, plan)
    }

    suspend fun refreshUbuntu(): UbuntuRuntimeStatus = container.ubuntuRuntime.refresh()

    fun selectLinuxDistribution(value: LinuxDistribution) = container.ubuntuRuntime.selectDistribution(value)

    suspend fun installUbuntu(): UbuntuRuntimeStatus = container.ubuntuRuntime.install()

    suspend fun removeUbuntu(): UbuntuRuntimeStatus = container.ubuntuRuntime.remove()

    suspend fun executeUbuntu(command: String, timeoutSeconds: Int = 180): UbuntuExecutionResult {
        val id = selectedConversationId.value ?: error("No conversation")
        return container.ubuntuRuntime.execute(id, command, timeoutSeconds)
    }

    suspend fun reviewUbuntuPackages(packages: String): PackageReview {
        val id = selectedConversationId.value ?: error("No conversation")
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        return container.packageApprovals.review(id, container.ubuntuRuntime.preflightPackages(id, packages, restrictions))
    }

    suspend fun installUbuntuPackages(packages: String, approvedPlan: PackagePlan? = null): UbuntuPackageInstallResult {
        val id = selectedConversationId.value ?: error("No conversation")
        val restrictions = container.repository.automationSettingsNow().packageRestrictionsEnabled
        val result = container.ubuntuRuntime.installPackages(id, packages, restrictions, approvedPlan)
        if (result.success) container.repository.recordSystemEvent(
            id,
            "Arbor's configured package approval policy allowed and installed these ${container.ubuntuRuntime.distribution.value.displayName} packages: ${result.packages.joinToString()}.",
        )
        return result
    }

    suspend fun installUbuntuPackagesAndContinue(operationKey: String, packages: String, approvedPlan: PackagePlan): UbuntuPackageInstallResult {
        val id = selectedConversationId.value ?: error("No conversation")
        val previous = container.repository.packageTransaction(operationKey)
        if (previous?.status == PACKAGE_SUCCEEDED && previous.requirements == packages) {
            return UbuntuPackageInstallResult(true, packages = approvedPlan.items.map { it.name })
        }
        savePackageTransaction(operationKey, id, approvedPlan.ecosystem, packages, approvedPlan, PACKAGE_INSTALLING, "Installation started")
        val result = try {
            installUbuntuPackages(packages, approvedPlan)
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
        val id = selectedConversationId.value ?: error("No conversation")
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

    suspend fun searchPythonPackages(query: String): List<app.arbor.chat.sandbox.PythonPackageSearchResult> =
        container.ubuntuRuntime.searchPythonPackages(query)

    suspend fun pythonEnvironment(): PythonEnvironmentInfo {
        val id = selectedConversationId.value ?: error("No conversation")
        return container.ubuntuRuntime.pythonEnvironment(id)
    }

    suspend fun removePythonPackages(names: List<String>): PythonEnvironmentInfo {
        val id = selectedConversationId.value ?: error("No conversation")
        val result = container.ubuntuRuntime.removePythonPackages(id, names)
        container.repository.recordSystemEvent(id, "The user removed these Python packages from this conversation environment: ${names.joinToString()}.")
        return result
    }

    suspend fun repairPythonEnvironment(): PythonEnvironmentInfo {
        val id = selectedConversationId.value ?: error("No conversation")
        return container.ubuntuRuntime.repairPythonEnvironment(id)
    }

    suspend fun resetPythonSession() {
        val id = selectedConversationId.value ?: error("No conversation")
        // Distro Python starts a fresh interpreter for each run; files and installed packages remain persistent.
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
        if (leaf?.role == app.arbor.chat.data.MessageRole.SYSTEM && leaf.content.contains("package", ignoreCase = true)) {
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
