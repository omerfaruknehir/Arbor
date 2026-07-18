package app.arbor.chat.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ArborBlurEdge { TOP, BOTTOM }

/**
 * Shared configuration for the gradual blur applied to a screen's actual body.
 *
 * Older Arbor builds captured the screen into a GraphicsLayer and replayed
 * cropped copies behind app chrome. Compose renders those layers lazily, so the
 * replay could be blank or one frame stale. This state instead lets the body
 * itself receive an Agora-style two-pass AGSL edge blur.
 */
@Stable
class ArborBackdropBlurState internal constructor() {
    internal var topRadiusDp by mutableFloatStateOf(0f)
    internal var bottomRadiusDp by mutableFloatStateOf(0f)
    internal var topFadeDp by mutableFloatStateOf(DEFAULT_TOP_FADE_DP)
    internal var bottomFadeDp by mutableFloatStateOf(DEFAULT_BOTTOM_FADE_DP)

    internal fun update(edge: ArborBlurEdge, radiusDp: Float, fadeDp: Float) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRadiusDp = radiusDp.coerceAtLeast(0f)
                topFadeDp = fadeDp.coerceAtLeast(1f)
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusDp = radiusDp.coerceAtLeast(0f)
                bottomFadeDp = fadeDp.coerceAtLeast(1f)
            }
        }
    }

    internal fun clear(edge: ArborBlurEdge) {
        when (edge) {
            ArborBlurEdge.TOP -> topRadiusDp = 0f
            ArborBlurEdge.BOTTOM -> bottomRadiusDp = 0f
        }
    }
}

@Composable
fun rememberArborBackdropBlurState(): ArborBackdropBlurState = remember { ArborBackdropBlurState() }

/** Applies a spatially gradual blur directly to the scrolling content layer. */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val density = LocalDensity.current.density
    val topRadiusPx = state.topRadiusDp * density
    val bottomRadiusPx = state.bottomRadiusDp * density
    if (topRadiusPx < MIN_VISIBLE_RADIUS_PX && bottomRadiusPx < MIN_VISIBLE_RADIUS_PX) {
        return@composed this
    }

    val topFadePx = state.topFadeDp * density
    val bottomFadePx = state.bottomFadeDp * density
    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onSizeChanged { contentHeightPx = it.height.toFloat().coerceAtLeast(1f) }
    if (contentHeightPx <= 0f) return@composed measured

    val horizontalShader = remember(topRadiusPx, bottomRadiusPx, topFadePx, bottomFadePx, contentHeightPx) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uFade", topFadePx, bottomFadePx)
            setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uDirection", 1f, 0f)
        }
    }
    val verticalShader = remember(topRadiusPx, bottomRadiusPx, topFadePx, bottomFadePx, contentHeightPx) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uFade", topFadePx, bottomFadePx)
            setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uDirection", 0f, 1f)
        }
    }
    val composeEffect = remember(horizontalShader, verticalShader) {
        val horizontal = RenderEffect.createRuntimeShaderEffect(horizontalShader, "content")
        val vertical = RenderEffect.createRuntimeShaderEffect(verticalShader, "content")
        RenderEffect.createChainEffect(vertical, horizontal).asComposeRenderEffect()
    }

    measured.graphicsLayer { renderEffect = composeEffect }
}

internal fun arborBlurProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    val smooth = p * p * (3f - 2f * p)
    return 0.28f + 0.72f * smooth
}

/** Registers one chrome edge and paints its stable translucent tint. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    progress: Float,
    strength: Float,
    tint: Color,
    edge: ArborBlurEdge = ArborBlurEdge.TOP,
    maxRadius: Dp = 8.dp,
    fadeDistance: Dp = if (edge == ArborBlurEdge.TOP) DEFAULT_TOP_FADE_DP.dp else DEFAULT_BOTTOM_FADE_DP.dp,
): Modifier = composed {
    val radiusDp = if (enabled) {
        maxRadius.value * strength.coerceIn(0f, 1f) * arborBlurProgress(progress)
    } else {
        0f
    }

    SideEffect { state.update(edge, radiusDp, fadeDistance.value) }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    this.drawWithContent {
        drawRect(if (enabled) tint else tint.copy(alpha = 0.96f))
        drawContent()
    }
}

private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val DEFAULT_TOP_FADE_DP = 148f
private const val DEFAULT_BOTTOM_FADE_DP = 112f

/** Fixed nine-tap separable kernel derived from Agora's gradient blur. */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uFade;
    uniform float uHeight;
    uniform float2 uDirection;

    half4 main(float2 coord) {
        float topMix = saturate(1.0 - coord.y / max(uFade.x, 1.0));
        float bottomMix = saturate(1.0 - (uHeight - coord.y) / max(uFade.y, 1.0));
        float radius = max(uBlur.x * topMix, uBlur.y * bottomMix);
        if (radius < 0.35) return content.eval(coord);

        float2 axis = uDirection * radius;
        half4 accum = half4(content.eval(coord)) * 0.24084130;
        accum += half4(content.eval(coord + axis * 0.6)) * 0.20116756;
        accum += half4(content.eval(coord - axis * 0.6)) * 0.20116756;
        accum += half4(content.eval(coord + axis * 1.2)) * 0.11723004;
        accum += half4(content.eval(coord - axis * 1.2)) * 0.11723004;
        accum += half4(content.eval(coord + axis * 1.8)) * 0.04766218;
        accum += half4(content.eval(coord - axis * 1.8)) * 0.04766218;
        accum += half4(content.eval(coord + axis * 2.4)) * 0.01351957;
        accum += half4(content.eval(coord - axis * 2.4)) * 0.01351957;
        return accum;
    }
""".trimIndent()
