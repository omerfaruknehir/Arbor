from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected patch anchor not found in {path}: {old[:180]!r}")
    file.write_text(text.replace(old, new, 1))


# Keep a protocol-only allowlist after the execution budget disables tools. This
# lets the transport recognize and quarantine stale DSML without re-exposing a
# callable function to the model.
replace_once(
    "app/src/main/java/app/arbor/chat/provider/ProviderModels.kt",
    '''    val customHeaders: Map<String, String> = emptyMap(),
    val tools: List<NativeToolDefinition> = emptyList(),
)
''',
    '''    val customHeaders: Map<String, String> = emptyMap(),
    val tools: List<NativeToolDefinition> = emptyList(),
    /** Names recognized only by the protocol firewall; these are never serialized as callable tools. */
    val toolProtocolNames: Set<String> = emptySet(),
)
''',
)

replace_once(
    "app/src/main/java/app/arbor/chat/generation/GenerationWorker.kt",
    '''                        customHeaders = parseHeaders(provider.customHeadersJson),
                        tools = if (nativeToolsDisabled) emptyList() else nativeToolDefinitions,
                    )
''',
    '''                        customHeaders = parseHeaders(provider.customHeadersJson),
                        tools = if (nativeToolsDisabled) emptyList() else nativeToolDefinitions,
                        // Tool execution can be disabled for the final synthesis turn, but
                        // stale text-encoded calls must still be recognized and suppressed.
                        toolProtocolNames = nativeToolDefinitions.mapTo(linkedSetOf()) { it.name },
                    )
''',
)

provider = "app/src/main/java/app/arbor/chat/provider/OpenAiCompatibleProvider.kt"
replace_once(
    provider,
    '''            val calls = linkedMapOf<Int, ToolCallAccumulator>()
            val allowedTools = attemptRequest.tools.mapTo(linkedSetOf()) { it.name.lowercase() }
            val dsmlChannels = allowedTools.takeIf { it.isNotEmpty() }?.let(::DsmlChannelsAdapter)
            val rawText = StringBuilder()
            val rawReasoning = StringBuilder()
            val bufferedVisibleText = StringBuilder()
            val bufferedVisibleReasoning = StringBuilder()
            val isDeepSeekToolTurn = attemptRequest.isDeepSeekToolTurn()
            val quarantineToolText = isDeepSeekToolTurn
''',
    '''            val calls = linkedMapOf<Int, ToolCallAccumulator>()
            val exposedTools = attemptRequest.tools.mapTo(linkedSetOf()) { it.name.lowercase() }
            val protocolTools = linkedSetOf<String>().apply {
                addAll(exposedTools)
                attemptRequest.toolProtocolNames.mapTo(this) { it.lowercase() }
            }
            val dsmlChannels = protocolTools.takeIf { it.isNotEmpty() }?.let(::DsmlChannelsAdapter)
            val rawText = StringBuilder()
            val rawReasoning = StringBuilder()
            val bufferedVisibleText = StringBuilder()
            val bufferedVisibleReasoning = StringBuilder()
            // DeepSeek tool turns and no-tool finalization turns are held until EOF.
            // The latter is the critical case: after the execution budget is exhausted,
            // a stale DSML request must never be streamed into the conversation.
            val quarantineToolText = attemptRequest.isDeepSeekFamily() ||
                (exposedTools.isEmpty() && protocolTools.isNotEmpty())
''',
)

replace_once(
    provider,
    '''            val recoveredPlainTextCalls = if (completedStructuredCalls.isEmpty() && allowedTools.isNotEmpty()) {
                PlainTextToolCallDetector.extractTrailingCalls(rawText.toString(), allowedTools)
                    .ifEmpty { PlainTextToolCallDetector.extractTrailingCalls(rawReasoning.toString(), allowedTools) }
            } else {
                emptyList()
            }
            val recoveredTextCalls = recoveredProtocolCalls.ifEmpty { recoveredPlainTextCalls }
            val protocolHint = DsmlToolProtocol.containsProtocolHint(rawText) ||
                DsmlToolProtocol.containsProtocolHint(rawReasoning)
            val textEncodedToolFailure = completedStructuredCalls.isEmpty() &&
                allowedTools.isNotEmpty() &&
                (recoveredTextCalls.isNotEmpty() || adapted?.malformed == true || protocolHint)
''',
    '''            val recoveredPlainTextCalls = if (completedStructuredCalls.isEmpty() && protocolTools.isNotEmpty()) {
                PlainTextToolCallDetector.extractTrailingCalls(rawText.toString(), protocolTools)
                    .ifEmpty { PlainTextToolCallDetector.extractTrailingCalls(rawReasoning.toString(), protocolTools) }
            } else {
                emptyList()
            }
            val recoveredTextCalls = recoveredProtocolCalls.ifEmpty { recoveredPlainTextCalls }
            val protocolHint = DsmlToolProtocol.containsProtocolHint(rawText) ||
                DsmlToolProtocol.containsProtocolHint(rawReasoning)
            val textEncodedToolFailure = completedStructuredCalls.isEmpty() &&
                protocolTools.isNotEmpty() &&
                (recoveredTextCalls.isNotEmpty() || adapted?.malformed == true || protocolHint)
''',
)

replace_once(
    provider,
    '''                if (recoveredTextCalls.isEmpty()) {
                    throw ProviderProtocolException(
                        "The provider repeatedly serialized a tool request into assistant text or reasoning, and Arbor could not safely recover its arguments.",
                    )
                }
                emit(
                    StreamChunk(
                        toolCalls = recoveredTextCalls,
                        inputTokens = attemptInputTokens.plusUsage(discardedInputTokens),
                        outputTokens = attemptOutputTokens.plusUsage(discardedOutputTokens),
                        cachedInputTokens = attemptCachedTokens.plusUsage(discardedCachedTokens),
                        finishReason = "tool_calls",
                    ),
                )
                break
''',
    '''                if (exposedTools.isEmpty()) {
                    throw ProviderProtocolException(
                        "The provider repeatedly printed a tool request after Arbor disabled tools for finalization. " +
                            "Arbor discarded the protocol instead of displaying or executing it.",
                    )
                }
                val executableRecoveredCalls = recoveredTextCalls.filter { call ->
                    call.name.lowercase() in exposedTools
                }
                if (executableRecoveredCalls.isEmpty()) {
                    throw ProviderProtocolException(
                        "The provider repeatedly serialized a tool request into assistant text or reasoning, and Arbor could not safely recover an exposed tool call.",
                    )
                }
                emit(
                    StreamChunk(
                        toolCalls = executableRecoveredCalls,
                        inputTokens = attemptInputTokens.plusUsage(discardedInputTokens),
                        outputTokens = attemptOutputTokens.plusUsage(discardedOutputTokens),
                        cachedInputTokens = attemptCachedTokens.plusUsage(discardedCachedTokens),
                        finishReason = "tool_calls",
                    ),
                )
                break
''',
)

replace_once(
    provider,
    '''    internal fun deepSeekToolGuardedRequest(
        request: ChatRequest,
        correctionAttempt: Int,
    ): ChatRequest {
        if (!request.isDeepSeekToolTurn() && correctionAttempt == 0) return request
        val instruction = buildString {
            append(DEEPSEEK_TOOL_CALL_GUARD)
            if (correctionAttempt > 0) {
                append("\\n\\n")
                append(DEEPSEEK_TOOL_CALL_CORRECTION)
            }
        }
''',
    '''    internal fun deepSeekToolGuardedRequest(
        request: ChatRequest,
        correctionAttempt: Int,
    ): ChatRequest {
        val hasExposedTools = request.tools.isNotEmpty()
        val hasProtocolGuard = request.toolProtocolNames.isNotEmpty()
        if (correctionAttempt == 0 && !(request.isDeepSeekFamily() && hasExposedTools)) return request
        if (!hasExposedTools && !hasProtocolGuard) return request
        val instruction = buildString {
            if (hasExposedTools) append(DEEPSEEK_TOOL_CALL_GUARD)
            if (correctionAttempt > 0) {
                if (isNotEmpty()) append("\\n\\n")
                append(
                    if (hasExposedTools) DEEPSEEK_TOOL_CALL_CORRECTION
                    else TOOL_DISABLED_PROTOCOL_CORRECTION
                )
            }
        }
''',
)

replace_once(
    provider,
    '''    private fun ChatRequest.isDeepSeekToolTurn(): Boolean {
        if (tools.isEmpty()) return false
        return listOf(
            provider.id,
            provider.displayName,
            provider.baseUrl,
            model.modelId,
            model.displayName,
        ).any { it.contains("deepseek", ignoreCase = true) }
    }
''',
    '''    private fun ChatRequest.isDeepSeekFamily(): Boolean = listOf(
        provider.id,
        provider.displayName,
        provider.baseUrl,
        model.modelId,
        model.displayName,
    ).any { it.contains("deepseek", ignoreCase = true) }
''',
)

replace_once(
    provider,
    '''        const val DEEPSEEK_TOOL_CALL_CORRECTION =
            "Retry the current turn from scratch. Your previous attempt serialized a tool request into content. " +
                "Use structured tool_calls only, with no preamble; otherwise answer normally without tool syntax."
''',
    '''        const val DEEPSEEK_TOOL_CALL_CORRECTION =
            "Retry the current turn from scratch. Your previous attempt serialized a tool request into content. " +
                "Use structured tool_calls only, with no preamble; otherwise answer normally without tool syntax."
        const val TOOL_DISABLED_PROTOCOL_CORRECTION =
            "Retry the current turn from scratch. Tools are unavailable for this finalization turn. " +
                "Do not print DSML, XML-like tool markup, function names, or tool arguments. " +
                "Answer only from the evidence already present and state any concrete limitation."
''',
)

# Regression test: reproduce the actual post-budget failure. The first response
# prints the prayer-time web_fetch DSML while no callable tools are exposed; the
# transport must roll it back, issue a no-tool correction, and expose only the
# clean second response.
test = "app/src/test/java/app/arbor/chat/provider/NativeProviderProtocolTest.kt"
replace_once(
    test,
    '''import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
''',
    '''import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
''',
)

replace_once(
    test,
    '''    @Test
    fun providerSpecificThinkingControlsAreSerialized() {
''',
    '''    @Test
    fun disabledToolFinalizationQuarantinesPrayerTimeDsmlAndRetriesCleanly() = runBlocking {
        fun sse(text: String): String =
            "data: {\\\"choices\\\":[{\\\"delta\\\":{\\\"content\\\":${JsonPrimitive(text)}}}]}\\n\\ndata: [DONE]\\n\\n"

        val attempt = AtomicInteger(0)
        val requestBodies = mutableListOf<String>()
        val responses = listOf(
            sse(
                "Aladhan API'sine compile_widget erişemiyor. Alternatif bir API deneyeyim.\\n" +
                    "< | | DSML | | tool_calls>< | | DSML | | invoke name=\\\"web_fetch\\\">" +
                    "< | | DSML | | parameter name=\\\"url\\\" string=\\\"true\\\">" +
                    "https://api.pray.zone/v2/times/today.json?latitude=39.9334&longitude=32.8597&method=13" +
                    "< / | | DSML | | parameter>< / | | DSML | | invoke>< / | | DSML | | tool_calls>",
            ),
            sse("Mevcut araç sonuçları yeterli değil; eksik veriyi açıkça belirterek devam ediyorum."),
        )
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                requestBodies += buffer.readUtf8()
                val index = attempt.getAndIncrement().coerceAtMost(responses.lastIndex)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responses[index].toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()
        val guardedRequest = request(
            ProviderKind.OPENAI_COMPATIBLE,
            listOf(InputMessage(MessageRole.USER, "Namaz vakti widget'i yap")),
            modelId = "deepseek-v4-pro",
            providerId = "deepseek",
        ).copy(
            tools = emptyList(),
            toolProtocolNames = setOf("compile_widget", "web_search", "web_fetch"),
        )
        val chunks = mutableListOf<StreamChunk>()

        OpenAiCompatibleProvider(client).stream(guardedRequest) { chunks += it }

        val visible = chunks.joinToString(separator = "") { it.text + it.reasoning }
        assertEquals(2, attempt.get())
        assertTrue(chunks.any { it.resetCurrentAttempt })
        assertFalse(visible.contains("DSML", ignoreCase = true))
        assertFalse(visible.contains("web_fetch", ignoreCase = true))
        assertTrue(visible.contains("Mevcut araç sonuçları"))
        assertTrue(requestBodies.none { it.contains("\\\"tools\\\"") })
        assertTrue(requestBodies.last().contains("Tools are unavailable for this finalization turn"))
    }

    @Test
    fun providerSpecificThinkingControlsAreSerialized() {
''',
)

# Keep the dedicated request-construction test so the guard-only correction
# contract remains explicit even if the stream implementation is refactored.
deepseek_test = "app/src/test/java/app/arbor/chat/provider/DeepSeekCompatibilityTest.kt"
replace_once(
    deepseek_test,
    '''    @Test
    fun correctionRetryAddsStrictStructuredToolReminder() {
''',
    '''    @Test
    fun disabledToolRetryAddsNoProtocolFinalizationReminder() {
        val base = ChatRequest(
            provider = ProviderEntity("deepseek", "DeepSeek", ProviderKind.OPENAI_COMPATIBLE, "https://api.deepseek.com"),
            model = ModelEntity(
                providerId = "deepseek",
                modelId = "deepseek-v4-pro",
                displayName = "DeepSeek V4 Pro",
                contextWindow = 1_000_000,
                maxOutputTokens = 384_000,
                inputCacheHitUsdPerMillion = 0.0,
                inputCacheMissUsdPerMillion = 0.0,
                outputUsdPerMillion = 0.0,
                supportsThinking = true,
                supportsTools = true,
            ),
            apiKey = "test",
            messages = listOf(InputMessage(MessageRole.USER, "Finish from current evidence")),
            maxOutputTokens = 1_024,
            thinkingEnabled = true,
            tools = emptyList(),
            toolProtocolNames = setOf("web_fetch"),
        )

        val guarded = OpenAiCompatibleProvider().deepSeekToolGuardedRequest(base, correctionAttempt = 1)
        val system = guarded.messages.first { it.role == MessageRole.SYSTEM }.content

        assertTrue(system.contains("Tools are unavailable for this finalization turn"))
        assertTrue(system.contains("Do not print DSML"))
        assertTrue(guarded.tools.isEmpty())
    }

    @Test
    fun correctionRetryAddsStrictStructuredToolReminder() {
''',
)

replace_once(
    "app/build.gradle.kts",
    '''        versionCode = 159
        versionName = "0.20.33"
''',
    '''        versionCode = 160
        versionName = "0.20.34"
''',
)

notes = Path("docs/releases/RELEASE_NOTES_0.20.34.md")
if notes.exists():
    raise SystemExit(f"Release notes already exist: {notes}")
notes.write_text(
    """# Arbor 0.20.34

- Fixes the remaining DeepSeek DSML leak after Arbor exhausted the tool-execution budget and entered its tools-disabled final synthesis turn.
- Keeps a separate protocol-firewall allowlist after callable tools are removed, so stale `compile_widget`, `web_search`, and `web_fetch` DSML can still be recognized without making those tools callable again.
- Quarantines the complete finalization response until it is classified; protocol text is rolled back before it can reach the message or title.
- Retries once with an explicit no-tools finalization instruction. Repeated protocol output is rejected cleanly and is never shown or executed.
- Adds an end-to-end transport regression test reproducing the prayer-time `web_fetch` DSML sequence from the device screenshot.
"""
)
