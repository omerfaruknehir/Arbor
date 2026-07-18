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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.roundToInt

/**
 * Shared capture used by Arbor's chrome to draw the actual UI behind it.
 * Unlike Modifier.blur, this records the background content and re-draws that
 * capture through a RenderEffect, so it is a real backdrop blur on Android 12+.
 */
@Stable
class ArborBackdropBlurState internal constructor(
    internal val graphicsLayer: GraphicsLayer,
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

/** Marks a full-screen content layer as the source for top/bottom chrome blur. */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier =
    onGloballyPositioned { coordinates ->
            state.sourcePosition = coordinates.positionInWindow()
            state.sourceSize = coordinates.size
        }
        .drawWithContent {
            val captureSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            if (captureSize.width > 0 && captureSize.height > 0) {
                state.graphicsLayer.record(
                    density = this,
                    layoutDirection = layoutDirection,
                    size = captureSize,
                ) {
                    this@drawWithContent.drawContent()
                }
                drawLayer(state.graphicsLayer)
            } else {
                drawContent()
            }
        }

/**
 * Draws a captured, blurred copy of [state] behind this composable.
 * [progress] is smoothly interpolated so the effect grows rather than snapping.
 */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    progress: Float,
    strength: Float,
    tint: Color,
    maxRadius: Dp = 32.dp,
): Modifier = composed {
    var effectPosition by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val clamped = progress.coerceIn(0f, 1f)
    val smoothProgress = clamped * clamped * (3f - 2f * clamped)
    val radiusPx = with(density) {
        (maxRadius * strength.coerceIn(0f, 1f) * smoothProgress).toPx()
    }

    this
        .onGloballyPositioned { coordinates -> effectPosition = coordinates.positionInWindow() }
        .drawWithContent {
            val canBlur = enabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                radiusPx >= 0.5f &&
                state.sourceSize.width > 0 &&
                state.sourceSize.height > 0

            if (canBlur) {
                val sourceOffset = effectPosition - state.sourcePosition
                state.graphicsLayer.renderEffect = BlurEffect(
                    radiusX = radiusPx,
                    radiusY = radiusPx,
                    edgeTreatment = TileMode.Clamp,
                )
                clipRect {
                    withTransform({ translate(-sourceOffset.x, -sourceOffset.y) }) {
                        drawLayer(state.graphicsLayer)
                    }
                }
                state.graphicsLayer.renderEffect = null
            }

            drawRect(if (enabled) tint else tint.copy(alpha = 0.96f))
            drawContent()
        }
}
