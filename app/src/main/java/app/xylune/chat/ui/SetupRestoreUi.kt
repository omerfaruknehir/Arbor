package app.xylune.chat.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.R
import app.xylune.chat.transfer.XYLUNE_BACKUP_MIME
import app.xylune.chat.transfer.XYLUNE_CHAT_MIME
import app.xylune.chat.transfer.CloudBackupEntry
import app.xylune.chat.transfer.CloudBackupProvider
import app.xylune.chat.transfer.CloudOAuthProvider
import app.xylune.chat.transfer.CloudOAuthState
import app.xylune.chat.transfer.DirectCloudProvider
import app.xylune.chat.transfer.S3CloudConfig
import app.xylune.chat.transfer.WebDavCloudConfig
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class SetupCloudSource { CHOOSE, FOLDER, GOOGLE_DRIVE, ONEDRIVE, DROPBOX, WEBDAV, S3 }

@Composable
internal fun SetupRestoreActions(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val oauthStates by viewModel.cloudOAuthStates.collectAsState()
    val directConfigurations by viewModel.directCloudConfigurations.collectAsState()
    var webDavDialogOpen by remember { mutableStateOf(false) }
    var s3DialogOpen by remember { mutableStateOf(false) }
    var cloudDialogOpen by remember { mutableStateOf(false) }
    var cloudSource by remember { mutableStateOf(SetupCloudSource.CHOOSE) }
    var entries by remember { mutableStateOf<List<CloudBackupEntry>>(emptyList()) }
    var googleAccessToken by remember { mutableStateOf<String?>(null) }
    var pendingOAuthProvider by remember { mutableStateOf<CloudOAuthProvider?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operationMessage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var compactStatus by remember { mutableStateOf<String?>(null) }

    fun beginOperation(message: String) {
        busy = true
        operationMessage = message
        error = null
    }

    fun finishOperation(message: String? = null) {
        busy = false
        operationMessage = message
    }

    fun showBackups(source: SetupCloudSource, values: List<CloudBackupEntry>) {
        cloudSource = source
        entries = values
        error = null
        finishOperation(
            if (values.isEmpty()) {
                "Connected to ${source.setupLabel()}, but no Xylune backups were found."
            } else {
                "Found ${values.size} Xylune backup${if (values.size == 1) "" else "s"} in ${source.setupLabel()}."
            },
        )
        cloudDialogOpen = true
    }

    fun loadGoogleBackups(accessToken: String) {
        googleAccessToken = accessToken
        scope.launch {
            beginOperation("Checking Google Drive app storage…")
            runCatching { viewModel.listGoogleDriveBackups(accessToken) }
                .onSuccess { showBackups(SetupCloudSource.GOOGLE_DRIVE, it) }
                .onFailure {
                    finishOperation()
                    error = it.message ?: "Could not read Google Drive app storage"
                }
        }
    }

    fun loadDirectBackups(provider: DirectCloudProvider, source: SetupCloudSource) {
        scope.launch {
            beginOperation("Checking ${provider.displayName}…")
            runCatching { viewModel.listDirectCloudBackups(provider) }
                .onSuccess { showBackups(source, it) }
                .onFailure {
                    finishOperation()
                    error = it.message ?: "Could not read ${provider.displayName} backups"
                }
        }
    }

    fun connectDirectOAuth(provider: CloudOAuthProvider) {
        when (val state = oauthStates[provider]) {
            is CloudOAuthState.Connected -> {
                loadDirectBackups(provider.directProvider(), provider.setupSource())
                return
            }
            is CloudOAuthState.Unavailable -> {
                error = state.reason
                operationMessage = null
                return
            }
            else -> Unit
        }

        error = null
        pendingOAuthProvider = provider
        operationMessage = "Opening ${provider.displayName} sign-in…"
        runCatching {
            val uri = viewModel.beginDirectCloudOAuth(provider)
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }.onSuccess {
            operationMessage = "Waiting for ${provider.displayName} sign-in to finish…"
        }.onFailure {
            pendingOAuthProvider = null
            operationMessage = null
            error = it.message ?: "Could not open ${provider.displayName} sign-in"
        }
    }

    LaunchedEffect(pendingOAuthProvider, oauthStates) {
        val provider = pendingOAuthProvider ?: return@LaunchedEffect
        when (val state = oauthStates[provider]) {
            is CloudOAuthState.Connected -> {
                pendingOAuthProvider = null
                loadDirectBackups(provider.directProvider(), provider.setupSource())
            }
            is CloudOAuthState.Error -> {
                pendingOAuthProvider = null
                finishOperation()
                error = state.message
            }
            is CloudOAuthState.Unavailable -> {
                pendingOAuthProvider = null
                finishOperation()
                error = state.reason
            }
            is CloudOAuthState.Authorizing -> {
                operationMessage = "Waiting for ${provider.displayName} sign-in to finish…"
            }
            CloudOAuthState.Disconnected, null -> Unit
        }
    }

    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            finishOperation("Google Drive sign-in was cancelled.")
            return@rememberLauncherForActivityResult
        }
        runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data ?: Intent()) }
            .onSuccess { authorization ->
                val token = authorization.accessToken
                if (token.isNullOrBlank()) {
                    finishOperation()
                    error = "Google Drive authorization returned no access token"
                } else {
                    loadGoogleBackups(token)
                }
            }
            .onFailure {
                finishOperation()
                error = it.message ?: "Google Drive authorization failed"
            }
    }

    fun authorizeGoogleDrive() {
        beginOperation("Requesting access to Google Drive app storage…")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        finishOperation()
                        error = "Google Drive authorization could not be opened"
                    } else {
                        operationMessage = "Waiting for Google Drive approval…"
                        googleAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) {
                        finishOperation()
                        error = "Google Drive authorization returned no access token"
                    } else {
                        loadGoogleBackups(token)
                    }
                }
            }
            .addOnFailureListener {
                finishOperation()
                error = it.message ?: "Google Drive authorization failed"
            }
    }

    val localPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            compactStatus = "No backup file selected."
        } else {
            compactStatus = "Opening backup preview…"
            viewModel.receivePortableArchive(uri)
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) {
            finishOperation("Folder selection was cancelled.")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            beginOperation("Checking the selected backup folder…")
            runCatching {
                viewModel.connectCloudFolder(uri)
                viewModel.listConnectedFolderBackups()
            }.onSuccess { showBackups(SetupCloudSource.FOLDER, it) }
                .onFailure {
                    finishOperation()
                    error = it.message ?: "Could not read the selected cloud folder"
                }
        }
    }

    fun openFolderPicker() {
        beginOperation("Opening Android's folder picker…")
        folderPicker.launch(viewModel.connectedCloudFolderUri())
    }

    fun refreshCurrentSource() {
        when (cloudSource) {
            SetupCloudSource.FOLDER -> scope.launch {
                beginOperation("Checking the selected backup folder…")
                runCatching { viewModel.listConnectedFolderBackups() }
                    .onSuccess { showBackups(SetupCloudSource.FOLDER, it) }
                    .onFailure {
                        finishOperation()
                        error = it.message ?: "Could not read the connected cloud folder"
                    }
            }
            SetupCloudSource.GOOGLE_DRIVE -> googleAccessToken?.let(::loadGoogleBackups) ?: authorizeGoogleDrive()
            SetupCloudSource.ONEDRIVE -> loadDirectBackups(DirectCloudProvider.ONEDRIVE, SetupCloudSource.ONEDRIVE)
            SetupCloudSource.DROPBOX -> loadDirectBackups(DirectCloudProvider.DROPBOX, SetupCloudSource.DROPBOX)
            SetupCloudSource.WEBDAV -> loadDirectBackups(DirectCloudProvider.WEBDAV, SetupCloudSource.WEBDAV)
            SetupCloudSource.S3 -> loadDirectBackups(DirectCloudProvider.S3, SetupCloudSource.S3)
            SetupCloudSource.CHOOSE -> Unit
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.size(42.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Cloud, null, Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(uiText("Restore an existing setup"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Optional. Preview a local or cloud backup before importing anything."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        compactStatus = "Opening file picker…"
                        localPicker.launch(
                            arrayOf(XYLUNE_BACKUP_MIME, XYLUNE_CHAT_MIME, "application/octet-stream", "application/zip"),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FileOpen, null, Modifier.size(20.dp))
                    Text(uiText("File"), Modifier.padding(start = 7.dp))
                }
                Button(
                    onClick = {
                        cloudSource = SetupCloudSource.CHOOSE
                        entries = emptyList()
                        operationMessage = null
                        error = null
                        cloudDialogOpen = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Cloud, null, Modifier.size(20.dp))
                    Text(uiText("Cloud"), Modifier.padding(start = 7.dp))
                }
            }
            compactStatus?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (cloudDialogOpen) {
        XyluneAlertDialog(
            onDismissRequest = { if (!busy) cloudDialogOpen = false },
            title = {
                Text(uiText(if (cloudSource == SetupCloudSource.CHOOSE) "Restore a backup" else "Choose a backup"))
            },
            text = {
                Column(
                    Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (cloudSource == SetupCloudSource.CHOOSE) {
                        Text(
                            uiText("Choose where Xylune should look. Every option is limited to app-only storage or a folder you explicitly select."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        SetupCloudAction(
                            title = "Google Drive",
                            subtitle = "Private Xylune app storage",
                            filled = true,
                            enabled = !busy,
                            onClick = ::authorizeGoogleDrive,
                            icon = { SetupProviderIcon(R.drawable.ic_google_drive, uiText("Google Drive")) },
                        )
                        SetupCloudAction(
                            title = "Choose a backup folder",
                            subtitle = "Google Drive, OneDrive, Nextcloud, USB, or local storage through Android",
                            enabled = !busy,
                            onClick = ::openFolderPicker,
                            icon = { SetupVectorIcon(Icons.Outlined.FolderOpen, uiText("Choose folder")) },
                        )
                        SetupCloudAction(
                            title = "OneDrive",
                            subtitle = oauthActionSubtitle(CloudOAuthProvider.ONEDRIVE, oauthStates[CloudOAuthProvider.ONEDRIVE]),
                            enabled = !busy,
                            onClick = { connectDirectOAuth(CloudOAuthProvider.ONEDRIVE) },
                            icon = { SetupProviderIcon(R.drawable.ic_onedrive, uiText("OneDrive")) },
                        )
                        SetupCloudAction(
                            title = "Dropbox",
                            subtitle = oauthActionSubtitle(CloudOAuthProvider.DROPBOX, oauthStates[CloudOAuthProvider.DROPBOX]),
                            enabled = !busy,
                            onClick = { connectDirectOAuth(CloudOAuthProvider.DROPBOX) },
                            icon = { SetupProviderIcon(R.drawable.ic_dropbox, uiText("Dropbox")) },
                        )
                        SetupCloudAction(
                            title = "Nextcloud / WebDAV",
                            subtitle = directConfigurations.webDav?.let { "Configured as ${it.label} • tap to check backups" }
                                ?: "Enter a server folder and credentials",
                            enabled = !busy,
                            onClick = {
                                if (directConfigurations.webDav == null) {
                                    cloudDialogOpen = false
                                    webDavDialogOpen = true
                                } else {
                                    loadDirectBackups(DirectCloudProvider.WEBDAV, SetupCloudSource.WEBDAV)
                                }
                            },
                            icon = { SetupProviderIcon(R.drawable.ic_nextcloud, uiText("Nextcloud / WebDAV")) },
                        )
                        SetupCloudAction(
                            title = "S3-compatible storage",
                            subtitle = directConfigurations.s3?.let { "Configured as ${it.label} • tap to check backups" }
                                ?: "Amazon S3, MinIO, Backblaze B2, or another compatible bucket",
                            enabled = !busy,
                            onClick = {
                                if (directConfigurations.s3 == null) {
                                    cloudDialogOpen = false
                                    s3DialogOpen = true
                                } else {
                                    loadDirectBackups(DirectCloudProvider.S3, SetupCloudSource.S3)
                                }
                            },
                            icon = { SetupVectorIcon(Icons.Outlined.Storage, uiText("S3-compatible storage")) },
                        )
                        if (viewModel.connectedCloudFolderUri() != null) {
                            SetupCloudAction(
                                title = "Use connected folder",
                                subtitle = viewModel.connectedCloudFolderLabel() ?: "Previously selected backup folder",
                                enabled = !busy,
                                onClick = {
                                    cloudSource = SetupCloudSource.FOLDER
                                    refreshCurrentSource()
                                },
                                icon = { SetupVectorIcon(Icons.Outlined.Refresh, uiText("Use connected folder")) },
                            )
                        }
                    } else {
                        Text(
                            cloudSource.setupLabel(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (entries.isEmpty() && !busy) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    uiText("This location is connected, but it does not contain a Xylune backup yet."),
                                    modifier = Modifier.padding(14.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            OutlinedButton(
                                onClick = ::refreshCurrentSource,
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(20.dp))
                                Text(uiText("Check again"), Modifier.padding(start = 8.dp))
                            }
                        }
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        setupBackupMetadata(entry),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            beginOperation("Downloading ${entry.name}…")
                                            runCatching {
                                                when (entry.provider) {
                                                    CloudBackupProvider.SCOPED_FOLDER -> viewModel.openConnectedFolderBackup(entry)
                                                    CloudBackupProvider.GOOGLE_DRIVE_APP_DATA -> {
                                                        val token = requireNotNull(googleAccessToken) {
                                                            "Google Drive authorization expired"
                                                        }
                                                        viewModel.downloadGoogleDriveBackup(token, entry)
                                                    }
                                                    CloudBackupProvider.ONEDRIVE_APP_FOLDER ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.ONEDRIVE, entry)
                                                    CloudBackupProvider.DROPBOX_APP_FOLDER ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.DROPBOX, entry)
                                                    CloudBackupProvider.WEBDAV ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.WEBDAV, entry)
                                                    CloudBackupProvider.S3 ->
                                                        viewModel.downloadDirectCloudBackup(DirectCloudProvider.S3, entry)
                                                }
                                            }.onSuccess { uri ->
                                                finishOperation()
                                                cloudDialogOpen = false
                                                viewModel.postNotice("Backup downloaded. Opening preview…")
                                                viewModel.receivePortableArchive(uri)
                                            }.onFailure { failure ->
                                                finishOperation()
                                                error = failure.message ?: "Could not download and inspect the cloud backup"
                                            }
                                        }
                                    },
                                    enabled = !busy,
                                ) { Text(uiText("Review")) }
                            }
                        }
                    }
                    if (busy) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(uiText(operationMessage ?: "Working…"), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        operationMessage?.let {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = MaterialTheme.shapes.large,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    error?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            dismissButton = {
                if (cloudSource != SetupCloudSource.CHOOSE) {
                    TextButton(
                        onClick = {
                            cloudSource = SetupCloudSource.CHOOSE
                            entries = emptyList()
                            operationMessage = null
                            error = null
                        },
                        enabled = !busy,
                    ) { Text(uiText("Back")) }
                }
            },
            confirmButton = {
                TextButton(onClick = { cloudDialogOpen = false }, enabled = !busy) { Text(uiText("Close")) }
            },
        )
    }

    if (webDavDialogOpen) {
        WebDavConfigDialog(
            existing = directConfigurations.webDav,
            onDismiss = {
                webDavDialogOpen = false
                cloudDialogOpen = true
            },
            onSave = { config: WebDavCloudConfig ->
                runCatching { viewModel.saveWebDavCloud(config) }
                    .onSuccess {
                        webDavDialogOpen = false
                        cloudDialogOpen = true
                        loadDirectBackups(DirectCloudProvider.WEBDAV, SetupCloudSource.WEBDAV)
                    }
                    .onFailure {
                        webDavDialogOpen = false
                        cloudDialogOpen = true
                        error = it.message ?: "Invalid WebDAV configuration"
                    }
            },
        )
    }

    if (s3DialogOpen) {
        S3ConfigDialog(
            existing = directConfigurations.s3,
            onDismiss = {
                s3DialogOpen = false
                cloudDialogOpen = true
            },
            onSave = { config: S3CloudConfig ->
                runCatching { viewModel.saveS3Cloud(config) }
                    .onSuccess {
                        s3DialogOpen = false
                        cloudDialogOpen = true
                        loadDirectBackups(DirectCloudProvider.S3, SetupCloudSource.S3)
                    }
                    .onFailure {
                        s3DialogOpen = false
                        cloudDialogOpen = true
                        error = it.message ?: "Invalid S3 configuration"
                    }
            },
        )
    }
}

@Composable
private fun SetupCloudAction(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    filled: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filled) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
    if (filled) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun SetupProviderIcon(@DrawableRes drawable: Int, description: String) {
    Icon(
        painter = painterResource(drawable),
        contentDescription = description,
        tint = Color.Unspecified,
        modifier = Modifier.size(28.dp),
    )
}

@Composable
private fun SetupVectorIcon(image: ImageVector, description: String) {
    Icon(
        imageVector = image,
        contentDescription = description,
        modifier = Modifier.size(26.dp),
    )
}

private fun oauthActionSubtitle(provider: CloudOAuthProvider, state: CloudOAuthState?): String = when (state) {
    is CloudOAuthState.Connected -> buildString {
        append("Connected")
        state.accountLabel?.takeIf(String::isNotBlank)?.let { append(" as $it") }
        append(" • tap to check backups")
    }
    is CloudOAuthState.Authorizing -> "Waiting for sign-in to finish"
    is CloudOAuthState.Error -> "Connection failed • tap to retry"
    is CloudOAuthState.Unavailable -> "Unavailable in this build • tap for details"
    CloudOAuthState.Disconnected, null -> "Sign in; Xylune only uses its app folder"
}

private fun CloudOAuthProvider.directProvider(): DirectCloudProvider = when (this) {
    CloudOAuthProvider.ONEDRIVE -> DirectCloudProvider.ONEDRIVE
    CloudOAuthProvider.DROPBOX -> DirectCloudProvider.DROPBOX
}

private fun CloudOAuthProvider.setupSource(): SetupCloudSource = when (this) {
    CloudOAuthProvider.ONEDRIVE -> SetupCloudSource.ONEDRIVE
    CloudOAuthProvider.DROPBOX -> SetupCloudSource.DROPBOX
}

private fun SetupCloudSource.setupLabel(): String = when (this) {
    SetupCloudSource.FOLDER -> "the selected backup folder"
    SetupCloudSource.GOOGLE_DRIVE -> "Google Drive app storage"
    SetupCloudSource.ONEDRIVE -> "OneDrive Apps/Xylune"
    SetupCloudSource.DROPBOX -> "Dropbox Xylune App folder"
    SetupCloudSource.WEBDAV -> "Nextcloud / WebDAV"
    SetupCloudSource.S3 -> "S3-compatible storage"
    SetupCloudSource.CHOOSE -> "cloud storage"
}

private fun setupBackupMetadata(entry: CloudBackupEntry): String = buildString {
    if (entry.modifiedAt > 0L) append(DateFormat.getDateTimeInstance().format(Date(entry.modifiedAt)))
    if (entry.sizeBytes > 0L) {
        if (isNotEmpty()) append(" • ")
        append(
            when {
                entry.sizeBytes >= 1024L * 1024 * 1024 -> "%.1f GiB".format(entry.sizeBytes.toDouble() / (1024.0 * 1024 * 1024))
                entry.sizeBytes >= 1024L * 1024 -> "%.1f MiB".format(entry.sizeBytes.toDouble() / (1024.0 * 1024))
                entry.sizeBytes >= 1024L -> "%.1f KiB".format(entry.sizeBytes.toDouble() / 1024.0)
                else -> "${entry.sizeBytes} B"
            },
        )
    }
}.ifBlank { "Portable Xylune backup" }
