package app.arbor.chat.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiOAuthUsageParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesWhamUsageWindowsCreditsAndAdditionalLimits() {
        val root = json.parseToJsonElement(
            """
            {
              "plan_type": "plus",
              "rate_limit": {
                "allowed": true,
                "limit_reached": false,
                "primary_window": {
                  "used_percent": 37.5,
                  "limit_window_seconds": 18000,
                  "reset_at": 1785120000
                },
                "secondary_window": {
                  "used_percent": 61,
                  "window_duration_mins": 10080,
                  "resets_at": 1785600000
                }
              },
              "credits": {
                "balance": "12.50",
                "has_credits": true,
                "unlimited": false
              },
              "additional_rate_limits": [
                {
                  "limit_id": "code-review",
                  "limit_name": "Code review",
                  "rate_limit": {
                    "primary_window": {
                      "used_percent": 10,
                      "limit_window_seconds": 86400,
                      "reset_at": 1785200000
                    }
                  }
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val usage = OpenAiOAuthUsageParser.parse(root, fetchedAtEpochMs = 1234L)

        assertEquals("plus", usage.planType)
        assertTrue(usage.allowed == true)
        assertFalse(usage.limitReached == true)
        assertEquals(37.5, usage.primary?.usedPercent ?: -1.0, 0.001)
        assertEquals(18_000L, usage.primary?.windowDurationSeconds)
        assertEquals(604_800L, usage.secondary?.windowDurationSeconds)
        assertEquals("12.50", usage.creditsBalance)
        assertTrue(usage.hasCredits == true)
        assertEquals(1, usage.additionalLimits.size)
        assertEquals("Code review", usage.additionalLimits.single().name)
        assertNotNull(usage.additionalLimits.single().primary)
        assertEquals(1234L, usage.fetchedAtEpochMs)
    }

    @Test(expected = ProviderProtocolException::class)
    fun rejectsUnknownUsageShape() {
        OpenAiOAuthUsageParser.parse(json.parseToJsonElement("{\"unrelated\":true}").jsonObject)
    }
}
