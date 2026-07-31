package app.arbor.chat.ui

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
import app.arbor.chat.transfer.ARBOR_BACKUP_EXTENSION
import app.arbor.chat.transfer.ARBOR_BACKUP_MIME
import app.arbor.chat.transfer.ARBOR_CHAT_MIME
import app.arbor.chat.transfer.ArchiveKind
import app.arbor.chat.transfer.ArchiveOptions
import app.arbor.chat.transfer.IncomingArchiveState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupSettingsPage(viewModel: ChatViewModel) = SettingsPage {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var includeAttachments by remember { mutableStateOf(true) }
    var includePrivateData by remember { mutableStateOf(true) }
    var includeSystemPrompt by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val passwordsMatch = password == confirmPassword
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ARBOR_BACKUP_MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            runCatching {
                viewModel.writePortableBackup(
                    uri = uri,
                    options = ArchiveOptions(
                        includeAttachments = includeAttachments,
                        includeReasoning = includePrivateData,
                        includeToolData = includePrivateData,
                        includeSystemPrompt = includeSystemPrompt,
                        includeRequestMetadata = includePrivateData,
                    ),
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
        subtitle = "The Android document picker can save directly to Google Drive, OneDrive, Dropbox, Nextcloud, a USB drive, or local storage. Arbor does not upload through a hidden server.",
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
                    Text("Portable Arbor backup", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Chats, branches, per-chat settings, metadata, and optional attachments. API keys and OAuth sessions are deliberately excluded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TransferSwitch("Include attachments", includeAttachments) { includeAttachments = it }
            TransferSwitch("Include reasoning, tool traces, and request metadata", includePrivateData) { includePrivateData = it }
            TransferSwitch("Include custom system prompts", includeSystemPrompt) { includeSystemPrompt = it }
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

    Button(
        onClick = {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
            backupLauncher.launch("Arbor-backup-$stamp$ARBOR_BACKUP_EXTENSION")
        },
        enabled = !busy && passwordsMatch,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
        else Icon(Icons.Outlined.CloudUpload, null)
        Text(" Save backup", Modifier.padding(start = 4.dp))
    }

    OutlinedButton(
        onClick = {
            restoreLauncher.launch(arrayOf(ARBOR_BACKUP_MIME, ARBOR_CHAT_MIME, "application/octet-stream", "application/zip"))
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.FileOpen, null)
        Text(" Preview and import a backup", Modifier.padding(start = 6.dp))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "Imports are non-destructive: every chat is created as a separate copy. Existing chats are never overwritten. Provider credentials are not imported, so reconnect the required provider before continuing an imported chat.",
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

    ArborAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Share portable chat") },
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
                            "Safe defaults share visible messages and attachments. Hidden reasoning, tool diagnostics, prompts, and request snapshots stay excluded until you enable them.",
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
            OutlinedButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
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
                                type = ARBOR_CHAT_MIME
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, "Arbor chat", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share Arbor chat"))
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
                Text(" Share")
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
    ArborAlertDialog(
        onDismissRequest = { if (!state.importing) viewModel.dismissIncomingArchive() },
        title = {
            Text(
                when {
                    preview?.kind == ArchiveKind.BACKUP -> "Import Arbor backup"
                    preview?.kind == ArchiveKind.CHAT -> "Open shared Arbor chat"
                    else -> "Open Arbor archive"
                },
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
                        Text("This file is encrypted. Enter its password to inspect it.", Modifier.padding(start = 10.dp))
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Archive password") },
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
                        Text(" Unlock preview", Modifier.padding(start = 6.dp))
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
                            Text("${value.conversationCount} chat${if (value.conversationCount == 1) "" else "s"} • ${value.messageCount} messages • ${value.attachmentCount} attachments")
                            Text(
                                "Created by Arbor ${value.appVersion} • ${if (value.encrypted) "Password protected" else "Not encrypted"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text("Included content", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    IncludedRow("Attachments", value.options.includeAttachments)
                    IncludedRow("Reasoning", value.options.includeReasoning)
                    IncludedRow("Tool traces", value.options.includeToolData)
                    IncludedRow("Custom system prompt", value.options.includeSystemPrompt)
                    IncludedRow("Request metadata", value.options.includeRequestMetadata)
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Text(
                            "Import creates separate local copies. It never replaces an existing chat and does not import API keys or OAuth sessions.",
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
            OutlinedButton(onClick = viewModel::dismissIncomingArchive, enabled = !state.importing) { Text("Cancel") }
        },
        confirmButton = {
            if (preview != null) {
                Button(
                    onClick = { viewModel.importIncomingArchive(password) },
                    enabled = !state.importing,
                ) {
                    if (state.importing) CircularProgressIndicator(Modifier.padding(end = 6.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.FileOpen, null)
                    Text(if (preview.kind == ArchiveKind.CHAT) " Import and continue" else " Import backup")
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
        Text("Encryption password (optional)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = password,
            onValueChange = onPassword,
            label = { Text("Password — leave blank for none") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (password.isNotEmpty()) {
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPassword,
                label = { Text("Confirm password") },
                visualTransformation = PasswordVisualTransformation(),
                isError = !passwordsMatch,
                supportingText = if (!passwordsMatch) ({ Text("Passwords do not match") }) else null,
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
        Text(if (included) "$label included" else "$label excluded", Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TransferHeading(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}