package app.arbor.chat.widgets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

data class ArborWidgetDefinition(
    val type: String,
    val title: String,
    val description: String = "",
    val options: List<String> = emptyList(),
    val fields: List<ArborWidgetField> = emptyList(),
    val outputs: List<ArborWidgetOutput> = emptyList(),
    val actions: List<ArborWidgetAction> = emptyList(),
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val value: Double = 0.0,
    val fromUnit: String = "",
    val toUnit: String = "",
    val rate: Double = 1.0,
    val symbol: String = "",
    val dataSource: ArborWidgetDataSource? = null,
    val schedule: List<ArborWidgetScheduleItem> = emptyList(),
    val timezone: String = "",
    val miniApp: ArborMiniAppDefinition? = null,
    val homeEnabled: Boolean = false,
)

data class ArborWidgetDataSource(
    val url: String,
    val refreshMinutes: Long = 30,
    val bindings: List<ArborWidgetBinding>,
)

data class ArborWidgetBinding(
    val id: String,
    val label: String,
    val path: String,
    val decimals: Int = 2,
    val prefix: String = "",
    val suffix: String = "",
)

data class ArborWidgetScheduleItem(
    val id: String,
    val label: String,
    val time: String,
    val detail: String = "",
)

data class ArborWidgetField(
    val id: String,
    val label: String,
    val kind: String,
    val value: String = "",
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val options: List<String> = emptyList(),
    val prefix: String = "",
    val suffix: String = "",
)

data class ArborWidgetOutput(
    val label: String,
    val expression: String,
    val decimals: Int = 2,
    val prefix: String = "",
    val suffix: String = "",
)

data class ArborWidgetAction(
    val label: String,
    val target: String = "",
    val operation: String = "submit",
    val value: Double = 0.0,
)

object ArborWidgetParser {
    private val json = Json { ignoreUnknownKeys = true }
    private val supportedTypes = setOf(
        "choice", "checklist", "slider", "form", "calculator", "converter", "counter", "rating", "progress",
        "live_data", "stock", "schedule", "prayer_times", "mini_app",
    )
    private val supportedFields = setOf("number", "text", "slider", "toggle", "choice")

    fun parse(source: String): Result<ArborWidgetDefinition> = runCatching {
        require(source.length <= 48_000) { "Widget definition is too large" }
        val root = json.parseToJsonElement(source).jsonObject
        val type = root.string("type").lowercase().ifBlank { "form" }
        require(type in supportedTypes) { "Unsupported widget type: $type" }
        val title = root.string("title").take(120).ifBlank { "Interactive widget" }
        val options = root.stringList("options").take(24).map { it.take(120) }
        val min = root.number("min", 0.0)
        val max = root.number("max", 100.0).coerceAtLeast(min)
        val step = root.number("step", 1.0).coerceAtLeast(0.000001)
        val value = root.number("value", min).coerceIn(min, max)
        val fields = root.arrayObjects("fields").take(16).mapIndexed { index, field ->
            val id = field.string("id").replace(Regex("[^A-Za-z0-9_]"), "_").take(40).ifBlank { "field_$index" }
            val kind = field.string("kind").lowercase().ifBlank { "number" }
            require(kind in supportedFields) { "Unsupported field kind: $kind" }
            val fieldMin = field.number("min", 0.0)
            ArborWidgetField(
                id = id,
                label = field.string("label").take(100).ifBlank { id.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                kind = kind,
                value = field.string("value").ifBlank { field["value"]?.jsonPrimitive?.contentOrNull.orEmpty() }.take(500),
                min = fieldMin,
                max = field.number("max", 100.0).coerceAtLeast(fieldMin),
                step = field.number("step", 1.0).coerceAtLeast(0.000001),
                options = field.stringList("options").take(24).map { it.take(100) },
                prefix = field.string("prefix").take(16),
                suffix = field.string("suffix").take(16),
            )
        }.distinctBy { it.id }
        val outputs = root.arrayObjects("outputs").take(12).map { output ->
            ArborWidgetOutput(
                label = output.string("label").take(100).ifBlank { "Result" },
                expression = output.string("expression").take(500).also { require(it.isNotBlank()) { "Output expression is missing" } },
                decimals = output.number("decimals", 2.0).toInt().coerceIn(0, 8),
                prefix = output.string("prefix").take(16),
                suffix = output.string("suffix").take(16),
            )
        }
        val actions = root.arrayObjects("actions").take(8).map { action ->
            val operation = action.string("operation").lowercase().ifBlank { "submit" }
            require(operation in setOf("add", "set", "multiply", "toggle", "reset", "submit")) { "Unsupported action: $operation" }
            ArborWidgetAction(
                label = action.string("label").take(32).ifBlank { operation.replaceFirstChar(Char::uppercase) },
                target = action.string("target").take(40),
                operation = operation,
                value = action.number("value", 0.0),
            )
        }
        val dataSource = root.objectOrNull("dataSource")?.let { data ->
            val url = data.string("url").take(2_048)
            require(url.startsWith("https://")) { "Live widgets require an HTTPS data-source URL" }
            val bindings = data.arrayObjects("bindings").take(12).mapIndexed { index, binding ->
                val id = binding.safeId("id", "value_$index")
                val path = binding.string("path").removePrefix("$.").take(300)
                require(path.isNotBlank() && path.matches(JSON_PATH)) { "Invalid JSON path for $id" }
                ArborWidgetBinding(
                    id = id,
                    label = binding.string("label").take(80).ifBlank { id.replace('_', ' ').replaceFirstChar(Char::uppercase) },
                    path = path,
                    decimals = binding.number("decimals", 2.0).toInt().coerceIn(0, 8),
                    prefix = binding.string("prefix").take(16),
                    suffix = binding.string("suffix").take(16),
                )
            }.distinctBy { it.id }
            require(bindings.isNotEmpty()) { "A live data source needs at least one binding" }
            ArborWidgetDataSource(
                url = url,
                refreshMinutes = data.number("refreshMinutes", 30.0).toLong().coerceIn(15, 1_440),
                bindings = bindings,
            )
        }
        val schedule = (root.arrayObjects("items") + root.arrayObjects("events")).take(12).mapIndexed { index, item ->
            val time = item.string("time").take(5)
            require(time.matches(TIME)) { "Schedule time must use 24-hour HH:mm format" }
            ArborWidgetScheduleItem(
                id = item.safeId("id", "event_$index"),
                label = item.string("label").take(80).ifBlank { "Event ${index + 1}" },
                time = time,
                detail = item.string("detail").take(120),
            )
        }.distinctBy { it.id }
        if (type in setOf("choice", "checklist") && options.isEmpty()) require(fields.isNotEmpty()) { "Widget options are missing" }
        if (type == "converter") require(root.number("rate", 1.0) > 0.0) { "Converter rate must be positive" }
        if (type in setOf("live_data", "stock")) require(dataSource != null) { "$type widgets need a dataSource" }
        if (type in setOf("schedule", "prayer_times")) require(schedule.isNotEmpty()) { "$type widgets need items" }
        val miniApp = if (type == "mini_app") ArborMiniAppParser.parse(source).getOrThrow() else null
        val surface = root.string("surface").lowercase()
        require(surface.isBlank() || surface in setOf("chat", "home", "both")) { "surface must be chat, home, or both" }
        val homeEnabled = when (surface) {
            "home", "both" -> true
            "chat" -> false
            else -> root["home"]?.jsonPrimitive?.booleanOrNull ?: false
        }
        ArborWidgetDefinition(
            type = type,
            title = title,
            description = root.string("description").take(500),
            options = options,
            fields = fields,
            outputs = outputs,
            actions = actions,
            min = min,
            max = max,
            step = step,
            value = value,
            fromUnit = root.string("from").take(24),
            toUnit = root.string("to").take(24),
            rate = root.number("rate", 1.0),
            symbol = root.string("symbol").take(24),
            dataSource = dataSource,
            schedule = schedule,
            timezone = root.string("timezone").take(80),
            miniApp = miniApp,
            homeEnabled = homeEnabled,
        )
    }

    private fun JsonObject.string(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.number(name: String, fallback: Double): Double = this[name]?.jsonPrimitive?.doubleOrNull?.takeIf(Double::isFinite) ?: fallback
    private fun JsonObject.stringList(name: String): List<String> = runCatching { this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } }.getOrNull().orEmpty()
    private fun JsonObject.arrayObjects(name: String): List<JsonObject> = runCatching { this[name]?.jsonArray?.map { it.jsonObject } }.getOrNull().orEmpty()
    private fun JsonObject.objectOrNull(name: String): JsonObject? = runCatching { this[name]?.jsonObject }.getOrNull()
    private fun JsonObject.safeId(name: String, fallback: String): String =
        string(name).replace(Regex("[^A-Za-z0-9_]"), "_").take(40).ifBlank { fallback }

    private val JSON_PATH = Regex("[A-Za-z0-9_-]+(?:\\[\\d+])?(?:\\.[A-Za-z0-9_-]+(?:\\[\\d+])?)*")
    private val TIME = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
}

/** A deliberately small numeric language for generated widgets; it never evaluates code. */
object SafeExpression {
    fun evaluate(expression: String, variables: Map<String, Double> = emptyMap()): Result<Double> = runCatching {
        require(expression.length <= 500) { "Expression is too long" }
        Parser(expression, variables).parse().also { require(it.isFinite()) { "Result is not finite" } }
    }

    fun format(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals.coerceIn(0, 8)}f", value)

    private class Parser(private val source: String, private val variables: Map<String, Double>) {
        private var index = 0

        fun parse(): Double {
            val value = expression()
            whitespace()
            require(index == source.length) { "Unexpected '${source[index]}' at ${index + 1}" }
            return value
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
            if (take('(')) return expression().also { whitespace(); require(take(')')) { "Missing ')'" } }
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
                do arguments += expression() while (run { whitespace(); take(',') })
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
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_')) index++
            return source.substring(start, index)
        }

        private fun whitespace() { while (index < source.length && source[index].isWhitespace()) index++ }
        private fun take(char: Char): Boolean = if (index < source.length && source[index] == char) { index++; true } else false

        companion object { private val NUMBER = Regex("(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?") }
    }
}
