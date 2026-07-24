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
import kotlin.math.abs
import kotlin.math.roundToInt
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
 * itself receive a single-pass AGSL edge blur.
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
        val radius = quantizeBlurRadiusDp(radiusDp)
        val fade = fadeDp.coerceAtLeast(1f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
            }
        }
    }

    internal fun updateSource(topInRootPx: Float) {
        if (abs(sourceTopInRootPx - topInRootPx) >= 0.5f) sourceTopInRootPx = topInRootPx
    }

    internal fun updateBottomEdge(bottomInRootPx: Float) {
        if (!bottomEdgeInRootPx.isFinite() || abs(bottomEdgeInRootPx - bottomInRootPx) >= 0.5f) {
            bottomEdgeInRootPx = bottomInRootPx
        }
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
    val active = topRadiusPx >= MIN_VISIBLE_RADIUS_PX || bottomRadiusPx >= MIN_VISIBLE_RADIUS_PX

    var contentWidthPx by remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onGloballyPositioned { coordinates ->
        val nextWidth = coordinates.size.width.toFloat().coerceAtLeast(1f)
        val nextHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
        if (contentWidthPx != nextWidth) contentWidthPx = nextWidth
        if (contentHeightPx != nextHeight) contentHeightPx = nextHeight
        state.updateSource(coordinates.boundsInRoot().top)
    }
    if (!active || contentWidthPx <= 0f || contentHeightPx <= 0f) return@composed measured

    val topFadePx = state.topFadeDp * density
    val bottomFadePx = state.bottomFadeDp * density
    val bottomEdgePx = state.bottomEdgeInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?.coerceIn(0f, contentHeightPx)
        ?: contentHeightPx

    // Keep one shader/effect for the lifetime of the source layer. Earlier code
    // recreated two RuntimeShaders and a chained RenderEffect whenever scroll
    // progress changed, which produced allocation spikes directly on a fling.
    val shader = remember { RuntimeShader(EDGE_BLUR_SHADER) }
    val composeEffect = remember(shader) {
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
    SideEffect {
        shader.setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        shader.setFloatUniform("uFade", topFadePx, bottomFadePx)
        shader.setFloatUniform("uSize", contentWidthPx, contentHeightPx)
        shader.setFloatUniform("uBottomEdge", bottomEdgePx)
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
    overlayDistance: Dp = fadeDistance,
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

    val overlayDistancePx = with(LocalDensity.current) { overlayDistance.toPx() }

    anchored.drawWithContent {
        val overlayProgress = if (enabled) easedProgress else 1f
        val peak = tint.copy(alpha = tint.alpha * overlayProgress)
        val middle = tint.copy(alpha = tint.alpha * overlayProgress * 0.58f)
        val feather = tint.copy(alpha = tint.alpha * overlayProgress * 0.12f)
        val extent = overlayDistancePx.coerceIn(1f, size.height.coerceAtLeast(1f))
        when (edge) {
            ArborBlurEdge.TOP -> {
                val brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to peak,
                        0.30f to middle,
                        0.62f to feather,
                        1f to Color.Transparent,
                    ),
                    startY = 0f,
                    endY = extent,
                )
                drawRect(brush = brush, size = androidx.compose.ui.geometry.Size(size.width, extent))
            }
            ArborBlurEdge.BOTTOM -> {
                val startY = (size.height - extent).coerceAtLeast(0f)
                val brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.38f to feather,
                        0.70f to middle,
                        1f to peak,
                    ),
                    startY = startY,
                    endY = size.height,
                )
                drawRect(
                    brush = brush,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, startY),
                    size = androidx.compose.ui.geometry.Size(size.width, extent),
                )
            }
        }
        drawContent()
    }
}

internal fun quantizeBlurRadiusDp(radiusDp: Float): Float =
    ((radiusDp.coerceAtLeast(0f) / BLUR_RADIUS_STEP_DP).roundToInt() * BLUR_RADIUS_STEP_DP)

private const val BLUR_RADIUS_STEP_DP = 0.25f
private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val DEFAULT_MAX_RADIUS_DP = 36f
private const val DEFAULT_TOP_FADE_DP = 88f
private const val DEFAULT_BOTTOM_FADE_DP = 152f

/**
 * One rotated Poisson kernel replaces the former horizontal+vertical chain.
 * The irregular paired directions remove the visible cross/grid structure of a
 * separable blur, while 13 total samples are cheaper than the old 18 fetches.
 */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uFade;
    uniform float2 uSize;
    uniform float uBottomEdge;

    half4 main(float2 coord) {
        float topMix = saturate(1.0 - coord.y / max(uFade.x, 1.0));
        float bottomEdge = clamp(uBottomEdge, 0.0, uSize.y);
        float bottomMix = saturate(1.0 - (bottomEdge - coord.y) / max(uFade.y, 1.0));
        float radius = max(uBlur.x * topMix, uBlur.y * bottomMix);
        if (radius < 0.35) return content.eval(coord);

        float2 lo = float2(0.5, 0.5);
        float2 hi = max(uSize - lo, lo);
        half4 accum = half4(content.eval(clamp(coord, lo, hi))) * 0.04;

        float2 o1 = float2( 0.2391,  0.0731) * radius;
        float2 o2 = float2( 0.0463,  0.3772) * radius;
        float2 o3 = float2(-0.4457,  0.2678) * radius;
        float2 o4 = float2( 0.5055,  0.4396) * radius;
        float2 o5 = float2(-0.2397,  0.7842) * radius;
        float2 o6 = float2(-0.9528,  0.1170) * radius;

        accum += (half4(content.eval(clamp(coord + o1, lo, hi))) + half4(content.eval(clamp(coord - o1, lo, hi)))) * 0.10;
        accum += (half4(content.eval(clamp(coord + o2, lo, hi))) + half4(content.eval(clamp(coord - o2, lo, hi)))) * 0.10;
        accum += (half4(content.eval(clamp(coord + o3, lo, hi))) + half4(content.eval(clamp(coord - o3, lo, hi)))) * 0.09;
        accum += (half4(content.eval(clamp(coord + o4, lo, hi))) + half4(content.eval(clamp(coord - o4, lo, hi)))) * 0.08;
        accum += (half4(content.eval(clamp(coord + o5, lo, hi))) + half4(content.eval(clamp(coord - o5, lo, hi)))) * 0.06;
        accum += (half4(content.eval(clamp(coord + o6, lo, hi))) + half4(content.eval(clamp(coord - o6, lo, hi)))) * 0.05;
        return accum;
    }
""".trimIndent()
