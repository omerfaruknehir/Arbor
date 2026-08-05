package app.xylune.chat.chat

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import app.xylune.chat.data.XyluneDatabase
import app.xylune.chat.data.AutomationSettingsEntity
import app.xylune.chat.data.AuxiliaryMode
import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.ConversationListItem
import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.MessageStatus
import app.xylune.chat.data.MemoryEntity
import app.xylune.chat.data.PendingMessageEntity
import app.xylune.chat.data.ProjectEntity
import app.xylune.chat.data.ContextSummaryEntity
import app.xylune.chat.data.GenerationUsageEntity
import app.xylune.chat.data.PackageTransactionEntity
import app.xylune.chat.data.SearchHit
import app.xylune.chat.data.SendMode
import app.xylune.chat.data.SystemPromptProfileEntity
import app.xylune.chat.generation.GenerationRequestSnapshot
import app.xylune.chat.settings.NewChatDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

internal fun activeMessagePathNodeIds(
    messages: List<MessageEntity>,
    leafNodeId: String?,
): Set<String> {
    if (leafNodeId.isNullOrBlank()) return emptySet()
    val byId = messages.associateBy(MessageEntity::nodeId)
    val path = LinkedHashSet<String>()
    var cursor = byId[leafNodeId]
    while (cursor != null && path.add(cursor.nodeId)) {
        cursor = cursor.parentNodeId?.let(byId::get)
    }
    return path
}

class ChatRepository(private val database: XyluneDatabase) {
    val conversations: Flow<List<ConversationListItem>> = database.conversationDao().observeAll()
    val archivedConversations: Flow<List<ConversationListItem>> = database.conversationDao().observeArchived()
    val projects: Flow<List<ProjectEntity>> = database.projectDao().observeAll()
    val automationSettings: Flow<AutomationSettingsEntity?> = database.automationSettingsDao().observe()
    val systemPromptProfiles: Flow<List<SystemPromptProfileEntity>> = database.systemPromptProfileDao().observeAll()
    val memories: Flow<List<MemoryEntity>> = database.memoryDao().observeAll()

    fun conversation(id: String) = database.conversationDao().observe(id)
    fun recoverable(id: String) = database.messageDao().observeRecoverable(id)
    fun history(id: String) = database.messageDao().observeSuperseded(id)
    fun pending(id: String) = database.pendingDao().observe(id)

    suspend fun packageTransaction(operationKey: String) = database.packageTransactionDao().get(operationKey)
    suspend fun savePackageTransaction(value: PackageTransactionEntity) = database.packageTransactionDao().upsert(value)

    fun messages(id: String, initialIndex: Int? = null): Flow<PagingData<MessageEntity>> = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 12, enablePlaceholders = false, initialLoadSize = 40),
        initialKey = initialIndex,
    ) { database.messageDao().paging(id) }.flow

    suspend fun messageIndexFromLatest(conversationId: String, nodeId: String) =
        database.messageDao().indexFromLatest(conversationId, nodeId)

    /**
     * Enforce the single active conversation path described by activeLeafNodeId.
     * Older builds could leave sibling retries marked active, which made several
     * complete assistant responses appear one after another in the chat.
     */
    suspend fun repairActiveMessagePath(conversationId: String) {
        database.withTransaction {
            val conversation = database.conversationDao().get(conversationId) ?: return@withTransaction
            val allMessages = database.messageDao().allForConversation(conversationId)
            if (allMessages.isEmpty()) return@withTransaction
            val byId = allMessages.associateBy(MessageEntity::nodeId)
            val target = conversation.activeLeafNodeId?.let(byId::get)
                ?: allMessages.asSequence()
                    .filter { it.supersededAt == null }
                    .maxWithOrNull(compareBy<MessageEntity> { it.createdAt }.thenBy { it.rowId })
                ?: allMessages.maxWithOrNull(compareBy<MessageEntity> { it.createdAt }.thenBy { it.rowId })
                ?: return@withTransaction
            val targetPath = activeMessagePathNodeIds(allMessages, target.nodeId)
            if (targetPath.isEmpty()) return@withTransaction
            val activeIds = allMessages.asSequence()
                .filter { it.supersededAt == null }
                .map(MessageEntity::nodeId)
                .toSet()
            val now = System.currentTimeMillis()
            val strayActive = (activeIds - targetPath).toList()
            val hiddenPathNodes = targetPath.filter { byId[it]?.supersededAt != null }
            if (strayActive.isNotEmpty()) database.messageDao().markSuperseded(strayActive, now)
            if (hiddenPathNodes.isNotEmpty()) database.messageDao().clearSuperseded(hiddenPathNodes)
            if (conversation.activeLeafNodeId != target.nodeId) {
                database.conversationDao().setLeaf(conversationId, target.nodeId, now)
            }
        }
    }

    fun newConversationDraft(projectId: String? = null, defaults: NewChatDefaults = NewChatDefaults()): ConversationEntity {
        val now = System.currentTimeMillis()
        return defaults.applyTo(ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = "New conversation",
            createdAt = now,
            updatedAt = now,
            projectId = projectId,
        ))
    }

    suspend fun createConversation(projectId: String? = null, defaults: NewChatDefaults = NewChatDefaults()): ConversationEntity {
        return newConversationDraft(projectId, defaults).also { database.conversationDao().upsert(it) }
    }

    suspend fun getOrCreateConversation(id: String?, defaults: NewChatDefaults = NewChatDefaults()): ConversationEntity =
        id?.let { database.conversationDao().get(it) } ?: createConversation(defaults = defaults)

    suspend fun persistConversationDraft(value: ConversationEntity) = database.conversationDao().upsert(value)

    suspend fun submit(
        conversationId: String,
        text: String,
        attachmentIds: List<String>,
        mode: SendMode,
    ): String? {
        if (mode == SendMode.QUEUE) {
            val now = System.currentTimeMillis()
            database.withTransaction {
                val previous = database.pendingDao().maxPosition(conversationId)
                database.pendingDao().upsert(PendingMessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    content = text,
                    attachmentIdsJson = Json.encodeToString(attachmentIds),
                    position = if (previous == 0L) now else previous + 1L,
                    createdAt = now,
                ))
            }
            return null
        }
        return createExchange(conversationId, text, attachmentIds)
    }

    suspend fun createExchange(conversationId: String, text: String, attachmentIds: List<String>): String {
        val conversation = requireNotNull(database.conversationDao().get(conversationId))
        val now = System.currentTimeMillis()
        val snapshot = generationSnapshot(conversation)
        val assistantId = database.withTransaction { insertExchange(conversation, text, attachmentIds, now, snapshot) }
        if (conversation.autoTitle && automationSettingsNow().titleMode == AuxiliaryMode.LOCAL) regenerateTitle(conversationId)
        return assistantId
    }

    private suspend fun insertExchange(
        conversation: ConversationEntity,
        text: String,
        attachmentIds: List<String>,
        now: Long,
        requestSnapshotJson: String,
    ): String {
        val userId = UUID.randomUUID().toString()
        val assistantId = UUID.randomUUID().toString()
        val branch = UUID.randomUUID().toString()
        database.messageDao().insert(MessageEntity(
            nodeId = userId,
            conversationId = conversation.id,
            parentNodeId = conversation.activeLeafNodeId,
            branchId = branch,
            role = MessageRole.USER,
            content = text,
            status = MessageStatus.COMPLETE,
            createdAt = now,
            updatedAt = now,
        ))
        database.messageDao().insert(MessageEntity(
            nodeId = assistantId,
            conversationId = conversation.id,
            parentNodeId = userId,
            branchId = branch,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.STREAMING,
            providerId = conversation.selectedProviderId,
            modelId = conversation.selectedModelId,
            requestSnapshotJson = requestSnapshotJson,
            createdAt = now + 1,
            updatedAt = now + 1,
        ))
        if (attachmentIds.isNotEmpty()) database.attachmentDao().attachToMessage(attachmentIds, userId)
        database.conversationDao().setLeaf(conversation.id, assistantId, now)
        return assistantId
    }

    suspend fun materializeNextPending(conversationId: String): String? {
        var titleLocally = false
        val assistantId = database.withTransaction {
            val pending = database.pendingDao().next(conversationId) ?: return@withTransaction null
            val conversation = requireNotNull(database.conversationDao().get(conversationId))
            val ids = runCatching { Json.decodeFromString<List<String>>(pending.attachmentIdsJson) }.getOrDefault(emptyList())
            val snapshot = generationSnapshot(conversation)
            database.pendingDao().delete(pending.id)
            titleLocally = conversation.autoTitle
            insertExchange(conversation, pending.content, ids, System.currentTimeMillis(), snapshot)
        }
        if (assistantId != null && titleLocally && automationSettingsNow().titleMode == AuxiliaryMode.LOCAL) regenerateTitle(conversationId)
        return assistantId
    }

    suspend fun markStreaming(nodeId: String) = database.messageDao().markStreaming(nodeId, System.currentTimeMillis())
    suspend fun markRetrying(nodeId: String, reason: String) = database.messageDao().markRetrying(nodeId, reason, System.currentTimeMillis())

    suspend fun markInterrupted(nodeId: String, reason: String = "Stopped") {
        database.messageDao().interruptIfStreaming(nodeId, reason, System.currentTimeMillis())
    }

    suspend fun activeStream(conversationId: String) = database.messageDao().streamingForConversation(conversationId)
    suspend fun activeStreams(conversationId: String) = database.messageDao().streamingForConversationAll(conversationId)
    suspend fun message(nodeId: String) = database.messageDao().get(nodeId)
    suspend fun conversationNow(id: String) = database.conversationDao().get(id)
    suspend fun recent(id: String, limit: Int = 10_000) = database.messageDao().recent(id, limit)
    suspend fun provider(id: String) = database.catalogDao().provider(id)
    suspend fun model(providerId: String, modelId: String) = database.catalogDao().model(providerId, modelId)
    suspend fun attachments(nodeId: String) = database.attachmentDao().forMessage(nodeId)

    suspend fun append(nodeId: String, text: String, reasoning: String) =
        database.messageDao().append(nodeId, text, reasoning, text.length + reasoning.length, System.currentTimeMillis())

    suspend fun replaceWorkingState(nodeId: String, content: String, reasoning: String, toolTraceJson: String, timelineJson: String) =
        database.messageDao().replaceWorkingState(nodeId, content, reasoning, toolTraceJson, timelineJson, content.length + reasoning.length, System.currentTimeMillis())

    suspend fun finish(nodeId: String, status: MessageStatus, error: String?, input: Long, output: Long, cached: Long, cost: Long, costKnown: Boolean) =
        database.messageDao().finish(nodeId, status, error, input, output, cached, cost, costKnown, System.currentTimeMillis())

    suspend fun addUsage(conversationId: String, input: Long, output: Long, cost: Long, costKnown: Boolean) =
        database.conversationDao().addUsage(conversationId, input, output, cost, costKnown, System.currentTimeMillis())

    suspend fun saveGenerationUsage(value: GenerationUsageEntity) = database.generationUsageDao().upsert(value)
    suspend fun generationUsage(assistantId: String) = database.generationUsageDao().forAssistant(assistantId)

    fun observeAttachments(nodeId: String) = database.attachmentDao().observeForMessage(nodeId)

    fun observeProviders() = database.catalogDao().observeProviders()
    fun observeModels(providerId: String) = database.catalogDao().observeModels(providerId)

    suspend fun saveProvider(value: app.xylune.chat.data.ProviderEntity) = database.catalogDao().upsertProvider(value)
    suspend fun saveModel(value: app.xylune.chat.data.ModelEntity) = database.catalogDao().upsertModel(value)
    suspend fun mergeModels(values: List<app.xylune.chat.data.ModelEntity>) =
        database.catalogDao().mergeModels(values)
    fun observeAllModels() = database.catalogDao().observeAllModels()

    suspend fun saveConversation(value: ConversationEntity) = database.conversationDao().update(value)

    suspend fun markRead(id: String) = database.conversationDao().markRead(id, System.currentTimeMillis())

    suspend fun regenerateTitle(conversationId: String): String {
        val conversation = requireNotNull(database.conversationDao().get(conversationId))
        val messages = database.messageDao().recent(conversationId, 80)
        val title = ChatTitleGenerator.generate(messages).ifBlank { "New conversation" }
        database.conversationDao().rename(conversationId, title, autoTitle = true, now = System.currentTimeMillis())
        return title
    }

    suspend fun setGeneratedTitle(conversationId: String, title: String): String {
        val clean = title.trim().trim('"', '\'', '`').replace(Regex("\\s+"), " ").take(120)
            .ifBlank { "New conversation" }
        database.conversationDao().rename(conversationId, clean, autoTitle = true, now = System.currentTimeMillis())
        return clean
    }

    suspend fun recordSystemEvent(conversationId: String, content: String) {
        val conversation = requireNotNull(database.conversationDao().get(conversationId))
        val parent = conversation.activeLeafNodeId?.let { database.messageDao().get(it) }
        val now = System.currentTimeMillis()
        val nodeId = UUID.randomUUID().toString()
        database.withTransaction {
            database.messageDao().insert(MessageEntity(
                nodeId = nodeId,
                conversationId = conversationId,
                parentNodeId = conversation.activeLeafNodeId,
                branchId = parent?.branchId ?: UUID.randomUUID().toString(),
                role = MessageRole.SYSTEM,
                content = content,
                status = MessageStatus.COMPLETE,
                createdAt = now,
                updatedAt = now,
            ))
            database.conversationDao().setLeaf(conversationId, nodeId, now)
        }
    }

    suspend fun createAssistantAfterSystemEvent(conversationId: String): String {
        require(database.messageDao().streamingForConversation(conversationId) == null) { "This chat is already responding" }
        val conversation = requireNotNull(database.conversationDao().get(conversationId))
        val parent = conversation.activeLeafNodeId?.let { database.messageDao().get(it) }
        val now = System.currentTimeMillis()
        val assistantId = UUID.randomUUID().toString()
        val snapshot = generationSnapshot(conversation)
        database.withTransaction {
            database.messageDao().insert(MessageEntity(
                nodeId = assistantId,
                conversationId = conversationId,
                parentNodeId = conversation.activeLeafNodeId,
                branchId = parent?.branchId ?: UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.STREAMING,
                providerId = conversation.selectedProviderId,
                modelId = conversation.selectedModelId,
                requestSnapshotJson = snapshot,
                createdAt = now,
                updatedAt = now,
            ))
            database.conversationDao().setLeaf(conversationId, assistantId, now)
        }
        return assistantId
    }

    suspend fun editUserMessage(nodeId: String, content: String): String {
        val original = requireNotNull(database.messageDao().get(nodeId))
        require(original.role == MessageRole.USER) { "Only user messages can be edited" }
        val conversation = requireNotNull(database.conversationDao().get(original.conversationId))
        val now = System.currentTimeMillis()
        val newUserId = UUID.randomUUID().toString()
        val assistantId = UUID.randomUUID().toString()
        val branch = UUID.randomUUID().toString()
        val snapshot = generationSnapshot(conversation)
        val attachments = database.attachmentDao().forMessage(original.nodeId)
        database.withTransaction {
            database.messageDao().markSuperseded(database.messageDao().descendantNodeIds(original.nodeId), now)
            database.messageDao().insert(original.copy(
                rowId = 0, nodeId = newUserId, parentNodeId = original.parentNodeId,
                branchId = branch, content = content, createdAt = now, updatedAt = now,
                supersededAt = null,
            ))
            attachments.forEach { attachment ->
                database.attachmentDao().upsert(attachment.copy(id = UUID.randomUUID().toString(), messageNodeId = newUserId, createdAt = now))
            }
            database.messageDao().insert(MessageEntity(
                nodeId = assistantId, conversationId = original.conversationId,
                parentNodeId = newUserId, branchId = branch, role = MessageRole.ASSISTANT,
                content = "", status = MessageStatus.STREAMING,
                providerId = conversation.selectedProviderId, modelId = conversation.selectedModelId,
                requestSnapshotJson = snapshot,
                createdAt = now + 1, updatedAt = now + 1,
            ))
            database.conversationDao().setLeaf(original.conversationId, assistantId, now)
        }
        if (conversation.autoTitle && automationSettingsNow().titleMode == AuxiliaryMode.LOCAL) regenerateTitle(original.conversationId)
        return assistantId
    }

    suspend fun activateBranch(nodeId: String) {
        val requestedTarget = requireNotNull(database.messageDao().get(nodeId))
        val conversation = requireNotNull(database.conversationDao().get(requestedTarget.conversationId))
        val allMessages = database.messageDao().allForConversation(requestedTarget.conversationId)
        // Selecting an edited user node restores its generated response too when
        // that direct sibling-pair is available. Assistant retries already point
        // at the response node itself.
        val target = if (requestedTarget.role == MessageRole.USER) {
            allMessages
                .filter { it.parentNodeId == requestedTarget.nodeId && it.role == MessageRole.ASSISTANT }
                .maxWithOrNull(compareBy<MessageEntity> { it.createdAt }.thenBy { it.rowId })
                ?: requestedTarget
        } else requestedTarget
        val targetPath = activeMessagePathNodeIds(allMessages, target.nodeId)
        val activeIds = allMessages.asSequence().filter { it.supersededAt == null }.map(MessageEntity::nodeId).toSet()
        val deactivate = (activeIds - targetPath).toList()
        val now = System.currentTimeMillis()
        database.withTransaction {
            if (deactivate.isNotEmpty()) database.messageDao().markSuperseded(deactivate, now)
            database.messageDao().clearSuperseded(targetPath.toList())
            database.conversationDao().setLeaf(conversation.id, target.nodeId, now)
        }
    }

    suspend fun retryAssistant(nodeId: String): String {
        val original = requireNotNull(database.messageDao().get(nodeId))
        require(original.role == MessageRole.ASSISTANT) { "Only assistant messages can be retried" }
        val conversation = requireNotNull(database.conversationDao().get(original.conversationId))
        val now = System.currentTimeMillis()
        val assistantId = UUID.randomUUID().toString()
        val snapshot = generationSnapshot(conversation)
        database.withTransaction {
            database.messageDao().markSuperseded(database.messageDao().descendantNodeIds(original.nodeId), now)
            database.messageDao().insert(MessageEntity(
                nodeId = assistantId, conversationId = original.conversationId,
                parentNodeId = original.parentNodeId, branchId = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT, content = "", status = MessageStatus.STREAMING,
                providerId = conversation.selectedProviderId, modelId = conversation.selectedModelId,
                requestSnapshotJson = snapshot,
                createdAt = now, updatedAt = now,
            ))
            database.conversationDao().setLeaf(original.conversationId, assistantId, now)
        }
        return assistantId
    }

    suspend fun deleteConversation(id: String) = database.conversationDao().delete(id)

    suspend fun renameConversation(id: String, title: String) {
        val clean = title.trim().replace(Regex("\\s+"), " ").take(120)
        require(clean.isNotBlank()) { "Chat name cannot be empty" }
        database.conversationDao().rename(id, clean, autoTitle = false, now = System.currentTimeMillis())
    }

    suspend fun archiveConversation(id: String, archived: Boolean) {
        val now = System.currentTimeMillis()
        database.conversationDao().setArchived(id, archived, if (archived) now else null, now)
    }

    suspend fun pinConversation(id: String, pinned: Boolean) =
        database.conversationDao().setPinned(id, pinned, System.currentTimeMillis())

    suspend fun moveConversation(id: String, projectId: String?) =
        database.conversationDao().setProject(id, projectId, System.currentTimeMillis())

    suspend fun createProject(name: String): ProjectEntity {
        val clean = name.trim().replace(Regex("\\s+"), " ").take(80)
        require(clean.isNotBlank()) { "Project name cannot be empty" }
        val now = System.currentTimeMillis()
        val palette = listOf(0xFF4F6BED, 0xFF00897B, 0xFFE16A3D, 0xFF8E5BB7, 0xFFD19A00, 0xFF4C7A34)
        val project = ProjectEntity(
            id = UUID.randomUUID().toString(),
            name = clean,
            colorArgb = palette[kotlin.math.abs(clean.hashCode()) % palette.size],
            createdAt = now,
            updatedAt = now,
        )
        database.projectDao().insert(project)
        return project
    }

    suspend fun renameProject(id: String, name: String) {
        val current = requireNotNull(database.projectDao().get(id))
        val clean = name.trim().replace(Regex("\\s+"), " ").take(80)
        require(clean.isNotBlank()) { "Project name cannot be empty" }
        database.projectDao().update(current.copy(name = clean, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProject(id: String) = database.withTransaction {
        database.conversationDao().detachProject(id)
        database.projectDao().delete(id)
    }

    suspend fun systemPromptProfile(id: String?) = id?.let { database.systemPromptProfileDao().get(it) }

    suspend fun createSystemPromptProfile(name: String, prompt: String, mode: app.xylune.chat.data.SystemPromptMode): SystemPromptProfileEntity {
        val cleanName = name.trim().replace(Regex("\\s+"), " ").take(80)
        require(cleanName.isNotBlank()) { "Prompt name cannot be empty" }
        val cleanPrompt = prompt.trim().take(64_000)
        require(cleanPrompt.isNotBlank()) { "Prompt cannot be empty" }
        val now = System.currentTimeMillis()
        return SystemPromptProfileEntity(UUID.randomUUID().toString(), cleanName, cleanPrompt, mode, now, now).also {
            database.systemPromptProfileDao().insert(it)
        }
    }

    suspend fun updateSystemPromptProfile(value: SystemPromptProfileEntity) {
        val cleanName = value.name.trim().replace(Regex("\\s+"), " ").take(80)
        val cleanPrompt = value.prompt.trim().take(64_000)
        require(cleanName.isNotBlank() && cleanPrompt.isNotBlank()) { "Prompt name and content are required" }
        database.systemPromptProfileDao().update(value.copy(name = cleanName, prompt = cleanPrompt, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSystemPromptProfile(id: String) = database.withTransaction {
        database.systemPromptProfileDao().detachFromConversations(id)
        database.systemPromptProfileDao().delete(id)
    }

    suspend fun enabledMemories(limit: Int = 500): List<MemoryEntity> = database.memoryDao().enabled(limit)

    suspend fun memoriesForContext(
        messagesNewestFirst: List<MessageEntity>,
        currentConversationId: String?,
        maxItems: Int = MemoryManagement.DEFAULT_CONTEXT_ITEMS,
        maxCharacters: Int = MemoryManagement.DEFAULT_CONTEXT_CHARACTERS,
    ): List<MemoryEntity> = MemoryManagement.selectForContext(
        memories = database.memoryDao().enabled(500),
        messagesNewestFirst = messagesNewestFirst,
        currentConversationId = currentConversationId,
        maxItems = maxItems,
        maxCharacters = maxCharacters,
    )

    suspend fun searchMemories(
        query: String,
        includeDisabled: Boolean = false,
        limit: Int = 100,
    ): List<MemoryEntity> = MemoryManagement.search(
        memories = database.memoryDao().all(),
        query = query,
        includeDisabled = includeDisabled,
        limit = limit,
    )

    suspend fun saveMemory(
        content: String,
        category: String = "general",
        sourceConversationId: String? = null,
    ): MemoryEntity = saveMemoryManaged(content, category, sourceConversationId).memory

    suspend fun saveMemoryManaged(
        content: String,
        category: String = "general",
        sourceConversationId: String? = null,
    ): MemoryWriteResult = writeMemory(
        existingId = null,
        content = content,
        category = category,
        sourceConversationId = sourceConversationId,
    )

    suspend fun updateMemory(id: String, content: String, category: String): MemoryWriteResult = writeMemory(
        existingId = id,
        content = content,
        category = category,
        sourceConversationId = null,
    )

    private suspend fun writeMemory(
        existingId: String?,
        content: String,
        category: String,
        sourceConversationId: String?,
    ): MemoryWriteResult = database.withTransaction {
        val clean = MemoryManagement.cleanContent(content)
        require(clean.isNotBlank()) { "Memory content cannot be empty" }
        val cleanCategory = MemoryManagement.cleanCategory(category)
        val canonicalKey = MemoryManagement.canonicalKey(clean, cleanCategory)
        val dao = database.memoryDao()
        val target = existingId?.let { id -> requireNotNull(dao.get(id)) { "Memory no longer exists" } }
        val all = dao.all()
        val keyOwner = dao.byNormalizedKey(canonicalKey)?.takeIf { it.id != target?.id }
        val duplicate = keyOwner ?: MemoryManagement.findDuplicate(
            memories = all,
            content = clean,
            category = cleanCategory,
            excludingId = target?.id,
        )
        val now = System.currentTimeMillis()

        if (target != null && duplicate != null && target.id != duplicate.id) {
            val merged = duplicate.copy(
                normalizedKey = canonicalKey,
                content = clean,
                category = cleanCategory,
                sourceConversationId = sourceConversationId
                    ?: target.sourceConversationId
                    ?: duplicate.sourceConversationId,
                enabled = target.enabled || duplicate.enabled,
                updatedAt = now,
            )
            dao.upsert(merged)
            dao.delete(target.id)
            return@withTransaction MemoryWriteResult(
                memory = merged,
                created = false,
                mergedMemoryId = target.id,
            )
        }

        val base = duplicate ?: target
        val value = if (base == null) {
            MemoryEntity(
                id = UUID.randomUUID().toString(),
                normalizedKey = canonicalKey,
                content = clean,
                category = cleanCategory,
                sourceConversationId = sourceConversationId,
                enabled = true,
                createdAt = now,
                updatedAt = now,
            )
        } else base.copy(
            normalizedKey = canonicalKey,
            content = clean,
            category = cleanCategory,
            sourceConversationId = sourceConversationId ?: base.sourceConversationId,
            enabled = if (existingId == null) true else base.enabled,
            updatedAt = now,
        )
        dao.upsert(value)
        MemoryWriteResult(memory = value, created = base == null)
    }

    suspend fun deleteMemory(id: String): Boolean = database.memoryDao().delete(id) > 0
    suspend fun setMemoryEnabled(id: String, enabled: Boolean) =
        database.memoryDao().setEnabled(id, enabled, System.currentTimeMillis())
    suspend fun setMemoriesEnabled(ids: Collection<String>, enabled: Boolean): Int {
        val distinctIds = ids.filter(String::isNotBlank).distinct()
        return if (distinctIds.isEmpty()) 0
        else database.memoryDao().setEnabled(distinctIds, enabled, System.currentTimeMillis())
    }
    suspend fun deleteMemories(ids: Collection<String>): Int {
        val distinctIds = ids.filter(String::isNotBlank).distinct()
        return if (distinctIds.isEmpty()) 0 else database.memoryDao().delete(distinctIds)
    }
    suspend fun setAllMemoriesEnabled(enabled: Boolean): Int =
        database.memoryDao().setAllEnabled(enabled, System.currentTimeMillis())
    suspend fun deleteDisabledMemories(): Int = database.memoryDao().deleteDisabled()

    suspend fun automationSettingsNow(): AutomationSettingsEntity {
        val existing = database.automationSettingsDao().get()
        if (existing != null) return existing
        val created = AutomationSettingsEntity()
        database.automationSettingsDao().upsert(created)
        return created
    }

    suspend fun saveAutomationSettings(value: AutomationSettingsEntity) = database.automationSettingsDao().upsert(value)

    suspend fun contextSummary(conversationId: String) = database.contextSummaryDao().get(conversationId)
    fun observeContextSummary(conversationId: String) = database.contextSummaryDao().observe(conversationId)
    suspend fun saveContextSummary(value: ContextSummaryEntity) = database.contextSummaryDao().upsert(value)
    suspend fun clearContextSummary(conversationId: String) = database.contextSummaryDao().delete(conversationId)

    suspend fun deleteStagedAttachment(id: String) = database.attachmentDao().get(id)?.let { attachment ->
        if (database.attachmentDao().deleteStaged(id) > 0) attachment else null
    }

    private suspend fun generationSnapshot(conversation: ConversationEntity): String {
        val provider = requireNotNull(database.catalogDao().provider(conversation.selectedProviderId)) {
            "Provider ${conversation.selectedProviderId} is not configured"
        }
        val model = requireNotNull(database.catalogDao().model(conversation.selectedProviderId, conversation.selectedModelId)) {
            "Model ${conversation.selectedModelId} is not configured"
        }
        val promptProfile = systemPromptProfile(conversation.systemPromptProfileId)
        return Json.encodeToString(GenerationRequestSnapshot.capture(conversation, provider, model, promptProfile))
    }

    fun search(text: String): Flow<List<SearchHit>> {
        val safe = text.trim().split(Regex("\\s+")).filter(String::isNotBlank).joinToString(" ") { "\"${it.replace("\"", "\"\"")}\"*" }
        val like = "%${text.trim()}%"
        return database.messageDao().search(SimpleSQLiteQuery(
            """
                SELECT nodeId, conversationId, conversationTitle, snippet, rank FROM (
                    SELECT COALESCE(c.activeLeafNodeId, c.id) AS nodeId, c.id AS conversationId,
                        c.title AS conversationTitle, c.title AS snippet, -100.0 AS rank
                    FROM conversations c WHERE c.title LIKE ?
                    UNION ALL
                    SELECT message_fts.nodeId, message_fts.conversationId, c.title AS conversationTitle,
                        snippet(message_fts, 2, '[', ']', ' … ', 18) AS snippet,
                        bm25(message_fts) AS rank
                    FROM message_fts JOIN conversations c ON c.id = message_fts.conversationId
                    WHERE message_fts MATCH ?
                ) ORDER BY rank LIMIT 100
            """.trimIndent(),
            arrayOf(like, safe),
        ))
    }
}
