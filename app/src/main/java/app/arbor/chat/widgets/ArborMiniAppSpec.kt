package app.arbor.chat.widgets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ArborMiniAppDefinition(
    val initialState: Map<String, String>,
    val screens: List<ArborMiniAppScreen>,
)

data class ArborMiniAppScreen(
    val id: String,
    val title: String,
    val components: List<ArborMiniAppComponent>,
)

data class ArborMiniAppComponent(
    val type: String,
    val id: String,
    val label: String = "",
    val text: String = "",
    val value: String = "",
    val expression: String = "",
    val visibleWhen: String = "",
    val placeholder: String = "",
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val decimals: Int = 2,
    val prefix: String = "",
    val suffix: String = "",
    val options: List<String> = emptyList(),
    val items: List<ArborMiniAppItem> = emptyList(),
    val buttons: List<ArborMiniAppButton> = emptyList(),
)

data class ArborMiniAppItem(
    val label: String,
    val value: String = "",
    val detail: String = "",
    val visibleWhen: String = "",
    val actions: List<ArborMiniAppAction> = emptyList(),
)

data class ArborMiniAppButton(
    val label: String,
    val style: String = "secondary",
    val visibleWhen: String = "",
    val actions: List<ArborMiniAppAction>,
)

data class ArborMiniAppAction(
    val operation: String,
    val target: String = "",
    val value: String = "",
    val expression: String = "",
    val screen: String = "",
    val message: String = "",
    val condition: String = "",
)

object ArborMiniAppParser {
    private val json = Json { ignoreUnknownKeys = true }
    val supportedComponentTypes: Set<String> = setOf(
        "text", "metric", "input", "slider", "toggle", "choice", "buttons", "progress",
        "list", "table", "chart", "timer", "divider", "spacer",
    )
    val supportedOperations: Set<String> = setOf(
        "set", "add", "multiply", "toggle", "append", "backspace", "evaluate", "navigate",
        "reset", "refresh", "submit", "timer_start", "timer_pause", "timer_reset",
    )

    fun parse(source: String): Result<ArborMiniAppDefinition> = runCatching {
        val root = json.parseToJsonElement(source).jsonObject
        val rawState = root.objectOrNull("state") ?: JsonObject(emptyMap())
        require(rawState.size <= 48) { "A mini_app supports at most 48 state values" }
        val state = rawState.entries.associate { (rawKey, rawValue) ->
            val key = sanitizeId(rawKey, "state")
            key to rawValue.jsonPrimitive.contentOrNull.orEmpty().take(500)
        }
        require(state.size == rawState.size.coerceAtMost(48)) { "Mini-app state keys must be unique after sanitizing" }
        val rawScreens = root.arrayObjects("screens")
        require(rawScreens.size <= 8) { "A mini_app supports at most 8 screens" }
        val screens = rawScreens.mapIndexed { screenIndex, screen ->
            val screenId = screen.safeId("id", "screen_$screenIndex")
            val rawComponents = screen.arrayObjects("components")
            require(rawComponents.size <= 32) { "Screen $screenId supports at most 32 components" }
            val components = rawComponents.mapIndexed { componentIndex, component ->
                parseComponent(component, "${screenId}_component_$componentIndex")
            }.distinctBy { it.id }
            require(components.isNotEmpty()) { "Screen $screenId has no components" }
            ArborMiniAppScreen(
                id = screenId,
                title = screen.string("title").take(100),
                components = components,
            )
        }.distinctBy { it.id }
        require(screens.isNotEmpty()) { "A mini_app needs at least one screen" }
        val screenIds = screens.mapTo(mutableSetOf()) { it.id }
        screens.flatMap { it.components }.flatMap { component ->
            component.buttons.flatMap { it.actions } + component.items.flatMap { it.actions }
        }.forEach { action ->
            if (action.operation == "navigate") require(action.screen in screenIds) { "Unknown mini-app screen: ${action.screen}" }
            if (action.operation in STATE_OPERATIONS) require(action.target.isNotBlank()) { "${action.operation} needs a target" }
        }
        ArborMiniAppDefinition(state, screens)
    }

    private fun parseComponent(component: JsonObject, fallbackId: String): ArborMiniAppComponent {
        val type = component.string("type").lowercase().ifBlank { "text" }
        require(type in supportedComponentTypes) { "Unsupported mini-app component: $type" }
        val min = component.number("min", 0.0)
        val rawOptions = component.stringList("options")
        require(rawOptions.size <= 24) { "Mini-app choices support at most 24 options" }
        val options = rawOptions.map { it.take(100) }
        val rawItems = component.arrayObjects("items")
        require(rawItems.size <= 24) { "Mini-app components support at most 24 items" }
        val items = rawItems.map { item ->
            ArborMiniAppItem(
                label = item.string("label").take(120),
                value = item.string("value").take(500),
                detail = item.string("detail").take(200),
                visibleWhen = item.string("visibleWhen").take(300),
                actions = parseActions(item),
            )
        }
        val rawButtons = component.arrayObjects("buttons")
        require(rawButtons.size <= 16) { "Mini-app button groups support at most 16 buttons" }
        val buttons = rawButtons.map { button ->
            ArborMiniAppButton(
                label = button.string("label").take(48).ifBlank { "Action" },
                style = button.string("style").lowercase().takeIf { it in setOf("primary", "secondary", "danger", "quiet") } ?: "secondary",
                visibleWhen = button.string("visibleWhen").take(300),
                actions = parseActions(button).also { require(it.isNotEmpty()) { "Mini-app buttons need actions" } },
            )
        }
        return ArborMiniAppComponent(
            type = type,
            id = component.safeId("id", fallbackId),
            label = component.string("label").take(100),
            text = component.string("text").take(1_000),
            value = component.string("value").take(500),
            expression = component.string("expression").take(500),
            visibleWhen = component.string("visibleWhen").take(300),
            placeholder = component.string("placeholder").take(120),
            min = min,
            max = component.number("max", 100.0).coerceAtLeast(min),
            step = component.number("step", 1.0).coerceAtLeast(0.000001),
            decimals = component.number("decimals", 2.0).toInt().coerceIn(0, 8),
            prefix = component.string("prefix").take(16),
            suffix = component.string("suffix").take(16),
            options = options,
            items = items,
            buttons = buttons,
        )
    }

    private fun parseActions(parent: JsonObject): List<ArborMiniAppAction> {
        val raw = parent.arrayObjects("actions").ifEmpty { parent.objectOrNull("action")?.let(::listOf).orEmpty() }
        require(raw.size <= 8) { "A mini-app control supports at most 8 actions" }
        return raw.map { action ->
            val operation = action.string("operation").lowercase()
            require(operation in supportedOperations) { "Unsupported mini-app action: $operation" }
            ArborMiniAppAction(
                operation = operation,
                target = action.safeId("target", "").takeIf { action.string("target").isNotBlank() }.orEmpty(),
                value = action.string("value").ifBlank { action["value"]?.jsonPrimitive?.contentOrNull.orEmpty() }.take(500),
                expression = action.string("expression").take(500),
                screen = action.safeId("screen", "").takeIf { action.string("screen").isNotBlank() }.orEmpty(),
                message = action.string("message").take(500),
                condition = action.string("condition").take(300),
            )
        }
    }

    private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.number(name: String, fallback: Double): Double = this[name]?.jsonPrimitive?.doubleOrNull?.takeIf(Double::isFinite) ?: fallback
    private fun JsonObject.stringList(name: String): List<String> = runCatching { this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()
    private fun JsonObject.arrayObjects(name: String): List<JsonObject> = runCatching { this[name]?.jsonArray?.map { it.jsonObject } }.getOrNull().orEmpty()
    private fun JsonObject.objectOrNull(name: String): JsonObject? = runCatching { this[name]?.jsonObject }.getOrNull()
    private fun JsonObject.safeId(name: String, fallback: String): String = sanitizeId(string(name), fallback)
    private fun sanitizeId(raw: String, fallback: String): String = raw.replace(Regex("[^A-Za-z0-9_]"), "_").take(40).ifBlank { fallback }

    private val STATE_OPERATIONS = setOf("set", "add", "multiply", "toggle", "append", "backspace", "evaluate", "timer_start", "timer_pause", "timer_reset")
}

object ArborMiniAppRuntime {
    fun render(template: String, state: Map<String, String>): String = TEMPLATE.replace(template) { match ->
        val token = match.groupValues[1].trim()
        if (token.startsWith("=")) {
            SafeExpression.evaluate(token.drop(1), numericState(state)).getOrNull()?.let(::formatCompact).orEmpty()
        } else state[token].orEmpty()
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
            return (state[left].orEmpty() == render(right.removeSurrounding("\""), state)) == (operator == "==")
        }
        SafeExpression.evaluate(trimmed, numericState(state)).getOrNull()?.let { return it != 0.0 }
        return state[trimmed]?.let(::truthy) ?: false
    }

    fun apply(
        actions: List<ArborMiniAppAction>,
        state: Map<String, String>,
        defaults: Map<String, String>,
    ): ArborMiniAppTransition {
        val next = state.toMutableMap()
        var submitMessage: String? = null
        var refresh = false
        actions.forEach { action ->
            if (!visible(action.condition, next)) return@forEach
            val current = next[action.target].orEmpty()
            when (action.operation) {
                "set" -> next[action.target] = render(action.value, next).take(500)
                "add" -> next[action.target] = formatCompact((current.toDoubleOrNull() ?: 0.0) + actionNumber(action, next))
                "multiply" -> next[action.target] = formatCompact((current.toDoubleOrNull() ?: 0.0) * actionNumber(action, next))
                "toggle" -> next[action.target] = (!truthy(current)).toString()
                "append" -> next[action.target] = (current + render(action.value, next)).take(500)
                "backspace" -> next[action.target] = current.dropLast(1)
                "evaluate" -> {
                    val expression = action.expression.ifBlank { current }
                    SafeExpression.evaluate(render(expression, next), numericState(next)).getOrNull()?.let { next[action.target] = formatCompact(it) }
                }
                "navigate" -> next[SCREEN_STATE] = action.screen
                "reset" -> { next.clear(); next.putAll(defaults) }
                "refresh" -> refresh = true
                "submit" -> submitMessage = render(action.message.ifBlank { action.value }, next)
                "timer_start" -> {
                    next["${action.target}_running"] = "true"
                    next["${action.target}_started_at"] = System.currentTimeMillis().toString()
                    next["${action.target}_started_value"] = next[action.target].orEmpty()
                }
                "timer_pause" -> next["${action.target}_running"] = "false"
                "timer_reset" -> {
                    next[action.target] = render(action.value, next).ifBlank { defaults[action.target].orEmpty() }
                    next["${action.target}_running"] = "false"
                    next["${action.target}_started_at"] = ""
                    next["${action.target}_started_value"] = ""
                }
            }
        }
        return ArborMiniAppTransition(next, submitMessage, refresh)
    }

    fun numericState(state: Map<String, String>): Map<String, Double> = state.mapValues { (_, value) ->
        value.toDoubleOrNull() ?: if (truthy(value)) 1.0 else 0.0
    }

    private fun actionNumber(action: ArborMiniAppAction, state: Map<String, String>): Double =
        action.expression.takeIf(String::isNotBlank)?.let { SafeExpression.evaluate(it, numericState(state)).getOrNull() }
            ?: render(action.value, state).toDoubleOrNull() ?: 0.0

    private fun truthy(value: String): Boolean = value.equals("true", true) || value.toDoubleOrNull()?.let { it != 0.0 } == true
    private fun formatCompact(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else SafeExpression.format(value, 6).trimEnd('0').trimEnd('.')
    // Keep both template delimiters explicit. Some Android regex runtimes
    // reject the formerly bare closing braces during class initialization.
    private val TEMPLATE = Regex("\\{\\{\\s*([^{}]+?)\\s*\\}\\}")
    const val SCREEN_STATE = "_screen"
}

data class ArborMiniAppTransition(
    val state: Map<String, String>,
    val submitMessage: String?,
    val refreshRequested: Boolean,
)
