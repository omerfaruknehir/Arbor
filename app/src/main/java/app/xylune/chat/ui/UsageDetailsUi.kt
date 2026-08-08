package app.xylune.chat.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.xylune.chat.XyluneApplication
import app.xylune.chat.data.AttachmentEntity
import app.xylune.chat.data.GenerationUsageEntity
import app.xylune.chat.data.MessageEntity
import app.xylune.chat.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

internal data class ConversationUsageSnapshot(
    val requestCount: Int = 0,
    val inputTokens: Long = 0,
    val cachedInputTokens: Long = 0,
    val outputTokens: Long = 0,
    val costMicros: Long = 0,
    val unknownCostCalls: Int = 0,
)

@Composable
internal fun MessageContextMenu(message: MessageEntity) {
    if (message.role != MessageRole.USER && message.role != MessageRole.ASSISTANT) return
    var open by remember(message.nodeId) { mutableStateOf(false) }
    var showUsage by remember(message.nodeId) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = remember(context) { (context.applicationContext as XyluneApplication).container }

    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.MoreVert, uiText("Message actions"), Modifier.size(18.dp))
        }
        XyluneDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (message.role == MessageRole.ASSISTANT) {
                DropdownMenuItem(
                    text = { Text(uiText("Usage details")) },
                    leadingIcon = { Icon(Icons.Outlined.DataUsage, null) },
                    onClick = {
                        open = false
                        showUsage = true
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(uiText("Share message")) },
                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                onClick = {
                    open = false
                    scope.launch {
                        val attachments = withContext(Dispatchers.IO) {
                            container.database.attachmentDao().forMessage(message.nodeId)
                        }
                        shareMessage(context, message, attachments)
                    }
                },
            )
        }
    }
    if (showUsage) {
        MessageUsageDialog(message = message, onDismiss = { showUsage = false })
    }
}

@Composable
internal fun MessageUsageDialog(
    message: MessageEntity,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as XyluneApplication).container }
    val calls by produceState<List<GenerationUsageEntity>?>(initialValue = null, message.nodeId) {
        value = withContext(Dispatchers.IO) { container.repository.generationUsage(message.nodeId) }
    }
    XyluneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Usage details")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UsageMetric("Provider", message.providerId ?: "—")
                UsageMetric("Model", message.modelId ?: "—")
                UsageMetric("Input tokens", formatTokenCount(message.inputTokens))
                UsageMetric("Cached input", formatTokenCount(message.cachedInputTokens))
                UsageMetric("Non-cached input", formatTokenCount((message.inputTokens - message.cachedInputTokens).coerceAtLeast(0)))
                UsageMetric("Output / billed tokens", formatTokenCount(message.outputTokens))
                UsageMetric("Total tokens", formatTokenCount(message.inputTokens + message.outputTokens))
                UsageMetric(
                    "Recorded cost",
                    if (message.costKnown) formatCostMicros(message.costMicros) else "Unavailable / calculate manually",
                )
                val rows = calls
                if (!rows.isNullOrEmpty()) {
                    HorizontalDivider()
                    Text(uiText("Provider calls"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rows!!.forEachIndexed { index, call ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        uiText("Call ${index + 1} · round ${call.roundIndex + 1}"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        uiText("input ${call.inputTokens} · cached ${call.cachedInputTokens} · output ${call.outputTokens}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    val nonCached = (call.inputTokens - call.cachedInputTokens).coerceAtLeast(0)
                                    Text(
                                        uiText("non-cached $nonCached · total ${call.inputTokens + call.outputTokens}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        uiText(buildString {
                                            append(call.status.lowercase(Locale.ROOT))
                                            call.finishReason?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
                                            append(" · ")
                                            append(if (call.costKnown) formatCostMicros(call.costMicros) else "cost unavailable")
                                        }),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    call.error?.takeIf(String::isNotBlank)?.let { error ->
                                        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                } else if (calls != null) {
                    Text(
                        uiText("No per-call usage rows were recorded for this response. The aggregate values above are still available."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    uiText("Token fields are stored from provider usage metadata when the provider reports them. Otherwise Xylune may fall back to its local counter/estimator. Use the raw token counts above with your provider's current pricing table for manual calculation."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(uiText("Close")) } },
    )
}

@Composable
internal fun ConversationUsageSection(conversationId: String) {
    val context = LocalContext.current
    val container = remember(context) { (context.applicationContext as XyluneApplication).container }
    val snapshot by produceState<ConversationUsageSnapshot?>(initialValue = null, conversationId) {
        value = withContext(Dispatchers.IO) {
            var requestCount = 0
            var input = 0L
            var cached = 0L
            var output = 0L
            var cost = 0L
            var unknown = 0
            val messages = container.repository.recent(conversationId, 10_000)
            for (message in messages) {
                if (message.role != MessageRole.ASSISTANT) continue
                val rows = container.repository.generationUsage(message.nodeId)
                for (row in rows) {
                    requestCount++
                    input += row.inputTokens
                    cached += row.cachedInputTokens
                    output += row.outputTokens
                    cost += row.costMicros
                    if (!row.costKnown && (row.inputTokens > 0 || row.outputTokens > 0)) unknown++
                }
            }
            ConversationUsageSnapshot(requestCount, input, cached, output, cost, unknown)
        }
    }
    val usage = snapshot
    if (usage == null) {
        Text(uiText("Loading usage…"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val nonCached = (usage.inputTokens - usage.cachedInputTokens).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        UsageMetric("Provider calls", usage.requestCount.toString())
        UsageMetric("Input tokens", formatTokenCount(usage.inputTokens))
        UsageMetric("Cached input", formatTokenCount(usage.cachedInputTokens))
        UsageMetric("Non-cached input", formatTokenCount(nonCached))
        UsageMetric("Output / billed tokens", formatTokenCount(usage.outputTokens))
        UsageMetric("Total tokens", formatTokenCount(usage.inputTokens + usage.outputTokens))
        UsageMetric(
            "Known calculated cost",
            buildString {
                append(formatCostMicros(usage.costMicros))
                if (usage.unknownCostCalls > 0) append(" · ${usage.unknownCostCalls} call(s) unpriced")
            },
        )
        Text(
            uiText("Per-response call breakdowns are available from each assistant message's ⋮ menu."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

private fun formatTokenCount(value: Long): String = "%,d".format(Locale.US, value)
private fun formatCostMicros(value: Long): String = "$" + "%.6f".format(Locale.US, value / 1_000_000.0)

private fun shareMessage(
    context: android.content.Context,
    message: MessageEntity,
    attachments: List<AttachmentEntity>,
) {
    val role = when (message.role) {
        MessageRole.USER -> "You"
        MessageRole.ASSISTANT -> "Xylune"
        else -> message.role.name.lowercase().replaceFirstChar(Char::uppercase)
    }
    val text = buildString {
        append(role).append(":\n")
        append(message.content.ifBlank { message.reasoning })
    }
    val shareable = attachments.mapNotNull { attachment ->
        val file = File(attachment.localPath)
        if (!file.isFile) return@mapNotNull null
        runCatching {
            attachment to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    }
    val intent = when (shareable.size) {
        0 -> Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        1 -> Intent(Intent.ACTION_SEND).apply {
            val (attachment, uri) = shareable.single()
            type = attachment.mimeType.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, attachment.displayName, uri)
        }
        else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            val mimeTypes = shareable.map { it.first.mimeType }.filter(String::isNotBlank).distinct()
            type = mimeTypes.singleOrNull() ?: "*/*"
            putExtra(Intent.EXTRA_TEXT, text)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(shareable.map { it.second }))
            val first = shareable.first()
            clipData = ClipData.newUri(context.contentResolver, first.first.displayName, first.second).also { clips ->
                shareable.drop(1).forEach { (attachment, uri) ->
                    clips.addItem(ClipData.Item(uri))
                }
            }
        }
    }.apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share message").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
