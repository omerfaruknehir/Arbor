#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:160]!r}")
    write(path, content.replace(old, new, 1))


# ---------------------------------------------------------------------------
# App graph.
# ---------------------------------------------------------------------------
app = "app/src/main/java/app/arbor/chat/ArborApplication.kt"
replace_once(
    app,
    "import app.arbor.chat.transfer.GoogleDriveAppDataClient\n",
    "import app.arbor.chat.transfer.GoogleDriveAppDataClient\nimport app.arbor.chat.transfer.CloudOAuthManager\nimport app.arbor.chat.transfer.DirectCloudConfigStore\nimport app.arbor.chat.transfer.DirectCloudBackupCoordinator\n",
)
replace_once(
    app,
    '''    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val repositoryUpdates = RepositoryUpdateManager(application)
''',
    '''    val scopedCloudFolder = ScopedCloudFolderStore(application)
    val googleDriveAppData = GoogleDriveAppDataClient(application)
    val cloudOAuth = CloudOAuthManager(application, secureStore)
    val directCloudConfigs = DirectCloudConfigStore(secureStore)
    val directCloud = DirectCloudBackupCoordinator(application, cloudOAuth, directCloudConfigs)
    val repositoryUpdates = RepositoryUpdateManager(application)
''',
)

# ---------------------------------------------------------------------------
# ViewModel methods.
# ---------------------------------------------------------------------------
vm = "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt"
replace_once(
    vm,
    "import app.arbor.chat.transfer.CloudBackupEntry\n",
    "import app.arbor.chat.transfer.CloudBackupEntry\nimport app.arbor.chat.transfer.CloudOAuthProvider\nimport app.arbor.chat.transfer.CloudOAuthState\nimport app.arbor.chat.transfer.DirectCloudProvider\nimport app.arbor.chat.transfer.DirectCloudConfigurationSnapshot\nimport app.arbor.chat.transfer.WebDavCloudConfig\nimport app.arbor.chat.transfer.S3CloudConfig\n",
)
replace_once(
    vm,
    '''    val repositoryUpdateState: StateFlow<RepositoryUpdateState> = container.repositoryUpdates.state
    val shareConversationId''',
    '''    val repositoryUpdateState: StateFlow<RepositoryUpdateState> = container.repositoryUpdates.state
    val cloudOAuthStates: StateFlow<Map<CloudOAuthProvider, CloudOAuthState>> = container.cloudOAuth.states
    val directCloudConfigurations: StateFlow<DirectCloudConfigurationSnapshot> = container.directCloudConfigs.state
    val shareConversationId''',
)
replace_once(
    vm,
    '''    suspend fun downloadGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry): Uri =
        container.googleDriveAppData.downloadBackup(accessToken, entry)

    fun receivePortableArchive''',
    '''    suspend fun downloadGoogleDriveBackup(accessToken: String, entry: CloudBackupEntry): Uri =
        container.googleDriveAppData.downloadBackup(accessToken, entry)

    fun directCloudBuildConfigured(provider: CloudOAuthProvider): Boolean =
        container.cloudOAuth.isBuildConfigured(provider)

    fun directCloudConfigurationReason(provider: CloudOAuthProvider): String? =
        container.cloudOAuth.configurationReason(provider)

    fun directCloudRedirectUri(provider: CloudOAuthProvider): String =
        container.cloudOAuth.redirectUri(provider)

    fun beginDirectCloudOAuth(provider: CloudOAuthProvider): Uri =
        container.cloudOAuth.beginAuthorization(provider)

    fun handleCloudOAuthRedirect(uri: Uri): Boolean {
        if (!container.cloudOAuth.canHandleRedirect(uri)) return false
        viewModelScope.launch {
            runCatching { container.cloudOAuth.completeRedirect(uri) }
                .onSuccess { session -> notices.emit("Connected ${session.provider.displayName}") }
                .onFailure { error -> notices.emit(error.message ?: "Cloud account connection failed") }
        }
        return true
    }

    fun saveWebDavCloud(config: WebDavCloudConfig) = container.directCloudConfigs.saveWebDav(config)
    fun saveS3Cloud(config: S3CloudConfig) = container.directCloudConfigs.saveS3(config)

    fun disconnectDirectCloud(provider: DirectCloudProvider) = container.directCloud.disconnect(provider)

    suspend fun testDirectCloud(provider: DirectCloudProvider): String = container.directCloud.test(provider)

    suspend fun writeDirectCloudBackup(
        provider: DirectCloudProvider,
        options: ArchiveOptions,
        password: String,
    ): CloudBackupEntry {
        val file = container.archiveManager.writeBackupToCache(options, password)
        return try {
            container.directCloud.upload(provider, file, file.name)
        } finally {
            file.delete()
        }
    }

    suspend fun listDirectCloudBackups(provider: DirectCloudProvider): List<CloudBackupEntry> =
        container.directCloud.list(provider)

    suspend fun downloadDirectCloudBackup(provider: DirectCloudProvider, entry: CloudBackupEntry): Uri =
        container.directCloud.download(provider, entry)

    suspend fun deleteDirectCloudBackup(provider: DirectCloudProvider, entry: CloudBackupEntry) =
        container.directCloud.delete(provider, entry)

    fun receivePortableArchive''',
)

# ---------------------------------------------------------------------------
# Route OAuth callbacks before treating ACTION_VIEW as a backup archive.
# ---------------------------------------------------------------------------
main = "app/src/main/java/app/arbor/chat/MainActivity.kt"
replace_once(
    main,
    '''        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let {
                viewModel.receivePortableArchive(it)
                return
            }
        }
''',
    '''        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let {
                if (viewModel.handleCloudOAuthRedirect(it)) return
                viewModel.receivePortableArchive(it)
                return
            }
        }
''',
)

# ---------------------------------------------------------------------------
# Direct provider UI.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/arbor/chat/ui/DirectCloudProvidersUi.kt",
    r'''package app.arbor.chat.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.arbor.chat.BuildConfig
import app.arbor.chat.transfer.ArchiveOptions
import app.arbor.chat.transfer.CloudBackupEntry
import app.arbor.chat.transfer.CloudOAuthProvider
import app.arbor.chat.transfer.CloudOAuthState
import app.arbor.chat.transfer.DirectCloudProvider
import app.arbor.chat.transfer.S3CloudConfig
import app.arbor.chat.transfer.WebDavCloudConfig
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
internal fun DirectCloudProviderTargets(
    viewModel: ChatViewModel,
    options: ArchiveOptions,
    password: String,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val oauthStates by viewModel.cloudOAuthStates.collectAsState()
    val configurations by viewModel.directCloudConfigurations.collectAsState()
    var entries by remember { mutableStateOf<Map<DirectCloudProvider, List<CloudBackupEntry>>>(emptyMap()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var errors by remember { mutableStateOf<Map<DirectCloudProvider, String>>(emptyMap()) }
    var webDavDialog by remember { mutableStateOf(false) }
    var s3Dialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<DirectCloudProvider, CloudBackupEntry>?>(null) }

    fun setError(provider: DirectCloudProvider, message: String?) {
        errors = errors.toMutableMap().apply {
            if (message.isNullOrBlank()) remove(provider) else put(provider, message)
        }
    }

    fun refresh(provider: DirectCloudProvider) {
        scope.launch {
            busy = "refresh-${provider.name}"
            setError(provider, null)
            runCatching { viewModel.listDirectCloudBackups(provider) }
                .onSuccess { entries = entries.toMutableMap().apply { put(provider, it) } }
                .onFailure { setError(provider, it.message ?: "Could not list backups") }
            busy = null
        }
    }

    fun backup(provider: DirectCloudProvider) {
        scope.launch {
            busy = "backup-${provider.name}"
            setError(provider, null)
            runCatching { viewModel.writeDirectCloudBackup(provider, options, password) }
                .onSuccess {
                    viewModel.postNotice("Backup saved to ${provider.displayName}")
                    entries = entries.toMutableMap().apply {
                        put(provider, viewModel.listDirectCloudBackups(provider))
                    }
                }
                .onFailure { setError(provider, it.message ?: "Cloud backup failed") }
            busy = null
        }
    }

    fun preview(provider: DirectCloudProvider, entry: CloudBackupEntry) {
        scope.launch {
            busy = "open-${provider.name}-${entry.id}"
            setError(provider, null)
            runCatching { viewModel.downloadDirectCloudBackup(provider, entry) }
                .onSuccess(viewModel::receivePortableArchive)
                .onFailure { setError(provider, it.message ?: "Could not download backup") }
            busy = null
        }
    }

    fun connectOAuth(provider: CloudOAuthProvider) {
        runCatching { viewModel.beginDirectCloudOAuth(provider) }
            .onSuccess { uri ->
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            .onFailure { viewModel.postNotice(it.message ?: "Could not open cloud sign-in") }
    }

    Text(
        "Direct app storage",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        "These providers keep Arbor backups in an app-specific folder or prefix. OAuth tokens and storage credentials are encrypted on this device and excluded from exported backups.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OAuthCloudCard(
        provider = DirectCloudProvider.ONEDRIVE,
        state = oauthStates[CloudOAuthProvider.ONEDRIVE] ?: CloudOAuthState.Disconnected,
        description = "Uses OneDrive's Apps/Arbor folder with Files.ReadWrite.AppFolder instead of access to the whole drive.",
        entries = entries[DirectCloudProvider.ONEDRIVE].orEmpty(),
        busy = busy,
        error = errors[DirectCloudProvider.ONEDRIVE],
        enabled = enabled,
        onConnect = { connectOAuth(CloudOAuthProvider.ONEDRIVE) },
        onBackup = { backup(DirectCloudProvider.ONEDRIVE) },
        onRefresh = { refresh(DirectCloudProvider.ONEDRIVE) },
        onDisconnect = {
            viewModel.disconnectDirectCloud(DirectCloudProvider.ONEDRIVE)
            entries = entries - DirectCloudProvider.ONEDRIVE
        },
        onPreview = { preview(DirectCloudProvider.ONEDRIVE, it) },
        onDelete = { deleteTarget = DirectCloudProvider.ONEDRIVE to it },
        setupVariable = "ARBOR_MICROSOFT_CLIENT_ID",
        redirectUri = runCatching { viewModel.directCloudRedirectUri(CloudOAuthProvider.ONEDRIVE) }.getOrNull(),
        onOpenGuide = { openCloudGuide(uriHandler) },
    )

    OAuthCloudCard(
        provider = DirectCloudProvider.DROPBOX,
        state = oauthStates[CloudOAuthProvider.DROPBOX] ?: CloudOAuthState.Disconnected,
        description = "Uses Dropbox App folder access and scoped file permissions. Arbor cannot browse the rest of Dropbox.",
        entries = entries[DirectCloudProvider.DROPBOX].orEmpty(),
        busy = busy,
        error = errors[DirectCloudProvider.DROPBOX],
        enabled = enabled,
        onConnect = { connectOAuth(CloudOAuthProvider.DROPBOX) },
        onBackup = { backup(DirectCloudProvider.DROPBOX) },
        onRefresh = { refresh(DirectCloudProvider.DROPBOX) },
        onDisconnect = {
            viewModel.disconnectDirectCloud(DirectCloudProvider.DROPBOX)
            entries = entries - DirectCloudProvider.DROPBOX
        },
        onPreview = { preview(DirectCloudProvider.DROPBOX, it) },
        onDelete = { deleteTarget = DirectCloudProvider.DROPBOX to it },
        setupVariable = "ARBOR_DROPBOX_APP_KEY",
        redirectUri = runCatching { viewModel.directCloudRedirectUri(CloudOAuthProvider.DROPBOX) }.getOrNull(),
        onOpenGuide = { openCloudGuide(uriHandler) },
    )

    CredentialCloudCard(
        provider = DirectCloudProvider.WEBDAV,
        connectedLabel = configurations.webDav?.label,
        description = "Direct HTTPS WebDAV support for Nextcloud, ownCloud, NAS servers, and compatible hosts. Use an app password when available.",
        entries = entries[DirectCloudProvider.WEBDAV].orEmpty(),
        busy = busy,
        error = errors[DirectCloudProvider.WEBDAV],
        enabled = enabled,
        onConfigure = { webDavDialog = true },
        onBackup = { backup(DirectCloudProvider.WEBDAV) },
        onRefresh = { refresh(DirectCloudProvider.WEBDAV) },
        onDisconnect = {
            viewModel.disconnectDirectCloud(DirectCloudProvider.WEBDAV)
            entries = entries - DirectCloudProvider.WEBDAV
        },
        onPreview = { preview(DirectCloudProvider.WEBDAV, it) },
        onDelete = { deleteTarget = DirectCloudProvider.WEBDAV to it },
    )

    CredentialCloudCard(
        provider = DirectCloudProvider.S3,
        connectedLabel = configurations.s3?.label,
        description = "Works with AWS S3, Cloudflare R2, Backblaze B2, MinIO, and other Signature V4-compatible object stores.",
        entries = entries[DirectCloudProvider.S3].orEmpty(),
        busy = busy,
        error = errors[DirectCloudProvider.S3],
        enabled = enabled,
        onConfigure = { s3Dialog = true },
        onBackup = { backup(DirectCloudProvider.S3) },
        onRefresh = { refresh(DirectCloudProvider.S3) },
        onDisconnect = {
            viewModel.disconnectDirectCloud(DirectCloudProvider.S3)
            entries = entries - DirectCloudProvider.S3
        },
        onPreview = { preview(DirectCloudProvider.S3, it) },
        onDelete = { deleteTarget = DirectCloudProvider.S3 to it },
    )

    if (webDavDialog) {
        WebDavConfigDialog(
            existing = configurations.webDav,
            onDismiss = { webDavDialog = false },
            onSave = { config ->
                runCatching { viewModel.saveWebDavCloud(config) }
                    .onSuccess {
                        webDavDialog = false
                        scope.launch {
                            busy = "test-WEBDAV"
                            runCatching { viewModel.testDirectCloud(DirectCloudProvider.WEBDAV) }
                                .onSuccess { viewModel.postNotice("Connected ${config.label.ifBlank { "WebDAV" }}") }
                                .onFailure { setError(DirectCloudProvider.WEBDAV, it.message) }
                            busy = null
                        }
                    }
                    .onFailure { viewModel.postNotice(it.message ?: "Invalid WebDAV configuration") }
            },
        )
    }

    if (s3Dialog) {
        S3ConfigDialog(
            existing = configurations.s3,
            onDismiss = { s3Dialog = false },
            onSave = { config ->
                runCatching { viewModel.saveS3Cloud(config) }
                    .onSuccess {
                        s3Dialog = false
                        scope.launch {
                            busy = "test-S3"
                            runCatching { viewModel.testDirectCloud(DirectCloudProvider.S3) }
                                .onSuccess { viewModel.postNotice("Connected ${config.label.ifBlank { "S3" }}") }
                                .onFailure { setError(DirectCloudProvider.S3, it.message) }
                            busy = null
                        }
                    }
                    .onFailure { viewModel.postNotice(it.message ?: "Invalid S3 configuration") }
            },
        )
    }

    deleteTarget?.let { (provider, entry) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete cloud backup?") },
            text = { Text("${entry.name} will be permanently deleted from ${provider.displayName}.") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    scope.launch {
                        busy = "delete-${provider.name}-${entry.id}"
                        runCatching { viewModel.deleteDirectCloudBackup(provider, entry) }
                            .onSuccess {
                                entries = entries.toMutableMap().apply {
                                    put(provider, entries[provider].orEmpty().filterNot { it.id == entry.id })
                                }
                                viewModel.postNotice("Cloud backup deleted")
                            }
                            .onFailure { setError(provider, it.message ?: "Could not delete backup") }
                        busy = null
                    }
                }) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun OAuthCloudCard(
    provider: DirectCloudProvider,
    state: CloudOAuthState,
    description: String,
    entries: List<CloudBackupEntry>,
    busy: String?,
    error: String?,
    enabled: Boolean,
    onConnect: () -> Unit,
    onBackup: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onPreview: (CloudBackupEntry) -> Unit,
    onDelete: (CloudBackupEntry) -> Unit,
    setupVariable: String,
    redirectUri: String?,
    onOpenGuide: () -> Unit,
) {
    CloudProviderSurface(provider, description) {
        when (state) {
            is CloudOAuthState.Unavailable -> {
                ProviderError(state.reason)
                Text("Build variable: $setupVariable", style = MaterialTheme.typography.labelSmall)
                redirectUri?.takeIf { !it.contains("unconfigured") }?.let {
                    Text("Redirect URI: $it", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.OpenInBrowser, null)
                    Text(" Open provider setup guide")
                }
            }
            CloudOAuthState.Disconnected -> {
                Button(onClick = onConnect, enabled = enabled && busy == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, null)
                    Text(" Connect ${provider.displayName}")
                }
            }
            is CloudOAuthState.Authorizing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Waiting for ${provider.displayName} authorization…")
                }
            }
            is CloudOAuthState.Connected -> {
                ConnectedSummary(state.accountLabel ?: "Account connected")
                ProviderActions(enabled, busy, onBackup, onRefresh, onDisconnect)
                DirectBackupList(entries, busy, onPreview, onDelete)
            }
            is CloudOAuthState.Error -> {
                ProviderError(state.message)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onConnect, enabled = busy == null, modifier = Modifier.weight(1f)) {
                        Text("Reconnect")
                    }
                    OutlinedButton(onClick = onDisconnect, enabled = busy == null, modifier = Modifier.weight(1f)) {
                        Text("Reset")
                    }
                }
            }
        }
        error?.let(::ProviderError)
    }
}

@Composable
private fun CredentialCloudCard(
    provider: DirectCloudProvider,
    connectedLabel: String?,
    description: String,
    entries: List<CloudBackupEntry>,
    busy: String?,
    error: String?,
    enabled: Boolean,
    onConfigure: () -> Unit,
    onBackup: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onPreview: (CloudBackupEntry) -> Unit,
    onDelete: (CloudBackupEntry) -> Unit,
) {
    CloudProviderSurface(provider, description) {
        if (connectedLabel == null) {
            Button(onClick = onConfigure, enabled = enabled && busy == null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, null)
                Text(" Configure ${provider.displayName}")
            }
        } else {
            ConnectedSummary(connectedLabel)
            ProviderActions(enabled, busy, onBackup, onRefresh, onDisconnect, onConfigure)
            DirectBackupList(entries, busy, onPreview, onDelete)
        }
        error?.let(::ProviderError)
    }
}

@Composable
private fun CloudProviderSurface(
    provider: DirectCloudProvider,
    description: String,
    content: @Composable Column.() -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(provider.displayName, fontWeight = FontWeight.SemiBold)
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun ConnectedSummary(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Connected", fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProviderActions(
    enabled: Boolean,
    busy: String?,
    onBackup: () -> Unit,
    onRefresh: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: (() -> Unit)? = null,
) {
    Button(onClick = onBackup, enabled = enabled && busy == null, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Backup, null)
        Text(" Back up now")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRefresh, enabled = busy == null, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Refresh, null)
            Text(" Backups")
        }
        onEdit?.let {
            OutlinedButton(onClick = it, enabled = busy == null) { Icon(Icons.Outlined.Edit, "Edit") }
        }
        OutlinedButton(onClick = onDisconnect, enabled = busy == null) { Icon(Icons.Outlined.Logout, "Disconnect") }
    }
}

@Composable
private fun DirectBackupList(
    entries: List<CloudBackupEntry>,
    busy: String?,
    onPreview: (CloudBackupEntry) -> Unit,
    onDelete: (CloudBackupEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    HorizontalDivider()
    entries.take(50).forEach { entry ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    buildString {
                        if (entry.modifiedAt > 0L) append(DateFormat.getDateTimeInstance().format(Date(entry.modifiedAt)))
                        if (entry.sizeBytes > 0L) {
                            if (isNotEmpty()) append(" • ")
                            append(readableDirectBytes(entry.sizeBytes))
                        }
                    }.ifBlank { "Portable Arbor backup" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onPreview(entry) }, enabled = busy == null) { Text("Preview") }
            IconButton(onClick = { onDelete(entry) }, enabled = busy == null) {
                Icon(Icons.Outlined.DeleteOutline, "Delete backup")
            }
        }
    }
}

@Composable
private fun ProviderError(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun WebDavConfigDialog(
    existing: WebDavCloudConfig?,
    onDismiss: () -> Unit,
    onSave: (WebDavCloudConfig) -> Unit,
) {
    var label by remember(existing) { mutableStateOf(existing?.label.orEmpty()) }
    var url by remember(existing) { mutableStateOf(existing?.folderUrl.orEmpty()) }
    var username by remember(existing) { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember(existing) { mutableStateOf(existing?.password.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV / Nextcloud") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the exact HTTPS URL of a dedicated Arbor folder. For Nextcloud this normally ends with /remote.php/dav/files/USERNAME/Arbor/.")
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text("WebDAV folder URL") }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password or app password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text("Credentials are encrypted with Android Keystore and are never included in Arbor backups.", style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = { onSave(WebDavCloudConfig(label, url, username, password)) }) { Text("Save and test") }
        },
    )
}

@Composable
private fun S3ConfigDialog(
    existing: S3CloudConfig?,
    onDismiss: () -> Unit,
    onSave: (S3CloudConfig) -> Unit,
) {
    var label by remember(existing) { mutableStateOf(existing?.label.orEmpty()) }
    var endpoint by remember(existing) { mutableStateOf(existing?.endpoint.orEmpty()) }
    var region by remember(existing) { mutableStateOf(existing?.region ?: "us-east-1") }
    var bucket by remember(existing) { mutableStateOf(existing?.bucket.orEmpty()) }
    var prefix by remember(existing) { mutableStateOf(existing?.prefix ?: "arbor") }
    var accessKey by remember(existing) { mutableStateOf(existing?.accessKeyId.orEmpty()) }
    var secretKey by remember(existing) { mutableStateOf(existing?.secretAccessKey.orEmpty()) }
    var sessionToken by remember(existing) { mutableStateOf(existing?.sessionToken.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("S3-compatible storage") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Label") }, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text("HTTPS endpoint") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(region, { region = it }, label = { Text("Region") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bucket, { bucket = it }, label = { Text("Bucket") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(prefix, { prefix = it }, label = { Text("Prefix") }, singleLine = true)
                OutlinedTextField(accessKey, { accessKey = it }, label = { Text("Access key ID") }, singleLine = true)
                OutlinedTextField(
                    secretKey,
                    { secretKey = it },
                    label = { Text("Secret access key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    sessionToken,
                    { sessionToken = it },
                    label = { Text("Session token (optional)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text("Use a key restricted to this bucket and prefix. Credentials remain encrypted on-device.", style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                onSave(
                    S3CloudConfig(
                        label = label,
                        endpoint = endpoint,
                        region = region,
                        bucket = bucket,
                        prefix = prefix,
                        accessKeyId = accessKey,
                        secretAccessKey = secretKey,
                        sessionToken = sessionToken.takeIf(String::isNotBlank),
                    ),
                )
            }) { Text("Save and test") }
        },
    )
}

private fun openCloudGuide(uriHandler: androidx.compose.ui.platform.UriHandler) {
    val repository = BuildConfig.SOURCE_REPOSITORY
    if (repository.isNotBlank()) {
        uriHandler.openUri("https://github.com/$repository/blob/main/docs/CLOUD_PROVIDERS_SETUP.md")
    }
}

private fun readableDirectBytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "%.1f GiB".format(value.toDouble() / (1024.0 * 1024 * 1024))
    value >= 1024L * 1024 -> "%.1f MiB".format(value.toDouble() / (1024.0 * 1024))
    value >= 1024L -> "%.1f KiB".format(value.toDouble() / 1024.0)
    else -> "$value B"
}
''',
)

# Call direct provider UI before the security note.
cloud_ui = "app/src/main/java/app/arbor/chat/ui/CloudBackupUi.kt"
replace_once(
    cloud_ui,
    '''    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
''',
    '''    DirectCloudProviderTargets(
        viewModel = viewModel,
        options = options,
        password = password,
        enabled = enabled,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
''',
)
replace_once(
    cloud_ui,
    'subtitle = "Use either one folder you explicitly choose or Google Drive\'s hidden Arbor-only app storage. Arbor never requests access to your whole cloud drive.",',
    'subtitle = "Choose a scoped Android folder, an OAuth app folder, WebDAV/Nextcloud, or an S3-compatible bucket prefix. Arbor avoids account-wide cloud access.",',
)

# ---------------------------------------------------------------------------
# Build workflows receive public provider identifiers from repository vars.
# ---------------------------------------------------------------------------
release = ".github/workflows/release.yml"
replace_once(
    release,
    '''      ARBOR_SOURCE_REPOSITORY: ${{ github.repository }}
      ARBOR_SOURCE_COMMIT: ${{ github.sha }}
''',
    '''      ARBOR_SOURCE_REPOSITORY: ${{ github.repository }}
      ARBOR_SOURCE_COMMIT: ${{ github.sha }}
      ARBOR_MICROSOFT_CLIENT_ID: ${{ vars.ARBOR_MICROSOFT_CLIENT_ID }}
      ARBOR_DROPBOX_APP_KEY: ${{ vars.ARBOR_DROPBOX_APP_KEY }}
''',
)
android = ".github/workflows/android.yml"
replace_once(
    android,
    '''permissions:
  contents: read

jobs:
''',
    '''permissions:
  contents: read

env:
  ARBOR_SOURCE_REPOSITORY: ${{ github.repository }}
  ARBOR_SOURCE_COMMIT: ${{ github.sha }}
  ARBOR_MICROSOFT_CLIENT_ID: ${{ vars.ARBOR_MICROSOFT_CLIENT_ID }}
  ARBOR_DROPBOX_APP_KEY: ${{ vars.ARBOR_DROPBOX_APP_KEY }}

jobs:
''',
)

# ---------------------------------------------------------------------------
# Documentation and regression tests.
# ---------------------------------------------------------------------------
write(
    "docs/CLOUD_PROVIDERS_SETUP.md",
    r'''# Cloud provider setup

Arbor supports six cloud paths:

1. Android's scoped folder picker
2. Google Drive `appDataFolder`
3. OneDrive application folder
4. Dropbox App folder
5. Nextcloud or generic WebDAV
6. S3-compatible object storage

No provider refresh token, user password, S3 access key, or storage secret belongs in GitHub Actions. Those values are entered by the user and encrypted locally with Android Keystore.

## GitHub Actions configuration

Two provider values are public application identifiers and are embedded in the APK. Configure them as **Repository variables**, not secrets:

- `ARBOR_MICROSOFT_CLIENT_ID`
- `ARBOR_DROPBOX_APP_KEY`

Repository path: **Settings → Secrets and variables → Actions → Variables**.

Release-signing keystore passwords remain GitHub **Secrets**. OAuth client secrets are neither needed nor safe in a native Android application.

## Google Drive

The repository owner must create or select one Google Cloud project:

1. Enable Google Drive API.
2. Configure the OAuth consent screen.
3. Create an Android OAuth client for every officially distributed package/signing pair.
4. Register the exact package and SHA-1 shown by Arbor's diagnostic card.
5. Keep the requested scope limited to `https://www.googleapis.com/auth/drive.appdata`.

The normal public GitHub release currently uses package `app.arbor.chat.debug`. Protected production releases use `app.arbor.chat` and need their private release certificate SHA-1 registered separately in the same Cloud project.

Google Android OAuth clients have no client secret to embed.

## OneDrive

Create one Microsoft Entra app registration:

1. Choose supported account types. For personal OneDrive plus work/school accounts, allow organizational directories and personal Microsoft accounts.
2. Add the Microsoft Graph delegated permission `Files.ReadWrite.AppFolder`.
3. Add the Android platform using Arbor's package name and signature hash. Arbor shows the exact `msauth://...` redirect URI in the provider card.
4. Enable public-client/native flows.
5. Put the Application (client) ID in repository variable `ARBOR_MICROSOFT_CLIENT_ID`.

Arbor uses Authorization Code + PKCE and requests `offline_access`; do not create or embed a client secret.

## Dropbox

Create one scoped Dropbox API app:

1. Choose **App folder** access, not Full Dropbox.
2. Enable `account_info.read`, `files.metadata.read`, `files.content.read`, and `files.content.write`.
3. Add redirect URI `db-APP_KEY://2/token`, replacing `APP_KEY` with the app key.
4. Put the app key in repository variable `ARBOR_DROPBOX_APP_KEY`.

Arbor uses Authorization Code + PKCE with refresh tokens. A Dropbox app secret is not used by the Android app.

## Nextcloud / WebDAV

No developer project or repository variable is required. Each user enters:

- a dedicated HTTPS WebDAV folder URL
- username
- password or, preferably, an app password

A typical Nextcloud URL is:

```text
https://cloud.example.com/remote.php/dav/files/USERNAME/Arbor/
```

Arbor refuses unencrypted HTTP endpoints and stores credentials in encrypted local preferences.

## S3-compatible storage

No repository-level cloud account is required. Each user enters an HTTPS endpoint, region, bucket, prefix, access key, and secret key. The client uses AWS Signature Version 4 and supports AWS S3, Cloudflare R2, Backblaze B2 S3, MinIO, and compatible services.

Use a dedicated key restricted to the selected bucket and prefix. A minimal policy should allow only listing that prefix and getting, putting, and deleting objects inside it. Never put a user's S3 keys in GitHub Actions.
''',
)

write(
    "docs/releases/RELEASE_NOTES_0.22.5.md",
    '''# Arbor 0.22.5

- Add a unified cloud-provider layer alongside Android's scoped folder picker.
- Add OneDrive app-folder support using Authorization Code + PKCE and `Files.ReadWrite.AppFolder`.
- Add Dropbox App-folder support using scoped permissions, PKCE, refresh tokens, and resumable uploads.
- Add direct HTTPS WebDAV and Nextcloud backup browsing, upload, preview, restore, and deletion.
- Add AWS Signature Version 4 support for S3, Cloudflare R2, Backblaze B2, MinIO, and compatible storage.
- Encrypt OAuth sessions, WebDAV credentials, and S3 credentials with Android Keystore and exclude them from portable backups.
- Add provider-specific connection tests, errors, backup lists, previews, and confirmed deletion.
- Configure public Microsoft client IDs and Dropbox app keys through GitHub Actions repository variables rather than secrets.
- Document the exact external setup required for Google Cloud, Microsoft Entra, Dropbox, WebDAV, and S3.
''',
)

write(
    "app/src/test/java/app/arbor/chat/transfer/DirectCloudConfigurationTest.kt",
    r'''package app.arbor.chat.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFailsWith
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectCloudConfigurationTest {
    @Test
    fun webDavRequiresHttpsAndNormalizesFolderSlash() {
        val value = validateWebDavConfig(
            WebDavCloudConfig("Home", "https://cloud.example/dav/arbor", "omer", "app-password"),
        )
        assertEquals("https://cloud.example/dav/arbor/", value.folderUrl)
        assertFailsWith<IllegalArgumentException> {
            validateWebDavConfig(WebDavCloudConfig("Bad", "http://cloud.example/dav", "u", "p"))
        }
    }

    @Test
    fun s3NormalizesPrefixAndRequiresCredentials() {
        val value = validateS3Config(
            S3CloudConfig("R2", "https://example.r2.cloudflarestorage.com/", "auto", "arbor-backups", "/mobile/", "key", "secret"),
        )
        assertEquals("https://example.r2.cloudflarestorage.com", value.endpoint)
        assertEquals("mobile", value.prefix)
        assertFailsWith<IllegalArgumentException> {
            validateS3Config(value.copy(secretAccessKey = ""))
        }
    }

    @Test
    fun providerSetupUsesPublicBuildVariablesNotClientSecrets() {
        val build = java.io.File("build.gradle.kts").readText()
        val release = java.io.File("../.github/workflows/release.yml").readText()
        assertTrue(build.contains("ARBOR_MICROSOFT_CLIENT_ID"))
        assertTrue(build.contains("ARBOR_DROPBOX_APP_KEY"))
        assertTrue(release.contains("vars.ARBOR_MICROSOFT_CLIENT_ID"))
        assertTrue(release.contains("vars.ARBOR_DROPBOX_APP_KEY"))
        assertTrue(!release.contains("MICROSOFT_CLIENT_SECRET"))
        assertTrue(!release.contains("DROPBOX_APP_SECRET"))
    }

    @Test
    fun manifestRoutesNativeProviderCallbacks() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:scheme=\"msauth\""))
        assertTrue(manifest.contains("android:scheme=\"${dropboxOAuthScheme}\""))
    }
}
''',
)

print("Applied Arbor 0.22.5 cloud UI, integration, docs, and tests")
