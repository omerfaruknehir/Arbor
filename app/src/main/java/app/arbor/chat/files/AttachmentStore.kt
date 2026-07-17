package app.arbor.chat.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.os.StatFs
import app.arbor.chat.data.AttachmentDao
import app.arbor.chat.data.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AttachmentStore(
    private val context: Context,
    private val attachmentDao: AttachmentDao,
) {
    companion object {
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
        const val MAX_CHAT_ATTACHMENT_BYTES = 512L * 1024 * 1024
        const val MAX_STAGED_ATTACHMENTS = 12
        const val MAX_APP_ATTACHMENT_BYTES = 2L * 1024 * 1024 * 1024
        private const val MIN_FREE_BYTES = 64L * 1024 * 1024
    }

    suspend fun deleteConversationFiles(conversationId: String) = withContext(Dispatchers.IO) {
        attachmentDao.forConversation(conversationId).forEach { attachment ->
            runCatching { File(attachment.localPath).parentFile?.deleteRecursively() }
            attachment.thumbnailPath?.let { runCatching { File(it).delete() } }
        }
    }

    suspend fun import(conversationId: String, uri: Uri): AttachmentEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var displayName = "attachment"
        var declaredSize = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                displayName = cursor.getString(0) ?: displayName
                declaredSize = cursor.getLong(1)
            }
        }
        require(declaredSize < 0 || declaredSize <= MAX_FILE_BYTES) { "Files are limited to 64 MB" }
        val existing = attachmentDao.forConversation(conversationId)
        require(existing.count { it.messageNodeId == null } < MAX_STAGED_ATTACHMENTS) { "Attach at most $MAX_STAGED_ATTACHMENTS files at a time" }
        require(existing.sumOf { it.sizeBytes.coerceAtLeast(0) } + declaredSize.coerceAtLeast(0) <= MAX_CHAT_ATTACHMENT_BYTES) {
            "This chat has reached its 512 MB attachment limit"
        }
        val attachmentsRoot = File(context.filesDir, "attachments")
        val appBytes = attachmentsRoot.walkTopDown().filter(File::isFile).sumOf(File::length)
        require(appBytes + declaredSize.coerceAtLeast(0) <= MAX_APP_ATTACHMENT_BYTES) { "Arbor's 2 GB attachment storage limit has been reached" }
        val available = StatFs(context.filesDir.absolutePath).availableBytes
        require(declaredSize < 0 || available > declaredSize * 2 + MIN_FREE_BYTES) { "Not enough free storage to attach this file and make its workspace copy" }
        val id = UUID.randomUUID().toString()
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._() -]"), "_").take(160).ifBlank { "attachment" }
        val directory = File(context.filesDir, "attachments/$id").also { it.mkdirs() }
        val file = File(directory, safeName)
        val workspaceCopy = File(context.filesDir, "workspaces/$conversationId/incoming/$id-$safeName")
        try {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open $displayName" }
                file.outputStream().use { output -> copyWithLimit(input, output, MAX_FILE_BYTES) }
            }
            workspaceCopy.parentFile?.mkdirs()
            file.copyTo(workspaceCopy, overwrite = true)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            workspaceCopy.delete()
            throw error
        }
        val mime = resolver.getType(uri) ?: guessMime(safeName)
        val extractedText = if (isText(mime, safeName) && file.length() <= 4L * 1024 * 1024) {
            runCatching { file.readText().take(1_000_000) }.getOrNull()
        } else null
        AttachmentEntity(
            id = id,
            conversationId = conversationId,
            messageNodeId = null,
            displayName = displayName,
            mimeType = mime,
            sizeBytes = if (declaredSize >= 0) declaredSize else file.length(),
            localPath = file.absolutePath,
            extractedText = extractedText,
            createdAt = System.currentTimeMillis(),
        ).also { attachmentDao.upsert(it) }
    }

    suspend fun removeStaged(id: String) = withContext(Dispatchers.IO) {
        val attachment = attachmentDao.get(id) ?: return@withContext
        if (attachment.messageNodeId != null || attachmentDao.deleteStaged(id) == 0) return@withContext
        runCatching { File(attachment.localPath).parentFile?.deleteRecursively() }
        File(context.filesDir, "workspaces/${attachment.conversationId}/incoming").listFiles()
            ?.filter { it.name.startsWith("$id-") }
            ?.forEach { runCatching { it.delete() } }
    }

    suspend fun importWorkspaceOutput(conversationId: String, messageNodeId: String, relativePath: String): AttachmentEntity? = withContext(Dispatchers.IO) {
        val workspace = File(context.filesDir, "workspaces/$conversationId").canonicalFile
        val source = File(workspace, relativePath).canonicalFile
        if (!source.isFile || !source.path.startsWith(workspace.path + File.separator) || source.length() > MAX_FILE_BYTES) return@withContext null
        val existing = attachmentDao.forConversation(conversationId)
        if (existing.sumOf { it.sizeBytes.coerceAtLeast(0) } + source.length() > MAX_CHAT_ATTACHMENT_BYTES) return@withContext null
        val id = UUID.randomUUID().toString()
        val safeName = source.name.replace(Regex("[^A-Za-z0-9._() -]"), "_").take(160).ifBlank { "output" }
        val destination = File(context.filesDir, "attachments/$id/$safeName")
        destination.parentFile?.mkdirs()
        source.copyTo(destination, overwrite = true)
        val mime = guessMime(safeName)
        val extractedText = if (isText(mime, safeName) && destination.length() <= 4L * 1024 * 1024) {
            runCatching { destination.readText().take(1_000_000) }.getOrNull()
        } else null
        AttachmentEntity(
            id = id,
            conversationId = conversationId,
            messageNodeId = messageNodeId,
            displayName = source.name,
            mimeType = mime,
            sizeBytes = destination.length(),
            localPath = destination.absolutePath,
            extractedText = extractedText,
            createdAt = System.currentTimeMillis(),
        ).also { attachmentDao.upsert(it) }
    }

    private fun isText(mime: String, name: String): Boolean = mime.startsWith("text/") ||
        name.substringAfterLast('.', "").lowercase() in setOf("md", "json", "xml", "yaml", "yml", "toml", "csv", "tsv", "kt", "java", "py", "c", "cpp", "h", "rs", "js", "ts", "css", "sql", "sh")

    private fun guessMime(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
            "md", "markdown" -> "text/markdown"
            "json" -> "application/json"
            "jsonl" -> "application/x-ndjson"
            "csv" -> "text/csv"
            "tsv" -> "text/tab-separated-values"
            "yaml", "yml" -> "application/yaml"
            "toml" -> "application/toml"
            "svg" -> "image/svg+xml"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heif"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            else -> "application/octet-stream"
        }
    }

    private fun copyWithLimit(input: java.io.InputStream, output: java.io.OutputStream, limit: Long) {
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "Files are limited to 64 MB" }
            output.write(buffer, 0, count)
        }
    }

}
