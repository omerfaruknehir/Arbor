package app.xylune.chat.ui

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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.xylune.chat.R
import app.xylune.chat.sandbox.UbuntuExecutionResult
import app.xylune.chat.sandbox.UbuntuStage
import kotlinx.coroutines.launch

private data class TerminalEntry(
    val command: String,
    val result: UbuntuExecutionResult? = null,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxTerminalScreen(viewModel: ChatViewModel) {
    val appName = stringResource(R.string.app_name)
    val chromeBlurStrength by viewModel.chromeBlurStrength.collectAsState()
    val chromeEdgeSoftness by viewModel.chromeEdgeSoftness.collectAsState()
    val chromeOverlayOpacity by viewModel.chromeOverlayOpacity.collectAsState()
    val status by viewModel.ubuntuStatus.collectAsState()
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
    val blurState = rememberXyluneBackdropBlurState()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CollapsingTranslucentTopBar(
                title = "Linux terminal",
                scrollBehavior = scrollBehavior,
                blurState = blurState,
                blurStrength = chromeBlurStrength,
                edgeSoftness = chromeEdgeSoftness,
                overlayOpacity = chromeOverlayOpacity,
                blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp,
                navigationIcon = {
                    IconButton(onClick = { viewModel.screen.value = Screen.SANDBOX }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, uiText("Back to runtime manager"))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.screen.value = Screen.SANDBOX }) {
                        Icon(Icons.Outlined.Settings, uiText("Manage Linux workspace"))
                    }
                    IconButton(onClick = { entries.clear() }) { Icon(Icons.Outlined.DeleteSweep, uiText("Clear terminal")) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .xyluneBackdropSource(blurState)
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                uiText(when (status.stage) {
                    UbuntuStage.READY -> "Root shell • ${status.distribution.displayName} ${status.release} • /workspace"
                    else -> "${status.distribution.displayName}: ${status.detail.ifBlank { status.stage.name.lowercase() }}"
                }),
                style = MaterialTheme.typography.labelMedium,
                color = if (status.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!status.installed) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.WarningAmber, null)
                            Text(uiText("Linux workspace not ready"), fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            uiText("Distribution selection, installation, packages, and removal are managed in one place."),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { viewModel.screen.value = Screen.SANDBOX }) {
                            Text(uiText("Manage Linux workspace"))
                        }
                    }
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
                        uiText("$appName ${status.distribution.displayName} root terminal\nCommands run as uid 0 inside the selected PRoot distribution."),
                        color = Color(0xFF9CCB9C),
                        fontFamily = FontFamily.Monospace,
                    )
                }
                items(entries) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            uiText("root@xylune-${status.distribution.id}:/workspace#"),
                            color = Color(0xFF91A391),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        HighlightedCodeText(
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
                            Text(uiText("exit ${result.exitCode} • ${result.elapsedMs} ms"), color = Color(0xFF91A391), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                        }
                        entry.error?.let { Text(it, color = Color(0xFFFFB4AB), fontFamily = FontFamily.Monospace) }
                    }
                }
                if (running) item { Text(uiText("running…"), color = Color(0xFFFFD37A), fontFamily = FontFamily.Monospace) }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = status.installed && !running,
                label = { Text(uiText("root@xylune-${status.distribution.id}:/workspace#")) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                visualTransformation = rememberCodeVisualTransformation("bash"),
                trailingIcon = { IconButton(onClick = ::submit, enabled = input.isNotBlank() && status.installed && !running) { Icon(Icons.Outlined.PlayArrow, uiText("Run")) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
                maxLines = 4,
            )
        }
    }
}
