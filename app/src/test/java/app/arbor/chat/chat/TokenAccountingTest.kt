package app.arbor.chat.chat

import app.arbor.chat.data.ModelEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TokenAccountingTest {
    @Test
    fun deepSeekV4FlashPricingSeparatesCachedAndMissTokens() {
        val model = ModelEntity(
            providerId = "deepseek",
            modelId = "deepseek-v4-flash",
            displayName = "DeepSeek V4 Flash",
            contextWindow = 1_000_000,
            maxOutputTokens = 384_000,
            inputCacheHitUsdPerMillion = 0.0028,
            inputCacheMissUsdPerMillion = 0.14,
            outputUsdPerMillion = 0.28,
            pricingConfigured = true,
        )

        assertEquals(209_314L, CostCalculator.micros(model, input = 1_000_000, cached = 5_000, output = 250_000))
    }

    @Test
    fun unknownPricingIsNotReportedAsFree() {
        val model = ModelEntity(
            providerId = "custom",
            modelId = "dynamic",
            displayName = "Dynamic pricing",
            contextWindow = 128_000,
            maxOutputTokens = 16_384,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            pricingConfigured = false,
        )

        assertNull(CostCalculator.micros(model, input = 1_000, cached = 0, output = 500))
    }
}
