package app.arbor.chat.provider

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
}
