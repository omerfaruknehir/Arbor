package app.arbor.chat.generated

import app.arbor.chat.widgets.ArborMiniAppParser
import app.arbor.chat.widgets.ArborWidgetParser
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
    const val CONTRACT_FAMILY = "arbor-generated-content/1"
    const val VALIDATOR_VERSION = "1.0.0"
    val chartTypes = setOf("bar", "line", "area", "scatter", "pie", "donut")
    val miniAppComponentTypes: Set<String> get() = ArborMiniAppParser.supportedComponentTypes
    val miniAppActionTypes: Set<String> get() = ArborMiniAppParser.supportedOperations
    val widgetTypes: Set<String> get() = ArborWidgetParser.supportedTypes
    val widgetFieldTypes: Set<String> get() = ArborWidgetParser.supportedFields

    val fences = listOf(
        GeneratedFenceCapability(GeneratedBlockType.CHAT_UI, "arbor-ui", setOf("arbor-ui", "ui", "arbor-form"), 48_000),
        GeneratedFenceCapability(GeneratedBlockType.HOME_WIDGET, "arbor-widget", setOf("arbor-widget", "widget"), 48_000),
        GeneratedFenceCapability(GeneratedBlockType.CHART, "arbor-chart", setOf("arbor-chart", "chart", "bar-chart", "barchart", "line-chart", "pie-chart"), 48_000),
        GeneratedFenceCapability(GeneratedBlockType.DIAGRAM, "mermaid", setOf("mermaid", "graph", "diagram", "dot", "graphviz"), 48_000),
    )
    val fenceNames: Set<String> = fences.flatMapTo(linkedSetOf()) { it.aliases }
    val CONTRACT_VERSION: String by lazy { contractVersionForShape(contractShape()) }

    fun contractShape(): String = buildString {
        append(VALIDATOR_VERSION).append('|')
        fences.sortedBy { it.canonicalFence }.forEach { append(it.canonicalFence).append(':').append(it.aliases.sorted()).append(':').append(it.maxSourceChars).append('|') }
        append("widgets=").append(widgetTypes.sorted()).append('|')
        append("fields=").append(widgetFieldTypes.sorted()).append('|')
        append("components=").append(miniAppComponentTypes.sorted()).append('|')
        append("actions=").append(miniAppActionTypes.sorted()).append('|')
        append("charts=").append(chartTypes.sorted()).append('|')
        append("limits=screens8,components32,state48,items24,buttons16,actions8,series8,points80,diagramLines240")
        append("|security=no-html-js-jsx-webview-executable-ui;home-explicit")
    }

    fun contractVersionForShape(shape: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(shape.toByteArray()).joinToString("") { "%02x".format(it) }
        return "$CONTRACT_FAMILY-${digest.take(8)}"
    }

    fun capability(language: String): GeneratedFenceCapability? = fences.firstOrNull { language.lowercase() in it.aliases }

    fun compactSummary(): String = """
        Arbor generated-content contract: $CONTRACT_VERSION; validator $VALIDATOR_VERSION.
        Validated native fences: ${fences.joinToString { "`${it.canonicalFence}` (${it.aliases.joinToString()})" }}.
        `arbor-ui` is chat-only native interaction. `arbor-widget` is Home-screen eligible only with surface `home` or `both`; eligibility otherwise remains false. `arbor-chart` uses Arbor's JSON series schema. `mermaid`/`dot` use Arbor's bounded native diagram subset. Ordinary Markdown tables remain Markdown, not mini-apps.
        Generated blocks are parsed, schema/semantic/security validated, and renderer-prepared before display. Never invent fields or component/action types. Never emit HTML, JavaScript, JSX, WebView content, executable generated UI, or an HTML/JS fallback.
    """.trimIndent()

    fun promptForRequest(userText: String): String = buildString {
        append(compactSummary())
        relevantTypes(userText).forEach { append("\n\n").append(fullSchema(it)) }
    }

    fun relevantTypes(userText: String): Set<GeneratedBlockType> {
        val value = userText.lowercase()
        return buildSet {
            if (listOf("widget", "mini-app", "mini app", "interactive", "form", "questionnaire", "calculator", "home screen").any(value::contains)) {
                add(GeneratedBlockType.CHAT_UI)
                if ("widget" in value || "home screen" in value || "launcher" in value) add(GeneratedBlockType.HOME_WIDGET)
            }
            if (listOf("chart", "plot", "graph of", "visualize data", "grafik").any(value::contains)) add(GeneratedBlockType.CHART)
            if (listOf("diagram", "mermaid", "flowchart", "sequence diagram", "architecture graph").any(value::contains)) add(GeneratedBlockType.DIAGRAM)
        }
    }

    fun fullSchema(type: GeneratedBlockType): String = when (type) {
        GeneratedBlockType.CHAT_UI, GeneratedBlockType.HOME_WIDGET -> widgetSchema(type)
        GeneratedBlockType.CHART -> chartSchema()
        GeneratedBlockType.DIAGRAM -> diagramSchema()
    }

    fun validate(type: GeneratedBlockType, source: String): GeneratedValidationResult {
        val max = fences.first { it.type == type }.maxSourceChars
        if (source.isBlank()) return error("syntax", "/", "Block source is empty")
        if (source.length > max) return error("limits", "/", "Source exceeds $max characters")
        return when (type) {
            GeneratedBlockType.CHAT_UI, GeneratedBlockType.HOME_WIDGET -> validateWidget(type, source)
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
                """{"type":"choice","title":"Choose","options":["A","B"]}""",
                """{"type":"mini_app","title":"Counter","state":{"count":0},"screens":[{"id":"main","components":[{"type":"metric","id":"count","label":"Count","value":"{{count}}"},{"type":"buttons","id":"actions","buttons":[{"label":"Add","style":"primary","actions":[{"operation":"add","target":"count","value":1}]}]}]}]}""",
            ),
            GeneratedBlockType.HOME_WIDGET to listOf(
                """{"type":"counter","title":"Counter","surface":"home","value":0,"actions":[{"label":"+1","target":"value","operation":"add","value":1}]}""",
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

    private fun validateWidget(type: GeneratedBlockType, source: String): GeneratedValidationResult {
        val root = parseObject(source) ?: return error("syntax", "/", "Expected one JSON object")
        val errors = mutableListOf<GeneratedValidationError>()
        rejectUnknown(root, WIDGET_ROOT_FIELDS, "/", errors)
        validateWidgetUnknownFields(root, errors)
        if (type == GeneratedBlockType.CHAT_UI && root["surface"]?.jsonPrimitive?.contentOrNull in setOf("home", "both")) {
            errors += GeneratedValidationError("security", "/surface", "arbor-ui is chat-only")
        }
        val parsed = ArborWidgetParser.parse(source)
        parsed.exceptionOrNull()?.let {
            errors += GeneratedValidationError("schema", "/", it.message ?: "Widget validation failed")
        }
        if (type == GeneratedBlockType.HOME_WIDGET && parsed.getOrNull()?.homeEnabled != true) {
            errors += GeneratedValidationError("semantic", "/surface", "arbor-widget requires surface home or both")
        }
        return GeneratedValidationResult(errors.distinct())
    }

    private fun validateWidgetUnknownFields(root: JsonObject, errors: MutableList<GeneratedValidationError>) {
        if (root["type"]?.jsonPrimitive?.contentOrNull?.lowercase() == "mini_app") validateMiniAppUnknownFields(root, errors)
        fun arrays(name: String, allowed: Set<String>) {
            (root[name] as? JsonArray).orEmpty().forEachIndexed { index, element ->
                (element as? JsonObject)?.let { rejectUnknown(it, allowed, "/$name/$index", errors) }
            }
        }
        arrays("fields", FIELD_FIELDS)
        arrays("outputs", OUTPUT_FIELDS)
        arrays("actions", LEGACY_ACTION_FIELDS)
        arrays("items", SCHEDULE_FIELDS)
        arrays("events", SCHEDULE_FIELDS)
        (root["dataSource"] as? JsonObject)?.let { data ->
            rejectUnknown(data, DATA_SOURCE_FIELDS, "/dataSource", errors)
            (data["bindings"] as? JsonArray).orEmpty().forEachIndexed { index, element ->
                (element as? JsonObject)?.let { rejectUnknown(it, BINDING_FIELDS, "/dataSource/bindings/$index", errors) }
            }
        }
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

    private fun validateMiniAppUnknownFields(root: JsonObject, errors: MutableList<GeneratedValidationError>) {
        val screens = root["screens"] as? JsonArray ?: return
        screens.forEachIndexed { screenIndex, screenValue ->
            val screen = screenValue as? JsonObject ?: return@forEachIndexed
            rejectUnknown(screen, setOf("id", "title", "components"), "/screens/$screenIndex", errors)
            (screen["components"] as? JsonArray).orEmpty().forEachIndexed { componentIndex, componentValue ->
                val component = componentValue as? JsonObject ?: return@forEachIndexed
                rejectUnknown(component, MINI_COMPONENT_FIELDS, "/screens/$screenIndex/components/$componentIndex", errors)
                listOf("buttons", "items").forEach { collection ->
                    (component[collection] as? JsonArray).orEmpty().forEachIndexed { itemIndex, itemValue ->
                        val item = itemValue as? JsonObject ?: return@forEachIndexed
                        rejectUnknown(item, if (collection == "buttons") BUTTON_FIELDS else ITEM_FIELDS, "/screens/$screenIndex/components/$componentIndex/$collection/$itemIndex", errors)
                        (item["actions"] as? JsonArray).orEmpty().forEachIndexed { actionIndex, actionValue ->
                            (actionValue as? JsonObject)?.let { rejectUnknown(it, ACTION_FIELDS, "/screens/$screenIndex/components/$componentIndex/$collection/$itemIndex/actions/$actionIndex", errors) }
                        }
                    }
                }
            }
        }
    }

    private fun rejectUnknown(value: JsonObject, allowed: Set<String>, path: String, errors: MutableList<GeneratedValidationError>) {
        (value.keys - allowed).sorted().forEach { field -> errors += GeneratedValidationError("schema", "$path/$field".replace("//", "/"), "Unsupported field") }
    }

    private fun parseObject(source: String): JsonObject? = runCatching { Json.parseToJsonElement(source) as? JsonObject }.getOrNull()
    private fun error(phase: String, path: String, message: String) = GeneratedValidationResult(listOf(GeneratedValidationError(phase, path, message)))

    private fun widgetSchema(type: GeneratedBlockType) = """
        ${if (type == GeneratedBlockType.CHAT_UI) "`arbor-ui` chat-native schema" else "`arbor-widget` Home-screen schema"} — $CONTRACT_VERSION
        Root `type`: ${widgetTypes.sorted().joinToString()}. Field kinds: ${widgetFieldTypes.sorted().joinToString()}.
        `mini_app` components (max 8 screens, 32 components/screen): ${miniAppComponentTypes.sorted().joinToString()}.
        Actions (max 8/control): ${miniAppActionTypes.sorted().joinToString()}. State max 48 values; options/items max 24; buttons max 16; strings and expressions are bounded by the validator.
        Safe expressions: numbers, state identifiers, + - * / % ^, parentheses, min/max/abs/round/pow. No code execution.
        Home eligibility exists only in `arbor-widget` with `"surface":"home"` or `"surface":"both"`; default is false. Live data is read-only public HTTPS JSON with explicit bounded bindings. No credentials, private hosts, mutation, HTML, JS, or Android permissions.
        Exact examples:
        ```${if (type == GeneratedBlockType.CHAT_UI) "arbor-ui" else "arbor-widget"}
        ${validExamples.getValue(type).first()}
        ```
        ```arbor-ui
        ${validExamples.getValue(GeneratedBlockType.CHAT_UI).last()}
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

    private val WIDGET_ROOT_FIELDS = setOf("type", "title", "description", "surface", "home", "options", "fields", "outputs", "actions", "min", "max", "step", "value", "from", "to", "rate", "symbol", "dataSource", "items", "events", "timezone", "state", "screens")
    private val MINI_COMPONENT_FIELDS = setOf("type", "id", "label", "text", "value", "expression", "visibleWhen", "placeholder", "min", "max", "step", "decimals", "prefix", "suffix", "options", "items", "buttons")
    private val BUTTON_FIELDS = setOf("label", "style", "visibleWhen", "actions", "action")
    private val ITEM_FIELDS = setOf("label", "value", "detail", "visibleWhen", "actions", "action")
    private val ACTION_FIELDS = setOf("operation", "target", "value", "expression", "screen", "message", "condition")
    private val FIELD_FIELDS = setOf("id", "label", "kind", "value", "min", "max", "step", "options", "prefix", "suffix")
    private val OUTPUT_FIELDS = setOf("label", "expression", "decimals", "prefix", "suffix")
    private val LEGACY_ACTION_FIELDS = setOf("label", "target", "operation", "value")
    private val SCHEDULE_FIELDS = setOf("id", "label", "time", "detail")
    private val DATA_SOURCE_FIELDS = setOf("url", "refreshMinutes", "bindings")
    private val BINDING_FIELDS = setOf("id", "label", "path", "decimals", "prefix", "suffix")
}
