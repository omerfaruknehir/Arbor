package app.xylune.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.settings.modelPreferenceKey
import java.util.Locale

internal enum class ModelPickerFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    THINKING("Thinking"),
    TOOLS("Tools"),
    VISION("Vision"),
    FILES("Files"),
    IMAGE("Image output"),
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
    filter: ModelPickerFilter,
    favoriteKeys: Set<String>,
    recentKeys: List<String>,
    selectedKey: String? = null,
): List<ModelPickerChoice> {
    val providersById = providers.associateBy(ProviderEntity::id)
    val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter(String::isNotBlank)
    val recentRanks = recentKeys.withIndex().associate { it.value to it.index }
    return models.asSequence()
        .mapNotNull { model -> providersById[model.providerId]?.let { ModelPickerChoice(it, model) } }
        .filter { choice -> providerId == null || choice.provider.id == providerId }
        .filter { choice ->
            val key = modelPreferenceKey(choice.provider.id, choice.model.modelId)
            when (filter) {
                ModelPickerFilter.ALL -> true
                ModelPickerFilter.FAVORITES -> key in favoriteKeys
                ModelPickerFilter.RECENT -> key in recentRanks
                ModelPickerFilter.THINKING -> choice.model.supportsThinking
                ModelPickerFilter.TOOLS -> choice.model.supportsTools
                ModelPickerFilter.VISION -> choice.model.supportsVision
                ModelPickerFilter.FILES -> choice.model.supportsFiles
                ModelPickerFilter.IMAGE -> choice.model.supportsImageGeneration
                ModelPickerFilter.FREE -> choice.model.pricingConfigured &&
                    choice.model.inputCacheMissUsdPerMillion == 0.0 && choice.model.outputUsdPerMillion == 0.0
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var query by remember { mutableStateOf("") }
    var providerId by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(ModelPickerFilter.ALL) }
    val providerIds = remember(providers) { providers.mapTo(hashSetOf()) { it.id } }
    val availableModelCount = remember(models, providerIds) { models.count { it.providerId in providerIds } }
    val selectedKey = selectedProviderId?.let { provider ->
        selectedModelId?.let { model -> modelPreferenceKey(provider, model) }
    }
    val choices = remember(providers, models, query, providerId, filter, favoriteKeys, recentKeys, selectedKey) {
        filteredModelChoices(
            providers = providers,
            models = models,
            query = query,
            providerId = providerId,
            filter = filter,
            favoriteKeys = favoriteKeys,
            recentKeys = recentKeys,
            selectedKey = selectedKey,
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Choose a model", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Search $availableModelCount models by name, ID, provider, or description",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close model picker") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("Search models") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = providerId == null, onClick = { providerId = null }, label = { Text("All providers") })
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
                ModelPickerFilter.entries.forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option.label) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${choices.size} result${if (choices.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                if (favoriteKeys.isNotEmpty() && filter != ModelPickerFilter.FAVORITES) {
                    AssistChip(onClick = { filter = ModelPickerFilter.FAVORITES }, label = { Text("${favoriteKeys.size} starred") })
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
                                    "${choice.provider.displayName} · ${choice.model.modelId}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    choice.model.pickerSummary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingContent = if (selected) ({
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
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
                                Text("No matching models", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Clear a filter or try a model name, author, or capability.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(18.dp))
        }
    }
}

private val ModelEntity.pickerSummary: String
    get() = buildList {
        add("${contextWindow.compactTokens()} context")
        add("${maxOutputTokens.compactTokens()} output")
        if (supportsThinking) add(if (reasoningMandatory) "Thinking always on" else "Thinking")
        if (supportsTools) add("Tools")
        if (supportsVision) add("Vision")
        if (supportsFiles) add("Files")
        if (supportsImageGeneration) add("Image output")
        if (pricingConfigured && inputCacheMissUsdPerMillion == 0.0 && outputUsdPerMillion == 0.0) add("Free")
    }.joinToString(" · ")

private fun Int.compactTokens(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000 -> "${this / 1_000}K"
    else -> toString()
}
