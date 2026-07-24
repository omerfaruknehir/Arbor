package app.arbor.chat.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorizontalGesturePriorityRegistryTest {
    @Test fun regionOwnershipAndRemoval() {
        val registry = HorizontalGesturePriorityRegistry()
        val first = Any()
        val second = Any()
        registry.update(first, Rect(10f, 20f, 110f, 80f))
        assertTrue(registry.owns(Offset(50f, 50f)))
        assertFalse(registry.owns(Offset(9f, 50f)))
        registry.update(second, Rect(25f, 25f, 75f, 75f))
        registry.remove(first)
        assertTrue(registry.owns(Offset(30f, 30f)))
        registry.remove(second)
        assertFalse(registry.owns(Offset(30f, 30f)))
    }

    @Test fun collapsedBoundsDoNotOwnPointers() {
        val registry = HorizontalGesturePriorityRegistry()
        registry.update(Any(), Rect(0f, 0f, 0f, 100f))
        assertFalse(registry.owns(Offset(0f, 20f)))
    }
}
