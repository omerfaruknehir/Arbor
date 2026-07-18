package app.arbor.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.arbor.chat.sandbox.LinuxDistribution
import app.arbor.chat.sandbox.UbuntuExecutionResult
import app.arbor.chat.sandbox.UbuntuStage
import kotlinx.coroutines.launch

private data class TerminalEntry(
    val command: String,
    val result: UbuntuExecutionResult? = null,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxTerminalScreen(viewModel: ChatViewModel, openDrawer: (() -> Unit)?) {
    val chromeBlurEnabled by viewModel.chromeBlurEnabled.collectAsState()
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    val status by viewModel.ubuntuStatus.collectAsState()
    val selectedDistribution by viewModel.linuxDistribution.collectAsState()
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<TerminalEntry>() }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }

    fun submit() {
        val command = input.trim()
        if (command.isBlank() || running || !status.installed) return
        input = ""
        entries += TerminalEntry(command)
        val index = entries.lastIndex
        running = true
        scope.launch {
            val completed = runCatching { viewModel.executeUbuntu(command, 3_600) }
            entries[index] = completed.fold(
                onSuccess = { TerminalEntry(command, result = it) },
                onFailure = { TerminalEntry(command, error = it.message ?: it::class.java.simpleName) },
            )
            running = false
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollapsingTranslucentTopBar(
                title = "Linux terminal",
                scrollBehavior = scrollBehavior,
                blurEnabled = chromeBlurEnabled,
                blurStrength = chromeBlurStrength,
                navigationIcon = {
                    IconButton(onClick = { openDrawer?.invoke() ?: run { viewModel.screen.value = Screen.SETTINGS } }) {
                        Icon(if (openDrawer != null) Icons.Outlined.Menu else Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { entries.clear() }) { Icon(Icons.Outlined.DeleteSweep, "Clear terminal") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinuxDistribution.entries.forEach { distribution ->
                    FilterChip(
                        selected = selectedDistribution == distribution,
                        onClick = { if (!running) viewModel.selectLinuxDistribution(distribution) },
                        label = { Text(distribution.displayName) },
                    )
                }
            }
            Text(
                when (status.stage) {
                    UbuntuStage.READY -> "Root shell • ${status.distribution.displayName} ${status.release} • /workspace"
                    else -> "${status.distribution.displayName}: ${status.detail.ifBlank { status.stage.name.lowercase() }}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (status.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!status.installed) {
                Button(onClick = { scope.launch { viewModel.installUbuntu() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Install ${status.distribution.displayName}")
                }
            } else {
                OutlinedButton(onClick = { scope.launch { viewModel.removeUbuntu() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Remove ${status.distribution.displayName}")
                }
            }
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF101410), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "Arbor ${status.distribution.displayName} root terminal\nCommands run as uid 0 inside the selected PRoot distribution.",
                        color = Color(0xFF9CCB9C),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                items(entries) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "root@arbor-${selectedDistribution.id}:/workspace#",
                            color = Color(0xFF91A391),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        AutoLintedCodeText(
                            language = "bash",
                            code = entry.command,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFB7F7B7),
                                fontWeight = FontWeight.SemiBold,
                            ),
                            softWrap = true,
                        )
                        entry.result?.let { result ->
                            if (result.stdout.isNotBlank()) Text(result.stdout, color = Color(0xFFE4E9E4), fontFamily = FontFamily.Monospace)
                            if (result.stderr.isNotBlank()) Text(result.stderr, color = Color(0xFFFFB4AB), fontFamily = FontFamily.Monospace)
                            Text("exit ${result.exitCode} • ${result.elapsedMs} ms", color = Color(0xFF91A391), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                        entry.error?.let { Text(it, color = Color(0xFFFFB4AB), fontFamily = FontFamily.Monospace) }
                    }
                }
                if (running) item { Text("running…", color = Color(0xFFFFD37A), fontFamily = FontFamily.Monospace) }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = status.installed && !running,
                label = { Text("root@arbor-${selectedDistribution.id}:/workspace#") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = rememberCodeVisualTransformation("bash"),
                trailingIcon = { IconButton(onClick = ::submit, enabled = input.isNotBlank() && status.installed && !running) { Icon(Icons.Outlined.PlayArrow, "Run") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 4,
            )
        }
    }
}
