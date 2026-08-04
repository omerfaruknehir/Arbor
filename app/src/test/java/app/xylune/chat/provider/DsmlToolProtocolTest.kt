package app.xylune.chat.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DsmlToolProtocolTest {
    @Test
    fun parsesDeepSeekCallsWithoutLeakingProtocolText() {
        val source = """
            <|DSML|tool_calls>
            <|DSML|invoke name="web_fetch">
            <|DSML|parameter name="url" string="true">https://example.com/one</|DSML|parameter>
            </|DSML|invoke>
            <|DSML|invoke name="web_fetch">
            <|DSML|parameter name="url" string="true">https://example.com/two?a=1&amp;b=2</|DSML|parameter>
            </|DSML|invoke>
            </|DSML|tool_calls>
        """.trimIndent()

        val result = DsmlToolProtocol.parseBlock(source, setOf("web_fetch"))

        assertFalse(result.malformed)
        assertEquals("", result.visibleText)
        assertEquals(2, result.calls.size)
        assertEquals("web_fetch", result.calls.first().name)
        assertEquals(
            "https://example.com/two?a=1&b=2",
            Json.parseToJsonElement(result.calls.last().argumentsJson)
                .jsonObject.getValue("url").jsonPrimitive.content,
        )
    }

    @Test
    fun splitStreamingMarkersStayHiddenAndBecomeNativeCalls() {
        val adapter = DsmlToolStreamAdapter(setOf("web_fetch"))
        val chunks = listOf(
            "<|DSM",
            "L|tool_calls><|DSML|invoke name=\"web_fetch\"><|DSML|parameter ",
            "name=\"url\" string=\"true\">https://example.com</|DSML|parameter>",
            "</|DSML|invoke></|DSML|tool_calls>",
        )
        val streamed = chunks.joinToString(separator = "") { adapter.accept(it) }
        val result = adapter.finish()

        assertEquals("", streamed + result.visibleText)
        assertEquals(1, result.calls.size)
        assertEquals("web_fetch", result.calls.single().name)
    }

    @Test
    fun doublePipeMarkersFromDeepSeekV4StayHiddenAndBecomeNativeCalls() {
        val adapter = DsmlToolStreamAdapter(setOf("compile_widget"))
        val chunks = listOf(
            "<||DSML||tool_calls><||DSML||invoke name=\"compile_widget\"><||DSML||parameter ",
            "name=\"source\" string=\"true\">{\"schema\":\"xylune-widget/1\",",
            "\"id\":\"namaz-vakti\"}</||DSML||parameter></||DSML||invoke>",
            "</||DSML||tool_calls>",
        )
        val streamed = chunks.joinToString(separator = "") { adapter.accept(it) }
        val result = adapter.finish()
        val arguments = Json.parseToJsonElement(result.calls.single().argumentsJson).jsonObject
        val widgetSource = Json.parseToJsonElement(arguments.getValue("source").jsonPrimitive.content).jsonObject

        assertEquals("", streamed + result.visibleText)
        assertEquals(1, result.calls.size)
        assertEquals("compile_widget", result.calls.single().name)
        assertEquals("namaz-vakti", widgetSource.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun whitespaceSeparatedDoublePipeMarkersAreAccepted() {
        val source = """
            < | | DSML | | tool_calls >
            < | | DSML | | invoke name="compile_widget" >
            < | | DSML | | parameter name="source" string="true" >{"id":"clock"}< / | | DSML | | parameter >
            < / | | DSML | | invoke >
            < / | | DSML | | tool_calls >
        """.trimIndent()

        val result = DsmlToolProtocol.parseBlock(source, setOf("compile_widget"))

        assertFalse(result.malformed)
        assertEquals("", result.visibleText)
        assertEquals(1, result.calls.size)
    }

    @Test
    fun extractsOnlyTrailingAllowedPlainTextCalls() {
        val result = PlainTextToolCallDetector.extractTrailingCalls(
            "I need fresh data before answering.\ncompile_widget{\"source\":\"widget-json\"}",
            setOf("compile_widget"),
        )

        assertEquals(1, result.size)
        assertEquals("compile_widget", result.single().name)
        assertEquals("{\"source\":\"widget-json\"}", result.single().argumentsJson)
    }

    @Test
    fun ignoresToolSyntaxInsideCodeFenceOrFollowedByProse() {
        assertTrue(
            PlainTextToolCallDetector.extractTrailingCalls(
                "Example:\n```text\ncompile_widget{\"source\":\"example\"}\n```\nThis is documentation.",
                setOf("compile_widget"),
            ).isEmpty(),
        )
        assertTrue(
            PlainTextToolCallDetector.extractTrailingCalls(
                "compile_widget{\"source\":\"example\"} but do not execute it",
                setOf("compile_widget"),
            ).isEmpty(),
        )
    }

    @Test
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
            &lt;​│​│DSML││tool_calls&gt;
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
        val adapter = DsmlToolStreamAdapter(setOf("web_fetch"))
        val visible = adapter.accept(
            "<|DSML|tool_calls><|DSML|invoke name=\"linux_exec\"></|DSML|invoke></|DSML|tool_calls>",
        )
        val result = adapter.finish()

        assertTrue((visible + result.visibleText).contains("malformed tool request"))
        assertTrue(result.calls.isEmpty())
        assertFalse((visible + result.visibleText).contains("linux_exec"))
    }

    @Test
    fun ordinaryProseStreamsImmediatelyWithoutACharacterGate() {
        val adapter = DsmlToolStreamAdapter(setOf("web_fetch"))

        assertEquals("Hello", adapter.accept("Hello"))
        assertEquals(" world", adapter.accept(" world"))
        assertEquals("", adapter.finish().visibleText)
    }

    @Test
    fun onlyAnActuallyAmbiguousMarkerSuffixIsHeld() {
        val adapter = DsmlToolStreamAdapter(setOf("web_fetch"))

        assertEquals("Hello ", adapter.accept("Hello <"))
        assertEquals("<there", adapter.accept("there"))
        assertEquals("", adapter.finish().visibleText)
    }

    @Test
    fun splitDsmlPrefixRemainsHiddenUntilTheMarkerCompletes() {
        val adapter = DsmlToolStreamAdapter(setOf("web_fetch"))

        assertEquals("Before ", adapter.accept("Before <|DSM"))
        assertEquals("", adapter.accept("L|tool_calls>"))
        val result = adapter.finish()
        assertTrue(result.malformed)
        assertFalse(result.visibleText.contains("DSML"))
    }

}
