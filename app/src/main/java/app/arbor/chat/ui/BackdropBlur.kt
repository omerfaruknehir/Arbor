package app.arbor.chat.ui

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ArborBlurEdge { TOP, BOTTOM }

/**
 * Canonical geometry and styling for Arbor's top and bottom glass panels.
 *
 * Blur and tint are deliberately rendered in the same RenderEffect and use the
 * same mask. The chrome composables only register their geometry; they never
 * draw a second overlay in a different coordinate system.
 */
@Stable
class ArborBackdropBlurState internal constructor() {
    internal var topRegistered by mutableStateOf(false)
    internal var bottomRegistered by mutableStateOf(false)
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
    internal var topTint by mutableStateOf(Color.Transparent)
    internal var bottomTint by mutableStateOf(Color.Transparent)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var bottomEdgeInRootPx by mutableFloatStateOf(Float.NaN)
    internal var bottomPanelStartInRootPx by mutableFloatStateOf(Float.NaN)
    internal var bottomPanelEndInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(
        edge: ArborBlurEdge,
        radiusDp: Float,
        fadeDp: Float,
        gradual: Boolean,
        cornerRadiusDp: Float,
        mergeDp: Float,
        tint: Color,
    ) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val fade = fadeDp.coerceAtLeast(1f)
        val corner = cornerRadiusDp.coerceAtLeast(0f)
        val merge = mergeDp.coerceIn(1f, fade)
        val boostedTint = tint.copy(alpha = panelTintAlpha(tint.alpha))
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRegistered = true
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
                if (topGradual != gradual) topGradual = gradual
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
                if (topTint != boostedTint) topTint = boostedTint
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRegistered = true
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
                if (bottomGradual != gradual) bottomGradual = gradual
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
                if (bottomTint != boostedTint) bottomTint = boostedTint
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

    internal fun updateBottomPanel(startInRootPx: Float, endInRootPx: Float) {
        if (!bottomPanelStartInRootPx.isFinite() || abs(bottomPanelStartInRootPx - startInRootPx) >= 0.5f) {
            bottomPanelStartInRootPx = startInRootPx
        }
        if (!bottomPanelEndInRootPx.isFinite() || abs(bottomPanelEndInRootPx - endInRootPx) >= 0.5f) {
            bottomPanelEndInRootPx = endInRootPx
        }
        updateBottomEdge(endInRootPx)
    }

    internal fun clear(edge: ArborBlurEdge) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRegistered = false
                topRadiusDp = 0f
                topTint = Color.Transparent
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRegistered = false
                bottomRadiusDp = 0f
                bottomTint = Color.Transparent
                bottomEdgeInRootPx = Float.NaN
                bottomPanelStartInRootPx = Float.NaN
                bottomPanelEndInRootPx = Float.NaN
            }
        }
    }
}

@Composable
fun rememberArborBackdropBlurState(): ArborBackdropBlurState = remember { ArborBackdropBlurState() }

/**
 * Applies native Skia Gaussian blur and the glass tint through one canonical
 * rounded mask. AGSL does no blur sampling; it only masks and composites.
 */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val density = LocalDensity.current.density
    var contentWidthPx by remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onGloballyPositioned { coordinates ->
        val nextWidth = coordinates.size.width.toFloat().coerceAtLeast(1f)
        val nextHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
        if (contentWidthPx != nextWidth) contentWidthPx = nextWidth
        if (contentHeightPx != nextHeight) contentHeightPx = nextHeight
        state.updateSource(coordinates.boundsInRoot().top)
    }

    if ((!state.topRegistered && !state.bottomRegistered) || contentWidthPx <= 0f || contentHeightPx <= 0f) {
        return@composed measured
    }

    val topRange = alignedTopBlurRange(state.topFadeDp * density, contentHeightPx)
    val bottomRange = alignedBottomBlurRange(
        sourceTopInRootPx = state.sourceTopInRootPx,
        panelStartInRootPx = state.bottomPanelStartInRootPx,
        panelEndInRootPx = state.bottomPanelEndInRootPx,
        fallbackEndInRootPx = state.bottomEdgeInRootPx,
        fallbackExtentPx = state.bottomFadeDp * density,
        contentHeightPx = contentHeightPx,
    )

    fun panelEffect(
        registered: Boolean,
        edge: ArborBlurEdge,
        radiusDp: Float,
        range: Pair<Float, Float>,
        gradual: Boolean,
        cornerDp: Float,
        mergeDp: Float,
        tint: Color,
    ): RenderEffect? {
        if (!registered || range.second <= range.first) return null

        val shader = RuntimeShader(GLASS_PANEL_SHADER).apply {
            setFloatUniform("uSize", contentWidthPx, contentHeightPx)
            setFloatUniform("uRange", range.first, range.second)
            setFloatUniform("uEdge", if (edge == ArborBlurEdge.TOP) 0f else 1f)
            setFloatUniform("uGradual", if (gradual) 1f else 0f)
            setFloatUniform("uCorner", cornerDp * density)
            setFloatUniform("uMerge", mergeDp * density)
            setFloatUniform("uTint", tint.red, tint.green, tint.blue, tint.alpha)
        }
        val maskAndTint = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val radiusPx = nativeBlurRadiusPx(radiusDp, density)
        val input = if (radiusPx >= MIN_VISIBLE_RADIUS_PX) {
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.MIRROR)
        } else {
            RenderEffect.createOffsetEffect(0f, 0f)
        }
        return RenderEffect.createChainEffect(maskAndTint, input)
    }

    val composeEffect = remember(
        state.topRegistered,
        state.bottomRegistered,
        state.topRadiusDp,
        state.bottomRadiusDp,
        topRange,
        bottomRange,
        state.topGradual,
        state.bottomGradual,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
        state.topTint,
        state.bottomTint,
        contentWidthPx,
        contentHeightPx,
        density,
    ) {
        val identity = RenderEffect.createOffsetEffect(0f, 0f)
        val top = panelEffect(
            registered = state.topRegistered,
            edge = ArborBlurEdge.TOP,
            radiusDp = state.topRadiusDp,
            range = topRange,
            gradual = state.topGradual,
            cornerDp = state.topCornerRadiusDp,
            mergeDp = state.topMergeDp,
            tint = state.topTint,
        )
        val bottom = panelEffect(
            registered = state.bottomRegistered,
            edge = ArborBlurEdge.BOTTOM,
            radiusDp = state.bottomRadiusDp,
            range = bottomRange,
            gradual = state.bottomGradual,
            cornerDp = state.bottomCornerRadiusDp,
            mergeDp = state.bottomMergeDp,
            tint = state.bottomTint,
        )
        var result = identity
        if (top != null) result = RenderEffect.createBlendModeEffect(result, top, BlendMode.SRC_OVER)
        if (bottom != null) result = RenderEffect.createBlendModeEffect(result, bottom, BlendMode.SRC_OVER)
        result.asComposeRenderEffect()
    }

    measured.graphicsLayer { renderEffect = composeEffect }
}

internal fun arborBlurProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
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

/** Registers one chrome panel. Rendering happens entirely in arborBackdropSource. */
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
            tint = tint,
        )
    }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    val overlayDistancePx = with(LocalDensity.current) { overlayDistance.toPx() }
    this.onGloballyPositioned { coordinates ->
        if (edge == ArborBlurEdge.BOTTOM) {
            val bounds = coordinates.boundsInRoot()
            val actualExtent = overlayDistancePx.coerceIn(1f, coordinates.size.height.toFloat().coerceAtLeast(1f))
            state.updateBottomPanel(bounds.bottom - actualExtent, bounds.bottom)
        }
    }
}

internal fun alignedTopBlurRange(fadeDistancePx: Float, contentHeightPx: Float): Pair<Float, Float> =
    0f to fadeDistancePx.coerceIn(1f, contentHeightPx.coerceAtLeast(1f))

internal fun alignedBottomBlurRange(
    sourceTopInRootPx: Float,
    panelStartInRootPx: Float,
    panelEndInRootPx: Float,
    fallbackEndInRootPx: Float,
    fallbackExtentPx: Float,
    contentHeightPx: Float,
): Pair<Float, Float> {
    val height = contentHeightPx.coerceAtLeast(1f)
    val endRoot = panelEndInRootPx.takeIf { it.isFinite() }
        ?: fallbackEndInRootPx.takeIf { it.isFinite() }
        ?: (sourceTopInRootPx + height)
    val startRoot = panelStartInRootPx.takeIf { it.isFinite() }
        ?: (endRoot - fallbackExtentPx.coerceAtLeast(1f))
    val start = (startRoot - sourceTopInRootPx).coerceIn(0f, height)
    val end = (endRoot - sourceTopInRootPx).coerceIn(start, height)
    return start to end
}

internal fun nativeBlurRadiusPx(radiusDp: Float, density: Float): Float =
    (radiusDp.coerceAtLeast(0f) * density.coerceAtLeast(0f) * NATIVE_BLUR_RADIUS_SCALE)
        .coerceAtMost(MAX_NATIVE_BLUR_RADIUS_PX)

internal fun panelTintAlpha(alpha: Float): Float =
    (alpha.coerceIn(0f, 1f) * PANEL_OPACITY_BOOST).coerceIn(0f, 1f)

internal fun quantizeBlurRadiusDp(radiusDp: Float): Float =
    ((radiusDp.coerceAtLeast(0f) / BLUR_RADIUS_STEP_DP).roundToInt() * BLUR_RADIUS_STEP_DP)

private const val BLUR_RADIUS_STEP_DP = 0.25f
private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val NATIVE_BLUR_RADIUS_SCALE = 0.72f
private const val MAX_NATIVE_BLUR_RADIUS_PX = 96f
private const val DEFAULT_MAX_RADIUS_DP = 56f
private const val DEFAULT_MIN_RADIUS_DP = 16f
private const val PANEL_OPACITY_BOOST = 1.30f
private const val DEFAULT_PANEL_CORNER_RADIUS_DP = 28f
private const val DEFAULT_MERGE_DISTANCE_DP = 34f
private const val DEFAULT_TOP_FADE_DP = 128f
private const val DEFAULT_BOTTOM_FADE_DP = 208f

/**
 * Masks a native Gaussian input and adds Arbor's glass tint. The output is
 * transparent outside the panel so it can be composited over the untouched
 * source with SRC_OVER. Blur and tint therefore share the exact same geometry.
 */
private val GLASS_PANEL_SHADER = """
    uniform shader content;
    uniform float2 uSize;
    uniform float2 uRange;
    uniform float uEdge;
    uniform float uGradual;
    uniform float uCorner;
    uniform float uMerge;
    uniform float4 uTint;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    float roundedPanelMask(float2 coord) {
        float start = uRange.x;
        float end = uRange.y;
        if (coord.y < start || coord.y > end) return 0.0;
        float extent = max(end - start, 1.0);
        float radius = clamp(uCorner, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5) return 1.0;

        if (uEdge < 0.5) {
            if (coord.y <= end - radius) return 1.0;
            if (coord.x < radius) {
                float d = length(coord - float2(radius, end - radius));
                return 1.0 - smoothstep(radius - 0.75, radius + 0.75, d);
            }
            if (coord.x > uSize.x - radius) {
                float d = length(coord - float2(uSize.x - radius, end - radius));
                return 1.0 - smoothstep(radius - 0.75, radius + 0.75, d);
            }
            return 1.0;
        }

        if (coord.y >= start + radius) return 1.0;
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
        float shape = roundedPanelMask(coord);
        if (shape < 0.001) return half4(0.0);

        float merge = max(uMerge, 1.0);
        float feather = uEdge < 0.5
            ? smoother((uRange.y - coord.y) / merge)
            : smoother((coord.y - uRange.x) / merge);
        float mask = shape * mix(1.0, feather, uGradual);
        if (mask < 0.001) return half4(0.0);

        half4 blurred = half4(content.eval(coord));
        half luminance = dot(blurred.rgb, half3(0.2126, 0.7152, 0.0722));
        half3 glass = mix(half3(luminance), blurred.rgb, half(1.055));
        glass = clamp(glass * half(1.012) + half3(0.003), half3(0.0), half3(1.0));
        glass = mix(glass, half3(uTint.rgb), half(uTint.a));

        half alpha = half(mask) * blurred.a;
        return half4(glass * alpha, alpha);
    }
""".trimIndent()
