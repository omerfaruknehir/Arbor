package app.xylune.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.sandbox.ExecutionResult
import app.xylune.chat.sandbox.ExecutionProgress
import app.xylune.chat.sandbox.UbuntuExecutionResult

@Composable
fun CodeSourcePanel(
    language: String,
    code: String,
    title: String = language.ifBlank { "code" }.uppercase(),
    live: Boolean = false,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                if (live) Text(uiText("Streaming…"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LowSensitivityHorizontalScroll(Modifier.padding(12.dp)) {
                HighlightedCodeText(
                    language = language,
                    code = code,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = false,
                )
            }
        }
    }
}


@Composable
fun LiveExecutionCard(progress: ExecutionProgress, title: String = "Code execution") {
    ExecutionFrame(title, "Running", formatExecutionDuration(progress.elapsedMs), failed = false) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(uiText("Live output updates as the process prints."), style = MaterialTheme.typography.bodySmall)
        }
        progress.stdoutTail.takeIf(String::isNotBlank)?.let { LiveOutputSection("Output", it) }
        progress.stderrTail.takeIf(String::isNotBlank)?.let { LiveOutputSection("Errors", it, error = true) }
        if (progress.stdoutTail.isBlank() && progress.stderrTail.isBlank()) {
            OutputSection("Output", "Waiting for output…")
        }
    }
}

@Composable
private fun LiveOutputSection(label: String, text: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                label,
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer(Modifier.padding(10.dp)) {
                Text(
                    text.takeLast(6_000),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 14,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun PythonExecutionCard(output: ExecutionResult, title: String = "Python result") {
    val failed = output.stderr.isNotBlank() || output.timedOut
    ExecutionFrame(title, if (failed) "Failed" else "Complete", formatExecutionDuration(output.elapsedMs), failed) {
        output.result?.takeIf(String::isNotBlank)?.let { OutputSection("Result", it) }
        output.stdout.takeIf(String::isNotBlank)?.let { OutputSection("Output", it) }
        output.stderr.takeIf(String::isNotBlank)?.let { OutputSection("Errors", it, error = true) }
        if (output.timedOut) OutputSection("Timed out", "Execution exceeded its configured deadline.", error = true)
        if (output.files.isNotEmpty()) OutputSection("Changed files", output.files.joinToString("\n") { "• $it" })
        if (output.result.isNullOrBlank() && output.stdout.isBlank() && output.stderr.isBlank() && output.files.isEmpty()) OutputSection("Output", "Completed without output.")
    }
}

@Composable
fun UbuntuExecutionCard(output: UbuntuExecutionResult, title: String = "Ubuntu result") {
    val failed = output.exitCode != 0 || output.timedOut
    ExecutionFrame(title, if (output.timedOut) "Timed out" else "Exit ${output.exitCode}", formatExecutionDuration(output.elapsedMs), failed) {
        output.stdout.takeIf(String::isNotBlank)?.let { OutputSection("Output", it) }
        output.stderr.takeIf(String::isNotBlank)?.let { OutputSection("Errors", it, error = true) }
        if (output.files.isNotEmpty()) OutputSection("Changed files", output.files.joinToString("\n") { "• $it" })
        if (output.stdout.isBlank() && output.stderr.isBlank() && output.files.isEmpty()) OutputSection("Output", "Completed without output.")
    }
}

@Composable
fun GenericToolOutputCard(output: String, failed: Boolean = false) {
    ExecutionFrame("Tool output", if (failed) "Failed" else "Complete", "", failed) {
        OutputSection(if (failed) "Error" else "Output", output.ifBlank { "No output." }, error = failed)
    }
}

@Composable
private fun ExecutionFrame(title: String, state: String, detail: String, failed: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Surface(
                    color = if (failed) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(
                        uiText(listOf(state, detail).filter(String::isNotBlank).joinToString(" • ")),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            content()
        }
    }
}

internal fun formatExecutionDuration(elapsedMs: Long): String = when {
    elapsedMs <= 0L -> ""
    elapsedMs < 1_000L -> "$elapsedMs ms"
    elapsedMs < 10_000L -> "${elapsedMs / 100 / 10.0} s"
    else -> "${elapsedMs / 1_000} s"
}

@Composable
private fun OutputSection(label: String, text: String, error: Boolean = false) {
    Surface(
        color = if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(label, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SelectionContainer(Modifier.padding(10.dp)) {
                Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
