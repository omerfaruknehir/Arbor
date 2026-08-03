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
# Scoped folder and Google Drive: deletion and complete pagination.
# ---------------------------------------------------------------------------
clients = "app/src/main/java/app/arbor/chat/transfer/CloudBackupClients.kt"
replace_once(
    clients,
    '''    fun open(entry: CloudBackupEntry): Uri {
        require(entry.provider == CloudBackupProvider.SCOPED_FOLDER)
        return Uri.parse(requireNotNull(entry.uriString) { "Cloud backup URI is missing" })
    }

''',
    '''    fun open(entry: CloudBackupEntry): Uri {
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

''',
)
replace_once(
    clients,
    '''    suspend fun listBackups(accessToken: String): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
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
''',
    '''    suspend fun listBackups(accessToken: String): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
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
''',
)
replace_once(
    clients,
    '''    suspend fun downloadBackup(accessToken: String, entry: CloudBackupEntry): Uri = withContext(Dispatchers.IO) {
''',
    '''    suspend fun deleteBackup(accessToken: String, entry: CloudBackupEntry) = withContext(Dispatchers.IO) {
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
''',
)

# ---------------------------------------------------------------------------
# OneDrive and S3 list all pages.
# ---------------------------------------------------------------------------
direct = "app/src/main/java/app/arbor/chat/transfer/DirectCloudBackupClients.kt"
replace_once(
    direct,
    '''    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val url = "$GRAPH/me/drive/special/approot/children".toHttpUrl().newBuilder()
            .addQueryParameter("\\$select", "id,name,size,lastModifiedDateTime,file")
            .addQueryParameter("\\$orderby", "lastModifiedDateTime desc")
            .addQueryParameter("\\$top", "100")
            .build()
        val request = authorized(Request.Builder().url(url).get()).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { graphError(response.code, raw) }
            json.parseToJsonElement(raw).jsonObject["value"]?.jsonArray.orEmpty()
                .mapNotNull { element ->
                    val value = element.jsonObject
                    val name = value["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (!name.endsWith(ARBOR_BACKUP_EXTENSION, ignoreCase = true)) null
                    else parseOneDriveEntry(value)
                }
        }
    }
''',
    '''    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val initial = "$GRAPH/me/drive/special/approot/children".toHttpUrl().newBuilder()
            .addQueryParameter("\\$select", "id,name,size,lastModifiedDateTime,file")
            .addQueryParameter("\\$orderby", "lastModifiedDateTime desc")
            .addQueryParameter("\\$top", "200")
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
''',
)
replace_once(
    direct,
    '''    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
        val url = buildS3Url(query = mapOf("list-type" to "2", "prefix" to "${config.prefix}/", "max-keys" to "100"))
        executeSigned("GET", url, null, EMPTY_SHA256).use { response ->
            val raw = response.body?.string().orEmpty()
            require(response.isSuccessful) { s3Error(response.code, raw) }
            parseS3Entries(raw)
        }
    }
''',
    '''    override suspend fun listBackups(): List<CloudBackupEntry> = withContext(Dispatchers.IO) {
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
''',
)
replace_once(
    direct,
    '''private fun parseS3Entries(raw: String): List<CloudBackupEntry> {
''',
    '''private data class S3ListPage(
    val entries: List<CloudBackupEntry>,
    val nextContinuationToken: String?,
)

private fun parseS3Page(raw: String): S3ListPage {
''',
)
replace_once(
    direct,
    '''    var modified = 0L
    var event = parser.eventType
''',
    '''    var modified = 0L
    var continuationToken: String? = null
    var event = parser.eventType
''',
)
replace_once(
    direct,
    '''                    "LastModified" -> modified = runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
                }
''',
    '''                    "LastModified" -> modified = runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
                    "NextContinuationToken" -> continuationToken = text.takeIf(String::isNotBlank)
                }
''',
)
replace_once(
    direct,
    '''    return values.sortedByDescending(CloudBackupEntry::modifiedAt)
}

private fun parseSimpleXml''',
    '''    return S3ListPage(
        entries = values.sortedByDescending(CloudBackupEntry::modifiedAt),
        nextContinuationToken = continuationToken,
    )
}

private fun parseSimpleXml''',
)

# ---------------------------------------------------------------------------
# ViewModel wrappers.
# ---------------------------------------------------------------------------
vm = "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt"
replace_once(
    vm,
    '''    fun openConnectedFolderBackup(entry: CloudBackupEntry): Uri =
        container.scopedCloudFolder.open(entry)

''',
    '''    fun openConnectedFolderBackup(entry: CloudBackupEntry): Uri =
        container.scopedCloudFolder.open(entry)

    suspend fun deleteConnectedFolderBackup(entry: CloudBackupEntry) =
        container.scopedCloudFolder.deleteBackup(entry)

''',
)
replace_once(
    vm,
    '''    suspend fun downloadGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry): Uri =
        container.googleDriveAppData.downloadBackup(accessToken, entry)

''',
    '''    suspend fun downloadGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry): Uri =
        container.googleDriveAppData.downloadBackup(accessToken, entry)

    suspend fun deleteGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry) =
        container.googleDriveAppData.deleteBackup(accessToken, entry)

''',
)

# ---------------------------------------------------------------------------
# Consistent confirmed deletion in the legacy folder and Google cards.
# ---------------------------------------------------------------------------
ui = "app/src/main/java/app/arbor/chat/ui/CloudBackupUi.kt"
replace_once(ui, "import androidx.compose.material3.Button\n", "import androidx.compose.material3.AlertDialog\nimport androidx.compose.material3.Button\n")
replace_once(ui, "import androidx.compose.material3.Icon\n", "import androidx.compose.material3.Icon\nimport androidx.compose.material3.IconButton\n")
replace_once(ui, "import androidx.compose.material3.Text\n", "import androidx.compose.material3.Text\nimport androidx.compose.material3.TextButton\n")
replace_once(
    ui,
    '''    data object List : GoogleBackupAction
    data class Open(val entry: CloudBackupEntry) : GoogleBackupAction
''',
    '''    data object List : GoogleBackupAction
    data class Open(val entry: CloudBackupEntry) : GoogleBackupAction
    data class Delete(val entry: CloudBackupEntry) : GoogleBackupAction
''',
)
replace_once(
    ui,
    '''    var driveAuthorizationFailure by remember { mutableStateOf<GoogleDriveAuthorizationFailure?>(null) }
''',
    '''    var driveAuthorizationFailure by remember { mutableStateOf<GoogleDriveAuthorizationFailure?>(null) }
    var deleteTarget by remember { mutableStateOf<CloudBackupEntry?>(null) }
''',
)
replace_once(
    ui,
    '''                is GoogleBackupAction.Open -> "drive-open-${action.entry.id}"
''',
    '''                is GoogleBackupAction.Open -> "drive-open-${action.entry.id}"
                is GoogleBackupAction.Delete -> "drive-delete-${action.entry.id}"
''',
)
replace_once(
    ui,
    '''                    is GoogleBackupAction.Open -> {
                        val uri = viewModel.downloadGoogleDriveBackup(accessToken, action.entry)
                        viewModel.receivePortableArchive(uri)
                    }
''',
    '''                    is GoogleBackupAction.Open -> {
                        val uri = viewModel.downloadGoogleDriveBackup(accessToken, action.entry)
                        viewModel.receivePortableArchive(uri)
                    }
                    is GoogleBackupAction.Delete -> {
                        viewModel.deleteGoogleDriveBackup(accessToken, action.entry)
                        driveBackups = driveBackups.filterNot { it.id == action.entry.id }
                    }
''',
)
replace_once(
    ui,
    '''                CloudBackupList(folderBackups, busy) { entry ->
                    runCatching { viewModel.openConnectedFolderBackup(entry) }
                        .onSuccess(viewModel::receivePortableArchive)
                        .onFailure { viewModel.postNotice(it.message ?: "Could not open backup") }
                }
''',
    '''                CloudBackupList(
                    entries = folderBackups,
                    busy = busy,
                    onOpen = { entry ->
                        runCatching { viewModel.openConnectedFolderBackup(entry) }
                            .onSuccess(viewModel::receivePortableArchive)
                            .onFailure { viewModel.postNotice(it.message ?: "Could not open backup") }
                    },
                    onDelete = { deleteTarget = it },
                )
''',
)
replace_once(
    ui,
    '''                CloudBackupList(driveBackups, busy) { entry -> authorizeGoogle(GoogleBackupAction.Open(entry)) }
''',
    '''                CloudBackupList(
                    entries = driveBackups,
                    busy = busy,
                    onOpen = { authorizeGoogle(GoogleBackupAction.Open(it)) },
                    onDelete = { deleteTarget = it },
                )
''',
)
replace_once(
    ui,
    '''    DirectCloudProviderTargets(
''',
    '''    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete cloud backup?") },
            text = { Text("${entry.name} will be permanently removed from its cloud provider.") },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    when (entry.provider) {
                        app.arbor.chat.transfer.CloudBackupProvider.SCOPED_FOLDER -> scope.launch {
                            busy = "folder-delete-${entry.id}"
                            runCatching { viewModel.deleteConnectedFolderBackup(entry) }
                                .onSuccess { folderBackups = folderBackups.filterNot { it.id == entry.id } }
                                .onFailure { viewModel.postNotice(it.message ?: "Could not delete backup") }
                            busy = null
                        }
                        app.arbor.chat.transfer.CloudBackupProvider.GOOGLE_DRIVE_APP_DATA ->
                            authorizeGoogle(GoogleBackupAction.Delete(entry))
                        else -> Unit
                    }
                }) { Text("Delete") }
            },
        )
    }

    DirectCloudProviderTargets(
''',
)
replace_once(
    ui,
    '''private fun CloudBackupList(
    entries: List<CloudBackupEntry>,
    busy: String?,
    onOpen: (CloudBackupEntry) -> Unit,
) {
''',
    '''private fun CloudBackupList(
    entries: List<CloudBackupEntry>,
    busy: String?,
    onOpen: (CloudBackupEntry) -> Unit,
    onDelete: ((CloudBackupEntry) -> Unit)? = null,
) {
''',
)
replace_once(ui, "    entries.take(20).forEach { entry ->", "    entries.take(100).forEach { entry ->")
replace_once(
    ui,
    '''            OutlinedButton(onClick = { onOpen(entry) }, enabled = busy == null) { Text("Preview") }
''',
    '''            OutlinedButton(onClick = { onOpen(entry) }, enabled = busy == null) { Text("Preview") }
            onDelete?.let { delete ->
                IconButton(onClick = { delete(entry) }, enabled = busy == null) {
                    Icon(Icons.Outlined.DeleteOutline, "Delete backup")
                }
            }
''',
)

# Regression checks.
test = "app/src/test/java/app/arbor/chat/transfer/DirectCloudConfigurationTest.kt"
replace_once(
    test,
    '''    @Test
    fun manifestRoutesNativeProviderCallbacks() {
''',
    '''    @Test
    fun cloudListingsFollowProviderPagination() {
        val google = java.io.File("src/main/java/app/arbor/chat/transfer/CloudBackupClients.kt").readText()
        val direct = java.io.File("src/main/java/app/arbor/chat/transfer/DirectCloudBackupClients.kt").readText()
        assertTrue(google.contains("nextPageToken"))
        assertTrue(direct.contains("@odata.nextLink"))
        assertTrue(direct.contains("NextContinuationToken"))
    }

    @Test
    fun everyCloudBackupSurfaceSupportsDeletion() {
        val legacy = java.io.File("src/main/java/app/arbor/chat/ui/CloudBackupUi.kt").readText()
        val direct = java.io.File("src/main/java/app/arbor/chat/ui/DirectCloudProvidersUi.kt").readText()
        assertTrue(legacy.contains("deleteGoogleDriveBackup"))
        assertTrue(legacy.contains("deleteConnectedFolderBackup"))
        assertTrue(direct.contains("deleteDirectCloudBackup"))
    }

    @Test
    fun manifestRoutesNativeProviderCallbacks() {
''',
)

print("Finished Arbor 0.22.5 provider pagination and consistent deletion")
