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
import androidx.compose.runtime.mutableStateOf
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
 * itself receive a three-pass multi-axis AGSL edge blur.
 */
@Stable
class ArborBackdropBlurState internal constructor() {
    internal var topRadiusDp by mutableFloatStateOf(0f)
    internal var bottomRadiusDp by mutableFloatStateOf(0f)
    internal var topFadeDp by mutableFloatStateOf(DEFAULT_TOP_FADE_DP)
    internal var bottomFadeDp by mutableFloatStateOf(DEFAULT_BOTTOM_FADE_DP)
    internal var topGradual by mutableStateOf(true)
    internal var bottomGradual by mutableStateOf(true)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var bottomEdgeInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(edge: ArborBlurEdge, radiusDp: Float, fadeDp: Float, gradual: Boolean) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val fade = fadeDp.coerceAtLeast(1f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
                if (topGradual != gradual) topGradual = gradual
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
                if (bottomGradual != gradual) bottomGradual = gradual
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
     * Two rotated passes still leave visible line structure because each pass is
     * fundamentally one-dimensional. Move to three evenly spaced, non-axis-
     * aligned passes so no single sample line dominates. This looks closer to a
     * soft mica/frosted surface than a rotated grid.
     *
     * Radius values are quantized by ArborBackdropBlurState, limiting effect
     * reconstruction while the header moves without relying on mutable shader
     * uniforms after RenderEffect creation (which was unreliable on-device).
     */
    fun buildShader(directionX: Float, directionY: Float) = RuntimeShader(EDGE_BLUR_SHADER).apply {
        setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        setFloatUniform("uFade", topFadePx, bottomFadePx)
        setFloatUniform("uHeight", contentHeightPx.coerceAtLeast(1f))
        setFloatUniform("uBottomEdge", bottomEdgePx)
        setFloatUniform("uGradual", if (state.topGradual) 1f else 0f, if (state.bottomGradual) 1f else 0f)
        setFloatUniform("uDirection", directionX, directionY)
    }

    val firstShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentHeightPx,
        bottomEdgePx,
        state.topGradual,
        state.bottomGradual,
    ) { buildShader(BLUR_AXIS_A_X, BLUR_AXIS_A_Y) }
    val secondShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentHeightPx,
        bottomEdgePx,
        state.topGradual,
        state.bottomGradual,
    ) { buildShader(BLUR_AXIS_B_X, BLUR_AXIS_B_Y) }
    val thirdShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentHeightPx,
        bottomEdgePx,
        state.topGradual,
        state.bottomGradual,
    ) { buildShader(BLUR_AXIS_C_X, BLUR_AXIS_C_Y) }
    val composeEffect = remember(firstShader, secondShader, thirdShader) {
        val first = RenderEffect.createRuntimeShaderEffect(firstShader, "content")
        val second = RenderEffect.createRuntimeShaderEffect(secondShader, "content")
        val third = RenderEffect.createRuntimeShaderEffect(thirdShader, "content")
        RenderEffect.createChainEffect(third, RenderEffect.createChainEffect(second, first)).asComposeRenderEffect()
    }

    measured.graphicsLayer { renderEffect = composeEffect }
}

internal fun arborBlurProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    // Quintic smootherstep: gentler acceleration and settling than smoothstep.
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

internal fun calculateBlurRadiusDp(
    enabled: Boolean,
    progress: Float,
    strength: Float,
    maxRadiusDp: Float = DEFAULT_MAX_RADIUS_DP,
    minimumRadiusDp: Float = DEFAULT_MIN_RADIUS_DP,
): Float {
    if (!enabled) return 0f
    val maximum = maxRadiusDp.coerceAtLeast(0f)
    val minimum = minimumRadiusDp.coerceIn(0f, maximum)
    val configuredMaximum = minimum + (maximum - minimum) * strength.coerceIn(0f, 1f)
    return minimum + (configuredMaximum - minimum) * arborBlurProgress(progress)
}

internal fun calculatePanelOverlayAmount(gradual: Boolean, progress: Float): Float {
    if (!gradual) return 1f
    return MIN_OVERLAY_AMOUNT + (1f - MIN_OVERLAY_AMOUNT) * arborBlurProgress(progress)
}

/** Registers one chrome edge and paints a directional, gradually fading tint. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    gradual: Boolean = true,
    progress: Float,
    strength: Float,
    tint: Color,
    edge: ArborBlurEdge = ArborBlurEdge.TOP,
    maxRadius: Dp = DEFAULT_MAX_RADIUS_DP.dp,
    fadeDistance: Dp = if (edge == ArborBlurEdge.TOP) DEFAULT_TOP_FADE_DP.dp else DEFAULT_BOTTOM_FADE_DP.dp,
    overlayDistance: Dp = fadeDistance,
): Modifier = composed {
    val radiusDp = calculateBlurRadiusDp(
        enabled = enabled,
        progress = if (gradual) progress else 1f,
        strength = strength,
        maxRadiusDp = maxRadius.value,
    )

    SideEffect { state.update(edge, radiusDp, fadeDistance.value, gradual) }
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
        val overlayProgress = calculatePanelOverlayAmount(gradual, progress)
        val peak = tint.copy(alpha = (tint.alpha * overlayProgress * 1.10f).coerceIn(0f, 1f))
        val middle = tint.copy(alpha = (tint.alpha * overlayProgress * 0.78f).coerceIn(0f, 1f))
        val feather = tint.copy(alpha = (tint.alpha * overlayProgress * 0.22f).coerceIn(0f, 1f))
        val extent = overlayDistancePx.coerceIn(1f, size.height.coerceAtLeast(1f))
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (gradual) {
                    val brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to peak,
                            0.22f to middle,
                            0.54f to feather,
                            0.82f to tint.copy(alpha = tint.alpha * overlayProgress * 0.05f),
                            1f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = extent,
                    )
                    drawRect(brush = brush, size = androidx.compose.ui.geometry.Size(size.width, extent))
                } else {
                    drawRect(color = peak, size = androidx.compose.ui.geometry.Size(size.width, extent))
                }
            }
            ArborBlurEdge.BOTTOM -> {
                val startY = (size.height - extent).coerceAtLeast(0f)
                if (gradual) {
                    val brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.18f to tint.copy(alpha = tint.alpha * overlayProgress * 0.05f),
                            0.46f to feather,
                            0.78f to middle,
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
                } else {
                    drawRect(
                        color = peak,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, startY),
                        size = androidx.compose.ui.geometry.Size(size.width, extent),
                    )
                }
            }
        }
        drawContent()
    }
}

internal fun quantizeBlurRadiusDp(radiusDp: Float): Float =
    ((radiusDp.coerceAtLeast(0f) / BLUR_RADIUS_STEP_DP).roundToInt() * BLUR_RADIUS_STEP_DP)

private const val BLUR_RADIUS_STEP_DP = 0.25f
private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val DEFAULT_MAX_RADIUS_DP = 56f
private const val DEFAULT_MIN_RADIUS_DP = 16f
private const val MIN_OVERLAY_AMOUNT = 0.48f
private const val DEFAULT_TOP_FADE_DP = 128f
private const val DEFAULT_BOTTOM_FADE_DP = 208f

// Three normalized directions spaced 60 degrees apart and rotated off the
// screen axes. This avoids a dominant line structure and gives a more mica-like
// frosted blur when the passes are chained.
internal const val BLUR_AXIS_A_X = 0.9238795f
internal const val BLUR_AXIS_A_Y = 0.3826834f
internal const val BLUR_AXIS_B_X = 0.1305262f
internal const val BLUR_AXIS_B_Y = 0.9914449f
internal const val BLUR_AXIS_C_X = -0.7933533f
internal const val BLUR_AXIS_C_Y = 0.6087614f

/** Bilinear-paired Gaussian taps: nine samples per pass across three directions. */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uFade;
    uniform float uHeight;
    uniform float uBottomEdge;
    uniform float2 uGradual;
    uniform float2 uDirection;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    half4 main(float2 coord) {
        float gradualTop = 1.0 - smoother(coord.y / max(uFade.x, 1.0));
        float panelTop = 1.0 - step(uFade.x, coord.y);
        float topMix = mix(panelTop, gradualTop, uGradual.x);
        float bottomEdge = clamp(uBottomEdge, 0.0, uHeight);
        float gradualBottom = 1.0 - smoother((bottomEdge - coord.y) / max(uFade.y, 1.0));
        float panelBottom = step(bottomEdge - uFade.y, coord.y) * (1.0 - step(bottomEdge, coord.y));
        float bottomMix = mix(panelBottom, gradualBottom, uGradual.y);
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
