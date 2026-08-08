package app.xylune.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.provider.ImageInputMode
import app.xylune.chat.provider.imageModelCapabilities
import app.xylune.chat.settings.modelPreferenceKey
import java.util.Locale

internal enum class ModelPickerMode(val label: String) {
    CHAT("Chat"),
    IMAGE("Images"),
}

internal enum class ModelPickerFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    THINKING("Thinking"),
    TOOLS("Tools"),
    VISION("Vision"),
    FILES("Files"),
    IMAGE("Image"),
    FREE("Free"),
}

internal data class ModelPickerChoice(
    val provider: ProviderEntity,
    val model: ModelEntity,
)

internal fun filteredModelChoices(
    providers: List<ProviderEntity>,
    models: List<ModelEntity>,
    query: String,
    providerId: String?,
    filters: Set<ModelPickerFilter>,
    favoriteKeys: Set<String>,
    recentKeys: List<String>,
    selectedKey: String? = null,
    mode: ModelPickerMode = ModelPickerMode.CHAT,
): List<ModelPickerChoice> {
    val providersById = providers.associateBy(ProviderEntity::id)
    val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
    val recentRanks = recentKeys.withIndex().associate { it.value to it.index }
    val activeFilters = filters - ModelPickerFilter.ALL
    return models.asSequence()
        .mapNotNull { model -> providersById[model.providerId]?.let { ModelPickerChoice(it, model) } }
        .filter { choice ->
            when (mode) {
                ModelPickerMode.CHAT -> !choice.model.supportsImageGeneration
                ModelPickerMode.IMAGE -> choice.model.supportsImageGeneration
            }
        }
        .filter { choice -> providerId == null || choice.provider.id == providerId }
        .filter { choice ->
            val key = modelPreferenceKey(choice.provider.id, choice.model.modelId)
            activeFilters.all { filter ->
                when (filter) {
                    ModelPickerFilter.ALL -> true
                    ModelPickerFilter.FAVORITES -> key in favoriteKeys
                    ModelPickerFilter.RECENT -> key in recentRanks
                    ModelPickerFilter.THINKING -> choice.model.supportsThinking
                    ModelPickerFilter.TOOLS -> choice.model.supportsTools
                    ModelPickerFilter.VISION -> choice.model.supportsVision
                    ModelPickerFilter.FILES -> choice.model.supportsFiles
                    ModelPickerFilter.IMAGE -> choice.model.supportsImageGeneration
                    ModelPickerFilter.FREE -> choice.model.isActuallyFree
                }
            }
        }
        .filter { choice ->
            if (terms.isEmpty()) true else {
                val haystack = listOf(
                    choice.model.displayName,
                    choice.model.modelId,
                    choice.model.description,
                    choice.provider.displayName,
                ).joinToString(" ").lowercase(Locale.ROOT)
                terms.all(haystack::contains)
            }
        }
        .sortedWith(
            compareByDescending<ModelPickerChoice> {
                modelPreferenceKey(it.provider.id, it.model.modelId) == selectedKey
            }.thenByDescending {
                modelPreferenceKey(it.provider.id, it.model.modelId) in favoriteKeys
            }.thenBy {
                recentRanks[modelPreferenceKey(it.provider.id, it.model.modelId)] ?: Int.MAX_VALUE
            }.thenBy { it.provider.displayName.lowercase(Locale.ROOT) }
                .thenBy { it.model.displayName.lowercase(Locale.ROOT) },
        )
        .toList()
}

@Composable
internal fun ModelPickerSheet(
    providers: List<ProviderEntity>,
    models: List<ModelEntity>,
    selectedProviderId: String?,
    selectedModelId: String?,
    favoriteKeys: Set<String>,
    recentKeys: List<String>,
    onToggleFavorite: (String, String) -> Unit,
    onSelect: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initiallySelected = remember(models, selectedProviderId, selectedModelId) {
        models.firstOrNull { it.providerId == selectedProviderId && it.modelId == selectedModelId }
    }
    var mode by remember(selectedProviderId, selectedModelId) {
        mutableStateOf(if (initiallySelected?.supportsImageGeneration == true) ModelPickerMode.IMAGE else ModelPickerMode.CHAT)
    }
    var query by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf<String?>(null) }
    var filters by remember { mutableStateOf(emptySet<ModelPickerFilter>()) }
    val providerIds = remember(providers) { providers.mapTo(hashSetOf()) { it.id } }
    val chatModelCount = remember(models, providerIds) {
        models.count { it.providerId in providerIds && !it.supportsImageGeneration }
    }
    val imageModelCount = remember(models, providerIds) {
        models.count { it.providerId in providerIds && it.supportsImageGeneration }
    }
    val selectedKey = selectedProviderId?.let { provider ->
        selectedModelId?.let { model -> modelPreferenceKey(provider, model) }
    }
    val choices = remember(providers, models, query, providerId, filters, favoriteKeys, recentKeys, selectedKey, mode) {
        filteredModelChoices(
            providers = providers,
            models = models,
            query = query,
            providerId = providerId,
            filters = filters,
            favoriteKeys = favoriteKeys,
            recentKeys = recentKeys,
            selectedKey = selectedKey,
            mode = mode,
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            uiText(if (mode == ModelPickerMode.IMAGE) "Choose an image model" else "Choose a chat model"),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            uiText(if (mode == ModelPickerMode.IMAGE) {
                                "Image generation and editing models are kept separate from chat models"
                            } else {
                                "Search chat models by name, ID, provider, or description"
                            }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, uiText("Close model picker")) }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = mode == ModelPickerMode.CHAT,
                        onClick = {
                            mode = ModelPickerMode.CHAT
                            filters = filters - setOf(ModelPickerFilter.VISION, ModelPickerFilter.FILES, ModelPickerFilter.IMAGE)
                        },
                        label = { Text(uiText("Chat · $chatModelCount")) },
                        leadingIcon = { Icon(Icons.Outlined.Psychology, null) },
                    )
                    FilterChip(
                        selected = mode == ModelPickerMode.IMAGE,
                        onClick = {
                            mode = ModelPickerMode.IMAGE
                            filters = filters.filterTo(mutableSetOf()) {
                                it in setOf(ModelPickerFilter.FAVORITES, ModelPickerFilter.RECENT)
                            }
                        },
                        label = { Text(uiText("Images · $imageModelCount")) },
                        leadingIcon = { Icon(Icons.Outlined.Image, null) },
                    )
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text(uiText(if (mode == ModelPickerMode.IMAGE) "Search image models" else "Search models")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = providerId == null, onClick = { providerId = null }, label = { Text(uiText("All providers")) })
                    providers.forEach { provider ->
                        FilterChip(
                            selected = providerId == provider.id,
                            onClick = { providerId = provider.id },
                            label = { Text(provider.displayName) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filters.isEmpty(),
                        onClick = { filters = emptySet() },
                        label = { Text(uiText(if (filters.isEmpty()) "All" else "Clear")) },
                    )
                    val visibleFilters = if (mode == ModelPickerMode.IMAGE) {
                        listOf(ModelPickerFilter.FAVORITES, ModelPickerFilter.RECENT)
                    } else {
                        ModelPickerFilter.entries.filterNot { it in setOf(ModelPickerFilter.ALL, ModelPickerFilter.IMAGE) }
                    }
                    visibleFilters.forEach { option ->
                        FilterChip(
                            selected = option in filters,
                            onClick = {
                                filters = if (option in filters) filters - option else filters + option
                            },
                            label = { Text(uiText(option.label)) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        uiText(buildString {
                            append(choices.size).append(" result").append(if (choices.size == 1) "" else "s")
                            if (filters.isNotEmpty()) append(" · ").append(filters.size).append(" filters")
                        }),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    if (favoriteKeys.isNotEmpty() && ModelPickerFilter.FAVORITES !in filters) {
                        AssistChip(
                            onClick = { filters = filters + ModelPickerFilter.FAVORITES },
                            label = { Text(uiText("${favoriteKeys.size} starred")) },
                        )
                    }
                }
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(
                        items = choices,
                        key = { choice -> modelPreferenceKey(choice.provider.id, choice.model.modelId) },
                    ) { choice ->
                        val key = modelPreferenceKey(choice.provider.id, choice.model.modelId)
                        val selected = key == selectedKey
                        ListItem(
                            headlineContent = {
                                Text(
                                    choice.model.displayName,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        uiText("${choice.provider.displayName} · ${choice.model.modelId}"),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        choice.pickerSummary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingContent = if (selected) ({
                                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }) else if (choice.model.supportsImageGeneration) ({
                                Icon(Icons.Outlined.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }) else null,
                            trailingContent = {
                                IconButton(onClick = { onToggleFavorite(choice.provider.id, choice.model.modelId) }) {
                                    Icon(
                                        if (key in favoriteKeys) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        if (key in favoriteKeys) "Remove favorite" else "Add favorite",
                                        tint = if (key in favoriteKeys) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onSelect(choice.provider.id, choice.model.modelId)
                                onDismiss()
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)
                                else MaterialTheme.colorScheme.surface,
                            ),
                        )
                        HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    }
                    if (choices.isEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            ) {
                                Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        uiText(if (mode == ModelPickerMode.IMAGE) "No matching image models" else "No matching chat models"),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        uiText(if (mode == ModelPickerMode.IMAGE) {
                                            "Try another provider or clear the current filters."
                                        } else {
                                            "Clear a filter or try a model name, author, or capability."
                                        }),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val ModelPickerChoice.pickerSummary: String
    get() {
        if (model.supportsImageGeneration) {
            val capabilities = imageModelCapabilities(provider, model)
            return buildList {
                add(
                    when (capabilities?.inputMode) {
                        ImageInputMode.REQUIRED -> "Edit images"
                        ImageInputMode.OPTIONAL -> "Generate + edit"
                        else -> "Generate images"
                    },
                )
                capabilities?.maxInputImages?.takeIf { it > 0 }?.let { max ->
                    add("up to $max reference image${if (max == 1) "" else "s"}")
                }
                if (capabilities?.supportsProgressivePreview == true) add("Live previews")
                add("Image billing")
            }.joinToString(" · ")
        }
        return buildList {
            add("${model.contextWindow.compactTokens()} context")
            add("${model.maxOutputTokens.compactTokens()} output")
            if (model.supportsThinking) add(if (model.reasoningMandatory) "Thinking always on" else "Thinking")
            if (model.supportsTools) add("Tools")
            if (model.supportsVision) add("Vision")
            if (model.supportsFiles) add("Files")
            if (model.isActuallyFree) add("Free")
        }.joinToString(" · ")
    }

internal val ModelEntity.isActuallyFree: Boolean
    get() = !supportsImageGeneration && pricingConfigured &&
        inputCacheMissUsdPerMillion == 0.0 && outputUsdPerMillion == 0.0

private fun Int.compactTokens(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000 -> "${this / 1_000}K"
    else -> toString()
}
