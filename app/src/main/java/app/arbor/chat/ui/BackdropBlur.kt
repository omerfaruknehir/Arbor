package app.arbor.chat.ui

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * A frame-local capture of the screen content which sits behind Arbor's chrome.
 *
 * The capture layer itself never receives a RenderEffect. Every blurred surface
 * owns a second layer which records a cropped copy of this source and keeps its
 * blur effect attached until the frame is submitted. This is important on
 * Android's deferred renderer: applying an effect to the shared source and
 * clearing it immediately can result in the draw command being executed after
 * the effect has already been removed.
 */
@Stable
class ArborBackdropBlurState internal constructor(
    internal val sourceLayer: GraphicsLayer,
) {
    internal var sourcePosition by mutableStateOf(Offset.Zero)
    internal var sourceSize by mutableStateOf(IntSize.Zero)
}

@Composable
fun rememberArborBackdropBlurState(): ArborBackdropBlurState {
    val graphicsContext = LocalGraphicsContext.current
    val layer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsContext, layer) {
        onDispose { graphicsContext.releaseGraphicsLayer(layer) }
    }
    return remember(layer) { ArborBackdropBlurState(layer) }
}

/** Records the complete scrolling/body layer which top and bottom chrome sample. */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier =
    onGloballyPositioned { coordinates ->
        state.sourcePosition = coordinates.positionInRoot()
        state.sourceSize = coordinates.size
    }.drawWithContent {
        val captureSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
        if (captureSize.width <= 0 || captureSize.height <= 0) {
            drawContent()
            return@drawWithContent
        }

        state.sourceLayer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = captureSize,
        ) {
            this@drawWithContent.drawContent()
        }
        drawLayer(state.sourceLayer)
    }

internal fun arborBlurProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    // Keep a visible glass effect even before scrolling, then increase it
    // smoothly as more content passes underneath the chrome.
    val smooth = p * p * (3f - 2f * p)
    return 0.35f + 0.65f * smooth
}

/**
 * Draws a genuine blurred copy of [state] behind this composable on Android 12+.
 *
 * A dedicated overscanned layer is used for each target. Overscan prevents the
 * usual hard/unchanged strip along the edge of a toolbar or composer and makes
 * the blur visibly continuous across the whole translucent surface.
 */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    progress: Float,
    strength: Float,
    tint: Color,
    maxRadius: Dp = 48.dp,
): Modifier = composed {
    val graphicsContext = LocalGraphicsContext.current
    val effectLayer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsContext, effectLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(effectLayer) }
    }

    var effectPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val radiusPx = with(density) {
        (maxRadius * strength.coerceIn(0f, 1f) * arborBlurProgress(progress)).toPx()
    }

    this
        .onGloballyPositioned { coordinates ->
            effectPosition = coordinates.positionInRoot()
        }
        .drawWithContent {
            val targetWidth = size.width.roundToInt()
            val targetHeight = size.height.roundToInt()
            val canBlur = enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                radiusPx >= 0.5f &&
                targetWidth > 0 && targetHeight > 0 &&
                state.sourceSize.width > 0 && state.sourceSize.height > 0

            if (canBlur) {
                // Gaussian blur needs neighbouring pixels outside the visible
                // target. Capture three radii on every side before clipping.
                val overscan = ceil(radiusPx * 3f).toInt().coerceAtLeast(1)
                val layerSize = IntSize(
                    width = targetWidth + overscan * 2,
                    height = targetHeight + overscan * 2,
                )
                val sourceOffset = effectPosition - state.sourcePosition

                effectLayer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = layerSize,
                ) {
                    withTransform({
                        translate(
                            left = -sourceOffset.x + overscan,
                            top = -sourceOffset.y + overscan,
                        )
                    }) {
                        drawLayer(state.sourceLayer)
                    }
                }
                effectLayer.renderEffect = BlurEffect(
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    edgeTreatment = TileMode.Clamp,
                )

                withTransform({ translate(-overscan.toFloat(), -overscan.toFloat()) }) {
                    drawLayer(effectLayer)
                }
            } else {
                effectLayer.renderEffect = null
            }

            // Tint opacity is deliberately constant. Scrolling changes only
            // blur strength, never turns the bar into an opaque panel.
            drawRect(if (enabled) tint else tint.copy(alpha = 0.96f))
            drawContent()
        }
}
