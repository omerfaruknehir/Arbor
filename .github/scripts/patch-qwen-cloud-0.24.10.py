from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


# First-class Alibaba Cloud Model Studio / Qwen Cloud preset and bundled models.
catalog_path = Path("app/src/main/java/app/xylune/chat/data/DefaultCatalog.kt")
catalog = catalog_path.read_text()
catalog = replace_once(
    catalog,
    '        ProviderEntity("xai", "xAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.x.ai/v1"),\n',
    '        ProviderEntity("xai", "xAI", ProviderKind.OPENAI_COMPATIBLE, "https://api.x.ai/v1"),\n'
    '        ProviderEntity("qwen-cloud", "Qwen Cloud", ProviderKind.OPENAI_COMPATIBLE, "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),\n',
    "Qwen provider preset",
)
catalog = replace_once(
    catalog,
    '        ModelEntity("xai", "grok-3", "Grok 3", 131_072, 32_768, 0.0, 0.0, 0.0, supportsVision = true, supportsTools = true),\n',
    '        ModelEntity("xai", "grok-3", "Grok 3", 131_072, 32_768, 0.0, 0.0, 0.0, supportsVision = true, supportsTools = true),\n'
    '        ModelEntity("qwen-cloud", "qwen3.7-max", "Qwen3.7 Max", 1_000_000, 65_536, 0.0, 0.0, 0.0, supportsVision = false, supportsFiles = false, supportsThinking = true, supportsTools = true),\n'
    '        ModelEntity("qwen-cloud", "qwen3.7-plus", "Qwen3.7 Plus", 1_000_000, 65_536, 0.0, 0.0, 0.0, supportsVision = true, supportsFiles = false, supportsThinking = true, supportsTools = true),\n'
    '        ModelEntity("qwen-cloud", "qwen3.6-flash", "Qwen3.6 Flash", 1_000_000, 65_536, 0.0, 0.0, 0.0, supportsVision = true, supportsFiles = false, supportsThinking = true, supportsTools = true),\n',
    "Qwen bundled models",
)
catalog_path.write_text(catalog)


# Identify Model Studio endpoints without treating every OpenAI-compatible endpoint as Qwen.
policy_path = Path("app/src/main/java/app/xylune/chat/provider/ModelRequestPolicy.kt")
policy = policy_path.read_text()
policy = replace_once(
    policy,
    '    private val automaticOpenAiCompatiblePresetIds = setOf("openai", "deepseek", "openrouter", "xai", "ollama")\n',
    '    private val automaticOpenAiCompatiblePresetIds = setOf("openai", "deepseek", "openrouter", "xai", "qwen-cloud", "ollama")\n',
    "automatic provider presets",
)
policy = replace_once(
    policy,
    '''    fun isOpenRouter(provider: ProviderEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (provider.id == "openrouter" || isOpenRouterBaseUrl(provider.baseUrl))

''',
    '''    fun isOpenRouter(provider: ProviderEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (provider.id == "openrouter" || isOpenRouterBaseUrl(provider.baseUrl))

    fun isQwenCloudBaseUrl(rawBaseUrl: String): Boolean {
        val uri = runCatching { URI(rawBaseUrl.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path?.trimEnd('/').orEmpty()
        return uri.scheme.equals("https", ignoreCase = true) &&
            (host.contains("dashscope") || host.endsWith(".maas.aliyuncs.com")) &&
            path.endsWith("/compatible-mode/v1")
    }

    fun isQwenCloud(provider: ProviderEntity, model: ModelEntity): Boolean =
        provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (provider.id.equals("qwen-cloud", ignoreCase = true) ||
                (isQwenCloudBaseUrl(provider.baseUrl) && model.modelId.startsWith("qwen", ignoreCase = true)))

''',
    "Qwen endpoint policy",
)
policy = replace_once(
    policy,
    '''    fun mergeOfficialOpenAiCatalog(rawBaseUrl: String, discovered: List<DiscoveredModel>): List<DiscoveredModel> {
        if (!isOfficialOpenAiBaseUrl(rawBaseUrl)) return discovered
        val byId = discovered.associateByTo(linkedMapOf()) { it.id }
        officialOpenAiImageModels().forEach { bundled ->
            val existing = byId[bundled.modelId]
            byId[bundled.modelId] = DiscoveredModel(
                id = bundled.modelId,
                displayName = existing?.displayName ?: bundled.displayName,
                contextWindow = existing?.contextWindow ?: bundled.contextWindow,
                maxOutputTokens = existing?.maxOutputTokens ?: bundled.maxOutputTokens,
                supportsThinking = existing?.supportsThinking ?: bundled.supportsThinking,
                supportsVision = existing?.supportsVision ?: bundled.supportsVision,
                supportsFiles = existing?.supportsFiles ?: bundled.supportsFiles,
                supportsTools = existing?.supportsTools ?: bundled.supportsTools,
                supportsImageGeneration = true,
            )
        }
        return byId.values.sortedBy { it.displayName.lowercase() }
    }

''',
    '''    fun mergeOfficialOpenAiCatalog(rawBaseUrl: String, discovered: List<DiscoveredModel>): List<DiscoveredModel> {
        if (!isOfficialOpenAiBaseUrl(rawBaseUrl)) return discovered
        val byId = discovered.associateByTo(linkedMapOf()) { it.id }
        officialOpenAiImageModels().forEach { bundled ->
            val existing = byId[bundled.modelId]
            byId[bundled.modelId] = DiscoveredModel(
                id = bundled.modelId,
                displayName = existing?.displayName ?: bundled.displayName,
                contextWindow = existing?.contextWindow ?: bundled.contextWindow,
                maxOutputTokens = existing?.maxOutputTokens ?: bundled.maxOutputTokens,
                supportsThinking = existing?.supportsThinking ?: bundled.supportsThinking,
                supportsVision = existing?.supportsVision ?: bundled.supportsVision,
                supportsFiles = existing?.supportsFiles ?: bundled.supportsFiles,
                supportsTools = existing?.supportsTools ?: bundled.supportsTools,
                supportsImageGeneration = true,
            )
        }
        return byId.values.sortedBy { it.displayName.lowercase() }
    }

    fun mergeQwenCloudCatalog(
        providerId: String = "qwen-cloud",
        discovered: List<DiscoveredModel>,
    ): List<DiscoveredModel> {
        val byId = discovered.associateByTo(linkedMapOf()) { it.id }
        DefaultCatalog.models.filter { it.providerId == "qwen-cloud" }.forEach { bundled ->
            val existing = byId[bundled.modelId]
            byId[bundled.modelId] = DiscoveredModel(
                id = bundled.modelId,
                displayName = existing?.displayName ?: bundled.displayName,
                contextWindow = existing?.contextWindow ?: bundled.contextWindow,
                maxOutputTokens = existing?.maxOutputTokens ?: bundled.maxOutputTokens,
                supportsThinking = existing?.supportsThinking ?: bundled.supportsThinking,
                supportsVision = existing?.supportsVision ?: bundled.supportsVision,
                supportsFiles = existing?.supportsFiles ?: bundled.supportsFiles,
                supportsTools = existing?.supportsTools ?: bundled.supportsTools,
                supportsImageGeneration = existing?.supportsImageGeneration ?: bundled.supportsImageGeneration,
                description = existing?.description.orEmpty(),
                createdAtEpochSeconds = existing?.createdAtEpochSeconds ?: 0,
                inputCacheHitUsdPerMillion = existing?.inputCacheHitUsdPerMillion,
                inputCacheMissUsdPerMillion = existing?.inputCacheMissUsdPerMillion,
                outputUsdPerMillion = existing?.outputUsdPerMillion,
                reasoningMetadataAvailable = existing?.reasoningMetadataAvailable ?: false,
                reasoningEfforts = existing?.reasoningEfforts.orEmpty(),
                reasoningDefaultEffort = existing?.reasoningDefaultEffort,
                reasoningDefaultEnabled = existing?.reasoningDefaultEnabled ?: false,
                reasoningMandatory = existing?.reasoningMandatory ?: false,
                reasoningSupportsMaxTokens = existing?.reasoningSupportsMaxTokens ?: false,
                metadataSource = existing?.metadataSource?.ifBlank { "Alibaba Cloud Model Studio" }
                    ?: "Alibaba Cloud Model Studio",
            )
        }
        return byId.values.map { model ->
            if (model.id in DefaultCatalog.models.filter { it.providerId == "qwen-cloud" }.map { it.modelId }.toSet()) model
            else model.copy(metadataSource = model.metadataSource.ifBlank { "Alibaba Cloud Model Studio" })
        }.sortedBy { it.displayName.lowercase() }
    }

''',
    "Qwen catalog metadata merge",
)
policy_path.write_text(policy)


# Preserve bundled Qwen capability metadata when refreshing /models.
discovery_path = Path("app/src/main/java/app/xylune/chat/provider/ModelDiscoveryService.kt")
discovery = discovery_path.read_text()
discovery = replace_once(
    discovery,
    '''        val merged = if (kind == ProviderKind.OPENAI_COMPATIBLE) {
            ModelRequestPolicy.mergeOfficialOpenAiCatalog(baseUrl, distinct)
        } else distinct
''',
    '''        val merged = if (kind == ProviderKind.OPENAI_COMPATIBLE) {
            val withOfficialOpenAi = ModelRequestPolicy.mergeOfficialOpenAiCatalog(baseUrl, distinct)
            if (providerId.equals("qwen-cloud", ignoreCase = true) || ModelRequestPolicy.isQwenCloudBaseUrl(baseUrl)) {
                ModelRequestPolicy.mergeQwenCloudCatalog(providerId ?: "qwen-cloud", withOfficialOpenAi)
            } else {
                withOfficialOpenAi
            }
        } else distinct
''',
    "Qwen discovery metadata",
)
discovery_path.write_text(discovery)


# Qwen Chat Completions uses enable_thinking and max_completion_tokens.
openai_path = Path("app/src/main/java/app/xylune/chat/provider/OpenAiCompatibleProvider.kt")
openai = openai_path.read_text()
openai = replace_once(
    openai,
    '''        val isDeepSeek = request.provider.id == "deepseek"
        val isOpenRouter = ModelRequestPolicy.isOpenRouter(request.provider)
        return buildJsonObject {
''',
    '''        val isDeepSeek = request.provider.id == "deepseek"
        val isOpenRouter = ModelRequestPolicy.isOpenRouter(request.provider)
        val isQwenCloud = ModelRequestPolicy.isQwenCloud(request.provider, request.model)
        return buildJsonObject {
''',
    "Qwen request detection",
)
openai = replace_once(
    openai,
    '''            put("max_tokens", JsonPrimitive(request.maxOutputTokens))
            if (request.provider.id in setOf("openai", "deepseek", "openrouter", "xai") || isOpenRouter) {
''',
    '''            put(
                if (isQwenCloud) "max_completion_tokens" else "max_tokens",
                JsonPrimitive(request.maxOutputTokens),
            )
            if (request.provider.id in setOf("openai", "deepseek", "openrouter", "xai", "qwen-cloud") || isOpenRouter || isQwenCloud) {
''',
    "Qwen output and usage parameters",
)
openai = replace_once(
    openai,
    '''                when {
                    isOpenRouter -> put("reasoning", buildJsonObject {
''',
    '''                when {
                    isQwenCloud -> put("enable_thinking", JsonPrimitive(enabled))
                    isOpenRouter -> put("reasoning", buildJsonObject {
''',
    "Qwen thinking parameter",
)
openai_path.write_text(openai)


# Route Qwen's supported models through Model Studio's Responses API for native
# search and preserve action.sources as Xylune citations.
responses_path = Path("app/src/main/java/app/xylune/chat/provider/ResponsesApiTransport.kt")
responses = responses_path.read_text()
responses = replace_once(
    responses,
    '''            providerId == "deepseek" || baseUrl.contains("api.deepseek.com") -> {
                if (modelId == "deepseek-v4-flash") NativeWebSearchMode.RESPONSES else NativeWebSearchMode.NONE
            }
            providerId in setOf("openai", "openrouter", "xai") -> NativeWebSearchMode.RESPONSES
''',
    '''            providerId == "deepseek" || baseUrl.contains("api.deepseek.com") -> {
                if (modelId == "deepseek-v4-flash") NativeWebSearchMode.RESPONSES else NativeWebSearchMode.NONE
            }
            ModelRequestPolicy.isQwenCloud(request.provider, request.model) -> NativeWebSearchMode.RESPONSES
            providerId in setOf("openai", "openrouter", "xai") -> NativeWebSearchMode.RESPONSES
''',
    "Qwen native search routing",
)
responses = replace_once(
    responses,
    '''            providerId == "xai" || baseUrl.contains("api.x.ai") -> "xAI native search"
            baseUrl.contains("api.perplexity.ai") -> "Perplexity native search"
''',
    '''            providerId == "xai" || baseUrl.contains("api.x.ai") -> "xAI native search"
            ModelRequestPolicy.isQwenCloud(request.provider, request.model) -> "Qwen Cloud native search"
            baseUrl.contains("api.perplexity.ai") -> "Perplexity native search"
''',
    "Qwen native source label",
)
responses = replace_once(
    responses,
    '''        put("parallel_tool_calls", JsonPrimitive(false))
        if (request.model.supportsThinking) {
''',
    '''        put("parallel_tool_calls", JsonPrimitive(false))
        if (ModelRequestPolicy.isQwenCloud(request.provider, request.model)) {
            put("enable_thinking", JsonPrimitive(request.model.supportsThinking && request.thinkingEnabled))
        }
        if (request.model.supportsThinking) {
''',
    "Qwen Responses thinking",
)
responses = replace_once(
    responses,
    '''            add(buildJsonObject {
                put("type", JsonPrimitive(NativeWebSearch.responsesServerToolType(request)))
                if (NativeWebSearch.responsesServerToolType(request) == "openrouter:web_search") {
                    put("parameters", buildJsonObject {
                        put("engine", JsonPrimitive("auto"))
                        put("max_uses", JsonPrimitive(8))
                        put("max_total_results", JsonPrimitive(request.webSearchMaxResults.coerceIn(3, 20)))
                    })
                }
            })
            clientTools.forEach { tool ->
''',
    '''            add(buildJsonObject {
                put("type", JsonPrimitive(NativeWebSearch.responsesServerToolType(request)))
                if (NativeWebSearch.responsesServerToolType(request) == "openrouter:web_search") {
                    put("parameters", buildJsonObject {
                        put("engine", JsonPrimitive("auto"))
                        put("max_uses", JsonPrimitive(8))
                        put("max_total_results", JsonPrimitive(request.webSearchMaxResults.coerceIn(3, 20)))
                    })
                }
            })
            if (ModelRequestPolicy.isQwenCloud(request.provider, request.model) && NativeWebSearch.requestedFetch(request)) {
                add(buildJsonObject { put("type", JsonPrimitive("web_extractor")) })
            }
            clientTools.forEach { tool ->
''',
    "Qwen web extractor",
)
responses = replace_once(
    responses,
    '''                outputItems[index] = item
                collectCitations(item)
                when (item.string("type")) {
''',
    '''                outputItems[index] = item
                collectSearchSources(item)
                collectCitations(item)
                when (item.string("type")) {
''',
    "streaming Qwen source collection",
)
responses = replace_once(
    responses,
    '''            outputItems[index] = item
            collectCitations(item)
            if (item.string("type") == "function_call") {
''',
    '''            outputItems[index] = item
            collectSearchSources(item)
            collectCitations(item)
            if (item.string("type") == "function_call") {
''',
    "completed Qwen source collection",
)
responses = replace_once(
    responses,
    '''    private fun collectCitations(element: JsonElement?) {
''',
    '''    private fun collectSearchSources(item: JsonObject?) {
        if (item?.string("type") != "web_search_call") return
        item.obj("action")?.array("sources").orEmpty().forEach { sourceElement ->
            val source = sourceElement as? JsonObject ?: return@forEach
            val url = source.string("url") ?: source.string("uri")
            if (!url.isNullOrBlank() && url.startsWith("http")) {
                val title = source.string("title") ?: source.string("name") ?: url
                citations.putIfAbsent(url, title)
            }
        }
    }

    private fun collectCitations(element: JsonElement?) {
''',
    "Qwen action source parser",
)
responses_path.write_text(responses)


# Regression coverage for the preset, Chat Completions, Responses tools and
# Model Studio action.sources citations.
test_path = Path("app/src/test/java/app/xylune/chat/provider/QwenCloudProviderTest.kt")
test_path.write_text(r'''package app.xylune.chat.provider

import app.xylune.chat.data.DefaultCatalog
import app.xylune.chat.data.MessageRole
import app.xylune.chat.data.ModelEntity
import app.xylune.chat.data.ProviderEntity
import app.xylune.chat.data.ProviderKind
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwenCloudProviderTest {
    private val webSearch = NativeToolDefinition(
        name = "web_search",
        description = "Search the web",
        parametersJson = """{"type":"object","properties":{"query":{"type":"string"}}}""",
    )
    private val webFetch = NativeToolDefinition(
        name = "web_fetch",
        description = "Fetch a page",
        parametersJson = """{"type":"object","properties":{"url":{"type":"string"}}}""",
    )

    @Test
    fun defaultCatalogContainsAWorkingQwenCloudPreset() {
        val provider = DefaultCatalog.providers.single { it.id == "qwen-cloud" }
        assertEquals(ProviderKind.OPENAI_COMPATIBLE, provider.kind)
        assertTrue(ModelRequestPolicy.isQwenCloudBaseUrl(provider.baseUrl))
        assertNotNull(DefaultCatalog.models.singleOrNull { it.providerId == provider.id && it.modelId == "qwen3.7-plus" })
        assertNotNull(DefaultCatalog.models.singleOrNull { it.providerId == provider.id && it.modelId == "qwen3.7-max" })
        assertNotNull(DefaultCatalog.models.singleOrNull { it.providerId == provider.id && it.modelId == "qwen3.6-flash" })
    }

    @Test
    fun chatCompletionsUsesQwenThinkingAndCompletionParameters() {
        val request = request().copy(tools = emptyList())
        val body = OpenAiCompatibleProvider().buildRequestBody(request)

        assertTrue(body["enable_thinking"]!!.jsonPrimitive.content.toBoolean())
        assertEquals("2048", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertFalse("max_tokens" in body)
        assertTrue(body["stream_options"]!!.jsonObject["include_usage"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun qwenNativeSearchUsesResponsesAndAddsWebExtractor() {
        val request = request()
        assertEquals(NativeWebSearchMode.RESPONSES, NativeWebSearch.mode(request))

        val body = ResponsesApiTransport(OkHttpClient()).buildRequestBody(request)
        val toolTypes = body["tools"]!!.jsonArray.map { it.jsonObject["type"]!!.jsonPrimitive.content }
        assertTrue("web_search" in toolTypes)
        assertTrue("web_extractor" in toolTypes)
        assertTrue(body["enable_thinking"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun qwenResponsesActionSourcesBecomeVisibleCitations() {
        val state = ResponsesApiStreamState("Qwen Cloud native search")
        val chunk = state.accept(
            """{"type":"response.completed","response":{"status":"completed","output":[{"type":"web_search_call","id":"ws_1","status":"completed","action":{"type":"search","query":"Xylune","sources":[{"url":"https://example.com/xylune","title":"Xylune source"}]}},{"type":"message","id":"m_1","role":"assistant","content":[{"type":"output_text","text":"Result"}]}],"usage":{"input_tokens":4,"output_tokens":2}}}""",
        )!!

        assertTrue(chunk.text.contains("Xylune source"))
        assertTrue(chunk.text.contains("https://example.com/xylune"))
        assertTrue(chunk.nativeProviderPayloadJson.contains("web_search_call"))
    }

    private fun request() = ChatRequest(
        provider = ProviderEntity(
            id = "qwen-cloud",
            displayName = "Qwen Cloud",
            kind = ProviderKind.OPENAI_COMPATIBLE,
            baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1",
        ),
        model = ModelEntity(
            providerId = "qwen-cloud",
            modelId = "qwen3.7-plus",
            displayName = "Qwen3.7 Plus",
            contextWindow = 1_000_000,
            maxOutputTokens = 65_536,
            inputCacheHitUsdPerMillion = 0.0,
            inputCacheMissUsdPerMillion = 0.0,
            outputUsdPerMillion = 0.0,
            supportsVision = true,
            supportsThinking = true,
            supportsTools = true,
        ),
        apiKey = "test-key",
        messages = listOf(InputMessage(MessageRole.USER, "Search for Xylune")),
        maxOutputTokens = 2_048,
        thinkingEnabled = true,
        tools = listOf(webSearch, webFetch),
    )
}
''')


# Keep the existing 0.24.10 release and document the additional provider.
changelog_path = Path("CHANGELOG.md")
changelog = changelog_path.read_text()
changelog = replace_once(
    changelog,
    "## 0.24.10 — 2026-08-06\n\n",
    "## 0.24.10 — 2026-08-06\n\n"
    "- Add first-class Qwen Cloud / Alibaba Cloud Model Studio support with a ready-to-edit international endpoint and bundled Qwen3.7/Qwen3.6 models.\n"
    "- Use Qwen-specific thinking and output-limit parameters, Model Studio Responses native search, web extraction, and returned search-source parsing.\n",
    "Qwen changelog",
)
changelog_path.write_text(changelog)

notes_path = Path("docs/releases/RELEASE_NOTES_0.24.10.md")
notes = notes_path.read_text()
notes += '''

## Qwen Cloud

Qwen Cloud is now a first-class provider backed by Alibaba Cloud Model Studio's OpenAI-compatible API. The preset uses the still-supported Singapore international endpoint by default; users can paste the API Host from their Model Studio workspace to select another region or the newer workspace-specific endpoint.

Bundled defaults include Qwen3.7 Max, Qwen3.7 Plus, and Qwen3.6 Flash. Model discovery remains available, while bundled capability metadata is retained for the main Qwen models.

Xylune sends Qwen's native `enable_thinking` and `max_completion_tokens` parameters for Chat Completions. When provider-native web search is selected, supported Qwen models use Model Studio's Responses API with `web_search` and `web_extractor`; returned `action.sources` links feed Xylune's inline citations, search result cards, and bottom Sources bar.
'''
notes_path.write_text(notes)
