package app.arbor.chat.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.arbor.chat.sandbox.ExecutionResult
import app.arbor.chat.sandbox.UbuntuExecutionResult

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
                if (live) Text("Streaming…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HighlightedCodeText(
                language = language,
                code = code,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
            )
        }
    }
}

@Composable
fun PythonExecutionCard(output: ExecutionResult, title: String = "Python result") {
    val failed = output.stderr.isNotBlank() || output.timedOut
    ExecutionFrame(title, if (failed) "FAILED" else "COMPLETE", "${output.elapsedMs} ms", failed) {
        output.result?.takeIf(String::isNotBlank)?.let { OutputSection("RESULT", it) }
        output.stdout.takeIf(String::isNotBlank)?.let { OutputSection("STDOUT", it) }
        output.stderr.takeIf(String::isNotBlank)?.let { OutputSection("STDERR", it, error = true) }
        if (output.timedOut) OutputSection("TIMEOUT", "Execution exceeded its configured deadline.", error = true)
        if (output.files.isNotEmpty()) OutputSection("CHANGED FILES", output.files.joinToString("\n") { "• $it" })
        if (output.result.isNullOrBlank() && output.stdout.isBlank() && output.stderr.isBlank() && output.files.isEmpty()) OutputSection("OUTPUT", "Command completed without output.")
    }
}

@Composable
fun UbuntuExecutionCard(output: UbuntuExecutionResult, title: String = "Ubuntu result") {
    val failed = output.exitCode != 0 || output.timedOut
    ExecutionFrame(title, if (output.timedOut) "TIMED OUT" else "EXIT ${output.exitCode}", "${output.elapsedMs} ms", failed) {
        output.stdout.takeIf(String::isNotBlank)?.let { OutputSection("STDOUT", it) }
        output.stderr.takeIf(String::isNotBlank)?.let { OutputSection("STDERR", it, error = true) }
        if (output.files.isNotEmpty()) OutputSection("CHANGED FILES", output.files.joinToString("\n") { "• $it" })
        if (output.stdout.isBlank() && output.stderr.isBlank() && output.files.isEmpty()) OutputSection("OUTPUT", "Command completed without output.")
    }
}

@Composable
fun GenericToolOutputCard(output: String, failed: Boolean = false) {
    ExecutionFrame("Tool output", if (failed) "FAILED" else "COMPLETE", "", failed) {
        OutputSection(if (failed) "ERROR" else "OUTPUT", output.ifBlank { "No output." }, error = failed)
    }
}

@Composable
private fun ExecutionFrame(title: String, state: String, detail: String, failed: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(listOf(state, detail).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.labelMedium, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
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
