package app.arbor.chat.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/** Root-space horizontal viewports which own a drag before the app drawer. */
@Stable
internal class HorizontalGesturePriorityRegistry {
    private val regions = mutableMapOf<Any, Rect>()

    fun update(owner: Any, boundsInRoot: Rect) {
        if (boundsInRoot.width > 0f && boundsInRoot.height > 0f) regions[owner] = boundsInRoot
        else regions.remove(owner)
    }

    fun remove(owner: Any) {
        regions.remove(owner)
    }

    fun owns(positionInRoot: Offset): Boolean =
        regions.values.any { bounds -> bounds.contains(positionInRoot) }
}

internal val LocalHorizontalGesturePriorityRegistry =
    staticCompositionLocalOf<HorizontalGesturePriorityRegistry?> { null }
