package app.xylune.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageResetCountdownTest {
    @Test
    fun countdownUsesActualTimestampDifference() {
        val now = 1_800_000_000L
        assertEquals("now", usageResetCountdown(now - 1, now))
        assertEquals("in 1s", usageResetCountdown(now + 1, now))
        assertEquals("in 2h 5m", usageResetCountdown(now + 7_501, now))
        assertEquals("in 2d 3h", usageResetCountdown(now + 183_600, now))
        assertEquals("in 2h 59m", usageResetCountdown(now + 10_799, now))
        assertEquals("in 59m 59s", usageResetCountdown(now + 3_599, now))
    }
}
