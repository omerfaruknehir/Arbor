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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
    internal var topCornerRadiusDp by mutableFloatStateOf(DEFAULT_PANEL_CORNER_RADIUS_DP)
    internal var bottomCornerRadiusDp by mutableFloatStateOf(DEFAULT_PANEL_CORNER_RADIUS_DP)
    internal var topMergeDp by mutableFloatStateOf(DEFAULT_MERGE_DISTANCE_DP)
    internal var bottomMergeDp by mutableFloatStateOf(DEFAULT_MERGE_DISTANCE_DP)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var bottomEdgeInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(
        edge: ArborBlurEdge,
        radiusDp: Float,
        fadeDp: Float,
        gradual: Boolean,
        cornerRadiusDp: Float,
        mergeDp: Float,
    ) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val fade = fadeDp.coerceAtLeast(1f)
        val corner = cornerRadiusDp.coerceAtLeast(0f)
        val merge = mergeDp.coerceIn(1f, fade)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
                if (topGradual != gradual) topGradual = gradual
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
                if (bottomGradual != gradual) bottomGradual = gradual
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
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
        setFloatUniform("uSize", contentWidthPx.coerceAtLeast(1f), contentHeightPx.coerceAtLeast(1f))
        setFloatUniform("uBottomEdge", bottomEdgePx)
        setFloatUniform("uGradual", if (state.topGradual) 1f else 0f, if (state.bottomGradual) 1f else 0f)
        setFloatUniform("uCorner", state.topCornerRadiusDp * density, state.bottomCornerRadiusDp * density)
        setFloatUniform("uMerge", state.topMergeDp * density, state.bottomMergeDp * density)
        setFloatUniform("uDirection", directionX, directionY)
    }

    val firstShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentWidthPx,
        contentHeightPx,
        bottomEdgePx,
        state.topGradual,
        state.bottomGradual,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
    ) { buildShader(BLUR_AXIS_A_X, BLUR_AXIS_A_Y) }
    val secondShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentWidthPx,
        contentHeightPx,
        bottomEdgePx,
        state.topGradual,
        state.bottomGradual,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
    ) { buildShader(BLUR_AXIS_B_X, BLUR_AXIS_B_Y) }
    val thirdShader = remember(
        topRadiusPx,
        bottomRadiusPx,
        topFadePx,
        bottomFadePx,
        contentWidthPx,
        contentHeightPx,
        bottomEdgePx,
        state.topGradual,
        state.bottomGradual,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
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

/** Registers one chrome panel; only its merge edge fades when gradual mode is enabled. */
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
    cornerRadius: Dp = DEFAULT_PANEL_CORNER_RADIUS_DP.dp,
    mergeDistance: Dp = DEFAULT_MERGE_DISTANCE_DP.dp,
): Modifier = composed {
    val radiusDp = calculateBlurRadiusDp(
        enabled = enabled,
        progress = if (gradual) progress else 1f,
        strength = strength,
        maxRadiusDp = maxRadius.value,
    )

    SideEffect {
        state.update(
            edge = edge,
            radiusDp = radiusDp,
            fadeDp = fadeDistance.value,
            gradual = gradual,
            cornerRadiusDp = cornerRadius.value,
            mergeDp = mergeDistance.value,
        )
    }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    val anchored = if (edge == ArborBlurEdge.BOTTOM) {
        this.onGloballyPositioned { coordinates ->
            state.updateBottomEdge(coordinates.boundsInRoot().bottom)
        }
    } else {
        this
    }

    val density = LocalDensity.current
    val overlayDistancePx = with(density) { overlayDistance.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val mergeDistancePx = with(density) { mergeDistance.toPx() }

    anchored.drawWithContent {
        val panelColor = tint.copy(alpha = (tint.alpha * PANEL_OPACITY_BOOST).coerceIn(0f, 1f))
        val extent = overlayDistancePx.coerceIn(1f, size.height.coerceAtLeast(1f))
        val radius = cornerRadiusPx.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
        val merge = if (gradual) mergeDistancePx.coerceIn(1f, extent) else 0f

        val panelPath = Path().apply {
            when (edge) {
                ArborBlurEdge.TOP -> addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = extent,
                        topLeftCornerRadius = CornerRadius.Zero,
                        topRightCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius(radius, radius),
                        bottomLeftCornerRadius = CornerRadius(radius, radius),
                    ),
                )
                ArborBlurEdge.BOTTOM -> {
                    val startY = (size.height - extent).coerceAtLeast(0f)
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = startY,
                            right = size.width,
                            bottom = size.height,
                            topLeftCornerRadius = CornerRadius(radius, radius),
                            topRightCornerRadius = CornerRadius(radius, radius),
                            bottomRightCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero,
                        ),
                    )
                }
            }
        }

        clipPath(panelPath) {
            when (edge) {
                ArborBlurEdge.TOP -> {
                    val bodyEnd = (extent - merge).coerceAtLeast(0f)
                    if (bodyEnd > 0f) drawRect(color = panelColor, size = Size(size.width, bodyEnd))
                    if (merge > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to panelColor,
                                    0.52f to panelColor.copy(alpha = panelColor.alpha * 0.92f),
                                    0.78f to panelColor.copy(alpha = panelColor.alpha * 0.54f),
                                    1f to Color.Transparent,
                                ),
                                startY = bodyEnd,
                                endY = extent,
                            ),
                            topLeft = Offset(0f, bodyEnd),
                            size = Size(size.width, merge),
                        )
                    } else {
                        drawRect(color = panelColor, size = Size(size.width, extent))
                    }
                }
                ArborBlurEdge.BOTTOM -> {
                    val startY = (size.height - extent).coerceAtLeast(0f)
                    val bodyStart = (startY + merge).coerceAtMost(size.height)
                    if (merge > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.22f to panelColor.copy(alpha = panelColor.alpha * 0.54f),
                                    0.48f to panelColor.copy(alpha = panelColor.alpha * 0.92f),
                                    1f to panelColor,
                                ),
                                startY = startY,
                                endY = bodyStart,
                            ),
                            topLeft = Offset(0f, startY),
                            size = Size(size.width, merge),
                        )
                    }
                    if (bodyStart < size.height) {
                        drawRect(
                            color = panelColor,
                            topLeft = Offset(0f, bodyStart),
                            size = Size(size.width, size.height - bodyStart),
                        )
                    }
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
private const val PANEL_OPACITY_BOOST = 1.30f
private const val DEFAULT_PANEL_CORNER_RADIUS_DP = 28f
private const val DEFAULT_MERGE_DISTANCE_DP = 34f
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
    uniform float2 uSize;
    uniform float uBottomEdge;
    uniform float2 uGradual;
    uniform float2 uCorner;
    uniform float2 uMerge;
    uniform float2 uDirection;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    float roundedTopPanelMask(float2 coord, float extent, float radius) {
        if (coord.y < 0.0 || coord.y > extent) return 0.0;
        radius = clamp(radius, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5 || coord.y <= extent - radius) return 1.0;
        if (coord.x < radius) {
            float d = length(coord - float2(radius, extent - radius));
            return 1.0 - smoothstep(radius - 0.75, radius + 0.75, d);
        }
        if (coord.x > uSize.x - radius) {
            float d = length(coord - float2(uSize.x - radius, extent - radius));
            return 1.0 - smoothstep(radius - 0.75, radius + 0.75, d);
        }
        return 1.0;
    }

    float roundedBottomPanelMask(float2 coord, float start, float bottom, float radius) {
        if (coord.y < start || coord.y > bottom) return 0.0;
        float extent = bottom - start;
        radius = clamp(radius, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5 || coord.y >= start + radius) return 1.0;
        if (coord.x < radius) {
            float d = length(coord - float2(radius, start + radius));
            return 1.0 - smoothstep(radius - 0.75, radius + 0.75, d);
        }
        if (coord.x > uSize.x - radius) {
            float d = length(coord - float2(uSize.x - radius, start + radius));
            return 1.0 - smoothstep(radius - 0.75, radius + 0.75, d);
        }
        return 1.0;
    }

    half4 main(float2 coord) {
        float topExtent = max(uFade.x, 1.0);
        float topMask = roundedTopPanelMask(coord, topExtent, uCorner.x);
        float topMerge = max(uMerge.x, 1.0);
        float topFeather = smoother((topExtent - coord.y) / topMerge);
        float topMix = topMask * mix(1.0, topFeather, uGradual.x);

        float bottomEdge = clamp(uBottomEdge, 0.0, uSize.y);
        float bottomExtent = max(uFade.y, 1.0);
        float bottomStart = bottomEdge - bottomExtent;
        float bottomMask = roundedBottomPanelMask(coord, bottomStart, bottomEdge, uCorner.y);
        float bottomMerge = max(uMerge.y, 1.0);
        float bottomFeather = smoother((coord.y - bottomStart) / bottomMerge);
        float bottomMix = bottomMask * mix(1.0, bottomFeather, uGradual.y);

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
