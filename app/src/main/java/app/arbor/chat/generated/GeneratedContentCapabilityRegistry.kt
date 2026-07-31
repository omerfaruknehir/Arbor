package app.arbor.chat.generated

import app.arbor.chat.widgets.ArborProgramParser
import app.arbor.chat.widgets.ArborProgramSurface
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest

@Serializable
enum class GeneratedBlockType { CHAT_UI, HOME_WIDGET, CHART, DIAGRAM }

@Serializable
data class GeneratedValidationError(val phase: String, val path: String, val message: String)

data class GeneratedValidationResult(val errors: List<GeneratedValidationError>) {
    val valid: Boolean get() = errors.isEmpty()
    fun summary(): String = errors.joinToString("\n") { "${it.path}: ${it.message} (${it.phase})" }
    companion object { val Valid = GeneratedValidationResult(emptyList()) }
}

data class GeneratedFenceCapability(
    val type: GeneratedBlockType,
    val canonicalFence: String,
    val aliases: Set<String>,
    val maxSourceChars: Int,
)

/** Authoritative prompt and validator registry for native generated content. */
object GeneratedContentCapabilityRegistry {
    const val CONTRACT_FAMILY = "arbor-generated-content/2"
    const val VALIDATOR_VERSION = "2.0.0"
    val chartTypes = setOf("bar", "line", "area", "scatter", "pie", "donut")
    val programNodeTypes: Set<String> get() = ArborProgramParser.nodeTypes
    val programActionTypes: Set<String> get() = ArborProgramParser.actionOps
    val widgetCapabilityTypes: Set<String> get() = ArborProgramParser.capabilityTypes
    val widgetDataSourceTypes: Set<String> get() = ArborProgramParser.dataSourceTypes

    // No aliases for the removed arbor-ui/ui/widget/mini_app formats.
    val fences = listOf(
        GeneratedFenceCapability(GeneratedBlockType.CHAT_UI, "arbor-snippet", setOf("arbor-snippet"), 96_000),
        GeneratedFenceCapability(GeneratedBlockType.HOME_WIDGET, "arbor-widget", setOf("arbor-widget"), 96_000),
        GeneratedFenceCapability(GeneratedBlockType.CHART, "arbor-chart", setOf("arbor-chart", "chart", "bar-chart", "barchart", "line-chart", "pie-chart"), 48_000),
        GeneratedFenceCapability(GeneratedBlockType.DIAGRAM, "mermaid", setOf("mermaid", "graph", "diagram", "dot", "graphviz"), 48_000),
    )
    val fenceNames: Set<String> = fences.flatMapTo(linkedSetOf()) { it.aliases }
    val CONTRACT_VERSION: String by lazy { contractVersionForShape(contractShape()) }

    fun contractShape(): String = buildString {
        append(VALIDATOR_VERSION).append('|')
        fences.sortedBy { it.canonicalFence }.forEach { append(it.canonicalFence).append(':').append(it.aliases.sorted()).append(':').append(it.maxSourceChars).append('|') }
        append("nodes=").append(programNodeTypes.sorted()).append('|')
        append("actions=").append(programActionTypes.sorted()).append('|')
        append("capabilities=").append(widgetCapabilityTypes.sorted()).append('|')
        append("sources=").append(widgetDataSourceTypes.sorted()).append('|')
        append("charts=").append(chartTypes.sorted()).append('|')
        append("limits=nodes160,depth12,state64,actionGroups64,sources12,origins8,series8,points80,diagramLines240")
        append("|security=no-html-js-jsx-webview-reflection-shell-downloaded-code;widget-grants-instance-scoped")
    }

    fun contractVersionForShape(shape: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(shape.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$CONTRACT_FAMILY-${digest.take(8)}"
    }

    fun capability(language: String): GeneratedFenceCapability? = fences.firstOrNull { language.lowercase() in it.aliases }

    fun compactSummary(): String = """
        Arbor generated-content contract: $CONTRACT_VERSION; validator $VALIDATOR_VERSION.
        Use `arbor-snippet` only for interactive content inside a chat message. Use `arbor-widget` only for an installable Android Home-screen program. They have separate schemas and are never interchangeable.
        Snippets have no Android permissions, background jobs, network, location, or folder access. Widgets must declare every capability with a user-facing reason and receive a per-widget grant before pinning. Network grants are exact HTTPS origins; folder grants are one user-selected document tree; location is approximate or precise; scheduled refresh is explicit and Android-limited to 15 minutes or slower.
        Both surfaces use one bounded component tree (${programNodeTypes.sorted().joinToString()}) and one bounded action language (${programActionTypes.sorted().joinToString()}). Build quizzes, forms, calculators, trackers, dashboards, and other experiences by composing nodes; never invent category-specific widget types.
        Never emit the removed `arbor-ui`, `ui`, `arbor-form`, `widget`, or `mini_app` formats. Never emit HTML, JavaScript, JSX, WebView content, reflection, shell commands, downloaded code, or an executable fallback.
    """.trimIndent()

    fun promptForRequest(userText: String): String = buildString {
        append(compactSummary())
        relevantTypes(userText).forEach { append("\n\n").append(fullSchema(it)) }
    }

    fun relevantTypes(userText: String): Set<GeneratedBlockType> {
        val value = userText.lowercase()
        return buildSet {
            if (listOf("quiz", "question", "interactive", "form", "questionnaire", "calculator", "snippet", "inside chat").any(value::contains)) {
                add(GeneratedBlockType.CHAT_UI)
            }
            if (listOf("widget", "home screen", "launcher", "live update", "background refresh").any(value::contains)) {
                add(GeneratedBlockType.HOME_WIDGET)
            }
            if (listOf("chart", "plot", "graph of", "visualize data", "grafik").any(value::contains)) add(GeneratedBlockType.CHART)
            if (listOf("diagram", "mermaid", "flowchart", "sequence diagram", "architecture graph").any(value::contains)) add(GeneratedBlockType.DIAGRAM)
        }
    }

    fun fullSchema(type: GeneratedBlockType): String = when (type) {
        GeneratedBlockType.CHAT_UI -> snippetSchema()
        GeneratedBlockType.HOME_WIDGET -> widgetSchema()
        GeneratedBlockType.CHART -> chartSchema()
        GeneratedBlockType.DIAGRAM -> diagramSchema()
    }

    fun validate(type: GeneratedBlockType, source: String): GeneratedValidationResult {
        val max = fences.first { it.type == type }.maxSourceChars
        if (source.isBlank()) return error("syntax", "/", "Block source is empty")
        if (source.length > max) return error("limits", "/", "Source exceeds $max characters")
        return when (type) {
            GeneratedBlockType.CHAT_UI -> validateProgram(source, ArborProgramSurface.SNIPPET)
            GeneratedBlockType.HOME_WIDGET -> validateProgram(source, ArborProgramSurface.WIDGET)
            GeneratedBlockType.CHART -> validateChart(source)
            GeneratedBlockType.DIAGRAM -> validateDiagram(source)
        }
    }

    fun extractSingleReplacement(raw: String, expectedFence: String): Result<String> = runCatching {
        val match = Regex("\\A```([A-Za-z0-9_-]+)[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n```\\z").matchEntire(raw.trim())
            ?: error("Repair output must contain exactly one fenced block and no prose")
        require(match.groupValues[1].equals(expectedFence, ignoreCase = true)) {
            "Repair returned `${match.groupValues[1]}` instead of `$expectedFence`"
        }
        match.groupValues[2].also { require("```" !in it) { "Repair output contains multiple fenced blocks" } }
    }

    val validExamples: Map<GeneratedBlockType, List<String>> by lazy {
        mapOf(
            GeneratedBlockType.CHAT_UI to listOf(
                """{"schema":"arbor-snippet/1","id":"quick_quiz","title":"Quick quiz","state":{"answer":"","checked":false},"ui":{"type":"column","children":[{"type":"text","text":"Which number is prime?","style":{"emphasis":"strong"}},{"type":"choice","id":"answer_choice","value":"answer","options":[{"label":"9","value":"9"},{"label":"11","value":"11"},{"label":"15","value":"15"}]},{"type":"button","label":"Check","action":"check"},{"type":"text","text":"{{answer == 11}}","visibleWhen":"checked == true"}]},"actions":{"check":[{"op":"set","target":"checked","value":true},{"op":"submit","message":"Quiz answer: {{answer}}"}]}}""",
            ),
            GeneratedBlockType.HOME_WIDGET to listOf(
                """{"schema":"arbor-widget/1","id":"counter","title":"Counter","state":{"count":0},"ui":{"type":"column","children":[{"type":"metric","label":"Count","value":"{{count}}"},{"type":"button","label":"Add one","action":"increment"}]},"actions":{"increment":[{"op":"add","target":"count","value":1}]},"capabilities":[],"dataSources":[]}""",
                """{"schema":"arbor-widget/1","id":"weather","title":"Weather","description":"Live temperature","state":{"latitude":0,"longitude":0,"temperature":"—"},"ui":{"type":"column","children":[{"type":"metric","label":"Temperature","value":"{{temperature}} °C"},{"type":"button","label":"Refresh","action":"refresh_weather"}]},"actions":{"refresh_weather":[{"op":"refresh","source":"weather"}]},"capabilities":[{"type":"location","accuracy":"approximate","reason":"Use the device area for local weather."},{"type":"network","origins":["https://api.open-meteo.com"],"reason":"Download current weather from Open-Meteo."},{"type":"background_refresh","reason":"Keep the launcher value current."}],"dataSources":[{"id":"location","type":"location","bindings":[{"state":"latitude","path":"latitude"},{"state":"longitude","path":"longitude"}]},{"id":"weather","type":"http_json","url":"https://api.open-meteo.com/v1/forecast?latitude={{latitude}}&longitude={{longitude}}&current=temperature_2m","bindings":[{"state":"temperature","path":"current.temperature_2m","fallback":"—"}]}],"refreshMinutes":30}""",
            ),
            GeneratedBlockType.CHART to listOf(
                """{"type":"bar","title":"Example","series":[{"name":"Value","values":[{"label":"A","value":1},{"label":"B","value":2}]}]}""",
                """{"type":"line","title":"Trend","series":[{"name":"Rate","values":[{"label":"Jan","value":4.5},{"label":"Feb","value":6.0}]}]}""",
            ),
            GeneratedBlockType.DIAGRAM to listOf(
                "flowchart TD\n  A[Start] --> B[Done]",
                "sequenceDiagram\n  participant U as User\n  U->>A: Request\n  A-->>U: Response",
            ),
        )
    }

    private fun validateProgram(source: String, surface: ArborProgramSurface): GeneratedValidationResult {
        val parsed = ArborProgramParser.parse(source, surface)
        return parsed.exceptionOrNull()?.let { error("schema", "/", it.message ?: "Program validation failed") }
            ?: GeneratedValidationResult.Valid
    }

    private fun validateChart(source: String): GeneratedValidationResult {
        val root = parseObject(source) ?: return error("syntax", "/", "arbor-chart requires one JSON object")
        val errors = mutableListOf<GeneratedValidationError>()
        rejectUnknown(root, setOf("type", "title", "series"), "/", errors)
        val type = root["type"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
        if (type !in chartTypes) errors += GeneratedValidationError("schema", "/type", "Unsupported chart type: $type")
        val series = root["series"] as? JsonArray
        if (series == null || series.isEmpty()) errors += GeneratedValidationError("schema", "/series", "At least one series is required")
        if ((series?.size ?: 0) > 8) errors += GeneratedValidationError("limits", "/series", "At most 8 series are supported")
        series.orEmpty().forEachIndexed { seriesIndex, element ->
            val item = element as? JsonObject
            if (item == null) {
                errors += GeneratedValidationError("schema", "/series/$seriesIndex", "Series must be an object")
            } else {
                rejectUnknown(item, setOf("name", "values"), "/series/$seriesIndex", errors)
                val values = item["values"] as? JsonArray
                if (values == null || values.isEmpty()) errors += GeneratedValidationError("semantic", "/series/$seriesIndex/values", "Series values cannot be empty")
                if ((values?.size ?: 0) > 80) errors += GeneratedValidationError("limits", "/series/$seriesIndex/values", "At most 80 points are supported")
                values.orEmpty().forEachIndexed { pointIndex, point ->
                    val value = point as? JsonObject
                    if (value == null) errors += GeneratedValidationError("schema", "/series/$seriesIndex/values/$pointIndex", "Point must be an object")
                    else {
                        rejectUnknown(value, setOf("label", "value"), "/series/$seriesIndex/values/$pointIndex", errors)
                        if (value["value"]?.jsonPrimitive?.doubleOrNull?.isFinite() != true) errors += GeneratedValidationError("schema", "/series/$seriesIndex/values/$pointIndex/value", "A finite number is required")
                    }
                }
            }
        }
        return GeneratedValidationResult(errors)
    }

    private fun validateDiagram(source: String): GeneratedValidationResult {
        if (Regex("(?is)<script|javascript:|<iframe|<html").containsMatchIn(source)) return error("security", "/", "HTML and JavaScript are forbidden")
        val first = source.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val validHeader = Regex("(?i)^(?:flowchart|graph)\\s+(?:TB|TD|BT|LR|RL)\\b").containsMatchIn(first) ||
            first.equals("sequenceDiagram", true) || Regex("(?i)^(?:di)?graph\\b.*\\{").containsMatchIn(first)
        if (!validHeader) return error("syntax", "/1", "Supported diagrams start with flowchart/graph direction, sequenceDiagram, graph {, or digraph {")
        val edge = Regex("[A-Za-z0-9_.-]+\\s*(?:-->|==>|---|-\\.->|--?>>?|->)").containsMatchIn(source)
        val node = Regex("[A-Za-z0-9_.-]+\\s*[\\[({]").containsMatchIn(source)
        if (!edge && !node) return error("semantic", "/", "Diagram contains no supported nodes or edges")
        if (source.lines().size > 240) return error("limits", "/", "Diagram exceeds 240 lines")
        return GeneratedValidationResult.Valid
    }

    private fun rejectUnknown(value: JsonObject, allowed: Set<String>, path: String, errors: MutableList<GeneratedValidationError>) {
        (value.keys - allowed).sorted().forEach { field -> errors += GeneratedValidationError("schema", "$path/$field".replace("//", "/"), "Unsupported field") }
    }

    private fun parseObject(source: String): JsonObject? = runCatching { Json.parseToJsonElement(source) as? JsonObject }.getOrNull()
    private fun error(phase: String, path: String, message: String) = GeneratedValidationResult(listOf(GeneratedValidationError(phase, path, message)))

    private fun snippetSchema() = """
        `arbor-snippet` schema — $CONTRACT_VERSION
        Root: schema=`arbor-snippet/1`; id optional; title/description optional; state object; ui node required; actions object optional. Capabilities, dataSources, and refreshMinutes are forbidden.
        UI nodes: ${programNodeTypes.sorted().joinToString()}. Compose these nodes to build quizzes, simple questions, forms, calculators, checklists, trackers, and other in-chat interactions. Do not create a special root type for the use case.
        Every node has type and optional id, text, label, value, action, visibleWhen, options, items, children, min/max/step/decimals, and style. Style supports foreground/background theme tokens or hex colors, emphasis, align, padding, gap, cornerRadius, fontSize, and weight.
        Actions: ${programActionTypes.sorted().joinToString()}. Snippets normally use state actions and submit; refresh/open_app have no external capability and should be avoided. Safe expressions support state identifiers, numbers, + - * / % ^, parentheses, min/max/abs/round/pow.
        Exact example:
        ```arbor-snippet
        ${validExamples.getValue(GeneratedBlockType.CHAT_UI).first()}
        ```
    """.trimIndent()

    private fun widgetSchema() = """
        `arbor-widget` schema — $CONTRACT_VERSION
        Root: schema=`arbor-widget/1`; stable id required; title/description; state; ui; actions; capabilities; dataSources; optional refreshMinutes (15–1440).
        UI nodes and actions are the same bounded declarative program used by snippets: nodes=${programNodeTypes.sorted().joinToString()}; actions=${programActionTypes.sorted().joinToString()}. Home widgets cannot use input because launchers cannot host a keyboard. The launcher renders the component tree and exposes at most four visible button/toggle actions.
        Capabilities are explicit objects with type and reason. Supported: network (exact HTTPS origins), location (approximate|precise), folder (read|read_write, one user-selected tree), background_refresh. Data sources: http_json, location, folder_text. A data source is rejected unless its exact capability is declared; the user must grant it again for each pinned widget instance.
        http_json is GET-only, max 1 MB, no redirects, no credentials, no private/local IPs. Bindings copy bounded JSON paths into state. location binds latitude/longitude/accuracy/updatedAt. folder_text reads one relative file inside the selected tree and binds text/size/lineCount. Scheduled refresh requires background_refresh and is 15 minutes or slower.
        No HTML, JavaScript, WebView, code download, shell, reflection, arbitrary Android intents, hidden permissions, unrestricted network, or unrestricted file access.
        Exact local example:
        ```arbor-widget
        ${validExamples.getValue(GeneratedBlockType.HOME_WIDGET)[0]}
        ```
        Exact live example:
        ```arbor-widget
        ${validExamples.getValue(GeneratedBlockType.HOME_WIDGET)[1]}
        ```
    """.trimIndent()

    private fun chartSchema() = """
        `arbor-chart` schema — $CONTRACT_VERSION
        Root fields: type (required: ${chartTypes.sorted().joinToString()}), title optional, series required (1–8). Each series: name optional, values required (1–80). Each point: label and finite numeric value. No arbitrary plotting-library fields or executable formatters.
        Exact examples:
        ```arbor-chart
        ${validExamples.getValue(GeneratedBlockType.CHART)[0]}
        ```
        ```arbor-chart
        ${validExamples.getValue(GeneratedBlockType.CHART)[1]}
        ```
    """.trimIndent()

    private fun diagramSchema() = """
        `mermaid`/`dot` native subset — $CONTRACT_VERSION
        Supported: flowchart/graph with TB, TD, BT, LR, RL; -->, ==>, ---, -.-> edges, labels and chained edges; sequenceDiagram participant/actor and message arrows; basic graph/digraph DOT edges, labels and rankdir. Max 48,000 chars and 240 lines. No Mermaid HTML labels, click directives, scripts, styles requiring browser execution, or unsupported diagram families.
        Exact examples:
        ```mermaid
        ${validExamples.getValue(GeneratedBlockType.DIAGRAM)[0]}
        ```
        ```mermaid
        ${validExamples.getValue(GeneratedBlockType.DIAGRAM)[1]}
        ```
    """.trimIndent()
}
