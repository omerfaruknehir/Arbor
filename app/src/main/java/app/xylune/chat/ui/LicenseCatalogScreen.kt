package app.xylune.chat.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val LICENSE_ASSET_ROOT = "licenses"

@Serializable
private data class OfflineLicenseCatalog(
    val schemaVersion: Int,
    val components: List<OfflineLicenseComponent>,
)

@Serializable
private data class OfflineLicenseComponent(
    val id: String,
    val name: String,
    val version: String,
    val category: String,
    val description: String,
    val projectUrl: String,
    val icon: String,
    val coordinates: List<String> = emptyList(),
    val licenses: List<OfflineLicenseDocument>,
    val notice: String? = null,
)

@Serializable
private data class OfflineLicenseDocument(
    val name: String,
    val spdx: String? = null,
    val file: String,
)

private val licenseCatalogJson = Json {
    ignoreUnknownKeys = true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicenseCatalogSettingsPage() {
    val context = LocalContext.current
    val catalogResult = remember {
        runCatching {
            context.assets.open("$LICENSE_ASSET_ROOT/catalog.json")
                .bufferedReader()
                .use { licenseCatalogJson.decodeFromString<OfflineLicenseCatalog>(it.readText()) }
        }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<OfflineLicenseComponent?>(null) }
    val catalog = catalogResult.getOrNull()

    SettingsPage {
        Text(
            uiText("Everything listed here is embedded in this build and available without a network connection."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        DevelopmentDisclosureCard()

        if (catalog == null) {
            LicenseCatalogError()
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text(uiText("Search libraries or licenses")) },
                shape = RoundedCornerShape(22.dp),
            )

            val normalizedQuery = query.trim()
            val filtered = remember(catalog.components, normalizedQuery) {
                if (normalizedQuery.isEmpty()) {
                    catalog.components
                } else {
                    catalog.components.filter { component ->
                        buildString {
                            append(component.name)
                            append(' ')
                            append(component.description)
                            append(' ')
                            append(component.category)
                            append(' ')
                            append(component.coordinates.joinToString(" "))
                            append(' ')
                            append(component.licenses.joinToString(" ") { "${it.name} ${it.spdx.orEmpty()}" })
                        }.contains(normalizedQuery, ignoreCase = true)
                    }
                }
            }

            Text(
                uiText("${filtered.size} of ${catalog.components.size} components"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (filtered.isEmpty()) {
                EmptyLicenseSearch()
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column {
                        filtered.forEachIndexed { index, component ->
                            if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            LicenseComponentRow(component, onClick = { selected = component })
                        }
                    }
                }
            }
        }

        Spacer(Modifier.padding(bottom = 24.dp))
    }

    selected?.let { component ->
        LicenseDetailSheet(
            component = component,
            onDismiss = { selected = null },
        )
    }
}

@Composable
private fun DevelopmentDisclosureCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                uiText("Development disclosure & disclaimer"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                uiText("Xylune was made with full vibe coding: features and changes were primarily directed in natural language and implemented with AI-assisted coding tools. It may contain serious defects."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                uiText("The app is provided “AS IS”, without warranties. Use it at your own risk. To the maximum extent permitted by applicable law, the author and contributors are not responsible for data loss, device damage, account loss, charges, security incidents, or other consequences arising from its use, modification, or distribution. Review the source and keep backups before relying on it."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun LicenseComponentRow(
    component: OfflineLicenseComponent,
    onClick: () -> Unit,
) {
    val haptics = rememberXyluneHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics.selection()
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LicenseIcon(component)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(component.name, fontWeight = FontWeight.SemiBold)
            Text(
                uiText("${component.category} · ${component.version}"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                component.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Text(
                uiText(component.licenses.joinToString(" · ") { it.spdx ?: it.name }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Outlined.Description,
            contentDescription = uiText("Read license"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicenseIcon(component: OfflineLicenseComponent, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBytes = remember(context, component.icon) {
        runCatching {
            context.assets.open("$LICENSE_ASSET_ROOT/${component.icon}").use { it.readBytes() }
        }.getOrNull()
    }
    val isSvg = component.icon.endsWith(".svg", ignoreCase = true)
    val rasterImage = remember(iconBytes, isSvg) {
        if (!isSvg && iconBytes != null) {
            BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size)?.asImageBitmap()
        } else {
            null
        }
    }
    val svgRequest = remember(context, component.icon, iconBytes, isSvg) {
        if (isSvg && iconBytes != null) {
            ImageRequest.Builder(context)
                .data(iconBytes)
                .decoderFactory(SvgDecoder.Factory())
                .crossfade(false)
                .build()
        } else {
            null
        }
    }

    Surface(
        modifier = modifier.size(48.dp),
        shape = RoundedCornerShape(15.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        if (component.id == "xylune") {
            XyluneMark(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentDescription = uiText("Xylune"),
            )
        } else {
            when {
                svgRequest != null -> SubcomposeAsyncImage(
                    model = svgRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(9.dp),
                    contentScale = ContentScale.Fit,
                    loading = {},
                    error = { LicenseIconFallback(component.name) },
                )
                rasterImage != null -> Image(
                    bitmap = rasterImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(9.dp),
                    contentScale = ContentScale.Fit,
                )
                else -> LicenseIconFallback(component.name)
            }
        }
    }
}

@Composable
private fun LicenseIconFallback(componentName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = uiText(componentName.firstOrNull()?.uppercase() ?: "?"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LicenseDetailSheet(
    component: OfflineLicenseComponent,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val documents = remember(component.id) {
        component.licenses.map { document ->
            document to runCatching {
                context.assets.open("$LICENSE_ASSET_ROOT/${document.file}")
                    .bufferedReader()
                    .use { it.readText() }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LicenseIcon(component, Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        component.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        component.version,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(component.description, style = MaterialTheme.typography.bodyMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                component.licenses.forEach { document ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            document.spdx ?: document.name,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            OutlinedButton(onClick = { uriHandler.openUri(component.projectUrl) }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp))
                Text(uiText("Project website"), Modifier.padding(start = 8.dp))
            }

            if (component.coordinates.isNotEmpty()) {
                Text(uiText("Included modules"), fontWeight = FontWeight.SemiBold)
                SelectionContainer {
                    Text(
                        uiText(component.coordinates.joinToString("\n")),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            component.notice?.takeIf { it.isNotBlank() }?.let { notice ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.large,
                ) {
                    Text(
                        notice,
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            documents.forEachIndexed { index, (document, textResult) ->
                if (index > 0) HorizontalDivider()
                Text(
                    document.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                textResult.fold(
                    onSuccess = { text ->
                        SelectionContainer {
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onFailure = {
                        Text(
                            uiText("The embedded license document could not be opened."),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyLicenseSearch() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.Search, null)
            Text(uiText("No matching components"), fontWeight = FontWeight.SemiBold)
            Text(
                uiText("Try a library name such as SQLCipher or a license such as Apache-2.0."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseCatalogError() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Column {
                Text(
                    uiText("License catalog unavailable"),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    uiText("This build is missing its generated offline notices."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
