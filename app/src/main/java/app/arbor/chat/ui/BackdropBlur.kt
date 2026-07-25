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
 * itself receive a two-pass rotated AGSL edge blur.
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

    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onGloballyPositioned { coordinates ->
        val nextHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
        if (contentHeightPx != nextHeight) contentHeightPx = nextHeight
        state.updateSource(coordinates.boundsInRoot().top)
    }
    if (!active || contentHeightPx <= 0f) return@composed measured

    val topFadePx = state.topFadeDp * density
    val bottomFadePx = state.bottomFadeDp * density
    val bottomEdgePx = state.bottomEdgeInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?.coerceIn(0f, contentHeightPx)
        ?: contentHeightPx

    /*
     * Use the same two-pass RuntimeShader chain that rendered correctly before
     * 0.17.2. The axes are still orthogonal, so the result is an isotropic
     * Gaussian, but they are rotated away from the screen's pixel rows and
     * columns to avoid the old horizontal/vertical grid pattern.
     *
     * Radius values are quantized by ArborBackdropBlurState, limiting effect
     * reconstruction while the header moves without relying on mutable shader
     * uniforms after RenderEffect creation (which was unreliable on-device).
     */
    val firstShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentHeightPx,
        bottomEdgePx,
    ) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uFade", topFadePx, bottomFadePx)
            setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uBottomEdge", bottomEdgePx)
            setFloatUniform("uDirection", BLUR_AXIS_A_X, BLUR_AXIS_A_Y)
        }
    }
    val secondShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentHeightPx,
        bottomEdgePx,
    ) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uFade", topFadePx, bottomFadePx)
            setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uBottomEdge", bottomEdgePx)
            setFloatUniform("uDirection", BLUR_AXIS_B_X, BLUR_AXIS_B_Y)
        }
    }
    val composeEffect = remember(firstShader, secondShader) {
        val first = RenderEffect.createRuntimeShaderEffect(firstShader, "content")
        val second = RenderEffect.createRuntimeShaderEffect(secondShader, "content")
        RenderEffect.createChainEffect(second, first).asComposeRenderEffect()
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

// Two normalized, orthogonal axes rotated 22.5 degrees from the screen axes.
internal const val BLUR_AXIS_A_X = 0.9238795f
internal const val BLUR_AXIS_A_Y = 0.3826834f
internal const val BLUR_AXIS_B_X = -0.3826834f
internal const val BLUR_AXIS_B_Y = 0.9238795f

/** Bilinear-paired Gaussian taps: nine samples per rotated axis. */
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
        accum += half4(content.eval(coord + sampleStep * 1.476579653)) * 0.191010813;
        accum += half4(content.eval(coord - sampleStep * 1.476579653)) * 0.191010813;
        accum += half4(content.eval(coord + sampleStep * 3.445529534)) * 0.140428908;
        accum += half4(content.eval(coord - sampleStep * 3.445529534)) * 0.140428908;
        accum += half4(content.eval(coord + sampleStep * 5.414898846)) * 0.080715462;
        accum += half4(content.eval(coord - sampleStep * 5.414898846)) * 0.080715462;
        accum += half4(content.eval(coord + sampleStep * 7.384912150)) * 0.036268507;
        accum += half4(content.eval(coord - sampleStep * 7.384912150)) * 0.036268507;
        return accum;
    }
""".trimIndent()
