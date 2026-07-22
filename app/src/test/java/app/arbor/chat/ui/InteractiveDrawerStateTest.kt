package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveDrawerStateTest {
    private val width = 1_000f

    @Test fun twentyPercentDragRemainsTwentyPercentBeforeRelease() {
        assertEquals(.20f, DrawerPhysics.fraction(DrawerPhysics.dragOffset(0f, 200f, width), width), .001f)
    }

    @Test fun reversingDragImmediatelyReversesDrawerOffset() {
        val forward = DrawerPhysics.dragOffset(0f, 400f, width)
        val reversed = DrawerPhysics.dragOffset(0f, 200f, width)
        assertEquals(400f, forward, .001f)
        assertEquals(200f, reversed, .001f)
        assertTrue(reversed < forward)
    }

    @Test fun releaseBelowThresholdSettlesClosed() {
        assertEquals(DrawerAnchor.CLOSED, DrawerPhysics.settleTarget(290f, width, 0f, 950f))
    }

    @Test fun releaseAboveThresholdSettlesOpen() {
        assertEquals(DrawerAnchor.OPEN, DrawerPhysics.settleTarget(340f, width, 0f, 950f))
    }

    @Test fun fastRightwardFlingOpensBelowThreshold() {
        assertEquals(DrawerAnchor.OPEN, DrawerPhysics.settleTarget(100f, width, 1_200f, 950f))
    }

    @Test fun fastLeftwardFlingClosesAboveThreshold() {
        assertEquals(DrawerAnchor.CLOSED, DrawerPhysics.settleTarget(800f, width, -1_200f, 950f))
    }

    @Test fun verticalEdgeDragPassesToChat() {
        assertEquals(DrawerGestureIntent.PASS_TO_CONTENT, DrawerPhysics.gestureIntent(7f, 20f, 6f))
    }

    @Test fun smallVerticalFingerDriftDoesNotRejectIntendedPull() {
        assertEquals(DrawerGestureIntent.TRACK_DRAWER, DrawerPhysics.gestureIntent(7f, 5f, 4f))
        assertEquals(DrawerGestureIntent.UNDECIDED, DrawerPhysics.gestureIntent(2f, 7f, 4f))
        assertEquals(DrawerGestureIntent.PASS_TO_CONTENT, DrawerPhysics.gestureIntent(2f, 10f, 4f))
    }

    @Test fun horizontalDragAwayFromEdgeStartsAfterMovementThreshold() {
        assertEquals(DrawerGestureIntent.UNDECIDED, DrawerPhysics.gestureIntent(5f, 1f, 6f))
        assertEquals(DrawerGestureIntent.TRACK_DRAWER, DrawerPhysics.gestureIntent(7f, 1f, 6f))
    }

    @Test fun drawerMathNeverMutatesChatListPosition() {
        val index = 42
        val scrollOffset = 317
        DrawerPhysics.dragOffset(0f, 500f, width)
        DrawerPhysics.settleTarget(500f, width, 0f, 950f)
        assertEquals(42, index)
        assertEquals(317, scrollOffset)
    }

    @Test fun crossingActivationDistanceOnlyStartsTrackingAndDoesNotChooseOpenAnchor() {
        assertEquals(DrawerGestureIntent.TRACK_DRAWER, DrawerPhysics.gestureIntent(7f, 1f, 6f))
        assertEquals(7f, DrawerPhysics.dragOffset(0f, 7f, width), .001f)
        assertEquals(DrawerAnchor.CLOSED, DrawerPhysics.settleTarget(7f, width, 0f, 950f))
    }
}
