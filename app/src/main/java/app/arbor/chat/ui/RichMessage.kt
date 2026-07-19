package app.arbor.chat.ui

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.SystemClock
import android.net.Uri
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.ArrowKeyMovementMethod
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.UpdateAppearance
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import android.util.TypedValue
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.arbor.chat.sandbox.ExecutionResult
import app.arbor.chat.sandbox.PackageInstallResult
import app.arbor.chat.sandbox.PackageAction
import app.arbor.chat.sandbox.PackageApprovalState
import app.arbor.chat.sandbox.PackageReview
import app.arbor.chat.sandbox.PackagePlan
import app.arbor.chat.sandbox.StaticCodeLinter
import app.arbor.chat.sandbox.UbuntuExecutionResult
import app.arbor.chat.sandbox.UbuntuPackageInstallResult
import app.arbor.chat.widgets.ProgrammableWidgetBlock
import app.arbor.chat.ArborApplication
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal sealed interface RichBlock {
    data class Markdown(val text: String) : RichBlock
    data class Table(val text: String) : RichBlock
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
    val blockParser = remember(operationScope) { StreamingRichBlockParser() }
    val blocks = remember(blockParser, text, streaming) { blockParser.update(text, streaming) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is RichBlock.Markdown -> MarkdownBlock(
                    markdown = block.text,
                    key = "$operationScope:markdown:$index",
                    streaming = streaming && index == blocks.lastIndex,
                )
                is RichBlock.Table -> MarkdownBlock(
                    markdown = block.text,
                    key = "$operationScope:table:$index",
                    streaming = streaming && index == blocks.lastIndex,
                    horizontallyScrollable = true,
                )
                is RichBlock.Code -> StreamingFade(
                    transitionKey = "$operationScope:code:$index:${block.language.lowercase()}",
                    enabled = streaming && index == blocks.lastIndex,
                ) {
                    when (block.language.lowercase()) {
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
            AnimatedVisibility(expanded, enter = streamingFadeIn(), exit = streamingFadeOut()) {
                AutoLintedCodeText(
                    language = "text",
                    code = source,
                    style = MaterialTheme.typography.bodySmall,
                    softWrap = true,
                )
            }
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
                AnimatedVisibility(showDetails, enter = streamingFadeIn(), exit = streamingFadeOut()) {
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

private val ArborReferenceNotation = Regex(
    """\[\[(source|file)\|([^|\]]{1,160})\|([^\]]{1,1500})]]""",
    RegexOption.IGNORE_CASE,
)

internal fun prepareReferenceMarkdown(markdown: String): String = ArborReferenceNotation.replace(markdown) { match ->
    val kind = match.groupValues[1].lowercase()
    val rawLabel = match.groupValues[2].trim().replace("[", "(").replace("]", ")")
    val label = if (rawLabel.length <= 30) rawLabel else rawLabel.take(29).trimEnd() + "…"
    val target = URLEncoder.encode(match.groupValues[3].trim(), Charsets.UTF_8.name()).replace("+", "%20")
    "[$label](arbor-$kind://reference?target=$target)"
}

@Composable
internal fun MarkdownBlock(
    markdown: String,
    key: String,
    streaming: Boolean = false,
    horizontallyScrollable: Boolean = false,
) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface.toArgbCompat()
    val linkColor = MaterialTheme.colorScheme.primary.toArgbCompat()
    val pillBackground = MaterialTheme.colorScheme.secondaryContainer.toArgbCompat()
    val pillForeground = MaterialTheme.colorScheme.onSecondaryContainer.toArgbCompat()
    val tableViewportDp = (LocalConfiguration.current.screenWidthDp - 48).coerceAtLeast(240)
    val tableWidth = remember(markdown, tableViewportDp) {
        estimateMarkdownTableWidthDp(markdown, tableViewportDp).dp
    }
    var pendingReference by remember(key) { mutableStateOf<LinkReferencePreview?>(null) }
    val markwon = remember(context) {
        Markwon.builder(context)
            .bufferType(TextView.BufferType.EDITABLE)
            .textSetter(IncrementalMarkwonTextSetter)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(JLatexMathPlugin.create(42f))
            .build()
    }

    if (horizontallyScrollable) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            MarkdownAndroidView(
                markwon = markwon,
                markdown = markdown,
                streaming = streaming,
                textColor = color,
                linkColor = linkColor,
                pillBackground = pillBackground,
                pillForeground = pillForeground,
                onReference = { pendingReference = it },
                modifier = Modifier.width(tableWidth),
            )
        }
    } else {
        MarkdownAndroidView(
            markwon = markwon,
            markdown = markdown,
            streaming = streaming,
            textColor = color,
            linkColor = linkColor,
            pillBackground = pillBackground,
            pillForeground = pillForeground,
            onReference = { pendingReference = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    pendingReference?.let { reference ->
        AnchoredLinkPreview(reference = reference, onDismiss = { pendingReference = null })
    }
}

@Composable
internal fun StreamingPlainText(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.toArgbCompat()
    val textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
    AndroidView(
        factory = { context ->
            ArborMarkdownTextView(context).apply {
                setTextIsSelectable(true)
                setTextClassifier(TextClassifier.NO_OP)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                includeFontPadding = false
                movementMethod = selectableLinkMovementMethod
                highlightColor = AndroidColor.TRANSPARENT
                setLineSpacing(0f, 1.08f)
                setText("", TextView.BufferType.EDITABLE)
            }
        },
        update = { view ->
            view.setTextColor(color)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            if (view.lastSourceText != text) {
                val previousLength = view.text.length
                val editable = view.editableBuffer()
                if (text.startsWith(view.lastSourceText)) {
                    editable.append(text, view.lastSourceText.length, text.length)
                } else {
                    editable.replace(0, editable.length, text)
                }
                view.lastSourceText = text
                animateAppendedMarkdown(view, previousLength, streaming)
                view.previousRenderedLength = view.text.length
            }
            if (!streaming) view.finishStreamingFade()
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun MarkdownAndroidView(
    markwon: Markwon,
    markdown: String,
    streaming: Boolean,
    textColor: Int,
    linkColor: Int,
    pillBackground: Int,
    pillForeground: Int,
    onReference: (LinkReferencePreview) -> Unit,
    modifier: Modifier,
) {
    val latestRequest by rememberUpdatedState(MarkdownRenderRequest(markdown, streaming))
    val latestOnReference by rememberUpdatedState(onReference)
    val latestLinkColor by rememberUpdatedState(linkColor)
    val latestPillBackground by rememberUpdatedState(pillBackground)
    val latestPillForeground by rememberUpdatedState(pillForeground)
    val renderer = remember(markwon) { IncrementalMarkdownRenderer(markwon) }
    val viewReady = remember(markwon) { CompletableDeferred<ArborMarkdownTextView>() }

    AndroidView(
        factory = { context ->
            ArborMarkdownTextView(context).apply {
                setTextIsSelectable(true)
                setTextClassifier(TextClassifier.NO_OP)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                textSize = 16f
                includeFontPadding = false
                linksClickable = true
                movementMethod = selectableLinkMovementMethod
                highlightColor = AndroidColor.TRANSPARENT
                setLineSpacing(0f, 1.08f)
                setText("", TextView.BufferType.EDITABLE)

                // Streaming content is displayed immediately as an append-only
                // editable tail. Closed Markdown blocks are promoted to styled
                // spans asynchronously; the active block is never reparsed for
                // every token.
                syncSourceText(markdown, streaming, animateAppend = false)
                if (!streaming) {
                    renderer.render(markdown, streaming)?.let { delta ->
                        applyMarkdownDelta(
                            markwon = markwon,
                            delta = delta,
                            streaming = false,
                            linkColor = linkColor,
                            pillBackground = pillBackground,
                            pillForeground = pillForeground,
                            onReference = { latestOnReference(it) },
                        )
                    }
                }
                if (!viewReady.isCompleted) viewReady.complete(this)
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.setHorizontallyScrolling(false)
            view.updateReferenceColors(linkColor, pillBackground, pillForeground)
            view.syncSourceText(markdown, streaming, animateAppend = streaming)
            if (!streaming) view.finishStreamingFade()
        },
        modifier = modifier,
    )

    LaunchedEffect(markwon, renderer, viewReady) {
        val view = viewReady.await()
        snapshotFlow { latestRequest }
            .distinctUntilChanged()
            .collect { request ->
                val delta = withContext(MarkdownRenderDispatcher) {
                    renderer.render(request.source, request.streaming)
                } ?: return@collect

                view.applyMarkdownDelta(
                    markwon = markwon,
                    delta = delta,
                    streaming = request.streaming,
                    linkColor = latestLinkColor,
                    pillBackground = latestPillBackground,
                    pillForeground = latestPillForeground,
                    onReference = { latestOnReference(it) },
                )
            }
    }
}

private data class MarkdownRenderRequest(
    val source: String,
    val streaming: Boolean,
)

private data class MarkdownRenderDelta(
    val source: String,
    val replaceFrom: Int,
    val sourceCharsToReplace: Int,
    val replacement: Spanned,
    val replaceAll: Boolean,
    val validatedSourcePrefixEnd: Int,
    val revision: Long,
)

@OptIn(ExperimentalCoroutinesApi::class)
private val MarkdownRenderDispatcher = Dispatchers.Default.limitedParallelism(2)

/**
 * Incremental Markdown promotion planner.
 *
 * The visible active tail remains ordinary editable text and receives token
 * appends synchronously. This renderer runs only when a complete block becomes
 * stable, replacing that raw source slice with its styled Markwon span. The
 * whole message is parsed exactly once when streaming ends.
 */
private class IncrementalMarkdownRenderer(
    private val markwon: Markwon,
) {
    private var previousSource = ""
    private var previousStreaming = false
    private var committedSourceEnd = 0
    private var committedRenderedLength = 0
    private var revision = 0L

    fun render(source: String, streaming: Boolean): MarkdownRenderDelta? {
        if (source == previousSource && streaming == previousStreaming) return null

        if (!streaming) {
            val rendered = renderFragment(source)
            revision += 1
            val delta = MarkdownRenderDelta(
                source = source,
                replaceFrom = 0,
                sourceCharsToReplace = Int.MAX_VALUE,
                replacement = rendered,
                replaceAll = true,
                validatedSourcePrefixEnd = source.length,
                revision = revision,
            )
            previousSource = source
            previousStreaming = false
            committedSourceEnd = source.length
            committedRenderedLength = rendered.length
            return delta
        }

        val appendOnly = source.startsWith(previousSource)
        if (!appendOnly || !previousStreaming) {
            committedSourceEnd = 0
            committedRenderedLength = 0
        }

        val boundary = stableMarkdownCommitBoundary(source, committedSourceEnd)
        previousSource = source
        previousStreaming = true
        if (boundary <= committedSourceEnd) return null

        val stableSource = source.substring(committedSourceEnd, boundary)
        val rendered = renderFragment(stableSource)
        revision += 1
        val delta = MarkdownRenderDelta(
            source = source,
            replaceFrom = committedRenderedLength,
            sourceCharsToReplace = stableSource.length,
            replacement = rendered,
            replaceAll = false,
            validatedSourcePrefixEnd = boundary,
            revision = revision,
        )
        committedSourceEnd = boundary
        committedRenderedLength += rendered.length
        return delta
    }

    private fun renderFragment(source: String): Spanned {
        if (source.isEmpty()) return SpannableString("")
        return markwon.toMarkdown(prepareReferenceMarkdown(source))
    }
}

private const val MarkdownRetainedBlockBreaks = 1
private const val MarkdownMaximumVolatileTailChars = 12_000

/**
 * Returns a block boundary that can be styled permanently. The current block
 * remains raw and appendable, while every completed block before it can be
 * promoted without touching the rest of the message.
 */
internal fun stableMarkdownCommitBoundary(source: String, committedEnd: Int): Int {
    val start = committedEnd.coerceIn(0, source.length)
    if (start == source.length) return start

    val boundaries = ArrayList<Int>()
    var index = start
    while (index < source.length) {
        if (source[index] == '\n') {
            var next = index + 1
            while (next < source.length && (source[next] == ' ' || source[next] == '\t' || source[next] == '\r')) next++
            if (next < source.length && source[next] == '\n') {
                boundaries += next + 1
                index = next + 1
                continue
            }
        }
        index++
    }

    var boundary = if (boundaries.size >= MarkdownRetainedBlockBreaks) {
        boundaries[boundaries.size - MarkdownRetainedBlockBreaks]
    } else {
        start
    }

    if (source.length - boundary > MarkdownMaximumVolatileTailChars && boundaries.isNotEmpty()) {
        boundary = boundaries.last()
    }

    if (source.length - boundary > MarkdownMaximumVolatileTailChars) {
        val target = (source.length - MarkdownMaximumVolatileTailChars).coerceAtLeast(start)
        var soft = source.indexOf('\n', target)
        if (soft < 0) soft = source.indexOf(' ', target)
        if (soft in (start + 256) until source.length) {
            val candidate = source.substring(start, soft + 1)
            if (candidate.none { it == '`' || it == '*' || it == '_' || it == '~' || it == '[' || it == '$' }) {
                boundary = soft + 1
            }
        }
    }
    return boundary.coerceIn(start, source.length)
}

private val IncrementalMarkwonTextSetter = Markwon.TextSetter { textView, parsed, _, onComplete ->
    if (textView is ArborMarkdownTextView) {
        textView.applyPendingParsedMarkdown(parsed)
    } else {
        textView.setText(parsed, TextView.BufferType.EDITABLE)
    }
    onComplete.run()
}

@SuppressLint("AppCompatCustomView")
private class ArborMarkdownTextView(context: Context) : TextView(context) {
    var previousRenderedLength: Int = 0
    var lastSourceText: String = ""
    val selectableLinkMovementMethod = SelectableLinkMovementMethod()
    var pendingMarkdownDelta: MarkdownRenderDelta? = null
    var lastAppliedRevision: Long = -1L
    var lastDeltaReplaceFrom: Int = 0

    private var fadeFrameScheduled = false
    private var fadeDeadlineUptimeMillis = 0L
    private val fadeFrame = object : Runnable {
        override fun run() {
            fadeFrameScheduled = false
            val now = SystemClock.uptimeMillis()
            removeExpiredStreamingAlphaSpans(now)
            invalidate()
            if (isAttachedToWindow && now < fadeDeadlineUptimeMillis) {
                fadeFrameScheduled = true
                postOnAnimation(this)
            }
        }
    }

    fun editableBuffer(): Editable {
        val existing = text
        if (existing is Editable) return existing
        setText(existing, BufferType.EDITABLE)
        return editableText
    }

    fun syncSourceText(source: String, streaming: Boolean, animateAppend: Boolean) {
        if (source == lastSourceText) return
        val editable = editableBuffer()
        val previousViewLength = editable.length
        if (source.startsWith(lastSourceText)) {
            editable.append(source, lastSourceText.length, source.length)
        } else {
            finishStreamingFade()
            editable.replace(0, editable.length, source)
        }
        lastSourceText = source
        previousRenderedLength = editable.length
        if (animateAppend && streaming && editable.length > previousViewLength) {
            animateAppendedMarkdown(this, previousViewLength, true)
        }
    }

    fun applyPendingParsedMarkdown(parsed: Spanned) {
        val delta = pendingMarkdownDelta ?: return
        val editable = editableBuffer()
        val from = delta.replaceFrom.coerceIn(0, editable.length)
        val to = if (delta.replaceAll) {
            editable.length
        } else {
            (from + delta.sourceCharsToReplace).coerceIn(from, editable.length)
        }
        lastDeltaReplaceFrom = from
        editable.replace(from, to, parsed)
        lastAppliedRevision = delta.revision
        pendingMarkdownDelta = null
    }

    fun scheduleStreamingFade(deadlineUptimeMillis: Long) {
        fadeDeadlineUptimeMillis = maxOf(fadeDeadlineUptimeMillis, deadlineUptimeMillis)
        if (!fadeFrameScheduled && isAttachedToWindow) {
            fadeFrameScheduled = true
            postOnAnimation(fadeFrame)
        }
    }

    fun finishStreamingFade() {
        fadeDeadlineUptimeMillis = 0L
        if (fadeFrameScheduled) {
            removeCallbacks(fadeFrame)
            fadeFrameScheduled = false
        }
        clearStreamingAlphaSpans()
    }

    fun updateReferenceColors(linkColor: Int, pillBackground: Int, pillForeground: Int) {
        val spannable = text as? Spannable ?: return
        spannable.getSpans(0, spannable.length, PreviewClickableSpan::class.java)
            .forEach { it.color = linkColor }
        spannable.getSpans(0, spannable.length, LinkPillSpan::class.java)
            .forEach {
                it.backgroundColor = pillBackground
                it.foregroundColor = pillForeground
            }
        invalidate()
    }

    private fun removeExpiredStreamingAlphaSpans(nowUptimeMillis: Long) {
        val spannable = text as? Spannable ?: return
        spannable.getSpans(0, spannable.length, StreamingAlphaSpan::class.java)
            .filter { it.isFinished(nowUptimeMillis) }
            .forEach(spannable::removeSpan)
    }

    private fun clearStreamingAlphaSpans() {
        val spannable = text as? Spannable ?: return
        spannable.getSpans(0, spannable.length, StreamingAlphaSpan::class.java)
            .forEach(spannable::removeSpan)
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (SystemClock.uptimeMillis() < fadeDeadlineUptimeMillis && !fadeFrameScheduled) {
            fadeFrameScheduled = true
            postOnAnimation(fadeFrame)
        }
    }

    override fun onDetachedFromWindow() {
        if (fadeFrameScheduled) removeCallbacks(fadeFrame)
        fadeFrameScheduled = false
        super.onDetachedFromWindow()
    }
}

private fun ArborMarkdownTextView.applyMarkdownDelta(
    markwon: Markwon,
    delta: MarkdownRenderDelta,
    streaming: Boolean,
    linkColor: Int,
    pillBackground: Int,
    pillForeground: Int,
    onReference: (LinkReferencePreview) -> Unit,
) {
    if (delta.revision <= lastAppliedRevision) return
    val sourceStillMatches = if (delta.replaceAll) {
        lastSourceText == delta.source
    } else {
        lastSourceText.length >= delta.validatedSourcePrefixEnd &&
            delta.source.length >= delta.validatedSourcePrefixEnd &&
            lastSourceText.regionMatches(
                thisOffset = 0,
                other = delta.source,
                otherOffset = 0,
                length = delta.validatedSourcePrefixEnd,
            )
    }
    if (!sourceStillMatches) return

    pendingMarkdownDelta = delta
    markwon.setParsedMarkdown(this, delta.replacement)
    installReferenceSpans(
        view = this,
        start = lastDeltaReplaceFrom,
        end = text.length,
        linkColor = linkColor,
        pillBackground = pillBackground,
        pillForeground = pillForeground,
        onClick = onReference,
    )
    previousRenderedLength = text.length
    if (!streaming) finishStreamingFade()
}

private class StreamingAlphaSpan(
    private val startedAtUptimeMillis: Long,
    private val durationMillis: Long,
) : CharacterStyle(), UpdateAppearance {
    private val finishedAtUptimeMillis = startedAtUptimeMillis + durationMillis

    fun isFinished(nowUptimeMillis: Long): Boolean = nowUptimeMillis >= finishedAtUptimeMillis

    override fun updateDrawState(textPaint: TextPaint) {
        val elapsed = SystemClock.uptimeMillis() - startedAtUptimeMillis
        val progress = (elapsed.toFloat() / durationMillis.coerceAtLeast(1L)).coerceIn(0f, 1f)
        val alphaScale = StreamingFadeStartAlpha + (1f - StreamingFadeStartAlpha) * progress
        textPaint.alpha = (textPaint.alpha * alphaScale).roundToInt().coerceIn(0, 255)
    }
}

private fun animateAppendedMarkdown(view: ArborMarkdownTextView, previousLength: Int, streaming: Boolean) {
    if (!streaming) {
        view.finishStreamingFade()
        return
    }
    val spannable = view.text as? Spannable ?: return
    val currentLength = spannable.length
    val start = previousLength.coerceIn(0, currentLength)
    if (currentLength <= start) return

    val durationScale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ValueAnimator.getDurationScale()
    } else {
        1f
    }
    val durationMillis = (StreamingFadeDurationMillis * durationScale).roundToLong()
    if (durationMillis <= 0L) return

    // Each append batch keeps its own start time. New tokens never restart the
    // fade of already-visible text, so the animation continues at display FPS.
    val startedAt = SystemClock.uptimeMillis()
    spannable.setSpan(
        StreamingAlphaSpan(startedAt, durationMillis),
        start,
        currentLength,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    view.scheduleStreamingFade(startedAt + durationMillis)
    view.invalidate()
}

private class SelectableLinkMovementMethod : ArrowKeyMovementMethod() {
    private var pressedSpan: ClickableSpan? = null
    private var downX = 0f
    private var downY = 0f

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val span = clickableSpanAt(widget, buffer, event)
        val slop = ViewConfiguration.get(widget.context).scaledTouchSlop.toFloat()
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (span != null) {
                    pressedSpan = span
                    downX = event.x
                    downY = event.y
                    true
                } else {
                    pressedSpan = null
                    super.onTouchEvent(widget, buffer, event)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pressed = pressedSpan
                if (pressed != null) {
                    val moved = kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop
                    if (moved || span !== pressed) pressedSpan = null
                    true
                } else {
                    super.onTouchEvent(widget, buffer, event)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedSpan
                pressedSpan = null
                if (pressed != null && span === pressed) {
                    pressed.onClick(widget)
                    true
                } else {
                    super.onTouchEvent(widget, buffer, event)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedSpan = null
                super.onTouchEvent(widget, buffer, event)
            }
            else -> super.onTouchEvent(widget, buffer, event)
        }
    }

    private fun clickableSpanAt(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val layout = widget.layout ?: return null
        if (y < 0 || y > layout.height) return null
        val line = layout.getLineForVertical(y.toInt())
        val offset = layout.getOffsetForHorizontal(line, x)
        return buffer.getSpans(offset, offset, ClickableSpan::class.java).firstOrNull()
    }
}

private fun installReferenceSpans(
    view: TextView,
    start: Int,
    end: Int,
    linkColor: Int,
    pillBackground: Int,
    pillForeground: Int,
    onClick: (LinkReferencePreview) -> Unit,
) {
    val text = view.text as? Spannable ?: return
    val safeStart = start.coerceIn(0, text.length)
    val safeEnd = end.coerceIn(safeStart, text.length)
    text.getSpans(safeStart, safeEnd, URLSpan::class.java).forEach { span ->
        val spanStart = text.getSpanStart(span)
        val spanEnd = text.getSpanEnd(span)
        if (spanStart < 0 || spanEnd <= spanStart) return@forEach
        val raw = span.url.orEmpty()
        val parsed = Uri.parse(raw)
        val kind = when (parsed.scheme?.lowercase()) {
            "arbor-source" -> LinkReferenceKind.SOURCE
            "arbor-file" -> LinkReferenceKind.FILE
            else -> LinkReferenceKind.LINK
        }
        val target = if (kind == LinkReferenceKind.LINK) raw else parsed.getQueryParameter("target").orEmpty()
        val label = text.subSequence(spanStart, spanEnd).toString()
        text.removeSpan(span)
        text.setSpan(
            PreviewClickableSpan(
                color = linkColor,
                kind = kind,
                label = label,
                target = target,
                onClick = onClick,
            ),
            spanStart,
            spanEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (kind != LinkReferenceKind.LINK) {
            text.setSpan(
                LinkPillSpan(
                    icon = if (kind == LinkReferenceKind.FILE) "▣" else "↗",
                    backgroundColor = pillBackground,
                    foregroundColor = pillForeground,
                ),
                spanStart,
                spanEnd,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    view.movementMethod = (view as? ArborMarkdownTextView)?.selectableLinkMovementMethod
        ?: ArrowKeyMovementMethod.getInstance()
}

private class PreviewClickableSpan(
    var color: Int,
    private val kind: LinkReferenceKind,
    private val label: String,
    private val target: String,
    private val onClick: (LinkReferencePreview) -> Unit,
) : ClickableSpan() {
    override fun onClick(widget: View) {
        val textView = widget as? TextView
        val spanned = textView?.text as? Spanned
        val start = spanned?.getSpanStart(this)?.coerceAtLeast(0) ?: 0
        val end = spanned?.getSpanEnd(this)?.coerceAtLeast(start + 1) ?: (start + 1)
        onClick(
            LinkReferencePreview(
                kind = kind,
                label = label,
                target = target,
                anchorBoundsInWindow = spanBoundsInWindow(textView, start, end),
            ),
        )
    }

    override fun updateDrawState(ds: TextPaint) {
        ds.color = color
        ds.isUnderlineText = false
    }
}

private fun spanBoundsInWindow(view: TextView?, start: Int, end: Int): IntRect {
    if (view == null) return IntRect(0, 0, 1, 1)
    val location = IntArray(2)
    view.getLocationInWindow(location)
    val layout = view.layout
    if (layout == null || start !in 0..view.text.length || end !in 0..view.text.length) {
        return IntRect(location[0], location[1], location[0] + view.width.coerceAtLeast(1), location[1] + view.height.coerceAtLeast(1))
    }
    val safeEnd = end.coerceAtLeast(start + 1).coerceAtMost(view.text.length)
    val startLine = layout.getLineForOffset(start.coerceAtMost(view.text.length))
    val endLine = layout.getLineForOffset((safeEnd - 1).coerceAtLeast(0))
    val left = if (startLine == endLine) {
        minOf(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(safeEnd))
    } else layout.getLineLeft(startLine)
    val right = if (startLine == endLine) {
        maxOf(layout.getPrimaryHorizontal(start), layout.getPrimaryHorizontal(safeEnd))
    } else layout.getLineRight(startLine)
    val localLeft = (left + view.totalPaddingLeft - view.scrollX).roundToInt()
    val localRight = (right + view.totalPaddingLeft - view.scrollX).roundToInt().coerceAtLeast(localLeft + 1)
    val localTop = layout.getLineTop(startLine) + view.totalPaddingTop - view.scrollY
    val localBottom = layout.getLineBottom(startLine) + view.totalPaddingTop - view.scrollY
    return IntRect(
        left = location[0] + localLeft,
        top = location[1] + localTop,
        right = location[0] + localRight,
        bottom = location[1] + localBottom,
    )
}

private class LinkPillSpan(
    private val icon: String,
    var backgroundColor: Int,
    var foregroundColor: Int,
) : ReplacementSpan() {
    private val horizontalPadding = 5f
    private val verticalPadding = 1f
    private val iconGap = 3f
    private val textScale = 0.84f

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        val oldSize = paint.textSize
        paint.textSize = oldSize * textScale
        val width = horizontalPadding * 2 + paint.measureText(icon) + iconGap + paint.measureText(text, start, end)
        paint.textSize = oldSize
        return width.roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val oldColor = paint.color
        val oldStyle = paint.style
        val oldAntiAlias = paint.isAntiAlias
        val oldTextSize = paint.textSize
        paint.isAntiAlias = true
        paint.textSize = oldTextSize * textScale
        val width = (horizontalPadding * 2 + paint.measureText(icon) + iconGap + paint.measureText(text, start, end))
        val fm = paint.fontMetrics
        val baseline = (top + bottom - fm.ascent - fm.descent) / 2f
        val rect = RectF(
            x,
            baseline + fm.ascent - verticalPadding,
            x + width,
            baseline + fm.descent + verticalPadding,
        )
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, paint)
        paint.color = foregroundColor
        val iconX = x + horizontalPadding
        canvas.drawText(icon, iconX, baseline, paint)
        val labelX = iconX + paint.measureText(icon) + iconGap
        canvas.drawText(text, start, end, labelX, baseline, paint)
        paint.color = oldColor
        paint.style = oldStyle
        paint.isAntiAlias = oldAntiAlias
        paint.textSize = oldTextSize
    }
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
    val lint = remember(language, code) { StaticCodeLinter.lint(language, code) }
    var copied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var ubuntuResult by remember { mutableStateOf<UbuntuExecutionResult?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(language.ifBlank { "code" }.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CodeLintBadge(lint)
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
                AutoLintedCodeText(
                    language = language,
                    code = code,
                    lintResult = lint,
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = false,
                )
            }
            AnimatedVisibility(result != null, enter = streamingFadeIn(), exit = streamingFadeOut()) {
                result?.let { output -> Column(Modifier.padding(10.dp)) { PythonExecutionCard(output) } }
            }
            AnimatedVisibility(ubuntuResult != null, enter = streamingFadeIn(), exit = streamingFadeOut()) {
                ubuntuResult?.let { output -> Column(Modifier.padding(10.dp)) { UbuntuExecutionCard(output) } }
            }
        }
    }
}

/**
 * Incremental top-level block scanner. It only examines bytes appended since
 * the previous update, promotes completed fenced blocks once, and keeps just
 * the active Markdown/code tail mutable. This prevents the old full-message
 * fence regex and table splitter from running for every token.
 */
internal class StreamingRichBlockParser {
    private val completed = mutableListOf<RichBlock>()
    private var previousText = ""
    private var scanOffset = 0
    private var segmentStart = 0
    private var inFence = false
    private var fenceCharacter = '`'
    private var fenceLength = 3
    private var fenceLanguage = ""
    private var codeContentStart = 0
    private var currentMarkdownHasPipe = false

    fun update(text: String, streaming: Boolean): List<RichBlock> {
        if (!text.startsWith(previousText)) reset()
        scan(text, includePartialLastLine = !streaming)
        previousText = text

        val result = ArrayList<RichBlock>(completed.size + 2)
        result += completed
        if (inFence) {
            val code = text.substring(codeContentStart.coerceAtMost(text.length)).trimEnd('\r', '\n')
            result += RichBlock.Code(fenceLanguage, code)
        } else {
            appendCurrentMarkdown(result, text.substring(segmentStart.coerceAtMost(text.length)))
        }
        return result.filterNot {
            (it is RichBlock.Markdown && it.text.isBlank()) ||
                (it is RichBlock.Table && it.text.isBlank())
        }
    }

    private fun reset() {
        completed.clear()
        previousText = ""
        scanOffset = 0
        segmentStart = 0
        inFence = false
        fenceCharacter = '`'
        fenceLength = 3
        fenceLanguage = ""
        codeContentStart = 0
        currentMarkdownHasPipe = false
    }

    private fun scan(text: String, includePartialLastLine: Boolean) {
        while (scanOffset < text.length) {
            val newline = text.indexOf('\n', scanOffset)
            if (newline < 0 && !includePartialLastLine) break
            val lineEnd = if (newline >= 0) newline + 1 else text.length
            val contentEnd = if (newline >= 0) newline else text.length
            val lineStart = scanOffset
            val line = text.substring(lineStart, contentEnd).trimEnd('\r')

            if (inFence) {
                if (isClosingFence(line, fenceCharacter, fenceLength)) {
                    val code = text.substring(codeContentStart, lineStart).trimEnd('\r', '\n')
                    completed += RichBlock.Code(fenceLanguage, code)
                    inFence = false
                    segmentStart = lineEnd
                    currentMarkdownHasPipe = false
                }
            } else {
                val opening = openingFence(line)
                if (opening != null) {
                    appendCurrentMarkdown(completed, text.substring(segmentStart, lineStart))
                    inFence = true
                    fenceCharacter = opening.character
                    fenceLength = opening.length
                    fenceLanguage = opening.info
                    codeContentStart = lineEnd
                    currentMarkdownHasPipe = false
                } else if ('|' in line) {
                    currentMarkdownHasPipe = true
                }
            }
            scanOffset = lineEnd
        }

        if (!inFence && scanOffset < text.length && text.indexOf('|', scanOffset) >= 0) {
            currentMarkdownHasPipe = true
        }
    }

    private fun appendCurrentMarkdown(destination: MutableList<RichBlock>, markdown: String) {
        if (markdown.isBlank()) return
        if (currentMarkdownHasPipe) appendMarkdownBlocks(destination, markdown)
        else destination += RichBlock.Markdown(markdown)
    }
}

private data class FenceOpening(val character: Char, val length: Int, val info: String)

private fun openingFence(line: String): FenceOpening? {
    var index = 0
    while (index < line.length && index < 3 && line[index] == ' ') index++
    if (index >= line.length) return null
    val character = line[index]
    if (character != '`' && character != '~') return null
    var end = index
    while (end < line.length && line[end] == character) end++
    val length = end - index
    if (length < 3) return null
    return FenceOpening(character, length, line.substring(end).trim())
}

private fun isClosingFence(line: String, character: Char, minimumLength: Int): Boolean {
    var index = 0
    while (index < line.length && index < 3 && line[index] == ' ') index++
    var end = index
    while (end < line.length && line[end] == character) end++
    return end - index >= minimumLength && line.substring(end).all { it == ' ' || it == '\t' }
}

private fun parseBlocks(text: String): List<RichBlock> {
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    val fence = Regex("""```([^\n`]*)\n([\s\S]*?)```""", RegexOption.MULTILINE)
    fence.findAll(text).forEach { match ->
        if (match.range.first > cursor) appendMarkdownBlocks(result, text.substring(cursor, match.range.first))
        result += RichBlock.Code(match.groupValues[1].trim(), match.groupValues[2].trimEnd())
        cursor = match.range.last + 1
    }
    if (cursor < text.length) appendMarkdownBlocks(result, text.substring(cursor))
    return result.filterNot {
        (it is RichBlock.Markdown && it.text.isBlank()) || (it is RichBlock.Table && it.text.isBlank())
    }
}

internal data class MarkdownSegment(val table: Boolean, val text: String)

private val MarkdownTableSeparator = Regex(
    """^\s*\|?\s*:?-{3,}:?\s*(?:\|\s*:?-{3,}:?\s*)+\|?\s*$""",
)

/** Splits complete Markdown tables so only the table receives horizontal scrolling. */
internal fun splitMarkdownTables(markdown: String): List<MarkdownSegment> {
    if (markdown.isBlank()) return emptyList()
    val lines = markdown.split('\n')
    val segments = mutableListOf<MarkdownSegment>()
    var plainStart = 0
    var index = 1

    while (index < lines.size) {
        val header = lines[index - 1]
        val separator = lines[index]
        if ('|' in header && MarkdownTableSeparator.matches(separator)) {
            val tableStart = index - 1
            if (tableStart > plainStart) {
                segments += MarkdownSegment(false, lines.subList(plainStart, tableStart).joinToString("\n"))
            }
            var tableEnd = index + 1
            while (tableEnd < lines.size && lines[tableEnd].isNotBlank() && '|' in lines[tableEnd]) {
                tableEnd++
            }
            segments += MarkdownSegment(true, lines.subList(tableStart, tableEnd).joinToString("\n"))
            plainStart = tableEnd
            index = tableEnd + 1
        } else {
            index++
        }
    }

    if (plainStart < lines.size) {
        segments += MarkdownSegment(false, lines.subList(plainStart, lines.size).joinToString("\n"))
    }
    return segments.filter { it.text.isNotBlank() }
}

internal fun estimateMarkdownTableWidthDp(markdown: String, viewportDp: Int): Int {
    val rows = markdown.lineSequence()
        .filter { line -> line.isNotBlank() && !MarkdownTableSeparator.matches(line) && '|' in line }
        .map { line ->
            line.trim().trim('|').split('|').map { cell -> cell.trim().replace(Regex("""[`*_~]"""), "") }
        }
        .toList()
    val columnCount = rows.maxOfOrNull { it.size } ?: return viewportDp.coerceAtLeast(240)
    val estimated = (0 until columnCount).sumOf { column ->
        val longest = rows.maxOfOrNull { row -> row.getOrNull(column)?.length ?: 0 } ?: 0
        (longest.coerceIn(4, 36) * 8 + 28).coerceIn(84, 316)
    }
    return estimated.coerceAtLeast(viewportDp.coerceAtLeast(240)).coerceAtMost(2400)
}

private fun appendMarkdownBlocks(destination: MutableList<RichBlock>, markdown: String) {
    splitMarkdownTables(markdown).forEach { segment ->
        destination += if (segment.table) RichBlock.Table(segment.text) else RichBlock.Markdown(segment.text)
    }
}

private fun Color.toArgbCompat(): Int = AndroidColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
