package app.xylune.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.generated.GeneratedBlockRepairState
import app.xylune.chat.generated.GeneratedBlockType
import app.xylune.chat.generated.GeneratedContentCapabilityRegistry
import app.xylune.chat.generated.GeneratedRepairStatus
import app.xylune.chat.generated.GeneratedValidationError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun RepairableGeneratedContent(
    blockId: String,
    messageId: String,
    type: GeneratedBlockType,
    source: String,
    preparationErrors: List<GeneratedValidationError> = emptyList(),
    repair: suspend (
        blockId: String,
        messageId: String,
        type: GeneratedBlockType,
        source: String,
        errors: List<GeneratedValidationError>,
        newCycle: Boolean,
        progress: (GeneratedBlockRepairState) -> Unit,
    ) -> GeneratedBlockRepairState,
    acceptEdit: suspend (GeneratedBlockRepairState, String) -> GeneratedBlockRepairState,
    workingCardViewport: WorkingCardViewportController,
    render: @Composable (String) -> Unit,
) {
    val localValidation = remember(type, source, preparationErrors) {
        val registry = GeneratedContentCapabilityRegistry.validate(type, source)
        registry.errors + preparationErrors
    }
    val requiresCompilation = type == GeneratedBlockType.HOME_WIDGET
    if (localValidation.isEmpty() && !requiresCompilation) {
        render(source)
        return
    }

    var state by remember(blockId, source) { mutableStateOf<GeneratedBlockRepairState?>(null) }
    var cycle by remember(blockId, source) { mutableIntStateOf(1) }
    var forceNewCycle by remember(blockId, source) { mutableStateOf(false) }
    var editing by remember(blockId, source) { mutableStateOf(false) }
    var editSource by remember(blockId, source) { mutableStateOf(source) }
    var editError by remember(blockId, source) { mutableStateOf("") }
    var details by remember(blockId, source) { mutableStateOf(false) }
    var cardBounds by remember(blockId, source) { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(blockId, source, cycle) {
        val completed = try {
            repair(blockId, messageId, type, source, localValidation, forceNewCycle) { next ->
                workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { state = next }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (current: Throwable) {
            state?.copy(status = GeneratedRepairStatus.PROVIDER_FAILED, providerError = current.message.orEmpty())
        }
        if (completed != null) workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { state = completed }
        forceNewCycle = false
    }

    val current = state
    if (current?.status == GeneratedRepairStatus.ACCEPTED && current.acceptedSource != null) {
        Column(Modifier.noOpBringIntoView().onGloballyPositioned { cardBounds = it.boundsInRoot() }, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f), shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Build, null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        uiText(if (current.attemptCount == 0) "Compiled and tested ${type.displayLabel}" else "AI rebuilt and compiled ${type.displayLabel} · ${current.attemptCount} attempt${if (current.attemptCount == 1) "" else "s"}"),
                        Modifier.weight(1f).padding(start = 7.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    IconButton(onClick = {
                        workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { details = !details }
                    }) { Icon(Icons.Outlined.Edit, uiText("Inspect repair")) }
                }
            }
            AnimatedVisibility(details) { RepairDetails(current) }
            render(current.acceptedSource)
        }
    } else {
        RepairStatusCard(
            state = current,
            type = type,
            source = current?.currentCandidate ?: source,
            maxAttempts = current?.maxAttempts ?: 3,
            details = details,
            onToggleDetails = {
                workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { details = !details }
            },
            onRetry = { forceNewCycle = true; cycle++ },
            onEdit = { editSource = current?.currentCandidate ?: source; editError = ""; editing = true },
            modifier = Modifier.onGloballyPositioned { cardBounds = it.boundsInRoot() },
        )
    }

    if (editing && current != null) XyluneAlertDialog(
        onDismissRequest = { editing = false },
        title = { Text(uiText("Edit ${type.displayLabel} source")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editSource,
                    onValueChange = { editSource = it.take(48_000); editError = "" },
                    minLines = 10,
                    maxLines = 20,
                    modifier = Modifier.fillMaxWidth(),
                    isError = editError.isNotBlank(),
                    supportingText = { Text(uiText(editError.ifBlank { "The edited source is compiled, executed in the bounded runtime, and rendered before use." })) },
                )
            }
        },
        dismissButton = { OutlinedButton(onClick = { editing = false }) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(onClick = {
                scope.launch {
                    runCatching { acceptEdit(current, editSource) }
                        .onSuccess { accepted ->
                            workingCardViewport.applyMutation(WorkingCardMutation.AUTO_EXPAND, { cardBounds }) { state = accepted }
                            editing = false
                        }
                        .onFailure { editError = it.message.orEmpty() }
                }
            }) { Text(uiText("Compile & use")) }
        },
    )
}

@Composable
private fun RepairStatusCard(
    state: GeneratedBlockRepairState?,
    type: GeneratedBlockType,
    source: String,
    maxAttempts: Int,
    details: Boolean,
    onToggleDetails: () -> Unit,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repairing = state == null || state.status in setOf(
        GeneratedRepairStatus.PENDING,
        GeneratedRepairStatus.COMPILING,
        GeneratedRepairStatus.REPAIRING,
    )
    val title = when (state?.status) {
        null, GeneratedRepairStatus.COMPILING -> "Compiling and testing ${type.displayLabel}…"
        GeneratedRepairStatus.REPAIRING -> "Rebuilding ${type.displayLabel} from compiler feedback… attempt ${(state.attemptCount + 1).coerceAtMost(maxAttempts)} of $maxAttempts"
        GeneratedRepairStatus.PENDING -> "Preparing another ${type.displayLabel} build…"
        GeneratedRepairStatus.EXHAUSTED -> "Could not compile this ${type.displayLabel} after ${state.attemptCount} repair attempts."
        GeneratedRepairStatus.PROVIDER_FAILED -> "Automatic ${type.displayLabel} repair is unavailable."
        GeneratedRepairStatus.ACCEPTED -> "Compiled ${type.displayLabel}"
    }
    Surface(color = if (repairing) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().noOpBringIntoView()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            state?.providerError?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (!repairing) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onRetry) { Icon(Icons.Outlined.Refresh, null); Text(uiText("Ask AI to retry")) }
                OutlinedButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, null); Text(uiText("Edit source")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("generated source", source))
                }) { Icon(Icons.Outlined.ContentCopy, null); Text(uiText("Copy source")) }
                if (state != null) OutlinedButton(onClick = onToggleDetails) { Text(uiText(if (details) "Hide attempts" else "View attempts")) }
            }
            if (!repairing) OutlinedButton(onClick = {
                val errors = (state.errors + state.attempts.flatMap { it.errors }).distinct()
                    .joinToString("\n") { "${it.phase} ${it.path}: ${it.message}" }
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("generated content errors", errors))
            }) { Text(uiText("Copy errors")) }
            AnimatedVisibility(details && state != null) { state?.let { RepairDetails(it) } }
        }
    }
}

@Composable
private fun RepairDetails(state: GeneratedBlockRepairState) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        state.attempts.forEach { attempt ->
            Text(uiText("Attempt ${attempt.number} · ${if (attempt.repeatedCandidate) "repeated candidate · " else ""}${attempt.errors.size} error(s)"), style = MaterialTheme.typography.labelMedium)
            attempt.errors.take(6).forEach { Text(uiText("${it.path}: ${it.message}"), style = MaterialTheme.typography.bodySmall) }
        }
        OutlinedButton(onClick = {
            val errors = state.attempts.flatMap { it.errors }.joinToString("\n") { "${it.phase} ${it.path}: ${it.message}" }
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("generated content errors", errors))
        }) { Text(uiText("Copy errors")) }
    }
}

private val GeneratedBlockType.displayLabel: String get() = when (this) {
    GeneratedBlockType.CHAT_UI -> "snippet"
    GeneratedBlockType.HOME_WIDGET -> "widget"
    GeneratedBlockType.CHART -> "chart"
    GeneratedBlockType.DIAGRAM -> "diagram"
}
