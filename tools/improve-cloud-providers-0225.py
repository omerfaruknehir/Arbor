#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content)


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:180]!r}")
    write(path, content.replace(old, new, 1))


# ---------------------------------------------------------------------------
# S3 multipart uploads and WebDAV absolute href correctness.
# ---------------------------------------------------------------------------
clients = "app/src/main/java/app/arbor/chat/transfer/DirectCloudBackupClients.kt"
replace_once(
    clients,
    '''    override suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry = withContext(Dispatchers.IO) {
        require(source.isFile) { "Backup file no longer exists" }
        require(source.length() <= S3_SINGLE_PUT_LIMIT) { "S3 backups larger than 5 GiB require multipart upload" }
        val key = objectKey(fileName)
        val hash = sha256Hex(source)
        executeSigned("PUT", buildS3Url(key), FileRequestBody(source, BACKUP_MEDIA_TYPE), hash).use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { s3Error(response.code, raw) }
        }
        CloudBackupEntry(
            provider = CloudBackupProvider.S3,
            id = key,
            name = fileName,
            modifiedAt = System.currentTimeMillis(),
            sizeBytes = source.length(),
        )
    }
''',
    '''    override suspend fun uploadBackup(source: File, fileName: String): CloudBackupEntry = withContext(Dispatchers.IO) {
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
''',
)
replace_once(
    clients,
    '''    private fun executeSigned(method: String, url: HttpUrl, body: RequestBody?, payloadHash: String) =
        client.newCall(signedRequest(method, url, body, payloadHash)).execute()

    private fun signedRequest''',
    '''    private fun multipartUpload(source: File, key: String) {
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

    private fun signedRequest''',
)
replace_once(
    clients,
    '''private fun resolveWebDav(base: String, value: String): String {
    if (value.startsWith("https://")) return value
    return URI(base).resolve(value.removePrefix("/")).toString()
}
''',
    '''private fun resolveWebDav(base: String, value: String): String {
    if (value.startsWith("https://")) return value
    return URI(base).resolve(value).toString()
}
''',
)
replace_once(
    clients,
    '''private fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun sha256Hex(value: ByteArray): String''',
    '''private fun sha256Hex(file: File): String = sha256Hex(file, 0L, file.length())

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

private fun sha256Hex(value: ByteArray): String''',
)
replace_once(
    clients,
    '''private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun safeFileName''',
    '''private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\\\"", "&quot;")
    .replace("'", "&apos;")

private fun safeFileName''',
)
replace_once(
    clients,
    '''private const val S3_SINGLE_PUT_LIMIT = 5L * 1024L * 1024L * 1024L
private const val WEBDAV_PROPFIND''',
    '''private const val S3_MULTIPART_THRESHOLD = 64L * 1024L * 1024L
private const val S3_DEFAULT_PART_BYTES = 16L * 1024L * 1024L
private const val S3_PART_ALIGNMENT = 1024L * 1024L
private const val S3_MAX_PARTS = 10_000L
private const val S3_MAX_OBJECT_BYTES = 5L * 1024L * 1024L * 1024L * 1024L
private const val WEBDAV_PROPFIND''',
)

# ---------------------------------------------------------------------------
# Reuse provider credential dialogs from first-run restore.
# ---------------------------------------------------------------------------
direct_ui = "app/src/main/java/app/arbor/chat/ui/DirectCloudProvidersUi.kt"
replace_once(direct_ui, "private fun WebDavConfigDialog(", "internal fun WebDavConfigDialog(")
replace_once(direct_ui, "private fun S3ConfigDialog(", "internal fun S3ConfigDialog(")

# ---------------------------------------------------------------------------
# First-run restore across every direct provider.
# ---------------------------------------------------------------------------
setup = "app/src/main/java/app/arbor/chat/ui/SetupRestoreUi.kt"
replace_once(
    setup,
    "import androidx.compose.runtime.getValue\n",
    "import androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.getValue\n",
)
replace_once(
    setup,
    "import app.arbor.chat.transfer.CloudBackupProvider\n",
    "import app.arbor.chat.transfer.CloudBackupProvider\nimport app.arbor.chat.transfer.CloudOAuthProvider\nimport app.arbor.chat.transfer.CloudOAuthState\nimport app.arbor.chat.transfer.DirectCloudProvider\nimport app.arbor.chat.transfer.S3CloudConfig\nimport app.arbor.chat.transfer.WebDavCloudConfig\n",
)
replace_once(
    setup,
    "private enum class SetupCloudSource { CHOOSE, FOLDER, GOOGLE_DRIVE }",
    "private enum class SetupCloudSource { CHOOSE, FOLDER, GOOGLE_DRIVE, ONEDRIVE, DROPBOX, WEBDAV, S3 }",
)
replace_once(
    setup,
    '''    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    var cloudDialogOpen''',
    '''    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val oauthStates by viewModel.cloudOAuthStates.collectAsState()
    val directConfigurations by viewModel.directCloudConfigurations.collectAsState()
    var webDavDialogOpen by remember { mutableStateOf(false) }
    var s3DialogOpen by remember { mutableStateOf(false) }
    var cloudDialogOpen''',
)
replace_once(
    setup,
    '''    fun loadGoogleBackups(accessToken: String) {
        googleAccessToken = accessToken
        scope.launch {
            busy = true
            error = null
            runCatching { viewModel.listGoogleDriveBackups(accessToken) }
                .onSuccess { showBackups(SetupCloudSource.GOOGLE_DRIVE, it) }
                .onFailure { error = it.message ?: "Could not read Google Drive app storage" }
            busy = false
        }
    }

''',
    '''    fun loadGoogleBackups(accessToken: String) {
        googleAccessToken = accessToken
        scope.launch {
            busy = true
            error = null
            runCatching { viewModel.listGoogleDriveBackups(accessToken) }
                .onSuccess { showBackups(SetupCloudSource.GOOGLE_DRIVE, it) }
                .onFailure { error = it.message ?: "Could not read Google Drive app storage" }
            busy = false
        }
    }

    fun loadDirectBackups(provider: DirectCloudProvider, source: SetupCloudSource) {
        scope.launch {
            busy = true
            error = null
            runCatching { viewModel.listDirectCloudBackups(provider) }
                .onSuccess { showBackups(source, it) }
                .onFailure { error = it.message ?: "Could not read ${provider.displayName} backups" }
            busy = false
        }
    }

    fun connectDirectOAuth(provider: CloudOAuthProvider) {
        runCatching { viewModel.beginDirectCloudOAuth(provider) }
            .onSuccess { uri -> context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            .onFailure { error = it.message ?: "Could not open ${provider.displayName} sign-in" }
    }

''',
)
replace_once(
    setup,
    '''                        OutlinedButton(
                            onClick = { folderPicker.launch(viewModel.connectedCloudFolderUri()) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, null)
                            Text(" Choose an app backup folder", Modifier.padding(start = 6.dp))
                        }
''',
    '''                        OutlinedButton(
                            onClick = { folderPicker.launch(viewModel.connectedCloudFolderUri()) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, null)
                            Text(" Choose an app backup folder", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = {
                                when (oauthStates[CloudOAuthProvider.ONEDRIVE]) {
                                    is CloudOAuthState.Connected -> loadDirectBackups(
                                        DirectCloudProvider.ONEDRIVE,
                                        SetupCloudSource.ONEDRIVE,
                                    )
                                    else -> connectDirectOAuth(CloudOAuthProvider.ONEDRIVE)
                                }
                            },
                            enabled = !busy && oauthStates[CloudOAuthProvider.ONEDRIVE] !is CloudOAuthState.Unavailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Cloud, null)
                            Text(
                                if (oauthStates[CloudOAuthProvider.ONEDRIVE] is CloudOAuthState.Connected) {
                                    " OneDrive app folder"
                                } else " Connect OneDrive",
                                Modifier.padding(start = 6.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                when (oauthStates[CloudOAuthProvider.DROPBOX]) {
                                    is CloudOAuthState.Connected -> loadDirectBackups(
                                        DirectCloudProvider.DROPBOX,
                                        SetupCloudSource.DROPBOX,
                                    )
                                    else -> connectDirectOAuth(CloudOAuthProvider.DROPBOX)
                                }
                            },
                            enabled = !busy && oauthStates[CloudOAuthProvider.DROPBOX] !is CloudOAuthState.Unavailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Cloud, null)
                            Text(
                                if (oauthStates[CloudOAuthProvider.DROPBOX] is CloudOAuthState.Connected) {
                                    " Dropbox app folder"
                                } else " Connect Dropbox",
                                Modifier.padding(start = 6.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (directConfigurations.webDav == null) webDavDialogOpen = true
                                else loadDirectBackups(DirectCloudProvider.WEBDAV, SetupCloudSource.WEBDAV)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Cloud, null)
                            Text(
                                if (directConfigurations.webDav == null) " Configure WebDAV / Nextcloud"
                                else " ${directConfigurations.webDav?.label ?: "WebDAV"}",
                                Modifier.padding(start = 6.dp),
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (directConfigurations.s3 == null) s3DialogOpen = true
                                else loadDirectBackups(DirectCloudProvider.S3, SetupCloudSource.S3)
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Cloud, null)
                            Text(
                                if (directConfigurations.s3 == null) " Configure S3-compatible storage"
                                else " ${directConfigurations.s3?.label ?: "S3"}",
                                Modifier.padding(start = 6.dp),
                            )
                        }
''',
)
replace_once(
    setup,
    '''                                SetupCloudSource.GOOGLE_DRIVE -> "Google Drive hidden Arbor app storage"
                                SetupCloudSource.CHOOSE -> ""
''',
    '''                                SetupCloudSource.GOOGLE_DRIVE -> "Google Drive hidden Arbor app storage"
                                SetupCloudSource.ONEDRIVE -> "OneDrive Apps/Arbor folder"
                                SetupCloudSource.DROPBOX -> "Dropbox Arbor App folder"
                                SetupCloudSource.WEBDAV -> directConfigurations.webDav?.label ?: "WebDAV / Nextcloud"
                                SetupCloudSource.S3 -> directConfigurations.s3?.label ?: "S3-compatible storage"
                                SetupCloudSource.CHOOSE -> ""
''',
)
replace_once(
    setup,
    '''                                                    else -> error("This cloud provider is not available from first-run restore yet")
                                                }
''',
    '''                                                    CloudBackupProvider.ONEDRIVE_APP_FOLDER ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.ONEDRIVE, entry)
                                                    CloudBackupProvider.DROPBOX_APP_FOLDER ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.DROPBOX, entry)
                                                    CloudBackupProvider.WEBDAV ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.WEBDAV, entry)
                                                    CloudBackupProvider.S3 ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.S3, entry)
                                                }
''',
)
replace_once(
    setup,
    '''    }
}

private fun setupBackupMetadata''',
    '''    }

    if (webDavDialogOpen) {
        WebDavConfigDialog(
            existing = directConfigurations.webDav,
            onDismiss = { webDavDialogOpen = false },
            onSave = { config: WebDavCloudConfig ->
                runCatching { viewModel.saveWebDavCloud(config) }
                    .onSuccess {
                        webDavDialogOpen = false
                        loadDirectBackups(DirectCloudProvider.WEBDAV, SetupCloudSource.WEBDAV)
                    }
                    .onFailure { error = it.message ?: "Invalid WebDAV configuration" }
            },
        )
    }

    if (s3DialogOpen) {
        S3ConfigDialog(
            existing = directConfigurations.s3,
            onDismiss = { s3DialogOpen = false },
            onSave = { config: S3CloudConfig ->
                runCatching { viewModel.saveS3Cloud(config) }
                    .onSuccess {
                        s3DialogOpen = false
                        loadDirectBackups(DirectCloudProvider.S3, SetupCloudSource.S3)
                    }
                    .onFailure { error = it.message ?: "Invalid S3 configuration" }
            },
        )
    }
}

private fun setupBackupMetadata''',
)

# Source-inspection tests for the finished restore/multipart paths.
test = "app/src/test/java/app/arbor/chat/transfer/DirectCloudConfigurationTest.kt"
replace_once(
    test,
    '''    @Test
    fun manifestRoutesNativeProviderCallbacks() {
''',
    '''    @Test
    fun firstRunRestoreIncludesEveryDirectProvider() {
        val setup = java.io.File("src/main/java/app/arbor/chat/ui/SetupRestoreUi.kt").readText()
        assertTrue(setup.contains("SetupCloudSource.ONEDRIVE"))
        assertTrue(setup.contains("SetupCloudSource.DROPBOX"))
        assertTrue(setup.contains("SetupCloudSource.WEBDAV"))
        assertTrue(setup.contains("SetupCloudSource.S3"))
        assertTrue(setup.contains("downloadDirectCloudBackup"))
    }

    @Test
    fun s3UsesMultipartForLargeBackups() {
        val client = java.io.File("src/main/java/app/arbor/chat/transfer/DirectCloudBackupClients.kt").readText()
        assertTrue(client.contains("multipartUpload(source, key)"))
        assertTrue(client.contains("CompleteMultipartUpload"))
        assertTrue(client.contains("S3_MAX_PARTS"))
    }

    @Test
    fun manifestRoutesNativeProviderCallbacks() {
''',
)

print("Completed Arbor 0.22.5 first-run restore, S3 multipart, and WebDAV href handling")
