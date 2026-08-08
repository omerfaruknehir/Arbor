package app.xylune.chat.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.xylune.chat.transfer.XYLUNE_BACKUP_EXTENSION
import app.xylune.chat.transfer.XYLUNE_BACKUP_MIME
import app.xylune.chat.transfer.XYLUNE_CHAT_MIME
import app.xylune.chat.transfer.ArchiveKind
import app.xylune.chat.transfer.ArchiveOptions
import app.xylune.chat.transfer.IncomingArchiveState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupSettingsPage(viewModel: ChatViewModel) = SettingsPage {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var includeAppSettings by remember { mutableStateOf(true) }
    var includeAttachments by remember { mutableStateOf(true) }
    var includePrivateData by remember { mutableStateOf(true) }
    var includeSystemPrompt by remember { mutableStateOf(true) }
    var includeLinuxEnvironments by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val passwordsMatch = password == confirmPassword
    val backupOptions = ArchiveOptions(
        includeAttachments = includeAttachments,
        includeReasoning = includePrivateData,
        includeToolData = includePrivateData,
        includeSystemPrompt = includeSystemPrompt,
        includeRequestMetadata = includePrivateData,
        includeLinuxEnvironments = includeLinuxEnvironments,
        includeAppSettings = includeAppSettings,
    )
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(XYLUNE_BACKUP_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                viewModel.writePortableBackup(
                    uri = uri,
                    options = backupOptions,
                    password = password,
                )
            }.onSuccess {
                viewModel.postNotice("Backup saved")
            }.onFailure {
                viewModel.postNotice(it.message ?: "Backup failed")
            }
            busy = false
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(viewModel::receivePortableArchive)
    }

    TransferHeading(
        title = "Cloud & file backup",
        subtitle = "The Android document picker can save directly to Google Drive, OneDrive, Dropbox, Nextcloud, a USB drive, or local storage. Xylune does not upload through a hidden server.",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(uiText("Portable Xylune backup"), fontWeight = FontWeight.SemiBold)
                    Text(
                        uiText("Chats, branches, app configuration, organization, metadata, and optional attachments. API keys and OAuth sessions are deliberately excluded."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TransferSwitch("Include app settings and configuration", includeAppSettings) { includeAppSettings = it }
            if (includeAppSettings) {
                Text(
                    uiText("Includes theme, UI behavior, new-chat defaults, provider endpoints/models, projects, prompt profiles, and automation settings. Credentials, OAuth sessions, provider authorization headers, cloud grants, drafts, and navigation state stay excluded."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TransferSwitch("Include attachments", includeAttachments) { includeAttachments = it }
            TransferSwitch("Include reasoning, tool traces, and request metadata", includePrivateData) { includePrivateData = it }
            TransferSwitch("Include custom system prompts", includeSystemPrompt) { includeSystemPrompt = it }
            TransferSwitch("Include installed Linux environments", includeLinuxEnvironments) { includeLinuxEnvironments = it }
            if (includeLinuxEnvironments) {
                Text(
                    uiText("Xylune includes each installed root filesystem, packages, and configuration. Permissions, symbolic links, and hard links are preserved. This can make the backup several gigabytes."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    PasswordSection(
        password = password,
        confirmPassword = confirmPassword,
        onPassword = { password = it },
        onConfirmPassword = { confirmPassword = it },
        passwordsMatch = passwordsMatch,
        unencryptedLabel = "No password: backup remains readable to anyone who gets the file. This is allowed; choose a trusted destination.",
    )

    CloudBackupTargets(
        viewModel = viewModel,
        options = backupOptions,
        password = password,
        enabled = !busy && passwordsMatch,
    )

    Button(
        onClick = {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            backupLauncher.launch("Xylune-backup-$stamp$XYLUNE_BACKUP_EXTENSION")
        },
        enabled = !busy && passwordsMatch,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        else Icon(Icons.Outlined.CloudUpload, null)
        Text(uiText(" Save backup"), Modifier.padding(start = 4.dp))
    }

    OutlinedButton(
        onClick = {
            restoreLauncher.launch(arrayOf(XYLUNE_BACKUP_MIME, XYLUNE_CHAT_MIME, "application/octet-stream", "application/zip"))
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.FileOpen, null)
        Text(uiText(" Preview and import a backup"), Modifier.padding(start = 6.dp))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            uiText("Imports are non-destructive: every chat is created as a separate copy. Existing chats are never overwritten. Provider credentials are not imported, so reconnect the required provider before continuing an imported chat."),
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ChatShareDialog(
    viewModel: ChatViewModel,
    conversationId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var includeAttachments by remember { mutableStateOf(true) }
    var includeReasoning by remember { mutableStateOf(false) }
    var includeToolData by remember { mutableStateOf(false) }
    var includeSystemPrompt by remember { mutableStateOf(false) }
    var includeRequestMetadata by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val passwordsMatch = password == confirmPassword

    XyluneAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(uiText("Share portable chat")) },
        text = {
            Column(
                Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, null)
                        Text(
                            uiText("Safe defaults share visible messages and attachments. Hidden reasoning, tool diagnostics, prompts, and request snapshots stay excluded until you enable them."),
                            Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TransferSwitch("Attachments", includeAttachments) { includeAttachments = it }
                TransferSwitch("Reasoning", includeReasoning) { includeReasoning = it }
                TransferSwitch("Tool traces and working timeline", includeToolData) { includeToolData = it }
                TransferSwitch("Custom system prompt", includeSystemPrompt) { includeSystemPrompt = it }
                TransferSwitch("Request snapshots and extracted attachment metadata", includeRequestMetadata) { includeRequestMetadata = it }
                HorizontalDivider()
                PasswordSection(
                    password = password,
                    confirmPassword = confirmPassword,
                    onPassword = { password = it },
                    onConfirmPassword = { confirmPassword = it },
                    passwordsMatch = passwordsMatch,
                    unencryptedLabel = "No password: the recipient can open the file immediately. This is allowed, but anyone with the file can read the included content.",
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text(uiText("Cancel")) }
        },
        confirmButton = {
            Button(
                enabled = !busy && passwordsMatch,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        runCatching {
                            viewModel.createPortableChatShare(
                                conversationId = conversationId,
                                options = ArchiveOptions(
                                    includeAttachments = includeAttachments,
                                    includeReasoning = includeReasoning,
                                    includeToolData = includeToolData,
                                    includeSystemPrompt = includeSystemPrompt,
                                    includeRequestMetadata = includeRequestMetadata,
                                ),
                                password = password,
                            )
                        }.onSuccess { uri ->
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = XYLUNE_CHAT_MIME
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "Xylune chat", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share Xylune chat"))
                            onDismiss()
                        }.onFailure {
                            error = it.message ?: "Could not create the chat file"
                        }
                        busy = false
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(end = 6.dp), strokeWidth = 2.dp)
                else Icon(Icons.Outlined.Share, null)
                Text(uiText(" Share"))
            }
        },
    )
}

@Composable
internal fun IncomingArchiveDialog(
    viewModel: ChatViewModel,
    state: IncomingArchiveState,
) {
    var password by remember(state.uri) { mutableStateOf("") }
    val preview = state.preview
    XyluneAlertDialog(
        onDismissRequest = { if (!state.importing) viewModel.dismissIncomingArchive() },
        title = {
            Text(
                uiText(when {
                    preview?.kind == ArchiveKind.BACKUP -> "Import Xylune backup"
                    preview?.kind == ArchiveKind.CHAT -> "Open shared Xylune chat"
                    else -> "Open Xylune archive"
                }),
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.passwordRequired && preview == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Text(uiText("This file is encrypted. Enter its password to inspect it."), Modifier.padding(start = 10.dp))
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(uiText("Archive password")) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.unlockIncomingArchive(password) },
                        enabled = password.isNotEmpty() && !state.importing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.LockOpen, null)
                        Text(uiText(" Unlock preview"), Modifier.padding(start = 6.dp))
                    }
                }
                preview?.let { value ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(value.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                uiText(buildString {
                                    append("${value.conversationCount} chat${if (value.conversationCount == 1) "" else "s"} • ${value.messageCount} messages • ${value.attachmentCount} attachments")
                                    if (value.linuxEnvironmentCount > 0) {
                                        append(" • ${value.linuxEnvironmentCount} Linux environment${if (value.linuxEnvironmentCount == 1) "" else "s"}")
                                    }
                                }),
                            )
                            Text(
                                uiText("Created by Xylune ${value.appVersion} • ${if (value.encrypted) "Password protected" else "Not encrypted"}"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(uiText("Included content"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    IncludedRow("Attachments", value.options.includeAttachments)
                    IncludedRow("Reasoning", value.options.includeReasoning)
                    IncludedRow("Tool traces", value.options.includeToolData)
                    IncludedRow("Custom system prompt", value.options.includeSystemPrompt)
                    IncludedRow("Request metadata", value.options.includeRequestMetadata)
                    IncludedRow("App settings and configuration", value.appSettingsIncluded)
                    IncludedRow("Installed Linux environments", value.options.includeLinuxEnvironments)
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            uiText(if (value.appSettingsIncluded) {
                                "Chats are imported as separate copies. Included app settings and organization are applied, but API keys, OAuth sessions, provider authorization headers, and cloud grants are never imported."
                            } else {
                                "Import creates separate local copies. It never replaces an existing chat and does not import API keys or OAuth sessions."
                            }),
                            Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                state.error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.WarningAmber, null)
                            Text(it, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = viewModel::dismissIncomingArchive, enabled = !state.importing) { Text(uiText("Cancel")) }
        },
        confirmButton = {
            if (preview != null) {
                Button(
                    onClick = { viewModel.importIncomingArchive(password) },
                    enabled = !state.importing,
                ) {
                    if (state.importing) CircularProgressIndicator(Modifier.padding(end = 6.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.FileOpen, null)
                    Text(uiText(if (preview.kind == ArchiveKind.CHAT) " Import and continue" else " Import backup"))
                }
            }
        },
    )
}

@Composable
private fun PasswordSection(
    password: String,
    confirmPassword: String,
    onPassword: (String) -> Unit,
    onConfirmPassword: (String) -> Unit,
    passwordsMatch: Boolean,
    unencryptedLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Encryption password (optional)"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text(uiText("Password — leave blank for none")) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (password.isNotEmpty()) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPassword,
                label = { Text(uiText("Confirm password")) },
                visualTransformation = PasswordVisualTransformation(),
                isError = !passwordsMatch,
                supportingText = if (!passwordsMatch) ({ Text(uiText("Passwords do not match")) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, null)
                    Text(unencryptedLabel, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TransferSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IncludedRow(label: String, included: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (included) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            null,
            tint = if (included) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(uiText(if (included) "$label included" else "$label excluded"), Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun TransferHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}