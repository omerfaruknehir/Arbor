package app.xylune.chat.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.xylune.chat.BuildConfig
import app.xylune.chat.transfer.ArchiveOptions
import app.xylune.chat.transfer.CloudBackupEntry
import app.xylune.chat.transfer.CloudOAuthProvider
import app.xylune.chat.transfer.CloudOAuthState
import app.xylune.chat.transfer.DirectCloudProvider
import app.xylune.chat.transfer.S3CloudConfig
import app.xylune.chat.transfer.WebDavCloudConfig
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
            runCatching {
                viewModel.writeDirectCloudBackup(provider, options, password)
                viewModel.listDirectCloudBackups(provider)
            }
                .onSuccess { refreshed ->
                    viewModel.postNotice("Backup saved to ${provider.displayName}")
                    entries = entries.toMutableMap().apply { put(provider, refreshed) }
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
        uiText("Direct app storage"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        uiText("These providers keep Xylune backups in an app-specific folder or prefix. OAuth tokens and storage credentials are encrypted on this device and excluded from exported backups."),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OAuthCloudCard(
        provider = DirectCloudProvider.ONEDRIVE,
        state = oauthStates[CloudOAuthProvider.ONEDRIVE] ?: CloudOAuthState.Disconnected,
        description = "Uses OneDrive's Apps/Xylune folder with Files.ReadWrite.AppFolder instead of access to the whole drive.",
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
        setupVariable = "XYLUNE_MICROSOFT_CLIENT_ID",
        redirectUri = runCatching { viewModel.directCloudRedirectUri(CloudOAuthProvider.ONEDRIVE) }.getOrNull(),
        onOpenGuide = { openCloudGuide(uriHandler) },
    )

    OAuthCloudCard(
        provider = DirectCloudProvider.DROPBOX,
        state = oauthStates[CloudOAuthProvider.DROPBOX] ?: CloudOAuthState.Disconnected,
        description = "Uses Dropbox App folder access and scoped file permissions. Xylune cannot browse the rest of Dropbox.",
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
        setupVariable = "XYLUNE_DROPBOX_APP_KEY",
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
            title = { Text(uiText("Delete cloud backup?")) },
            text = { Text(uiText("${entry.name} will be permanently deleted from ${provider.displayName}.")) },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(uiText("Cancel")) } },
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
                }) { Text(uiText("Delete")) }
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
                Text(uiText("Build variable: $setupVariable"), style = MaterialTheme.typography.labelSmall)
                redirectUri?.takeIf { !it.contains("unconfigured") }?.let {
                    Text(uiText("Redirect URI: $it"), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.OpenInBrowser, null)
                    Text(uiText(" Open provider setup guide"))
                }
            }
            CloudOAuthState.Disconnected -> {
                Button(onClick = onConnect, enabled = enabled && busy == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Link, null)
                    Text(uiText(" Connect ${provider.displayName}"))
                }
            }
            is CloudOAuthState.Authorizing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(uiText("Waiting for ${provider.displayName} authorization…"))
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
                        Text(uiText("Reconnect"))
                    }
                    OutlinedButton(onClick = onDisconnect, enabled = busy == null, modifier = Modifier.weight(1f)) {
                        Text(uiText("Reset"))
                    }
                }
            }
        }
        error?.let { message -> ProviderError(message) }
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
                Text(uiText(" Configure ${provider.displayName}"))
            }
        } else {
            ConnectedSummary(connectedLabel)
            ProviderActions(enabled, busy, onBackup, onRefresh, onDisconnect, onConfigure)
            DirectBackupList(entries, busy, onPreview, onDelete)
        }
        error?.let { message -> ProviderError(message) }
    }
}

@Composable
private fun CloudProviderSurface(
    provider: DirectCloudProvider,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
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
                Text(uiText("Connected"), fontWeight = FontWeight.SemiBold)
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
        Text(uiText(" Back up now"))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRefresh, enabled = busy == null, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Refresh, null)
            Text(uiText(" Backups"))
        }
        onEdit?.let {
            OutlinedButton(onClick = it, enabled = busy == null) { Icon(Icons.Outlined.Edit, uiText("Edit")) }
        }
        OutlinedButton(onClick = onDisconnect, enabled = busy == null) { Icon(Icons.Outlined.Logout, uiText("Disconnect")) }
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
                    uiText(buildString {
                        if (entry.modifiedAt > 0L) append(DateFormat.getDateTimeInstance().format(Date(entry.modifiedAt)))
                        if (entry.sizeBytes > 0L) {
                            if (isNotEmpty()) append(" • ")
                            append(readableDirectBytes(entry.sizeBytes))
                        }
                    }.ifBlank { "Portable Xylune backup" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onPreview(entry) }, enabled = busy == null) { Text(uiText("Preview")) }
            IconButton(onClick = { onDelete(entry) }, enabled = busy == null) {
                Icon(Icons.Outlined.DeleteOutline, uiText("Delete backup"))
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
internal fun WebDavConfigDialog(
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
        title = { Text(uiText("WebDAV / Nextcloud")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Enter the exact HTTPS URL of a dedicated Xylune folder. For Nextcloud this normally ends with /remote.php/dav/files/USERNAME/Xylune/."))
                OutlinedTextField(label, { label = it }, label = { Text(uiText("Label")) }, singleLine = true)
                OutlinedTextField(url, { url = it }, label = { Text(uiText("WebDAV folder URL")) }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text(uiText("Username")) }, singleLine = true)
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text(uiText("Password or app password")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text(uiText("Credentials are encrypted with Android Keystore and are never included in Xylune backups."), style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(onClick = { onSave(WebDavCloudConfig(label, url, username, password)) }) { Text(uiText("Save and test")) }
        },
    )
}

@Composable
internal fun S3ConfigDialog(
    existing: S3CloudConfig?,
    onDismiss: () -> Unit,
    onSave: (S3CloudConfig) -> Unit,
) {
    var label by remember(existing) { mutableStateOf(existing?.label.orEmpty()) }
    var endpoint by remember(existing) { mutableStateOf(existing?.endpoint.orEmpty()) }
    var region by remember(existing) { mutableStateOf(existing?.region ?: "us-east-1") }
    var bucket by remember(existing) { mutableStateOf(existing?.bucket.orEmpty()) }
    var prefix by remember(existing) { mutableStateOf(existing?.prefix ?: "xylune") }
    var accessKey by remember(existing) { mutableStateOf(existing?.accessKeyId.orEmpty()) }
    var secretKey by remember(existing) { mutableStateOf(existing?.secretAccessKey.orEmpty()) }
    var sessionToken by remember(existing) { mutableStateOf(existing?.sessionToken.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("S3-compatible storage")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text(uiText("Label")) }, singleLine = true)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(uiText("HTTPS endpoint")) }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(region, { region = it }, label = { Text(uiText("Region")) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bucket, { bucket = it }, label = { Text(uiText("Bucket")) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(prefix, { prefix = it }, label = { Text(uiText("Prefix")) }, singleLine = true)
                OutlinedTextField(accessKey, { accessKey = it }, label = { Text(uiText("Access key ID")) }, singleLine = true)
                OutlinedTextField(
                    secretKey,
                    { secretKey = it },
                    label = { Text(uiText("Secret access key")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    sessionToken,
                    { sessionToken = it },
                    label = { Text(uiText("Session token (optional)")) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Text(uiText("Use a key restricted to this bucket and prefix. Credentials remain encrypted on-device."), style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(uiText("Cancel")) } },
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
            }) { Text(uiText("Save and test")) }
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
