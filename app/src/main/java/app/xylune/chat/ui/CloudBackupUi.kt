package app.xylune.chat.ui

import android.accounts.Account
import android.content.ClipData
import android.content.ClipboardManager
import android.app.Activity
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
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.BuildConfig
import app.xylune.chat.transfer.ArchiveOptions
import app.xylune.chat.transfer.GoogleDriveAuthorizationFailure
import app.xylune.chat.transfer.GoogleDriveAuthorizationFailureKind
import app.xylune.chat.transfer.describeGoogleDriveAuthorizationFailure
import app.xylune.chat.transfer.CloudBackupEntry
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

internal enum class GoogleAuthorizationResultRoute {
    PARSE_RESULT,
    CANCELLED,
    MISSING_RESULT,
}

internal fun googleAuthorizationResultRoute(resultCode: Int, hasData: Boolean): GoogleAuthorizationResultRoute =
    when {
        hasData -> GoogleAuthorizationResultRoute.PARSE_RESULT
        resultCode == Activity.RESULT_CANCELED -> GoogleAuthorizationResultRoute.CANCELLED
        else -> GoogleAuthorizationResultRoute.MISSING_RESULT
    }

private sealed interface GoogleBackupAction {
    data object Connect : GoogleBackupAction
    data object Save : GoogleBackupAction
    data object List : GoogleBackupAction
    data class Open(val entry: CloudBackupEntry) : GoogleBackupAction
    data class Delete(val entry: CloudBackupEntry) : GoogleBackupAction
}

@Composable
internal fun CloudBackupTargets(
    viewModel: ChatViewModel,
    options: ArchiveOptions,
    password: String,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val authorizationClient = remember(context) { Identity.getAuthorizationClient(context) }
    val driveScopes = remember { listOf(Scope(Scopes.DRIVE_APPFOLDER)) }
    var folderLabel by remember { mutableStateOf(viewModel.connectedCloudFolderLabel()) }
    var folderBackups by remember { mutableStateOf<List<CloudBackupEntry>>(emptyList()) }
    var driveBackups by remember { mutableStateOf<List<CloudBackupEntry>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingGoogleAction by remember { mutableStateOf<GoogleBackupAction?>(null) }
    var googleConnected by remember { mutableStateOf(false) }
    var googleAccount by remember { mutableStateOf<Account?>(null) }
    var googleAccountLabel by remember { mutableStateOf<String?>(null) }
    var driveError by remember { mutableStateOf<String?>(null) }
    var driveAuthorizationFailure by remember { mutableStateOf<GoogleDriveAuthorizationFailure?>(null) }
    var deleteTarget by remember { mutableStateOf<CloudBackupEntry?>(null) }

    fun recordAuthorizationFailure(error: Throwable) {
        val failure = context.describeGoogleDriveAuthorizationFailure(
            error = error,
            sourceRepository = BuildConfig.SOURCE_REPOSITORY,
        )
        driveAuthorizationFailure = failure
        driveError = failure.userMessage
    }

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
                GoogleBackupAction.Connect, GoogleBackupAction.List -> "drive-list"
                GoogleBackupAction.Save -> "drive-save"
                is GoogleBackupAction.Open -> "drive-open-${action.entry.id}"
                is GoogleBackupAction.Delete -> "drive-delete-${action.entry.id}"
            }
            driveError = null
            driveAuthorizationFailure = null
            runCatching {
                when (action) {
                    GoogleBackupAction.Connect, GoogleBackupAction.List -> {
                        driveBackups = viewModel.listGoogleDriveBackups(accessToken)
                    }
                    GoogleBackupAction.Save -> {
                        viewModel.writeGoogleDriveBackup(accessToken, options, password)
                        driveBackups = viewModel.listGoogleDriveBackups(accessToken)
                    }
                    is GoogleBackupAction.Open -> {
                        val uri = viewModel.downloadGoogleDriveBackup(accessToken, action.entry)
                        viewModel.receivePortableArchive(uri)
                    }
                    is GoogleBackupAction.Delete -> {
                        viewModel.deleteGoogleDriveBackup(accessToken, action.entry)
                        driveBackups = driveBackups.filterNot { it.id == action.entry.id }
                    }
                }
            }.onSuccess {
                googleConnected = true
                if (action == GoogleBackupAction.Save) viewModel.postNotice("Backup saved to Google Drive")
            }.onFailure { error ->
                val message = error.message ?: "Google Drive backup failed"
                driveError = message
                if (message.contains("authorization", ignoreCase = true) || message.contains("401")) {
                    googleConnected = false
                }
            }
            busy = null
        }
    }

    fun acceptAuthorization(action: GoogleBackupAction, authorization: AuthorizationResult) {
        driveAuthorizationFailure = null
        val token = authorization.accessToken
        if (token.isNullOrBlank()) {
            driveError = "Google returned no access token. Check that this app's package name and signing certificate are registered for its OAuth client."
            googleConnected = false
            return
        }
        val account = authorization.toGoogleSignInAccount()
        googleAccount = account?.account
        googleAccountLabel = account?.email ?: account?.displayName ?: account?.account?.name
        googleConnected = true
        performGoogle(action, token)
    }

    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pendingGoogleAction
        pendingGoogleAction = null
        if (action == null) return@rememberLauncherForActivityResult
        val data = result.data
        when (googleAuthorizationResultRoute(result.resultCode, data != null)) {
            GoogleAuthorizationResultRoute.PARSE_RESULT -> {
                runCatching { authorizationClient.getAuthorizationResultFromIntent(requireNotNull(data)) }
                    .onSuccess { acceptAuthorization(action, it) }
                    .onFailure(::recordAuthorizationFailure)
            }
            GoogleAuthorizationResultRoute.CANCELLED -> {
                driveError = "Google Drive connection was canceled."
            }
            GoogleAuthorizationResultRoute.MISSING_RESULT -> {
                driveError = "Google Drive authorization returned no result (code ${result.resultCode})."
            }
        }
    }

    fun authorizeGoogle(action: GoogleBackupAction, selectAccount: Boolean = false) {
        if (busy != null || pendingGoogleAction != null) return
        driveError = null
        driveAuthorizationFailure = null
        pendingGoogleAction = action
        val builder = AuthorizationRequest.builder().setRequestedScopes(driveScopes)
        if (selectAccount) {
            builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        } else {
            googleAccount?.let(builder::setAccount)
        }
        authorizationClient.authorize(builder.build())
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        pendingGoogleAction = null
                        driveError = "Google Drive authorization could not be opened."
                    } else {
                        googleAuthorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                } else {
                    pendingGoogleAction = null
                    acceptAuthorization(action, result)
                }
            }
            .addOnFailureListener { error ->
                pendingGoogleAction = null
                googleConnected = false
                recordAuthorizationFailure(error)
            }
    }

    DisposableEffect(authorizationClient) {
        var active = true
        val request = AuthorizationRequest.builder().setRequestedScopes(driveScopes).build()
        authorizationClient.authorize(request)
            .addOnSuccessListener { result ->
                if (active && !result.hasResolution() && !googleConnected && busy == null) {
                    acceptAuthorization(GoogleBackupAction.Connect, result)
                }
            }
            .addOnFailureListener { /* No prior grant: stay disconnected without showing an error. */ }
        onDispose { active = false }
    }

    fun disconnectGoogle() {
        val account = googleAccount
        if (account == null) {
            googleConnected = false
            googleAccountLabel = null
            driveBackups = emptyList()
            return
        }
        busy = "drive-disconnect"
        val request = RevokeAccessRequest.builder()
            .setAccount(account)
            .setScopes(driveScopes)
            .build()
        authorizationClient.revokeAccess(request)
            .addOnSuccessListener {
                googleConnected = false
                googleAccount = null
                googleAccountLabel = null
                driveBackups = emptyList()
                driveError = null
                driveAuthorizationFailure = null
                busy = null
            }
            .addOnFailureListener {
                driveError = it.message ?: "Google Drive could not be disconnected"
                busy = null
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
                viewModel.postNotice("Xylune cloud folder connected")
            }
            .onFailure { viewModel.postNotice(it.message ?: "Could not connect the cloud folder") }
    }

    TransferHeading(
        title = "Private cloud targets",
        subtitle = "Choose a scoped Android folder, an OAuth app folder, WebDAV/Nextcloud, or an S3-compatible bucket prefix. Xylune avoids account-wide cloud access.",
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
                    Text(uiText("Selected cloud folder"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText(folderLabel?.let { "Connected: $it" }
                            ?: "Works with Drive, OneDrive, Dropbox, Nextcloud, USB, local storage, and other Android document providers."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                uiText("Android grants Xylune persistent access only to the folder you select. Create or choose a dedicated Xylune folder; no account-wide permission is requested."),
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
                    Text(uiText(if (folderLabel == null) " Connect folder" else " Change folder"))
                }
                if (folderLabel != null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.disconnectCloudFolder()
                            folderLabel = null
                            folderBackups = emptyList()
                        },
                        enabled = busy == null,
                    ) { Icon(Icons.Outlined.DeleteOutline, null) }
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
                    Text(uiText(" Back up now"))
                }
                OutlinedButton(onClick = { refreshFolderBackups() }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(uiText(" Show backups"))
                }
                CloudBackupList(
                    entries = folderBackups,
                    busy = busy,
                    onOpen = { entry ->
                        runCatching { viewModel.openConnectedFolderBackup(entry) }
                            .onSuccess(viewModel::receivePortableArchive)
                            .onFailure { viewModel.postNotice(it.message ?: "Could not open backup") }
                    },
                    onDelete = { deleteTarget = it },
                )
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
                    Text(uiText("Google Drive"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Uses only Drive's hidden appDataFolder. Connect once, then create, browse, preview, and restore backups without repeating the consent flow."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (!googleConnected) {
                Button(
                    onClick = { authorizeGoogle(GoogleBackupAction.Connect, selectAccount = true) },
                    enabled = enabled && busy == null && pendingGoogleAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (pendingGoogleAction != null) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Person, null)
                    Text(uiText(" Connect Google Drive"))
                }
                Text(
                    uiText("Xylune asks for the non-sensitive drive.appdata scope only. The backup files remain hidden from normal Drive browsing and from other apps."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudDone, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(uiText("Connected"), fontWeight = FontWeight.SemiBold)
                            Text(
                                uiText(googleAccountLabel ?: "Google account selected"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Button(
                    onClick = { authorizeGoogle(GoogleBackupAction.Save) },
                    enabled = enabled && busy == null && pendingGoogleAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy == "drive-save") CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Backup, null)
                    Text(uiText(if (busy == "drive-save") " Creating and uploading…" else " Back up now"))
                }
                OutlinedButton(
                    onClick = { authorizeGoogle(GoogleBackupAction.List) },
                    enabled = busy == null && pendingGoogleAction == null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Refresh, null)
                    Text(uiText(" Refresh backup list"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { authorizeGoogle(GoogleBackupAction.Connect, selectAccount = true) },
                        enabled = busy == null && pendingGoogleAction == null,
                        modifier = Modifier.weight(1f),
                    ) { Text(uiText("Switch account")) }
                    OutlinedButton(
                        onClick = ::disconnectGoogle,
                        enabled = busy == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Logout, null)
                        Text(uiText(" Disconnect"))
                    }
                }
                CloudBackupList(
                    entries = driveBackups,
                    busy = busy,
                    onOpen = { authorizeGoogle(GoogleBackupAction.Open(it)) },
                    onDelete = { deleteTarget = it },
                )
            }

            driveError?.let { message ->
                val failure = driveAuthorizationFailure
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            uiText(failure?.title ?: "Google Drive error"),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (failure?.kind == GoogleDriveAuthorizationFailureKind.UNREGISTERED_ON_API_CONSOLE) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .55f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(uiText("Package"), style = MaterialTheme.typography.labelSmall)
                                    Text(failure.identity.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text(uiText("Signing SHA-1"), style = MaterialTheme.typography.labelSmall)
                                    Text(failure.identity.signingSha1, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                        ClipData.newPlainText("Xylune Google Drive OAuth setup", failure.copyableSetupDetails()),
                                    )
                                    viewModel.postNotice("Google Drive registration details copied")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(uiText("Copy setup details")) }
                            failure.setupGuideUrl?.let { guide ->
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(guide) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text(uiText("Open setup guide")) }
                            }
                        }
                        OutlinedButton(
                            onClick = { authorizeGoogle(GoogleBackupAction.Connect, selectAccount = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(uiText("Reconnect")) }
                    }
                }
            }
        }
    }

    deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(uiText("Delete cloud backup?")) },
            text = { Text(uiText("${entry.name} will be permanently removed from its cloud provider.")) },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(uiText("Cancel")) }
            },
            confirmButton = {
                Button(onClick = {
                    deleteTarget = null
                    when (entry.provider) {
                        app.xylune.chat.transfer.CloudBackupProvider.SCOPED_FOLDER -> scope.launch {
                            busy = "folder-delete-${entry.id}"
                            runCatching { viewModel.deleteConnectedFolderBackup(entry) }
                                .onSuccess { folderBackups = folderBackups.filterNot { it.id == entry.id } }
                                .onFailure { viewModel.postNotice(it.message ?: "Could not delete backup") }
                            busy = null
                        }
                        app.xylune.chat.transfer.CloudBackupProvider.GOOGLE_DRIVE_APP_DATA ->
                            authorizeGoogle(GoogleBackupAction.Delete(entry))
                        else -> Unit
                    }
                }) { Text(uiText("Delete")) }
            },
        )
    }

    DirectCloudProviderTargets(
        viewModel = viewModel,
        options = options,
        password = password,
        enabled = enabled,
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            uiText("Portable cloud backups can include chats, app settings, organization, and optional Linux root filesystems. Passwordless backups are allowed after an explicit warning; API keys, OAuth sessions, provider authorization headers, cloud grants, and database encryption keys are excluded."),
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
    onDelete: ((CloudBackupEntry) -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    HorizontalDivider()
    entries.take(100).forEach { entry ->
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
                            append(readableBytes(entry.sizeBytes))
                        }
                    }.ifBlank { "Portable Xylune backup" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { onOpen(entry) }, enabled = busy == null) { Text(uiText("Preview")) }
            onDelete?.let { delete ->
                IconButton(onClick = { delete(entry) }, enabled = busy == null) {
                    Icon(Icons.Outlined.DeleteOutline, uiText("Delete backup"))
                }
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
