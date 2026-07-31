package app.arbor.chat.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("""
        SELECT c.*,
            EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.status = 'STREAMING') AS isResponding,
            EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.status IN ('INTERRUPTED', 'ERROR')) AS needsAttention,
            (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id AND m.role = 'ASSISTANT' AND m.status = 'COMPLETE' AND m.updatedAt > c.lastReadAt) AS unreadCount,
            p.name AS projectName
        FROM conversations c
        LEFT JOIN projects p ON p.id = c.projectId
        WHERE c.archived = 0
          AND (EXISTS(SELECT 1 FROM messages visible_m WHERE visible_m.conversationId = c.id)
               OR EXISTS(SELECT 1 FROM attachments visible_a WHERE visible_a.conversationId = c.id))
        ORDER BY c.pinned DESC, c.updatedAt DESC
    """)
    fun observeAll(): Flow<List<ConversationListItem>>

    @Query("""
        SELECT c.*,
            EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.status = 'STREAMING') AS isResponding,
            EXISTS(SELECT 1 FROM messages m WHERE m.conversationId = c.id AND m.status IN ('INTERRUPTED', 'ERROR')) AS needsAttention,
            (SELECT COUNT(*) FROM messages m WHERE m.conversationId = c.id AND m.role = 'ASSISTANT' AND m.status = 'COMPLETE' AND m.updatedAt > c.lastReadAt) AS unreadCount,
            p.name AS projectName
        FROM conversations c
        LEFT JOIN projects p ON p.id = c.projectId
        WHERE c.archived = 1
          AND (EXISTS(SELECT 1 FROM messages visible_m WHERE visible_m.conversationId = c.id)
               OR EXISTS(SELECT 1 FROM attachments visible_a WHERE visible_a.conversationId = c.id))
        ORDER BY c.archivedAt DESC, c.updatedAt DESC
    """)
    fun observeArchived(): Flow<List<ConversationListItem>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations ORDER BY createdAt, id")
    suspend fun all(): List<ConversationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: ConversationEntity)

    @Update suspend fun update(value: ConversationEntity)

    @Query("UPDATE conversations SET activeLeafNodeId = :leaf, updatedAt = :now WHERE id = :id")
    suspend fun setLeaf(id: String, leaf: String, now: Long)

    @Query("UPDATE conversations SET totalInputTokens = totalInputTokens + :input, totalOutputTokens = totalOutputTokens + :output, totalCostMicros = totalCostMicros + :cost, hasUnknownCost = CASE WHEN :costKnown THEN hasUnknownCost ELSE 1 END, updatedAt = :now WHERE id = :id")
    suspend fun addUsage(id: String, input: Long, output: Long, cost: Long, costKnown: Boolean, now: Long)

    @Query("UPDATE conversations SET lastReadAt = :now WHERE id = :id")
    suspend fun markRead(id: String, now: Long)

    @Query("UPDATE conversations SET title = :title, autoTitle = :autoTitle, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, autoTitle: Boolean, now: Long)

    @Query("UPDATE conversations SET archived = :archived, archivedAt = :archivedAt, pinned = CASE WHEN :archived THEN 0 ELSE pinned END, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, archivedAt: Long?, now: Long)

    @Query("UPDATE conversations SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE conversations SET projectId = :projectId, updatedAt = :now WHERE id = :id")
    suspend fun setProject(id: String, projectId: String?, now: Long)

    @Query("UPDATE conversations SET projectId = NULL WHERE projectId = :projectId")
    suspend fun detachProject(projectId: String)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)

    @Query("""
        DELETE FROM conversations
        WHERE NOT EXISTS (SELECT 1 FROM messages m WHERE m.conversationId = conversations.id)
          AND NOT EXISTS (SELECT 1 FROM attachments a WHERE a.conversationId = conversations.id)
    """)
    suspend fun deleteTrulyEmpty()
}


@Dao
interface SystemPromptProfileDao {
    @Query("SELECT * FROM system_prompt_profiles ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SystemPromptProfileEntity>>

    @Query("SELECT * FROM system_prompt_profiles ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<SystemPromptProfileEntity>

    @Query("SELECT * FROM system_prompt_profiles WHERE id = :id")
    suspend fun get(id: String): SystemPromptProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: SystemPromptProfileEntity)

    @Update
    suspend fun update(value: SystemPromptProfileEntity)

    @Query("UPDATE conversations SET systemPromptProfileId = NULL WHERE systemPromptProfileId = :id")
    suspend fun detachFromConversations(id: String)

    @Query("DELETE FROM system_prompt_profiles WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: ProjectEntity)

    @Update
    suspend fun update(value: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface AutomationSettingsDao {
    @Query("SELECT * FROM automation_settings WHERE id = 'default'")
    fun observe(): Flow<AutomationSettingsEntity?>

    @Query("SELECT * FROM automation_settings WHERE id = 'default'")
    suspend fun get(): AutomationSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: AutomationSettingsEntity)
}

@Dao
interface ContextSummaryDao {
    @Query("SELECT * FROM context_summaries WHERE conversationId = :conversationId")
    suspend fun get(conversationId: String): ContextSummaryEntity?

    @Query("SELECT * FROM context_summaries WHERE conversationId = :conversationId")
    fun observe(conversationId: String): Flow<ContextSummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: ContextSummaryEntity)

    @Query("DELETE FROM context_summaries WHERE conversationId = :conversationId")
    suspend fun delete(conversationId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND supersededAt IS NULL ORDER BY createdAt DESC, rowId DESC")
    fun paging(conversationId: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND supersededAt IS NULL ORDER BY createdAt DESC, rowId DESC LIMIT :limit")
    suspend fun recent(conversationId: String, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = 'USER'")
    suspend fun userMessageCount(conversationId: String): Int

    @Query("SELECT * FROM messages WHERE nodeId = :nodeId")
    suspend fun get(nodeId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, rowId ASC")
    suspend fun allForConversation(conversationId: String): List<MessageEntity>


    @Query("""
        SELECT COUNT(*) FROM messages m
        WHERE m.conversationId = :conversationId AND m.supersededAt IS NULL
          AND (m.createdAt > (SELECT createdAt FROM messages WHERE nodeId = :nodeId)
               OR (m.createdAt = (SELECT createdAt FROM messages WHERE nodeId = :nodeId)
                   AND m.rowId > (SELECT rowId FROM messages WHERE nodeId = :nodeId)))
    """)
    suspend fun indexFromLatest(conversationId: String, nodeId: String): Int

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND supersededAt IS NULL AND status IN ('STREAMING','INTERRUPTED','ERROR') ORDER BY updatedAt DESC")
    fun observeRecoverable(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND supersededAt IS NOT NULL ORDER BY supersededAt DESC, createdAt DESC")
    fun observeSuperseded(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status = 'STREAMING'")
    suspend fun streamingMessages(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND supersededAt IS NULL AND status = 'STREAMING' ORDER BY updatedAt DESC LIMIT 1")
    suspend fun streamingForConversation(conversationId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND supersededAt IS NULL AND status = 'STREAMING' ORDER BY updatedAt DESC")
    suspend fun streamingForConversationAll(conversationId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(value: MessageEntity): Long

    @Update suspend fun update(value: MessageEntity)

    @Query("UPDATE messages SET content = content || :text, reasoning = reasoning || :reasoning, streamOffset = streamOffset + :offset, updatedAt = :now WHERE nodeId = :nodeId")
    suspend fun append(nodeId: String, text: String, reasoning: String, offset: Int, now: Long)

    @Query("UPDATE messages SET content = :content, reasoning = :reasoning, toolTraceJson = :toolTraceJson, timelineJson = :timelineJson, streamOffset = :offset, updatedAt = :now WHERE nodeId = :nodeId")
    suspend fun replaceWorkingState(nodeId: String, content: String, reasoning: String, toolTraceJson: String, timelineJson: String, offset: Int, now: Long)

    @Query("UPDATE messages SET status = :status, error = :error, inputTokens = :input, outputTokens = :output, cachedInputTokens = :cached, costMicros = :cost, costKnown = :costKnown, updatedAt = :now WHERE nodeId = :nodeId")
    suspend fun finish(nodeId: String, status: MessageStatus, error: String?, input: Long, output: Long, cached: Long, cost: Long, costKnown: Boolean, now: Long)

    @Query("UPDATE messages SET status = 'STREAMING', error = NULL, updatedAt = :now WHERE nodeId = :nodeId")
    suspend fun markStreaming(nodeId: String, now: Long)

    @Query("UPDATE messages SET status = 'STREAMING', error = :reason, updatedAt = :now WHERE nodeId = :nodeId")
    suspend fun markRetrying(nodeId: String, reason: String, now: Long)

    @Query("UPDATE messages SET status = 'INTERRUPTED', error = :reason, updatedAt = :now WHERE nodeId = :nodeId AND status = 'STREAMING'")
    suspend fun interruptIfStreaming(nodeId: String, reason: String, now: Long)

    @Query("WITH RECURSIVE descendants(nodeId) AS (SELECT :root UNION ALL SELECT m.nodeId FROM messages m JOIN descendants d ON m.parentNodeId = d.nodeId) SELECT nodeId FROM descendants")
    suspend fun descendantNodeIds(root: String): List<String>

    @Query("UPDATE messages SET supersededAt = :now WHERE nodeId IN (:nodeIds)")
    suspend fun markSuperseded(nodeIds: List<String>, now: Long)

    @Query("UPDATE messages SET supersededAt = NULL WHERE nodeId IN (:nodeIds)")
    suspend fun clearSuperseded(nodeIds: List<String>)

    @Query("UPDATE messages SET status = 'INTERRUPTED', error = 'Generation process stopped', updatedAt = :now WHERE status = 'STREAMING'")
    suspend fun markOrphanedStreamsInterrupted(now: Long)

    @RawQuery(observedEntities = [MessageEntity::class, ConversationEntity::class])
    fun search(query: SupportSQLiteQuery): Flow<List<SearchHit>>
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageNodeId = :nodeId ORDER BY createdAt")
    fun observeForMessage(nodeId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageNodeId = :nodeId ORDER BY createdAt")
    suspend fun forMessage(nodeId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId")
    suspend fun forConversation(conversationId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE conversationId = :conversationId AND messageNodeId IS NULL ORDER BY createdAt")
    suspend fun stagedForConversation(conversationId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: String): AttachmentEntity?

    @Query("DELETE FROM attachments WHERE id = :id AND messageNodeId IS NULL")
    suspend fun deleteStaged(id: String): Int

    @Query("UPDATE attachments SET messageNodeId = :nodeId WHERE id IN (:ids)")
    suspend fun attachToMessage(ids: List<String>, nodeId: String)

    @Query("UPDATE attachments SET ocrJson = NULL, imageDescription = NULL, extractedText = NULL WHERE mimeType LIKE 'image/%' AND messageNodeId IN (SELECT nodeId FROM messages WHERE role = 'ASSISTANT')")
    suspend fun clearAssistantImageAnalysis()
}

@Dao
interface CatalogDao {
    @Query("SELECT * FROM providers WHERE enabled = 1 ORDER BY displayName")
    fun observeProviders(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers ORDER BY displayName")
    suspend fun allProviders(): List<ProviderEntity>

    @Query("SELECT * FROM models ORDER BY providerId, displayName")
    suspend fun allModels(): List<ModelEntity>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun provider(id: String): ProviderEntity?

    @Query("SELECT * FROM models WHERE providerId = :providerId ORDER BY displayName")
    fun observeModels(providerId: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE providerId = :providerId AND modelId = :modelId")
    suspend fun model(providerId: String, modelId: String): ModelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProviders(values: List<ProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModels(values: List<ModelEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProvidersIfMissing(values: List<ProviderEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModelsIfMissing(values: List<ModelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProvider(value: ProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(value: ModelEntity)

    @Query("DELETE FROM models WHERE providerId = :providerId")
    suspend fun deleteModels(providerId: String)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_messages WHERE conversationId = :conversationId ORDER BY position")
    fun observe(conversationId: String): Flow<List<PendingMessageEntity>>

    @Query("SELECT * FROM pending_messages WHERE conversationId = :conversationId ORDER BY position LIMIT 1")
    suspend fun next(conversationId: String): PendingMessageEntity?

    @Query("SELECT COALESCE(MAX(position), 0) FROM pending_messages WHERE conversationId = :conversationId")
    suspend fun maxPosition(conversationId: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: PendingMessageEntity)

    @Query("DELETE FROM pending_messages WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface GenerationUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: GenerationUsageEntity)

    @Query("SELECT * FROM generation_usage WHERE assistantNodeId = :assistantNodeId ORDER BY roundIndex, createdAt")
    suspend fun forAssistant(assistantNodeId: String): List<GenerationUsageEntity>

    @Query("SELECT COALESCE(SUM(inputTokens), 0) FROM generation_usage WHERE assistantNodeId = :assistantNodeId")
    suspend fun totalInput(assistantNodeId: String): Long

    @Query("SELECT COALESCE(SUM(outputTokens), 0) FROM generation_usage WHERE assistantNodeId = :assistantNodeId")
    suspend fun totalOutput(assistantNodeId: String): Long

    @Query("SELECT COALESCE(SUM(cachedInputTokens), 0) FROM generation_usage WHERE assistantNodeId = :assistantNodeId")
    suspend fun totalCached(assistantNodeId: String): Long

    @Query("SELECT COALESCE(SUM(costMicros), 0) FROM generation_usage WHERE assistantNodeId = :assistantNodeId")
    suspend fun totalCost(assistantNodeId: String): Long
}

@Dao
interface PackageTransactionDao {
    @Query("SELECT * FROM package_transactions WHERE operationKey = :operationKey")
    suspend fun get(operationKey: String): PackageTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(value: PackageTransactionEntity)
}
