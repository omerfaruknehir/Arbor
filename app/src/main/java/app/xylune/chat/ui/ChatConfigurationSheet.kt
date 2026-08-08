package app.xylune.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.xylune.chat.R
import app.xylune.chat.data.ContextSummaryEntity
import app.xylune.chat.data.ConversationEntity
import app.xylune.chat.data.ReasoningVisibility

/** Advanced values which are intentionally absent from the everyday composer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConfigurationSheet(
    conversation: ConversationEntity,
    contextSummary: ContextSummaryEntity?,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val appName = stringResource(R.string.app_name)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(uiText("Chat configuration"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                uiText("Advanced limits and prompt behavior for this conversation. Thinking, effort, tools, files, and Deep Research live beside the message box."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ChatNumberSetting("Last request/answer pairs", conversation.contextPairs, 1..500) { value ->
                viewModel.updateConversation { it.copy(contextPairs = value) }
            }
            ChatNumberSetting("Context token ceiling", conversation.contextTokenLimit, 1_024..2_000_000) { value ->
                viewModel.updateConversation { it.copy(contextTokenLimit = value) }
            }
            ChatNumberSetting("Working-history token budget", conversation.workingTokenLimit, 0..2_000_000) { value ->
                viewModel.updateConversation { it.copy(workingTokenLimit = value) }
            }
            ChatNumberSetting("Maximum output tokens", conversation.maxOutputTokens, 1..384_000) { value ->
                viewModel.updateConversation { it.copy(maxOutputTokens = value) }
            }

            HorizontalDivider()
            Text(uiText("Usage"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                uiText("Raw token totals recorded from provider calls. Use these with the provider's current pricing when you want to verify or calculate cost manually."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConversationUsageSection(conversation.id)

            HorizontalDivider()
            Text(uiText("Token counting"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(uiText("Hybrid preflight counting"))
                    Text(
                        uiText("Exact provider counters where available, then local family and generic fallbacks."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = conversation.hybridTokenCountingEnabled,
                    onCheckedChange = { enabled -> viewModel.updateConversation { it.copy(hybridTokenCountingEnabled = enabled) } },
                )
            }

            HorizontalDivider()
            Text(uiText("Working display"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReasoningVisibility.entries.forEach { option ->
                    AssistChip(
                        onClick = { viewModel.updateConversation { it.copy(reasoningVisibility = option) } },
                        label = { Text(uiText(option.chatLabel)) },
                        leadingIcon = if (conversation.reasoningVisibility == option) ({
                            androidx.compose.material3.Icon(Icons.Outlined.CheckCircle, null)
                        }) else null,
                    )
                }
            }

            HorizontalDivider()
            Text(uiText("Custom instructions"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val promptProfiles by viewModel.systemPromptProfiles.collectAsStateWithLifecycle()
            var promptMenu by remember { mutableStateOf(false) }
            val activePrompt = promptProfiles.firstOrNull { it.id == conversation.systemPromptProfileId }
            androidx.compose.foundation.layout.Box {
                OutlinedButton(onClick = { promptMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(uiText(activePrompt?.let { "${it.name} · ${it.mode.name.lowercase()}" } ?: "$appName core prompt only"), Modifier.weight(1f))
                }
                XyluneDropdownMenu(expanded = promptMenu, onDismissRequest = { promptMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(uiText("$appName core prompt only")) },
                        onClick = { viewModel.selectSystemPromptProfileForCurrent(null); promptMenu = false },
                    )
                    promptProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(uiText("${profile.name} · ${profile.mode.name.lowercase()}")) },
                            onClick = { viewModel.selectSystemPromptProfileForCurrent(profile.id); promptMenu = false },
                        )
                    }
                }
            }
            if (activePrompt != null) Text(
                activePrompt.prompt,
                maxLines = 5,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ) else Text(
                uiText("$appName's built-in core prompt is versioned with the app and cannot be edited. Create reusable custom profiles in Settings."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            contextSummary?.let { summary ->
                HorizontalDivider()
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(uiText("Compressed context active"), fontWeight = FontWeight.SemiBold)
                        Text(
                            uiText("${summary.sourceMessageCount} older messages • about ${summary.tokenEstimate} tokens"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::compressContextNow, modifier = Modifier.weight(1f)) { Text(uiText("Compress")) }
                OutlinedButton(onClick = viewModel::clearContextSummary, modifier = Modifier.weight(1f)) { Text(uiText("Clear summary")) }
            }
            OutlinedButton(onClick = viewModel::applyNewChatDefaultsToCurrent, modifier = Modifier.fillMaxWidth()) {
                Text(uiText("Reset advanced values to defaults"))
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun ChatNumberSetting(label: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { raw -> raw.toIntOrNull()?.coerceIn(range)?.let(onValue) },
        label = { Text(uiText(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val ReasoningVisibility.chatLabel: String
    get() = when (this) {
        ReasoningVisibility.ALWAYS -> "Expanded"
        ReasoningVisibility.SHOW_WHILE_WORKING -> "While working"
        ReasoningVisibility.COLLAPSED -> "Collapsed"
    }
