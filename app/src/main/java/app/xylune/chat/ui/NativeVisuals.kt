package app.xylune.chat.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal data class NativeDiagram(
    val direction: String,
    val nodes: List<NativeDiagramNode>,
    val edges: List<NativeDiagramEdge>,
    val sequence: Boolean = false,
)
internal data class NativeDiagramNode(val id: String, val label: String)
internal data class NativeDiagramEdge(val from: String, val to: String, val label: String = "", val dashed: Boolean = false)

internal object NativeDiagramParser {
    fun parse(source: String): NativeDiagram {
        val first = source.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val sequence = first.startsWith("sequenceDiagram", true)
        val dot = Regex("(?i)^(?:di)?graph\\b").containsMatchIn(first) && "{" in source
        return when { sequence -> parseSequence(source); dot -> parseDot(source); else -> parseFlow(source) }
    }

    private fun parseFlow(source: String): NativeDiagram {
        val first = source.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val direction = Regex("(?i)(?:flowchart|graph)\\s+(TB|TD|BT|LR|RL)").find(first)?.groupValues?.get(1)?.uppercase() ?: "TB"
        val nodes = linkedMapOf<String, NativeDiagramNode>()
        val edges = mutableListOf<NativeDiagramEdge>()
        val arrow = Regex("(-->|==>|---|-\\.->)(?:\\|([^|]{1,100})\\|)?")
        source.lineSequence().flatMap { it.substringBefore("%%").split(';').asSequence() }.forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || line.startsWith("flowchart", true) || line.startsWith("graph", true) || line.startsWith("classDef", true) || line.startsWith("style ", true)) return@forEach
            val matches = arrow.findAll(line).toList()
            if (matches.isNotEmpty()) {
                var left = parseNodeToken(line.substring(0, matches.first().range.first))
                matches.forEachIndexed { index, match ->
                    val rightEnd = matches.getOrNull(index + 1)?.range?.first ?: line.length
                    val right = parseNodeToken(line.substring(match.range.last + 1, rightEnd))
                    val currentLeft = left
                    if (currentLeft != null && right != null) {
                        nodes[currentLeft.id] = merge(nodes[currentLeft.id], currentLeft)
                        nodes[right.id] = merge(nodes[right.id], right)
                        edges += NativeDiagramEdge(currentLeft.id, right.id, cleanLabel(match.groupValues[2]), match.groupValues[1].contains('.'))
                    }
                    left = right
                }
            } else parseNodeToken(line)?.let { nodes[it.id] = merge(nodes[it.id], it) }
        }
        return NativeDiagram(direction, nodes.values.take(80), edges.filter { it.from in nodes && it.to in nodes }.take(140))
    }

    private fun parseSequence(source: String): NativeDiagram {
        val nodes = linkedMapOf<String, NativeDiagramNode>()
        val edges = mutableListOf<NativeDiagramEdge>()
        val participant = Regex("(?i)^(?:participant|actor)\\s+([A-Za-z0-9_.-]+)(?:\\s+as\\s+(.+))?")
        val message = Regex("^([A-Za-z0-9_.-]+)\\s*(-{1,2}>>?|--?[x)])\\s*([A-Za-z0-9_.-]+)\\s*:\\s*(.+)$")
        source.lineSequence().forEach { raw ->
            val line = raw.trim()
            participant.find(line)?.let { match ->
                val id = match.groupValues[1]
                nodes[id] = NativeDiagramNode(id, cleanLabel(match.groupValues[2].ifBlank { id }))
                return@forEach
            }
            message.find(line)?.let { match ->
                val from = match.groupValues[1]
                val to = match.groupValues[3]
                nodes.putIfAbsent(from, NativeDiagramNode(from, from))
                nodes.putIfAbsent(to, NativeDiagramNode(to, to))
                edges += NativeDiagramEdge(from, to, cleanLabel(match.groupValues[4]), match.groupValues[2].startsWith("--"))
            }
        }
        return NativeDiagram("LR", nodes.values.take(16), edges.take(80), sequence = true)
    }

    private fun parseDot(source: String): NativeDiagram {
        val nodes = linkedMapOf<String, NativeDiagramNode>()
        val edges = mutableListOf<NativeDiagramEdge>()
        val direction = Regex("(?i)rankdir\\s*=\\s*(LR|RL|TB|BT)").find(source)?.groupValues?.get(1)?.uppercase() ?: "TB"
        val edge = Regex("([A-Za-z0-9_.-]+)\\s*(->|--)\\s*([A-Za-z0-9_.-]+)(?:\\s*\\[([^\\]]*)\\])?")
        val node = Regex("^\\s*([A-Za-z0-9_.-]+)\\s*\\[([^\\]]*)\\]\\s*$")
        source.replace('{', ';').replace('}', ';').split(';', '\n').forEach { raw ->
            val line = raw.trim()
            edge.find(line)?.let { match ->
                val from = match.groupValues[1]; val to = match.groupValues[3]
                nodes.putIfAbsent(from, NativeDiagramNode(from, from)); nodes.putIfAbsent(to, NativeDiagramNode(to, to))
                val label = Regex("(?i)label\\s*=\\s*[\"']?([^\"',]+)").find(match.groupValues[4])?.groupValues?.get(1).orEmpty()
                edges += NativeDiagramEdge(from, to, cleanLabel(label), match.groupValues[2] == "--")
                return@forEach
            }
            node.find(line)?.let { match ->
                val id = match.groupValues[1]
                val label = Regex("(?i)label\\s*=\\s*[\"']?([^\"',]+)").find(match.groupValues[2])?.groupValues?.get(1) ?: id
                nodes[id] = NativeDiagramNode(id, cleanLabel(label))
            }
        }
        return NativeDiagram(direction, nodes.values.take(80), edges.take(140))
    }

    private fun parseNodeToken(raw: String): NativeDiagramNode? {
        val cleaned = raw.trim().substringBefore(":::").trim()
        if (cleaned.isEmpty()) return null

        // Avoid a delimiter-heavy regular expression here. Android 16's ICU
        // engine rejects some patterns which the desktop JDK accepts, and that
        // used to crash message rendering before Xylune could fall back safely.
        val idEnd = cleaned.indexOfFirst { it !in NODE_ID_CHARS }
            .let { if (it < 0) cleaned.length else it }
        if (idEnd == 0) return null

        val id = cleaned.substring(0, idEnd)
        val suffix = cleaned.substring(idEnd).trimStart()
        if (suffix.isEmpty()) return NativeDiagramNode(id, id)

        val closingDelimiter = when (suffix.first()) {
            '[' -> ']'
            '(' -> ')'
            '{' -> '}'
            else -> return NativeDiagramNode(id, id)
        }
        val closingIndex = suffix.indexOf(closingDelimiter, startIndex = 1)
        val label = if (closingIndex > 1) suffix.substring(1, closingIndex) else id
        return NativeDiagramNode(id, cleanLabel(label))
    }

    private val NODE_ID_CHARS =
        (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('_', '.', '-')).toSet()

    private fun merge(old: NativeDiagramNode?, new: NativeDiagramNode) = if (old == null || new.label != new.id) new else old
    private fun cleanLabel(value: String) = value.trim().trim('"', '\'', '`').replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ").take(140)
}

@Composable
fun NativeDiagramBlock(source: String) {
    val diagram = remember(source) { NativeDiagramParser.parse(source) }
    var expanded by remember(source) { mutableStateOf(false) }
    VisualFrame("DIAGRAM • native", onExpand = { expanded = true }) {
        if (diagram.nodes.isEmpty()) HighlightedCodeText(
            language = "mermaid",
            code = source,
            style = MaterialTheme.typography.bodySmall,
            softWrap = true,
        )
        else DiagramCanvas(diagram, large = false)
    }
    if (expanded) VisualDialog("Diagram", onDismiss = { expanded = false }) { DiagramCanvas(diagram, large = true) }
}

@Composable
private fun DiagramCanvas(diagram: NativeDiagram, large: Boolean) {
    if (diagram.sequence) SequenceCanvas(diagram, large) else FlowCanvas(diagram, large)
}

@Composable
private fun FlowCanvas(diagram: NativeDiagram, large: Boolean) {
    val levels = remember(diagram) { diagramLevels(diagram) }
    val horizontal = diagram.direction in setOf("LR", "RL")
    val levelGroups = levels.entries.groupBy({ it.value }, { it.key }).toSortedMap()
    val across = max(1, levelGroups.values.maxOfOrNull { it.size } ?: 1)
    val deep = max(1, levelGroups.size)
    val nodeW = if (large) 180.dp else 148.dp
    val nodeH = 62.dp
    val gapX = 26.dp
    val gapY = 38.dp
    val width = if (horizontal) (deep * (nodeW.value + gapX.value) + 20).dp else (across * (nodeW.value + gapX.value) + 20).dp
    val height = if (horizontal) (across * (nodeH.value + gapY.value) + 20).dp else (deep * (nodeH.value + gapY.value) + 20).dp
    val positions = remember(diagram, levels, horizontal, width, height) {
        val map = mutableMapOf<String, Pair<Float, Float>>()
        val maxLevel = levelGroups.keys.maxOrNull() ?: 0
        levelGroups.forEach { (level, ids) ->
            ids.forEachIndexed { row, id ->
                val visualLevel = if (diagram.direction in setOf("RL", "BT")) maxLevel - level else level
                val x = if (horizontal) 10 + visualLevel * (nodeW.value + gapX.value) else (width.value - ids.size * nodeW.value - (ids.size - 1) * gapX.value) / 2 + row * (nodeW.value + gapX.value)
                val y = if (horizontal) (height.value - ids.size * nodeH.value - (ids.size - 1) * gapY.value) / 2 + row * (nodeH.value + gapY.value) else 10 + visualLevel * (nodeH.value + gapY.value)
                map[id] = x to y
            }
        }
        map
    }
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val text = MaterialTheme.colorScheme.onSurface
    LowSensitivityHorizontalScroll(Modifier.fillMaxWidth()) {
        Canvas(Modifier.width(width.coerceAtLeast(320.dp)).height(height.coerceAtLeast(150.dp))) {
            val nw = with(density) { nodeW.toPx() }; val nh = with(density) { nodeH.toPx() }
            diagram.edges.forEach { edge ->
                val a = positions[edge.from] ?: return@forEach
                val b = positions[edge.to] ?: return@forEach
                val start = Offset(with(density) { a.first.dp.toPx() } + nw / 2, with(density) { a.second.dp.toPx() } + nh / 2)
                val end = Offset(with(density) { b.first.dp.toPx() } + nw / 2, with(density) { b.second.dp.toPx() } + nh / 2)
                val vector = end - start
                val unit = vector / vector.getDistance().coerceAtLeast(1f)
                val inset = if (kotlin.math.abs(unit.x) > kotlin.math.abs(unit.y)) nw / 2 + 5.dp.toPx() else nh / 2 + 5.dp.toPx()
                drawArrow(start + unit * inset, end - unit * inset, primary, edge.dashed, clip = 0f)
                if (edge.label.isNotBlank()) drawLabel(edge.label, Offset((start.x + end.x) / 2, (start.y + end.y) / 2 - 5.dp.toPx()), text, surface)
            }
            diagram.nodes.forEach { node ->
                val p = positions[node.id] ?: return@forEach
                val left = with(density) { p.first.dp.toPx() }; val top = with(density) { p.second.dp.toPx() }
                drawRoundRect(surface, Offset(left, top), Size(nw, nh), CornerRadius(13.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Fill)
                drawRoundRect(primary.copy(alpha = .75f), Offset(left, top), Size(nw, nh), CornerRadius(13.dp.toPx()), style = Stroke(1.2.dp.toPx()))
                drawCenteredText(node.label, Rect(left, top, left + nw, top + nh), text)
            }
        }
    }
}

@Composable
private fun SequenceCanvas(diagram: NativeDiagram, large: Boolean) {
    val nodeW = if (large) 164.dp else 132.dp
    val gap = if (large) 52.dp else 34.dp
    val width = (diagram.nodes.size * (nodeW.value + gap.value) + 20).dp.coerceAtLeast(320.dp)
    val height = (110 + diagram.edges.size * 62).dp.coerceAtMost(if (large) 1600.dp else 720.dp)
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val text = MaterialTheme.colorScheme.onSurface
    val density = LocalDensity.current
    LowSensitivityHorizontalScroll(Modifier.fillMaxWidth()) {
        Canvas(Modifier.width(width).height(height)) {
            val nw = with(density) { nodeW.toPx() }; val gp = with(density) { gap.toPx() }
            val centers = diagram.nodes.mapIndexed { index, node -> node.id to (10.dp.toPx() + nw / 2 + index * (nw + gp)) }.toMap()
            diagram.nodes.forEach { node ->
                val center = centers.getValue(node.id)
                drawRoundRect(surface, Offset(center - nw / 2, 12.dp.toPx()), Size(nw, 54.dp.toPx()), CornerRadius(12.dp.toPx()))
                drawRoundRect(primary.copy(alpha = .7f), Offset(center - nw / 2, 12.dp.toPx()), Size(nw, 54.dp.toPx()), CornerRadius(12.dp.toPx()), style = Stroke(1.2.dp.toPx()))
                drawCenteredText(node.label, Rect(center - nw / 2, 12.dp.toPx(), center + nw / 2, 66.dp.toPx()), text)
                drawLine(primary.copy(alpha = .35f), Offset(center, 66.dp.toPx()), Offset(center, height.toPx() - 12.dp.toPx()), strokeWidth = 1.dp.toPx())
            }
            diagram.edges.forEachIndexed { index, edge ->
                val from = centers[edge.from] ?: return@forEachIndexed
                val to = centers[edge.to] ?: return@forEachIndexed
                val y = (96 + index * 62).dp.toPx()
                drawArrow(Offset(from, y), Offset(to, y), primary, edge.dashed)
                if (edge.label.isNotBlank()) drawLabel(edge.label, Offset((from + to) / 2, y - 7.dp.toPx()), text, surface)
            }
        }
    }
}

private fun diagramLevels(diagram: NativeDiagram): Map<String, Int> {
    val levels = diagram.nodes.associate { it.id to 0 }.toMutableMap()
    repeat(diagram.nodes.size.coerceAtMost(20)) {
        var changed = false
        diagram.edges.forEach { edge ->
            val candidate = (levels[edge.from] ?: 0) + 1
            if (candidate > (levels[edge.to] ?: 0) && candidate < diagram.nodes.size) { levels[edge.to] = candidate; changed = true }
        }
        if (!changed) return levels
    }
    return levels.mapValues { it.value.coerceAtMost(12) }
}

private data class NativeChart(val type: String, val title: String, val series: List<NativeSeries>)
private data class NativeSeries(val name: String, val points: List<Pair<String, Double>>)

private fun parseChart(source: String): NativeChart {
    runCatching {
        val root = Json.parseToJsonElement(source).jsonObject
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase().ifBlank { "bar" }
        val title = root["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val series = root["series"]?.jsonArray?.mapIndexed { index, value ->
            val item = value.jsonObject
            val name = item["name"]?.jsonPrimitive?.contentOrNull ?: "Series ${index + 1}"
            val points = item["values"]?.jsonArray?.mapIndexedNotNull { pointIndex, point ->
                runCatching {
                    if (point is kotlinx.serialization.json.JsonObject) {
                        val label = point["label"]?.jsonPrimitive?.contentOrNull ?: (pointIndex + 1).toString()
                        val number = point["value"]?.jsonPrimitive?.doubleOrNull ?: return@runCatching null
                        label to number
                    } else (pointIndex + 1).toString() to point.jsonPrimitive.doubleOrNull!!
                }.getOrNull()
            }.orEmpty()
            NativeSeries(name, points.take(80))
        }.orEmpty()
        if (series.isNotEmpty()) return NativeChart(type, title, series.take(8))
    }
    val values = source.lineSequence().mapNotNull { line ->
        val split = line.split(Regex("[:,=]"), limit = 2)
        if (split.size == 2) split[1].trim().toDoubleOrNull()?.let { split[0].trim() to it } else null
    }.take(80).toList()
    return NativeChart("bar", "", listOf(NativeSeries("Values", values)))
}

@Composable
fun NativeChartBlock(source: String) {
    val chart = remember(source) { parseChart(source) }
    var expanded by remember(source) { mutableStateOf(false) }
    VisualFrame(listOfNotNull("CHART • ${chart.type.lowercase()} • native", chart.title.takeIf(String::isNotBlank)).joinToString(" — "), onExpand = { expanded = true }) {
        if (chart.series.all { it.points.isEmpty() }) Text(uiText("Use chart JSON or `label: value` lines."), style = MaterialTheme.typography.bodySmall)
        else ChartCanvas(chart, if (chart.type == "pie") 270.dp else 250.dp, large = false)
    }
    if (expanded) VisualDialog(chart.title.ifBlank { "Chart" }, onDismiss = { expanded = false }) { ChartCanvas(chart, 520.dp, large = true) }
}

@Composable
private fun ChartCanvas(chart: NativeChart, height: Dp, large: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val chartBackground = MaterialTheme.colorScheme.surfaceContainer
    val text = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val colors = listOf(primary, secondary, tertiary, Color(0xFFE69F00), Color(0xFF009E73), Color(0xFFCC79A7), Color(0xFF56B4E9), Color(0xFFD55E00))
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val all = chart.series.flatMap { series -> series.points.map { it.second } }
        if (all.isEmpty()) return@Canvas
        if (chart.type == "pie" || chart.type == "donut") {
            val values = chart.series.first().points.filter { it.second > 0 }
            val total = values.sumOf { it.second }.takeIf { it > 0 } ?: return@Canvas
            val diameter = minOf(size.width * .62f, size.height * .75f)
            val topLeft = Offset(12.dp.toPx(), (size.height - diameter) / 2)
            var start = -90f
            values.forEachIndexed { index, (_, value) ->
                val sweep = (value / total * 360).toFloat()
                drawArc(colors[index % colors.size], start, sweep, true, topLeft, Size(diameter, diameter))
                start += sweep
            }
            values.take(8).forEachIndexed { index, (label, value) ->
                val y = 24.dp.toPx() + index * 24.dp.toPx()
                drawCircle(colors[index % colors.size], 5.dp.toPx(), Offset(diameter + 34.dp.toPx(), y))
                drawText("$label  ${SafeNumber(value)}", Offset(diameter + 46.dp.toPx(), y + 5.dp.toPx()), text, 12.dp.toPx())
            }
            if (chart.type == "donut") drawCircle(chartBackground, diameter * .23f, Offset(topLeft.x + diameter / 2, topLeft.y + diameter / 2))
            return@Canvas
        }
        val minValue = minOf(0.0, all.minOrNull() ?: 0.0)
        val maxValue = maxOf(1.0, all.maxOrNull() ?: 1.0)
        val plotLeft = 42.dp.toPx(); val plotTop = 14.dp.toPx(); val plotRight = size.width - 10.dp.toPx(); val plotBottom = size.height - 34.dp.toPx()
        repeat(5) { line ->
            val y = plotTop + (plotBottom - plotTop) * line / 4f
            drawLine(grid, Offset(plotLeft, y), Offset(plotRight, y), strokeWidth = 1.dp.toPx())
            val value = maxValue - (maxValue - minValue) * line / 4.0
            drawText(SafeNumber(value), Offset(2.dp.toPx(), y + 4.dp.toPx()), text.copy(alpha = .7f), 10.dp.toPx())
        }
        val count = chart.series.maxOf { it.points.size }.coerceAtLeast(1)
        fun point(index: Int, value: Double): Offset {
            val x = if (count == 1) (plotLeft + plotRight) / 2 else plotLeft + (plotRight - plotLeft) * index / (count - 1).toFloat()
            val y = plotBottom - ((value - minValue) / (maxValue - minValue).coerceAtLeast(.000001) * (plotBottom - plotTop)).toFloat()
            return Offset(x, y)
        }
        chart.series.forEachIndexed { seriesIndex, series ->
            val color = colors[seriesIndex % colors.size]
            if (chart.type == "bar") {
                val groupWidth = (plotRight - plotLeft) / count
                val barWidth = (groupWidth * .72f / chart.series.size).coerceAtLeast(3.dp.toPx())
                series.points.forEachIndexed { index, (_, value) ->
                    val zero = point(index, 0.0); val valuePoint = point(index, value)
                    val left = plotLeft + groupWidth * index + groupWidth * .14f + seriesIndex * barWidth
                    drawRoundRect(color, Offset(left, minOf(zero.y, valuePoint.y)), Size(barWidth, kotlin.math.abs(zero.y - valuePoint.y).coerceAtLeast(1f)), CornerRadius(3.dp.toPx()))
                }
            } else {
                val path = Path()
                series.points.forEachIndexed { index, (_, value) -> val p = point(index, value); if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                if (chart.type == "area" && series.points.isNotEmpty()) {
                    val last = point(series.points.lastIndex, series.points.last().second); val first = point(0, series.points.first().second)
                    val fill = Path().apply { addPath(path); lineTo(last.x, plotBottom); lineTo(first.x, plotBottom); close() }
                    drawPath(fill, color.copy(alpha = .2f))
                }
                if (chart.type != "scatter") drawPath(path, color, style = Stroke(if (large) 3.dp.toPx() else 2.dp.toPx()))
                series.points.forEachIndexed { index, (_, value) -> drawCircle(color, 3.5.dp.toPx(), point(index, value)) }
            }
        }
        chart.series.first().points.take(if (large) 24 else 12).forEachIndexed { index, (label, _) ->
            if (index % max(1, count / 8) == 0) drawText(label.take(10), Offset(point(index, minValue).x - 8.dp.toPx(), plotBottom + 18.dp.toPx()), text.copy(alpha = .75f), 10.dp.toPx())
        }
    }
}

@Composable
private fun VisualFrame(title: String, onExpand: () -> Unit, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onExpand) { Icon(Icons.Outlined.OpenInFull, uiText("Open full-screen preview")) }
            }
            content()
        }
    }
}

@Composable
private fun VisualDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxSize().padding(10.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row { Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, uiText("Close")) } }
                content()
            }
        }
    }
}

private fun DrawScope.drawArrow(start: Offset, end: Offset, color: Color, dashed: Boolean, clip: Float = 34.dp.toPx()) {
    val vector = end - start
    val length = vector.getDistance().coerceAtLeast(1f)
    val unit = vector / length
    val clippedStart = start + unit * clip
    val clippedEnd = end - unit * clip
    if (dashed) {
        val segment = 7.dp.toPx(); var at = 0f
        while (at < (clippedEnd - clippedStart).getDistance()) { val a = clippedStart + unit * at; val b = clippedStart + unit * minOf(at + segment, (clippedEnd - clippedStart).getDistance()); drawLine(color, a, b, strokeWidth = 1.6.dp.toPx()); at += segment * 1.8f }
    } else drawLine(color, clippedStart, clippedEnd, strokeWidth = 1.8.dp.toPx())
    val angle = kotlin.math.atan2(unit.y, unit.x)
    val wing = 8.dp.toPx()
    drawLine(color, clippedEnd, Offset(clippedEnd.x - wing * cos(angle - PI / 6).toFloat(), clippedEnd.y - wing * sin(angle - PI / 6).toFloat()), strokeWidth = 1.8.dp.toPx())
    drawLine(color, clippedEnd, Offset(clippedEnd.x - wing * cos(angle + PI / 6).toFloat(), clippedEnd.y - wing * sin(angle + PI / 6).toFloat()), strokeWidth = 1.8.dp.toPx())
}

private fun DrawScope.drawCenteredText(value: String, rect: Rect, color: Color) {
    val lines = value.chunked(24).take(2)
    lines.forEachIndexed { index, line ->
        val y = rect.center.y + (index - (lines.size - 1) / 2f) * 16.dp.toPx()
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgbCompat(); textSize = 13.dp.toPx(); textAlign = Paint.Align.CENTER }
            canvas.nativeCanvas.drawText(line, rect.center.x, y - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }
}

private fun DrawScope.drawLabel(value: String, center: Offset, color: Color, background: Color) {
    val clean = value.take(42)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgbCompat(); textSize = 10.dp.toPx(); textAlign = Paint.Align.CENTER }
    val width = paint.measureText(clean) + 9.dp.toPx()
    drawRoundRect(background, Offset(center.x - width / 2, center.y - 10.dp.toPx()), Size(width, 17.dp.toPx()), CornerRadius(5.dp.toPx()))
    drawIntoCanvas { it.nativeCanvas.drawText(clean, center.x, center.y + 3.dp.toPx(), paint) }
}

private fun DrawScope.drawText(value: String, at: Offset, color: Color, size: Float) = drawIntoCanvas { canvas ->
    canvas.nativeCanvas.drawText(value, at.x, at.y, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.toArgbCompat(); textSize = size })
}

private fun SafeNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
