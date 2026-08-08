package app.xylune.chat.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.ArrowKeyMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.textclassifier.TextClassifier
import android.widget.TextView
import android.util.TypedValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import app.xylune.chat.sandbox.ExecutionResult
import app.xylune.chat.sandbox.ExecutionProgress
import app.xylune.chat.sandbox.PackageInstallResult
import app.xylune.chat.sandbox.PackageInstallProgress
import app.xylune.chat.sandbox.PackageAction
import app.xylune.chat.sandbox.PackageApprovalState
import app.xylune.chat.sandbox.PackageReview
import app.xylune.chat.sandbox.PackagePlan
import app.xylune.chat.sandbox.UbuntuExecutionResult
import app.xylune.chat.sandbox.UbuntuPackageInstallResult
import app.xylune.chat.widgets.SnippetBlock
import app.xylune.chat.widgets.WidgetInstallBlock
import app.xylune.chat.XyluneApplication
import app.xylune.chat.generated.GeneratedBlockRepairState
import app.xylune.chat.generated.GeneratedBlockType
import app.xylune.chat.generated.GeneratedContentCapabilityRegistry
import app.xylune.chat.generated.GeneratedValidationError
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.conflate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.math.roundToInt

private val MarkdownTableStreamHint = Regex(
    """(?m)^\s*\|?.+\|.+\r?\n\s*\|?\s*:?-{3,}:?\s*\|""",
)
private const val MarkdownTableDetectionWindowChars = 8_192

/**
 * Table detection runs for every streamed database update, so it must stay
 * bounded. Sampling both ends catches a table that starts after prose as soon
 * as its separator reaches the live tail; RichMessage then latches the result.
 */
internal fun containsMarkdownTableCandidate(markdown: String): Boolean {
    if (markdown.length <= MarkdownTableDetectionWindowChars * 2) {
        return MarkdownTableStreamHint.containsMatchIn(markdown)
    }
    val sample = buildString(MarkdownTableDetectionWindowChars * 2 + 1) {
        append(markdown, 0, MarkdownTableDetectionWindowChars)
        append('\n')
        append(markdown, markdown.length - MarkdownTableDetectionWindowChars, markdown.length)
    }
    return MarkdownTableStreamHint.containsMatchIn(sample)
}

internal sealed interface RichBlock {
    data class Markdown(val text: String) : RichBlock
    data class Table(val text: String) : RichBlock
    data class Code(val language: String, val code: String, val complete: Boolean = true) : RichBlock
}

internal data class StableRichBlock(
    val key: String,
    val block: RichBlock,
    val liveTail: Boolean,
)

/**
 * Append-only parser state for a single streamed response. Completed Markdown
 * regions are parsed once and retained; only the unfinished tail is reparsed.
 */
internal class IncrementalRichTextParser {
    private var previousLength = 0
    private var previousSuffix = ""
    private var committedOffset = 0
    private var nextStableId = 0L
    private val committedBlocks = mutableListOf<StableRichBlock>()

    fun update(source: String, streaming: Boolean): List<StableRichBlock> {
        val suffixStart = previousLength - previousSuffix.length
        val appendCompatible = source.length >= previousLength &&
            (previousSuffix.isEmpty() || source.regionMatches(suffixStart, previousSuffix, 0, previousSuffix.length))
        if (!appendCompatible) reset()

        val uncommitted = source.substring(committedOffset.coerceAtMost(source.length))
        val stableLength = if (streaming) stableMarkdownPrefixLength(uncommitted) else uncommitted.length
        if (stableLength > 0) {
            parseBlocks(uncommitted.substring(0, stableLength), streaming = false).forEach { block ->
                committedBlocks += StableRichBlock("stable-${nextStableId++}", block, liveTail = false)
            }
            committedOffset += stableLength
        }

        val tail = source.substring(committedOffset)
        val tailBlocks = if (tail.isBlank()) emptyList() else parseBlocks(tail, streaming = streaming)
        previousLength = source.length
        previousSuffix = source.takeLast(96)
        return committedBlocks + tailBlocks.mapIndexed { index, block ->
            StableRichBlock("tail-$committedOffset-$index", block, liveTail = streaming)
        }
    }

    private fun reset() {
        previousLength = 0
        previousSuffix = ""
        committedOffset = 0
        nextStableId = 0L
        committedBlocks.clear()
    }
}

@Composable
internal fun RichMessage(
    operationScope: String,
    text: String,
    streaming: Boolean = false,
    displayOnly: Boolean = false,
    staticContent: Boolean = false,
    onRunPython: suspend (String, suspend (ExecutionProgress) -> Unit) -> ExecutionResult,
    onRunUbuntu: suspend (String, suspend (ExecutionProgress) -> Unit) -> UbuntuExecutionResult,
    onReviewPythonPackages: suspend (String, String) -> PackageReview,
    onInstallPackages: suspend (String, String, PackagePlan) -> PackageInstallResult,
    onReviewUbuntuPackages: suspend (String, String) -> PackageReview,
    onInstallUbuntuPackages: suspend (String, String, PackagePlan, suspend (PackageInstallProgress) -> Unit) -> UbuntuPackageInstallResult,
    onWidgetSubmit: (String) -> Unit,
    onReviewWidgetSecurity: suspend (String) -> String,
    onRepairGeneratedBlock: suspend (
        String,
        String,
        GeneratedBlockType,
        String,
        List<GeneratedValidationError>,
        Boolean,
        (GeneratedBlockRepairState) -> Unit,
    ) -> GeneratedBlockRepairState,
    onAcceptGeneratedEdit: suspend (GeneratedBlockRepairState, String) -> GeneratedBlockRepairState,
    workingCardViewport: WorkingCardViewportController,
) {
    val context = LocalContext.current
    val crashReporter = (context.applicationContext as? XyluneApplication)?.container?.crashReporter
    val safeRendering by crashReporter?.renderSafeMode?.collectAsState() ?: remember { mutableStateOf(false) }
    val tableCandidate = remember(text) { containsMarkdownTableCandidate(text) }
    var tableLike by remember(operationScope) { mutableStateOf(tableCandidate) }
    var firstTableStart by remember(operationScope) {
        mutableStateOf(if (tableCandidate) findFirstMarkdownTableStart(text) else null)
    }
    LaunchedEffect(tableCandidate, text.length) {
        // Latch both the presence and start of the first table while the header
        // is still in the small detection window. This lets a huge live table
        // use a bounded renderer without rescanning or copying the full source.
        if (tableCandidate) {
            tableLike = true
            if (firstTableStart == null) firstTableStart = findFirstMarkdownTableStart(text)
        }
    }
    val useTableCadence = tableLike || tableCandidate
    val renderedText = rememberBatchedStreamingText(
        text = text,
        streaming = streaming,
        // Agora-style cadence: table/Markdown layout is intentionally not
        // rebuilt for every provider token. Each table flush contains the newest
        // complete source snapshot, while ordinary prose remains more fluid.
        intervalNanos = if (useTableCadence) 250_000_000L else 16_500_000L,
        maxStepChars = if (useTableCadence) Int.MAX_VALUE else 48,
    )
    // Completion can arrive while the frame-batched renderer still has a
    // backlog. Keep the tail on the streaming path until it catches up.
    val renderStreaming = isStreamingRenderActive(streaming, renderedText, text)
    val useWholeMessageTablePreview = renderStreaming && tableLike &&
        isOversizedTablePreviewSource(
            renderedText,
            maxChars = StreamingTablePreviewMaxChars,
            maxLines = StreamingTablePreviewMaxLines,
        )
    if (useWholeMessageTablePreview) {
        // Once a table becomes very large, never feed the complete growing
        // source through Markwon or splitMarkdownTables on the UI thread. The
        // bounded renderer still shows a real aligned table, including newest
        // rows, instead of falling back to raw Markdown.
        val tableStart = (firstTableStart ?: 0).coerceIn(0, renderedText.length)
        val prefix = remember(renderedText, tableStart) { renderedText.substring(0, tableStart).trimEnd() }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (prefix.isNotBlank()) {
                val markwon = remember(context.applicationContext) { XyluneMarkwonCache.get(context.applicationContext) }
                MarkdownBlock(
                    markwon = markwon,
                    markdown = prefix,
                    key = "$operationScope:streaming-table-prefix",
                    streaming = false,
                )
            }
            StreamingFade(
                transitionKey = "$operationScope:streaming-table-source",
                enabled = renderStreaming,
            ) {
                StreamingTablePreviewText(
                    markdown = renderedText,
                    startOffset = tableStart,
                    streaming = true,
                )
            }
            StreamingTokenPulse(visible = streaming, modifier = Modifier.padding(top = 2.dp))
        }
        return
    }

    val incrementalParser = remember(operationScope) { IncrementalRichTextParser() }
    var blocks by remember(operationScope) { mutableStateOf<List<StableRichBlock>>(emptyList()) }
    // LaunchedEffect itself is intentionally keyed only by the message scope so
    // one serial parser survives the entire response. The values consumed by
    // snapshotFlow must be State objects, though: closing over the plain String
    // returned by rememberBatchedStreamingText captures the first composition
    // forever. That bug left the table header and every later token invisible
    // until the chat was reopened.
    val latestRenderedText = rememberUpdatedState(renderedText)
    val latestRenderStreaming = rememberUpdatedState(renderStreaming)
    LaunchedEffect(operationScope) {
        snapshotFlow { latestRenderedText.value to latestRenderStreaming.value }
            .conflate()
            .collect { (source, active) ->
                // Keep parsing serial and off the UI thread. Conflation drops
                // obsolete token snapshots while guaranteeing that the newest
                // table state and any Markdown after it are eventually applied.
                blocks = withContext(Dispatchers.Default) {
                    incrementalParser.update(source, active)
                }
            }
    }
    val staticBlocks = remember(operationScope, renderedText, staticContent) {
        if (!staticContent) emptyList()
        else parseBlocks(renderedText, streaming = false).mapIndexed { index, block ->
            StableRichBlock("static-$index", block, liveTail = false)
        }
    }
    val visibleBlocks = if (staticContent) staticBlocks else blocks
    val markwon = remember(context.applicationContext) { XyluneMarkwonCache.get(context.applicationContext) }
    val sourceReferences = remember(renderedText) { extractSourceReferences(renderedText) }
    Column(
        modifier = Modifier.noOpBringIntoView(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleBlocks.forEach { parsed ->
            key("$operationScope:${parsed.key}") {
                val block = parsed.block
                val live = renderStreaming && parsed.liveTail
                when (block) {
                    is RichBlock.Markdown -> StreamingFade(
                        transitionKey = "$operationScope:${parsed.key}:markdown",
                        enabled = live,
                    ) {
                        MarkdownBlock(
                            markwon = markwon,
                            markdown = block.text,
                            key = "$operationScope:${parsed.key}",
                            streaming = live,
                        )
                    }
                    is RichBlock.Table -> StreamingFade(
                        transitionKey = "$operationScope:${parsed.key}:table",
                        enabled = live,
                    ) {
                        MarkdownBlock(
                            markwon = markwon,
                            markdown = block.text,
                            key = "$operationScope:${parsed.key}",
                            horizontallyScrollable = true,
                            streaming = live,
                        )
                    }
                    is RichBlock.Code -> StreamingFade(
                        transitionKey = "$operationScope:${parsed.key}:code:${block.language.lowercase()}",
                        enabled = live,
                    ) {
                        val operationKey = "$operationScope:${parsed.key}"
                        if (!shouldExecuteRichCodeBlock(displayOnly, block.complete)) {
                            CodeBlock(block.language, block.code, onRunPython, onRunUbuntu, executable = false)
                        } else when (block.language.lowercase()) {
                            "mermaid", "graph", "diagram", "dot", "graphviz",
                            "chart", "xylune-chart", "bar-chart", "barchart", "line-chart", "pie-chart",
                            "xylune-snippet", "xylune-widget" -> {
                                val capability = checkNotNull(GeneratedContentCapabilityRegistry.capability(block.language))
                                val preparationErrors = remember(block.code, capability.type) {
                                    generatedPreparationErrors(capability.type, block.code)
                                }
                                RepairableGeneratedContent(
                                    blockId = operationKey,
                                    messageId = operationScope.substringBefore(':'),
                                    type = capability.type,
                                    source = block.code,
                                    preparationErrors = preparationErrors,
                                    repair = { blockId, messageId, type, source, errors, newCycle, progress ->
                                        onRepairGeneratedBlock(blockId, messageId, type, source, errors, newCycle, progress)
                                    },
                                    acceptEdit = onAcceptGeneratedEdit,
                                    workingCardViewport = workingCardViewport,
                                ) { repaired ->
                                    when (capability.type) {
                                        GeneratedBlockType.DIAGRAM -> if (safeRendering) SafeGeneratedBlock("Diagram", repaired) { crashReporter?.setRenderSafeMode(false) } else NativeDiagramBlock(repaired)
                                        GeneratedBlockType.CHART -> if (safeRendering) SafeGeneratedBlock("Chart", repaired) { crashReporter?.setRenderSafeMode(false) } else NativeChartBlock(repaired)
                                        GeneratedBlockType.CHAT_UI -> SnippetBlock(repaired, onWidgetSubmit)
                                        GeneratedBlockType.HOME_WIDGET -> WidgetInstallBlock(repaired)
                                    }
                                }
                            }
                            "python-requirements", "requirements", "pip" -> PackageRequestBlock(
                                operationKey = operationKey,
                                title = "Python package request",
                                requirements = block.code,
                                onReview = { requested -> onReviewPythonPackages(operationKey, requested) },
                                onInstall = { requested, plan, _ ->
                                    val result = onInstallPackages(operationKey, requested, plan)
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
                                operationKey = operationKey,
                                title = "Linux package request",
                                requirements = block.code,
                                onReview = { requested -> onReviewUbuntuPackages(operationKey, requested) },
                                onInstall = { requested, plan, progress ->
                                    val result = onInstallUbuntuPackages(operationKey, requested, plan, progress)
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
        }
        if (!renderStreaming && sourceReferences.isNotEmpty()) {
            SourceReferenceBar(
                sources = sourceReferences,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        StreamingTokenPulse(visible = streaming, modifier = Modifier.padding(top = 2.dp))
    }
}

internal fun generatedPreparationErrors(type: GeneratedBlockType, source: String): List<GeneratedValidationError> =
    try {
        when (type) {
            GeneratedBlockType.DIAGRAM -> if (NativeDiagramParser.parse(source).nodes.isEmpty()) {
                listOf(GeneratedValidationError("renderer_preparation", "/", "Diagram renderer found no supported nodes"))
            } else emptyList()
            // Widget and chart renderer inputs are fully prepared by their authoritative parsers.
            GeneratedBlockType.CHAT_UI, GeneratedBlockType.HOME_WIDGET, GeneratedBlockType.CHART -> emptyList()
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        listOf(GeneratedValidationError("renderer_preparation", "/", (error.message ?: error::class.java.simpleName).take(500)))
    }

@Composable
private fun SafeGeneratedBlock(label: String, source: String, retry: () -> Unit) {
    var expanded by remember(source) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(uiText("$label paused for crash recovery"), fontWeight = FontWeight.SemiBold)
            Text(uiText("The source and conversation are intact."), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }) { Text(uiText(if (expanded) "Collapse source" else "Show source")) }
                Button(onClick = retry) { Text(uiText("Try full rendering")) }
            }
            AnimatedVisibility(expanded, enter = workingCardExpandIn(), exit = workingCardCollapseOut()) {
                HighlightedCodeText(
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
    onInstall: suspend (String, PackagePlan, suspend (PackageInstallProgress) -> Unit) -> InstallUiResult,
) {
    val scope = rememberCoroutineScope()
    var confirm by remember(operationKey, requirements) { mutableStateOf(false) }
    var reviewing by remember(operationKey, requirements) { mutableStateOf(true) }
    var installing by remember(operationKey, requirements) { mutableStateOf(false) }
    var review by remember(operationKey, requirements) { mutableStateOf<PackageReview?>(null) }
    var result by remember(operationKey, requirements) { mutableStateOf<InstallUiResult?>(null) }
    var installProgress by remember(operationKey, requirements) { mutableStateOf<PackageInstallProgress?>(null) }
    var showDetails by remember(operationKey, requirements) { mutableStateOf(false) }

    suspend fun installNow() {
        installing = true
        val approved = review?.plan ?: run {
            result = InstallUiResult(false, "Install blocked", "The reviewed plan is no longer available. Run preflight again.")
            installing = false
            return
        }
        installProgress = null
        result = runCatching {
            onInstall(requirements, approved) { progress ->
                withContext(Dispatchers.Main.immediate) { installProgress = progress }
            }
        }
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
                        uiText(when {
                            reviewing -> "Checking installed packages and resolving dependencies…"
                            installing -> if (review?.state == PackageApprovalState.APPROVED) "Approved automatically • installing now…" else "Installing approved changes…"
                            result != null -> result!!.message
                            review?.state == PackageApprovalState.NOT_NEEDED -> "Everything requested is already installed."
                            review?.state == PackageApprovalState.DENIED -> "Installation denied • ${review?.reason.orEmpty()}"
                            review?.state == PackageApprovalState.APPROVED -> "Approved automatically • waiting to start"
                            review?.state == PackageApprovalState.REQUIRED -> "Ready for your review"
                            else -> "Preparing package plan…"
                        }),
                        Modifier.padding(start = if (reviewing || installing) 8.dp else 0.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (result?.success == false || review?.state == PackageApprovalState.DENIED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            installProgress?.let { progress ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (progress.percent != null) {
                        LinearProgressIndicator(
                            progress = { progress.percent.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (installing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(uiText(progress.phase), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        progress.percent?.let { Text(uiText("${(it.coerceIn(0f, 1f) * 100).toInt()}%"), style = MaterialTheme.typography.labelMedium) }
                    }
                    progress.currentPackage?.let {
                        Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                    }
                    if (progress.detail.isNotBlank()) {
                        Text(uiText(progress.detail), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val liveLog = listOf(progress.stdoutTail, progress.stderrTail)
                        .filter(String::isNotBlank)
                        .joinToString("\n")
                        .takeLast(8_000)
                    if (liveLog.isNotBlank() && installing) GenericToolOutputCard(liveLog)
                }
            }
            review?.let { current ->
                val changed = current.plan.items.count { it.action == PackageAction.INSTALL || it.action == PackageAction.UPDATE }
                val dependencies = current.plan.items.count { it.detail == "Dependency" }
                Text(uiText("${current.plan.items.size} packages resolved • $changed changes${if (dependencies > 0) " • $dependencies dependencies" else ""}"), style = MaterialTheme.typography.labelMedium)
                if (current.plan.downloadSummary.isNotBlank() || current.plan.diskSummary.isNotBlank()) {
                    Text(uiText(listOf(current.plan.downloadSummary, current.plan.diskSummary).filter(String::isNotBlank).joinToString(" • ")), style = MaterialTheme.typography.labelMedium)
                }
                Text(uiText("${current.decidedBy}: ${current.reason}"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showDetails = !showDetails }) { Text(uiText(if (showDetails) "Collapse package plan" else "Show complete package plan")) }
                AnimatedVisibility(showDetails, enter = workingCardExpandIn(), exit = workingCardCollapseOut()) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        current.plan.items.forEach { item ->
                            val status = when (item.action) {
                                PackageAction.ALREADY_INSTALLED -> "Installed${item.installedVersion?.let { " $it" }.orEmpty()}"
                                PackageAction.INSTALL -> "Install${item.candidateVersion?.let { " $it" }.orEmpty()}"
                                PackageAction.UPDATE -> "Update ${item.installedVersion.orEmpty()} → ${item.candidateVersion ?: "candidate"}"
                                PackageAction.INVALID -> "Invalid"
                            }
                            Text(uiText("${item.name} • $status${item.detail.takeIf(String::isNotBlank)?.let { " • $it" }.orEmpty()}"), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (review?.state == PackageApprovalState.REQUIRED) OutlinedButton(onClick = { confirm = true }, enabled = !installing && !reviewing) { Text(uiText("Review and install changes")) }
            result?.let { installed ->
                if (installed.detail.isNotBlank()) GenericToolOutputCard(installed.detail, failed = !installed.success)
            }
        }
    }
    if (confirm) XyluneAlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(uiText("Allow package changes?")) },
        text = {
            val plan = review?.plan
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiText("Only missing or outdated packages will be changed. Packages and install scripts run with Xylune's app permissions."))
                plan?.items?.filter { it.action != PackageAction.ALREADY_INSTALLED }?.forEach { item ->
                    Text(uiText("• ${item.name}: ${item.action.name.lowercase()}${item.candidateVersion?.let { " $it" }.orEmpty()}"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                if (plan?.downloadSummary?.isNotBlank() == true) Text(uiText("Download: ${plan.downloadSummary}"))
                if (plan?.diskSummary?.isNotBlank() == true) Text(uiText("Disk: ${plan.diskSummary}"))
            }
        },
        dismissButton = { OutlinedButton(onClick = { confirm = false }) { Text(uiText("Cancel")) } },
        confirmButton = {
            Button(onClick = {
                confirm = false
                scope.launch { installNow() }
            }, enabled = review?.plan?.hasChanges == true) { Text(uiText("Allow and install")) }
        },
    )
}

internal data class XyluneSourceReference(
    val label: String,
    val target: String,
)

private val XyluneLegacyReferenceNotation = Regex(
    """\[\[(source|file)\|([^|\]]{1,160})\|([^\]]{1,1500})]]""",
    RegexOption.IGNORE_CASE,
)
private val XyluneCompactSourceNotation = Regex(
    """\[\[([^|\]\n]{1,160})\|(https?://[^\]\n]{1,1500})]]""",
    RegexOption.IGNORE_CASE,
)
private val PreparedXyluneReferenceLink = Regex(
    """\[[^\]\n]+]\(xylune-(?:source|file)://reference\?target=[^)]+\)""",
    RegexOption.IGNORE_CASE,
)

private val InlineMarkdownLink = Regex("!?\\[[^\\]\\n]+\\]\\([^)\\n]+\\)")
private val ReferenceMarkdownLink = Regex("!?\\[[^\\]\\n]+\\]\\[[^\\]\\n]+\\]")
private val AutoMarkdownLink = Regex("<(?:https?|mailto):[^>\\n]+>", RegexOption.IGNORE_CASE)

private fun referenceMarkdownLink(kind: String, rawLabel: String, rawTarget: String): String {
    val safeLabel = rawLabel.trim().replace("[", "(").replace("]", ")")
    val label = if (safeLabel.length <= 30) safeLabel else safeLabel.take(29).trimEnd() + "…"
    val target = URLEncoder.encode(rawTarget.trim(), Charsets.UTF_8.name()).replace("+", "%20")
    return "[$label](xylune-${kind.lowercase()}://reference?target=$target)"
}

internal fun prepareReferenceMarkdown(markdown: String): String {
    var rendered = XyluneLegacyReferenceNotation.replace(markdown) { match ->
        referenceMarkdownLink(
            kind = match.groupValues[1],
            rawLabel = match.groupValues[2],
            rawTarget = match.groupValues[3],
        )
    }
    rendered = XyluneCompactSourceNotation.replace(rendered) { match ->
        referenceMarkdownLink(
            kind = "source",
            rawLabel = match.groupValues[1],
            rawTarget = match.groupValues[2],
        )
    }
    return rendered
}

internal fun renderMarkdownLinksLiterally(markdown: String): String {
    fun escapeDelimiters(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            if (char in charArrayOf('[', ']', '(', ')', '<', '>')) append('\\')
            append(char)
        }
    }

    val protectedReferences = mutableListOf<String>()
    var rendered = PreparedXyluneReferenceLink.replace(prepareReferenceMarkdown(markdown)) { match ->
        val index = protectedReferences.size
        protectedReferences += match.value
        "\u0001xylune-reference-$index\u0002"
    }
    rendered = InlineMarkdownLink.replace(rendered) { escapeDelimiters(it.value) }
    rendered = ReferenceMarkdownLink.replace(rendered) { escapeDelimiters(it.value) }
    rendered = AutoMarkdownLink.replace(rendered) { escapeDelimiters(it.value) }
    protectedReferences.forEachIndexed { index, reference ->
        rendered = rendered.replace("\u0001xylune-reference-$index\u0002", reference)
    }
    return rendered
}

internal fun extractSourceReferences(markdown: String): List<XyluneSourceReference> {
    data class LocatedReference(val offset: Int, val reference: XyluneSourceReference)

    val located = mutableListOf<LocatedReference>()
    XyluneLegacyReferenceNotation.findAll(markdown).forEach { match ->
        if (match.groupValues[1].equals("source", ignoreCase = true)) {
            located += LocatedReference(
                offset = match.range.first,
                reference = XyluneSourceReference(
                    label = match.groupValues[2].trim(),
                    target = match.groupValues[3].trim(),
                ),
            )
        }
    }
    XyluneCompactSourceNotation.findAll(markdown).forEach { match ->
        located += LocatedReference(
            offset = match.range.first,
            reference = XyluneSourceReference(
                label = match.groupValues[1].trim(),
                target = match.groupValues[2].trim(),
            ),
        )
    }

    val seenTargets = linkedSetOf<String>()
    return located.sortedBy(LocatedReference::offset).mapNotNull { locatedReference ->
        val reference = locatedReference.reference
        if (reference.label.isBlank() || reference.target.isBlank() || !seenTargets.add(reference.target)) {
            null
        } else {
            reference
        }
    }
}

private object XyluneMarkwonCache {
    @Volatile private var instance: Markwon? = null

    fun get(context: Context): Markwon = instance ?: synchronized(this) {
        instance ?: Markwon.builder(context.applicationContext)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context.applicationContext))
            .usePlugin(TaskListPlugin.create(context.applicationContext))
            .usePlugin(JLatexMathPlugin.create(42f))
            .build()
            .also { instance = it }
    }
}

@Composable
internal fun MarkdownBlock(
    markwon: Markwon,
    markdown: String,
    key: String,
    horizontallyScrollable: Boolean = false,
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    val color = MaterialTheme.colorScheme.onSurface.toArgbCompat()
    val linkColor = MaterialTheme.colorScheme.primary.toArgbCompat()
    val pillBackground = MaterialTheme.colorScheme.secondaryContainer.toArgbCompat()
    val pillForeground = MaterialTheme.colorScheme.onSecondaryContainer.toArgbCompat()
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .32f).toArgbCompat()
    var pendingReference by remember(key) { mutableStateOf<LinkReferencePreview?>(null) }
    val renderedMarkdown = remember(markdown) { renderMarkdownLinksLiterally(markdown) }
    when {
        // Never feed an actively growing table to Markwon. Its TablePlugin assumes
        // the parser has already produced a complete alignment/cell matrix and can
        // throw from TableRowSpan when a provider update ends mid-row. The bounded
        // native grid is still a rendered table and is cheap enough to refresh at
        // the 250 ms streaming cadence used by RichMessage.
        horizontallyScrollable && streaming -> {
            StreamingTablePreviewText(markdown = markdown, streaming = true)
        }
        horizontallyScrollable && shouldUseLightweightTableRenderer(markdown, streaming = false) -> {
            StreamingTablePreviewText(markdown = markdown, streaming = false)
        }
        horizontallyScrollable -> {
            val tableViewportDp = with(LocalDensity.current) {
                LocalWindowInfo.current.containerSize.width.toDp().value.roundToInt()
            }.minus(48).coerceAtLeast(240)
            val tableWidth = remember(markdown, tableViewportDp) {
                estimateMarkdownTableWidthDp(markdown, tableViewportDp).dp
            }
            LowSensitivityHorizontalScroll(Modifier.fillMaxWidth()) {
                MarkdownAndroidView(
                    markwon = markwon,
                    markdown = renderedMarkdown,
                    textColor = color,
                    linkColor = linkColor,
                    pillBackground = pillBackground,
                    pillForeground = pillForeground,
                    selectionColor = selectionColor,
                    onReference = { pendingReference = it },
                    modifier = Modifier.width(tableWidth),
                )
            }
        }
        else -> {
            MarkdownAndroidView(
                markwon = markwon,
                markdown = renderedMarkdown,
                textColor = color,
                linkColor = linkColor,
                pillBackground = pillBackground,
                pillForeground = pillForeground,
                selectionColor = selectionColor,
                onReference = { pendingReference = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    pendingReference?.let { reference ->
        AnchoredLinkPreview(reference = reference, onDismiss = { pendingReference = null })
    }
}



internal fun findFirstMarkdownTableStart(markdown: String, maxSearchChars: Int = 65_536): Int? {
    if (markdown.isBlank()) return null
    val limit = markdown.length.coerceAtMost(maxSearchChars.coerceAtLeast(1))
    var headerStart = 0
    while (headerStart < limit) {
        val headerEnd = markdown.indexOf('\n', headerStart).let { if (it < 0 || it > limit) limit else it }
        if (headerEnd >= limit) break
        val separatorStart = headerEnd + 1
        val separatorEnd = markdown.indexOf('\n', separatorStart).let { if (it < 0 || it > limit) limit else it }
        val header = markdown.substring(headerStart, headerEnd).trimEnd('\r')
        val separator = markdown.substring(separatorStart, separatorEnd).trimEnd('\r')
        val headerCells = splitMarkdownTableCells(header)
        val separatorColumns = markdownTableSeparatorColumns(separator)
        if (headerCells != null && separatorColumns != null && headerCells.size == separatorColumns) {
            return headerStart
        }
        if (headerEnd >= limit) break
        headerStart = headerEnd + 1
    }
    return null
}

internal data class RenderedStreamingTable(
    val text: String,
    val widestLineCharacters: Int,
    val displayedRows: Int,
)

private const val StreamingTableHiddenMarker = "… omitted table rows from live preview …"

/**
 * Converts a bounded Markdown-table slice into a cheap monospaced grid. This is
 * intentionally not Markwon: table spans repeatedly measuring a growing table
 * were capable of blocking the main thread. The grid still renders as a table
 * during streaming and the completed, reasonably sized table uses Markwon.
 */
internal fun renderStreamingTableGrid(
    markdown: String,
    startOffset: Int = 0,
    maxChars: Int = StreamingTablePreviewMaxChars,
    maxLines: Int = StreamingTablePreviewMaxLines,
    maxCellCharacters: Int = 40,
): RenderedStreamingTable {
    val bounded = boundedTablePreviewTextRange(
        markdown = markdown,
        startOffset = startOffset,
        maxChars = maxChars,
        maxLines = maxLines,
    )
    val parsedRows = mutableListOf<List<String>?>()
    bounded.lineSequence().forEach { rawLine ->
        when {
            rawLine.contains("hidden from the inline preview") -> parsedRows += null
            markdownTableSeparatorColumns(rawLine) != null -> Unit
            else -> {
                val cells = splitMarkdownTableCells(rawLine)
                if (cells != null) {
                    parsedRows += cells.map { cell ->
                        cell.trim()
                            .replace("\\|", "|")
                            .replace(MarkdownTableFormatting, "")
                            .replace(Regex("\\s+"), " ")
                            .take(maxCellCharacters.coerceAtLeast(4))
                    }
                }
            }
        }
    }
    val concreteRows = parsedRows.filterNotNull()
    if (concreteRows.isEmpty()) {
        return RenderedStreamingTable(
            text = bounded,
            widestLineCharacters = bounded.lineSequence().maxOfOrNull(String::length) ?: 0,
            displayedRows = 0,
        )
    }

    val columnCount = concreteRows.maxOf { it.size }.coerceAtMost(24)
    val widths = IntArray(columnCount) { column ->
        concreteRows.maxOfOrNull { row -> row.getOrNull(column)?.length ?: 0 }
            ?.coerceIn(3, maxCellCharacters.coerceAtLeast(4)) ?: 3
    }
    fun border(left: Char, middle: Char, right: Char): String = buildString {
        append(left)
        widths.forEachIndexed { index, width ->
            append("─".repeat(width + 2))
            append(if (index == widths.lastIndex) right else middle)
        }
    }
    fun row(cells: List<String>): String = buildString {
        append('│')
        widths.forEachIndexed { index, width ->
            val value = cells.getOrNull(index).orEmpty().take(width)
            append(' ').append(value.padEnd(width)).append(' ').append('│')
        }
    }

    val output = buildString {
        append(border('┌', '┬', '┐')).append('\n')
        var concreteIndex = 0
        parsedRows.forEachIndexed { index, cells ->
            if (cells == null) {
                val innerWidth = widths.sum() + widths.size * 3 - 1
                append('│')
                    .append(StreamingTableHiddenMarker.take(innerWidth).padEnd(innerWidth))
                    .append('│').append('\n')
            } else {
                append(row(cells)).append('\n')
                if (concreteIndex == 0) append(border('├', '┼', '┤')).append('\n')
                concreteIndex++
            }
        }
        append(border('└', '┴', '┘'))
    }
    return RenderedStreamingTable(
        text = output,
        widestLineCharacters = output.lineSequence().maxOfOrNull(String::length) ?: 0,
        displayedRows = concreteRows.size,
    )
}

internal const val StreamingTablePreviewMaxChars = 12_000
internal const val StreamingTablePreviewMaxLines = 90
internal const val CompletedTablePreviewMaxChars = 36_000
internal const val CompletedTablePreviewMaxLines = 220

internal fun isOversizedTablePreviewSource(
    markdown: String,
    maxChars: Int = CompletedTablePreviewMaxChars,
    maxLines: Int = CompletedTablePreviewMaxLines,
): Boolean {
    if (markdown.length > maxChars) return true
    var lines = 1
    for (char in markdown) {
        if (char == '\n' && ++lines > maxLines) return true
    }
    return false
}

internal fun shouldUseLightweightTableRenderer(markdown: String, streaming: Boolean): Boolean =
    if (streaming) {
        isOversizedTablePreviewSource(
            markdown,
            maxChars = StreamingTablePreviewMaxChars,
            maxLines = StreamingTablePreviewMaxLines,
        )
    } else {
        isOversizedTablePreviewSource(markdown)
    }

/**
 * Keeps table rendering bounded while a model is still appending rows. The
 * complete source remains in the message and in Copy; this is only the inline
 * preview used to keep Markwon's table span layout off the UI thread.
 *
 * This deliberately scans at most the retained preview budget rather than
 * materialising every line of a potentially multi-megabyte generated table.
 */
internal fun boundedTablePreviewText(
    markdown: String,
    maxChars: Int,
    maxLines: Int,
): String = boundedTablePreviewTextRange(markdown, 0, maxChars, maxLines)

internal fun boundedTablePreviewTextRange(
    markdown: String,
    startOffset: Int,
    maxChars: Int,
    maxLines: Int,
): String {
    require(maxChars > 0) { "maxChars must be positive" }
    require(maxLines > 2) { "maxLines must be at least 3" }
    val safeStart = startOffset.coerceIn(0, markdown.length)
    val sourceLength = markdown.length - safeStart
    if (sourceLength <= maxChars) {
        var lines = 1
        var oversized = false
        var index = safeStart
        while (index < markdown.length) {
            if (markdown[index++] == '\n' && ++lines > maxLines) {
                oversized = true
                break
            }
        }
        if (!oversized) return markdown.substring(safeStart)
    }

    val marker = "… earlier table content hidden from the inline preview …"
    val headLineLimit = 2
    val headCharLimit = (maxChars / 4).coerceAtLeast(64)
    var headEnd = safeStart
    var headLines = 1
    while (headEnd < markdown.length && headEnd - safeStart < headCharLimit && headLines <= headLineLimit) {
        val char = markdown[headEnd++]
        if (char == '\n') {
            headLines++
            if (headLines > headLineLimit) break
        }
    }
    val head = markdown.substring(safeStart, headEnd.coerceAtMost(markdown.length)).trimEnd('\n')

    val tailLineLimit = (maxLines - headLineLimit - 1).coerceAtLeast(1)
    val tailCharBudget = (maxChars - head.length - marker.length - 2).coerceAtLeast(1)
    var tailStart = markdown.length
    var tailChars = 0
    var tailLines = 1
    while (tailStart > headEnd && tailChars < tailCharBudget && tailLines <= tailLineLimit) {
        tailStart--
        tailChars++
        if (markdown[tailStart] == '\n') {
            tailLines++
            if (tailLines > tailLineLimit) {
                tailStart++
                break
            }
        }
    }
    if (tailStart > headEnd && tailStart < markdown.length && markdown[tailStart] != '\n') {
        val nextLine = markdown.indexOf('\n', tailStart)
        tailStart = if (nextLine >= 0 && nextLine + 1 < markdown.length) nextLine + 1 else markdown.length
    }
    val tail = markdown.substring(tailStart).trimStart('\n')

    return buildString(maxChars) {
        append(head.take(maxChars))
        if (isNotEmpty()) append('\n')
        append(marker)
        if (tail.isNotEmpty()) append('\n').append(tail)
    }.take(maxChars)
}

@Composable
private fun StreamingTablePreviewText(
    markdown: String,
    startOffset: Int = 0,
    streaming: Boolean,
) {
    val maxChars = if (streaming) StreamingTablePreviewMaxChars else CompletedTablePreviewMaxChars
    val maxLines = if (streaming) StreamingTablePreviewMaxLines else CompletedTablePreviewMaxLines
    val rendered = remember(markdown, startOffset, maxChars, maxLines) {
        renderStreamingTableGrid(markdown, startOffset, maxChars, maxLines)
    }
    val viewportDp = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp().value.roundToInt()
    }.minus(48).coerceAtLeast(240)
    val widthDp = (rendered.widestLineCharacters * 8 + 20).coerceIn(viewportDp, 2_400)
    val color = MaterialTheme.colorScheme.onSurface.toArgbCompat()
    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .34f).toArgbCompat()
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .32f).toArgbCompat()
    val textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value

    LowSensitivityHorizontalScroll(Modifier.fillMaxWidth()) {
        AndroidView(
            factory = { context ->
                XyluneMarkdownTextView(context).apply {
                    setTextIsSelectable(true)
                    setTextClassifier(TextClassifier.NO_OP)
                    includeFontPadding = false
                    typeface = Typeface.MONOSPACE
                    setHorizontallyScrolling(false)
                    setLineSpacing(0f, 1.04f)
                    setPadding(10, 8, 10, 8)
                }
            },
            onReset = { it.resetForReuse() },
            onRelease = { it.resetForReuse() },
            update = { view ->
                val appearanceKey = (((color * 31) + background) * 31 + selectionColor) * 31 + textSizeSp.toBits()
                if (view.appliedStyleKey != appearanceKey) {
                    view.setTextColor(color)
                    view.setBackgroundColor(background)
                    view.highlightColor = selectionColor
                    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                    view.appliedStyleKey = appearanceKey
                }
                if (view.renderedSource != rendered.text) {
                    view.setText(rendered.text, TextView.BufferType.SPANNABLE)
                    view.renderedSource = rendered.text
                }
            },
            modifier = Modifier.width(widthDp.dp),
        )
    }
}


@Composable
private fun LightweightTableText(
    markdown: String,
    streaming: Boolean,
) {
    val maxChars = if (streaming) StreamingTablePreviewMaxChars else CompletedTablePreviewMaxChars
    val maxLines = if (streaming) StreamingTablePreviewMaxLines else CompletedTablePreviewMaxLines
    val renderedPreview = remember(markdown, maxChars, maxLines) {
        boundedTablePreviewText(markdown, maxChars, maxLines)
    }
    val color = MaterialTheme.colorScheme.onSurface.toArgbCompat()
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .32f).toArgbCompat()
    val textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
    AndroidView(
        factory = { context ->
            XyluneMarkdownTextView(context).apply {
                setTextIsSelectable(true)
                setTextClassifier(TextClassifier.NO_OP)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                includeFontPadding = false
                typeface = Typeface.MONOSPACE
                setHorizontallyScrolling(false)
                setLineSpacing(0f, 1.04f)
            }
        },
        onReset = { it.resetForReuse() },
        onRelease = { it.resetForReuse() },
        update = { view ->
            val appearanceKey = ((color * 31) + selectionColor) * 31 + textSizeSp.toBits()
            if (view.appliedStyleKey != appearanceKey) {
                view.setTextColor(color)
                view.highlightColor = selectionColor
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                view.appliedStyleKey = appearanceKey
            }
            if (view.renderedSource != renderedPreview) {
                view.setText(renderedPreview, TextView.BufferType.SPANNABLE)
                view.renderedSource = renderedPreview
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun shouldExecuteRichCodeBlock(displayOnly: Boolean, complete: Boolean): Boolean =
    !displayOnly && complete

/** Full assistant Markdown visuals with a hard execution boundary for hidden reasoning. */
@Composable
internal fun DisplayOnlyMarkdown(
    operationScope: String,
    text: String,
    streaming: Boolean,
    workingCardViewport: WorkingCardViewportController,
) {
    RichMessage(
        operationScope = operationScope,
        text = text,
        streaming = streaming,
        displayOnly = true,
        onRunPython = { _, _ -> error("Display-only Markdown cannot execute Python") },
        onRunUbuntu = { _, _ -> error("Display-only Markdown cannot execute Linux tools") },
        onReviewPythonPackages = { _, _ -> error("Display-only Markdown cannot review packages") },
        onInstallPackages = { _, _, _ -> error("Display-only Markdown cannot install packages") },
        onReviewUbuntuPackages = { _, _ -> error("Display-only Markdown cannot review packages") },
        onInstallUbuntuPackages = { _, _, _, _ -> error("Display-only Markdown cannot install packages") },
        onWidgetSubmit = {},
        onReviewWidgetSecurity = { error("Display-only Markdown cannot run widgets") },
        onRepairGeneratedBlock = { _, _, _, _, _, _, _ -> error("Display-only Markdown cannot repair generated content") },
        onAcceptGeneratedEdit = { _, _ -> error("Display-only Markdown cannot edit generated content") },
        workingCardViewport = workingCardViewport,
    )
}

@Composable
internal fun StreamingPlainText(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val renderedText = rememberBatchedStreamingText(text, streaming)
    val color = MaterialTheme.colorScheme.onSurfaceVariant.toArgbCompat()
    val selectionColor = MaterialTheme.colorScheme.primary.copy(alpha = .32f).toArgbCompat()
    val textSizeSp = MaterialTheme.typography.bodySmall.fontSize.value
    AndroidView(
        factory = { context ->
            XyluneMarkdownTextView(context).apply {
                setTextIsSelectable(true)
                setTextClassifier(TextClassifier.NO_OP)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                includeFontPadding = false
                movementMethod = selectableLinkMovementMethod
                setLineSpacing(0f, 1.08f)
            }
        },
        onReset = { it.resetForReuse() },
        onRelease = { it.resetForReuse() },
        update = { view ->
            val appearanceKey = ((color * 31) + selectionColor) * 31 + textSizeSp.toBits()
            if (view.appliedStyleKey != appearanceKey) {
                view.setTextColor(color)
                view.highlightColor = selectionColor
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                view.appliedStyleKey = appearanceKey
            }
            if (view.renderedSource != renderedText) {
                view.setText(renderedText, TextView.BufferType.SPANNABLE)
                view.renderedSource = renderedText
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private data class ParsedMarkdownSource(
    val source: String,
    val spanned: Spanned,
)

/**
 * Last-resort text for a renderer failure. Tables remain visually tabular rather
 * than degrading to pipe-delimited source; ordinary Markdown remains selectable.
 */
internal fun markdownRenderFallbackText(markdown: String): String =
    if (containsMarkdownTableCandidate(markdown)) {
        renderStreamingTableGrid(
            markdown = markdown,
            maxChars = CompletedTablePreviewMaxChars,
            maxLines = CompletedTablePreviewMaxLines,
        ).text
    } else {
        markdown
    }

private fun renderMarkdownSafely(markwon: Markwon, markdown: String): Spanned = try {
    markwon.render(markwon.parse(markdown))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    // Markwon's table extension can throw for a syntactically valid prefix that
    // becomes structurally incomplete between provider chunks. Rendering must
    // never be allowed to terminate the main Compose coroutine/app process.
    SpannableString(markdownRenderFallbackText(markdown))
}

@Composable
private fun MarkdownAndroidView(
    markwon: Markwon,
    markdown: String,
    textColor: Int,
    linkColor: Int,
    pillBackground: Int,
    pillForeground: Int,
    selectionColor: Int,
    onReference: (LinkReferencePreview) -> Unit,
    modifier: Modifier,
) {
    var parsedMarkdown by remember(markwon, markdown) { mutableStateOf<ParsedMarkdownSource?>(null) }
    LaunchedEffect(markwon, markdown) {
        val spanned = withContext(Dispatchers.Default) {
            renderMarkdownSafely(markwon, markdown)
        }
        parsedMarkdown = ParsedMarkdownSource(markdown, spanned)
    }
    AndroidView(
        factory = { context ->
            XyluneMarkdownTextView(context).apply {
                setTextIsSelectable(true)
                setTextClassifier(TextClassifier.NO_OP)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                textSize = 16f
                includeFontPadding = false
                linksClickable = true
                movementMethod = selectableLinkMovementMethod
                setLineSpacing(0f, 1.08f)
            }
        },
        onReset = { it.resetForReuse() },
        onRelease = { it.resetForReuse() },
        update = { view ->
            val styleKey = ((((textColor * 31) + linkColor) * 31 + pillBackground) * 31 + pillForeground) * 31 + selectionColor
            if (view.appliedStyleKey != styleKey) {
                view.setTextColor(textColor)
                view.setLinkTextColor(linkColor)
                view.highlightColor = selectionColor
                view.setHorizontallyScrolling(false)
                view.appliedStyleKey = styleKey
            }
            val ready = parsedMarkdown
            if (ready == null) {
                if (view.renderedSource != markdown || !view.renderedAsFallback || view.renderedStyleKey != styleKey) {
                    view.setText(markdownRenderFallbackText(markdown), TextView.BufferType.SPANNABLE)
                    view.renderedSource = markdown
                    view.renderedStyleKey = styleKey
                    view.renderedAsFallback = true
                }
            } else if (
                view.renderedSource != ready.source ||
                view.renderedStyleKey != styleKey ||
                view.renderedAsFallback
            ) {
                try {
                    markwon.setParsedMarkdown(view, ready.spanned)
                    installReferenceSpans(
                        view = view,
                        linkColor = linkColor,
                        pillBackground = pillBackground,
                        pillForeground = pillForeground,
                        onClick = onReference,
                    )
                    view.renderedAsFallback = false
                } catch (_: Exception) {
                    // Rendering can also fail while Android applies/measures spans,
                    // so guard the UI hand-off as well as parse/render above.
                    view.setText(
                        markdownRenderFallbackText(ready.source),
                        TextView.BufferType.SPANNABLE,
                    )
                    view.renderedAsFallback = true
                }
                view.renderedSource = ready.source
                view.renderedStyleKey = styleKey
            }
        },
        modifier = modifier,
    )
}

@SuppressLint("AppCompatCustomView")
private class XyluneMarkdownTextView(context: Context) : TextView(context) {
    var renderedSource: String = ""
    var renderedStyleKey: Int = 0
    var appliedStyleKey: Int = 0
    var renderedAsFallback: Boolean = false
    val selectableLinkMovementMethod = SelectableLinkMovementMethod()

    fun resetForReuse() {
        text = null
        renderedSource = ""
        renderedStyleKey = 0
        appliedStyleKey = 0
        renderedAsFallback = false
    }
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
    linkColor: Int,
    pillBackground: Int,
    pillForeground: Int,
    onClick: (LinkReferencePreview) -> Unit,
) {
    val source = view.text as? Spanned ?: return
    val text = SpannableString(source)
    text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start < 0 || end <= start) return@forEach
        val raw = span.url.orEmpty()
        val parsed = raw.toUri()
        val kind = when (parsed.scheme?.lowercase()) {
            "xylune-source" -> LinkReferenceKind.SOURCE
            "xylune-file" -> LinkReferenceKind.FILE
            else -> LinkReferenceKind.LINK
        }
        val target = if (kind == LinkReferenceKind.LINK) raw else parsed.getQueryParameter("target").orEmpty()
        val label = text.subSequence(start, end).toString()
        text.removeSpan(span)
        val clickableSpan = PreviewClickableSpan(linkColor) { widget ->
            onClick(
                LinkReferencePreview(
                    kind = kind,
                    label = label,
                    target = target,
                    anchorBoundsInWindow = spanBoundsInWindow(widget as? TextView, start, end),
                ),
            )
        }
        text.setSpan(
            clickableSpan,
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        if (kind != LinkReferenceKind.LINK) {
            text.setSpan(
                LinkPillSpan(
                    icon = if (kind == LinkReferenceKind.FILE) "▣" else "↗",
                    backgroundColor = pillBackground,
                    foregroundColor = pillForeground,
                ),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    view.text = text
    view.movementMethod = (view as? XyluneMarkdownTextView)?.selectableLinkMovementMethod
        ?: ArrowKeyMovementMethod.getInstance()
}

private class PreviewClickableSpan(
    private val color: Int,
    private val click: (View) -> Unit,
) : ClickableSpan() {
    override fun onClick(widget: View) = click(widget)
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
    private val backgroundColor: Int,
    private val foregroundColor: Int,
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
    onRunPython: suspend (String, suspend (ExecutionProgress) -> Unit) -> ExecutionResult,
    onRunUbuntu: suspend (String, suspend (ExecutionProgress) -> Unit) -> UbuntuExecutionResult,
    executable: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var liveProgress by remember { mutableStateOf<ExecutionProgress?>(null) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var ubuntuResult by remember { mutableStateOf<UbuntuExecutionResult?>(null) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(uiText(language.ifBlank { "code" }.uppercase()), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (executable && language.lowercase() in setOf("python", "py")) {
                    IconButton(onClick = {
                        scope.launch {
                            running = true
                            liveProgress = ExecutionProgress()
                            result = null
                            result = runCatching {
                                onRunPython(code) { progress ->
                                    withContext(Dispatchers.Main.immediate) { liveProgress = progress }
                                }
                            }.getOrElse { ExecutionResult(stderr = it.stackTraceToString()) }
                            liveProgress = null
                            running = false
                        }
                    }, enabled = !running) { Icon(Icons.Outlined.PlayArrow, uiText("Run in workspace")) }
                }
                if (executable && language.lowercase() in setOf("bash", "sh", "shell", "ubuntu", "debian", "alpine", "linux")) {
                    IconButton(onClick = {
                        scope.launch {
                            running = true
                            liveProgress = ExecutionProgress()
                            ubuntuResult = null
                            ubuntuResult = runCatching {
                                onRunUbuntu(code) { progress ->
                                    withContext(Dispatchers.Main.immediate) { liveProgress = progress }
                                }
                            }.getOrElse { UbuntuExecutionResult(stderr = it.message.orEmpty()) }
                            liveProgress = null
                            running = false
                        }
                    }, enabled = !running) { Icon(Icons.Outlined.PlayArrow, uiText("Run with Linux tools")) }
                }
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("code", code))
                    copied = true
                }) { Icon(if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy, uiText("Copy")) }
            }
            LowSensitivityHorizontalScroll(Modifier.padding(14.dp)) {
                HighlightedCodeText(
                    language = language,
                    code = code,
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = false,
                )
            }
            AnimatedVisibility(running, enter = streamingFadeIn(), exit = streamingFadeOut()) {
                Column(Modifier.padding(10.dp)) {
                    LiveExecutionCard(
                        progress = liveProgress ?: ExecutionProgress(),
                        title = if (language.lowercase() in setOf("python", "py")) "Python execution" else "Linux execution",
                    )
                }
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

private data class MarkdownFence(val marker: String, val info: String, val lineEndExclusive: Int)

private fun markdownFenceAtLine(line: String): String? {
    val leading = line.indexOfFirst { it != ' ' && it != '\t' }.let { if (it < 0) line.length else it }
    if (leading > 3 || leading >= line.length) return null
    val char = line[leading]
    if (char != '`' && char != '~') return null
    var end = leading
    while (end < line.length && line[end] == char) end++
    return line.substring(leading, end).takeIf { it.length >= 3 }
}

private fun findOpeningFence(text: String, fromIndex: Int): Pair<Int, MarkdownFence>? {
    var lineStart = fromIndex.coerceAtLeast(0)
    if (lineStart > 0 && text.getOrNull(lineStart - 1) != '\n') {
        lineStart = text.indexOf('\n', lineStart).let { if (it < 0) return null else it + 1 }
    }
    while (lineStart < text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        val rawLine = text.substring(lineStart, lineEnd).trimEnd('\r')
        val marker = markdownFenceAtLine(rawLine)
        if (marker != null) {
            val trimmed = rawLine.trimStart()
            val info = trimmed.drop(marker.length).trim()
            return lineStart to MarkdownFence(
                marker = marker,
                info = info,
                lineEndExclusive = if (newline < 0) text.length else newline + 1,
            )
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
    return null
}

private fun findClosingFence(text: String, fromIndex: Int, marker: String): IntRange? {
    var lineStart = fromIndex
    while (lineStart < text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        val rawLine = text.substring(lineStart, lineEnd).trimEnd('\r')
        val candidate = markdownFenceAtLine(rawLine)
        if (candidate != null && candidate.first() == marker.first() && candidate.length >= marker.length) {
            val remainder = rawLine.trimStart().drop(candidate.length)
            if (remainder.isBlank()) return lineStart until lineEnd
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
    return null
}

/** Returns the append-only prefix which can no longer change Markdown meaning. */
internal fun stableMarkdownPrefixLength(text: String): Int {
    var lineStart = 0
    var openFence: String? = null
    var lastStable = 0
    while (lineStart < text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline < 0) text.length else newline
        val rawLine = text.substring(lineStart, lineEnd).trimEnd('\r')
        val marker = markdownFenceAtLine(rawLine)
        if (openFence == null) {
            if (marker != null) {
                openFence = marker
            } else if (rawLine.isBlank()) {
                lastStable = if (newline < 0) text.length else newline + 1
            }
        } else if (marker != null && marker.first() == openFence.first() && marker.length >= openFence.length) {
            val remainder = rawLine.trimStart().drop(marker.length)
            if (remainder.isBlank()) {
                openFence = null
                lastStable = if (newline < 0) text.length else newline + 1
            }
        }
        if (newline < 0) break
        lineStart = newline + 1
    }
    return lastStable
}

internal fun parseBlocks(text: String, streaming: Boolean): List<RichBlock> {
    if (text.isBlank()) return emptyList()
    val result = mutableListOf<RichBlock>()
    var cursor = 0
    while (cursor < text.length) {
        val opening = findOpeningFence(text, cursor)
        if (opening == null) {
            appendMarkdownBlocks(result, text.substring(cursor), streaming)
            break
        }
        val (openingStart, fence) = opening
        if (openingStart > cursor) appendMarkdownBlocks(result, text.substring(cursor, openingStart), streaming)
        val closing = findClosingFence(text, fence.lineEndExclusive, fence.marker)
        val language = fence.info.substringBefore(' ').trim()
        if (closing == null) {
            val code = text.substring(fence.lineEndExclusive).trimEnd('\r', '\n')
            result += RichBlock.Code(language, code, complete = false)
            break
        }
        val code = text.substring(fence.lineEndExclusive, closing.first).trimEnd('\r', '\n')
        result += RichBlock.Code(language, code, complete = true)
        val newlineAfterClosing = text.indexOf('\n', closing.last + 1)
        cursor = if (newlineAfterClosing < 0) text.length else newlineAfterClosing + 1
    }
    return result.filterNot {
        (it is RichBlock.Markdown && it.text.isBlank()) || (it is RichBlock.Table && it.text.isBlank())
    }
}

internal data class MarkdownSegment(val table: Boolean, val text: String)

private val MarkdownTableSeparatorCell = Regex("""^\s*:?-{3,}:?\s*$""")
private val MarkdownTableFormatting = Regex("""[`*_~]""")

/**
 * Splits a Markdown row on structural pipes only. Escaped pipes and pipes inside
 * inline-code spans belong to the cell and must not create phantom columns.
 */
internal fun splitMarkdownTableCells(line: String): List<String>? {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var delimiterCount = 0
    var backslashRun = 0
    var codeFenceLength = 0
    var index = 0

    while (index < line.length) {
        val char = line[index]
        if (char == '`' && backslashRun % 2 == 0) {
            var run = 1
            while (index + run < line.length && line[index + run] == '`') run++
            codeFenceLength = when {
                codeFenceLength == 0 -> run
                codeFenceLength == run -> 0
                else -> codeFenceLength
            }
            repeat(run) { current.append('`') }
            index += run
            backslashRun = 0
            continue
        }
        if (char == '|' && backslashRun % 2 == 0 && codeFenceLength == 0) {
            cells += current.toString()
            current.clear()
            delimiterCount++
        } else {
            current.append(char)
        }
        backslashRun = if (char == '\\') backslashRun + 1 else 0
        index++
    }
    if (delimiterCount == 0) return null
    cells += current.toString()

    val startsWithPipe = line.trimStart().startsWith('|')
    val endsWithPipe = line.trimEnd().endsWith('|')
    if (startsWithPipe && cells.firstOrNull()?.isBlank() == true) cells.removeAt(0)
    if (endsWithPipe && cells.lastOrNull()?.isBlank() == true) cells.removeAt(cells.lastIndex)
    return cells.takeIf { it.size >= 2 }
}

private fun markdownTableSeparatorColumns(line: String): Int? = splitMarkdownTableCells(line)
    ?.takeIf { cells -> cells.all { MarkdownTableSeparatorCell.matches(it) } }
    ?.size

/**
 * Keeps the currently-written final row structurally inside its table. Without
 * this, every partial row alternates between a plain Markdown block and a table
 * block as pipes arrive, recreating the TextView and visibly flickering.
 */
internal fun stabilizeStreamingTableRow(line: String, expectedColumns: Int): String? {
    if (expectedColumns < 2 || line.isBlank()) return null
    var candidate = line.trimEnd()
    if (!candidate.trimStart().startsWith('|')) candidate = "| $candidate"
    if (!candidate.endsWith('|')) candidate += " |"

    var cells = splitMarkdownTableCells(candidate) ?: emptyList()
    while (cells.size < expectedColumns) {
        candidate += " |"
        cells = splitMarkdownTableCells(candidate) ?: emptyList()
    }
    return candidate.takeIf { cells.size == expectedColumns }
}

private fun addMarkdownSegment(
    destination: MutableList<MarkdownSegment>,
    table: Boolean,
    lines: List<String>,
) {
    val first = lines.indexOfFirst(String::isNotBlank)
    if (first < 0) return
    val last = lines.indexOfLast(String::isNotBlank)
    destination += MarkdownSegment(table, lines.subList(first, last + 1).joinToString("\n"))
}

/** Splits complete Markdown tables so only the table receives horizontal scrolling. */
internal fun splitMarkdownTables(markdown: String, streaming: Boolean = false): List<MarkdownSegment> {
    if (markdown.isBlank()) return emptyList()
    val lines = markdown.split('\n')
    val segments = mutableListOf<MarkdownSegment>()
    var plainStart = 0
    var index = 1

    while (index < lines.size) {
        val headerCells = splitMarkdownTableCells(lines[index - 1])
        val separatorColumns = markdownTableSeparatorColumns(lines[index])
        if (headerCells != null && separatorColumns != null && headerCells.size == separatorColumns) {
            val tableStart = index - 1
            if (tableStart > plainStart) addMarkdownSegment(segments, false, lines.subList(plainStart, tableStart))
            var tableEnd = index + 1
            var stabilizedTrailingRow: String? = null
            while (tableEnd < lines.size && lines[tableEnd].isNotBlank()) {
                val rowCells = splitMarkdownTableCells(lines[tableEnd])
                if (rowCells != null && rowCells.size == separatorColumns) {
                    tableEnd++
                    continue
                }
                if (streaming && tableEnd == lines.lastIndex) {
                    stabilizedTrailingRow = stabilizeStreamingTableRow(lines[tableEnd], separatorColumns)
                    if (stabilizedTrailingRow != null) tableEnd++
                }
                break
            }
            val tableLines = lines.subList(tableStart, tableEnd).toMutableList()
            if (stabilizedTrailingRow != null && tableLines.isNotEmpty()) {
                tableLines[tableLines.lastIndex] = stabilizedTrailingRow
            }
            addMarkdownSegment(segments, true, tableLines)
            plainStart = tableEnd
            index = tableEnd + 1
        } else {
            index++
        }
    }

    if (plainStart < lines.size) addMarkdownSegment(segments, false, lines.subList(plainStart, lines.size))
    return segments
}

internal fun estimateMarkdownTableWidthDp(markdown: String, viewportDp: Int): Int {
    val rows = markdown.lineSequence()
        .mapNotNull { line ->
            val cells = splitMarkdownTableCells(line) ?: return@mapNotNull null
            if (cells.all { MarkdownTableSeparatorCell.matches(it) }) null
            else cells.map { cell -> cell.trim().replace(MarkdownTableFormatting, "") }
        }
        .toList()
    val columnCount = rows.maxOfOrNull { it.size } ?: return viewportDp.coerceAtLeast(240)
    val estimated = (0 until columnCount).sumOf { column ->
        val longest = rows.maxOfOrNull { row -> row.getOrNull(column)?.length ?: 0 } ?: 0
        (longest.coerceIn(4, 36) * 8 + 28).coerceIn(84, 316)
    }
    return estimated.coerceAtLeast(viewportDp.coerceAtLeast(240)).coerceAtMost(2400)
}

private fun appendMarkdownBlocks(destination: MutableList<RichBlock>, markdown: String, streaming: Boolean) {
    splitMarkdownTables(markdown, streaming).forEach { segment ->
        destination += if (segment.table) RichBlock.Table(segment.text) else RichBlock.Markdown(segment.text)
    }
}

private fun Color.toArgbCompat(): Int = AndroidColor.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
