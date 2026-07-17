package app.arbor.chat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.arbor.chat.sandbox.ExecutionResult
import app.arbor.chat.sandbox.PackageInstallResult
import app.arbor.chat.sandbox.PackageAction
import app.arbor.chat.sandbox.PackageApprovalState
import app.arbor.chat.sandbox.PackageReview
import app.arbor.chat.sandbox.PackagePlan
import app.arbor.chat.sandbox.UbuntuExecutionResult
import app.arbor.chat.sandbox.UbuntuPackageInstallResult
import app.arbor.chat.widgets.ProgrammableWidgetBlock
import app.arbor.chat.ArborApplication
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.roundToInt

private sealed interface RichBlock {
    data class Markdown(val text: String) : RichBlock
    data class Code(val language: String, val code: String) : RichBlock
}

@Composable
fun RichMessage(
    operationScope: String,
    text: String,
    streaming: Boolean = false,
    onRunPython: suspend (String) -> ExecutionResult,
    onRunUbuntu: suspend (String) -> UbuntuExecutionResult,
    onReviewPythonPackages: suspend (String, String) -> PackageReview,
    onInstallPackages: suspend (String, String, PackagePlan) -> PackageInstallResult,
    onReviewUbuntuPackages: suspend (String, String) -> PackageReview,
    onInstallUbuntuPackages: suspend (String, String, PackagePlan) -> UbuntuPackageInstallResult,
    onWidgetSubmit: (String) -> Unit,
    onReviewWidgetSecurity: suspend (String) -> String,
) {
    val context = LocalContext.current
    val crashReporter = (context.applicationContext as? ArborApplication)?.container?.crashReporter
    val safeRendering by crashReporter?.renderSafeMode?.collectAsState() ?: remember { mutableStateOf(false) }
    val blocks = remember(text) { parseBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is RichBlock.Markdown -> MarkdownBlock(block.text, key = "$index:${block.text.hashCode()}")
                is RichBlock.Code -> when (block.language.lowercase()) {
                    "mermaid", "graph", "diagram", "dot", "graphviz" -> if (safeRendering) SafeGeneratedBlock("Diagram", block.code) { crashReporter?.setRenderSafeMode(false) } else NativeDiagramBlock(block.code)
                    "chart", "arbor-chart", "bar-chart", "barchart", "line-chart", "pie-chart" -> if (safeRendering) SafeGeneratedBlock("Chart", block.code) { crashReporter?.setRenderSafeMode(false) } else NativeChartBlock(block.code)
                    "arbor-ui", "ui", "arbor-form" -> ProgrammableWidgetBlock(block.code, onWidgetSubmit, onReviewWidgetSecurity, allowHomePinning = false)
                    "arbor-widget", "widget" -> ProgrammableWidgetBlock(block.code, onWidgetSubmit, onReviewWidgetSecurity, allowHomePinning = true)
                    "python-requirements", "requirements", "pip" -> PackageRequestBlock(
                        operationKey = "$operationScope:package:$index",
                        title = "Python package request",
                        requirements = block.code,
                        onReview = { requested -> onReviewPythonPackages("$operationScope:package:$index", requested) },
                        onInstall = { requested, plan ->
                            val result = onInstallPackages("$operationScope:package:$index", requested, plan)
                            InstallUiResult(
                                result.success && result.importErrors.isEmpty(),
                                if (result.success && result.importErrors.isEmpty()) "Installed and import-verified: ${result.packages.joinToString()}"
                                else if (result.success) "Installed, but import verification found a problem" else "Install failed",
                                buildString {
                                    if (result.importNames.isNotEmpty()) append(result.importNames.entries.joinToString("\n") { (distribution, names) -> "$distribution → import ${names.joinToString().ifBlank { "name unavailable" }}" })
                                    if (result.importErrors.isNotEmpty()) append("\n").append(result.importErrors.entries.joinToString("\n") { "${it.key}: ${it.value}" })
                                    if (!result.success) append("\n").append(result.stderr.lines().takeLast(12).joinToString("\n"))
                                }.trim().takeLast(2_000),
                            )
                        },
                    )
                    "linux-packages", "ubuntu-packages", "apt", "apt-packages", "apk", "apk-packages" -> PackageRequestBlock(
                        operationKey = "$operationScope:package:$index",
                        title = "Linux package request",
                        requirements = block.code,
                        onReview = { requested -> onReviewUbuntuPackages("$operationScope:package:$index", requested) },
                        onInstall = { requested, plan ->
                            val result = onInstallUbuntuPackages("$operationScope:package:$index", requested, plan)
                            InstallUiResult(
                                result.success,
                                if (result.success) "Installed: ${result.packages.joinToString()}" else "Package installation failed",
                                (result.stderr.ifBlank { result.stdout }).lines().takeLast(16).joinToString("\n").takeLast(2_000),
                            )
                        },
                    )
                    else -> CodeBlock(block.language, block.code, onRunPython, onRunUbuntu)
                }
            }
        }
        StreamingTokenPulse(visible = streaming, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SafeGeneratedBlock(label: String, source: String, retry: () -> Unit) {
    var expanded by remember(source) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("$label paused for crash recovery", fontWeight = FontWeight.SemiBold)
            Text("The source and conversation are intact.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Collapse source" else "Show source") }
                Button(onClick = retry) { Text("Try full rendering") }
            }
            AnimatedVisibility(expanded) { Text(source, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private data class InstallUiResult(val success: Boolean, val message: String, val detail: String = "")

@Composable
private fun PackageRequestBlock(
    operationKey: String,
    title: String,
    requirements: String,
    onReview: suspend (String) -> PackageReview,
    onInstall: suspend (String, PackagePlan) -> InstallUiResult,
) {
    val scope = rememberCoroutineScope()
    var confirm by remember(operationKey, requirements) { mutableStateOf(false) }
    var reviewing by remember(operationKey, requirements) { mutableStateOf(true) }
    var installing by remember(operationKey, requirements) { mutableStateOf(false) }
    var review by remember(operationKey, requirements) { mutableStateOf<PackageReview?>(null) }
    var result by remember(operationKey, requirements) { mutableStateOf<InstallUiResult?>(null) }
    var showDetails by remember(operationKey, requirements) { mutableStateOf(false) }

    suspend fun installNow() {
        installing = true
        val approved = review?.plan ?: run {
            result = InstallUiResult(false, "Install blocked", "The reviewed plan is no longer available. Run preflight again.")
            installing = false
            return
        }
        result = runCatching { onInstall(requirements, approved) }
            .getOrElse { InstallUiResult(false, "Install failed", it.message.orEmpty()) }
        installing = false
    }

    LaunchedEffect(operationKey, requirements) {
        reviewing = true
        review = runCatching { onReview(requirements) }.getOrElse { error ->
            null.also { result = InstallUiResult(false, "Preflight failed", error.message.orEmpty()) }
        }
        reviewing = false
        if (review?.state == PackageApprovalState.APPROVED) installNow()
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(requirements, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Surface(
                color = when {
                    result?.success == false -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f)
                    result?.success == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                    else -> MaterialTheme.colorScheme.surfaceContainerLowest
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (reviewing || installing) CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                    Text(
                        when {
                            reviewing -> "Checking installed packages and resolving dependencies…"
                            installing -> if (review?.state == PackageApprovalState.APPROVED) "Approved automatically • installing now…" else "Installing approved changes…"
                            result != null -> result!!.message
                            review?.state == PackageApprovalState.NOT_NEEDED -> "Everything requested is already installed."
                            review?.state == PackageApprovalState.DENIED -> "Installation denied • ${review?.reason.orEmpty()}"
                            review?.state == PackageApprovalState.APPROVED -> "Approved automatically • waiting to start"
                            review?.state == PackageApprovalState.REQUIRED -> "Ready for your review"
                            else -> "Preparing package plan…"
                        },
                        Modifier.padding(start = if (reviewing || installing) 8.dp else 0.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (result?.success == false || review?.state == PackageApprovalState.DENIED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            review?.let { current ->
                val changed = current.plan.items.count { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
                val dependencies = current.plan.items.count { it.detail == "Dependency" }
                Text("${current.plan.items.size} packages resolved • $changed changes${if (dependencies > 0) " • $dependencies dependencies" else ""}", style = MaterialTheme.typography.labelMedium)
                if (current.plan.downloadSummary.isNotBlank() || current.plan.diskSummary.isNotBlank()) {
                    Text(listOf(current.plan.downloadSummary, current.plan.diskSummary).filter(String::isNotBlank).joinToString(" • "), style = MaterialTheme.typography.labelMedium)
                }
                Text("${current.decidedBy}: ${current.reason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Collapse package plan" else "Show complete package plan") }
                AnimatedVisibility(showDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        current.plan.items.forEach { item ->
                            val status = when (item.action) {
                                PackageAction.ALREADY_INSTALLED -> "Installed${item.installedVersion?.let { " $it" }.orEmpty()}"
                                PackageAction.INSTALL -> "Install${item.candidateVersion?.let { " $it" }.orEmpty()}"
                                PackageAction.UPDATE -> "Update ${item.installedVersion.orEmpty()} → ${item.candidateVersion ?: "candidate"}"
                                PackageAction.INVALID -> "Invalid"
                            }
                            Text("${item.name} • $status${item.detail.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (review?.state == PackageApprovalState.REQUIRED) OutlinedButton(onClick = { confirm = true }, enabled = !installing && !reviewing) { Text("Review and install changes") }
            result?.let { installed ->
                if (installed.detail.isNotBlank()) GenericToolOutputCard(installed.detail, failed = !installed.success)
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Allow package changes?") },
        text = {
            val plan = review?.plan
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Only missing or outdated packages will be changed. Packages and install scripts run with Arbor's app permissions.")
                plan?.items?.filter { it.action != PackageAction.ALREADY_INSTALLED }?.forEach { item ->
                    Text("• ${item.name}: ${item.action.name.lowercase()}${item.candidateVersion?.let { " $it" }.orEmpty()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                if (plan?.downloadSummary?.isNotBlank() == true) Text("Download: ${plan.downloadSummary}")
                if (plan?.diskSummary?.isNotBlank() == true) Text("Disk: ${plan.diskSummary}")
            }
        },
        dismissButton = { OutlinedButton(onClick = { confirm = false }) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                confirm = false
                scope.launch { installNow() }
            }, enabled = review?.plan?.hasChanges == true) { Text("Allow and install") }
        },
    )
}

@Composable
private fun MarkdownBlock(markdown: String, key: String) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface
    val link = MaterialTheme.colorScheme.primary
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(JLatexMathPlugin.create(42f))
            .build()
    }
    AndroidView(
        factory = {
            TextView(it).apply {
                setTextIsSelectable(true)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                textSize = 16f
                includeFontPadding = false
                linksClickable = true
                setLineSpacing(0f, 1.08f)
            }
        },
        update = { view ->
            view.setTextColor(color.toArgbCompat())
            view.setLinkTextColor(link.toArgbCompat())
            markwon.setMarkdown(view, markdown)
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeBlock(
    language: String,
    code: String,
    onRunPython: suspend (String) -> ExecutionResult,
    onRunUbuntu: suspend (String) -> UbuntuExecutionResult,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var ubuntuResult by remember { mutableStateOf<UbuntuExecutionResult?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { "code" }.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (language.lowercase() in setOf("python", "py")) {
                    IconButton(onClick = {
                        scope.launch {
                            running = true
                            result = runCatching { onRunPython(code) }.getOrElse { ExecutionResult(stderr = it.stackTraceToString()) }
                            running = false
                        }
                    }, enabled = !running) { Icon(Icons.Outlined.PlayArrow, "Run in workspace") }
                }
                if (language.lowercase() in setOf("bash", "sh", "shell", "ubuntu", "debian", "alpine", "linux")) {
                    IconButton(onClick = {
                        scope.launch {
                            running = true
                            ubuntuResult = runCatching { onRunUbuntu(code) }.getOrElse { UbuntuExecutionResult(stderr = it.message.orEmpty()) }
                            running = false
                        }
                    }, enabled = !running) { Icon(Icons.Outlined.PlayArrow, "Run with Linux tools") }
                }
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                }) { Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, "Copy") }
            }
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(14.dp)) {
                Text(highlightedCode(language, code), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, softWrap = false)
            }
            AnimatedVisibility(result != null) {
                result?.let { output -> Column(Modifier.padding(10.dp)) { PythonExecutionCard(output) } }
            }
            AnimatedVisibility(ubuntuResult != null) {
                ubuntuResult?.let { output -> Column(Modifier.padding(10.dp)) { UbuntuExecutionCard(output) } }
            }
        }
    }
}

private fun parseBlocks(text: String): List<RichBlock> {
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    val fence = Regex("```([^\\n`]*)\\n([\\s\\S]*?)```", RegexOption.MULTILINE)
    fence.findAll(text).forEach { match ->
        if (match.range.first > cursor) result += RichBlock.Markdown(text.substring(cursor, match.range.first))
        result += RichBlock.Code(match.groupValues[1].trim(), match.groupValues[2].trimEnd())
        cursor = match.range.last + 1
    }
    if (cursor < text.length) result += RichBlock.Markdown(text.substring(cursor))
    return result.filterNot { it is RichBlock.Markdown && it.text.isBlank() }
}

private fun Color.toArgbCompat(): Int = AndroidColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
