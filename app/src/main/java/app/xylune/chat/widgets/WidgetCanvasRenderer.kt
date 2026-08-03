package app.xylune.chat.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.min

/** Renders arbitrary xylune-widget/1 component trees into a launcher-safe bitmap. */
internal data class WidgetRenderResult(
    val bitmap: Bitmap,
    val renderedNodes: Int,
    val clippedTextCount: Int,
    val crampedTextCount: Int,
    val minimumTextSp: Float,
    val clippedSamples: List<String>,
)

internal object WidgetCanvasRenderer {
    fun render(
        definition: XyluneProgramDefinition,
        state: Map<String, String>,
        widthPx: Int,
        heightPx: Int,
        dark: Boolean,
        suppressActionControls: Boolean = false,
        density: Float = 2f,
        scaledDensity: Float = density,
    ): Bitmap = renderWithDiagnostics(
        definition,
        state,
        widthPx,
        heightPx,
        dark,
        suppressActionControls,
        density,
        scaledDensity,
    ).bitmap

    fun renderWithDiagnostics(
        definition: XyluneProgramDefinition,
        state: Map<String, String>,
        widthPx: Int,
        heightPx: Int,
        dark: Boolean,
        suppressActionControls: Boolean = false,
        density: Float = 2f,
        scaledDensity: Float = density,
    ): WidgetRenderResult {
        val bitmap = createBitmap(widthPx.coerceIn(240, 2000), heightPx.coerceIn(120, 1600))
        val canvas = Canvas(bitmap)
        val palette = Palette(dark)
        canvas.drawColor(Color.TRANSPARENT)
        val renderer = Renderer(
            canvas = canvas,
            palette = palette,
            state = state,
            suppressActionControls = suppressActionControls,
            density = density.coerceAtLeast(.5f),
            scaledDensity = scaledDensity.coerceAtLeast(.5f),
        )
        renderer.renderNode(
            definition.ui,
            RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat()),
            0,
        )
        return WidgetRenderResult(
            bitmap = bitmap,
            renderedNodes = renderer.renderedNodes,
            clippedTextCount = renderer.clippedTextCount,
            crampedTextCount = renderer.crampedTextCount,
            minimumTextSp = renderer.minimumTextSp.takeIf(Float::isFinite) ?: 0f,
            clippedSamples = renderer.clippedSamples.toList(),
        )
    }

    private class Renderer(
        private val canvas: Canvas,
        private val palette: Palette,
        private val state: Map<String, String>,
        private val suppressActionControls: Boolean,
        private val density: Float,
        private val scaledDensity: Float,
    ) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        var renderedNodes: Int = 0
            private set
        var clippedTextCount: Int = 0
            private set
        var crampedTextCount: Int = 0
            private set
        var minimumTextSp: Float = Float.POSITIVE_INFINITY
            private set
        val clippedSamples = linkedSetOf<String>()

        fun renderNode(node: XyluneProgramNode, bounds: RectF, depth: Int) {
            if (depth > 12 || !XyluneProgramRuntime.visible(node.visibleWhen, state) || bounds.width() <= 2f || bounds.height() <= 2f) return
            if (suppressActionControls && isActionControl(node)) return
            if (node.type !in setOf("column", "row", "stack", "spacer", "divider")) renderedNodes += 1
            val defaultRootPadding = if (bounds.height() / density <= 120f || bounds.width() / density <= 260f) 6 else 10
            val logicalPadding = node.style.padding.takeIf { it > 0 }
                ?: if (depth == 0 && node.type in setOf("column", "row", "stack")) defaultRootPadding else 0
            val padding = dp(logicalPadding)
            val content = RectF(bounds.left + padding, bounds.top + padding, bounds.right - padding, bounds.bottom - padding)
            drawBackground(node.style, bounds)
            when (node.type) {
                "column" -> renderColumn(node, content, depth)
                "row" -> renderRow(node, content, depth)
                "stack" -> node.children.forEach { renderNode(it, content, depth + 1) }
                "text" -> drawTextBlock(XyluneProgramRuntime.render(node.text.ifBlank { node.value }, state), content, node.style, false)
                "metric" -> drawMetric(node, content)
                "button" -> drawButton(XyluneProgramRuntime.render(node.label, state), content, node.style)
                "toggle" -> drawToggle(node, content)
                "choice" -> drawChoice(node, content)
                "slider" -> drawSlider(node, content)
                "progress" -> drawProgress(node, content)
                "list" -> drawList(node, content)
                "chart" -> drawChart(node, content)
                "divider" -> drawDivider(content)
                "input" -> drawInput(node, content)
                "spacer" -> Unit
            }
        }

        private fun renderColumn(node: XyluneProgramNode, bounds: RectF, depth: Int) {
            val visible = node.children.filter { XyluneProgramRuntime.visible(it.visibleWhen, state) && !(suppressActionControls && isActionControl(it)) }
            if (visible.isEmpty()) return
            val gap = dp(node.style.gap)
            val available = (bounds.height() - gap * (visible.size - 1)).coerceAtLeast(1f)
            val heights = allocateAxis(
                available,
                visible.map(::preferredHeight),
                visible.map(::childWeight),
            )
            var top = bounds.top
            visible.forEachIndexed { index, child ->
                val height = if (index == visible.lastIndex) bounds.bottom - top else heights[index]
                renderNode(child, RectF(bounds.left, top, bounds.right, top + height), depth + 1)
                top += height + gap
            }
        }

        private fun renderRow(node: XyluneProgramNode, bounds: RectF, depth: Int) {
            val visible = node.children.filter { XyluneProgramRuntime.visible(it.visibleWhen, state) && !(suppressActionControls && isActionControl(it)) }
            if (visible.isEmpty()) return
            val gap = dp(node.style.gap)
            val available = (bounds.width() - gap * (visible.size - 1)).coerceAtLeast(1f)
            val widths = allocateAxis(
                available,
                visible.map(::preferredWidth),
                visible.map { child -> child.style.weight.takeIf { it > 0f } ?: if (child.type == "spacer") 1f else .05f },
            )
            var left = bounds.left
            visible.forEachIndexed { index, child ->
                val width = if (index == visible.lastIndex) bounds.right - left else widths[index]
                renderNode(child, RectF(left, bounds.top, left + width, bounds.bottom), depth + 1)
                left += width + gap
            }
        }

        private fun drawMetric(node: XyluneProgramNode, bounds: RectF) {
            val label = XyluneProgramRuntime.render(node.label, state)
            val value = XyluneProgramRuntime.render(node.value.ifBlank { node.text }, state)
            if (bounds.height() < dp(48)) {
                drawTextBlock(
                    listOf(label, value).filter(String::isNotBlank).joinToString("  "),
                    bounds,
                    node.style.copy(fontSize = node.style.fontSize.takeIf { it > 0 } ?: 14, emphasis = "strong"),
                    false,
                )
                return
            }
            val labelHeight = if (label.isBlank()) 0f else min(bounds.height() * .28f, dp(24))
            if (labelHeight > 0f) {
                drawTextBlock(
                    label,
                    RectF(bounds.left, bounds.top, bounds.right, bounds.top + labelHeight),
                    node.style.copy(fontSize = 13, emphasis = "medium", foreground = "muted"),
                    false,
                )
            }
            drawTextBlock(
                value,
                RectF(bounds.left, bounds.top + labelHeight, bounds.right, bounds.bottom),
                node.style.copy(fontSize = node.style.fontSize.takeIf { it > 0 } ?: 30, emphasis = "strong"),
                true,
            )
        }

        private fun drawButton(label: String, bounds: RectF, style: XyluneProgramStyle) {
            paint.color = color(style.background).takeIf { it != Color.TRANSPARENT } ?: palette.primaryContainer
            canvas.drawRoundRect(bounds, dp(style.cornerRadius).coerceAtLeast(dp(8)), dp(style.cornerRadius).coerceAtLeast(dp(8)), paint)
            drawTextBlock(label, bounds, style.copy(align = "center", emphasis = "medium", foreground = if (style.foreground.isBlank()) "primary_text" else style.foreground), false)
        }

        private fun drawToggle(node: XyluneProgramNode, bounds: RectF) {
            val on = XyluneProgramRuntime.truthy(state[node.value])
            val trackWidth = min(dp(44), bounds.width() * .28f)
            val track = RectF(bounds.right - trackWidth, bounds.centerY() - dp(10), bounds.right, bounds.centerY() + dp(10))
            paint.color = if (on) palette.primary else palette.outline
            canvas.drawRoundRect(track, dp(10), dp(10), paint)
            paint.color = palette.surface
            val radius = dp(8)
            val centerX = if (on) track.right - radius - dp(2) else track.left + radius + dp(2)
            canvas.drawCircle(centerX, track.centerY(), radius, paint)
            drawTextBlock(
                XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state),
                RectF(bounds.left, bounds.top, track.left - dp(8), bounds.bottom),
                node.style,
                false,
            )
        }

        private fun drawChoice(node: XyluneProgramNode, bounds: RectF) {
            val selected = state[node.value].orEmpty()
            val gap = dp(5)
            val count = node.options.size.coerceAtLeast(1)
            val width = (bounds.width() - gap * (count - 1)) / count
            node.options.forEachIndexed { index, option ->
                val left = bounds.left + index * (width + gap)
                val rect = RectF(left, bounds.top, left + width, bounds.bottom)
                val optionValue = XyluneProgramRuntime.render(option.value, state)
                paint.color = if (selected == optionValue) palette.primaryContainer else palette.surfaceVariant
                canvas.drawRoundRect(rect, dp(10), dp(10), paint)
                drawTextBlock(XyluneProgramRuntime.render(option.label, state), rect, node.style.copy(align = "center", fontSize = 13), false)
            }
        }

        private fun drawInput(node: XyluneProgramNode, bounds: RectF) {
            paint.color = color(node.style.background).takeIf { it != Color.TRANSPARENT } ?: palette.surfaceVariant
            canvas.drawRoundRect(bounds, dp(node.style.cornerRadius).coerceAtLeast(dp(8)), dp(node.style.cornerRadius).coerceAtLeast(dp(8)), paint)
            val value = state[node.value].orEmpty()
            drawTextBlock(
                if (value.isBlank()) XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state) else value,
                RectF(bounds.left + dp(10), bounds.top + dp(4), bounds.right - dp(10), bounds.bottom - dp(4)),
                node.style.copy(fontSize = node.style.fontSize.takeIf { it > 0 } ?: 15),
                false,
            )
        }

        private fun drawSlider(node: XyluneProgramNode, bounds: RectF) {
            val value = state[node.value]?.toDoubleOrNull()?.coerceIn(node.min, node.max) ?: node.min
            val ratio = ((value - node.min) / (node.max - node.min).coerceAtLeast(.000001)).toFloat()
            drawTextBlock(
                "${XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state)}  ${XyluneProgramRuntime.formatCompact(value)}",
                RectF(bounds.left, bounds.top, bounds.right, bounds.centerY()),
                node.style.copy(fontSize = 14),
                false,
            )
            val track = RectF(bounds.left, bounds.centerY() + dp(5), bounds.right, bounds.centerY() + dp(11))
            paint.color = palette.outline
            canvas.drawRoundRect(track, dp(3), dp(3), paint)
            paint.color = palette.primary
            canvas.drawRoundRect(RectF(track.left, track.top, track.left + track.width() * ratio, track.bottom), dp(3), dp(3), paint)
        }

        private fun drawProgress(node: XyluneProgramNode, bounds: RectF) {
            val value = XyluneProgramRuntime.render(node.value, state).toDoubleOrNull()?.coerceIn(node.min, node.max) ?: node.min
            val ratio = ((value - node.min) / (node.max - node.min).coerceAtLeast(.000001)).toFloat()
            if (node.label.isNotBlank()) {
                drawTextBlock(XyluneProgramRuntime.render(node.label, state), RectF(bounds.left, bounds.top, bounds.right, bounds.centerY()), node.style.copy(fontSize = 14), false)
            }
            val track = RectF(bounds.left, bounds.centerY() + dp(3), bounds.right, bounds.centerY() + dp(11))
            paint.color = palette.outline
            canvas.drawRoundRect(track, dp(4), dp(4), paint)
            paint.color = palette.primary
            canvas.drawRoundRect(RectF(track.left, track.top, track.left + track.width() * ratio, track.bottom), dp(4), dp(4), paint)
        }

        private fun drawList(node: XyluneProgramNode, bounds: RectF) {
            val items = node.items
            if (items.isEmpty()) return
            val rowHeight = bounds.height() / items.size
            val compactRows = rowHeight < dp(24)
            items.forEachIndexed { index, item ->
                val rect = RectF(bounds.left, bounds.top + index * rowHeight, bounds.right, bounds.top + (index + 1) * rowHeight)
                val label = XyluneProgramRuntime.render(item.label, state)
                val value = XyluneProgramRuntime.render(item.value, state)
                val detail = XyluneProgramRuntime.render(item.detail, state)
                if (compactRows) {
                    drawTextBlock(
                        listOf(label, value, detail).filter(String::isNotBlank).joinToString("  "),
                        rect,
                        node.style.copy(fontSize = node.style.fontSize.takeIf { it > 0 } ?: 12, emphasis = "medium"),
                        false,
                    )
                } else {
                    drawTextBlock(
                        label,
                        RectF(rect.left, rect.top, rect.centerX(), rect.bottom),
                        node.style.copy(fontSize = node.style.fontSize.takeIf { it > 0 } ?: 14, emphasis = "medium"),
                        false,
                    )
                    drawTextBlock(
                        listOf(value, detail).filter(String::isNotBlank).joinToString(" · "),
                        RectF(rect.centerX(), rect.top, rect.right, rect.bottom),
                        node.style.copy(fontSize = node.style.fontSize.takeIf { it > 0 } ?: 14, align = "end"),
                        false,
                    )
                }
                if (index < items.lastIndex) drawDivider(RectF(rect.left, rect.bottom - 1f, rect.right, rect.bottom + 1f))
            }
        }

        private fun drawChart(node: XyluneProgramNode, bounds: RectF) {
            val values = node.items.mapNotNull { XyluneProgramRuntime.render(it.value, state).toFloatOrNull() }
            if (values.size < 2) return
            val low = values.minOrNull() ?: 0f
            val high = values.maxOrNull() ?: 1f
            val range = (high - low).takeIf { it > 0f } ?: 1f
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = bounds.left + bounds.width() * index / (values.size - 1)
                val y = bounds.bottom - ((value - low) / range * bounds.height())
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke.color = palette.primary
            stroke.strokeWidth = dp(3)
            canvas.drawPath(path, stroke)
        }

        private fun drawDivider(bounds: RectF) {
            paint.color = palette.outline
            canvas.drawRect(bounds.left, bounds.centerY(), bounds.right, bounds.centerY() + max(1f, dp(1)), paint)
        }

        private fun drawBackground(style: XyluneProgramStyle, bounds: RectF) {
            val value = color(style.background)
            if (value == Color.TRANSPARENT) return
            paint.color = value
            canvas.drawRoundRect(bounds, dp(style.cornerRadius), dp(style.cornerRadius), paint)
        }

        private fun drawTextBlock(text: String, bounds: RectF, style: XyluneProgramStyle, large: Boolean) {
            val value = text.trim()
            if (value.isBlank() || bounds.width() <= 1f || bounds.height() <= 1f) return
            paint.color = textColor(style.foreground)
            paint.typeface = typeface(style)
            val requested = style.fontSize.takeIf { it > 0 } ?: if (large) 30 else 16
            val floor = min(requested, if (large) 18 else 11).coerceAtLeast(1)
            val width = bounds.width().toInt().coerceAtLeast(1)
            val alignment = when (style.align) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                "end" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            var selected: StaticLayout? = null
            var selectedSp = floor
            var floorLayout: StaticLayout? = null
            for (candidate in requested downTo floor) {
                paint.textSize = sp(candidate)
                val textPaint = TextPaint(paint)
                val lineHeight = (textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent).coerceAtLeast(1f)
                val maxLines = (bounds.height() / lineHeight).toInt().coerceAtLeast(1)
                val layout = StaticLayout.Builder.obtain(value, 0, value.length, textPaint, width)
                    .setAlignment(alignment)
                    .setIncludePad(false)
                    .setLineSpacing(0f, 1f)
                    .setMaxLines(maxLines)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .build()
                floorLayout = layout
                val ellipsized = layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
                if (!ellipsized && layout.height <= bounds.height() + .5f) {
                    selected = layout
                    selectedSp = candidate
                    break
                }
            }
            val layout = selected ?: floorLayout ?: return
            minimumTextSp = min(minimumTextSp, selectedSp.toFloat())
            val ellipsized = layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
            if (ellipsized) {
                clippedTextCount += 1
                if (clippedSamples.size < 8) clippedSamples += value.take(80)
            }
            if (selected == null && layout.height > bounds.height() + .5f) crampedTextCount += 1
            val y = bounds.top + ((bounds.height() - layout.height) / 2f).coerceAtLeast(0f)
            canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(bounds.left, y)
            layout.draw(canvas)
            canvas.restore()
        }

        private fun isActionControl(node: XyluneProgramNode): Boolean = when (node.type) {
            "button", "toggle", "input" -> node.action.isNotBlank()
            "choice" -> node.action.isNotBlank() || node.options.any { it.action.isNotBlank() }
            "list" -> node.items.any { it.action.isNotBlank() }
            else -> false
        }

        private fun childWeight(node: XyluneProgramNode): Float = node.style.weight.takeIf { it > 0f } ?: when (node.type) {
            "spacer" -> .25f
            "text", "button", "toggle", "choice", "divider" -> .7f
            "metric" -> 1.1f
            "chart", "list" -> 1.8f
            else -> 1f
        }

        private fun allocateAxis(total: Float, preferred: List<Float>, flex: List<Float>): List<Float> {
            if (preferred.isEmpty()) return emptyList()
            val safePreferred = preferred.map { it.coerceAtLeast(0f) }
            val preferredTotal = safePreferred.sum()
            if (preferredTotal >= total && preferredTotal > 0f) {
                val scale = total / preferredTotal
                return safePreferred.map { it * scale }
            }
            val remaining = (total - preferredTotal).coerceAtLeast(0f)
            val safeFlex = flex.map { it.coerceAtLeast(0f) }
            val flexTotal = safeFlex.sum()
            if (flexTotal <= 0f) return List(preferred.size) { total / preferred.size }
            return safePreferred.indices.map { index -> safePreferred[index] + remaining * safeFlex[index] / flexTotal }
        }

        private fun preferredHeight(node: XyluneProgramNode): Float = when (node.type) {
            "spacer" -> 0f
            "divider" -> dp(1)
            "text" -> sp(node.style.fontSize.takeIf { it > 0 } ?: 16) * 1.25f
            "metric" -> if (node.label.isBlank()) sp(node.style.fontSize.takeIf { it > 0 } ?: 30) * 1.2f else dp(52)
            "button", "toggle", "choice", "input" -> dp(32)
            "slider", "progress" -> dp(40)
            "list" -> node.items.size * sp(node.style.fontSize.takeIf { it > 0 } ?: 12) * 1.2f
            "chart" -> dp(72)
            "row", "stack" -> node.children.maxOfOrNull(::preferredHeight) ?: 0f
            "column" -> node.children.sumOf { preferredHeight(it).toDouble() }.toFloat() +
                dp(node.style.gap) * (node.children.size - 1).coerceAtLeast(0)
            else -> dp(24)
        }

        private fun preferredWidth(node: XyluneProgramNode): Float = when (node.type) {
            "spacer" -> 0f
            "text" -> measureText(XyluneProgramRuntime.render(node.text.ifBlank { node.value }, state), node.style, 16)
            "metric" -> max(
                measureText(XyluneProgramRuntime.render(node.label, state), node.style.copy(emphasis = "medium"), 13),
                measureText(
                    XyluneProgramRuntime.render(node.value.ifBlank { node.text }, state),
                    node.style.copy(emphasis = "strong"),
                    node.style.fontSize.takeIf { it > 0 } ?: 30,
                ),
            )
            "button", "toggle", "input" -> measureText(
                XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state),
                node.style,
                14,
            ) + dp(20)
            "divider" -> dp(8)
            else -> dp(48)
        }

        private fun measureText(value: String, style: XyluneProgramStyle, fallbackSp: Int): Float {
            paint.typeface = typeface(style)
            paint.textSize = sp(style.fontSize.takeIf { it > 0 } ?: fallbackSp)
            return paint.measureText(value) + dp(style.padding * 2)
        }

        private fun typeface(style: XyluneProgramStyle) = when (style.emphasis) {
            "strong" -> android.graphics.Typeface.DEFAULT_BOLD
            "medium" -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            else -> android.graphics.Typeface.DEFAULT
        }

        private fun ellipsize(value: String, maxWidth: Float, paint: Paint): String {
            if (paint.measureText(value) <= maxWidth) return value
            val suffix = "…"
            var low = 0
            var high = value.length
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (paint.measureText(value.substring(0, mid) + suffix) <= maxWidth) low = mid else high = mid - 1
            }
            return value.substring(0, low) + suffix
        }

        private fun color(value: String): Int = when (value) {
            "primary" -> palette.primaryContainer
            "secondary" -> palette.secondaryContainer
            "tertiary" -> palette.tertiaryContainer
            "surface" -> palette.surface
            "surface_variant" -> palette.surfaceVariant
            "error" -> palette.errorContainer
            "transparent", "" -> Color.TRANSPARENT
            else -> parseColor(value) ?: Color.TRANSPARENT
        }

        private fun textColor(value: String): Int = when (value) {
            "primary" -> palette.primary
            "secondary" -> palette.secondary
            "tertiary" -> palette.tertiary
            "muted" -> palette.mutedText
            "primary_text" -> palette.onPrimaryContainer
            "error" -> palette.error
            else -> parseColor(value) ?: palette.text
        }

        private fun parseColor(value: String): Int? = runCatching { Color.parseColor(value) }.getOrNull()
        private fun dp(value: Int): Float = value * density
        private fun sp(value: Int): Float = value * scaledDensity
    }

    private data class Palette(val dark: Boolean) {
        val surface = if (dark) Color.rgb(30, 30, 34) else Color.rgb(250, 249, 252)
        val surfaceVariant = if (dark) Color.rgb(52, 51, 58) else Color.rgb(235, 233, 240)
        val primary = if (dark) Color.rgb(190, 198, 255) else Color.rgb(65, 79, 170)
        val primaryContainer = if (dark) Color.rgb(48, 59, 130) else Color.rgb(222, 225, 255)
        val onPrimaryContainer = if (dark) Color.WHITE else Color.rgb(20, 28, 90)
        val secondary = if (dark) Color.rgb(197, 196, 221) else Color.rgb(90, 90, 112)
        val secondaryContainer = if (dark) Color.rgb(68, 67, 87) else Color.rgb(226, 224, 249)
        val tertiary = if (dark) Color.rgb(235, 183, 213) else Color.rgb(121, 73, 105)
        val tertiaryContainer = if (dark) Color.rgb(91, 48, 75) else Color.rgb(255, 216, 238)
        val text = if (dark) Color.rgb(232, 225, 229) else Color.rgb(28, 27, 31)
        val mutedText = if (dark) Color.rgb(202, 196, 204) else Color.rgb(73, 69, 78)
        val outline = if (dark) Color.rgb(110, 105, 116) else Color.rgb(198, 194, 202)
        val error = if (dark) Color.rgb(255, 180, 171) else Color.rgb(186, 26, 26)
        val errorContainer = if (dark) Color.rgb(105, 0, 5) else Color.rgb(255, 218, 214)
    }
}
