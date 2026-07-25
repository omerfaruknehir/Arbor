package app.arbor.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Material's large bar is retained only as the scroll/height controller. Arbor
 * draws exactly one title above it and physically moves that title into the
 * compact header. No expanded/collapsed title crossfade is involved.
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
    gradualEnabled: Boolean = true,
    blurStrength: Float = 0.7f,
    overlayOpacity: Float = 1f,
    blurArea: Dp = 128.dp,
) {
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val travel = arborBlurProgress(collapse)
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .arborBackdropBlur(
                state = blurState,
                enabled = blurEnabled,
                gradual = gradualEnabled,
                progress = collapse,
                strength = blurStrength,
                overlayOpacity = overlayOpacity,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                fadeDistance = blurArea,
                overlayDistance = blurArea,
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
        val titleTranslationX = with(density) { ((expandedX - collapsedX) * (1f - travel)).toPx() }
        // The compact title is centered in the same 64 dp row as the
        // navigation icon. Translation starts from that exact baseline, so the
        // fully collapsed state cannot drift upward with font metrics.
        val titleTranslationY = with(density) { (58.dp * (1f - travel)).toPx() }
        val titleScale = 1f + 0.18f * (1f - travel)

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .graphicsLayer {
                    translationX = titleTranslationX
                    translationY = titleTranslationY
                    scaleX = titleScale
                    scaleY = titleScale
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .padding(start = collapsedX, end = 80.dp)
                .zIndex(2f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
