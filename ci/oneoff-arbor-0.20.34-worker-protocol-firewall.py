from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected patch anchor not found in {path}: {old[:160]!r}")
    file.write_text(text.replace(old, new, 1))


worker = "app/src/main/java/app/arbor/chat/generation/GenerationWorker.kt"
replace_once(
    worker,
    '''import app.arbor.chat.provider.ChatRequest
import app.arbor.chat.provider.InputMessage
''',
    '''import app.arbor.chat.provider.ChatRequest
import app.arbor.chat.provider.DsmlChannelDelta
import app.arbor.chat.provider.DsmlChannelsAdapter
import app.arbor.chat.provider.DsmlToolProtocol
import app.arbor.chat.provider.InputMessage
''',
)

replace_once(
    worker,
    '''                    val (request, preflightInputTokens) = prepareCountedRequest(baseRequest)
                    passInput = preflightInputTokens
                    container.providers.get(provider.kind).stream(request) { chunk ->
''',
    '''                    val (request, preflightInputTokens) = prepareCountedRequest(baseRequest)
                    passInput = preflightInputTokens

                    // Provider adapters are not the final trust boundary. Some gateways route
                    // text/reasoning through a different provider implementation or transform
                    // the stream after the provider parser. Apply the DSML firewall again here,
                    // immediately before anything can reach persistent message state.
                    val workerAllowedTools = nativeToolDefinitions
                        .mapTo(linkedSetOf()) { definition -> definition.name.lowercase() }
                    val workerToolCallsEnabled = request.tools.isNotEmpty()
                    var workerProtocolAdapter = workerAllowedTools
                        .takeIf { it.isNotEmpty() }
                        ?.let(::DsmlChannelsAdapter)
                    val workerRawText = StringBuilder()
                    val workerRawReasoning = StringBuilder()

                    container.providers.get(provider.kind).stream(request) { chunk ->
''',
)

replace_once(
    worker,
    '''                            persistTimeline(forceMetadata = true)
                            return@stream
                        }
                        if (chunk.text.isNotEmpty() || chunk.reasoning.isNotEmpty() || chunk.toolCallProgress.isNotEmpty() || chunk.toolCalls.isNotEmpty() || chunk.generatedImages.isNotEmpty()) passReceived = true
''',
    '''                            persistTimeline(forceMetadata = true)
                            workerRawText.clear()
                            workerRawReasoning.clear()
                            workerProtocolAdapter = workerAllowedTools
                                .takeIf { it.isNotEmpty() }
                                ?.let(::DsmlChannelsAdapter)
                            return@stream
                        }
                        if (chunk.text.isNotEmpty() || chunk.reasoning.isNotEmpty() || chunk.toolCallProgress.isNotEmpty() || chunk.toolCalls.isNotEmpty() || chunk.generatedImages.isNotEmpty()) passReceived = true
                        workerRawText.append(chunk.text)
                        workerRawReasoning.append(chunk.reasoning)
                        val workerAdapted = workerProtocolAdapter?.accept(chunk.text, chunk.reasoning)
                            ?: DsmlChannelDelta(chunk.text, chunk.reasoning)
''',
)

replace_once(
    worker,
    '''                        if (chunk.reasoning.isNotEmpty()) {
                            savedReasoning += chunk.reasoning
                            appendTimeline("reasoning", chunk.reasoning)
                            pendingCharacters += chunk.reasoning.length
                        }
                        if (chunk.text.isNotEmpty()) {
                            savedContent += chunk.text
                            appendTimeline("text", chunk.text)
                            pendingCharacters += chunk.text.length
                        }
''',
    '''                        if (workerAdapted.reasoning.isNotEmpty()) {
                            savedReasoning += workerAdapted.reasoning
                            appendTimeline("reasoning", workerAdapted.reasoning)
                            pendingCharacters += workerAdapted.reasoning.length
                        }
                        if (workerAdapted.text.isNotEmpty()) {
                            savedContent += workerAdapted.text
                            appendTimeline("text", workerAdapted.text)
                            pendingCharacters += workerAdapted.text.length
                        }
''',
)

replace_once(
    worker,
    '''                    }
                    flush()
                    lastFinishReason = passFinishReason ?: lastFinishReason
''',
    '''                    }

                    workerProtocolAdapter?.finish()?.let { protocolResult ->
                        val protocolDetected = DsmlToolProtocol.containsProtocolHint(workerRawText) ||
                            DsmlToolProtocol.containsProtocolHint(workerRawReasoning)
                        if (protocolResult.calls.isNotEmpty()) {
                            if (!workerToolCallsEnabled) {
                                throw ProviderProtocolException(
                                    "The provider emitted a tool request after tools were disabled for finalization. Arbor blocked the protocol instead of displaying it.",
                                )
                            }
                            if (passToolCalls.isEmpty()) passToolCalls += protocolResult.calls
                        }
                        if (passToolCalls.isEmpty() && (protocolResult.malformed || protocolDetected)) {
                            throw ProviderProtocolException(
                                "The provider emitted malformed text-encoded tool protocol. Arbor blocked it before it could be saved to the chat.",
                            )
                        }
                        if (protocolResult.tailReasoning.isNotEmpty()) {
                            savedReasoning += protocolResult.tailReasoning
                            appendTimeline("reasoning", protocolResult.tailReasoning)
                            pendingCharacters += protocolResult.tailReasoning.length
                        }
                        if (protocolResult.tailText.isNotEmpty()) {
                            savedContent += protocolResult.tailText
                            appendTimeline("text", protocolResult.tailText)
                            pendingCharacters += protocolResult.tailText.length
                        }
                    }

                    flush()
                    lastFinishReason = passFinishReason ?: lastFinishReason
''',
)

build = "app/build.gradle.kts"
replace_once(
    build,
    '''        versionCode = 159
        versionName = "0.20.33"
''',
    '''        versionCode = 160
        versionName = "0.20.34"
''',
)

test = "app/src/test/java/app/arbor/chat/provider/DsmlToolProtocolTest.kt"
replace_once(
    test,
    '''    @Test
    fun malformedOrUnapprovedProtocolIsNotRenderedOrExecuted() {
''',
    '''    @Test
    fun spacedProtocolAfterVisibleProseNeverLeaksAndStillBecomesAToolCall() {
        val adapter = DsmlChannelsAdapter(setOf("web_fetch"))
        val chunks = listOf(
            "Aladhan API failed. Trying another source.\\n< | | DSM",
            "L | | tool_calls>< | | DSML | | invoke name=\\\"web_fetch\\\" >",
            "< | | DSML | | parameter name=\\\"url\\\" string=\\\"true\\\">https://api.pray.zone/v2/times/today.json?latitude=39.9334&longitude=32.8597&method=13",
            "< / | | DSML | | parameter>< / | | DSML | | invoke>< / | | DSML | | tool_calls>",
        )
        val streamed = buildString {
            chunks.forEach { chunk ->
                val delta = adapter.accept(textDelta = chunk, reasoningDelta = "")
                append(delta.text)
                append(delta.reasoning)
            }
        }
        val result = adapter.finish()
        val visible = streamed + result.tailText + result.tailReasoning
        val arguments = Json.parseToJsonElement(result.calls.single().argumentsJson).jsonObject

        assertEquals("Aladhan API failed. Trying another source.\\n", visible)
        assertFalse(visible.contains("DSML", ignoreCase = true))
        assertFalse(result.malformed)
        assertEquals("web_fetch", result.calls.single().name)
        assertEquals(
            "https://api.pray.zone/v2/times/today.json?latitude=39.9334&longitude=32.8597&method=13",
            arguments.getValue("url").jsonPrimitive.content,
        )
    }

    @Test
    fun malformedOrUnapprovedProtocolIsNotRenderedOrExecuted() {
''',
)

release_notes = Path("docs/releases/RELEASE_NOTES_0.20.34.md")
release_notes.write_text(
    """# Arbor 0.20.34

- Moves DSML filtering to the generation worker, the final boundary before streamed text or reasoning is persisted.
- Covers every provider implementation and gateway path, not only OpenAI-compatible provider parsing.
- Reconstructs valid `web_fetch`, `compile_widget`, and other allowed native calls from spaced DSML while keeping protocol text out of chat and titles.
- Blocks malformed protocol before persistence and refuses tool calls emitted after tool-budget finalization.
- Adds a regression fixture matching the on-device prayer-time failure where prose is followed by `< | | DSML | | tool_calls>`.
"""
)
