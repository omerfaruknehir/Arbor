package app.arbor.chat.provider

import app.arbor.chat.data.ProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class DiscoveredModel(
    val id: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsThinking: Boolean? = null,
)

class ModelDiscoveryService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun discover(
        kind: ProviderKind,
        rawBaseUrl: String,
        apiKey: String,
        customHeadersJson: String,
    ): List<DiscoveredModel> = withContext(Dispatchers.IO) {
        val baseUrl = ProviderEndpointPolicy.validate(rawBaseUrl)
        val customHeaders = parseHeaders(customHeadersJson)
        val collected = mutableListOf<DiscoveredModel>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        for (page in 0 until MAX_PAGES) {
            val endpoint = "$baseUrl/models".toHttpUrl().newBuilder().apply {
                when (kind) {
                    ProviderKind.OPENAI_COMPATIBLE -> Unit
                    ProviderKind.ANTHROPIC -> {
                        addQueryParameter("limit", "100")
                        cursor?.let { addQueryParameter("after_id", it) }
                    }
                    ProviderKind.GEMINI -> {
                        addQueryParameter("pageSize", MAX_MODELS.toString())
                        cursor?.let { addQueryParameter("pageToken", it) }
                    }
                }
            }.build()
            val body = fetchPage(kind, endpoint, apiKey, customHeaders)
            collected += when (kind) {
                ProviderKind.OPENAI_COMPATIBLE, ProviderKind.ANTHROPIC -> parseDataModels(body["data"] as? JsonArray)
                ProviderKind.GEMINI -> parseGeminiModels(body["models"] as? JsonArray)
            }
            if (collected.size >= MAX_MODELS || kind == ProviderKind.OPENAI_COMPATIBLE) break
            val next = when (kind) {
                ProviderKind.OPENAI_COMPATIBLE -> null
                ProviderKind.ANTHROPIC -> if (body["has_more"]?.jsonPrimitive?.booleanOrNull == true) {
                    body["last_id"]?.jsonPrimitive?.contentOrNull
                } else null
                ProviderKind.GEMINI -> body["nextPageToken"]?.jsonPrimitive?.contentOrNull
            }?.takeIf(String::isNotBlank)
            if (next == null || !seenCursors.add(next)) break
            cursor = next
        }
        collected.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }.take(MAX_MODELS)
            .ifEmpty { throw IllegalStateException("The provider returned no usable chat models") }
    }

    private suspend fun fetchPage(
        kind: ProviderKind,
        endpoint: HttpUrl,
        apiKey: String,
        customHeaders: Map<String, String>,
    ): JsonObject {
        val request = Request.Builder().url(endpoint).get().apply {
            when (kind) {
                ProviderKind.OPENAI_COMPATIBLE -> if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
                ProviderKind.ANTHROPIC -> {
                    if (apiKey.isNotBlank()) header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                }
                ProviderKind.GEMINI -> if (apiKey.isNotBlank()) header("x-goog-api-key", apiKey)
            }
            customHeaders.forEach { (name, value) -> header(name, value) }
        }.build()
        return client.newCall(request).useCancellable { response ->
            if (!response.isSuccessful) {
                val detail = response.body?.readErrorSnippet()?.trim().orEmpty()
                val safeDetail = if (response.code in setOf(401, 403)) response.message else detail.take(1_000).ifBlank { response.message }
                throw ProviderHttpException(response.code, "Model discovery failed (${response.code}): $safeDetail")
            }
            val responseBody = response.body ?: throw IllegalStateException("The provider returned an empty model list")
            val source = responseBody.source()
            source.request(MAX_DISCOVERY_BYTES + 1L)
            require(source.buffer.size <= MAX_DISCOVERY_BYTES) { "The provider's model list is unexpectedly large" }
            ProviderJson.parseToJsonElement(source.buffer.readUtf8()).jsonObject
        }
    }

    private fun parseDataModels(values: JsonArray?): List<DiscoveredModel> = values.orEmpty().mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id = model["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (id.isBlank()) return@mapNotNull null
        val name = model["display_name"]?.jsonPrimitive?.contentOrNull
            ?: model["displayName"]?.jsonPrimitive?.contentOrNull
            ?: humanize(id)
        DiscoveredModel(
            id = id,
            displayName = name,
            contextWindow = model["inputTokenLimit"]?.jsonPrimitive?.intOrNull,
            maxOutputTokens = model["outputTokenLimit"]?.jsonPrimitive?.intOrNull,
            supportsThinking = model["thinking"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    private fun parseGeminiModels(values: JsonArray?): List<DiscoveredModel> = values.orEmpty().mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val methods = (model["supportedGenerationMethods"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            .orEmpty()
        if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
        val id = model["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/")?.trim().orEmpty()
        if (id.isBlank()) return@mapNotNull null
        val name = model["displayName"]?.jsonPrimitive?.contentOrNull ?: humanize(id)
        DiscoveredModel(
            id = id,
            displayName = name,
            contextWindow = model["inputTokenLimit"]?.jsonPrimitive?.intOrNull,
            maxOutputTokens = model["outputTokenLimit"]?.jsonPrimitive?.intOrNull,
            supportsThinking = model["thinking"]?.jsonPrimitive?.booleanOrNull,
        )
    }

    private fun humanize(id: String): String = id.substringAfterLast('/').replace('-', ' ').replace('_', ' ')
        .split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private companion object {
        const val MAX_MODELS = 500
        const val MAX_PAGES = 10
        const val MAX_DISCOVERY_BYTES = 2L * 1024 * 1024
    }
}
