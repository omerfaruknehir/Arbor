package app.arbor.chat.widgets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Arbor has two generated-program surfaces with deliberately separate envelopes:
 *
 *  - arbor-snippet/1: interactive content which lives only inside a chat message.
 *  - arbor-widget/1: an installable Android home-screen program with explicit grants.
 *
 * Both use the same bounded declarative UI and action language. They do not execute
 * JavaScript, bytecode, shell commands, reflection, WebViews, or downloaded code.
 */
enum class ArborProgramSurface { SNIPPET, WIDGET }

data class ArborProgramDefinition(
    val surface: ArborProgramSurface,
    val schema: String,
    val id: String,
    val title: String,
    val description: String,
    val state: Map<String, String>,
    val ui: ArborProgramNode,
    val actions: Map<String, List<ArborProgramAction>>,
    val capabilities: List<ArborWidgetCapabilityRequest>,
    val dataSources: List<ArborWidgetDataSource>,
    val refreshMinutes: Long?,
)

data class ArborProgramNode(
    val type: String,
    val id: String,
    val text: String = "",
    val label: String = "",
    val value: String = "",
    val action: String = "",
    val visibleWhen: String = "",
    val options: List<ArborProgramOption> = emptyList(),
    val items: List<ArborProgramItem> = emptyList(),
    val children: List<ArborProgramNode> = emptyList(),
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val decimals: Int = 2,
    val style: ArborProgramStyle = ArborProgramStyle(),
)

data class ArborProgramOption(
    val label: String,
    val value: String,
    val action: String = "",
)

data class ArborProgramItem(
    val label: String,
    val value: String = "",
    val detail: String = "",
    val action: String = "",
)

data class ArborProgramStyle(
    val foreground: String = "",
    val background: String = "",
    val emphasis: String = "normal",
    val align: String = "start",
    val padding: Int = 0,
    val gap: Int = 8,
    val cornerRadius: Int = 12,
    val fontSize: Int = 0,
    val weight: Float = 0f,
)

data class ArborProgramAction(
    val op: String,
    val target: String = "",
    val value: String = "",
    val expression: String = "",
    val message: String = "",
    val source: String = "",
    val route: String = "",
    val condition: String = "",
)

data class ArborWidgetCapabilityRequest(
    val type: String,
    val reason: String,
    val origins: List<String> = emptyList(),
    val accuracy: String = "approximate",
    val mode: String = "read",
)

data class ArborWidgetDataSource(
    val id: String,
    val type: String,
    val url: String = "",
    val relativePath: String = "",
    val bindings: List<ArborWidgetBinding>,
)

data class ArborWidgetBinding(
    val state: String,
    val path: String = "",
    val fallback: String = "",
)

data class ArborFolderWriteRequest(
    val source: String,
    val content: String,
)

data class ArborProgramTransition(
    val state: Map<String, String>,
    val submitMessage: String? = null,
    val refreshSources: Set<String> = emptySet(),
    val folderWrites: List<ArborFolderWriteRequest> = emptyList(),
    val openRoute: String? = null,
)

object ArborProgramParser {
    const val SNIPPET_SCHEMA = "arbor-snippet/1"
    const val WIDGET_SCHEMA = "arbor-widget/1"

    val nodeTypes: Set<String> = setOf(
        "column", "row", "stack", "text", "metric", "button", "toggle", "choice",
        "input", "slider", "progress", "list", "chart", "divider", "spacer",
    )
    val actionOps: Set<String> = setOf(
        "set", "add", "multiply", "toggle", "append", "backspace", "evaluate", "reset",
        "submit", "refresh", "write_folder", "open_app",
    )
    val capabilityTypes: Set<String> = setOf("network", "location", "folder", "background_refresh")
    val dataSourceTypes: Set<String> = setOf("http_json", "location", "folder_text")

    private val json = Json { ignoreUnknownKeys = false }

    fun parse(source: String, expected: ArborProgramSurface): Result<ArborProgramDefinition> = runCatching {
        require(source.length <= MAX_SOURCE_CHARS) { "Program definition is too large" }
        val root = json.parseToJsonElement(source).jsonObject
        rejectUnknown(root, ROOT_FIELDS, "/")
        val schema = root.string("schema")
        val requiredSchema = if (expected == ArborProgramSurface.SNIPPET) SNIPPET_SCHEMA else WIDGET_SCHEMA
        require(schema == requiredSchema) { "Expected schema $requiredSchema" }
        val rawId = root.string("id")
        val id = when (expected) {
            ArborProgramSurface.SNIPPET -> rawId.takeIf(String::isNotBlank)?.let(::requireId) ?: "snippet"
            ArborProgramSurface.WIDGET -> requireId(rawId).also { require(it.isNotBlank()) { "Widget id is required" } }
        }
        val title = root.string("title").take(120).ifBlank { if (expected == ArborProgramSurface.WIDGET) "Arbor widget" else "Interactive snippet" }
        val stateObject = root.objectOrNull("state") ?: JsonObject(emptyMap())
        require(stateObject.size <= MAX_STATE_VALUES) { "At most $MAX_STATE_VALUES state values are allowed" }
        val state = stateObject.entries.associate { (key, value) ->
            requireId(key) to primitiveState(value).take(MAX_STATE_VALUE_CHARS)
        }
        require(state.size == stateObject.size) { "State keys must remain unique" }

        var nodeCount = 0
        fun parseNode(raw: JsonObject, path: String, depth: Int): ArborProgramNode {
            require(depth <= MAX_NODE_DEPTH) { "$path exceeds maximum nesting depth" }
            rejectUnknown(raw, NODE_FIELDS, path)
            nodeCount += 1
            require(nodeCount <= MAX_NODES) { "At most $MAX_NODES UI nodes are allowed" }
            val type = raw.string("type").lowercase()
            require(type in nodeTypes) { "$path has unsupported node type: $type" }
            val nodeId = raw.string("id").takeIf(String::isNotBlank)?.let(::requireId) ?: "node_$nodeCount"
            val childrenRaw = raw.arrayObjects("children")
            require(childrenRaw.size <= MAX_CHILDREN) { "$path has too many children" }
            val optionsRaw = raw.arrayObjects("options")
            require(optionsRaw.size <= MAX_OPTIONS) { "$path has too many options" }
            val itemsRaw = raw.arrayObjects("items")
            require(itemsRaw.size <= MAX_ITEMS) { "$path has too many items" }
            val min = raw.number("min", 0.0)
            val max = raw.number("max", 100.0).coerceAtLeast(min)
            val style = raw.objectOrNull("style")?.let { parseStyle(it, "$path/style") } ?: ArborProgramStyle()
            return ArborProgramNode(
                type = type,
                id = nodeId,
                text = raw.string("text").take(MAX_TEXT_CHARS),
                label = raw.string("label").take(160),
                value = raw.valueString("value").take(MAX_STATE_VALUE_CHARS),
                action = raw.string("action").take(80),
                visibleWhen = raw.string("visibleWhen").take(MAX_EXPRESSION_CHARS),
                options = optionsRaw.mapIndexed { index, option ->
                    rejectUnknown(option, OPTION_FIELDS, "$path/options/$index")
                    ArborProgramOption(
                        label = option.string("label").take(120),
                        value = option.valueString("value").take(MAX_STATE_VALUE_CHARS),
                        action = option.string("action").take(80),
                    )
                },
                items = itemsRaw.mapIndexed { index, item ->
                    rejectUnknown(item, ITEM_FIELDS, "$path/items/$index")
                    ArborProgramItem(
                        label = item.string("label").take(160),
                        value = item.valueString("value").take(MAX_STATE_VALUE_CHARS),
                        detail = item.string("detail").take(300),
                        action = item.string("action").take(80),
                    )
                },
                children = childrenRaw.mapIndexed { index, child -> parseNode(child, "$path/children/$index", depth + 1) },
                min = min,
                max = max,
                step = raw.number("step", 1.0).coerceAtLeast(0.000001),
                decimals = raw.number("decimals", 2.0).toInt().coerceIn(0, 8),
                style = style,
            ).also { node -> validateNode(node, expected, path) }
        }

        val uiObject = requireNotNull(root.objectOrNull("ui")) { "Program ui object is required" }
        val ui = parseNode(uiObject, "/ui", 0)
        val actions = parseActions(root.objectOrNull("actions") ?: JsonObject(emptyMap()))
        validateActionReferences(ui, actions)

        val capabilities = root.arrayObjects("capabilities").mapIndexed { index, value ->
            require(expected == ArborProgramSurface.WIDGET) { "Snippets cannot request Android capabilities" }
            parseCapability(value, "/capabilities/$index")
        }
        require(capabilities.size <= MAX_CAPABILITIES) { "At most $MAX_CAPABILITIES capabilities are allowed" }
        require(capabilities.map { capabilityKey(it) }.distinct().size == capabilities.size) { "Duplicate capability requests are not allowed" }

        val dataSources = root.arrayObjects("dataSources").mapIndexed { index, value ->
            require(expected == ArborProgramSurface.WIDGET) { "Snippets cannot use background data sources" }
            parseDataSource(value, "/dataSources/$index")
        }
        require(dataSources.size <= MAX_DATA_SOURCES) { "At most $MAX_DATA_SOURCES data sources are allowed" }
        require(dataSources.map { it.id }.distinct().size == dataSources.size) { "Data-source ids must be unique" }

        val refreshMinutes = root.numberOrNull("refreshMinutes")?.toLong()?.coerceIn(MIN_REFRESH_MINUTES, MAX_REFRESH_MINUTES)
        if (expected == ArborProgramSurface.SNIPPET) {
            require(capabilities.isEmpty() && dataSources.isEmpty() && refreshMinutes == null) {
                "Snippets are chat-local and cannot declare widget capabilities or background refresh"
            }
            require(actions.values.flatten().none { it.op in EXTERNAL_ACTIONS }) {
                "Snippets cannot refresh widget data, write folders, or open the app"
            }
        } else {
            validateWidgetCapabilities(capabilities, dataSources, refreshMinutes)
            validateWidgetActions(actions, capabilities, dataSources)
        }

        ArborProgramDefinition(
            surface = expected,
            schema = schema,
            id = id,
            title = title,
            description = root.string("description").take(500),
            state = state,
            ui = ui,
            actions = actions,
            capabilities = capabilities,
            dataSources = dataSources,
            refreshMinutes = refreshMinutes,
        )
    }

    private fun parseStyle(raw: JsonObject, path: String): ArborProgramStyle {
        rejectUnknown(raw, STYLE_FIELDS, path)
        val emphasis = raw.string("emphasis").lowercase().ifBlank { "normal" }
        require(emphasis in setOf("normal", "medium", "strong")) { "$path/emphasis is invalid" }
        val align = raw.string("align").lowercase().ifBlank { "start" }
        require(align in setOf("start", "center", "end")) { "$path/align is invalid" }
        return ArborProgramStyle(
            foreground = validateColor(raw.string("foreground"), "$path/foreground"),
            background = validateColor(raw.string("background"), "$path/background"),
            emphasis = emphasis,
            align = align,
            padding = raw.number("padding", 0.0).toInt().coerceIn(0, 32),
            gap = raw.number("gap", 8.0).toInt().coerceIn(0, 32),
            cornerRadius = raw.number("cornerRadius", 12.0).toInt().coerceIn(0, 32),
            fontSize = raw.number("fontSize", 0.0).toInt().coerceIn(0, 48),
            weight = raw.number("weight", 0.0).toFloat().coerceIn(0f, 10f),
        )
    }

    private fun parseActions(raw: JsonObject): Map<String, List<ArborProgramAction>> {
        require(raw.size <= MAX_ACTION_GROUPS) { "At most $MAX_ACTION_GROUPS action groups are allowed" }
        return raw.entries.associate { (rawId, element) ->
            val id = requireId(rawId)
            val array = element as? JsonArray ?: error("/actions/$id must be an array")
            require(array.size <= MAX_ACTIONS_PER_GROUP) { "$id has too many operations" }
            id to array.mapIndexed { index, item ->
                val value = item as? JsonObject ?: error("/actions/$id/$index must be an object")
                rejectUnknown(value, ACTION_FIELDS, "/actions/$id/$index")
                val op = value.string("op").lowercase()
                require(op in actionOps) { "Unsupported action operation: $op" }
                val target = value.string("target").takeIf(String::isNotBlank)?.let(::requireId).orEmpty()
                if (op in STATE_ACTIONS) require(target.isNotBlank()) { "$op requires a target" }
                ArborProgramAction(
                    op = op,
                    target = target,
                    value = value.valueString("value").take(MAX_STATE_VALUE_CHARS),
                    expression = value.string("expression").take(MAX_EXPRESSION_CHARS),
                    message = value.string("message").take(MAX_TEXT_CHARS),
                    source = value.string("source").take(80),
                    route = value.string("route").take(200),
                    condition = value.string("condition").take(MAX_EXPRESSION_CHARS),
                )
            }
        }
    }

    private fun parseCapability(raw: JsonObject, path: String): ArborWidgetCapabilityRequest {
        rejectUnknown(raw, CAPABILITY_FIELDS, path)
        val type = raw.string("type").lowercase()
        require(type in capabilityTypes) { "$path has unsupported capability: $type" }
        val reason = raw.string("reason").take(240)
        require(reason.isNotBlank()) { "$path/reason is required" }
        val origins = raw.stringList("origins").map { normalizeOrigin(it) }.distinct()
        val accuracy = raw.string("accuracy").lowercase().ifBlank { "approximate" }
        val mode = raw.string("mode").lowercase().ifBlank { "read" }
        when (type) {
            "network" -> require(origins.isNotEmpty() && origins.size <= MAX_NETWORK_ORIGINS) { "Network capability needs 1-$MAX_NETWORK_ORIGINS HTTPS origins" }
            "location" -> require(accuracy in setOf("approximate", "precise")) { "Location accuracy must be approximate or precise" }
            "folder" -> require(mode in setOf("read", "read_write")) { "Folder mode must be read or read_write" }
            "background_refresh" -> Unit
        }
        return ArborWidgetCapabilityRequest(type, reason, origins, accuracy, mode)
    }

    private fun parseDataSource(raw: JsonObject, path: String): ArborWidgetDataSource {
        rejectUnknown(raw, DATA_SOURCE_FIELDS, path)
        val id = requireId(raw.string("id"))
        val type = raw.string("type").lowercase()
        require(type in dataSourceTypes) { "$path has unsupported data-source type: $type" }
        val bindings = raw.arrayObjects("bindings").mapIndexed { index, binding ->
            rejectUnknown(binding, BINDING_FIELDS, "$path/bindings/$index")
            ArborWidgetBinding(
                state = requireId(binding.string("state")),
                path = binding.string("path").removePrefix("$.").take(300),
                fallback = binding.valueString("fallback").take(MAX_STATE_VALUE_CHARS),
            )
        }
        require(bindings.isNotEmpty() && bindings.size <= MAX_BINDINGS) { "$path needs 1-$MAX_BINDINGS bindings" }
        return ArborWidgetDataSource(
            id = id,
            type = type,
            url = raw.string("url").take(2_048),
            relativePath = raw.string("relativePath").take(240),
            bindings = bindings,
        ).also { source ->
            when (source.type) {
                "http_json" -> {
                    require(source.url.startsWith("https://")) { "$path/url must use HTTPS" }
                    source.bindings.forEach { require(it.path.isNotBlank() && it.path.matches(JSON_PATH)) { "$path has an invalid JSON binding path" } }
                }
                "location" -> source.bindings.forEach { require(it.path in LOCATION_PATHS) { "$path location binding path is invalid" } }
                "folder_text" -> require(source.relativePath.isNotBlank() && !source.relativePath.startsWith('/') && ".." !in source.relativePath.split('/')) {
                    "$path/relativePath must stay inside the granted folder"
                }
            }
        }
    }

    private fun validateNode(node: ArborProgramNode, surface: ArborProgramSurface, path: String) {
        when (node.type) {
            "column", "row", "stack" -> require(node.children.isNotEmpty()) { "$path requires children" }
            "button" -> require(node.label.isNotBlank() && node.action.isNotBlank()) { "$path button needs label and action" }
            "toggle", "input", "slider", "choice" -> require(node.value.isNotBlank()) { "$path requires value to name a state key" }
            "choice" -> require(node.options.isNotEmpty()) { "$path choice needs options" }
            "list" -> require(node.items.isNotEmpty()) { "$path list needs items" }
            "chart" -> require(node.items.size >= 2) { "$path chart needs at least two items" }
        }
        if (node.value.isNotBlank() && node.type in setOf("toggle", "input", "slider", "choice")) requireId(node.value)
        if (surface == ArborProgramSurface.WIDGET && node.type == "input") {
            error("$path input is snippet-only; home-screen widgets cannot summon a keyboard")
        }
    }

    private fun validateActionReferences(ui: ArborProgramNode, actions: Map<String, List<ArborProgramAction>>) {
        fun walk(node: ArborProgramNode) {
            node.action.takeIf(String::isNotBlank)?.let { require(it in actions) { "Unknown action group: $it" } }
            node.options.mapNotNull { it.action.takeIf(String::isNotBlank) }.forEach { require(it in actions) { "Unknown action group: $it" } }
            node.items.mapNotNull { it.action.takeIf(String::isNotBlank) }.forEach { require(it in actions) { "Unknown action group: $it" } }
            node.children.forEach(::walk)
        }
        walk(ui)
    }

    private fun validateWidgetCapabilities(
        capabilities: List<ArborWidgetCapabilityRequest>,
        dataSources: List<ArborWidgetDataSource>,
        refreshMinutes: Long?,
    ) {
        val byType = capabilities.groupBy { it.type }
        dataSources.forEach { source ->
            when (source.type) {
                "http_json" -> {
                    val origin = originOfTemplateUrl(source.url)
                    require(byType["network"].orEmpty().flatMap { it.origins }.contains(origin)) {
                        "Data source ${source.id} requires an exact network grant for $origin"
                    }
                }
                "location" -> require(byType["location"].orEmpty().isNotEmpty()) { "Data source ${source.id} requires location capability" }
                "folder_text" -> require(byType["folder"].orEmpty().isNotEmpty()) { "Data source ${source.id} requires folder capability" }
            }
        }
        if (refreshMinutes != null) {
            require(byType["background_refresh"].orEmpty().isNotEmpty()) { "Background refresh capability is required" }
            require(dataSources.isNotEmpty()) { "Background refresh requires a data source" }
        }
    }

    private fun validateWidgetActions(
        actions: Map<String, List<ArborProgramAction>>,
        capabilities: List<ArborWidgetCapabilityRequest>,
        dataSources: List<ArborWidgetDataSource>,
    ) {
        val sources = dataSources.associateBy { it.id }
        actions.values.flatten().forEach { action ->
            when (action.op) {
                "refresh" -> if (action.source.isNotBlank() && action.source != "*") {
                    require(action.source in sources) { "Unknown refresh source: ${action.source}" }
                }
                "write_folder" -> {
                    val source = sources[action.source]
                    require(source?.type == "folder_text") { "write_folder requires a folder_text source" }
                    require(capabilities.any { it.type == "folder" && it.mode == "read_write" }) {
                        "write_folder requires a read_write folder capability"
                    }
                }
            }
        }
    }

    private fun normalizeOrigin(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Network origins must be HTTPS origins"
        }
        require(uri.path.isNullOrBlank() || uri.path == "/") { "Network origins cannot include a path" }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host.lowercase()}$port"
    }

    private fun originOfTemplateUrl(url: String): String {
        val safe = TEMPLATE.replace(url) { "0" }
        val uri = URI(safe)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "Data source URL must use HTTPS" }
        val port = if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
        return "https://${uri.host.lowercase()}$port"
    }

    private fun validateColor(value: String, path: String): String {
        if (value.isBlank()) return ""
        require(value in COLOR_TOKENS || value.matches(HEX_COLOR)) { "$path must be a theme token or #RRGGBB/#AARRGGBB" }
        return value
    }

    private fun primitiveState(value: JsonElement): String = when (value) {
        JsonNull -> ""
        is JsonPrimitive -> value.content
        else -> error("State values must be strings, numbers, booleans, or null")
    }

    private fun requireId(raw: String): String {
        require(raw.matches(ID)) { "Invalid identifier: $raw" }
        return raw
    }

    private fun rejectUnknown(value: JsonObject, allowed: Set<String>, path: String) {
        val unknown = value.keys - allowed
        require(unknown.isEmpty()) { "$path has unknown fields: ${unknown.sorted().joinToString()}" }
    }

    private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.valueString(name: String): String = this[name]?.let(::primitiveState).orEmpty()
    private fun JsonObject.number(name: String, fallback: Double): Double = this[name]?.jsonPrimitive?.doubleOrNull?.takeIf(Double::isFinite) ?: fallback
    private fun JsonObject.numberOrNull(name: String): Double? = this[name]?.jsonPrimitive?.doubleOrNull?.takeIf(Double::isFinite)
    private fun JsonObject.objectOrNull(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.arrayObjects(name: String): List<JsonObject> = (this[name] as? JsonArray)?.map { it.jsonObject }.orEmpty()
    private fun JsonObject.stringList(name: String): List<String> = (this[name] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()

    private val ROOT_FIELDS = setOf("schema", "id", "title", "description", "state", "ui", "actions", "capabilities", "dataSources", "refreshMinutes")
    private val NODE_FIELDS = setOf("type", "id", "text", "label", "value", "action", "visibleWhen", "options", "items", "children", "min", "max", "step", "decimals", "style")
    private val OPTION_FIELDS = setOf("label", "value", "action")
    private val ITEM_FIELDS = setOf("label", "value", "detail", "action")
    private val STYLE_FIELDS = setOf("foreground", "background", "emphasis", "align", "padding", "gap", "cornerRadius", "fontSize", "weight")
    private val ACTION_FIELDS = setOf("op", "target", "value", "expression", "message", "source", "route", "condition")
    private val CAPABILITY_FIELDS = setOf("type", "reason", "origins", "accuracy", "mode")
    private val DATA_SOURCE_FIELDS = setOf("id", "type", "url", "relativePath", "bindings")
    private val BINDING_FIELDS = setOf("state", "path", "fallback")
    private val ID = Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")
    private val JSON_PATH = Regex("[A-Za-z0-9_-]+(?:\\[\\d+])?(?:\\.[A-Za-z0-9_-]+(?:\\[\\d+])?)*")
    private val HEX_COLOR = Regex("#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?")
    private val COLOR_TOKENS = setOf("primary", "secondary", "tertiary", "surface", "surface_variant", "on_surface", "error", "transparent")
    private val LOCATION_PATHS = setOf("latitude", "longitude", "accuracy", "updatedAt")
    private val STATE_ACTIONS = setOf("set", "add", "multiply", "toggle", "append", "backspace", "evaluate")
    private val EXTERNAL_ACTIONS = setOf("refresh", "write_folder", "open_app")
    private val TEMPLATE = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")

    private const val MAX_SOURCE_CHARS = 96_000
    private const val MAX_STATE_VALUES = 64
    private const val MAX_STATE_VALUE_CHARS = 1_000
    private const val MAX_TEXT_CHARS = 2_000
    private const val MAX_EXPRESSION_CHARS = 500
    private const val MAX_NODE_DEPTH = 12
    private const val MAX_NODES = 160
    private const val MAX_CHILDREN = 32
    private const val MAX_OPTIONS = 32
    private const val MAX_ITEMS = 48
    private const val MAX_ACTION_GROUPS = 64
    private const val MAX_ACTIONS_PER_GROUP = 12
    private const val MAX_CAPABILITIES = 8
    private const val MAX_NETWORK_ORIGINS = 8
    private const val MAX_DATA_SOURCES = 12
    private const val MAX_BINDINGS = 24
    const val MIN_REFRESH_MINUTES = 15L
    const val MAX_REFRESH_MINUTES = 1_440L
}

object ArborProgramRuntime {
    fun render(template: String, state: Map<String, String>): String = TEMPLATE.replace(template) { match ->
        val token = match.groupValues[1].trim()
        val direct = state[token]
        if (direct != null) direct else SafeExpression.evaluate(token.removePrefix("="), numericState(state)).getOrNull()?.let(::formatCompact).orEmpty()
    }

    fun visible(condition: String, state: Map<String, String>): Boolean {
        if (condition.isBlank()) return true
        val trimmed = condition.trim()
        val operator = when {
            "!=" in trimmed -> "!="
            "==" in trimmed -> "=="
            else -> null
        }
        if (operator != null) {
            val (left, right) = trimmed.split(operator, limit = 2).map(String::trim)
            val leftValue = state[left] ?: render(left.removeSurrounding("\""), state)
            val rightValue = render(right.removeSurrounding("\""), state)
            return (leftValue == rightValue) == (operator == "==")
        }
        SafeExpression.evaluate(trimmed, numericState(state)).getOrNull()?.let { return it != 0.0 }
        return state[trimmed]?.let(::truthy) ?: false
    }

    fun apply(
        actionId: String,
        definition: ArborProgramDefinition,
        currentState: Map<String, String>,
    ): ArborProgramTransition {
        val operations = definition.actions[actionId].orEmpty()
        val next = currentState.toMutableMap()
        var submit: String? = null
        val refresh = linkedSetOf<String>()
        val folderWrites = mutableListOf<ArborFolderWriteRequest>()
        var route: String? = null
        operations.forEach { action ->
            if (!visible(action.condition, next)) return@forEach
            val current = next[action.target].orEmpty()
            when (action.op) {
                "set" -> next[action.target] = render(action.value, next).take(1_000)
                "add" -> next[action.target] = formatCompact((current.toDoubleOrNull() ?: 0.0) + actionNumber(action, next))
                "multiply" -> next[action.target] = formatCompact((current.toDoubleOrNull() ?: 0.0) * actionNumber(action, next))
                "toggle" -> next[action.target] = (!truthy(current)).toString()
                "append" -> next[action.target] = (current + render(action.value, next)).take(1_000)
                "backspace" -> next[action.target] = current.dropLast(1)
                "evaluate" -> {
                    val expression = action.expression.ifBlank { current }
                    SafeExpression.evaluate(render(expression, next), numericState(next)).getOrNull()?.let { next[action.target] = formatCompact(it) }
                }
                "reset" -> { next.clear(); next.putAll(definition.state) }
                "submit" -> submit = render(action.message.ifBlank { action.value }, next)
                "refresh" -> refresh += action.source.ifBlank { "*" }
                "write_folder" -> folderWrites += ArborFolderWriteRequest(action.source, render(action.value, next).take(1_000_000))
                "open_app" -> route = render(action.route, next)
            }
        }
        return ArborProgramTransition(next, submit, refresh, folderWrites, route)
    }

    fun numericState(state: Map<String, String>): Map<String, Double> = state.mapValues { (_, value) ->
        value.toDoubleOrNull() ?: if (truthy(value)) 1.0 else 0.0
    }

    fun truthy(value: String?): Boolean = value.equals("true", true) || value?.toDoubleOrNull()?.let { it != 0.0 } == true

    private fun actionNumber(action: ArborProgramAction, state: Map<String, String>): Double =
        action.expression.takeIf(String::isNotBlank)?.let { SafeExpression.evaluate(render(it, state), numericState(state)).getOrNull() }
            ?: render(action.value, state).toDoubleOrNull() ?: 0.0

    fun formatCompact(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString()
    else SafeExpression.format(value, 6).trimEnd('0').trimEnd('.')

    private val TEMPLATE = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")
}

/** A deliberately small numeric language for generated programs; it never evaluates code. */
object SafeExpression {
    fun evaluate(expression: String, variables: Map<String, Double> = emptyMap()): Result<Double> = runCatching {
        require(expression.length <= 500) { "Expression is too long" }
        Parser(expression, variables).parse().also { require(it.isFinite()) { "Result is not finite" } }
    }

    fun format(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals.coerceIn(0, 8)}f", value)

    private class Parser(private val source: String, private val variables: Map<String, Double>) {
        private var index = 0

        fun parse(): Double {
            val value = comparison()
            whitespace()
            require(index == source.length) { "Unexpected '${source[index]}' at ${index + 1}" }
            return value
        }

        private fun comparison(): Double {
            val left = expression()
            whitespace()
            return when {
                takeString(">=") -> if (left >= expression()) 1.0 else 0.0
                takeString("<=") -> if (left <= expression()) 1.0 else 0.0
                takeString("==") -> if (left == expression()) 1.0 else 0.0
                takeString("!=") -> if (left != expression()) 1.0 else 0.0
                take('>') -> if (left > expression()) 1.0 else 0.0
                take('<') -> if (left < expression()) 1.0 else 0.0
                else -> left
            }
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                whitespace()
                value = when {
                    take('+') -> value + term()
                    take('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                whitespace()
                value = when {
                    take('*') -> value * power()
                    take('/') -> value / power()
                    take('%') -> value % power()
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            whitespace()
            return if (take('^')) base.pow(power()) else base
        }

        private fun unary(): Double {
            whitespace()
            return when {
                take('+') -> unary()
                take('-') -> -unary()
                else -> atom()
            }
        }

        private fun atom(): Double {
            whitespace()
            if (take('(')) return comparison().also { whitespace(); require(take(')')) { "Missing ')'" } }
            if (index < source.length && (source[index].isDigit() || source[index] == '.')) return number()
            val name = identifier()
            require(name.isNotBlank()) { "Expected a number at ${index + 1}" }
            whitespace()
            if (!take('(')) return variables[name] ?: when (name.lowercase()) {
                "pi" -> Math.PI
                "e" -> Math.E
                else -> error("Unknown value: $name")
            }
            val arguments = mutableListOf<Double>()
            whitespace()
            if (!take(')')) {
                do arguments += comparison() while (run { whitespace(); take(',') })
                whitespace(); require(take(')')) { "Missing ')' after $name" }
            }
            return function(name.lowercase(), arguments)
        }

        private fun function(name: String, args: List<Double>): Double = when (name) {
            "abs" -> abs(args.single())
            "round" -> round(args.single())
            "min" -> args.reduce(::min)
            "max" -> args.reduce(::max)
            "pow" -> args.also { require(it.size == 2) }.let { it[0].pow(it[1]) }
            else -> error("Unknown function: $name")
        }

        private fun number(): Double {
            val start = index
            val match = NUMBER.find(source, index)?.takeIf { it.range.first == index } ?: error("Invalid number at ${start + 1}")
            index = match.range.last + 1
            return match.value.toDouble()
        }

        private fun identifier(): String {
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '.')) index++
            return source.substring(start, index)
        }

        private fun whitespace() { while (index < source.length && source[index].isWhitespace()) index++ }
        private fun take(char: Char): Boolean = if (index < source.length && source[index] == char) { index++; true } else false
        private fun takeString(value: String): Boolean = if (source.startsWith(value, index)) { index += value.length; true } else false

        companion object { private val NUMBER = Regex("(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?") }
    }
}
