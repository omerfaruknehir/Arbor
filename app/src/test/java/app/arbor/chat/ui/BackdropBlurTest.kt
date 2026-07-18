package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test
    fun enabledBlurHasVisibleBaseline() {
        assertEquals(0.35f, arborBlurProgress(0f), 0.0001f)
    }

    @Test
    fun blurProgressIsClampedAndMonotonic() {
        val values = listOf(-1f, 0f, .25f, .5f, .75f, 1f, 2f).map(::arborBlurProgress)
        assertEquals(0.35f, values.first(), 0.0001f)
        assertEquals(1f, values.last(), 0.0001f)
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }
}
