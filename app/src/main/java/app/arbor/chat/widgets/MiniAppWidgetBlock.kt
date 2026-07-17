package app.arbor.chat.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.arbor.chat.ui.NativeChartBlock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

@Composable
internal fun MiniAppWidgetBlock(
    definition: ArborWidgetDefinition,
    source: String,
    onSubmit: (String) -> Unit,
    networkEnabled: Boolean,
    onEnableNetwork: () -> Unit,
) {
    val app = definition.miniApp ?: return
    val defaults = remember(app) {
        app.initialState + (ArborMiniAppRuntime.SCREEN_STATE to app.screens.first().id)
    }
    var encodedState by rememberSaveable(source) { mutableStateOf(encodeState(defaults)) }
    var refreshKey by rememberSaveable(source) { mutableIntStateOf(0) }
    var liveStatus by remember(source) { mutableStateOf("") }
    val state = remember(encodedState) { decodeState(encodedState).ifEmpty { defaults } }
    val screen = app.screens.firstOrNull { it.id == state[ArborMiniAppRuntime.SCREEN_STATE] } ?: app.screens.first()

    fun replaceState(next: Map<String, String>) { encodedState = encodeState(next) }
    fun dispatch(actions: List<ArborMiniAppAction>) {
        val transition = ArborMiniAppRuntime.apply(actions, decodeState(encodedState).ifEmpty { defaults }, defaults)
        replaceState(transition.state)
        transition.submitMessage?.takeIf(String::isNotBlank)?.let { onSubmit("Mini-app response — ${definition.title}: $it") }
        if (transition.refreshRequested) {
            if (networkEnabled) refreshKey++ else onEnableNetwork()
        }
    }

    LaunchedEffect(definition.dataSource, refreshKey, networkEnabled) {
        val liveSource = definition.dataSource ?: return@LaunchedEffect
        if (!networkEnabled) {
            liveStatus = "Live data is disabled until you approve network access"
            return@LaunchedEffect
        }
        liveStatus = "Refreshing…"
        runCatching { WidgetLiveDataClient.fetch(liveSource) }.fold(
            onSuccess = { result ->
                val merged = decodeState(encodedState).ifEmpty { defaults }.toMutableMap().apply { putAll(result.values) }
                replaceState(merged)
                liveStatus = "Live data updated"
            },
            onFailure = { liveStatus = it.message ?: "Refresh failed" },
        )
    }

    if (screen.title.isNotBlank()) {
        Text(screen.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
    screen.components.forEach { component ->
        if (ArborMiniAppRuntime.visible(component.visibleWhen, state)) {
            MiniAppComponent(component, state, ::replaceState, ::dispatch)
        }
    }
    if (definition.dataSource != null && liveStatus.isNotBlank()) {
        Text(liveStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (app.screens.size > 1) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            app.screens.take(5).forEach { target ->
                FilterChip(
                    selected = target.id == screen.id,
                    onClick = { dispatch(listOf(ArborMiniAppAction("navigate", screen = target.id))) },
                    label = { Text(target.title.ifBlank { target.id }) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MiniAppComponent(
    component: ArborMiniAppComponent,
    state: Map<String, String>,
    replaceState: (Map<String, String>) -> Unit,
    dispatch: (List<ArborMiniAppAction>) -> Unit,
) {
    fun rendered(value: String) = ArborMiniAppRuntime.render(value, state)
    fun update(id: String, value: String) = replaceState(state.toMutableMap().apply { put(id, value.take(500)) })
    when (component.type) {
        "text" -> Text(rendered(component.text.ifBlank { component.value }), style = MaterialTheme.typography.bodyMedium)
        "metric" -> {
            val raw = component.expression.takeIf(String::isNotBlank)?.let {
                SafeExpression.evaluate(it, ArborMiniAppRuntime.numericState(state)).getOrNull()?.let { number -> SafeExpression.format(number, component.decimals) }
            } ?: rendered(component.value.ifBlank { "{{${component.id}}}" })
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    if (component.label.isNotBlank()) Text(component.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(component.prefix + raw + component.suffix, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        "input" -> OutlinedTextField(
            value = state[component.id].orEmpty(),
            onValueChange = { update(component.id, it) },
            label = { Text(component.label.ifBlank { component.id }) },
            placeholder = component.placeholder.takeIf(String::isNotBlank)?.let { { Text(rendered(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = if (component.value == "number") KeyboardType.Decimal else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        "slider" -> {
            val current = state[component.id]?.toDoubleOrNull()?.coerceIn(component.min, component.max) ?: component.min
            Text("${component.label.ifBlank { component.id }}: ${compactMini(current)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = current.toFloat(),
                onValueChange = { raw ->
                    val snapped = (((raw - component.min) / component.step).roundToInt() * component.step + component.min).coerceIn(component.min, component.max)
                    update(component.id, compactMini(snapped))
                },
                valueRange = component.min.toFloat()..component.max.toFloat(),
            )
        }
        "toggle" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(component.label.ifBlank { component.id }, Modifier.weight(1f))
            Switch(
                checked = state[component.id]?.toBooleanStrictOrNull() ?: false,
                onCheckedChange = { update(component.id, it.toString()) },
            )
        }
        "choice" -> {
            if (component.label.isNotBlank()) Text(component.label, style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                component.options.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = state[component.id] == option,
                                onClick = { update(component.id, option) },
                                label = { Text(option) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        "buttons" -> component.buttons.filter { ArborMiniAppRuntime.visible(it.visibleWhen, state) }.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { button ->
                    if (button.style == "primary") {
                        Button(onClick = { dispatch(button.actions) }, modifier = Modifier.weight(1f).height(46.dp)) { Text(rendered(button.label)) }
                    } else {
                        OutlinedButton(onClick = { dispatch(button.actions) }, modifier = Modifier.weight(1f).height(46.dp)) { Text(rendered(button.label)) }
                    }
                }
            }
        }
        "progress" -> {
            val value = component.expression.takeIf(String::isNotBlank)?.let { SafeExpression.evaluate(it, ArborMiniAppRuntime.numericState(state)).getOrNull() }
                ?: state[component.id]?.toDoubleOrNull() ?: rendered(component.value).toDoubleOrNull() ?: 0.0
            val fraction = ((value - component.min) / (component.max - component.min).coerceAtLeast(0.000001)).toFloat().coerceIn(0f, 1f)
            if (component.label.isNotBlank()) Text("${component.label}: ${compactMini(value)}", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        }
        "list", "table" -> {
            component.items.filter { ArborMiniAppRuntime.visible(it.visibleWhen, state) }.forEach { item ->
                val modifier = if (item.actions.isEmpty()) Modifier else Modifier.clickable { dispatch(item.actions) }
                Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().then(modifier)) {
                    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rendered(item.label), fontWeight = FontWeight.Medium)
                            if (item.detail.isNotBlank()) Text(rendered(item.detail), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (item.value.isNotBlank()) Text(rendered(item.value), fontFamily = if (component.type == "table") FontFamily.Monospace else FontFamily.Default)
                    }
                }
            }
        }
        "chart" -> NativeChartBlock(chartSource(component, state))
        "timer" -> MiniTimer(component, state, replaceState)
        "divider" -> HorizontalDivider()
        "spacer" -> Box(Modifier.height(component.value.toIntOrNull()?.coerceIn(4, 64)?.dp ?: 12.dp))
    }
}

@Composable
private fun MiniTimer(component: ArborMiniAppComponent, state: Map<String, String>, replaceState: (Map<String, String>) -> Unit) {
    val running = state["${component.id}_running"] == "true"
    val seconds = state[component.id]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
    LaunchedEffect(component.id, running, seconds) {
        if (running) {
            delay(1_000)
            val next = if (component.value == "countdown") (seconds - 1).coerceAtLeast(0) else seconds + 1
            replaceState(state.toMutableMap().apply {
                put(component.id, next.toString())
                if (component.value == "countdown" && next == 0L) put("${component.id}_running", "false")
            })
        }
    }
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remainder = seconds % 60
    Text(component.label.ifBlank { "Timer" }, style = MaterialTheme.typography.labelMedium)
    Text("%02d:%02d:%02d".format(hours, minutes, remainder), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
}

private fun chartSource(component: ArborMiniAppComponent, state: Map<String, String>): String = buildJsonObject {
    put("type", component.value.ifBlank { "bar" })
    put("title", component.label)
    put("series", buildJsonArray {
        add(buildJsonObject {
            put("name", component.label.ifBlank { "Values" })
            put("values", buildJsonArray {
                component.items.filter { ArborMiniAppRuntime.visible(it.visibleWhen, state) }.forEach { item ->
                    add(buildJsonObject {
                        put("label", ArborMiniAppRuntime.render(item.label, state))
                        val rendered = ArborMiniAppRuntime.render(item.value, state)
                        val number = rendered.toDoubleOrNull()
                            ?: SafeExpression.evaluate(rendered, ArborMiniAppRuntime.numericState(state)).getOrNull() ?: 0.0
                        put("value", number)
                    })
                }
            })
        })
    })
}.toString()

private val stateJson = Json { ignoreUnknownKeys = true }
private fun encodeState(state: Map<String, String>): String = JsonObject(state.mapValues { JsonPrimitive(it.value) }).toString()
private fun decodeState(source: String): Map<String, String> = runCatching {
    stateJson.parseToJsonElement(source).jsonObject.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }
}.getOrDefault(emptyMap())
private fun compactMini(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else SafeExpression.format(value, 4).trimEnd('0').trimEnd('.')
