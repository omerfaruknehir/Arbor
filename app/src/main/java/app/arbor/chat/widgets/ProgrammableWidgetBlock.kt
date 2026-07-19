package app.arbor.chat.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.net.URI
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import app.arbor.chat.ArborApplication
import app.arbor.chat.ui.ArborFadeVisibility

@Composable
fun ProgrammableWidgetBlock(
    source: String,
    onSubmit: (String) -> Unit,
    onSecurityReview: suspend (String) -> String,
    allowHomePinning: Boolean = false,
) {
    val context = LocalContext.current
    val crashReporter = (context.applicationContext as? ArborApplication)?.container?.crashReporter
    val safeRendering by crashReporter?.renderSafeMode?.collectAsState() ?: remember { mutableStateOf(false) }
    if (safeRendering) {
        var sourceExpanded by remember(source) { mutableStateOf(false) }
        Surface(color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Widget paused for crash recovery", fontWeight = FontWeight.SemiBold)
                Text("The message is intact. Arbor is showing it without executing generated UI until you try full rendering again.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { sourceExpanded = !sourceExpanded }) { Text(if (sourceExpanded) "Collapse source" else "Show source") }
                ArborFadeVisibility(sourceExpanded) { Text(source, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { crashReporter?.setRenderSafeMode(false) }) { Text("Try full rendering") }
            }
        }
        return
    }
    val parsed = remember(source) { ArborWidgetParser.parse(source) }
    val definition = parsed.getOrNull()
    if (definition == null) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Invalid widget", fontWeight = FontWeight.SemiBold)
                Text(parsed.exceptionOrNull()?.message ?: "The widget definition could not be read.", style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    val scope = rememberCoroutineScope()
    var pinStatus by remember(source) { mutableStateOf("") }
    var securityExpanded by remember(source) { mutableStateOf(false) }
    var pinReview by remember(source) { mutableStateOf(false) }
    var networkEnabled by remember(source) { mutableStateOf(false) }
    var aiReview by remember(source) { mutableStateOf("") }
    var reviewing by remember(source) { mutableStateOf(false) }
    val security = remember(definition, allowHomePinning) { WidgetSecurityAnalyzer.analyze(definition, allowHomePinning) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(definition.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (definition.description.isNotBlank()) Text(definition.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (allowHomePinning && definition.homeEnabled) IconButton(onClick = { pinReview = true }) {
                    Icon(Icons.Outlined.Home, "Review and add widget to Home screen")
                }
            }
            Surface(
                onClick = { securityExpanded = !securityExpanded },
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(start = 9.dp)) {
                            Text("Security & permissions", fontWeight = FontWeight.SemiBold)
                            Text("${security.risk.name.lowercase().replaceFirstChar(Char::uppercase)} risk • tap for capabilities", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(if (securityExpanded) "Collapse" else "Review", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    ArborFadeVisibility(securityExpanded) {
                        WidgetSecurityDetails(
                            report = security,
                            aiReview = aiReview,
                            reviewing = reviewing,
                            onAiReview = {
                                if (!reviewing) scope.launch {
                                    reviewing = true
                                    aiReview = runCatching { onSecurityReview(source) }.getOrElse { "AI review unavailable: ${it.message.orEmpty()}" }
                                    reviewing = false
                                }
                            },
                        )
                    }
                }
            }
            if (definition.dataSource != null && !networkEnabled) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Live data is off", fontWeight = FontWeight.SemiBold)
                        Text("Review the network capability above, then enable this widget's read-only HTTPS request.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { networkEnabled = true }, modifier = Modifier.alignEnd()) { Text("Allow live data") }
                    }
                }
            }
            when (definition.type) {
                "choice" -> ChoiceWidget(definition, onSubmit)
                "checklist" -> ChecklistWidget(definition, onSubmit)
                "slider" -> SliderWidget(definition, onSubmit)
                "calculator" -> CalculatorWidget(definition, onSubmit)
                "live_data", "stock" -> LiveDataWidget(definition, onSubmit, networkEnabled) { networkEnabled = true }
                "schedule", "prayer_times" -> ScheduleWidget(definition, onSubmit, networkEnabled) { networkEnabled = true }
                "mini_app" -> MiniAppWidgetBlock(definition, source, onSubmit, networkEnabled) { networkEnabled = true }
                "converter" -> ConverterWidget(definition, onSubmit)
                "counter" -> CounterWidget(definition, onSubmit)
                "rating" -> RatingWidget(definition, onSubmit)
                "progress" -> ProgressWidget(definition)
                else -> FormWidget(definition, onSubmit)
            }
            if (pinStatus.isNotBlank()) Text(pinStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (pinReview) AlertDialog(
        onDismissRequest = { pinReview = false },
        title = { Text("Add ${definition.title} to Home?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Review the capabilities that will remain active after pinning.")
                WidgetSecurityDetails(security, aiReview, reviewing, onAiReview = {
                    if (!reviewing) scope.launch {
                        reviewing = true
                        aiReview = runCatching { onSecurityReview(source) }.getOrElse { "AI review unavailable: ${it.message.orEmpty()}" }
                        reviewing = false
                    }
                })
            }
        },
        dismissButton = { OutlinedButton(onClick = { pinReview = false }) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                pinReview = false
                pinStatus = when (WidgetPinning.request(context, source)) {
                    WidgetPinResult.REQUESTED -> "Choose Add in the launcher prompt."
                    WidgetPinResult.UNSUPPORTED -> "This launcher does not support direct widget pinning."
                    WidgetPinResult.INVALID -> "This widget cannot be pinned."
                }
            }) { Text("Continue to launcher") }
        },
    )
}

@Composable
private fun WidgetSecurityDetails(
    report: WidgetSecurityReport,
    aiReview: String,
    reviewing: Boolean,
    onAiReview: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        report.capabilities.forEach { capability ->
            Text("${if (capability.caution) "●" else "✓"} ${capability.title}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium, color = if (capability.caution) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
            Text(capability.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Text("Benefits", fontWeight = FontWeight.SemiBold)
        report.benefits.forEach { Text("+ $it", style = MaterialTheme.typography.bodySmall) }
        Text("Cautions", fontWeight = FontWeight.SemiBold)
        report.cautions.forEach { Text("− $it", style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = onAiReview, enabled = !reviewing, modifier = Modifier.fillMaxWidth()) {
            Text(if (reviewing) "Reviewing…" else if (aiReview.isBlank()) "Ask AI for a second opinion" else "Review again")
        }
        if (aiReview.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp)) {
                Text("Advisory AI review", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(aiReview, style = MaterialTheme.typography.bodySmall)
                Text("The local capability manifest above is authoritative.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChoiceWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    definition.options.forEach { option ->
        OutlinedButton(onClick = { onSubmit("Widget response — ${definition.title}: $option") }, modifier = Modifier.fillMaxWidth()) { Text(option) }
    }
}

@Composable
private fun ChecklistWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    var selected by remember(definition) { mutableStateOf<Set<String>>(emptySet()) }
    definition.options.forEach { option ->
        Row(
            Modifier.fillMaxWidth().clickable { selected = if (option in selected) selected - option else selected + option },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = option in selected, onCheckedChange = { checked -> selected = if (checked) selected + option else selected - option })
            Text(option)
        }
    }
    Button(onClick = { onSubmit("Widget response — ${definition.title}: ${selected.joinToString().ifBlank { "None" }}") }, modifier = Modifier.alignEnd()) { Text("Submit") }
}

@Composable
private fun SliderWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    var value by remember(definition) { mutableDoubleStateOf(definition.value) }
    Slider(
        value = value.toFloat(),
        onValueChange = { raw -> value = (((raw - definition.min) / definition.step).roundToInt() * definition.step + definition.min).coerceIn(definition.min, definition.max) },
        valueRange = definition.min.toFloat()..definition.max.toFloat(),
        steps = (((definition.max - definition.min) / definition.step).roundToInt() - 1).coerceIn(0, 1000),
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(compact(value), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Button(onClick = { onSubmit("Widget response — ${definition.title}: ${compact(value)}") }) { Text("Submit") }
    }
}

@Composable
private fun CalculatorWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    var expression by remember(definition) { mutableStateOf(definition.fields.firstOrNull()?.value?.ifBlank { "" } ?: "") }
    var lastEquation by remember(definition) { mutableStateOf("") }
    val result = remember(expression) { if (expression.isBlank()) null else SafeExpression.evaluate(expression) }
    OutlinedTextField(
        value = expression,
        onValueChange = { expression = it.take(500) },
        label = { Text("Expression") },
        supportingText = { Text("Safe native calculator • no generated code is executed") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        isError = result?.isFailure == true,
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(
            result?.getOrNull()?.let { compact(it) } ?: result?.exceptionOrNull()?.message ?: "Enter an expression",
            Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall,
            color = if (result?.isFailure == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
    if (lastEquation.isNotBlank()) Text(lastEquation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val keys = listOf(
        listOf("C", "⌫", "(", ")"),
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("±", "0", ".", "+"),
        listOf("%", "^", "π", "="),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val action = {
                        expression = when (key) {
                            "C" -> ""
                            "⌫" -> expression.dropLast(1)
                            "=" -> result?.getOrNull()?.let { value ->
                                lastEquation = "$expression = ${compact(value)}"
                                compact(value)
                            } ?: expression
                            "%" -> result?.getOrNull()?.div(100.0)?.let(::compact) ?: expression
                            "±" -> when {
                                expression.isBlank() -> "-"
                                expression.startsWith("-(") && expression.endsWith(")") -> expression.substring(2, expression.length - 1)
                                else -> "-($expression)"
                            }
                            "÷" -> expression + "/"
                            "×" -> expression + "*"
                            "−" -> expression + "-"
                            "π" -> expression + "pi"
                            else -> expression + key
                        }.take(500)
                    }
                    if (key == "=" || key in setOf("+", "−", "×", "÷", "%", "^")) {
                        Button(onClick = action, modifier = Modifier.weight(1f).height(46.dp)) { Text(key) }
                    } else {
                        OutlinedButton(onClick = action, modifier = Modifier.weight(1f).height(46.dp)) { Text(key) }
                    }
                }
            }
        }
    }
    Button(
        onClick = { onSubmit("Widget result — ${definition.title}: $expression = ${compact(result!!.getOrThrow())}") },
        enabled = result?.isSuccess == true,
        modifier = Modifier.alignEnd(),
    ) { Text("Use result") }
}

private data class LiveWidgetState(
    val loading: Boolean = false,
    val values: Map<String, String> = emptyMap(),
    val error: String = "",
    val updatedAtMillis: Long? = null,
)

@Composable
private fun rememberLiveWidgetState(definition: ArborWidgetDefinition, refreshKey: Int, networkEnabled: Boolean): LiveWidgetState {
    var state by remember(definition) { mutableStateOf(LiveWidgetState()) }
    LaunchedEffect(definition.dataSource, refreshKey, networkEnabled) {
        if (!networkEnabled) return@LaunchedEffect
        val source = definition.dataSource ?: return@LaunchedEffect
        state = state.copy(loading = true, error = "")
        state = runCatching { WidgetLiveDataClient.fetch(source) }.fold(
            onSuccess = { LiveWidgetState(values = it.values, updatedAtMillis = it.updatedAtMillis) },
            onFailure = { LiveWidgetState(error = it.message ?: "Refresh failed") },
        )
    }
    return state
}

@Composable
private fun LiveDataWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit, networkEnabled: Boolean, onEnableNetwork: () -> Unit) {
    var refreshKey by remember(definition) { mutableIntStateOf(0) }
    val state = rememberLiveWidgetState(definition, refreshKey, networkEnabled)
    val source = definition.dataSource
    val host = remember(source?.url) { runCatching { URI(source?.url.orEmpty()).host }.getOrNull().orEmpty() }
    if (definition.symbol.isNotBlank()) Text(definition.symbol, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    source?.bindings.orEmpty().forEachIndexed { index, binding ->
        val raw = state.values[binding.id]
        val rendered = raw?.let { formatWidgetBinding(binding, it) } ?: "—"
        Surface(
            color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(binding.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rendered, style = if (index == 0) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    Text(
        when {
            state.loading -> "Refreshing from $host…"
            state.error.isNotBlank() -> state.error
            state.updatedAtMillis != null -> "Live data loaded • automatic refresh every ${source?.refreshMinutes} min"
            else -> "Waiting for live data"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (state.error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { if (networkEnabled) refreshKey++ else onEnableNetwork() }, enabled = !state.loading, modifier = Modifier.weight(1f)) { Text(if (networkEnabled) "Refresh" else "Enable live data") }
        Button(
            onClick = {
                val summary = source?.bindings.orEmpty().joinToString { binding -> "${binding.label}=${state.values[binding.id].orEmpty()}" }
                onSubmit("Widget snapshot — ${definition.title}: $summary")
            },
            enabled = state.values.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) { Text("Use snapshot") }
    }
    Text("Source: $host", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScheduleWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit, networkEnabled: Boolean, onEnableNetwork: () -> Unit) {
    var refreshKey by remember(definition) { mutableIntStateOf(0) }
    val live = rememberLiveWidgetState(definition, refreshKey, networkEnabled)
    val items = definition.schedule.map { item ->
        val liveTime = TIME_VALUE.find(live.values[item.id].orEmpty())?.value
        item.copy(time = liveTime ?: item.time)
    }
    val next = remember(items, definition.timezone) { nextEvent(items, definition.timezone) }
    if (next != null) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("NEXT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("${next.first.label} • ${next.first.time}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(durationLabel(next.second), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    items.forEach { item ->
        Surface(
            color = if (item.id == next?.first?.id) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(item.time, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 58.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.label)
                    if (item.detail.isNotBlank()) Text(item.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (definition.dataSource != null) {
        Text(
            if (live.loading) "Refreshing schedule…" else live.error.ifBlank { "Updates every ${definition.dataSource.refreshMinutes} min" },
            style = MaterialTheme.typography.labelSmall,
            color = if (live.error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { if (networkEnabled) refreshKey++ else onEnableNetwork() }, enabled = !live.loading, modifier = Modifier.fillMaxWidth()) { Text(if (networkEnabled) "Refresh times" else "Enable live data") }
    }
    Button(
        onClick = { onSubmit("Widget schedule — ${definition.title}: " + items.joinToString { "${it.label} ${it.time}" }) },
        modifier = Modifier.alignEnd(),
    ) { Text("Use schedule") }
}

@Composable
private fun ConverterWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    var rawAmount by remember(definition) { mutableStateOf(definition.value.takeIf { it != 0.0 }?.let(::compact) ?: "1") }
    var swapped by remember(definition) { mutableStateOf(false) }
    val amount = rawAmount.toDoubleOrNull()
    val effectiveRate = if (swapped) 1.0 / definition.rate else definition.rate
    val result = amount?.times(effectiveRate)
    val from = if (swapped) definition.toUnit else definition.fromUnit
    val to = if (swapped) definition.fromUnit else definition.toUnit
    OutlinedTextField(
        value = rawAmount,
        onValueChange = { rawAmount = it.take(40) },
        label = { Text(from.ifBlank { "From" }) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(14.dp)) {
                Text(to.ifBlank { "Converted value" }, style = MaterialTheme.typography.labelMedium)
                Text(result?.let(::compact) ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
        }
        IconButton(onClick = { swapped = !swapped }) { Icon(Icons.Outlined.SwapHoriz, "Swap conversion direction") }
    }
    Text("1 ${from.ifBlank { "unit" }} = ${compact(effectiveRate)} ${to.ifBlank { "units" }} • rate supplied in this answer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { onSubmit("Widget result — ${definition.title}: $rawAmount $from = ${compact(result!!)} $to") }, enabled = result != null, modifier = Modifier.alignEnd()) { Text("Use result") }
}

@Composable
private fun CounterWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    var count by remember(definition) { mutableDoubleStateOf(definition.value) }
    Text(compact(count), style = MaterialTheme.typography.displaySmall, modifier = Modifier.alignCenter())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { count -= definition.step }, modifier = Modifier.weight(1f)) { Text("− ${compact(definition.step)}") }
        OutlinedButton(onClick = { count = definition.value }, modifier = Modifier.weight(1f)) { Text("Reset") }
        Button(onClick = { count += definition.step }, modifier = Modifier.weight(1f)) { Text("+ ${compact(definition.step)}") }
    }
    OutlinedButton(onClick = { onSubmit("Widget response — ${definition.title}: ${compact(count)}") }, modifier = Modifier.alignEnd()) { Text("Submit") }
}

@Composable
private fun RatingWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    val maxRating = definition.max.toInt().coerceIn(1, 10)
    var rating by remember(definition) { mutableIntStateOf(definition.value.toInt().coerceIn(0, maxRating)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        (1..maxRating).forEach { score ->
            FilterChip(selected = score <= rating, onClick = { rating = score }, label = { Text("★") })
        }
    }
    Button(onClick = { onSubmit("Widget response — ${definition.title}: $rating/$maxRating") }, modifier = Modifier.alignEnd()) { Text("Submit rating") }
}

@Composable
private fun ProgressWidget(definition: ArborWidgetDefinition) {
    val progress = ((definition.value - definition.min) / (definition.max - definition.min).coerceAtLeast(0.000001)).toFloat().coerceIn(0f, 1f)
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Text("${compact(definition.value)} / ${compact(definition.max)}", style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun FormWidget(definition: ArborWidgetDefinition, onSubmit: (String) -> Unit) {
    val values = remember(definition) { mutableStateMapOf<String, String>().also { state -> definition.fields.forEach { state[it.id] = it.value } } }
    definition.fields.forEach { field ->
        when (field.kind) {
            "number", "text" -> OutlinedTextField(
                value = values[field.id].orEmpty(),
                onValueChange = { values[field.id] = it.take(500) },
                label = { Text(field.label) },
                prefix = field.prefix.takeIf(String::isNotBlank)?.let { { Text(it) } },
                suffix = field.suffix.takeIf(String::isNotBlank)?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == "number") KeyboardType.Decimal else KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            "slider" -> {
                val current = values[field.id]?.toDoubleOrNull()?.coerceIn(field.min, field.max) ?: field.min
                Text("${field.label}: ${compact(current)}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = current.toFloat(),
                    onValueChange = { raw -> values[field.id] = compact((((raw - field.min) / field.step).roundToInt() * field.step + field.min).coerceIn(field.min, field.max)) },
                    valueRange = field.min.toFloat()..field.max.toFloat(),
                )
            }
            "toggle" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(field.label, Modifier.weight(1f))
                Switch(checked = values[field.id]?.toBooleanStrictOrNull() ?: false, onCheckedChange = { values[field.id] = it.toString() })
            }
            "choice" -> {
                Text(field.label, style = MaterialTheme.typography.labelMedium)
                field.options.forEach { option -> FilterChip(selected = values[field.id] == option, onClick = { values[field.id] = option }, label = { Text(option) }) }
            }
        }
    }
    if (definition.outputs.isNotEmpty()) {
        HorizontalDivider()
        val numeric = values.mapValues { it.value.toDoubleOrNull() ?: if (it.value == "true") 1.0 else 0.0 }
        definition.outputs.forEach { output ->
            val result = SafeExpression.evaluate(output.expression, numeric)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(output.label, style = MaterialTheme.typography.labelMedium)
                    Text(
                        result.getOrNull()?.let { output.prefix + SafeExpression.format(it, output.decimals) + output.suffix } ?: result.exceptionOrNull()?.message.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = if (result.isFailure) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
            }
        }
    }
    Button(onClick = {
        onSubmit("Widget response — ${definition.title}: " + definition.fields.joinToString { "${it.label}=${values[it.id].orEmpty()}" })
    }, modifier = Modifier.alignEnd()) { Text("Submit") }
}

private fun Modifier.alignEnd(): Modifier = this.widthIn(min = 96.dp)
private fun Modifier.alignCenter(): Modifier = this.fillMaxWidth()
private fun compact(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else SafeExpression.format(value, 4).trimEnd('0').trimEnd('.')

private fun formatWidgetBinding(binding: ArborWidgetBinding, raw: String): String {
    val numeric = raw.replace(",", "").toDoubleOrNull()
    val value = numeric?.let { SafeExpression.format(it, binding.decimals) } ?: raw.take(100)
    return binding.prefix + value + binding.suffix
}

private fun nextEvent(items: List<ArborWidgetScheduleItem>, timezone: String): Pair<ArborWidgetScheduleItem, Duration>? {
    val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
    val now = ZonedDateTime.now(zone)
    return items.mapNotNull { item ->
        val time = runCatching { LocalTime.parse(item.time) }.getOrNull() ?: return@mapNotNull null
        var occurrence = now.toLocalDate().atTime(time).atZone(zone)
        if (!occurrence.isAfter(now)) occurrence = occurrence.plusDays(1)
        item to Duration.between(now, occurrence)
    }.minByOrNull { it.second }
}

private fun durationLabel(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(0)
    return if (minutes >= 60) "In ${minutes / 60}h ${minutes % 60}m" else "In ${minutes}m"
}

private val TIME_VALUE = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
