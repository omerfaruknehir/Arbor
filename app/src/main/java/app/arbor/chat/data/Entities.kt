package app.arbor.chat.data

import androidx.room.Entity
import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }
enum class MessageStatus { QUEUED, STREAMING, INTERRUPTED, COMPLETE, ERROR }
enum class SendMode { SEND_NOW, QUEUE, STEER }
@Serializable enum class ProviderKind { OPENAI_COMPATIBLE, ANTHROPIC, GEMINI }
enum class ReasoningVisibility { ALWAYS, SHOW_WHILE_WORKING, COLLAPSED }
enum class ThinkingEffort { MINIMAL, LOW, MEDIUM, HIGH }
enum class AuxiliaryMode { OFF, LOCAL, MODEL }
enum class PackageApprovalMode { ALWAYS_ASK, TRUSTED_ONLY, MODEL_REVIEW, AUTO_APPROVE }

@Entity(tableName = "conversations", indices = [Index("projectId")])
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val activeLeafNodeId: String? = null,
    val selectedProviderId: String = "deepseek",
    val selectedModelId: String = "deepseek-v4-flash",
    val contextPairs: Int = 24,
    val contextTokenLimit: Int = 64_000,
    @ColumnInfo(defaultValue = "16000") val workingTokenLimit: Int = 16_000,
    val maxOutputTokens: Int = 8_192,
    val systemPrompt: String = "",
    val totalInputTokens: Long = 0,
    val totalOutputTokens: Long = 0,
    val totalCostMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val hasUnknownCost: Boolean = false,
    @ColumnInfo(defaultValue = "0") val lastReadAt: Long = 0,
    @ColumnInfo(defaultValue = "1") val autoTitle: Boolean = true,
    @ColumnInfo(defaultValue = "'SHOW_WHILE_WORKING'") val reasoningVisibility: ReasoningVisibility = ReasoningVisibility.SHOW_WHILE_WORKING,
    @ColumnInfo(defaultValue = "1") val thinkingEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "'MEDIUM'") val thinkingEffort: ThinkingEffort = ThinkingEffort.MEDIUM,
    @ColumnInfo(defaultValue = "1") val webSearchEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "1") val agentPythonEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val agentUbuntuEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val deepResearchEnabled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hybridTokenCountingEnabled: Boolean = false,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    val projectId: String? = null,
    val archivedAt: Long? = null,
)

data class ConversationListItem(
    @Embedded val conversation: ConversationEntity,
    val isResponding: Boolean,
    val needsAttention: Boolean,
    val unreadCount: Int,
    val projectName: String? = null,
)

@Entity(tableName = "projects", indices = [Index(value = ["name"], unique = true)])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "automation_settings")
data class AutomationSettingsEntity(
    @PrimaryKey val id: String = "default",
    val titleMode: AuxiliaryMode = AuxiliaryMode.LOCAL,
    val titleProviderId: String = "",
    val titleModelId: String = "",
    val compressionMode: AuxiliaryMode = AuxiliaryMode.LOCAL,
    val compressionProviderId: String = "",
    val compressionModelId: String = "",
    @ColumnInfo(defaultValue = "'ALWAYS_ASK'") val packageApprovalMode: PackageApprovalMode = PackageApprovalMode.ALWAYS_ASK,
    @ColumnInfo(defaultValue = "''") val approvalProviderId: String = "",
    @ColumnInfo(defaultValue = "''") val approvalModelId: String = "",
    @ColumnInfo(defaultValue = "1") val packageRestrictionsEnabled: Boolean = true,
    @ColumnInfo(defaultValue = "''") val trustedPythonPackages: String = "",
    @ColumnInfo(defaultValue = "''") val trustedUbuntuPackages: String = "",
)

@Entity(
    tableName = "context_summaries",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class ContextSummaryEntity(
    @PrimaryKey val conversationId: String,
    val summary: String,
    val throughCreatedAt: Long,
    @ColumnInfo(defaultValue = "0") val throughRowId: Long = 0,
    val sourceMessageCount: Int,
    val tokenEstimate: Int,
    val providerId: String? = null,
    val modelId: String? = null,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["nodeId"], unique = true),
        Index(value = ["conversationId", "createdAt"]),
        Index(value = ["parentNodeId"]),
        Index(value = ["status"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val nodeId: String,
    val conversationId: String,
    val parentNodeId: String?,
    val branchId: String,
    val role: MessageRole,
    val content: String,
    val reasoning: String = "",
    val toolTraceJson: String = "[]",
    @ColumnInfo(defaultValue = "'[]'") val timelineJson: String = "[]",
    val status: MessageStatus,
    val providerId: String? = null,
    val modelId: String? = null,
    val requestSnapshotJson: String? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val costMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val costKnown: Boolean = false,
    val streamOffset: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String? = null,
    val supersededAt: Long? = null,
)

@Entity(
    tableName = "generation_usage",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["nodeId"],
            childColumns = ["assistantNodeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("assistantNodeId"), Index("conversationId"), Index(value = ["conversationId", "createdAt"])],
)
data class GenerationUsageEntity(
    @PrimaryKey val id: String,
    val assistantNodeId: String,
    val conversationId: String,
    val providerId: String,
    val modelId: String,
    val roundIndex: Int,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val costMicros: Long = 0,
    @ColumnInfo(defaultValue = "0") val costKnown: Boolean = false,
    val finishReason: String? = null,
    val status: String,
    val error: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "package_transactions",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId")],
)
data class PackageTransactionEntity(
    @PrimaryKey val operationKey: String,
    val conversationId: String,
    val ecosystem: String,
    val requirements: String,
    val planJson: String,
    val planFingerprint: String,
    val status: String,
    val resultSummary: String = "",
    val updatedAt: Long,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["nodeId"],
        childColumns = ["messageNodeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("messageNodeId"), Index("conversationId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val messageNodeId: String?,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localPath: String,
    val thumbnailPath: String? = null,
    val ocrJson: String? = null,
    val imageDescription: String? = null,
    val extractedText: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val enabled: Boolean = true,
    val customHeadersJson: String = "{}",
    @ColumnInfo(defaultValue = "0") val registered: Boolean = false,
    @ColumnInfo(defaultValue = "1") val apiKeyRequired: Boolean = true,
)

@Entity(
    tableName = "models",
    primaryKeys = ["providerId", "modelId"],
    indices = [Index("providerId")],
)
data class ModelEntity(
    val providerId: String,
    val modelId: String,
    val displayName: String,
    val contextWindow: Int,
    val maxOutputTokens: Int,
    val inputCacheHitUsdPerMillion: Double,
    val inputCacheMissUsdPerMillion: Double,
    val outputUsdPerMillion: Double,
    @ColumnInfo(defaultValue = "0") val pricingConfigured: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsFiles: Boolean = false,
    val supportsThinking: Boolean = false,
    val supportsTools: Boolean = false,
)

@Entity(
    tableName = "pending_messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversationId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("conversationId", "position")],
)
data class PendingMessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val content: String,
    val attachmentIdsJson: String = "[]",
    val position: Long,
    val createdAt: Long,
)

data class SearchHit(
    val nodeId: String,
    val conversationId: String,
    val conversationTitle: String,
    val snippet: String,
    val rank: Double,
)
