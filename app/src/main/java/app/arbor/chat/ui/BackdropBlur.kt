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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var bottomEdgeInRootPx by mutableFloatStateOf(Float.NaN)

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

    internal fun updateSource(topInRootPx: Float) {
        sourceTopInRootPx = topInRootPx
    }

    internal fun updateBottomEdge(bottomInRootPx: Float) {
        bottomEdgeInRootPx = bottomInRootPx
    }

    internal fun clear(edge: ArborBlurEdge) {
        when (edge) {
            ArborBlurEdge.TOP -> topRadiusDp = 0f
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusDp = 0f
                bottomEdgeInRootPx = Float.NaN
            }
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
    val measured = this.onGloballyPositioned { coordinates ->
        contentHeightPx = coordinates.size.height.toFloat().coerceAtLeast(1f)
        state.updateSource(coordinates.boundsInRoot().top)
    }
    if (contentHeightPx <= 0f) return@composed measured

    val bottomEdgePx = state.bottomEdgeInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?.coerceIn(0f, contentHeightPx)
        ?: contentHeightPx

    val horizontalShader = remember(topRadiusPx, bottomRadiusPx, topFadePx, bottomFadePx, contentHeightPx, bottomEdgePx) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uFade", topFadePx, bottomFadePx)
            setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uBottomEdge", bottomEdgePx)
            setFloatUniform("uDirection", 1f, 0f)
        }
    }
    val verticalShader = remember(topRadiusPx, bottomRadiusPx, topFadePx, bottomFadePx, contentHeightPx, bottomEdgePx) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uFade", topFadePx, bottomFadePx)
            setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uBottomEdge", bottomEdgePx)
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
    return p * p * (3f - 2f * p)
}

/** Registers one chrome edge and paints a directional, gradually fading tint. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    progress: Float,
    strength: Float,
    tint: Color,
    edge: ArborBlurEdge = ArborBlurEdge.TOP,
    maxRadius: Dp = DEFAULT_MAX_RADIUS_DP.dp,
    fadeDistance: Dp = if (edge == ArborBlurEdge.TOP) DEFAULT_TOP_FADE_DP.dp else DEFAULT_BOTTOM_FADE_DP.dp,
): Modifier = composed {
    val easedProgress = arborBlurProgress(progress)
    val radiusDp = if (enabled) {
        maxRadius.value * strength.coerceIn(0f, 1f) * easedProgress
    } else {
        0f
    }

    SideEffect { state.update(edge, radiusDp, fadeDistance.value) }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    val anchored = if (edge == ArborBlurEdge.BOTTOM) {
        this.onGloballyPositioned { coordinates ->
            state.updateBottomEdge(coordinates.boundsInRoot().bottom)
        }
    } else {
        this
    }

    anchored.drawWithContent {
        val overlayProgress = if (enabled) easedProgress else 1f
        val peak = tint.copy(alpha = tint.alpha * overlayProgress)
        val middle = tint.copy(alpha = tint.alpha * overlayProgress * 0.58f)
        val feather = tint.copy(alpha = tint.alpha * overlayProgress * 0.12f)
        val brush = when (edge) {
            ArborBlurEdge.TOP -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to peak,
                    0.30f to middle,
                    0.58f to feather,
                    1f to Color.Transparent,
                ),
            )
            ArborBlurEdge.BOTTOM -> Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.42f to feather,
                    0.70f to middle,
                    1f to peak,
                ),
            )
        }
        drawRect(brush = brush)
        drawContent()
    }
}

private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val DEFAULT_MAX_RADIUS_DP = 24f
private const val DEFAULT_TOP_FADE_DP = 180f
private const val DEFAULT_BOTTOM_FADE_DP = 152f

/**
 * Dense seventeen-tap separable Gaussian kernel.
 *
 * The old high-radius nine-tap kernel left large gaps between samples, which
 * appeared as a grid on high-density displays. These taps cover the requested
 * radius uniformly and rely on bilinear texture filtering between samples.
 */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uFade;
    uniform float uHeight;
    uniform float uBottomEdge;
    uniform float2 uDirection;

    half4 main(float2 coord) {
        float topMix = saturate(1.0 - coord.y / max(uFade.x, 1.0));
        float bottomEdge = clamp(uBottomEdge, 0.0, uHeight);
        float bottomMix = saturate(1.0 - (bottomEdge - coord.y) / max(uFade.y, 1.0));
        float radius = max(uBlur.x * topMix, uBlur.y * bottomMix);
        if (radius < 0.35) return content.eval(coord);

        float2 sampleStep = uDirection * (radius / 8.0);
        half4 accum = half4(content.eval(coord)) * 0.103152619;
        accum += half4(content.eval(coord + sampleStep * 1.0)) * 0.099978946;
        accum += half4(content.eval(coord - sampleStep * 1.0)) * 0.099978946;
        accum += half4(content.eval(coord + sampleStep * 2.0)) * 0.091031867;
        accum += half4(content.eval(coord - sampleStep * 2.0)) * 0.091031867;
        accum += half4(content.eval(coord + sampleStep * 3.0)) * 0.077863682;
        accum += half4(content.eval(coord - sampleStep * 3.0)) * 0.077863682;
        accum += half4(content.eval(coord + sampleStep * 4.0)) * 0.062565226;
        accum += half4(content.eval(coord - sampleStep * 4.0)) * 0.062565226;
        accum += half4(content.eval(coord + sampleStep * 5.0)) * 0.047226710;
        accum += half4(content.eval(coord - sampleStep * 5.0)) * 0.047226710;
        accum += half4(content.eval(coord + sampleStep * 6.0)) * 0.033488752;
        accum += half4(content.eval(coord - sampleStep * 6.0)) * 0.033488752;
        accum += half4(content.eval(coord + sampleStep * 7.0)) * 0.022308318;
        accum += half4(content.eval(coord - sampleStep * 7.0)) * 0.022308318;
        accum += half4(content.eval(coord + sampleStep * 8.0)) * 0.013960189;
        accum += half4(content.eval(coord - sampleStep * 8.0)) * 0.013960189;
        return accum;
    }
""".trimIndent()
