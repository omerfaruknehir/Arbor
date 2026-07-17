package app.arbor.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    @Test
    fun legacyHidePreferenceBecomesCollapsedWithoutHidingCards() {
        assertEquals(ReasoningVisibility.COLLAPSED, Converters().toReasoningVisibility("HIDE"))
    }

    @Test
    fun auxiliaryPolicyRoundTrips() {
        val converters = Converters()
        AuxiliaryMode.entries.forEach { mode ->
            assertEquals(mode, converters.toAuxiliaryMode(converters.fromAuxiliaryMode(mode)))
        }
    }
}
