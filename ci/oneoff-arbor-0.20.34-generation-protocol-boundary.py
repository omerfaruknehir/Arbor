from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected patch anchor not found in {path}: {old[:160]!r}")
    file.write_text(text.replace(old, new, 1))


worker = Path("app/src/main/java/app/arbor/chat/generation/GenerationWorker.kt")
text = worker.read_text()

old_import = '''import app.arbor.chat.provider.ChatRequest
import app.arbor.chat.provider.InputMessage
'''
new_import = '''import app.arbor.chat.provider.ChatRequest
import app.arbor.chat.provider.DsmlChannelsAdapter
import app.arbor.chat.provider.DsmlToolProtocol
import app.arbor.chat.provider.InputMessage
'''
if old_import not in text:
    raise SystemExit("GenerationWorker import anchor not found")
text = text.replace(old_import, new_import, 1)

old_tools = '''        val nativeToolDefinitions = if (model.supportsTools && !directImageModel) ArborNativeTools.definitions(conversation) else emptyList()
        val messages = ContextAssembler(container.database.attachmentDao()).assemble(
'''
new_tools = '''        val configuredNativeToolDefinitions = if (!directImageModel) ArborNativeTools.definitions(conversation) else emptyList()
        val nativeToolDefinitions = if (model.supportsTools) configuredNativeToolDefinitions else emptyList()
        val protocolToolNames = configuredNativeToolDefinitions.mapTo(linkedSetOf()) { it.name.lowercase() }
        val needsGenerationProtocolBoundary = provider.kind == ProviderKind.OPENAI_COMPATIBLE &&
            (protocolToolNames.isNotEmpty() || listOf(
                provider.id,
                provider.displayName,
                provider.baseUrl,
                model.modelId,
                model.displayName,
            ).any { it.contains("deepseek", ignoreCase = true) })
        val messages = ContextAssembler(container.database.attachmentDao()).assemble(
'''
if old_tools not in text:
    raise SystemExit("GenerationWorker tool-definition anchor not found")
text = text.replace(old_tools, new_tools, 1)

old_attempt = '''                var passReceived = false
                var passFinishReason: String? = null
                try {
'''
new_attempt = '''                var passReceived = false
                var passFinishReason: String? = null
                var boundaryQuarantine = if (needsGenerationProtocolBoundary) {
                    DsmlChannelsAdapter(protocolToolNames)
                } else {
                    null
                }
                val boundaryRawText = StringBuilder()
                val boundaryRawReasoning = StringBuilder()

                suspend fun rollbackCurrentCallState() {
                    closeOpenStreamEvents()
                    savedContent = savedContent.substring(0, callContentStart.coerceAtMost(savedContent.length))
                    savedReasoning = savedReasoning.substring(0, callReasoningStart.coerceAtMost(savedReasoning.length))
                    if (timeline.size > callTimelineStart) {
                        timeline.subList(callTimelineStart, timeline.size).clear()
                    }
                    passToolCalls.clear()
                    passNativePayload = ""
                    progressEventIds.clear()
                    progressWeights.clear()
                    pendingCharacters = 0
                    passReceived = false
                    passInput = null
                    passOutput = null
                    passCached = null
                    passFinishReason = null
                    boundaryQuarantine = if (needsGenerationProtocolBoundary) {
                        DsmlChannelsAdapter(protocolToolNames)
                    } else {
                        null
                    }
                    boundaryRawText.clear()
                    boundaryRawReasoning.clear()
                    timelineDirty = true
                    lastFlush = System.currentTimeMillis()
                    persistTimeline(forceMetadata = true)
                }

                try {
'''
if old_attempt not in text:
    raise SystemExit("GenerationWorker attempt anchor not found")
text = text.replace(old_attempt, new_attempt, 1)

old_reset = '''                        if (chunk.resetCurrentAttempt) {
                            closeOpenStreamEvents()
                            savedContent = savedContent.substring(0, callContentStart.coerceAtMost(savedContent.length))
                            savedReasoning = savedReasoning.substring(0, callReasoningStart.coerceAtMost(savedReasoning.length))
                            if (timeline.size > callTimelineStart) {
                                timeline.subList(callTimelineStart, timeline.size).clear()
                            }
                            passToolCalls.clear()
                            passNativePayload = ""
                            progressEventIds.clear()
                            progressWeights.clear()
                            pendingCharacters = 0
                            passReceived = false
                            passInput = null
                            passOutput = null
                            passCached = null
                            passFinishReason = null
                            timelineDirty = true
                            lastFlush = System.currentTimeMillis()
                            persistTimeline(forceMetadata = true)
                            return@stream
                        }
'''
new_reset = '''                        if (chunk.resetCurrentAttempt) {
                            rollbackCurrentCallState()
                            return@stream
                        }
'''
if old_reset not in text:
    raise SystemExit("GenerationWorker reset anchor not found")
text = text.replace(old_reset, new_reset, 1)

block_start = '''                        if (chunk.text.isNotEmpty() || chunk.reasoning.isNotEmpty() || chunk.toolCallProgress.isNotEmpty() || chunk.toolCalls.isNotEmpty() || chunk.generatedImages.isNotEmpty()) passReceived = true
'''
block_end = '''                    }
                    flush()
                    lastFinishReason = passFinishReason ?: lastFinishReason
'''
start = text.find(block_start)
end = text.find(block_end, start)
if start < 0 or end < 0:
    raise SystemExit("GenerationWorker provider callback block not found")
original_block = text[start:end]
protected_prefix = '''                        boundaryRawText.append(chunk.text)
                        boundaryRawReasoning.append(chunk.reasoning)
                        val boundaryDelta = boundaryQuarantine?.accept(chunk.text, chunk.reasoning)
                        val safeChunk = if (boundaryDelta == null) {
                            chunk
                        } else {
                            chunk.copy(text = boundaryDelta.text, reasoning = boundaryDelta.reasoning)
                        }
'''
protected_block = protected_prefix + original_block.replace("chunk.", "safeChunk.")
text = text[:start] + protected_block + text[end:]

old_after_stream = block_end
new_after_stream = '''                    }
                    val boundaryResult = boundaryQuarantine?.finish()
                    if (boundaryResult != null) {
                        val protocolHint = DsmlToolProtocol.containsProtocolHint(boundaryRawText) ||
                            DsmlToolProtocol.containsProtocolHint(boundaryRawReasoning)
                        if (boundaryResult.malformed || (protocolHint && boundaryResult.calls.isEmpty())) {
                            rollbackCurrentCallState()
                            throw ProviderProtocolException(
                                "The provider emitted malformed text-encoded tool protocol. Arbor quarantined it instead of displaying it.",
                            )
                        }
                        if (boundaryResult.tailReasoning.isNotEmpty()) {
                            savedReasoning += boundaryResult.tailReasoning
                            appendTimeline("reasoning", boundaryResult.tailReasoning)
                            pendingCharacters += boundaryResult.tailReasoning.length
                            passReceived = true
                        }
                        if (boundaryResult.tailText.isNotEmpty()) {
                            savedContent += boundaryResult.tailText
                            appendTimeline("text", boundaryResult.tailText)
                            pendingCharacters += boundaryResult.tailText.length
                            passReceived = true
                        }
                        val recoveredCalls = boundaryResult.calls.filterNot { recovered ->
                            passToolCalls.any { existing ->
                                existing.name.equals(recovered.name, ignoreCase = true) &&
                                    existing.argumentsJson == recovered.argumentsJson
                            }
                        }
                        if (recoveredCalls.isNotEmpty()) {
                            if (nativeToolDefinitions.isEmpty()) {
                                rollbackCurrentCallState()
                                throw ProviderProtocolException(
                                    "The provider emitted a tool request even though this model has no enabled native-tool channel. Arbor did not execute or display it.",
                                )
                            }
                            passToolCalls += recoveredCalls
                            passReceived = true
                            passFinishReason = "tool_calls"
                        }
                    }
                    flush()
                    lastFinishReason = passFinishReason ?: lastFinishReason
'''
if old_after_stream not in text:
    raise SystemExit("GenerationWorker post-stream anchor not found after callback rewrite")
text = text.replace(old_after_stream, new_after_stream, 1)
worker.write_text(text)


test_path = "app/src/test/java/app/arbor/chat/provider/DsmlToolProtocolTest.kt"
replace_once(
    test_path,
    '''    @Test
    fun malformedOrUnapprovedProtocolIsNotRenderedOrExecuted() {
''',
    '''    @Test
    fun prayerWidgetSecondRoundKeepsProseButQuarantinesVisibleDsml() {
        val adapter = DsmlChannelsAdapter(setOf("web_fetch"))
        val textPartOne = """
            Aladhan API'sine compile_widget erişemiyor. Alternatif bir namaz vakti API'si arayayım.

            < | | DSML | | to
        """.trimIndent()
        val textPartTwo = """
            ol_calls>< | | DSML | | invoke name="web_fetch">< | | DSML | | parameter name="url" string="true">https://api.pray.zone/v2/times/today.json?latitude=39.9334&longitude=32.8597&method=13< / | | DSML | | parameter>< / | | DSML | | invoke>< / | | DSML | | tool_calls>
        """.trimIndent()
        val reasoning = "İlginç! Hâlâ EOFException. Alternatif bir API deneyeceğim."

        val first = adapter.accept(textPartOne, reasoning)
        val second = adapter.accept(textPartTwo, "")
        val result = adapter.finish()
        val visibleText = first.text + second.text + result.tailText
        val visibleReasoning = first.reasoning + second.reasoning + result.tailReasoning
        val url = Json.parseToJsonElement(result.calls.single().argumentsJson)
            .jsonObject.getValue("url").jsonPrimitive.content

        assertTrue(visibleText.contains("Aladhan API'sine"))
        assertTrue(visibleReasoning.contains("EOFException"))
        assertFalse(visibleText.contains("DSML"))
        assertFalse(visibleReasoning.contains("DSML"))
        assertFalse(result.malformed)
        assertEquals("web_fetch", result.calls.single().name)
        assertEquals(
            "https://api.pray.zone/v2/times/today.json?latitude=39.9334&longitude=32.8597&method=13",
            url,
        )
    }

    @Test
    fun malformedOrUnapprovedProtocolIsNotRenderedOrExecuted() {
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

Path("docs/releases/RELEASE_NOTES_0.20.34.md").write_text(
    """# Arbor 0.20.34

- Adds a second, provider-independent protocol boundary in `GenerationWorker`, so raw DSML cannot reach Room, the timeline, the reasoning panel, or automatic chat titles even when a provider-specific adapter is bypassed.
- Applies the boundary across every OpenAI-compatible tool turn and across DeepSeek finalization turns where the native `tools` array may intentionally be empty.
- Recovers valid DSML into the existing native tool pipeline, deduplicates it against structured calls, and rejects malformed or unavailable-tool requests without displaying or executing them.
- Resets the boundary together with provider correction retries, preventing stale protocol fragments from one attempt contaminating the next.
- Adds a regression test reproducing the visible `pray.zone` prayer-widget failure with normal prose, reasoning, and whitespace-separated DSML in a later tool round.
"""
)
