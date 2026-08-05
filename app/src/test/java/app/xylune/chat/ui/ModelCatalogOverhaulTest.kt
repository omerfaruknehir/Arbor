package app.xylune.chat.ui

import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import app.xylune.chat.settings.modelPreferenceKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogOverhaulTest {
    private val openRouter = ProviderEntity(
        id = "openrouter",
        displayName = "OpenRouter",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai/api/v1",
    )

    @Test
    fun `large catalogs are searchable and capability filtered`() {
        val models = (1..350).map { index ->
            model(
                id = "author/model-$index",
                name = if (index == 317) "Needle Vision" else "Model $index",
                vision = index == 317,
            )
        }

        val search = filteredModelChoices(
            providers = listOf(openRouter),
            models = models,
            query = "needle vision",
            providerId = null,
            filter = ModelPickerFilter.ALL,
            favoriteKeys = emptySet(),
            recentKeys = emptyList(),
        )
        val vision = filteredModelChoices(
            providers = listOf(openRouter),
            models = models,
            query = "",
            providerId = null,
            filter = ModelPickerFilter.VISION,
            favoriteKeys = emptySet(),
            recentKeys = emptyList(),
        )

        assertEquals(listOf("author/model-317"), search.map { it.model.modelId })
        assertEquals(listOf("author/model-317"), vision.map { it.model.modelId })
    }

    @Test
    fun `selected favorites and recent models sort ahead of the long tail`() {
        val first = model("a/first", "First")
        val favorite = model("z/favorite", "Favorite")
        val selected = model("m/selected", "Selected")
        val choices = filteredModelChoices(
            providers = listOf(openRouter),
            models = listOf(first, favorite, selected),
            query = "",
            providerId = null,
            filter = ModelPickerFilter.ALL,
            favoriteKeys = setOf(modelPreferenceKey(openRouter.id, favorite.modelId)),
            recentKeys = listOf(modelPreferenceKey(openRouter.id, first.modelId)),
            selectedKey = modelPreferenceKey(openRouter.id, selected.modelId),
        )

        assertEquals(selected.modelId, choices.first().model.modelId)
        assertEquals(favorite.modelId, choices[1].model.modelId)
        assertTrue(choices.size == 3)
    }

    private fun model(id: String, name: String, vision: Boolean = false) = ModelEntity(
        providerId = openRouter.id,
        modelId = id,
        displayName = name,
        contextWindow = 128_000,
        maxOutputTokens = 16_384,
        inputCacheHitUsdPerMillion = 0.0,
        inputCacheMissUsdPerMillion = 0.0,
        outputUsdPerMillion = 0.0,
        supportsVision = vision,
    )
}
