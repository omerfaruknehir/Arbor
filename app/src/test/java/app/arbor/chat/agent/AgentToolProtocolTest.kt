package app.arbor.chat.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentToolProtocolTest {
    @Test
    fun extractsAndHidesToolFence() {
        val directive = AgentToolProtocol.extract(
            "Checking that now.\n```arbor-tool\n{\"type\":\"web_search\",\"query\":\"Android Room migration\"}\n```",
        )!!

        assertEquals("web_search", directive.request.type)
        assertEquals("Android Room migration", directive.request.query)
        assertEquals("Checking that now.", directive.visibleText)
    }

    @Test
    fun ignoresOrdinaryCodeFences() {
        assertNull(AgentToolProtocol.extract("```python\nprint('hello')\n```"))
    }

    @Test
    fun extractsPublicPageFetch() {
        val directive = AgentToolProtocol.extract(
            "```arbor-tool\n{\"type\":\"web_fetch\",\"url\":\"https://example.com/page\"}\n```",
        )!!
        assertEquals("web_fetch", directive.request.type)
        assertEquals("https://example.com/page", directive.request.url)
        assertEquals("", directive.visibleText)
    }

    @Test
    fun extractsUbuntuCommand() {
        val directive = AgentToolProtocol.extract(
            "```arbor-tool\n{\"type\":\"ubuntu_exec\",\"command\":\"file incoming/photo.jpg\"}\n```",
        )!!
        assertEquals("ubuntu_exec", directive.request.type)
        assertEquals("file incoming/photo.jpg", directive.request.command)
    }

    @Test
    fun extractsDedicatedFileDeliveryAtProtocolPosition() {
        val directive = AgentToolProtocol.extract(
            "```arbor-tool\n{\"type\":\"send_file\",\"path\":\"results/chart.png\",\"caption\":\"Final chart\"}\n```",
        )!!
        assertEquals("send_file", directive.request.type)
        assertEquals("results/chart.png", directive.request.path)
        assertEquals("Final chart", directive.request.caption)
    }
}
