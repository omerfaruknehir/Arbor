package app.xylune.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Chat counterpart of [CollapsingTranslucentTopBar]. Its Material state is driven
 * directly from the chat LazyList position, so finger scrolling, programmatic
 * auto-follow, search jumps, restoration, and viewport correction all share the
 * same collapse fraction without an independent animation owner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatCollapsingTranslucentTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modelSelector: @Composable () -> Unit,
    blurState: XyluneBackdropBlurState,
    blurStrength: Float = 0.7f,
    edgeSoftness: Float = 0.5f,
    overlayOpacity: Float = 1f,
    topPanelHeight: Dp = CHAT_TOP_PANEL_HEIGHT_DP.dp,
) {
    val collapse = scrollBehavior.state.collapsedFraction.coerceIn(0f, 1f)
    val travel = xyluneBlurProgress(collapse)
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .xyluneBackdropBlur(
                state = blurState,
                strength = blurStrength,
                edgeSoftness = edgeSoftness,
                overlayOpacity = overlayOpacity,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                panelHeight = topPanelHeight,
            ),
    ) {
        LargeTopAppBar(
            modifier = Modifier.zIndex(4f),
            title = {},
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.largeTopAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp),
        ) {
            val titleTranslationY = with(density) { (61.dp * (1f - travel)).toPx() }
            val titleScale = 1f + 0.20f * (1f - travel)
            // Expanded mode needs one symmetric icon gutter. Collapsed mode also
            // reserves the Share slot, so a long title can never sit below actions.
            val titleEndPadding = 72.dp + (48.dp * travel)
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset(y = 11.dp)
                    .padding(start = 72.dp, end = titleEndPadding)
                    .graphicsLayer {
                        translationY = titleTranslationY
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .zIndex(2f),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Keep the expanded pill at the same 108 dp baseline, but tuck
            // the collapsed pill 5 dp closer to the compact title.
            val modelTranslationY = with(density) { (71.dp * (1f - travel)).toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 37.dp)
                    .graphicsLayer { translationY = modelTranslationY }
                    .zIndex(3f),
            ) {
                modelSelector()
            }
        }
    }
}
