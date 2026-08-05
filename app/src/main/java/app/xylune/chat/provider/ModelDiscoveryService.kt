package app.xylune.chat.provider

import app.xylune.chat.data.ProviderKind
import app.xylune.chat.data.ThinkingEffort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
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
    val supportsVision: Boolean? = null,
    val supportsFiles: Boolean? = null,
    val supportsTools: Boolean? = null,
    val supportsImageGeneration: Boolean? = null,
    val description: String = "",
    val createdAtEpochSeconds: Long = 0,
    val inputCacheHitUsdPerMillion: Double? = null,
    val inputCacheMissUsdPerMillion: Double? = null,
    val outputUsdPerMillion: Double? = null,
    val reasoningMetadataAvailable: Boolean = false,
    val reasoningEfforts: List<ThinkingEffort> = emptyList(),
    val reasoningDefaultEffort: ThinkingEffort? = null,
    val reasoningDefaultEnabled: Boolean = false,
    val reasoningMandatory: Boolean = false,
    val reasoningSupportsMaxTokens: Boolean = false,
    val metadataSource: String = "",
)

class ModelDiscoveryService(
    private val oauth: OpenAiOAuthManager?,
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
        providerId: String? = null,
    ): List<DiscoveredModel> = withContext(Dispatchers.IO) {
        if (kind == ProviderKind.OPENAI_OAUTH) {
            val oauthManager = requireNotNull(oauth) { "OAuth model discovery requires an OAuth manager" }
            return@withContext oauthManager.modelCatalog(providerId ?: OpenAiOAuthManager.PROVIDER_ID, forceRefresh = true).map { model ->
                DiscoveredModel(
                    id = model.id,
                    displayName = model.displayName,
                    contextWindow = model.contextWindow,
                    maxOutputTokens = model.maxOutputTokens,
                    supportsThinking = model.supportsThinking,
                    supportsVision = true,
                    supportsFiles = false,
                    supportsTools = true,
                    supportsImageGeneration = model.supportsImageGeneration,
                )
            }
        }
        val baseUrl = ProviderEndpointPolicy.validate(rawBaseUrl)
        val customHeaders = parseHeaders(customHeadersJson)
        val collected = mutableListOf<DiscoveredModel>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        for (page in 0 until MAX_PAGES) {
            val endpoint = "$baseUrl/models".toHttpUrl().newBuilder().apply {
                when (kind) {
                    ProviderKind.OPENAI_COMPATIBLE -> if (ModelRequestPolicy.isOpenRouterBaseUrl(baseUrl)) {
                        // The OpenRouter endpoint otherwise defaults to text output and
                        // silently hides image-generation models from the catalog.
                        addQueryParameter("output_modalities", "all")
                        addQueryParameter("limit", MAX_MODELS.toString())
                    }
                    ProviderKind.OPENAI_OAUTH -> error("OAuth discovery is handled before paging")
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
                ProviderKind.OPENAI_COMPATIBLE, ProviderKind.ANTHROPIC -> parseDataModels(body["data"] as? JsonArray, baseUrl)
                ProviderKind.OPENAI_OAUTH -> error("OAuth discovery is handled before paging")
                ProviderKind.GEMINI -> parseGeminiModels(body["models"] as? JsonArray)
            }
            if (collected.size >= MAX_MODELS || kind == ProviderKind.OPENAI_COMPATIBLE) break
            val next = when (kind) {
                ProviderKind.OPENAI_COMPATIBLE -> null
                ProviderKind.OPENAI_OAUTH -> null
                ProviderKind.ANTHROPIC -> if (body["has_more"]?.jsonPrimitive?.booleanOrNull == true) {
                    body["last_id"]?.jsonPrimitive?.contentOrNull
                } else null
                ProviderKind.GEMINI -> body["nextPageToken"]?.jsonPrimitive?.contentOrNull
            }?.takeIf(String::isNotBlank)
            if (next == null || !seenCursors.add(next)) break
            cursor = next
        }
        if (kind == ProviderKind.OPENAI_COMPATIBLE && ModelRequestPolicy.isOpenRouterBaseUrl(baseUrl)) {
            // OpenRouter's dedicated Images API can contain image models that are
            // absent from the general catalog. Fetch both authoritative catalogs
            // and merge them so image output is discovered without name guessing.
            val imageEndpoint = "$baseUrl/images/models".toHttpUrl()
            val imageBody = try {
                fetchPage(kind, imageEndpoint, apiKey, customHeaders)
            } catch (error: ProviderHttpException) {
                if (error.status in setOf(404, 405)) null else throw error
            }
            collected += parseDataModels(imageBody?.get("data") as? JsonArray, baseUrl)
        }
        val distinct = mergeDiscoveredModels(collected)
            .sortedBy { it.displayName.lowercase() }
            .take(MAX_MODELS)
        val merged = if (kind == ProviderKind.OPENAI_COMPATIBLE) {
            ModelRequestPolicy.mergeOfficialOpenAiCatalog(baseUrl, distinct)
        } else distinct
        merged.ifEmpty { throw IllegalStateException("The provider returned no usable models") }
    }

    private fun mergeDiscoveredModels(models: List<DiscoveredModel>): List<DiscoveredModel> {
        fun mergeCapability(base: Boolean?, candidate: Boolean?): Boolean? = when {
            base == true || candidate == true -> true
            candidate != null -> candidate
            else -> base
        }
        val merged = linkedMapOf<String, DiscoveredModel>()
        models.forEach { candidate ->
            val base = merged[candidate.id]
            merged[candidate.id] = if (base == null) candidate else base.copy(
                displayName = base.displayName.ifBlank { candidate.displayName },
                contextWindow = base.contextWindow ?: candidate.contextWindow,
                maxOutputTokens = base.maxOutputTokens ?: candidate.maxOutputTokens,
                supportsThinking = mergeCapability(base.supportsThinking, candidate.supportsThinking),
                supportsVision = mergeCapability(base.supportsVision, candidate.supportsVision),
                supportsFiles = mergeCapability(base.supportsFiles, candidate.supportsFiles),
                supportsTools = mergeCapability(base.supportsTools, candidate.supportsTools),
                supportsImageGeneration = mergeCapability(base.supportsImageGeneration, candidate.supportsImageGeneration),
                description = base.description.ifBlank { candidate.description },
                createdAtEpochSeconds = maxOf(base.createdAtEpochSeconds, candidate.createdAtEpochSeconds),
                inputCacheHitUsdPerMillion = base.inputCacheHitUsdPerMillion ?: candidate.inputCacheHitUsdPerMillion,
                inputCacheMissUsdPerMillion = base.inputCacheMissUsdPerMillion ?: candidate.inputCacheMissUsdPerMillion,
                outputUsdPerMillion = base.outputUsdPerMillion ?: candidate.outputUsdPerMillion,
                reasoningMetadataAvailable = candidate.reasoningMetadataAvailable || base.reasoningMetadataAvailable,
                reasoningEfforts = if (base.reasoningMetadataAvailable) base.reasoningEfforts else candidate.reasoningEfforts,
                reasoningDefaultEffort = base.reasoningDefaultEffort ?: candidate.reasoningDefaultEffort,
                reasoningDefaultEnabled = if (base.reasoningMetadataAvailable) base.reasoningDefaultEnabled else candidate.reasoningDefaultEnabled,
                reasoningMandatory = if (base.reasoningMetadataAvailable) base.reasoningMandatory else candidate.reasoningMandatory,
                reasoningSupportsMaxTokens = if (base.reasoningMetadataAvailable) base.reasoningSupportsMaxTokens else candidate.reasoningSupportsMaxTokens,
                metadataSource = base.metadataSource.ifBlank { candidate.metadataSource },
            )
        }
        return merged.values.toList()
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
                ProviderKind.OPENAI_OAUTH -> error("OAuth discovery is handled by OpenAiOAuthManager")
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
            val limit = if (endpoint.host.equals("openrouter.ai", ignoreCase = true)) {
                MAX_OPENROUTER_DISCOVERY_BYTES
            } else {
                MAX_DISCOVERY_BYTES
            }
            source.request(limit + 1L)
            require(source.buffer.size <= limit) { "The provider's model list is unexpectedly large" }
            ProviderJson.parseToJsonElement(source.buffer.readUtf8()).jsonObject
        }
    }

    internal fun parseDataModels(values: JsonArray?, baseUrlForParsing: String): List<DiscoveredModel> = values.orEmpty().mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id = model["id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (id.isBlank()) return@mapNotNull null
        val name = model["display_name"]?.jsonPrimitive?.contentOrNull
            ?: model["displayName"]?.jsonPrimitive?.contentOrNull
            ?: model["name"]?.jsonPrimitive?.contentOrNull
            ?: humanize(id)
        val openRouter = ModelRequestPolicy.isOpenRouterBaseUrl(baseUrlForParsing)
        val architecture = model["architecture"] as? JsonObject
        val inputModalities = architecture.stringSet("input_modalities")
        val outputModalities = architecture.stringSet("output_modalities")
        if (openRouter && outputModalities.isNotEmpty() && outputModalities.none { it == "text" || it == "image" }) {
            return@mapNotNull null
        }
        val supportedParameters = model.stringSet("supported_parameters")
        val topProvider = model["top_provider"] as? JsonObject
        val pricing = model["pricing"] as? JsonObject
        val reasoning = model["reasoning"] as? JsonObject
        val efforts = reasoning?.stringSet("supported_efforts").orEmpty()
            .mapNotNull(::parseThinkingEffort)
        DiscoveredModel(
            id = id,
            displayName = name,
            contextWindow = model.int("context_length", "inputTokenLimit")
                ?: topProvider?.int("context_length"),
            maxOutputTokens = model.int("outputTokenLimit")
                ?: topProvider?.int("max_completion_tokens"),
            supportsThinking = when {
                reasoning != null || "reasoning" in supportedParameters -> true
                else -> model["thinking"]?.jsonPrimitive?.booleanOrNull
            },
            supportsVision = if (openRouter) "image" in inputModalities else {
                model.booleanCapability("supports_vision", "supportsVision", "vision")
            },
            supportsFiles = if (openRouter) "file" in inputModalities else {
                model.booleanCapability("supports_files", "supportsFiles", "files")
            },
            supportsTools = if (openRouter) "tools" in supportedParameters else {
                model.booleanCapability("supports_tools", "supportsTools", "tools")
            },
            supportsImageGeneration = model.booleanCapability(
                "supports_image_generation",
                "supportsImageGeneration",
                "image_generation",
            ) ?: when {
                openRouter -> "image" in outputModalities
                ModelRequestPolicy.isOfficialOpenAiBaseUrl(baseUrlForParsing) -> imageGenerationModelHeuristic(id)
                else -> null
            },
            description = model["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            createdAtEpochSeconds = model["created"]?.jsonPrimitive?.longOrNull ?: 0,
            inputCacheHitUsdPerMillion = pricing.pricePerMillion("input_cache_read"),
            inputCacheMissUsdPerMillion = pricing.pricePerMillion("prompt"),
            outputUsdPerMillion = pricing.pricePerMillion("completion"),
            reasoningMetadataAvailable = reasoning != null,
            reasoningEfforts = efforts,
            reasoningDefaultEffort = reasoning?.get("default_effort")?.jsonPrimitive?.contentOrNull
                ?.let(::parseThinkingEffort),
            reasoningDefaultEnabled = reasoning?.get("default_enabled")?.jsonPrimitive?.booleanOrNull ?: false,
            reasoningMandatory = reasoning?.get("mandatory")?.jsonPrimitive?.booleanOrNull ?: false,
            reasoningSupportsMaxTokens = reasoning?.get("supports_max_tokens")?.jsonPrimitive?.booleanOrNull ?: false,
            metadataSource = if (openRouter) "OpenRouter" else "",
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


    private fun JsonObject.booleanCapability(vararg names: String): Boolean? {
        names.forEach { name -> this[name]?.jsonPrimitive?.booleanOrNull?.let { return it } }
        val capabilities = this["capabilities"] as? JsonObject ?: return null
        names.forEach { name -> capabilities[name]?.jsonPrimitive?.booleanOrNull?.let { return it } }
        return null
    }

    private fun JsonObject?.pricePerMillion(name: String): Double? = this?.get(name)
        ?.jsonPrimitive?.doubleOrNull?.takeIf { it >= 0.0 }?.times(1_000_000.0)

    private fun JsonObject.int(vararg names: String): Int? {
        names.forEach { name -> this[name]?.jsonPrimitive?.intOrNull?.let { return it } }
        return null
    }

    private fun JsonObject?.stringSet(name: String): Set<String> =
        (this?.get(name) as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull?.lowercase() }?.toSet().orEmpty()

    private fun parseThinkingEffort(raw: String): ThinkingEffort? = when (raw.trim().lowercase()) {
        "minimal" -> ThinkingEffort.MINIMAL
        "low" -> ThinkingEffort.LOW
        "medium" -> ThinkingEffort.MEDIUM
        "high" -> ThinkingEffort.HIGH
        "xhigh" -> ThinkingEffort.XHIGH
        "max" -> ThinkingEffort.MAX
        else -> null
    }

    private fun humanize(id: String): String = id.substringAfterLast('/').replace('-', ' ').replace('_', ' ')
        .split(' ').filter(String::isNotBlank).joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    internal fun imageGenerationModelHeuristic(id: String): Boolean {
        val normalized = id.substringAfterLast('/').lowercase()
        return normalized.startsWith("gpt-image-") ||
            normalized.startsWith("dall-e-") ||
            normalized.startsWith("imagen-") ||
            normalized.contains("image-generation") ||
            normalized.endsWith("-image")
    }

    private companion object {
        const val MAX_MODELS = 1_000
        const val MAX_PAGES = 10
        const val MAX_DISCOVERY_BYTES = 2L * 1024 * 1024
        const val MAX_OPENROUTER_DISCOVERY_BYTES = 12L * 1024 * 1024
    }
}
