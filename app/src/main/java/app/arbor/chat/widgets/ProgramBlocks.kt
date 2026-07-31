package app.arbor.chat.widgets

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SnippetBlock(
    source: String,
    onSubmit: (String) -> Unit,
) {
    val parsed = remember(source) { ArborProgramParser.parse(source, ArborProgramSurface.SNIPPET) }
    val definition = parsed.getOrNull()
    if (definition == null) {
        InvalidProgramBlock("Invalid snippet", parsed.exceptionOrNull()?.message)
        return
    }
    val state = remember(source) { mutableStateMapOf<String, String>().also { it.putAll(definition.state) } }
    ProgramSurface(definition.title, definition.description) {
        ProgramNodeView(
            node = definition.ui,
            state = state,
            interactive = true,
            onStateChange = { key, value -> state[key] = value.take(1_000) },
            onAction = { actionId ->
                val transition = ArborProgramRuntime.apply(actionId, definition, state)
                state.clear(); state.putAll(transition.state)
                transition.submitMessage?.takeIf(String::isNotBlank)?.let(onSubmit)
            },
        )
    }
}

@Composable
fun WidgetInstallBlock(
    source: String,
) {
    val context = LocalContext.current
    val parsed = remember(source) { ArborProgramParser.parse(source, ArborProgramSurface.WIDGET) }
    val definition = parsed.getOrNull()
    if (definition == null) {
        InvalidProgramBlock("Invalid home widget", parsed.exceptionOrNull()?.message)
        return
    }

    val state = remember(source) { mutableStateMapOf<String, String>().also { it.putAll(definition.state) } }
    val networkOrigins = remember(source) { mutableStateMapOf<String, Boolean>() }
    definition.capabilities.filter { it.type == "network" }.flatMap { it.origins }.forEach { origin ->
        networkOrigins.putIfAbsent(origin, false)
    }
    var locationGranted by remember(source) { mutableStateOf(hasLocationPermission(context, precise = false)) }
    var preciseLocationGranted by remember(source) { mutableStateOf(hasLocationPermission(context, precise = true)) }
    var folderUri by remember(source) { mutableStateOf<Uri?>(null) }
    var backgroundGranted by remember(source) { mutableStateOf(false) }
    var permissionsExpanded by remember(source) { mutableStateOf(true) }
    var pinStatus by remember(source) { mutableStateOf("") }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true || hasLocationPermission(context, false)
        preciseLocationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasLocationPermission(context, true)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val write = definition.capabilities.any { it.type == "folder" && it.mode == "read_write" }
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            folderUri = uri
        }
    }

    val capabilityReady = definition.capabilities.all { capability ->
        when (capability.type) {
            "network" -> capability.origins.all { networkOrigins[it] == true }
            "location" -> if (capability.accuracy == "precise") preciseLocationGranted else locationGranted
            "folder" -> folderUri != null
            "background_refresh" -> backgroundGranted
            else -> false
        }
    }

    ProgramSurface(definition.title, definition.description) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Home-screen preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                ProgramNodeView(
                    node = definition.ui,
                    state = state,
                    interactive = false,
                    onStateChange = { _, _ -> },
                    onAction = { actionId ->
                        val transition = ArborProgramRuntime.apply(actionId, definition, state)
                        state.clear(); state.putAll(transition.state)
                    },
                )
            }
        }

        Surface(
            onClick = { permissionsExpanded = !permissionsExpanded },
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Security, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text("Widget capabilities", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (capabilityReady) "All requested grants are ready" else "Review every persistent grant before adding",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(if (permissionsExpanded) "Hide" else "Review", color = MaterialTheme.colorScheme.primary)
                }
                AnimatedVisibility(permissionsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (definition.capabilities.isEmpty()) {
                            Text("This widget requests no network, location, folder, or background capability.", style = MaterialTheme.typography.bodySmall)
                        }
                        definition.capabilities.forEach { capability ->
                            CapabilityGrantRow(
                                capability = capability,
                                networkOrigins = networkOrigins,
                                locationGranted = locationGranted,
                                preciseLocationGranted = preciseLocationGranted,
                                folderUri = folderUri,
                                backgroundGranted = backgroundGranted,
                                onNetworkChange = { origin, value -> networkOrigins[origin] = value },
                                onLocation = {
                                    val permissions = if (capability.accuracy == "precise") {
                                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
                                    } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    locationLauncher.launch(permissions)
                                },
                                onFolder = { folderPicker.launch(folderUri) },
                                onBackgroundChange = { backgroundGranted = it },
                            )
                        }
                        Text(
                            "Grants belong to this widget instance. Network access is restricted to the listed HTTPS origins; a folder grant stays inside the selected document tree.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val grants = WidgetCapabilityGrants(
                    networkOrigins = networkOrigins.filterValues { it }.keys,
                    location = when {
                        preciseLocationGranted -> WidgetLocationGrant.PRECISE
                        locationGranted -> WidgetLocationGrant.APPROXIMATE
                        else -> WidgetLocationGrant.NONE
                    },
                    folderUri = folderUri?.toString(),
                    folderWrite = definition.capabilities.any { it.type == "folder" && it.mode == "read_write" },
                    backgroundRefresh = backgroundGranted,
                )
                pinStatus = when (WidgetPinning.request(context, source, grants)) {
                    WidgetPinResult.REQUESTED -> "Choose Add in the launcher prompt."
                    WidgetPinResult.UNSUPPORTED -> "This launcher does not support direct widget pinning."
                    WidgetPinResult.INVALID -> "The widget definition or grants are invalid."
                }
            },
            enabled = capabilityReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.AddToHomeScreen, null)
            Text(" Add to Home screen")
        }
        if (pinStatus.isNotBlank()) Text(pinStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CapabilityGrantRow(
    capability: ArborWidgetCapabilityRequest,
    networkOrigins: Map<String, Boolean>,
    locationGranted: Boolean,
    preciseLocationGranted: Boolean,
    folderUri: Uri?,
    backgroundGranted: Boolean,
    onNetworkChange: (String, Boolean) -> Unit,
    onLocation: () -> Unit,
    onFolder: () -> Unit,
    onBackgroundChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (capability.type) {
                    "network" -> Icons.Outlined.Public
                    "location" -> Icons.Outlined.LocationOn
                    "folder" -> Icons.Outlined.FolderOpen
                    else -> Icons.Outlined.Refresh
                },
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(capabilityTitle(capability), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(capability.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (capability.type) {
            "network" -> capability.origins.forEach { origin ->
                Row(Modifier.fillMaxWidth().clickable { onNetworkChange(origin, networkOrigins[origin] != true) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = networkOrigins[origin] == true, onCheckedChange = { onNetworkChange(origin, it) })
                    Text(origin, style = MaterialTheme.typography.bodySmall)
                }
            }
            "location" -> OutlinedButton(onClick = onLocation, modifier = Modifier.fillMaxWidth()) {
                Text(
                    when {
                        capability.accuracy == "precise" && preciseLocationGranted -> "Precise location granted"
                        locationGranted -> "Approximate location granted"
                        else -> "Grant ${capability.accuracy} location"
                    },
                )
            }
            "folder" -> OutlinedButton(onClick = onFolder, modifier = Modifier.fillMaxWidth()) {
                Text(folderUri?.lastPathSegment?.let { "Folder selected: ${it.takeLast(40)}" } ?: "Choose one folder")
            }
            "background_refresh" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Allow scheduled refresh", Modifier.weight(1f))
                Switch(checked = backgroundGranted, onCheckedChange = onBackgroundChange)
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ProgramSurface(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ProgramNodeView(
    node: ArborProgramNode,
    state: Map<String, String>,
    interactive: Boolean,
    onStateChange: (String, String) -> Unit,
    onAction: (String) -> Unit,
) {
    if (!ArborProgramRuntime.visible(node.visibleWhen, state)) return
    val modifier = nodeModifier(node.style)
    when (node.type) {
        "column" -> Surface(color = nodeColor(node.style.background), shape = RoundedCornerShape(node.style.cornerRadius.dp), modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(node.style.padding.dp), verticalArrangement = Arrangement.spacedBy(node.style.gap.dp)) {
                node.children.forEach { ProgramNodeView(it, state, interactive, onStateChange, onAction) }
            }
        }
        "row" -> Surface(color = nodeColor(node.style.background), shape = RoundedCornerShape(node.style.cornerRadius.dp), modifier = modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(node.style.padding.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(node.style.gap.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                node.children.forEach { child ->
                    Box(Modifier.width(if (child.style.weight > 0f) 180.dp else 140.dp)) {
                        ProgramNodeView(child, state, interactive, onStateChange, onAction)
                    }
                }
            }
        }
        "stack" -> Box(modifier.fillMaxWidth()) { node.children.forEach { ProgramNodeView(it, state, interactive, onStateChange, onAction) } }
        "text" -> Text(
            ArborProgramRuntime.render(node.text.ifBlank { node.value }, state),
            color = nodeTextColor(node.style.foreground),
            fontWeight = nodeFontWeight(node.style.emphasis),
            style = if (node.style.fontSize > 0) MaterialTheme.typography.bodyMedium.copy(fontSize = node.style.fontSize.sp) else MaterialTheme.typography.bodyMedium,
            modifier = modifier,
        )
        "metric" -> Column(modifier) {
            if (node.label.isNotBlank()) Text(ArborProgramRuntime.render(node.label, state), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                ArborProgramRuntime.render(node.value.ifBlank { node.text }, state),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = nodeTextColor(node.style.foreground),
            )
        }
        "button" -> Button(onClick = { if (interactive) onAction(node.action) }, enabled = interactive, modifier = modifier.fillMaxWidth()) {
            Text(ArborProgramRuntime.render(node.label, state))
        }
        "toggle" -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ArborProgramRuntime.render(node.label.ifBlank { node.value }, state), Modifier.weight(1f))
            Switch(
                checked = ArborProgramRuntime.truthy(state[node.value]),
                onCheckedChange = { checked ->
                    if (interactive) {
                        onStateChange(node.value, checked.toString())
                        node.action.takeIf(String::isNotBlank)?.let(onAction)
                    }
                },
                enabled = interactive,
            )
        }
        "choice" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (node.label.isNotBlank()) Text(ArborProgramRuntime.render(node.label, state), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                node.options.forEach { option ->
                    val value = ArborProgramRuntime.render(option.value, state)
                    FilterChip(
                        selected = state[node.value] == value,
                        onClick = {
                            if (interactive) {
                                onStateChange(node.value, value)
                                option.action.ifBlank { node.action }.takeIf(String::isNotBlank)?.let(onAction)
                            }
                        },
                        enabled = interactive,
                        label = { Text(ArborProgramRuntime.render(option.label, state)) },
                    )
                }
            }
        }
        "input" -> OutlinedTextField(
            value = state[node.value].orEmpty(),
            onValueChange = { if (interactive) onStateChange(node.value, it) },
            enabled = interactive,
            label = { Text(ArborProgramRuntime.render(node.label.ifBlank { node.value }, state)) },
            keyboardOptions = KeyboardOptions(keyboardType = if (node.min != 0.0 || node.max != 100.0) KeyboardType.Decimal else KeyboardType.Text),
            modifier = modifier.fillMaxWidth(),
        )
        "slider" -> Column(modifier) {
            val raw = state[node.value]?.toDoubleOrNull()?.coerceIn(node.min, node.max) ?: node.min
            Text("${ArborProgramRuntime.render(node.label.ifBlank { node.value }, state)}: ${formatNumber(raw, node.decimals)}")
            Slider(
                value = raw.toFloat(),
                onValueChange = { value ->
                    if (interactive) {
                        val snapped = (((value - node.min) / node.step).roundToInt() * node.step + node.min).coerceIn(node.min, node.max)
                        onStateChange(node.value, ArborProgramRuntime.formatCompact(snapped))
                    }
                },
                valueRange = node.min.toFloat()..node.max.toFloat(),
                enabled = interactive,
            )
        }
        "progress" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (node.label.isNotBlank()) Text(ArborProgramRuntime.render(node.label, state), style = MaterialTheme.typography.labelMedium)
            val value = ArborProgramRuntime.render(node.value, state).toDoubleOrNull()?.coerceIn(node.min, node.max) ?: node.min
            LinearProgressIndicator(progress = { ((value - node.min) / (node.max - node.min).coerceAtLeast(0.000001)).toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        "list" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.items.forEach { item ->
                Surface(
                    onClick = { item.action.takeIf(String::isNotBlank)?.let { if (interactive) onAction(it) } },
                    enabled = interactive && item.action.isNotBlank(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(ArborProgramRuntime.render(item.label, state), fontWeight = FontWeight.SemiBold)
                        if (item.value.isNotBlank()) Text(ArborProgramRuntime.render(item.value, state))
                        if (item.detail.isNotBlank()) Text(ArborProgramRuntime.render(item.detail, state), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        "chart" -> ProgramChart(node, state, modifier.fillMaxWidth().height(150.dp))
        "divider" -> HorizontalDivider(modifier)
        "spacer" -> Spacer(modifier.height((node.style.padding.takeIf { it > 0 } ?: 12).dp))
    }
}

@Composable
private fun ProgramChart(node: ArborProgramNode, state: Map<String, String>, modifier: Modifier) {
    val values = node.items.mapNotNull { item -> ArborProgramRuntime.render(item.value, state).toFloatOrNull() }
    if (values.size < 2) return
    val min = values.minOrNull() ?: 0f
    val max = values.maxOrNull() ?: 1f
    val range = (max - min).takeIf { it > 0f } ?: 1f
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.clip(MaterialTheme.shapes.medium)) {
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1).coerceAtLeast(1)
            val y = size.height - ((value - min) / range * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, SolidColor(color), style = Stroke(width = 4f))
    }
}

@Composable
private fun InvalidProgramBlock(title: String, message: String?) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(message ?: "The generated program could not be read.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun nodeModifier(style: ArborProgramStyle): Modifier = Modifier
private fun nodeFontWeight(emphasis: String): FontWeight = when (emphasis) {
    "strong" -> FontWeight.Bold
    "medium" -> FontWeight.SemiBold
    else -> FontWeight.Normal
}

@Composable
private fun nodeColor(value: String): Color = when (value) {
    "primary" -> MaterialTheme.colorScheme.primaryContainer
    "secondary" -> MaterialTheme.colorScheme.secondaryContainer
    "tertiary" -> MaterialTheme.colorScheme.tertiaryContainer
    "surface" -> MaterialTheme.colorScheme.surface
    "surface_variant" -> MaterialTheme.colorScheme.surfaceVariant
    "error" -> MaterialTheme.colorScheme.errorContainer
    "transparent", "" -> Color.Transparent
    else -> parseColor(value) ?: Color.Transparent
}

@Composable
private fun nodeTextColor(value: String): Color = when (value) {
    "primary" -> MaterialTheme.colorScheme.primary
    "secondary" -> MaterialTheme.colorScheme.secondary
    "tertiary" -> MaterialTheme.colorScheme.tertiary
    "on_surface", "" -> MaterialTheme.colorScheme.onSurface
    "error" -> MaterialTheme.colorScheme.error
    else -> parseColor(value) ?: MaterialTheme.colorScheme.onSurface
}

private fun parseColor(value: String): Color? = runCatching {
    val hex = value.removePrefix("#")
    val argb = when (hex.length) {
        6 -> (0xFF000000L or hex.toLong(16)).toInt()
        8 -> hex.toLong(16).toInt()
        else -> return null
    }
    Color(argb)
}.getOrNull()

private fun formatNumber(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals.coerceIn(0, 8)}f", value)

private fun hasLocationPermission(context: android.content.Context, precise: Boolean): Boolean {
    val permission = if (precise) Manifest.permission.ACCESS_FINE_LOCATION else Manifest.permission.ACCESS_COARSE_LOCATION
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun capabilityTitle(value: ArborWidgetCapabilityRequest): String = when (value.type) {
    "network" -> "Network: ${value.origins.joinToString()}"
    "location" -> "${value.accuracy.replaceFirstChar(Char::uppercase)} location"
    "folder" -> "Selected folder (${value.mode.replace('_', ' ')})"
    "background_refresh" -> "Background refresh"
    else -> value.type
}
