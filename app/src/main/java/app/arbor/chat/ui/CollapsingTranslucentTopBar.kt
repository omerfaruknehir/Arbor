package app.arbor.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A collapsing app bar with one persistent title layer.
 *
 * Material 3's stock [LargeTopAppBar] internally crossfades separate expanded
 * and collapsed title layouts. Arbor keeps the title fully opaque and instead
 * translates/scales that same text into the compact header. The backdrop blur
 * is intentionally confined to the immediate app-bar edge rather than washing
 * far down the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTranslucentTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    blurState: ArborBackdropBlurState,
    blurEnabled: Boolean = true,
    blurStrength: Float = 0.7f,
) {
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val travel = arborBlurProgress(collapse)
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        Modifier
            .fillMaxWidth()
            .arborBackdropBlur(
                state = blurState,
                enabled = blurEnabled,
                progress = collapse,
                strength = blurStrength,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                fadeDistance = 64.dp,
                overlayDistance = 64.dp,
            ),
    ) {
        LargeTopAppBar(
            title = {},
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        val expandedX = 16.dp
        val collapsedX = 56.dp
        val expandedY = statusTop + 82.dp
        val collapsedY = statusTop + 13.dp
        val titleX = expandedX + (collapsedX - expandedX) * travel
        val titleY = expandedY + (collapsedY - expandedY) * travel
        val titleScale = 1.28f - 0.28f * travel

        Text(
            text = title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 80.dp)
                .offset {
                    IntOffset(
                        x = with(density) { titleX.roundToPx() },
                        y = with(density) { titleY.roundToPx() },
                    )
                }
                .graphicsLayer {
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .zIndex(2f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
