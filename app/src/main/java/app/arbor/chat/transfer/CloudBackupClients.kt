package app.arbor.chat.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class CloudBackupProvider { SCOPED_FOLDER, GOOGLE_DRIVE_APP_DATA, ONEDRIVE_APP_FOLDER, DROPBOX_APP_FOLDER, WEBDAV, S3 }

data class CloudBackupEntry(
    val provider: CloudBackupProvider,
    val id: String,
    val name: String,
    val modifiedAt: Long,
    val sizeBytes: Long,
    val uriString: String? = null,
)

/**
 * A persistent Storage Access Framework tree grant. Android gives Arbor access
 * only to the folder the user explicitly selected, even when the folder lives
 * in Google Drive, OneDrive, Dropbox, Nextcloud, a USB drive, or local storage.
 */
class ScopedCloudFolderStore(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun connectedUri(): Uri? = preferences.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun connect(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        resolver.takePersistableUriPermission(uri, flags)
        val label = queryDisplayName(uri).ifBlank { uri.authority.orEmpty().ifBlank { "Cloud folder" } }
        preferences.edit(commit = true) {
            putString(KEY_TREE_URI, uri.toString())
            putString(KEY_TREE_LABEL, label)
        }
    }

    fun disconnect() {
        connectedUri()?.let { uri ->
            runCatching {
                resolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        preferences.edit(commit = true) { clear() }
    }

    fun connectedLabel(): String? = connectedUri()?.let {
        preferences.getString(KEY_TREE_LABEL, null)?.takeIf(String::isNotBlank) ?: queryDisplayName(it)
    }

    suspend fun saveBackup(source: File, fileName: String): Uri = withContext(Dispatchers.IO) {
        require(source.isFile) { "Backup file no longer exists" }
        val tree = requireNotNull(connectedUri()) { "Choose an Arbor cloud folder first" }
        val treeDocument = DocumentsContract.buildDocumentUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val destination = requireNotNull(
            DocumentsContract.createDocument(resolver, treeDocument, ARBOR_BACKUP_MIME, fileName),
        ) { "The selected cloud provider could not create the backup file" }
        try {
            val output = requireNotNull(resolver.openOutputStream(destination, "w")) {
                "The selected cloud provider could not open the backup file"
            }
            source.inputStream().buffered().use { input ->
                output.buffered().use { out -> input.copyTo(out, COPY_BUFFER_BYTES) }
            }
            destination
        } catch (error: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, destination) }
            throw error
        }
    }

    suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val tree = connectedUri() ?: return@withContext emptyList()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            DocumentsContract.getTreeDocumentId(tree),
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val values = mutableListOf<CloudBackupEntry>()
        resolver.query(children, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val typeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex) ?: continue
                val name = cursor.getString(nameIndex).orEmpty()
                val mimeType = cursor.getString(typeIndex).orEmpty()
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue
                if (!name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true) && mimeType != ARBOR_BACKUP_MIME) continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                values += CloudBackupEntry(
                    provider = CloudBackupProvider.SCOPED_FOLDER,
                    id = id,
                    name = name,
                    modifiedAt = cursor.getLong(modifiedIndex).coerceAtLeast(0L),
                    sizeBytes = cursor.getLong(sizeIndex).coerceAtLeast(0L),
                    uriString = documentUri.toString(),
                )
            }
        }
        values.sortedByDescending(CloudBackupEntry::modifiedAt)
    }

    fun open(entry: CloudBackupEntry): Uri {
        require(entry.provider == CloudBackupProvider.SCOPED_FOLDER)
        return Uri.parse(requireNotNull(entry.uriString) { "Cloud backup URI is missing" })
    }

    suspend fun deleteBackup(entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.SCOPED_FOLDER)
        val uri = Uri.parse(requireNotNull(entry.uriString) { "Cloud backup URI is missing" })
        require(DocumentsContract.deleteDocument(resolver, uri)) {
            "The selected document provider could not delete this backup"
        }
    }

    private fun queryDisplayName(uri: Uri): String = runCatching {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val PREFERENCES = "arbor_scoped_cloud_folder"
        const val KEY_TREE_URI = "tree_uri"
        const val KEY_TREE_LABEL = "tree_label"
        const val COPY_BUFFER_BYTES = 256 * 1024
    }
}

/** Google Drive's hidden appDataFolder client. It never requests My Drive access. */
class GoogleDriveAppDataClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .build()

    suspend fun uploadBackup(accessToken: String, source: File, fileName: String): CloudBackupEntry =
        withContext(Dispatchers.IO) {
            require(accessToken.isNotBlank()) { "Google Drive authorization did not return an access token" }
            require(source.isFile) { "Backup file no longer exists" }
            val sessionUri = initiateResumableUpload(accessToken, source, fileName)
            uploadResumable(accessToken, sessionUri, source)
        }

    suspend fun listBackups(accessToken: String): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Google Drive authorization did not return an access token" }
        val values = mutableListOf<CloudBackupEntry>()
        var pageToken: String? = null
        do {
            val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "trashed = false")
                .addQueryParameter("orderBy", "modifiedTime desc")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(id,name,modifiedTime,size,mimeType)")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            pageToken = client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { driveError(response.code, raw) }
                val root = json.parseToJsonElement(raw).jsonObject
                root["files"]?.jsonArray.orEmpty().forEach { element ->
                    val value = element.jsonObject
                    val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val mimeType = value["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true) || mimeType == ARBOR_BACKUP_MIME) {
                        values += parseEntry(value)
                    }
                }
                root["nextPageToken"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            }
        } while (pageToken != null)
        values.sortedByDescending(CloudBackupEntry::modifiedAt)
    }

    suspend fun deleteBackup(accessToken: String, entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.GOOGLE_DRIVE_APP_DATA)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/${entry.id}")
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.code == 204 || response.isSuccessful) { driveError(response.code, raw) }
        }
    }

    suspend fun downloadBackup(accessToken: String, entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.GOOGLE_DRIVE_APP_DATA)
        val url = "https://www.googleapis.com/drive/v3/files/${entry.id}".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        val root = File(context.cacheDir, "drive-app-data").apply { mkdirs() }
        root.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > DOWNLOAD_CACHE_MAX_AGE_MS }
            ?.forEach(File::delete)
        val destination = File(root, safeFileName(entry.name))
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                error(driveError(response.code, raw))
            }
            val body = requireNotNull(response.body) { "Google Drive returned an empty backup" }
            destination.outputStream().buffered().use { output ->
                body.byteStream().use { it.copyTo(output, COPY_BUFFER_BYTES) }
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
    }

    private fun initiateResumableUpload(accessToken: String, source: File, fileName: String): String {
        val metadata = buildJsonObject {
            put("name", fileName)
            put("mimeType", ARBOR_BACKUP_MIME)
            put("parents", buildJsonArray { add(JsonPrimitive("appDataFolder")) })
            put("appProperties", buildJsonObject {
                put("format", "arborbackup")
                put("schema", "1")
            })
        }.toString()
        val url = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "resumable")
            .addQueryParameter("fields", "id,name,modifiedTime,size")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("X-Upload-Content-Type", ARBOR_BACKUP_MIME)
            .header("X-Upload-Content-Length", source.length().toString())
            .post(metadata.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { driveError(response.code, raw) }
            return requireNotNull(response.header("Location")) {
                "Google Drive did not return a resumable upload session"
            }
        }
    }

    private suspend fun uploadResumable(
        accessToken: String,
        sessionUri: String,
        source: File,
    ): CloudBackupEntry {
        val total = source.length()
        var offset = 0L
        var failures = 0
        while (offset < total) {
            val chunkLength = min(UPLOAD_CHUNK_BYTES.toLong(), total - offset)
            val end = offset + chunkLength - 1
            val request = Request.Builder()
                .url(sessionUri)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Range", "bytes $offset-$end/$total")
                .put(FileSliceRequestBody(source, offset, chunkLength, BACKUP_MEDIA_TYPE))
                .build()
            val state = try {
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    when {
                        response.code == 200 || response.code == 201 -> UploadState.Complete(
                            parseEntry(json.parseToJsonElement(raw).jsonObject),
                        )
                        response.code == RESUME_INCOMPLETE -> UploadState.Incomplete(nextOffset(response.header("Range")))
                        response.code in 500..599 -> null
                        else -> throw IllegalArgumentException(driveError(response.code, raw))
                    }
                }
            } catch (error: IOException) {
                null
            }
            if (state == null) {
                failures += 1
                if (failures > MAX_UPLOAD_RETRIES) error("Google Drive upload was interrupted too many times")
                delay(retryDelayMillis(failures))
                when (val queried = queryUploadState(accessToken, sessionUri, total)) {
                    is UploadState.Complete -> return queried.entry
                    is UploadState.Incomplete -> offset = queried.nextOffset
                }
                continue
            }
            when (state) {
                is UploadState.Complete -> return state.entry
                is UploadState.Incomplete -> {
                    if (state.nextOffset > offset) {
                        offset = state.nextOffset
                        failures = 0
                    } else {
                        failures += 1
                        if (failures > MAX_UPLOAD_RETRIES) error("Google Drive did not accept the upload chunk")
                        delay(retryDelayMillis(failures))
                    }
                }
            }
        }
        return when (val state = queryUploadState(accessToken, sessionUri, total)) {
            is UploadState.Complete -> state.entry
            is UploadState.Incomplete -> error("Google Drive upload ended before all bytes were stored")
        }
    }

    private fun queryUploadState(accessToken: String, sessionUri: String, total: Long): UploadState {
        val request = Request.Builder()
            .url(sessionUri)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Range", "bytes */$total")
            .put(ByteArray(0).toRequestBody(null))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            return when {
                response.code == 200 || response.code == 201 -> UploadState.Complete(
                    parseEntry(json.parseToJsonElement(raw).jsonObject),
                )
                response.code == RESUME_INCOMPLETE -> UploadState.Incomplete(nextOffset(response.header("Range")))
                response.code == 404 -> error("Google Drive upload session expired; start the backup again")
                else -> throw IllegalArgumentException(driveError(response.code, raw))
            }
        }
    }

    private fun nextOffset(rangeHeader: String?): Long {
        val lastByte = rangeHeader
            ?.let { RECEIVED_RANGE.matchEntire(it.trim()) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        return if (lastByte == null) 0L else lastByte + 1L
    }

    private fun retryDelayMillis(attempt: Int): Long =
        (500L * (1L shl (attempt - 1).coerceIn(0, 5))).coerceAtMost(15_000L)

    private fun parseEntry(value: JsonObject): CloudBackupEntry {
        val id = value["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(id.isNotBlank()) { "Google Drive returned a backup without an id" }
        val modified = value["modifiedTime"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: 0L
        return CloudBackupEntry(
            provider = CloudBackupProvider.GOOGLE_DRIVE_APP_DATA,
            id = id,
            name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank {
                "Arbor-backup$ARBOR_BACKUP_EXTENSION"
            },
            modifiedAt = modified,
            sizeBytes = value["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }

    private fun driveError(code: Int, raw: String): String {
        val detail = runCatching {
            json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty()
        return when (code) {
            401 -> "Google Drive authorization expired. Authorize Arbor again."
            403 -> "Google Drive rejected app-folder access. Confirm the Drive API and drive.appdata scope are enabled for Arbor."
            404 -> "Google Drive could not find this backup or upload session."
            507 -> "Google Drive does not have enough storage for this backup."
            else -> detail.ifBlank { "Google Drive backup failed with HTTP $code" }
        }
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._() -]"), "_")
        .trim()
        .take(160)
        .ifBlank { "Arbor-backup$ARBOR_BACKUP_EXTENSION" }

    private sealed interface UploadState {
        data class Incomplete(val nextOffset: Long) : UploadState
        data class Complete(val entry: CloudBackupEntry) : UploadState
    }

    private class FileSliceRequestBody(
        private val file: File,
        private val offset: Long,
        private val length: Long,
        private val mediaType: MediaType,
    ) : RequestBody() {
        override fun contentType(): MediaType = mediaType
        override fun contentLength(): Long = length

        override fun writeTo(sink: BufferedSink) {
            RandomAccessFile(file, "r").use { source ->
                source.seek(offset)
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var remaining = length
                while (remaining > 0L) {
                    val count = source.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                    if (count < 0) throw IOException("Backup file ended during upload")
                    sink.write(buffer, 0, count)
                    remaining -= count
                }
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val BACKUP_MEDIA_TYPE = ARBOR_BACKUP_MIME.toMediaType()
        val RECEIVED_RANGE = Regex("bytes=0-(\\d+)")
        const val COPY_BUFFER_BYTES = 256 * 1024
        const val UPLOAD_CHUNK_BYTES = 8 * 1024 * 1024
        const val RESUME_INCOMPLETE = 308
        const val MAX_UPLOAD_RETRIES = 6
        const val DOWNLOAD_CACHE_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}
