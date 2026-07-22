package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerGestureTest {
    @Test
    fun sensitiveEdgeSwipeOpensWithoutCapturingVerticalGestures() {
        assertTrue(shouldOpenDrawerFromEdgeSwipe(8f, 56f, 11f, 3f, 10f))
        assertFalse(shouldOpenDrawerFromEdgeSwipe(70f, 56f, 30f, 0f, 10f))
        assertFalse(shouldOpenDrawerFromEdgeSwipe(8f, 56f, 9f, 0f, 10f))
        assertFalse(shouldOpenDrawerFromEdgeSwipe(8f, 56f, 12f, 20f, 10f))
    }
}
