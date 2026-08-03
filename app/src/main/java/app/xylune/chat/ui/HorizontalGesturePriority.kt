package app.xylune.chat.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

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

/** Claims this composable's root-space bounds before the app drawer sees a drag. */
internal fun Modifier.horizontalGesturePriority(enabled: Boolean = true): Modifier = composed {
    val registry = LocalHorizontalGesturePriorityRegistry.current
    val owner = remember { Any() }
    DisposableEffect(registry, owner, enabled) {
        if (!enabled) registry?.remove(owner)
        onDispose { registry?.remove(owner) }
    }
    this.onGloballyPositioned { coordinates ->
        if (enabled) registry?.update(owner, coordinates.boundsInRoot())
        else registry?.remove(owner)
    }
}
