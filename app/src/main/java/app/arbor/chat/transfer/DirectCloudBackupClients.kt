package app.arbor.chat.transfer

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

internal interface DirectCloudBackupClient {
    suspend fun testConnection(): String
    suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry
    suspend fun listBackups(): List<CloudBackupEntry>
    suspend fun downloadBackup(entry: CloudBackupEntry): Uri
    suspend fun deleteBackup(entry: CloudBackupEntry)
}

internal class OneDriveAppFolderClient(
    private val context: Context,
    private val accessToken: String,
    private val client: OkHttpClient = defaultCloudHttpClient(),
) : DirectCloudBackupClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = authorized(Request.Builder().url("$GRAPH/me/drive/special/approot").get()).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { graphError(response.code, raw) }
            val root = json.parseToJsonElement(raw).jsonObject
            root["name"]?.jsonPrimitive?.contentOrNull ?: "OneDrive app folder"
        }
    }

    override suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry = withContext(Dispatchers.IO) {
        require(source.isFile) { "Backup file no longer exists" }
        if (source.length() <= SIMPLE_UPLOAD_LIMIT) simpleUpload(source, fileName)
        else resumableUpload(source, fileName)
    }

    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val initial = "$GRAPH/me/drive/special/approot/children".toHttpUrl().newBuilder()
            .addQueryParameter("\$select", "id,name,size,lastModifiedDateTime,file")
            .addQueryParameter("\$orderby", "lastModifiedDateTime desc")
            .addQueryParameter("\$top", "200")
            .build()
            .toString()
        val values = mutableListOf<CloudBackupEntry>()
        var nextUrl: String? = initial
        while (nextUrl != null) {
            val pageUrl = requireNotNull(nextUrl)
            require(pageUrl.startsWith(GRAPH)) { "OneDrive returned an invalid pagination URL" }
            val request = authorized(Request.Builder().url(pageUrl).get()).build()
            nextUrl = client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { graphError(response.code, raw) }
                val root = json.parseToJsonElement(raw).jsonObject
                root["value"]?.jsonArray.orEmpty().forEach { element ->
                    val value = element.jsonObject
                    val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true)) {
                        values += parseOneDriveEntry(value)
                    }
                }
                root["@odata.nextLink"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
            }
        }
        values.sortedByDescending(CloudBackupEntry::modifiedAt)
    }

    override suspend fun downloadBackup(entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.ONEDRIVE_APP_FOLDER)
        val request = authorized(Request.Builder().url("$GRAPH/me/drive/items/${Uri.encode(entry.id)}/content").get()).build()
        downloadToCache(context, client, request, "onedrive", entry.name) { code, raw -> graphError(code, raw) }
    }

    override suspend fun deleteBackup(entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.ONEDRIVE_APP_FOLDER)
        val request = authorized(Request.Builder().url("$GRAPH/me/drive/items/${Uri.encode(entry.id)}").delete()).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.code == 204 || response.isSuccessful) { graphError(response.code, raw) }
        }
    }

    private fun simpleUpload(source: File, fileName: String): CloudBackupEntry {
        val url = "$GRAPH/me/drive/special/approot:/${Uri.encode(fileName)}:/content"
        val request = authorized(
            Request.Builder().url(url).put(FileRequestBody(source, BACKUP_MEDIA_TYPE)),
        ).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { graphError(response.code, raw) }
            return parseOneDriveEntry(json.parseToJsonElement(raw).jsonObject)
        }
    }

    private fun resumableUpload(source: File, fileName: String): CloudBackupEntry {
        val sessionUrl = "$GRAPH/me/drive/special/approot:/${Uri.encode(fileName)}:/createUploadSession"
        val metadata = buildJsonObject {
            put("item", buildJsonObject {
                put("@microsoft.graph.conflictBehavior", "replace")
                put("name", fileName)
            })
        }.toString()
        val create = authorized(
            Request.Builder().url(sessionUrl).post(metadata.toRequestBody(JSON_MEDIA_TYPE)),
        ).build()
        val uploadUrl = client.newCall(create).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { graphError(response.code, raw) }
            json.parseToJsonElement(raw).jsonObject["uploadUrl"]?.jsonPrimitive?.contentOrNull
                ?: error("OneDrive did not return an upload session")
        }
        val total = source.length()
        var offset = 0L
        while (offset < total) {
            val length = min(ONEDRIVE_CHUNK_BYTES.toLong(), total - offset)
            val end = offset + length - 1L
            val request = Request.Builder()
                .url(uploadUrl)
                .header("Content-Length", length.toString())
                .header("Content-Range", "bytes $offset-$end/$total")
                .put(FileRangeRequestBody(source, offset, length, BACKUP_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                when {
                    response.code == 200 || response.code == 201 ->
                        return parseOneDriveEntry(json.parseToJsonElement(raw).jsonObject)
                    response.code == 202 -> offset += length
                    else -> error(graphError(response.code, raw))
                }
            }
        }
        error("OneDrive upload ended without a completed file")
    }

    private fun authorized(builder: Request.Builder): Request.Builder =
        builder.header("Authorization", "Bearer $accessToken").header("Accept", "application/json")

    private fun parseOneDriveEntry(value: JsonObject): CloudBackupEntry {
        val id = value["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(id.isNotBlank()) { "OneDrive returned a backup without an id" }
        return CloudBackupEntry(
            provider = CloudBackupProvider.ONEDRIVE_APP_FOLDER,
            id = id,
            name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank {
                "Arbor-backup$ARBOR_BACKUP_EXTENSION"
            },
            modifiedAt = value["lastModifiedDateTime"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
            sizeBytes = value["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }

    private fun graphError(code: Int, raw: String): String {
        val detail = runCatching {
            json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject
                ?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> "OneDrive authorization expired. Connect the account again."
            403 -> "OneDrive denied access to Arbor's app folder. Confirm Files.ReadWrite.AppFolder permission."
            404 -> "OneDrive could not find this app-folder backup."
            507 -> "OneDrive does not have enough storage for this backup."
            else -> detail ?: "OneDrive request failed with HTTP $code"
        }
    }

    companion object {
        private const val GRAPH = "https://graph.microsoft.com/v1.0"
        private const val SIMPLE_UPLOAD_LIMIT = 4L * 1024L * 1024L
        private const val ONEDRIVE_CHUNK_BYTES = 10 * 1024 * 1024
    }
}

internal class DropboxAppFolderClient(
    private val context: Context,
    private val accessToken: String,
    private val client: OkHttpClient = defaultCloudHttpClient(),
) : DirectCloudBackupClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = apiRequest("https://api.dropboxapi.com/2/users/get_current_account", "{}")
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { dropboxError(response.code, raw) }
            val root = json.parseToJsonElement(raw).jsonObject
            root["email"]?.jsonPrimitive?.contentOrNull
                ?: root["name"]?.jsonObject?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?: "Dropbox app folder"
        }
    }

    override suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry = withContext(Dispatchers.IO) {
        require(source.isFile) { "Backup file no longer exists" }
        if (source.length() <= DROPBOX_SIMPLE_LIMIT) simpleUpload(source, fileName)
        else uploadSession(source, fileName)
    }

    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val values = mutableListOf<CloudBackupEntry>()
        var cursor: String? = null
        do {
            val request = if (cursor == null) {
                apiRequest(
                    "https://api.dropboxapi.com/2/files/list_folder",
                    buildJsonObject {
                        put("path", "")
                        put("recursive", false)
                        put("include_deleted", false)
                        put("limit", 100)
                    }.toString(),
                )
            } else {
                apiRequest(
                    "https://api.dropboxapi.com/2/files/list_folder/continue",
                    buildJsonObject { put("cursor", cursor) }.toString(),
                )
            }
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { dropboxError(response.code, raw) }
                val root = json.parseToJsonElement(raw).jsonObject
                root["entries"]?.jsonArray.orEmpty().forEach { element ->
                    val value = element.jsonObject
                    if (value[".tag"]?.jsonPrimitive?.contentOrNull != "file") return@forEach
                    val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true)) {
                        values += parseDropboxEntry(value)
                    }
                }
                cursor = if (root["has_more"]?.jsonPrimitive?.contentOrNull == "true") {
                    root["cursor"]?.jsonPrimitive?.contentOrNull
                } else null
            }
        } while (cursor != null)
        values.sortedByDescending(CloudBackupEntry::modifiedAt)
    }

    override suspend fun downloadBackup(entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.DROPBOX_APP_FOLDER)
        val arg = buildJsonObject { put("path", entry.id) }.toString()
        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", arg)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        downloadToCache(context, client, request, "dropbox", entry.name) { code, raw -> dropboxError(code, raw) }
    }

    override suspend fun deleteBackup(entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.DROPBOX_APP_FOLDER)
        val request = apiRequest(
            "https://api.dropboxapi.com/2/files/delete_v2",
            buildJsonObject { put("path", entry.id) }.toString(),
        )
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { dropboxError(response.code, raw) }
        }
    }

    private fun simpleUpload(source: File, fileName: String): CloudBackupEntry {
        val arg = buildJsonObject {
            put("path", "/$fileName")
            put("mode", "overwrite")
            put("autorename", false)
            put("mute", true)
        }.toString()
        val request = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", arg)
            .header("Content-Type", "application/octet-stream")
            .post(FileRequestBody(source, OCTET_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { dropboxError(response.code, raw) }
            return parseDropboxEntry(json.parseToJsonElement(raw).jsonObject)
        }
    }

    private fun uploadSession(source: File, fileName: String): CloudBackupEntry {
        val start = Request.Builder()
            .url("https://content.dropboxapi.com/2/files/upload_session/start")
            .header("Authorization", "Bearer $accessToken")
            .header("Dropbox-API-Arg", "{\"close\":false}")
            .header("Content-Type", "application/octet-stream")
            .post(ByteArray(0).toRequestBody(OCTET_MEDIA_TYPE))
            .build()
        val sessionId = client.newCall(start).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { dropboxError(response.code, raw) }
            json.parseToJsonElement(raw).jsonObject["session_id"]?.jsonPrimitive?.contentOrNull
                ?: error("Dropbox did not return an upload session")
        }
        val total = source.length()
        var offset = 0L
        while (offset < total) {
            val length = min(DROPBOX_CHUNK_BYTES.toLong(), total - offset)
            val final = offset + length >= total
            val cursor = buildJsonObject {
                put("session_id", sessionId)
                put("offset", offset)
            }
            val url: String
            val arg: String
            if (final) {
                url = "https://content.dropboxapi.com/2/files/upload_session/finish"
                arg = buildJsonObject {
                    put("cursor", cursor)
                    put("commit", buildJsonObject {
                        put("path", "/$fileName")
                        put("mode", "overwrite")
                        put("autorename", false)
                        put("mute", true)
                    })
                }.toString()
            } else {
                url = "https://content.dropboxapi.com/2/files/upload_session/append_v2"
                arg = buildJsonObject {
                    put("cursor", cursor)
                    put("close", false)
                }.toString()
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .header("Dropbox-API-Arg", arg)
                .header("Content-Type", "application/octet-stream")
                .post(FileRangeRequestBody(source, offset, length, OCTET_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { dropboxError(response.code, raw) }
                if (final) return parseDropboxEntry(json.parseToJsonElement(raw).jsonObject)
            }
            offset += length
        }
        error("Dropbox upload ended without a completed file")
    }

    private fun apiRequest(url: String, body: String): Request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $accessToken")
        .header("Content-Type", "application/json")
        .post(body.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private fun parseDropboxEntry(value: JsonObject): CloudBackupEntry {
        val path = value["path_lower"]?.jsonPrimitive?.contentOrNull
            ?: value["id"]?.jsonPrimitive?.contentOrNull
            ?: error("Dropbox returned a backup without a path")
        return CloudBackupEntry(
            provider = CloudBackupProvider.DROPBOX_APP_FOLDER,
            id = path,
            name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank {
                "Arbor-backup$ARBOR_BACKUP_EXTENSION"
            },
            modifiedAt = value["server_modified"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L,
            sizeBytes = value["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        )
    }

    private fun dropboxError(code: Int, raw: String): String {
        val detail = runCatching {
            json.parseToJsonElement(raw).jsonObject["error_summary"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> "Dropbox authorization expired. Connect the account again."
            403 -> "Dropbox denied Arbor app-folder access. Confirm the app is scoped to App folder and has file permissions."
            409 -> detail ?: "Dropbox rejected this file operation."
            507 -> "Dropbox does not have enough storage for this backup."
            else -> detail ?: "Dropbox request failed with HTTP $code"
        }
    }

    companion object {
        private const val DROPBOX_SIMPLE_LIMIT = 140L * 1024L * 1024L
        private const val DROPBOX_CHUNK_BYTES = 8 * 1024 * 1024
    }
}

internal class WebDavBackupClient(
    private val context: Context,
    private val config: WebDavCloudConfig,
    private val client: OkHttpClient = defaultCloudHttpClient(),
) : DirectCloudBackupClient {
    private val authorization = Credentials.basic(config.username, config.password)

    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val request = webDavRequest(config.folderUrl)
            .method("PROPFIND", WEBDAV_PROPFIND.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "0")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.code == 207 || response.isSuccessful) { webDavError(response.code, raw) }
            config.label
        }
    }

    override suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry = withContext(Dispatchers.IO) {
        require(source.isFile) { "Backup file no longer exists" }
        val target = resolveWebDav(config.folderUrl, Uri.encode(fileName))
        val request = webDavRequest(target)
            .header("X-NC-WebDAV-AutoMkcol", "1")
            .put(FileRequestBody(source, BACKUP_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.code in setOf(200, 201, 204)) { webDavError(response.code, raw) }
        }
        CloudBackupEntry(
            provider = CloudBackupProvider.WEBDAV,
            id = target,
            name = fileName,
            modifiedAt = System.currentTimeMillis(),
            sizeBytes = source.length(),
        )
    }

    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val request = webDavRequest(config.folderUrl)
            .method("PROPFIND", WEBDAV_PROPFIND.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", "1")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.code == 207 || response.isSuccessful) { webDavError(response.code, raw) }
            parseWebDavEntries(raw, config.folderUrl)
        }
    }

    override suspend fun downloadBackup(entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.WEBDAV)
        val request = webDavRequest(resolveWebDav(config.folderUrl, entry.id)).get().build()
        downloadToCache(context, client, request, "webdav", entry.name) { code, raw -> webDavError(code, raw) }
    }

    override suspend fun deleteBackup(entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.WEBDAV)
        val request = webDavRequest(resolveWebDav(config.folderUrl, entry.id)).delete().build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.code in setOf(200, 202, 204)) { webDavError(response.code, raw) }
        }
    }

    private fun webDavRequest(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("Authorization", authorization)
        .header("Accept", "application/xml, text/xml, */*")

    private fun webDavError(code: Int, raw: String): String = when (code) {
        401 -> "WebDAV credentials were rejected. Use a Nextcloud app password when two-factor authentication is enabled."
        403 -> "WebDAV denied access to the selected folder."
        404 -> "The WebDAV folder was not found. Create it first or enter its exact folder URL."
        405 -> "This server does not allow the requested WebDAV operation."
        507 -> "The WebDAV server does not have enough storage for this backup."
        else -> raw.take(300).ifBlank { "WebDAV request failed with HTTP $code" }
    }
}

internal class S3BackupClient(
    private val context: Context,
    private val config: S3CloudConfig,
    private val client: OkHttpClient = defaultCloudHttpClient(),
) : DirectCloudBackupClient {
    override suspend fun testConnection(): String = withContext(Dispatchers.IO) {
        val url = buildS3Url(query = mapOf("list-type" to "2", "max-keys" to "1", "prefix" to "${config.prefix}/"))
        executeSigned("GET", url, null, EMPTY_SHA256).use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { s3Error(response.code, raw) }
            config.label
        }
    }

    override suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry = withContext(Dispatchers.IO) {
        require(source.isFile) { "Backup file no longer exists" }
        require(source.length() <= S3_MAX_OBJECT_BYTES) { "S3 objects may not exceed 5 TiB" }
        val key = objectKey(fileName)
        if (source.length() < S3_MULTIPART_THRESHOLD) {
            val hash = sha256Hex(source)
            executeSigned("PUT", buildS3Url(key), FileRequestBody(source, BACKUP_MEDIA_TYPE), hash).use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { s3Error(response.code, raw) }
            }
        } else {
            multipartUpload(source, key)
        }
        CloudBackupEntry(
            provider = CloudBackupProvider.S3,
            id = key,
            name = fileName,
            modifiedAt = System.currentTimeMillis(),
            sizeBytes = source.length(),
        )
    }

    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val values = mutableListOf<CloudBackupEntry>()
        var continuationToken: String? = null
        do {
            val query = linkedMapOf(
                "list-type" to "2",
                "prefix" to "${config.prefix}/",
                "max-keys" to "1000",
            ).apply {
                continuationToken?.let { put("continuation-token", it) }
            }
            val url = buildS3Url(query = query)
            continuationToken = executeSigned("GET", url, null, EMPTY_SHA256).use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful) { s3Error(response.code, raw) }
                val page = parseS3Page(raw)
                values += page.entries
                page.nextContinuationToken
            }
        } while (continuationToken != null)
        values.sortedByDescending(CloudBackupEntry::modifiedAt)
    }

    override suspend fun downloadBackup(entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.S3)
        val request = signedRequest("GET", buildS3Url(entry.id), null, EMPTY_SHA256)
        downloadToCache(context, client, request, "s3", entry.name) { code, raw -> s3Error(code, raw) }
    }

    override suspend fun deleteBackup(entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
        require(entry.provider == CloudBackupProvider.S3)
        executeSigned("DELETE", buildS3Url(entry.id), null, EMPTY_SHA256).use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful || response.code == 204) { s3Error(response.code, raw) }
        }
    }

    private fun multipartUpload(source: File, key: String) {
        val initiateUrl = buildS3Url(key, mapOf("uploads" to ""))
        val uploadId = executeSigned("POST", initiateUrl, ByteArray(0).toRequestBody(null), EMPTY_SHA256).use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { s3Error(response.code, raw) }
            parseSimpleXml(raw)["UploadId"]?.takeIf(String::isNotBlank)
                ?: error("S3 did not return a multipart upload id")
        }
        val completed = mutableListOf<Pair<Int, String>>()
        try {
            val partSize = multipartPartSize(source.length())
            var offset = 0L
            var partNumber = 1
            while (offset < source.length()) {
                val length = min(partSize, source.length() - offset)
                val payloadHash = sha256Hex(source, offset, length)
                val url = buildS3Url(
                    key,
                    mapOf("partNumber" to partNumber.toString(), "uploadId" to uploadId),
                )
                val etag = executeSigned(
                    "PUT",
                    url,
                    FileRangeRequestBody(source, offset, length, BACKUP_MEDIA_TYPE),
                    payloadHash,
                ).use { response ->
                    val raw = response.body?.string().orEmpty()
                    require(response.isSuccessful) { s3Error(response.code, raw) }
                    response.header("ETag")?.trim()?.takeIf(String::isNotBlank)
                        ?: error("S3 upload part $partNumber returned no ETag")
                }
                completed += partNumber to etag
                offset += length
                partNumber += 1
            }
            val completeXml = buildString {
                append("<CompleteMultipartUpload>")
                completed.forEach { (number, etag) ->
                    append("<Part><PartNumber>").append(number).append("</PartNumber><ETag>")
                    append(xmlEscape(etag)).append("</ETag></Part>")
                }
                append("</CompleteMultipartUpload>")
            }
            val bodyBytes = completeXml.toByteArray()
            val completeUrl = buildS3Url(key, mapOf("uploadId" to uploadId))
            executeSigned(
                "POST",
                completeUrl,
                bodyBytes.toRequestBody(XML_MEDIA_TYPE),
                sha256Hex(bodyBytes),
            ).use { response ->
                val raw = response.body?.string().orEmpty()
                require(response.isSuccessful && !raw.contains("<Error>", ignoreCase = true)) {
                    s3Error(response.code, raw)
                }
            }
        } catch (error: Throwable) {
            runCatching {
                executeSigned(
                    "DELETE",
                    buildS3Url(key, mapOf("uploadId" to uploadId)),
                    null,
                    EMPTY_SHA256,
                ).close()
            }
            throw error
        }
    }

    private fun multipartPartSize(total: Long): Long {
        val minimumForPartLimit = (total + S3_MAX_PARTS - 1L) / S3_MAX_PARTS
        val chosen = maxOf(S3_DEFAULT_PART_BYTES, minimumForPartLimit)
        return ((chosen + S3_PART_ALIGNMENT - 1L) / S3_PART_ALIGNMENT) * S3_PART_ALIGNMENT
    }

    private fun executeSigned(method: String, url: HttpUrl, body: RequestBody?, payloadHash: String) =
        client.newCall(signedRequest(method, url, body, payloadHash)).execute()

    private fun signedRequest(method: String, url: HttpUrl, body: RequestBody?, payloadHash: String): Request {
        val now = Instant.now()
        val amzDate = AMZ_DATE.format(now)
        val date = SHORT_DATE.format(now)
        val headers = sortedMapOf(
            "host" to url.hostHeader(),
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
        )
        config.sessionToken?.let { headers["x-amz-security-token"] = it }
        val canonicalHeaders = headers.entries.joinToString("") { (key, value) -> "$key:${value.trim()}\n" }
        val signedHeaders = headers.keys.joinToString(";")
        val canonicalQuery = url.queryParameterNames.sorted().flatMap { name ->
            url.queryParameterValues(name)
                .map { value -> value.orEmpty() }
                .sorted()
                .map { value -> "${awsEncode(name)}=${awsEncode(value)}" }
        }.joinToString("&")
        val canonicalRequest = listOf(
            method,
            url.encodedPath.ifBlank { "/" },
            canonicalQuery,
            canonicalHeaders,
            signedHeaders,
            payloadHash,
        ).joinToString("\n")
        val scope = "$date/${config.region}/s3/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            scope,
            sha256Hex(canonicalRequest.toByteArray()),
        ).joinToString("\n")
        val signingKey = hmac(
            hmac(
                hmac(
                    hmac(("AWS4" + config.secretAccessKey).toByteArray(), date),
                    config.region,
                ),
                "s3",
            ),
            "aws4_request",
        )
        val signature = hmac(signingKey, stringToSign).toHex()
        val authorization = "AWS4-HMAC-SHA256 Credential=${config.accessKeyId}/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
        return Request.Builder()
            .url(url)
            .method(method, body)
            .header("Authorization", authorization)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
    }

    private fun buildS3Url(key: String? = null, query: Map<String, String> = emptyMap()): HttpUrl {
        val endpoint = config.endpoint.toHttpUrl()
        val builder = endpoint.newBuilder()
        if (config.pathStyle) {
            builder.addPathSegment(config.bucket)
        } else {
            builder.host("${config.bucket}.${endpoint.host}")
        }
        key?.split('/')?.filter(String::isNotBlank)?.forEach(builder::addPathSegment)
        query.toSortedMap().forEach { (name, value) -> builder.addQueryParameter(name, value) }
        return builder.build()
    }

    private fun objectKey(fileName: String) = "${config.prefix.trim('/')}/${safeFileName(fileName)}"

    private fun s3Error(code: Int, raw: String): String {
        val parsed = parseSimpleXml(raw)
        val message = parsed["Message"] ?: parsed["Code"]
        return when (code) {
            401, 403 -> "S3 credentials or bucket permissions were rejected${message?.let { ": $it" }.orEmpty()}"
            404 -> "The S3 bucket or backup object was not found."
            409 -> message ?: "The S3 bucket rejected this operation."
            else -> message ?: "S3 request failed with HTTP $code"
        }
    }
}

class DirectCloudBackupCoordinator(
    private val context: Context,
    val oauth: CloudOAuthManager,
    val configs: DirectCloudConfigStore,
) {
    suspend fun test(provider: DirectCloudProvider): String = client(provider).testConnection()

    suspend fun upload(provider: DirectCloudProvider, source: File, fileName: String): CloudBackupEntry =
        client(provider).uploadBackup(source, fileName)

    suspend fun list(provider: DirectCloudProvider): List<CloudBackupEntry> =
        client(provider).listBackups()

    suspend fun download(provider: DirectCloudProvider, entry: CloudBackupEntry): Uri =
        client(provider).downloadBackup(entry)

    suspend fun delete(provider: DirectCloudProvider, entry: CloudBackupEntry) =
        client(provider).deleteBackup(entry)

    fun disconnect(provider: DirectCloudProvider) {
        when (provider) {
            DirectCloudProvider.ONEDRIVE -> oauth.disconnect(CloudOAuthProvider.ONEDRIVE)
            DirectCloudProvider.DROPBOX -> oauth.disconnect(CloudOAuthProvider.DROPBOX)
            DirectCloudProvider.WEBDAV -> configs.clearWebDav()
            DirectCloudProvider.S3 -> configs.clearS3()
        }
    }

    private suspend fun client(provider: DirectCloudProvider): DirectCloudBackupClient = when (provider) {
        DirectCloudProvider.ONEDRIVE -> OneDriveAppFolderClient(
            context,
            oauth.accessToken(CloudOAuthProvider.ONEDRIVE),
        )
        DirectCloudProvider.DROPBOX -> DropboxAppFolderClient(
            context,
            oauth.accessToken(CloudOAuthProvider.DROPBOX),
        )
        DirectCloudProvider.WEBDAV -> WebDavBackupClient(
            context,
            requireNotNull(configs.state.value.webDav) { "Configure WebDAV first" },
        )
        DirectCloudProvider.S3 -> S3BackupClient(
            context,
            requireNotNull(configs.state.value.s3) { "Configure S3 first" },
        )
    }
}

private fun defaultCloudHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.MINUTES)
    .writeTimeout(10, TimeUnit.MINUTES)
    .callTimeout(15, TimeUnit.MINUTES)
    .retryOnConnectionFailure(true)
    .build()

private class FileRequestBody(
    private val file: File,
    private val mediaType: MediaType,
) : RequestBody() {
    override fun contentType(): MediaType = mediaType
    override fun contentLength(): Long = file.length()
    override fun writeTo(sink: BufferedSink) {
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sink.write(buffer, 0, count)
            }
        }
    }
}

private class FileRangeRequestBody(
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

private fun downloadToCache(
    context: Context,
    client: OkHttpClient,
    request: Request,
    provider: String,
    fileName: String,
    error: (Int, String) -> String,
): Uri {
    val root = File(context.cacheDir, "cloud-backups/$provider").apply { mkdirs() }
    root.listFiles()?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > CACHE_MAX_AGE_MS }
        ?.forEach(File::delete)
    val destination = File(root, safeFileName(fileName))
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val raw = response.body?.string().orEmpty()
            throw IOException(error(response.code, raw))
        }
        val body = requireNotNull(response.body) { "Cloud provider returned an empty backup" }
        destination.outputStream().buffered().use { output ->
            body.byteStream().use { it.copyTo(output, COPY_BUFFER_BYTES) }
        }
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", destination)
}

private fun resolveWebDav(base: String, value: String): String {
    if (value.startsWith("https://")) return value
    return URI(base).resolve(value).toString()
}

private fun parseWebDavEntries(raw: String, baseUrl: String): List<CloudBackupEntry> {
    val parser = Xml.newPullParser().apply { setInput(raw.reader()) }
    val values = mutableListOf<CloudBackupEntry>()
    var href: String? = null
    var size = 0L
    var modified = 0L
    var collection = false
    var currentTag: String? = null
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                currentTag = parser.name.lowercase(Locale.ROOT)
                if (currentTag == "response") {
                    href = null
                    size = 0L
                    modified = 0L
                    collection = false
                }
                if (currentTag == "collection") collection = true
            }
            XmlPullParser.TEXT -> {
                val text = parser.text.orEmpty().trim()
                when (currentTag) {
                    "href" -> if (text.isNotBlank()) href = text
                    "getcontentlength" -> size = text.toLongOrNull() ?: 0L
                    "getlastmodified" -> modified = runCatching {
                        Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(text)).toEpochMilli()
                    }.getOrDefault(0L)
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name.equals("response", ignoreCase = true)) {
                    val rawHref = href
                    if (!collection && !rawHref.isNullOrBlank()) {
                        val absolute = resolveWebDav(baseUrl, rawHref)
                        val name = Uri.decode(Uri.parse(absolute).lastPathSegment.orEmpty())
                        if (name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true)) {
                            values += CloudBackupEntry(
                                provider = CloudBackupProvider.WEBDAV,
                                id = absolute,
                                name = name,
                                modifiedAt = modified,
                                sizeBytes = size,
                            )
                        }
                    }
                    currentTag = null
                } else currentTag = null
            }
        }
        event = parser.next()
    }
    return values.sortedByDescending(CloudBackupEntry::modifiedAt)
}

private data class S3ListPage(
    val entries: List<CloudBackupEntry>,
    val nextContinuationToken: String?,
)

private fun parseS3Page(raw: String): S3ListPage {
    val parser = Xml.newPullParser().apply { setInput(raw.reader()) }
    val values = mutableListOf<CloudBackupEntry>()
    var inContents = false
    var currentTag: String? = null
    var key: String? = null
    var size = 0L
    var modified = 0L
    var continuationToken: String? = null
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> {
                currentTag = parser.name
                if (currentTag == "Contents") {
                    inContents = true
                    key = null
                    size = 0L
                    modified = 0L
                }
            }
            XmlPullParser.TEXT -> if (inContents) {
                val text = parser.text.orEmpty().trim()
                when (currentTag) {
                    "Key" -> key = text
                    "Size" -> size = text.toLongOrNull() ?: 0L
                    "LastModified" -> modified = runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
                    "NextContinuationToken" -> continuationToken = text.takeIf(String::isNotBlank)
                }
            }
            XmlPullParser.END_TAG -> {
                if (parser.name == "Contents") {
                    val objectKey = key
                    val name = objectKey?.substringAfterLast('/').orEmpty()
                    if (!objectKey.isNullOrBlank() && name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true)) {
                        values += CloudBackupEntry(
                            provider = CloudBackupProvider.S3,
                            id = objectKey,
                            name = name,
                            modifiedAt = modified,
                            sizeBytes = size,
                        )
                    }
                    inContents = false
                }
                currentTag = null
            }
        }
        event = parser.next()
    }
    return S3ListPage(
        entries = values.sortedByDescending(CloudBackupEntry::modifiedAt),
        nextContinuationToken = continuationToken,
    )
}

private fun parseSimpleXml(raw: String): Map<String, String> = runCatching {
    val parser = Xml.newPullParser().apply { setInput(raw.reader()) }
    val values = mutableMapOf<String, String>()
    var current: String? = null
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        when (event) {
            XmlPullParser.START_TAG -> current = parser.name
            XmlPullParser.TEXT -> current?.let { values[it] = parser.text.orEmpty().trim() }
            XmlPullParser.END_TAG -> current = null
        }
        event = parser.next()
    }
    values
}.getOrDefault(emptyMap())

private fun HttpUrl.hostHeader(): String = when {
    port == 443 && scheme == "https" -> host
    port == 80 && scheme == "http" -> host
    else -> "$host:$port"
}

private fun awsEncode(value: String): String = Uri.encode(value, "-_.~")

private fun sha256Hex(file: File): String = sha256Hex(file, 0L, file.length())

private fun sha256Hex(file: File, offset: Long, length: Long): String {
    val digest = MessageDigest.getInstance("SHA-256")
    RandomAccessFile(file, "r").use { source ->
        source.seek(offset)
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var remaining = length
        while (remaining > 0L) {
            val count = source.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw IOException("Backup file ended while calculating its upload checksum")
            digest.update(buffer, 0, count)
            remaining -= count
        }
    }
    return digest.digest().toHex()
}

private fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(value).toHex()

private fun hmac(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run {
    init(SecretKeySpec(key, "HmacSHA256"))
    doFinal(value.toByteArray())
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun safeFileName(value: String): String = value
    .replace(Regex("[^A-Za-z0-9._() -]"), "_")
    .trim()
    .take(180)
    .ifBlank { "Arbor-backup$ARBOR_BACKUP_EXTENSION" }

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()
private val BACKUP_MEDIA_TYPE = ARBOR_BACKUP_MIME.toMediaType()
private val OCTET_MEDIA_TYPE = "application/octet-stream".toMediaType()
private val AMZ_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    .withLocale(Locale.US).withZone(ZoneOffset.UTC)
private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    .withLocale(Locale.US).withZone(ZoneOffset.UTC)
private val EMPTY_SHA256 = sha256Hex(ByteArray(0))
private const val COPY_BUFFER_BYTES = 256 * 1024
private const val CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
private const val S3_MULTIPART_THRESHOLD = 64L * 1024L * 1024L
private const val S3_DEFAULT_PART_BYTES = 16L * 1024L * 1024L
private const val S3_PART_ALIGNMENT = 1024L * 1024L
private const val S3_MAX_PARTS = 10_000L
private const val S3_MAX_OBJECT_BYTES = 5L * 1024L * 1024L * 1024L * 1024L
private const val WEBDAV_PROPFIND = """<?xml version="1.0" encoding="utf-8" ?>
<d:propfind xmlns:d="DAV:"><d:prop><d:displayname/><d:getcontentlength/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>"""
