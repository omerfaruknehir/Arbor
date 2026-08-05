package app.xylune.chat.provider

import app.xylune.chat.data.ThinkingEffort
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelDiscoveryTest {
    @Test
    fun `dedicated image catalog is merged with general OpenRouter models`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val json = when (chain.request().url.encodedPath) {
                "/api/v1/models" -> """{"data":[{"id":"vendor/chat","name":"Chat","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"}},{"id":"vendor/both","name":"Both","architecture":{"input_modalities":["text"],"output_modalities":["text"]},"supported_parameters":["tools"],"pricing":{"prompt":"0.000001","completion":"0.000002"}}]}"""
                "/api/v1/images/models" -> """{"data":[{"id":"vendor/image-only","name":"Image Only","architecture":{"input_modalities":["text"],"output_modalities":["image"]},"supported_parameters":{"aspect_ratio":{"type":"enum","values":["1:1"]}}},{"id":"vendor/both","name":"Both Images","architecture":{"input_modalities":["text"],"output_modalities":["image"]},"pricing":{"prompt":"0","completion":"0"}}]}"""
                else -> error("Unexpected discovery path: ${chain.request().url.encodedPath}")
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(json.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val models = ModelDiscoveryService(oauth = null, client = client).discover(
            kind = app.xylune.chat.data.ProviderKind.OPENAI_COMPATIBLE,
            rawBaseUrl = "https://openrouter.ai/api/v1",
            apiKey = "key",
            customHeadersJson = "{}",
        )

        assertTrue(models.any { it.id == "vendor/chat" })
        assertTrue(models.any { it.id == "vendor/image-only" && it.supportsImageGeneration == true })
        val both = models.single { it.id == "vendor/both" }
        assertTrue(both.supportsImageGeneration == true)
        assertTrue(both.supportsTools == true)
        assertEquals(1.0, both.inputCacheMissUsdPerMillion!!, 0.000001)
        assertEquals(2.0, both.outputUsdPerMillion!!, 0.000001)
    }

    @Test
    fun `general catalog still works when dedicated image catalog is unavailable`() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val response = if (chain.request().url.encodedPath == "/api/v1/models") {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"data":[{"id":"vendor/chat","architecture":{"input_modalities":["text"],"output_modalities":["text"]}}]}""".toResponseBody("application/json".toMediaType()))
            } else {
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body("{}".toResponseBody("application/json".toMediaType()))
            }
            response.build()
        }.build()

        val models = ModelDiscoveryService(oauth = null, client = client).discover(
            kind = app.xylune.chat.data.ProviderKind.OPENAI_COMPATIBLE,
            rawBaseUrl = "https://openrouter.ai/api/v1",
            apiKey = "key",
            customHeadersJson = "{}",
        )

        assertEquals(listOf("vendor/chat"), models.map { it.id })
    }

    @Test
    fun `catalog metadata is parsed instead of guessed from model names`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "data": [
                {
                  "id": "vendor/opaque-name",
                  "name": "Opaque Reasoner",
                  "description": "Authoritative metadata test",
                  "created": 1770000000,
                  "context_length": 200000,
                  "architecture": {
                    "input_modalities": ["text", "image", "file"],
                    "output_modalities": ["text"]
                  },
                  "supported_parameters": ["tools", "reasoning", "temperature"],
                  "top_provider": {"max_completion_tokens": 32000},
                  "pricing": {
                    "prompt": "0.0000015",
                    "completion": "0.000006",
                    "input_cache_read": "0.0000003"
                  },
                  "reasoning": {
                    "supported_efforts": ["low", "high", "xhigh"],
                    "default_effort": "high",
                    "default_enabled": true,
                    "supports_max_tokens": true,
                    "mandatory": false
                  }
                },
                {
                  "id": "vendor/embed-only",
                  "architecture": {
                    "input_modalities": ["text"],
                    "output_modalities": ["embeddings"]
                  }
                }
              ]
            }
            """.trimIndent(),
        ).jsonObject

        val models = ModelDiscoveryService(oauth = null).parseDataModels(
            payload["data"]!!.jsonArray,
            "https://openrouter.ai/api/v1",
        )

        assertEquals(1, models.size)
        val model = models.single()
        assertEquals("Opaque Reasoner", model.displayName)
        assertEquals(200_000, model.contextWindow)
        assertEquals(32_000, model.maxOutputTokens)
        assertEquals(1.5, model.inputCacheMissUsdPerMillion!!, 0.000001)
        assertEquals(6.0, model.outputUsdPerMillion!!, 0.000001)
        assertEquals(0.3, model.inputCacheHitUsdPerMillion!!, 0.000001)
        assertTrue(model.supportsVision == true)
        assertTrue(model.supportsFiles == true)
        assertTrue(model.supportsTools == true)
        assertTrue(model.supportsThinking == true)
        assertFalse(model.supportsImageGeneration == true)
        assertEquals(listOf(ThinkingEffort.LOW, ThinkingEffort.HIGH, ThinkingEffort.XHIGH), model.reasoningEfforts)
        assertEquals(ThinkingEffort.HIGH, model.reasoningDefaultEffort)
        assertTrue(model.reasoningDefaultEnabled)
        assertTrue(model.reasoningSupportsMaxTokens)
        assertEquals("OpenRouter", model.metadataSource)
    }
}
