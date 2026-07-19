package app.arbor.chat.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.Spannable
import android.text.SpannableString
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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.math.roundToInt

private sealed interface RichBlock {
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
    val blocks = remember(text) { parseBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is RichBlock.Markdown -> MarkdownBlock(
                    markdown = block.text,
                    key = "$index:${block.text.hashCode()}",
                    streaming = streaming && index == blocks.lastIndex,
                )
                is RichBlock.Table -> MarkdownBlock(
                    markdown = block.text,
                    key = "table:$index:${block.text.hashCode()}",
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
    val renderedMarkdown = remember(markdown) { prepareReferenceMarkdown(markdown) }
    val markwon = remember(context) {
        Markwon.builder(context)
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
                markdown = renderedMarkdown,
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
            markdown = renderedMarkdown,
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
            }
        },
        update = { view ->
            view.setTextColor(color)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            if (view.text.toString() != text) {
                val previousLength = view.previousRenderedLength
                view.setText(text, TextView.BufferType.SPANNABLE)
                animateAppendedMarkdown(view, previousLength, streaming)
                view.previousRenderedLength = view.text.length
            }
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
            }
        },
        update = { view ->
            val previousLength = view.previousRenderedLength
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            view.setHorizontallyScrolling(false)
            markwon.setMarkdown(view, markdown)
            installReferenceSpans(
                view = view,
                linkColor = linkColor,
                pillBackground = pillBackground,
                pillForeground = pillForeground,
                onClick = onReference,
            )
            animateAppendedMarkdown(view, previousLength, streaming)
            view.previousRenderedLength = view.text.length
        },
        modifier = modifier,
    )
}

@SuppressLint("AppCompatCustomView")
private class ArborMarkdownTextView(context: Context) : TextView(context) {
    var previousRenderedLength: Int = 0
    var tokenAnimator: ValueAnimator? = null
    val selectableLinkMovementMethod = SelectableLinkMovementMethod()

    override fun onDetachedFromWindow() {
        tokenAnimator?.cancel()
        tokenAnimator = null
        super.onDetachedFromWindow()
    }
}

private class StreamingAlphaSpan : CharacterStyle(), UpdateAppearance {
    var progress: Float = 0f

    override fun updateDrawState(textPaint: TextPaint) {
        val alphaScale = StreamingFadeStartAlpha + (1f - StreamingFadeStartAlpha) * progress.coerceIn(0f, 1f)
        textPaint.alpha = (textPaint.alpha * alphaScale).roundToInt().coerceIn(0, 255)
    }
}

private fun animateAppendedMarkdown(view: ArborMarkdownTextView, previousLength: Int, streaming: Boolean) {
    view.tokenAnimator?.cancel()
    view.tokenAnimator = null
    val currentLength = view.text.length
    if (!streaming || currentLength <= 0 || currentLength <= previousLength) return

    val start = if (previousLength in 1 until currentLength) {
        previousLength
    } else {
        (currentLength - 56).coerceAtLeast(0)
    }
    if (start >= currentLength) return

    val text = SpannableString(view.text)
    val fadeSpan = StreamingAlphaSpan()
    text.setSpan(fadeSpan, start, currentLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    view.setText(text, TextView.BufferType.SPANNABLE)
    view.movementMethod = view.selectableLinkMovementMethod

    view.tokenAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = StreamingFadeDurationMillis.toLong()
        addUpdateListener { animator ->
            fadeSpan.progress = animator.animatedValue as Float
            view.invalidate()
        }
        start()
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
        val parsed = Uri.parse(raw)
        val kind = when (parsed.scheme?.lowercase()) {
            "arbor-source" -> LinkReferenceKind.SOURCE
            "arbor-file" -> LinkReferenceKind.FILE
            else -> LinkReferenceKind.LINK
        }
        val target = if (kind == LinkReferenceKind.LINK) raw else parsed.getQueryParameter("target").orEmpty()
        val label = text.subSequence(start, end).toString()
        text.removeSpan(span)
        text.setSpan(
            PreviewClickableSpan(linkColor) { widget ->
                onClick(
                    LinkReferencePreview(
                        kind = kind,
                        label = label,
                        target = target,
                        anchorBoundsInWindow = spanBoundsInWindow(widget as? TextView, start, end),
                    ),
                )
            },
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
    view.movementMethod = (view as? ArborMarkdownTextView)?.selectableLinkMovementMethod
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
