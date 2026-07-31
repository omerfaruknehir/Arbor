package app.arbor.chat.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class CloudBackupProvider { SCOPED_FOLDER, GOOGLE_DRIVE_APP_DATA }

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
                output.buffered().use { out -> input.copyTo(out, 256 * 1024) }
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
    }
}

/** Google Drive's hidden appDataFolder client. It never requests My Drive access. */
class GoogleDriveAppDataClient(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(30, TimeUnit.MINUTES)
        .build()

    suspend fun uploadBackup(accessToken: String, source: File, fileName: String): CloudBackupEntry =
        withContext(Dispatchers.IO) {
            require(accessToken.isNotBlank()) { "Google Drive authorization did not return an access token" }
            require(source.isFile) { "Backup file no longer exists" }
            val metadata = buildJsonObject {
                put("name", fileName)
                put("mimeType", ARBOR_BACKUP_MIME)
                put("parents", kotlinx.serialization.json.buildJsonArray { add("appDataFolder") })
                put("appProperties", buildJsonObject {
                    put("format", "arborbackup")
                    put("schema", "1")
                })
            }.toString()
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(source.asRequestBody(ARBOR_BACKUP_MIME.toMediaType()))
                .build()
            val url = "https://www.googleapis.com/upload/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("uploadType", "multipart")
                .addQueryParameter("fields", "id,name,modifiedTime,size")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { driveError(response.code, raw) }
                parseEntry(json.parseToJsonElement(raw).jsonObject)
            }
        }

    suspend fun listBackups(accessToken: String): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "Google Drive authorization did not return an access token" }
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "trashed = false")
            .addQueryParameter("orderBy", "modifiedTime desc")
            .addQueryParameter("pageSize", "50")
            .addQueryParameter("fields", "files(id,name,modifiedTime,size,mimeType)")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { driveError(response.code, raw) }
            json.parseToJsonElement(raw).jsonObject["files"]?.jsonArray.orEmpty()
                .mapNotNull { element ->
                    val value = element.jsonObject
                    val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val mimeType = value["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (!name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true) && mimeType != ARBOR_BACKUP_MIME) null
                    else parseEntry(value)
                }
        }
    }

    suspend fun downloadBackup(accessToken: String, entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.GOOGLE_DRIVE_APP_DATA)
        val url = "https://www.googleapis.com/drive/v3/files".toHttpUrl().newBuilder()
            .addPathSegment(entry.id)
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        val root = File(context.cacheDir, "drive-app-data").apply { mkdirs() }
        val destination = File(root, safeFileName(entry.name))
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body?.string().orEmpty()
                error(driveError(response.code, raw))
            }
            val body = requireNotNull(response.body) { "Google Drive returned an empty backup" }
            destination.outputStream().buffered().use { output -> body.byteStream().use { it.copyTo(output, 256 * 1024) } }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
    }

    private fun parseEntry(value: kotlinx.serialization.json.JsonObject): CloudBackupEntry {
        val id = value["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(id.isNotBlank()) { "Google Drive returned a backup without an id" }
        val modified = value["modifiedTime"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: 0L
        return CloudBackupEntry(
            provider = CloudBackupProvider.GOOGLE_DRIVE_APP_DATA,
            id = id,
            name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Arbor-backup$ARBOR_BACKUP_EXTENSION" },
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
            507 -> "Google Drive does not have enough storage for this backup."
            else -> detail.ifBlank { "Google Drive backup failed with HTTP $code" }
        }
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._() -]"), "_")
        .trim()
        .take(160)
        .ifBlank { "Arbor-backup$ARBOR_BACKUP_EXTENSION" }
}
