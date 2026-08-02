package app.arbor.chat.widgets

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataSourcesTest {
    @Test
    fun shortJsonBodyStopsAtEofWithoutThrowing() {
        val payload = "{\"latitude\":41.0082,\"longitude\":28.9784}"
        val source = Buffer().writeUtf8(payload)

        assertEquals(payload, readWidgetHttpBody(source, maxBytes = 1_024))
    }

    @Test
    fun exactLimitBodyIsAccepted() {
        val payload = "12345678"
        val source = Buffer().writeUtf8(payload)

        assertEquals(payload, readWidgetHttpBody(source, maxBytes = 8))
    }

    @Test
    fun oneByteOverLimitIsRejectedWithoutReadingUnboundedData() {
        val source = Buffer().writeUtf8("123456789more-data-that-must-not-be-consumed")

        val failure = runCatching {
            readWidgetHttpBody(source, maxBytes = 8)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("larger than 8 bytes") == true)
        assertEquals("more-data-that-must-not-be-consumed", source.readUtf8())
    }
}
