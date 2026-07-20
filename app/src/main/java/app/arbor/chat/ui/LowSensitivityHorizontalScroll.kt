package app.arbor.chat.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration

/**
 * Horizontal containers used inside the vertically scrolling chat require a
 * deliberate sideways gesture. A modestly larger horizontal touch slop lets the
 * parent LazyColumn win ordinary diagonal/vertical drags instead of making the
 * conversation feel stuck.
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
    CompositionLocalProvider(LocalViewConfiguration provides tuned) {
        Box(
            modifier = modifier.horizontalScroll(state = state, enabled = enabled),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
