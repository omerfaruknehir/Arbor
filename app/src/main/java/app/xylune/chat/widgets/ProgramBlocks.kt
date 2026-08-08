package app.xylune.chat.widgets

import app.xylune.chat.ui.uiText

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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
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
    val parsed = remember(source) { XyluneProgramParser.parse(source, XyluneProgramSurface.SNIPPET) }
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
                val transition = XyluneProgramRuntime.apply(actionId, definition, state)
                state.clear(); state.putAll(transition.state)
                transition.submitMessage?.takeIf(String::isNotBlank)?.let(onSubmit)
            },
            minimumFontSp = 14,
        )
    }
}

@Composable
fun WidgetInstallBlock(
    source: String,
) {
    val context = LocalContext.current
    val parsed = remember(source) { XyluneProgramParser.parse(source, XyluneProgramSurface.WIDGET) }
    val definition = parsed.getOrNull()
    if (definition == null) {
        InvalidProgramBlock("Invalid home widget", parsed.exceptionOrNull()?.message)
        return
    }

    val state = remember(source) { mutableStateMapOf<String, String>().also { it.putAll(definition.state) } }
    val networkOrigins = remember(source) { mutableStateMapOf<String, Boolean>() }
    val requiredOrigins = remember(source) {
        definition.capabilities.filter { it.type == "network" }.flatMap { it.origins }.distinct()
    }
    requiredOrigins.forEach { origin -> networkOrigins.putIfAbsent(origin, false) }
    var locationGranted by remember(source) { mutableStateOf(hasLocationPermission(context, precise = false)) }
    var preciseLocationGranted by remember(source) { mutableStateOf(hasLocationPermission(context, precise = true)) }
    var folderUri by remember(source) { mutableStateOf<Uri?>(null) }
    var backgroundGranted by remember(source) { mutableStateOf(false) }
    var permissionsExpanded by remember(source) { mutableStateOf(definition.capabilities.isNotEmpty()) }
    var previewStatus by remember(source) { mutableStateOf("Compiled and tested before display") }
    var pinStatus by remember(source) { mutableStateOf("") }
    var pinStatusError by remember(source) { mutableStateOf(false) }

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

    fun granted(capability: XyluneWidgetCapabilityRequest): Boolean = capabilityGranted(
        capability = capability,
        networkOrigins = networkOrigins,
        locationGranted = locationGranted,
        preciseLocationGranted = preciseLocationGranted,
        folderUri = folderUri,
        backgroundGranted = backgroundGranted,
    )

    val capabilityReady = definition.capabilities.all(::granted)
    val grantedCount = definition.capabilities.count(::granted)
    val missing = definition.capabilities.filterNot(::granted)
    val visibleActionCount = widgetActionCount(definition.ui).coerceAtMost(4)
    val hasLiveData = definition.dataSources.isNotEmpty()

    ProgramSurface(definition.title, definition.description) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WidgetInfoPill("Compiled & tested")
            WidgetInfoPill(if (hasLiveData) "Live data" else "Works offline")
            WidgetInfoPill("$visibleActionCount action${if (visibleActionCount == 1) "" else "s"}")
            definition.refreshMinutes?.let { WidgetInfoPill("Refreshes every $it min") }
            if (definition.capabilities.isEmpty()) WidgetInfoPill("No permissions")
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("Interactive preview"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            uiText("This is the compiled launcher program. Local controls run here; live HTTP data and JSON bindings were preflighted before this card appeared."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        state.clear()
                        state.putAll(definition.state)
                        previewStatus = "Preview reset"
                    }) {
                        Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp))
                        Text(uiText(" Reset"))
                    }
                }
                ProgramNodeView(
                    node = definition.ui,
                    state = state,
                    interactive = true,
                    onStateChange = { key, value -> state[key] = value.take(1_000) },
                    onAction = { actionId ->
                        val transition = XyluneProgramRuntime.apply(actionId, definition, state)
                        state.clear()
                        state.putAll(transition.state)
                        previewStatus = when {
                            transition.refreshSources.isNotEmpty() -> "Live refresh will run from the installed widget."
                            transition.folderWrites.isNotEmpty() -> "Folder writing will run only after the selected-folder grant."
                            transition.openRoute != null -> "This action will open the matching Xylune screen after installation."
                            transition.submitMessage != null -> "Submit actions are chat-only and are ignored by Home widgets."
                            else -> "Preview updated"
                        }
                    },
                    minimumFontSp = 15,
                )
                if (previewStatus.isNotBlank()) {
                    Text(previewStatus, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
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
                    Icon(
                        if (capabilityReady) Icons.Outlined.CheckCircle else Icons.Outlined.Security,
                        null,
                        tint = if (capabilityReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(uiText("Permissions and data access"), fontWeight = FontWeight.SemiBold)
                        Text(
                            uiText(when {
                                definition.capabilities.isEmpty() -> "Nothing extra is required"
                                capabilityReady -> "All $grantedCount requested grants are ready"
                                else -> "$grantedCount of ${definition.capabilities.size} grants ready"
                            }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (permissionsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(permissionsExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (definition.capabilities.isEmpty()) {
                            Text(
                                uiText("This widget stays on-device and requests no network, location, folder, or scheduled-refresh access."),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (requiredOrigins.isNotEmpty() && requiredOrigins.any { networkOrigins[it] != true }) {
                            OutlinedButton(
                                onClick = { requiredOrigins.forEach { networkOrigins[it] = true } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Outlined.Public, null, modifier = Modifier.size(18.dp))
                                Text(uiText(" Allow ${requiredOrigins.size} listed network origin${if (requiredOrigins.size == 1) "" else "s"}"))
                            }
                        }
                        definition.capabilities.forEach { capability ->
                            CapabilityGrantRow(
                                capability = capability,
                                granted = granted(capability),
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
                            uiText("Every grant belongs only to this pinned copy. Network access is limited to the listed HTTPS origins, and folder access cannot leave the selected document tree."),
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
                when (WidgetPinning.request(context, source, grants)) {
                    WidgetPinResult.REQUESTED -> {
                        pinStatusError = false
                        pinStatus = "Launcher confirmation opened. Tap Add to place the configured widget."
                    }
                    WidgetPinResult.UNSUPPORTED -> {
                        pinStatusError = true
                        pinStatus = "This launcher blocks Android's one-tap widget pinning flow, so Xylune cannot safely transfer this configured copy."
                    }
                    WidgetPinResult.INVALID -> {
                        pinStatusError = true
                        pinStatus = "The widget or one of its required grants is no longer valid. Review the permissions and try again."
                    }
                }
            },
            enabled = capabilityReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.AddToHomeScreen, null)
            Text(uiText(if (capabilityReady) " Add configured widget" else " Review required permissions"))
        }
        if (!capabilityReady && missing.isNotEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    uiText(" Still needed: ${missing.joinToString { capabilityShortTitle(it) }}"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (pinStatus.isNotBlank()) {
            Surface(
                color = if (pinStatusError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    pinStatus,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pinStatusError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun CapabilityGrantRow(
    capability: XyluneWidgetCapabilityRequest,
    granted: Boolean,
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            WidgetGrantBadge(granted)
        }
        when (capability.type) {
            "network" -> capability.origins.forEach { origin ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable { onNetworkChange(origin, networkOrigins[origin] != true) },
                ) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = networkOrigins[origin] == true, onCheckedChange = { onNetworkChange(origin, it) })
                        Column(Modifier.weight(1f)) {
                            Text(origin, style = MaterialTheme.typography.bodySmall)
                            Text(uiText("HTTPS GET only"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            "location" -> OutlinedButton(onClick = onLocation, modifier = Modifier.fillMaxWidth()) {
                Text(
                    uiText(when {
                        capability.accuracy == "precise" && preciseLocationGranted -> "Precise location allowed"
                        locationGranted -> "Approximate location allowed"
                        else -> "Allow ${capability.accuracy} location"
                    }),
                )
            }
            "folder" -> OutlinedButton(onClick = onFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FolderOpen, null, modifier = Modifier.size(18.dp))
                Text(uiText(folderUri?.lastPathSegment?.let { " ${it.takeLast(44)}" } ?: " Choose one folder"))
            }
            "background_refresh" -> Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(uiText("Keep data current"), fontWeight = FontWeight.Medium)
                        Text(uiText("Android may delay work to protect battery."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = backgroundGranted, onCheckedChange = onBackgroundChange)
                }
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
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun ProgramNodeView(
    node: XyluneProgramNode,
    state: Map<String, String>,
    interactive: Boolean,
    onStateChange: (String, String) -> Unit,
    onAction: (String) -> Unit,
    minimumFontSp: Int,
) {
    if (!XyluneProgramRuntime.visible(node.visibleWhen, state)) return
    val modifier = nodeModifier(node.style)
    when (node.type) {
        "column" -> Surface(color = nodeColor(node.style.background), shape = RoundedCornerShape(node.style.cornerRadius.dp), modifier = modifier.fillMaxWidth()) {
            Column(Modifier.padding(node.style.padding.dp), verticalArrangement = Arrangement.spacedBy(node.style.gap.dp)) {
                node.children.forEach { ProgramNodeView(it, state, interactive, onStateChange, onAction, minimumFontSp) }
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
                        ProgramNodeView(child, state, interactive, onStateChange, onAction, minimumFontSp)
                    }
                }
            }
        }
        "stack" -> Box(modifier.fillMaxWidth()) { node.children.forEach { ProgramNodeView(it, state, interactive, onStateChange, onAction, minimumFontSp) } }
        "text" -> Text(
            XyluneProgramRuntime.render(node.text.ifBlank { node.value }, state),
            color = nodeTextColor(node.style.foreground),
            fontWeight = nodeFontWeight(node.style.emphasis),
            style = if (node.style.fontSize > 0) MaterialTheme.typography.bodyLarge.copy(fontSize = node.style.fontSize.coerceAtLeast(minimumFontSp).sp) else MaterialTheme.typography.bodyLarge,
            modifier = modifier,
        )
        "metric" -> Column(modifier) {
            if (node.label.isNotBlank()) Text(XyluneProgramRuntime.render(node.label, state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                XyluneProgramRuntime.render(node.value.ifBlank { node.text }, state),
                style = if (node.style.fontSize > 0) MaterialTheme.typography.headlineMedium.copy(fontSize = node.style.fontSize.coerceAtLeast(28).sp) else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = nodeTextColor(node.style.foreground),
            )
        }
        "button" -> Button(onClick = { if (interactive) onAction(node.action) }, enabled = interactive, modifier = modifier.fillMaxWidth()) {
            Text(XyluneProgramRuntime.render(node.label, state), style = MaterialTheme.typography.labelLarge)
        }
        "toggle" -> Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = XyluneProgramRuntime.truthy(state[node.value]),
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
            if (node.label.isNotBlank()) Text(XyluneProgramRuntime.render(node.label, state), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                node.options.forEach { option ->
                    val value = XyluneProgramRuntime.render(option.value, state)
                    FilterChip(
                        selected = state[node.value] == value,
                        onClick = {
                            if (interactive) {
                                onStateChange(node.value, value)
                                option.action.ifBlank { node.action }.takeIf(String::isNotBlank)?.let(onAction)
                            }
                        },
                        enabled = interactive,
                        label = { Text(XyluneProgramRuntime.render(option.label, state), style = MaterialTheme.typography.labelLarge) },
                    )
                }
            }
        }
        "input" -> OutlinedTextField(
            value = state[node.value].orEmpty(),
            onValueChange = { if (interactive) onStateChange(node.value, it) },
            enabled = interactive,
            label = { Text(XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state)) },
            keyboardOptions = KeyboardOptions(keyboardType = if (node.min != 0.0 || node.max != 100.0) KeyboardType.Decimal else KeyboardType.Text),
            modifier = modifier.fillMaxWidth(),
        )
        "slider" -> Column(modifier) {
            val raw = state[node.value]?.toDoubleOrNull()?.coerceIn(node.min, node.max) ?: node.min
            Text(uiText("${XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state)}: ${formatNumber(raw, node.decimals)}"), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = raw.toFloat(),
                onValueChange = { value ->
                    if (interactive) {
                        val snapped = (((value - node.min) / node.step).roundToInt() * node.step + node.min).coerceIn(node.min, node.max)
                        onStateChange(node.value, XyluneProgramRuntime.formatCompact(snapped))
                    }
                },
                valueRange = node.min.toFloat()..node.max.toFloat(),
                enabled = interactive,
            )
        }
        "progress" -> Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (node.label.isNotBlank()) Text(XyluneProgramRuntime.render(node.label, state), style = MaterialTheme.typography.bodyMedium)
            val value = XyluneProgramRuntime.render(node.value, state).toDoubleOrNull()?.coerceIn(node.min, node.max) ?: node.min
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
                    Column(Modifier.padding(12.dp)) {
                        Text(XyluneProgramRuntime.render(item.label, state), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        if (item.value.isNotBlank()) Text(XyluneProgramRuntime.render(item.value, state), style = MaterialTheme.typography.bodyMedium)
                        if (item.detail.isNotBlank()) Text(XyluneProgramRuntime.render(item.detail, state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        "chart" -> ProgramChart(node, state, modifier.fillMaxWidth().height(180.dp))
        "divider" -> HorizontalDivider(modifier)
        "spacer" -> Spacer(modifier.height((node.style.padding.takeIf { it > 0 } ?: 12).dp))
    }
}

@Composable
private fun ProgramChart(node: XyluneProgramNode, state: Map<String, String>, modifier: Modifier) {
    val values = node.items.mapNotNull { item -> XyluneProgramRuntime.render(item.value, state).toFloatOrNull() }
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
            Text(uiText(message ?: "The generated program could not be read."), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WidgetInfoPill(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WidgetGrantBadge(granted: Boolean) {
    Surface(
        color = if (granted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(100.dp),
    ) {
        Text(
            uiText(if (granted) "Ready" else "Required"),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (granted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun capabilityGranted(
    capability: XyluneWidgetCapabilityRequest,
    networkOrigins: Map<String, Boolean>,
    locationGranted: Boolean,
    preciseLocationGranted: Boolean,
    folderUri: Uri?,
    backgroundGranted: Boolean,
): Boolean = when (capability.type) {
    "network" -> capability.origins.all { networkOrigins[it] == true }
    "location" -> if (capability.accuracy == "precise") preciseLocationGranted else locationGranted
    "folder" -> folderUri != null
    "background_refresh" -> backgroundGranted
    else -> false
}

private fun widgetActionCount(node: XyluneProgramNode): Int = when (node.type) {
    "button", "toggle" -> if (node.action.isNotBlank()) 1 else 0
    "choice" -> node.options.count { it.action.isNotBlank() }
    "list" -> node.items.count { it.action.isNotBlank() }
    else -> 0
} + node.children.sumOf(::widgetActionCount)

private fun capabilityShortTitle(value: XyluneWidgetCapabilityRequest): String = when (value.type) {
    "network" -> if (value.origins.size == 1) value.origins.first() else "${value.origins.size} network origins"
    "location" -> "${value.accuracy} location"
    "folder" -> if (value.mode == "read_write") "folder read/write" else "folder access"
    "background_refresh" -> "scheduled refresh"
    else -> value.type
}

private fun nodeModifier(style: XyluneProgramStyle): Modifier = Modifier
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

private fun capabilityTitle(value: XyluneWidgetCapabilityRequest): String = when (value.type) {
    "network" -> "Network: ${value.origins.joinToString()}"
    "location" -> "${value.accuracy.replaceFirstChar(Char::uppercase)} location"
    "folder" -> "Selected folder (${value.mode.replace('_', ' ')})"
    "background_refresh" -> "Background refresh"
    else -> value.type
}
