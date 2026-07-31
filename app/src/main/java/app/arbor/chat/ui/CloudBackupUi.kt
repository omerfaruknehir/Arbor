package app.arbor.chat.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.DeleteOutline
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
import app.arbor.chat.transfer.ArchiveOptions
import app.arbor.chat.transfer.CloudBackupEntry
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private sealed interface GoogleBackupAction {
    data object Save : GoogleBackupAction
    data object List : GoogleBackupAction
    data class Open(val entry: CloudBackupEntry) : GoogleBackupAction
}

@Composable
internal fun CloudBackupTargets(
    viewModel: ChatViewModel,
    options: ArchiveOptions,
    password: String,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    var folderLabel by remember { mutableStateOf(viewModel.connectedCloudFolderLabel()) }
    var folderBackups by remember { mutableStateOf<List<CloudBackupEntry>>(emptyList()) }
    var driveBackups by remember { mutableStateOf<List<CloudBackupEntry>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingGoogleAction by remember { mutableStateOf<GoogleBackupAction?>(null) }

    fun refreshFolderBackups() {
        scope.launch {
            runCatching { viewModel.listConnectedFolderBackups() }
                .onSuccess { folderBackups = it }
                .onFailure { viewModel.postNotice(it.message ?: "Could not read the cloud folder") }
        }
    }

    fun performGoogle(action: GoogleBackupAction, accessToken: String) {
        scope.launch {
            busy = when (action) {
                GoogleBackupAction.Save -> "drive-save"
                GoogleBackupAction.List -> "drive-list"
                is GoogleBackupAction.Open -> "drive-open-${action.entry.id}"
            }
            runCatching {
                when (action) {
                    GoogleBackupAction.Save -> {
                        viewModel.writeGoogleDriveBackup(accessToken, options, password)
                        driveBackups = viewModel.listGoogleDriveBackups(accessToken)
                    }
                    GoogleBackupAction.List -> {
                        driveBackups = viewModel.listGoogleDriveBackups(accessToken)
                    }
                    is GoogleBackupAction.Open -> {
                        val uri = viewModel.downloadGoogleDriveBackup(accessToken, action.entry)
                        viewModel.receivePortableArchive(uri)
                    }
                }
            }.onSuccess {
                if (action == GoogleBackupAction.Save) viewModel.postNotice("Backup saved to Google Drive app storage")
            }.onFailure {
                viewModel.postNotice(it.message ?: "Google Drive backup failed")
            }
            busy = null
        }
    }

    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pendingGoogleAction
        pendingGoogleAction = null
        if (result.resultCode != Activity.RESULT_OK || action == null) return@rememberLauncherForActivityResult
        runCatching { authorizationClient.getAuthorizationResultFromIntent(result.data ?: Intent()) }
            .onSuccess { authorization ->
                val token = authorization.accessToken
                if (token.isNullOrBlank()) viewModel.postNotice("Google Drive authorization returned no access token")
                else performGoogle(action, token)
            }
            .onFailure { viewModel.postNotice(it.message ?: "Google Drive authorization failed") }
    }

    fun authorizeGoogle(action: GoogleBackupAction) {
        if (busy != null) return
        pendingGoogleAction = action
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Scopes.DRIVE_APPFOLDER)))
            .build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        pendingGoogleAction = null
                        viewModel.postNotice("Google Drive authorization could not be opened")
                    } else {
                        googleAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    pendingGoogleAction = null
                    val token = result.accessToken
                    if (token.isNullOrBlank()) viewModel.postNotice("Google Drive authorization returned no access token")
                    else performGoogle(action, token)
                }
            }
            .addOnFailureListener {
                pendingGoogleAction = null
                viewModel.postNotice(it.message ?: "Google Drive authorization failed")
            }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { viewModel.connectCloudFolder(uri) }
            .onSuccess {
                folderLabel = viewModel.connectedCloudFolderLabel()
                refreshFolderBackups()
                viewModel.postNotice("Arbor cloud folder connected")
            }
            .onFailure { viewModel.postNotice(it.message ?: "Could not connect the cloud folder") }
    }

    TransferHeading(
        title = "Private cloud targets",
        subtitle = "Arbor uses either a single folder you explicitly choose or Google Drive's hidden appDataFolder. It never requests access to all files in your cloud account.",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("App-only cloud folder", fontWeight = FontWeight.SemiBold)
                    Text(
                        folderLabel?.let { "Connected: $it" }
                            ?: "Works with Google Drive, OneDrive, Dropbox, Nextcloud, USB, and other Android document providers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Android grants Arbor persistent read/write access only to the selected folder. Choose or create a folder named Arbor; no account-wide permission is requested.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { folderPicker.launch(viewModel.connectedCloudFolderUri()) },
                    enabled = enabled && busy == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.FolderOpen, null)
                    Text(if (folderLabel == null) " Connect folder" else " Change folder")
                }
                if (folderLabel != null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.disconnectCloudFolder()
                            folderLabel = null
                            folderBackups = emptyList()
                        },
                        enabled = busy == null,
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, null)
                    }
                }
            }
            if (folderLabel != null) {
                Button(
                    onClick = {
                        scope.launch {
                            busy = "folder-save"
                            runCatching { viewModel.writeConnectedFolderBackup(options, password) }
                                .onSuccess {
                                    viewModel.postNotice("Backup saved to $folderLabel")
                                    folderBackups = viewModel.listConnectedFolderBackups()
                                }
                                .onFailure { viewModel.postNotice(it.message ?: "Cloud-folder backup failed") }
                            busy = null
                        }
                    },
                    enabled = enabled && busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "folder-save") CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.CloudDone, null)
                    Text(" Back up now to this folder")
                }
                OutlinedButton(
                    onClick = { refreshFolderBackups() },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(" Show backups in folder")
                }
                CloudBackupList(folderBackups, busy) { entry ->
                    runCatching { viewModel.openConnectedFolderBackup(entry) }
                        .onSuccess(viewModel::receivePortableArchive)
                        .onFailure { viewModel.postNotice(it.message ?: "Could not open backup") }
                }
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Cloud, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Google Drive app storage", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Uses the non-sensitive drive.appdata scope. Backups live in Drive's hidden Arbor-only appDataFolder and cannot be read by other apps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = { authorizeGoogle(GoogleBackupAction.Save) },
                enabled = enabled && busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy == "drive-save") CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Backup, null)
                Text(" Back up to Google Drive app storage")
            }
            OutlinedButton(
                onClick = { authorizeGoogle(GoogleBackupAction.List) },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Refresh, null)
                Text(" Show Google Drive backups")
            }
            CloudBackupList(driveBackups, busy) { entry -> authorizeGoogle(GoogleBackupAction.Open(entry)) }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Android/Google One app backup is enabled for small, non-secret Arbor preferences. Chats, attachments, and Linux root filesystems use the portable backup targets above because Android's standard app backup is limited to 25 MB. API keys, OAuth sessions, and database encryption keys are excluded everywhere.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CloudBackupList(
    entries: List<CloudBackupEntry>,
    busy: String?,
    onOpen: (CloudBackupEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    HorizontalDivider()
    entries.take(12).forEach { entry ->
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
                            append(readableBytes(entry.sizeBytes))
                        }
                    }.ifBlank { "Portable Arbor backup" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onOpen(entry) }, enabled = busy == null) {
                Text("Preview")
            }
        }
    }
}

private fun readableBytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> "%.1f GiB".format(value.toDouble() / (1024.0 * 1024 * 1024))
    value >= 1024L * 1024 -> "%.1f MiB".format(value.toDouble() / (1024.0 * 1024))
    value >= 1024L -> "%.1f KiB".format(value.toDouble() / 1024.0)
    else -> "$value B"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
