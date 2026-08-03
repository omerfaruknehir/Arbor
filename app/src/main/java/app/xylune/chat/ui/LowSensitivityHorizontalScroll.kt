package app.xylune.chat.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Horizontal chat surfaces use a slightly higher touch slop so vertical chat
 * scrolling remains easy, but they own a horizontal drag before the drawer.
 */
@Composable
internal fun LowSensitivityHorizontalScroll(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    touchSlopMultiplier: Float = 1.35f,
    content: @Composable () -> Unit,
) {
    val base = LocalViewConfiguration.current
    val tuned = remember(base, touchSlopMultiplier) {
        object : ViewConfiguration by base {
            override val touchSlop: Float = base.touchSlop * touchSlopMultiplier.coerceAtLeast(1f)
        }
    }
    val registry = LocalHorizontalGesturePriorityRegistry.current
    val owner = remember { Any() }
    DisposableEffect(registry, owner, enabled) {
        if (!enabled) registry?.remove(owner)
        onDispose { registry?.remove(owner) }
    }

    CompositionLocalProvider(LocalViewConfiguration provides tuned) {
        Box(
            modifier = modifier
                .onGloballyPositioned { coordinates ->
                    if (enabled) registry?.update(owner, coordinates.boundsInRoot())
                    else registry?.remove(owner)
                }
                .horizontalScroll(state = state, enabled = enabled),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
