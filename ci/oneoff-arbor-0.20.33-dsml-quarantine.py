from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected patch anchor not found in {path}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


provider = "app/src/main/java/app/arbor/chat/provider/OpenAiCompatibleProvider.kt"
replace_once(
    provider,
    '''            val calls = linkedMapOf<Int, ToolCallAccumulator>()
            val allowedTools = attemptRequest.tools.mapTo(linkedSetOf()) { it.name.lowercase() }
            val dsmlAdapter = allowedTools.takeIf { it.isNotEmpty() }?.let(::DsmlToolStreamAdapter)
            val rawText = StringBuilder()
            var meaningfulPayloadReceived = false
''',
    '''            val calls = linkedMapOf<Int, ToolCallAccumulator>()
            val allowedTools = attemptRequest.tools.mapTo(linkedSetOf()) { it.name.lowercase() }
            val dsmlChannels = allowedTools.takeIf { it.isNotEmpty() }?.let(::DsmlChannelsAdapter)
            val rawText = StringBuilder()
            val rawReasoning = StringBuilder()
            val bufferedVisibleText = StringBuilder()
            val bufferedVisibleReasoning = StringBuilder()
            val isDeepSeekToolTurn = attemptRequest.isDeepSeekToolTurn()
            val quarantineToolText = isDeepSeekToolTurn
            var meaningfulPayloadReceived = false
''',
)

replace_once(
    provider,
    '''                    parseChunk(payload, calls)?.let { chunk ->
                        rawText.append(chunk.text)
                        val adapted = dsmlAdapter?.accept(chunk.text) ?: chunk.text
                        val outgoing = if (adapted == chunk.text) chunk else chunk.copy(text = adapted)
                        if (outgoing.hasMeaningfulPayload()) meaningfulPayloadReceived = true
                        finishReason = outgoing.finishReason ?: finishReason
                        attemptInputTokens = outgoing.inputTokens ?: attemptInputTokens
                        attemptOutputTokens = outgoing.outputTokens ?: attemptOutputTokens
                        attemptCachedTokens = outgoing.cachedInputTokens ?: attemptCachedTokens
                        emit(outgoing)
                    }
''',
    '''                    parseChunk(payload, calls)?.let { chunk ->
                        rawText.append(chunk.text)
                        rawReasoning.append(chunk.reasoning)
                        val adapted = dsmlChannels?.accept(chunk.text, chunk.reasoning)
                            ?: DsmlChannelDelta(chunk.text, chunk.reasoning)
                        val outgoing = if (quarantineToolText) {
                            bufferedVisibleText.append(adapted.text)
                            bufferedVisibleReasoning.append(adapted.reasoning)
                            chunk.copy(text = "", reasoning = "")
                        } else if (adapted.text == chunk.text && adapted.reasoning == chunk.reasoning) {
                            chunk
                        } else {
                            chunk.copy(text = adapted.text, reasoning = adapted.reasoning)
                        }
                        if (outgoing.hasMeaningfulPayload()) meaningfulPayloadReceived = true
                        finishReason = outgoing.finishReason ?: finishReason
                        attemptInputTokens = outgoing.inputTokens ?: attemptInputTokens
                        attemptOutputTokens = outgoing.outputTokens ?: attemptOutputTokens
                        attemptCachedTokens = outgoing.cachedInputTokens ?: attemptCachedTokens
                        emit(outgoing)
                    }
''',
)

old_finish = '''            val adapted = dsmlAdapter?.finish()
            val completedStructuredCalls = calls.toSortedMap()
            val isDeepSeekToolTurn = attemptRequest.isDeepSeekToolTurn()
            val recoveredTextCalls = if (completedStructuredCalls.isEmpty() && isDeepSeekToolTurn) {
                adapted?.calls?.takeIf { it.isNotEmpty() }
                    ?: PlainTextToolCallDetector.extractTrailingCalls(rawText.toString(), allowedTools)
            } else {
                emptyList()
            }
            val textEncodedToolFailure = completedStructuredCalls.isEmpty() &&
                isDeepSeekToolTurn &&
                (recoveredTextCalls.isNotEmpty() || adapted?.malformed == true)

            if (textEncodedToolFailure) {
                emit(StreamChunk(resetCurrentAttempt = true))

                if (deepSeekCorrectionAttempt < MAX_DEEPSEEK_TOOL_CORRECTION_RETRIES) {
                    discardedInputTokens += attemptInputTokens ?: 0L
                    discardedOutputTokens += attemptOutputTokens ?: 0L
                    discardedCachedTokens += attemptCachedTokens ?: 0L
                    deepSeekCorrectionAttempt++
                    delay(DEEPSEEK_TOOL_CORRECTION_RETRY_DELAY_MS)
                    continue
                }

                if (recoveredTextCalls.isEmpty()) {
                    throw ProviderProtocolException(
                        "DeepSeek repeatedly serialized a tool request into assistant content, and Arbor could not safely recover its arguments.",
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
            }

            adapted?.let { result ->
                val finalChunk = StreamChunk(
                    text = result.visibleText,
                    toolCalls = if (completedStructuredCalls.isEmpty()) result.calls else emptyList(),
                )
                if (finalChunk.hasMeaningfulPayload()) {
                    meaningfulPayloadReceived = true
                    emit(finalChunk)
                }
            }
'''
new_finish = '''            val adapted = dsmlChannels?.finish()
            if (quarantineToolText) {
                bufferedVisibleText.append(adapted?.tailText.orEmpty())
                bufferedVisibleReasoning.append(adapted?.tailReasoning.orEmpty())
            }
            val completedStructuredCalls = calls.toSortedMap()
            val recoveredProtocolCalls = adapted?.calls.orEmpty()
            val recoveredPlainTextCalls = if (completedStructuredCalls.isEmpty() && allowedTools.isNotEmpty()) {
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

            if (textEncodedToolFailure) {
                emit(StreamChunk(resetCurrentAttempt = true))

                if (deepSeekCorrectionAttempt < MAX_DEEPSEEK_TOOL_CORRECTION_RETRIES) {
                    discardedInputTokens += attemptInputTokens ?: 0L
                    discardedOutputTokens += attemptOutputTokens ?: 0L
                    discardedCachedTokens += attemptCachedTokens ?: 0L
                    deepSeekCorrectionAttempt++
                    delay(DEEPSEEK_TOOL_CORRECTION_RETRY_DELAY_MS)
                    continue
                }

                if (recoveredTextCalls.isEmpty()) {
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
            }

            if (quarantineToolText) {
                val finalChunk = StreamChunk(
                    text = bufferedVisibleText.toString(),
                    reasoning = bufferedVisibleReasoning.toString(),
                )
                if (finalChunk.hasMeaningfulPayload()) {
                    meaningfulPayloadReceived = true
                    emit(finalChunk)
                }
            } else {
                adapted?.let { result ->
                    val finalChunk = StreamChunk(
                        text = result.tailText,
                        reasoning = result.tailReasoning,
                        toolCalls = if (completedStructuredCalls.isEmpty()) result.calls else emptyList(),
                    )
                    if (finalChunk.hasMeaningfulPayload()) {
                        meaningfulPayloadReceived = true
                        emit(finalChunk)
                    }
                }
            }
'''
replace_once(provider, old_finish, new_finish)

replace_once(
    provider,
    '''        if (!request.isDeepSeekToolTurn()) return request
''',
    '''        if (!request.isDeepSeekToolTurn() && correctionAttempt == 0) return request
''',
)

replace_once(
    provider,
    '''    private fun ChatRequest.isDeepSeekToolTurn(): Boolean =
        tools.isNotEmpty() &&
            (provider.id.equals("deepseek", ignoreCase = true) ||
                model.modelId.contains("deepseek", ignoreCase = true))
''',
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
)

protocol = "app/src/main/java/app/arbor/chat/provider/DsmlToolProtocol.kt"
replace_once(
    protocol,
    '''    // DeepSeek-compatible endpoints currently emit both <|DSML|...> and
    // <||DSML||...>. Some gateways also insert whitespace around each pipe.
    // Treat a run of ASCII or full-width pipes as the protocol fence while still
    // requiring the DSML namespace and an exact supported element name.
    private const val PIPE_RUN = "(?:[|｜]\\\\s*)+"
    private val startMarker = Regex("(?is)<\\\\s*$PIPE_RUN\\\\s*DSML\\\\s*$PIPE_RUN\\\\s*tool_calls\\\\s*>")
    private val endMarker = Regex("(?is)<\\\\s*/\\\\s*$PIPE_RUN\\\\s*DSML\\\\s*$PIPE_RUN\\\\s*tool_calls\\\\s*>")
    private val invokeMarker = Regex(
        "(?is)<\\\\s*$PIPE_RUN\\\\s*DSML\\\\s*$PIPE_RUN\\\\s*invoke\\\\b([^>]*)>(.*?)" +
            "<\\\\s*/\\\\s*$PIPE_RUN\\\\s*DSML\\\\s*$PIPE_RUN\\\\s*invoke\\\\s*>",
    )
    private val parameterMarker = Regex(
        "(?is)<\\\\s*$PIPE_RUN\\\\s*DSML\\\\s*$PIPE_RUN\\\\s*parameter\\\\b([^>]*)>(.*?)" +
            "<\\\\s*/\\\\s*$PIPE_RUN\\\\s*DSML\\\\s*$PIPE_RUN\\\\s*parameter\\\\s*>",
    )
''',
    '''    // DeepSeek-compatible gateways vary the fence glyphs, HTML-escape angle
    // brackets, and sometimes insert Unicode format/space characters. Match only
    // the exact DSML namespace and known element names, but tolerate those wire
    // representation differences.
    private const val GAP = "[\\\\s\\\\p{Z}\\\\p{Cf}]*"
    private const val PIPE_TOKEN =
        "(?:[|｜¦∣│❘￨]|&(?:vert|VerticalLine);|&#0*124;|&#x0*7c;)"
    private const val PIPE_RUN = "(?:$PIPE_TOKEN$GAP)+"
    private const val OPEN_ANGLE = "(?:<|&lt;|&#0*60;|&#x0*3c;)"
    private const val CLOSE_ANGLE = "(?:>|&gt;|&#0*62;|&#x0*3e;)"
    private const val SLASH = "(?:/|&#0*47;|&#x0*2f;)"

    private fun openingTag(element: String, captureAttributes: Boolean = false): String {
        val attributes = if (captureAttributes) "\\\\b(.*?)" else ""
        return "$OPEN_ANGLE$GAP$PIPE_RUN${GAP}DSML$GAP$PIPE_RUN$GAP$element$attributes$GAP$CLOSE_ANGLE"
    }

    private fun closingTag(element: String): String =
        "$OPEN_ANGLE$GAP$SLASH$GAP$PIPE_RUN${GAP}DSML$GAP$PIPE_RUN$GAP$element$GAP$CLOSE_ANGLE"

    private val startMarker = Regex("(?is)${openingTag("tool_calls")}")
    private val endMarker = Regex("(?is)${closingTag("tool_calls")}")
    private val invokeMarker = Regex(
        "(?is)${openingTag("invoke", captureAttributes = true)}(.*?)${closingTag("invoke")}",
    )
    private val parameterMarker = Regex(
        "(?is)${openingTag("parameter", captureAttributes = true)}(.*?)${closingTag("parameter")}",
    )
''',
)

replace_once(
    protocol,
    '''    internal fun findStart(value: CharSequence): MatchResult? = startMarker.find(value)
    internal fun findEnd(value: CharSequence): MatchResult? = endMarker.find(value)

    fun parseBlock(block: String, allowedTools: Set<String>): DsmlToolProtocolResult {
''',
    '''    internal fun findStart(value: CharSequence): MatchResult? = startMarker.find(value)
    internal fun findEnd(value: CharSequence): MatchResult? = endMarker.find(value)

    internal fun containsProtocolHint(value: CharSequence): Boolean {
        if (value.isEmpty()) return false
        val compact = buildString(value.length) {
            value.forEach { character ->
                if (character.isLetterOrDigit()) append(character.lowercaseChar())
            }
        }
        return compact.contains("dsmltoolcalls") &&
            (compact.contains("dsmlinvoke") || compact.contains("dsmlparameter"))
    }

    fun parseBlock(block: String, allowedTools: Set<String>): DsmlToolProtocolResult {
''',
)

replace_once(
    protocol,
    '''    private fun decodeEntities(value: String): String = value
        .replace("&quot;", "\\\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
''',
    '''    private fun decodeEntities(value: String): String = value
        .replace(Regex("(?i)&quot;"), "\\\"")
        .replace(Regex("(?i)&#(?:0*39|x0*27);"), "'")
        .replace(Regex("(?i)&lt;|&#(?:0*60|x0*3c);"), "<")
        .replace(Regex("(?i)&gt;|&#(?:0*62|x0*3e);"), ">")
        .replace(Regex("(?i)&vert;|&VerticalLine;|&#(?:0*124|x0*7c);"), "|")
        .replace(Regex("(?i)&amp;"), "&")
''',
)

anchor = '''
/** Incrementally removes DSML from streamed assistant text and emits native calls at EOF. */
internal class DsmlToolStreamAdapter(private val allowedTools: Set<String>) {
'''
insert = '''
internal data class DsmlChannelDelta(
    val text: String,
    val reasoning: String,
)

internal data class DsmlChannelsResult(
    val tailText: String,
    val tailReasoning: String,
    val calls: List<NativeToolCall>,
    val malformed: Boolean,
)

/** Applies the DSML quarantine independently to assistant content and reasoning. */
internal class DsmlChannelsAdapter(allowedTools: Set<String>) {
    private val text = DsmlToolStreamAdapter(allowedTools)
    private val reasoning = DsmlToolStreamAdapter(allowedTools)

    fun accept(textDelta: String, reasoningDelta: String): DsmlChannelDelta = DsmlChannelDelta(
        text = text.accept(textDelta),
        reasoning = reasoning.accept(reasoningDelta),
    )

    fun finish(): DsmlChannelsResult {
        val textResult = text.finish()
        val reasoningResult = reasoning.finish()
        val calls = (textResult.calls + reasoningResult.calls).distinctBy { call ->
            "${call.name.lowercase()}\\u0000${call.argumentsJson}"
        }
        return DsmlChannelsResult(
            tailText = textResult.visibleText,
            tailReasoning = reasoningResult.visibleText,
            calls = calls,
            malformed = textResult.malformed || reasoningResult.malformed,
        )
    }
}

/** Incrementally removes DSML from streamed assistant text and emits native calls at EOF. */
internal class DsmlToolStreamAdapter(private val allowedTools: Set<String>) {
'''
replace_once(protocol, anchor, insert)

replace_once(protocol, '        const val MARKER_LOOKBEHIND = 96\n', '        const val MARKER_LOOKBEHIND = 256\n')

tests = "app/src/test/java/app/arbor/chat/provider/DsmlToolProtocolTest.kt"
replace_once(
    tests,
    '''    @Test
    fun malformedOrUnapprovedProtocolIsNotRenderedOrExecuted() {
''',
    '''    @Test
    fun reasoningChannelProtocolIsQuarantinedAndRecovered() {
        val adapter = DsmlChannelsAdapter(setOf("web_fetch"))
        val source = """
            < | | DSML | | tool_calls >
            < | | DSML | | invoke name="web_fetch" >
            < | | DSML | | parameter name="url" string="true" >https://example.com< / | | DSML | | parameter >
            < / | | DSML | | invoke >
            < / | | DSML | | tool_calls >
        """.trimIndent()

        val delta = adapter.accept(textDelta = "", reasoningDelta = source)
        val result = adapter.finish()

        assertEquals("", delta.text + delta.reasoning + result.tailText + result.tailReasoning)
        assertFalse(result.malformed)
        assertEquals(1, result.calls.size)
        assertEquals("web_fetch", result.calls.single().name)
    }

    @Test
    fun htmlEscapedUnicodeFenceIsRecovered() {
        val source = """
            &lt;\u200b│\u200b│DSML││tool_calls&gt;
            &lt;││DSML││invoke name="web_fetch"&gt;
            &lt;││DSML││parameter name="url" string="true"&gt;https://example.com?a=1&amp;b=2&lt;/││DSML││parameter&gt;
            &lt;/││DSML││invoke&gt;
            &lt;/││DSML││tool_calls&gt;
        """.trimIndent()

        val result = DsmlToolProtocol.parseBlock(source, setOf("web_fetch"))
        val url = Json.parseToJsonElement(result.calls.single().argumentsJson)
            .jsonObject.getValue("url").jsonPrimitive.content

        assertFalse(result.malformed)
        assertEquals("https://example.com?a=1&b=2", url)
    }

    @Test
    fun unparseableDsmlStillTriggersProtocolHint() {
        val source = "<broken DSML marker tool_calls><broken DSML invoke>"
        assertTrue(DsmlToolProtocol.containsProtocolHint(source))
    }

    @Test
    fun malformedOrUnapprovedProtocolIsNotRenderedOrExecuted() {
''',
)

replace_once(
    "app/build.gradle.kts",
    '''        versionCode = 158
        versionName = "0.20.32"
''',
    '''        versionCode = 159
        versionName = "0.20.33"
''',
)

Path("docs/releases/RELEASE_NOTES_0.20.33.md").write_text('''# Arbor 0.20.33

- Quarantines DeepSeek tool-turn content and reasoning until Arbor can classify the completed response, preventing raw DSML from ever being committed to chat.
- Parses DSML emitted through either `content` or `reasoning_content`, including HTML-escaped brackets, Unicode pipe glyphs, zero-width format characters, and whitespace-separated fences.
- Detects unparseable DSML-shaped output and retries with the strict structured-tool correction prompt; after the retry, unsafe protocol text is rejected rather than displayed.
- Expands DeepSeek detection across provider id, provider name, base URL, model id, and model display name.
- Adds regression tests reproducing the on-device prayer-time `web_fetch` failure in the reasoning channel and escaped Unicode fence variants.
''')
