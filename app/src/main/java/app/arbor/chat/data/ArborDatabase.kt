package app.arbor.chat.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ProviderEntity::class,
        ModelEntity::class,
        PendingMessageEntity::class,
        ProjectEntity::class,
        AutomationSettingsEntity::class,
        ContextSummaryEntity::class,
        GenerationUsageEntity::class,
        PackageTransactionEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ArborDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun catalogDao(): CatalogDao
    abstract fun pendingDao(): PendingDao
    abstract fun projectDao(): ProjectDao
    abstract fun automationSettingsDao(): AutomationSettingsDao
    abstract fun contextSummaryDao(): ContextSummaryDao
    abstract fun generationUsageDao(): GenerationUsageDao
    abstract fun packageTransactionDao(): PackageTransactionDao

    companion object {
        fun create(context: Context, passphrase: ByteArray): ArborDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context, ArborDatabase::class.java, "arbor.db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        createSearchIndex(db, backfill = false)
                    }
                })
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN lastReadAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN autoTitle INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN reasoningVisibility TEXT NOT NULL DEFAULT 'SHOW_WHILE_WORKING'")
                db.execSQL("ALTER TABLE conversations ADD COLUMN webSearchEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN agentPythonEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE messages ADD COLUMN supersededAt INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN timelineJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN projectId TEXT")
                db.execSQL("ALTER TABLE conversations ADD COLUMN archivedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_projectId ON conversations(projectId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS projects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, colorArgb INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_projects_name ON projects(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS automation_settings (id TEXT NOT NULL PRIMARY KEY, titleMode TEXT NOT NULL, titleProviderId TEXT NOT NULL, titleModelId TEXT NOT NULL, compressionMode TEXT NOT NULL, compressionProviderId TEXT NOT NULL, compressionModelId TEXT NOT NULL)")
                db.execSQL("INSERT OR IGNORE INTO automation_settings(id, titleMode, titleProviderId, titleModelId, compressionMode, compressionProviderId, compressionModelId) VALUES ('default', 'LOCAL', '', '', 'LOCAL', '', '')")
                db.execSQL("CREATE TABLE IF NOT EXISTS context_summaries (conversationId TEXT NOT NULL PRIMARY KEY, summary TEXT NOT NULL, throughCreatedAt INTEGER NOT NULL, sourceMessageCount INTEGER NOT NULL, tokenEstimate INTEGER NOT NULL, providerId TEXT, modelId TEXT, updatedAt INTEGER NOT NULL, FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN agentUbuntuEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE automation_settings ADD COLUMN packageApprovalMode TEXT NOT NULL DEFAULT 'ALWAYS_ASK'")
                db.execSQL("ALTER TABLE automation_settings ADD COLUMN approvalProviderId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE automation_settings ADD COLUMN approvalModelId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE automation_settings ADD COLUMN packageRestrictionsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE automation_settings ADD COLUMN trustedPythonPackages TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE automation_settings ADD COLUMN trustedUbuntuPackages TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE providers ADD COLUMN registered INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE providers ADD COLUMN apiKeyRequired INTEGER NOT NULL DEFAULT 1")
                db.execSQL("UPDATE providers SET apiKeyRequired = 0 WHERE id = 'ollama'")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN requestSnapshotJson TEXT")
                db.execSQL("ALTER TABLE context_summaries ADD COLUMN throughRowId INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS generation_usage (
                        id TEXT NOT NULL PRIMARY KEY,
                        assistantNodeId TEXT NOT NULL,
                        conversationId TEXT NOT NULL,
                        providerId TEXT NOT NULL,
                        modelId TEXT NOT NULL,
                        roundIndex INTEGER NOT NULL,
                        inputTokens INTEGER NOT NULL,
                        outputTokens INTEGER NOT NULL,
                        cachedInputTokens INTEGER NOT NULL,
                        costMicros INTEGER NOT NULL,
                        finishReason TEXT,
                        status TEXT NOT NULL,
                        error TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(assistantNodeId) REFERENCES messages(nodeId) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_usage_assistantNodeId ON generation_usage(assistantNodeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_usage_conversationId ON generation_usage(conversationId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_generation_usage_conversationId_createdAt ON generation_usage(conversationId, createdAt)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS package_transactions (
                        operationKey TEXT NOT NULL PRIMARY KEY,
                        conversationId TEXT NOT NULL,
                        ecosystem TEXT NOT NULL,
                        requirements TEXT NOT NULL,
                        planJson TEXT NOT NULL,
                        planFingerprint TEXT NOT NULL,
                        status TEXT NOT NULL,
                        resultSummary TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_package_transactions_conversationId ON package_transactions(conversationId)")
                createSearchIndex(db, backfill = true)
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN workingTokenLimit INTEGER NOT NULL DEFAULT 16000")
                db.execSQL("ALTER TABLE conversations ADD COLUMN hasUnknownCost INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE messages ADD COLUMN costKnown INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE generation_usage ADD COLUMN costKnown INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE models ADD COLUMN pricingConfigured INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE models SET pricingConfigured = 1 WHERE inputCacheHitUsdPerMillion > 0 OR inputCacheMissUsdPerMillion > 0 OR outputUsdPerMillion > 0")
                db.execSQL("UPDATE messages SET costKnown = 1 WHERE costMicros > 0 OR (providerId = 'deepseek' AND (inputTokens > 0 OR outputTokens > 0))")
                db.execSQL("UPDATE generation_usage SET costKnown = 1 WHERE costMicros > 0 OR providerId = 'deepseek'")
                db.execSQL("UPDATE conversations SET hasUnknownCost = 1 WHERE EXISTS (SELECT 1 FROM messages WHERE messages.conversationId = conversations.id AND (messages.inputTokens > 0 OR messages.outputTokens > 0) AND messages.costKnown = 0)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN thinkingEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE conversations ADD COLUMN thinkingEffort TEXT NOT NULL DEFAULT 'MEDIUM'")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN deepResearchEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE conversations ADD COLUMN hybridTokenCountingEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        private fun createSearchIndex(db: SupportSQLiteDatabase, backfill: Boolean) {
            db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS message_fts USING fts5(nodeId UNINDEXED, conversationId UNINDEXED, content, reasoning, tokenize='unicode61')")
            db.execSQL("DROP TRIGGER IF EXISTS messages_ai")
            db.execSQL("DROP TRIGGER IF EXISTS messages_ad")
            db.execSQL("DROP TRIGGER IF EXISTS messages_au")
            db.execSQL("CREATE TRIGGER messages_ai AFTER INSERT ON messages WHEN new.status != 'STREAMING' BEGIN INSERT INTO message_fts(nodeId, conversationId, content, reasoning) VALUES (new.nodeId, new.conversationId, new.content, new.reasoning); END")
            db.execSQL("CREATE TRIGGER messages_ad AFTER DELETE ON messages BEGIN DELETE FROM message_fts WHERE nodeId = old.nodeId; END")
            db.execSQL("CREATE TRIGGER messages_au AFTER UPDATE OF content, reasoning, status ON messages WHEN new.status != 'STREAMING' BEGIN DELETE FROM message_fts WHERE nodeId = old.nodeId; INSERT INTO message_fts(nodeId, conversationId, content, reasoning) VALUES (new.nodeId, new.conversationId, new.content, new.reasoning); END")
            if (backfill) {
                db.execSQL("DELETE FROM message_fts")
                db.execSQL("INSERT INTO message_fts(nodeId, conversationId, content, reasoning) SELECT nodeId, conversationId, content, reasoning FROM messages WHERE status != 'STREAMING'")
            }
        }
    }
}
