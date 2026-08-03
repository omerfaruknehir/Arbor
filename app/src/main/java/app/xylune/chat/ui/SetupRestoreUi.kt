package app.xylune.chat.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.transfer.XYLUNE_BACKUP_MIME
import app.xylune.chat.transfer.XYLUNE_CHAT_MIME
import app.xylune.chat.transfer.CloudBackupEntry
import app.xylune.chat.transfer.CloudBackupProvider
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

private enum class SetupCloudSource { CHOOSE, FOLDER, GOOGLE_DRIVE }

@Composable
internal fun SetupRestoreActions(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    var cloudDialogOpen by remember { mutableStateOf(false) }
    var cloudSource by remember { mutableStateOf(SetupCloudSource.CHOOSE) }
    var entries by remember { mutableStateOf<List<CloudBackupEntry>>(emptyList()) }
    var googleAccessToken by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun showBackups(source: SetupCloudSource, values: List<CloudBackupEntry>) {
        cloudSource = source
        entries = values
        error = if (values.isEmpty()) "No Xylune backups were found in this app-only location." else null
        cloudDialogOpen = true
    }

    fun loadGoogleBackups(accessToken: String) {
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

    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            busy = false
            return@rememberLauncherForActivityResult
        }
        runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data ?: Intent()) }
            .onSuccess { authorization ->
                val token = authorization.accessToken
                if (token.isNullOrBlank()) {
                    busy = false
                    error = "Google Drive authorization returned no access token"
                } else loadGoogleBackups(token)
            }
            .onFailure {
                busy = false
                error = it.message ?: "Google Drive authorization failed"
            }
    }

    fun authorizeGoogleDrive() {
        busy = true
        error = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        busy = false
                        error = "Google Drive authorization could not be opened"
                    } else {
                        googleAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val token = result.accessToken
                    if (token.isNullOrBlank()) {
                        busy = false
                        error = "Google Drive authorization returned no access token"
                    } else loadGoogleBackups(token)
                }
            }
            .addOnFailureListener {
                busy = false
                error = it.message ?: "Google Drive authorization failed"
            }
    }

    val localPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::receivePortableArchive)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            runCatching {
                viewModel.connectCloudFolder(uri)
                viewModel.listConnectedFolderBackups()
            }.onSuccess { showBackups(SetupCloudSource.FOLDER, it) }
                .onFailure { error = it.message ?: "Could not read the selected cloud folder" }
            busy = false
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Already use Xylune?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Restore chats, app settings, provider configuration, projects, prompt profiles, and optional Linux environments before continuing setup. Credentials are never stored in backups.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        localPicker.launch(
                            arrayOf(XYLUNE_BACKUP_MIME, XYLUNE_CHAT_MIME, "application/octet-stream", "application/zip"),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FileOpen, null)
                    Text(" Local", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = {
                        cloudSource = SetupCloudSource.CHOOSE
                        entries = emptyList()
                        error = null
                        cloudDialogOpen = true
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.Cloud, null)
                    Text(" Cloud", Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    if (cloudDialogOpen) {
        XyluneAlertDialog(
            onDismissRequest = { if (!busy) cloudDialogOpen = false },
            title = { Text(if (cloudSource == SetupCloudSource.CHOOSE) "Restore from cloud" else "Choose a backup") },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (cloudSource == SetupCloudSource.CHOOSE) {
                        Text(
                            "Both options use app-only storage. Xylune never asks to browse an entire cloud account.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = ::authorizeGoogleDrive,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Cloud, null)
                            Text(" Google Drive app storage", Modifier.padding(start = 6.dp))
                        }
                        OutlinedButton(
                            onClick = { folderPicker.launch(viewModel.connectedCloudFolderUri()) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.FolderOpen, null)
                            Text(" Choose an app backup folder", Modifier.padding(start = 6.dp))
                        }
                        if (viewModel.connectedCloudFolderUri() != null) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        error = null
                                        runCatching { viewModel.listConnectedFolderBackups() }
                                            .onSuccess { showBackups(SetupCloudSource.FOLDER, it) }
                                            .onFailure { error = it.message ?: "Could not read the connected cloud folder" }
                                        busy = false
                                    }
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Refresh, null)
                                Text(" Use connected folder", Modifier.padding(start = 6.dp))
                            }
                        }
                    } else {
                        Text(
                            when (cloudSource) {
                                SetupCloudSource.FOLDER -> viewModel.connectedCloudFolderLabel()?.let { "Folder: $it" } ?: "Selected app backup folder"
                                SetupCloudSource.GOOGLE_DRIVE -> "Google Drive hidden Xylune app storage"
                                SetupCloudSource.CHOOSE -> ""
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                            busy = true
                                            error = null
                                            runCatching {
                                                when (entry.provider) {
                                                    CloudBackupProvider.SCOPED_FOLDER -> viewModel.openConnectedFolderBackup(entry)
                                                    CloudBackupProvider.GOOGLE_DRIVE_APP_DATA -> {
                                                        val token = requireNotNull(googleAccessToken) { "Google Drive authorization expired" }
                                                        viewModel.downloadGoogleDriveBackup(token, entry)
                                                    }
                                                }
                                            }.onSuccess { uri ->
                                                cloudDialogOpen = false
                                                viewModel.receivePortableArchive(uri)
                                            }.onFailure { error = it.message ?: "Could not open the cloud backup" }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                ) { Text("Preview") }
                            }
                        }
                    }
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                            Text("Loading backups…")
                        }
                    }
                    error?.let {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
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
                            error = null
                        },
                        enabled = !busy,
                    ) { Text("Back") }
                }
            },
            confirmButton = {
                TextButton(onClick = { cloudDialogOpen = false }, enabled = !busy) { Text("Close") }
            },
        )
    }
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
