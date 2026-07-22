package app.arbor.chat.ui

import kotlin.math.abs

internal enum class DrawerAnchor { CLOSED, OPEN }

internal enum class DrawerGestureIntent { UNDECIDED, TRACK_DRAWER, PASS_TO_CONTENT, REJECTED }

internal object DrawerPhysics {
    const val POSITIONAL_THRESHOLD = 0.30f

    fun fraction(offsetPx: Float, drawerWidthPx: Float): Float =
        if (drawerWidthPx <= 0f) 0f else (offsetPx / drawerWidthPx).coerceIn(0f, 1f)

    fun dragOffset(startOffsetPx: Float, accumulatedDragPx: Float, drawerWidthPx: Float): Float =
        (startOffsetPx + accumulatedDragPx).coerceIn(0f, drawerWidthPx.coerceAtLeast(0f))

    fun settleTarget(
        offsetPx: Float,
        drawerWidthPx: Float,
        velocityPxPerSecond: Float,
        velocityThresholdPxPerSecond: Float,
        positionalThreshold: Float = POSITIONAL_THRESHOLD,
    ): DrawerAnchor = when {
        velocityPxPerSecond >= velocityThresholdPxPerSecond -> DrawerAnchor.OPEN
        velocityPxPerSecond <= -velocityThresholdPxPerSecond -> DrawerAnchor.CLOSED
        fraction(offsetPx, drawerWidthPx) >= positionalThreshold -> DrawerAnchor.OPEN
        else -> DrawerAnchor.CLOSED
    }

    fun gestureIntent(
        accumulatedX: Float,
        accumulatedY: Float,
        activationDistancePx: Float,
    ): DrawerGestureIntent {
        val x = abs(accumulatedX)
        val y = abs(accumulatedY)
        // Do not reject an intended pull because a fingertip wandered a
        // few pixels vertically before the horizontal motion became clear.
        // A vertical gesture still wins once it is both deliberate and dominant.
        if (y >= activationDistancePx * 2f && y > x * 1.35f) return DrawerGestureIntent.PASS_TO_CONTENT
        if (x < activationDistancePx) return DrawerGestureIntent.UNDECIDED
        return if (x >= y * .75f) DrawerGestureIntent.TRACK_DRAWER else DrawerGestureIntent.UNDECIDED
    }
}
