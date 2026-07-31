package app.arbor.chat.transfer

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import app.arbor.chat.BuildConfig
import app.arbor.chat.data.ArborDatabase
import app.arbor.chat.data.AttachmentEntity
import app.arbor.chat.data.ConversationEntity
import app.arbor.chat.data.MessageEntity
import app.arbor.chat.data.MessageRole
import app.arbor.chat.data.MessageStatus
import app.arbor.chat.data.ReasoningVisibility
import app.arbor.chat.data.ThinkingEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val ARBOR_CHAT_MIME = "application/vnd.arbor.chat"
const val ARBOR_BACKUP_MIME = "application/vnd.arbor.backup"
const val ARBOR_CHAT_EXTENSION = ".arborchat"
const val ARBOR_BACKUP_EXTENSION = ".arborbackup"


private const val ENVELOPE_SCHEMA = "arbor-archive-envelope-v1"
private const val MANIFEST_SCHEMA = "arbor-portable-archive-v1"
private const val PBKDF_ITERATIONS = 240_000

@Serializable
enum class ArchiveKind { CHAT, BACKUP }

@Serializable
data class ArchiveOptions(
    val includeAttachments: Boolean = true,
    val includeReasoning: Boolean = false,
    val includeToolData: Boolean = false,
    val includeSystemPrompt: Boolean = false,
    val includeRequestMetadata: Boolean = false,
    val includeLinuxEnvironments: Boolean = false,
)

@Serializable
private data class EnvelopeHeader(
    val schema: String = ENVELOPE_SCHEMA,
    val kind: ArchiveKind,
    val encrypted: Boolean,
    val createdAt: Long,
    val saltBase64: String? = null,
    val ivBase64: String? = null,
    val iterations: Int = PBKDF_ITERATIONS,
)

@Serializable
private data class ArchiveManifest(
    val schema: String = MANIFEST_SCHEMA,
    val kind: ArchiveKind,
    val createdAt: Long,
    val appVersion: String,
    val title: String,
    val options: ArchiveOptions,
    val conversations: List<PortableConversationBundle>,
    val linuxEnvironments: List<PortableLinuxEnvironment> = emptyList(),
)

@Serializable
private data class PortableConversationBundle(
    val conversation: PortableConversation,
    val messages: List<PortableMessage>,
    val attachments: List<PortableAttachment>,
)

@Serializable
private data class PortableConversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val activeLeafNodeId: String?,
    val selectedProviderId: String,
    val selectedModelId: String,
    val contextPairs: Int,
    val contextTokenLimit: Int,
    val workingTokenLimit: Int,
    val maxOutputTokens: Int,
    val systemPrompt: String,
    val totalInputTokens: Long,
    val totalOutputTokens: Long,
    val totalCostMicros: Long,
    val hasUnknownCost: Boolean,
    val lastReadAt: Long,
    val autoTitle: Boolean,
    val reasoningVisibility: String,
    val thinkingEnabled: Boolean,
    val thinkingEffort: String,
    val webSearchEnabled: Boolean,
    val agentPythonEnabled: Boolean,
    val agentUbuntuEnabled: Boolean,
    val deepResearchEnabled: Boolean,
    val hybridTokenCountingEnabled: Boolean,
    val archived: Boolean,
    val pinned: Boolean,
    val archivedAt: Long?,
)

@Serializable
private data class PortableMessage(
    val nodeId: String,
    val parentNodeId: String?,
    val branchId: String,
    val role: String,
    val content: String,
    val reasoning: String,
    val toolTraceJson: String,
    val timelineJson: String,
    val status: String,
    val providerId: String?,
    val modelId: String?,
    val requestSnapshotJson: String?,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedInputTokens: Long,
    val costMicros: Long,
    val costKnown: Boolean,
    val streamOffset: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
    val supersededAt: Long?,
)

@Serializable
private data class PortableAttachment(
    val id: String,
    val messageNodeId: String?,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val entryName: String,
    val ocrJson: String?,
    val imageDescription: String?,
    val extractedText: String?,
    val createdAt: Long,
)

data class ArchivePreview(
    val kind: ArchiveKind,
    val title: String,
    val conversationCount: Int,
    val messageCount: Int,
    val attachmentCount: Int,
    val linuxEnvironmentCount: Int,
    val linuxEnvironmentBytes: Long,
    val encrypted: Boolean,
    val createdAt: Long,
    val appVersion: String,
    val options: ArchiveOptions,
)

data class IncomingArchiveState(
    val uri: Uri,
    val preview: ArchivePreview? = null,
    val passwordRequired: Boolean = false,
    val importing: Boolean = false,
    val error: String? = null,
)

data class ArchiveImportResult(
    val conversationIds: List<String>,
    val linuxEnvironmentCount: Int,
)

class ArchivePasswordRequiredException : IllegalArgumentException("This Arbor archive is password protected")

class ArborArchiveManager(
    private val context: Context,
    private val database: ArborDatabase,
    private val linuxEnvironments: LinuxEnvironmentArchiveStore,
) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun writeChatToCache(
        conversationId: String,
        options: ArchiveOptions,
        password: String,
    ): Uri = withContext(Dispatchers.IO) {
        val conversation = requireNotNull(database.conversationDao().get(conversationId)) { "Chat no longer exists" }
        val root = File(context.cacheDir, "shares").apply { mkdirs() }
        root.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > SHARE_CACHE_MAX_AGE_MS }
            ?.forEach(File::delete)
        val file = File(root, "${safeFileName(conversation.title)}-${System.currentTimeMillis()}$ARBOR_CHAT_EXTENSION")
        file.outputStream().buffered().use { output ->
            writeArchive(
                output = output,
                kind = ArchiveKind.CHAT,
                conversationIds = listOf(conversationId),
                options = options,
                password = password,
            )
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    suspend fun writeBackup(
        uri: Uri,
        options: ArchiveOptions,
        password: String,
    ) = withContext(Dispatchers.IO) {
        val conversationIds = database.conversationDao().all().map(ConversationEntity::id)
        val output = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) {
            "The selected backup destination could not be opened"
        }
        output.buffered().use {
            writeArchive(
                output = it,
                kind = ArchiveKind.BACKUP,
                conversationIds = conversationIds,
                options = options,
                password = password,
            )
        }
    }

    suspend fun writeBackupToCache(
        options: ArchiveOptions,
        password: String,
    ): File = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, "backup-exports").apply { mkdirs() }
        root.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > SHARE_CACHE_MAX_AGE_MS }
            ?.forEach(File::delete)
        val file = File(root, "Arbor-backup-${System.currentTimeMillis()}$ARBOR_BACKUP_EXTENSION")
        try {
            file.outputStream().buffered().use { output ->
                writeArchive(
                    output = output,
                    kind = ArchiveKind.BACKUP,
                    conversationIds = database.conversationDao().all().map(ConversationEntity::id),
                    options = options,
                    password = password,
                )
            }
            file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    suspend fun inspect(uri: Uri, password: String = ""): ArchivePreview = withContext(Dispatchers.IO) {
        val decoded = decodePayloadToTemp(uri, password)
        try {
            val manifest = readManifest(decoded.file)
            ArchivePreview(
                kind = manifest.kind,
                title = manifest.title,
                conversationCount = manifest.conversations.size,
                messageCount = manifest.conversations.sumOf { it.messages.size },
                attachmentCount = manifest.conversations.sumOf { it.attachments.size },
                linuxEnvironmentCount = manifest.linuxEnvironments.size,
                linuxEnvironmentBytes = manifest.linuxEnvironments.sumOf { it.sizeBytes },
                encrypted = decoded.header.encrypted,
                createdAt = manifest.createdAt,
                appVersion = manifest.appVersion,
                options = manifest.options,
            )
        } finally {
            decoded.file.delete()
        }
    }

    suspend fun importArchive(uri: Uri, password: String = ""): ArchiveImportResult = withContext(Dispatchers.IO) {
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

    private suspend fun writeArchive(
        output: OutputStream,
        kind: ArchiveKind,
        conversationIds: List<String>,
        options: ArchiveOptions,
        password: String,
    ) {
        val bundles = conversationIds.mapNotNull { id -> snapshotConversation(id, options) }
        val preparedLinux = if (kind == ArchiveKind.BACKUP && options.includeLinuxEnvironments) {
            linuxEnvironments.prepareSnapshots()
        } else emptyList()
        try {
            require(bundles.isNotEmpty() || preparedLinux.isNotEmpty()) {
                if (options.includeLinuxEnvironments) "There are no chats or installed Linux environments to back up"
                else "There are no chats to back up"
            }
            val now = System.currentTimeMillis()
            val manifest = ArchiveManifest(
                kind = kind,
                createdAt = now,
                appVersion = BuildConfig.VERSION_NAME,
                title = when {
                    kind == ArchiveKind.CHAT -> bundles.single().conversation.title
                    bundles.isEmpty() -> "Arbor Linux backup"
                    else -> "Arbor backup"
                },
                options = options,
                conversations = bundles,
                linuxEnvironments = preparedLinux.map(PreparedLinuxEnvironment::metadata),
            )
            val encrypted = password.isNotEmpty()
            val salt = if (encrypted) ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes) else null
            val iv = if (encrypted) ByteArray(GCM_IV_BYTES).also(SecureRandom()::nextBytes) else null
            val header = EnvelopeHeader(
                kind = kind,
                encrypted = encrypted,
                createdAt = now,
                saltBase64 = salt?.let(::encodeBase64),
                ivBase64 = iv?.let(::encodeBase64),
            )
            output.write(MAGIC)
            output.write(json.encodeToString(header).toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
            val payloadOutput: OutputStream = if (encrypted) {
                CipherOutputStream(output, encryptionCipher(password, requireNotNull(salt), requireNotNull(iv), header.iterations))
            } else output
            ZipOutputStream(BufferedOutputStream(payloadOutput)).use { zip ->
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                if (options.includeAttachments) {
                    bundles.forEach { bundle ->
                        val entities = database.attachmentDao().forConversation(bundle.conversation.id).associateBy(AttachmentEntity::id)
                        bundle.attachments.forEach { portable ->
                            val source = entities[portable.id]?.localPath?.let(::File)?.takeIf(File::isFile) ?: return@forEach
                            zip.putNextEntry(ZipEntry(portable.entryName))
                            source.inputStream().buffered().use { input -> copyWithLimit(input, zip, MAX_ATTACHMENT_BYTES) }
                            zip.closeEntry()
                        }
                    }
                }
                linuxEnvironments.writePrepared(zip, preparedLinux)
            }
        } finally {
            preparedLinux.forEach(PreparedLinuxEnvironment::delete)
        }
    }

    private suspend fun snapshotConversation(
        conversationId: String,
        options: ArchiveOptions,
    ): PortableConversationBundle? {
        val conversation = database.conversationDao().get(conversationId) ?: return null
        val messages = database.messageDao().allForConversation(conversationId)
        val attachments = if (options.includeAttachments) {
            database.attachmentDao().forConversation(conversationId).mapNotNull { attachment ->
                val source = File(attachment.localPath)
                if (!source.isFile || source.length() > MAX_ATTACHMENT_BYTES) return@mapNotNull null
                PortableAttachment(
                    id = attachment.id,
                    messageNodeId = attachment.messageNodeId,
                    displayName = attachment.displayName,
                    mimeType = attachment.mimeType,
                    sizeBytes = source.length(),
                    entryName = "attachments/${attachment.id}/${safeFileName(attachment.displayName)}",
                    ocrJson = if (options.includeRequestMetadata) attachment.ocrJson else null,
                    imageDescription = if (options.includeRequestMetadata) attachment.imageDescription else null,
                    extractedText = if (options.includeRequestMetadata) attachment.extractedText else null,
                    createdAt = attachment.createdAt,
                )
            }
        } else emptyList()
        return PortableConversationBundle(
            conversation = PortableConversation(
                id = conversation.id,
                title = conversation.title,
                createdAt = conversation.createdAt,
                updatedAt = conversation.updatedAt,
                activeLeafNodeId = conversation.activeLeafNodeId,
                selectedProviderId = conversation.selectedProviderId,
                selectedModelId = conversation.selectedModelId,
                contextPairs = conversation.contextPairs,
                contextTokenLimit = conversation.contextTokenLimit,
                workingTokenLimit = conversation.workingTokenLimit,
                maxOutputTokens = conversation.maxOutputTokens,
                systemPrompt = if (options.includeSystemPrompt) conversation.systemPrompt else "",
                totalInputTokens = conversation.totalInputTokens,
                totalOutputTokens = conversation.totalOutputTokens,
                totalCostMicros = conversation.totalCostMicros,
                hasUnknownCost = conversation.hasUnknownCost,
                lastReadAt = conversation.lastReadAt,
                autoTitle = conversation.autoTitle,
                reasoningVisibility = conversation.reasoningVisibility.name,
                thinkingEnabled = conversation.thinkingEnabled,
                thinkingEffort = conversation.thinkingEffort.name,
                webSearchEnabled = conversation.webSearchEnabled,
                agentPythonEnabled = conversation.agentPythonEnabled,
                agentUbuntuEnabled = conversation.agentUbuntuEnabled,
                deepResearchEnabled = conversation.deepResearchEnabled,
                hybridTokenCountingEnabled = conversation.hybridTokenCountingEnabled,
                archived = conversation.archived,
                pinned = conversation.pinned,
                archivedAt = conversation.archivedAt,
            ),
            messages = messages.map { message ->
                PortableMessage(
                    nodeId = message.nodeId,
                    parentNodeId = message.parentNodeId,
                    branchId = message.branchId,
                    role = message.role.name,
                    content = message.content,
                    reasoning = if (options.includeReasoning) message.reasoning else "",
                    toolTraceJson = if (options.includeToolData) message.toolTraceJson else "[]",
                    timelineJson = if (options.includeToolData) message.timelineJson else "[]",
                    status = message.status.name,
                    providerId = message.providerId,
                    modelId = message.modelId,
                    requestSnapshotJson = if (options.includeRequestMetadata) message.requestSnapshotJson else null,
                    inputTokens = message.inputTokens,
                    outputTokens = message.outputTokens,
                    cachedInputTokens = message.cachedInputTokens,
                    costMicros = message.costMicros,
                    costKnown = message.costKnown,
                    streamOffset = message.streamOffset,
                    createdAt = message.createdAt,
                    updatedAt = message.updatedAt,
                    error = message.error,
                    supersededAt = message.supersededAt,
                )
            },
            attachments = attachments,
        )
    }

    private suspend fun importConversation(
        zip: ZipFile,
        bundle: PortableConversationBundle,
        preserveArchiveState: Boolean,
    ): String {
        val now = System.currentTimeMillis()
        val conversationId = UUID.randomUUID().toString()
        val nodeIds = bundle.messages.associate { it.nodeId to UUID.randomUUID().toString() }
        val branchIds = bundle.messages.map(PortableMessage::branchId).distinct().associateWith { UUID.randomUUID().toString() }
        val attachmentIds = bundle.attachments.associate { it.id to UUID.randomUUID().toString() }
        val copiedDirectories = mutableListOf<File>()
        val importedAttachments = try {
            bundle.attachments.mapNotNull { portable ->
                val entry = zip.getEntry(portable.entryName) ?: return@mapNotNull null
                require(!entry.isDirectory && entry.size <= MAX_ATTACHMENT_BYTES) { "An attachment in the archive is too large" }
                val id = requireNotNull(attachmentIds[portable.id])
                val safeName = safeFileName(portable.displayName)
                val destination = File(context.filesDir, "attachments/$id/$safeName")
                destination.parentFile?.mkdirs()
                copiedDirectories += requireNotNull(destination.parentFile)
                zip.getInputStream(entry).buffered().use { input ->
                    destination.outputStream().buffered().use { output -> copyWithLimit(input, output, MAX_ATTACHMENT_BYTES) }
                }
                AttachmentEntity(
                    id = id,
                    conversationId = conversationId,
                    messageNodeId = portable.messageNodeId?.let(nodeIds::get),
                    displayName = portable.displayName,
                    mimeType = portable.mimeType,
                    sizeBytes = destination.length(),
                    localPath = destination.absolutePath,
                    thumbnailPath = null,
                    ocrJson = portable.ocrJson,
                    imageDescription = portable.imageDescription,
                    extractedText = portable.extractedText,
                    createdAt = portable.createdAt,
                )
            }
        } catch (error: Throwable) {
            copiedDirectories.forEach(File::deleteRecursively)
            throw error
        }
        try {
            database.withTransaction {
                val source = bundle.conversation
                database.conversationDao().upsert(
                    ConversationEntity(
                        id = conversationId,
                        title = if (preserveArchiveState) source.title else "${source.title} (imported)",
                        createdAt = source.createdAt,
                        updatedAt = now,
                        activeLeafNodeId = source.activeLeafNodeId?.let(nodeIds::get),
                        selectedProviderId = source.selectedProviderId,
                        selectedModelId = source.selectedModelId,
                        contextPairs = source.contextPairs,
                        contextTokenLimit = source.contextTokenLimit,
                        workingTokenLimit = source.workingTokenLimit,
                        maxOutputTokens = source.maxOutputTokens,
                        systemPrompt = source.systemPrompt,
                        systemPromptProfileId = null,
                        totalInputTokens = source.totalInputTokens,
                        totalOutputTokens = source.totalOutputTokens,
                        totalCostMicros = source.totalCostMicros,
                        hasUnknownCost = source.hasUnknownCost,
                        lastReadAt = source.lastReadAt,
                        autoTitle = source.autoTitle,
                        reasoningVisibility = source.reasoningVisibility.enumOr(ReasoningVisibility.SHOW_WHILE_WORKING),
                        thinkingEnabled = source.thinkingEnabled,
                        thinkingEffort = source.thinkingEffort.enumOr(ThinkingEffort.MEDIUM),
                        webSearchEnabled = source.webSearchEnabled,
                        agentPythonEnabled = source.agentPythonEnabled,
                        agentUbuntuEnabled = source.agentUbuntuEnabled,
                        deepResearchEnabled = source.deepResearchEnabled,
                        hybridTokenCountingEnabled = source.hybridTokenCountingEnabled,
                        archived = preserveArchiveState && source.archived,
                        pinned = preserveArchiveState && source.pinned,
                        projectId = null,
                        archivedAt = if (preserveArchiveState) source.archivedAt else null,
                    ),
                )
                bundle.messages.forEach { portable ->
                    val importedStatus = portable.status.enumOr(MessageStatus.COMPLETE).let {
                        if (it == MessageStatus.STREAMING) MessageStatus.INTERRUPTED else it
                    }
                    database.messageDao().insert(
                        MessageEntity(
                            nodeId = requireNotNull(nodeIds[portable.nodeId]),
                            conversationId = conversationId,
                            parentNodeId = portable.parentNodeId?.let(nodeIds::get),
                            branchId = branchIds[portable.branchId] ?: UUID.randomUUID().toString(),
                            role = portable.role.enumOr(MessageRole.SYSTEM),
                            content = portable.content,
                            reasoning = portable.reasoning,
                            toolTraceJson = portable.toolTraceJson,
                            timelineJson = portable.timelineJson,
                            status = importedStatus,
                            providerId = portable.providerId,
                            modelId = portable.modelId,
                            requestSnapshotJson = portable.requestSnapshotJson,
                            inputTokens = portable.inputTokens,
                            outputTokens = portable.outputTokens,
                            cachedInputTokens = portable.cachedInputTokens,
                            costMicros = portable.costMicros,
                            costKnown = portable.costKnown,
                            streamOffset = portable.streamOffset,
                            createdAt = portable.createdAt,
                            updatedAt = portable.updatedAt,
                            error = if (portable.status == MessageStatus.STREAMING.name) "Imported while generation was active" else portable.error,
                            supersededAt = portable.supersededAt,
                        ),
                    )
                }
                importedAttachments.forEach { database.attachmentDao().upsert(it) }
            }
        } catch (error: Throwable) {
            copiedDirectories.forEach(File::deleteRecursively)
            throw error
        }
        return conversationId
    }

    private data class DecodedPayload(val header: EnvelopeHeader, val file: File)

    private fun decodePayloadToTemp(uri: Uri, password: String): DecodedPayload {
        val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "The archive could not be opened" }
        val temp = File.createTempFile("arbor-import-", ".zip", File(context.cacheDir, "previews").apply { mkdirs() })
        try {
            BufferedInputStream(input).use { buffered ->
                val magic = ByteArray(MAGIC.size)
                require(buffered.readFully(magic) && magic.contentEquals(MAGIC)) { "This is not an Arbor archive" }
                val headerLine = readLine(buffered, MAX_HEADER_BYTES)
                val header = json.decodeFromString<EnvelopeHeader>(headerLine)
                require(header.schema == ENVELOPE_SCHEMA) { "Unsupported Arbor archive envelope" }
                if (header.encrypted && password.isEmpty()) throw ArchivePasswordRequiredException()
                val payloadInput: InputStream = if (header.encrypted) {
                    val salt = decodeBase64(requireNotNull(header.saltBase64))
                    val iv = decodeBase64(requireNotNull(header.ivBase64))
                    CipherInputStream(buffered, decryptionCipher(password, salt, iv, header.iterations))
                } else buffered
                FileOutputStream(temp).buffered().use { output -> copyWithLimit(payloadInput, output, MAX_ARCHIVE_BYTES) }
                return DecodedPayload(header, temp)
            }
        } catch (error: ArchivePasswordRequiredException) {
            temp.delete()
            throw error
        } catch (error: Throwable) {
            temp.delete()
            val message = if (error.message?.contains("tag", ignoreCase = true) == true || error.cause?.message?.contains("tag", ignoreCase = true) == true) {
                "Wrong password or damaged archive"
            } else error.message ?: "The Arbor archive is damaged"
            throw IllegalArgumentException(message, error)
        }
    }

    private fun readManifest(file: File): ArchiveManifest = ZipFile(file).use { zip ->
        val entry = requireNotNull(zip.getEntry(MANIFEST_ENTRY)) { "Archive manifest is missing" }
        require(!entry.isDirectory && entry.size in 1L..MAX_MANIFEST_BYTES) { "Archive manifest is invalid" }
        val bytes = zip.getInputStream(entry).use { input -> readBytesWithLimit(input, MAX_MANIFEST_BYTES) }
        json.decodeFromString<ArchiveManifest>(bytes.toString(Charsets.UTF_8)).also {
            require(it.schema == MANIFEST_SCHEMA) { "Unsupported Arbor archive version" }
        }
    }

    private fun encryptionCipher(password: String, salt: ByteArray, iv: ByteArray, iterations: Int): Cipher =
        cipher(Cipher.ENCRYPT_MODE, password, salt, iv, iterations)

    private fun decryptionCipher(password: String, salt: ByteArray, iv: ByteArray, iterations: Int): Cipher =
        cipher(Cipher.DECRYPT_MODE, password, salt, iv, iterations)

    private fun cipher(mode: Int, password: String, salt: ByteArray, iv: ByteArray, iterations: Int): Cipher {
        require(password.isNotEmpty()) { "Password is empty" }
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations.coerceIn(100_000, 1_000_000), 256))
            .encoded
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        }
    }

    private fun readLine(input: InputStream, limit: Int): String {
        val output = ByteArrayOutputStream()
        while (output.size() < limit) {
            val value = input.read()
            require(value >= 0) { "Archive header is incomplete" }
            if (value == '\n'.code) return output.toString(Charsets.UTF_8.name())
            output.write(value)
        }
        error("Archive header is too large")
    }

    private fun InputStream.readFully(destination: ByteArray): Boolean {
        var offset = 0
        while (offset < destination.size) {
            val count = read(destination, offset, destination.size - offset)
            if (count < 0) return false
            offset += count
        }
        return true
    }

    private fun readBytesWithLimit(input: InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        copyWithLimit(input, output, limit)
        return output.toByteArray()
    }

    private fun copyWithLimit(input: InputStream, output: OutputStream, limit: Long) {
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Archive data exceeds the supported size limit" }
            output.write(buffer, 0, count)
        }
    }

    private inline fun <reified T : Enum<T>> String.enumOr(fallback: T): T =
        runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._() -]"), "_")
        .trim()
        .take(140)
        .ifBlank { "Arbor-chat" }

    private fun encodeBase64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP)
    private fun decodeBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val SALT_BYTES = 16
        const val GCM_IV_BYTES = 12
        const val MAX_HEADER_BYTES = 16 * 1024
        const val MAX_MANIFEST_BYTES = 32L * 1024 * 1024
        const val MAX_ATTACHMENT_BYTES = 64L * 1024 * 1024
        const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024 * 1024
        const val SHARE_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
        val MAGIC = "ARBOR-ARCHIVE/1\n".toByteArray(Charsets.US_ASCII)
    }
}