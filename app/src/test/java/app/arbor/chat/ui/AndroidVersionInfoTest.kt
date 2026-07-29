package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidVersionInfoTest {
    @Test
    fun `minimum version is clearly open ended`() {
        assertEquals("Android 8.0+ · API 26+", androidVersionSummary(26, isMinimum = true))
    }

    @Test
    fun `current Android releases map to their API levels`() {
        assertEquals("Android 15 · API 35", androidVersionSummary(35))
        assertEquals("Android 16 · API 36", androidVersionSummary(36))
        assertEquals("Android 17 · API 37", androidVersionSummary(37))
    }

    @Test
    fun `future unknown APIs remain accurate`() {
        assertEquals("API 38", androidVersionSummary(38))
    }
}
