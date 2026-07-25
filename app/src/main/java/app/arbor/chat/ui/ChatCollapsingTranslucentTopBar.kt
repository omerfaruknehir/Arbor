package app.arbor.chat.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Chat counterpart of [CollapsingTranslucentTopBar]. The Material top-app-bar
 * state owns the collapse distance, exactly as it does on Settings screens.
 * There is no message-index, anchor-item, timer, or independent animation state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatCollapsingTranslucentTopBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    navigationIcon: @Composable () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    modelSelector: @Composable () -> Unit,
    blurState: ArborBackdropBlurState,
    blurEnabled: Boolean = true,
    gradualEnabled: Boolean = true,
    blurStrength: Float = 0.7f,
    blurProgress: Float = scrollBehavior.state.collapsedFraction,
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
                progress = blurProgress,
                strength = blurStrength,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                fadeDistance = 128.dp,
                overlayDistance = 128.dp,
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

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp),
        ) {
            val titleTranslationY = with(density) { (61.dp * (1f - travel)).toPx() }
            val titleScale = 1f + 0.20f * (1f - travel)
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .offset(y = 11.dp)
                    .padding(horizontal = 72.dp)
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

            val modelTranslationY = with(density) { (66.dp * (1f - travel)).toPx() }
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 42.dp)
                    .graphicsLayer { translationY = modelTranslationY }
                    .zIndex(3f),
            ) {
                modelSelector()
            }
        }
    }
}
