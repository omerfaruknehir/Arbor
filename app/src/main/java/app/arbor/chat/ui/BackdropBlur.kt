package app.arbor.chat.ui

import android.graphics.BlendMode
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import app.arbor.chat.settings.effectiveChromeEdgeSoftness
import app.arbor.chat.settings.snapChromeEdgeSoftness
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

/** Which chrome edge owns a backdrop panel. */
enum class ArborBlurEdge { TOP, BOTTOM }

/**
 * Shared backdrop state. Panel bounds are stored in root coordinates and then
 * converted to the source layer's coordinates. The blur mask and tint therefore
 * use the exact same geometry even while a Material top bar is collapsing.
 */
@Stable
class ArborBackdropBlurState internal constructor() {
    internal var topRadiusDp by mutableFloatStateOf(0f)
    internal var bottomRadiusDp by mutableFloatStateOf(0f)
    internal var topPanelHeightDp by mutableFloatStateOf(DEFAULT_TOP_PANEL_HEIGHT_DP)
    internal var bottomPanelHeightDp by mutableFloatStateOf(DEFAULT_BOTTOM_PANEL_HEIGHT_DP)
    internal var topSoftness by mutableFloatStateOf(0f)
    internal var bottomSoftness by mutableFloatStateOf(0f)
    internal var topCornerRadiusDp by mutableFloatStateOf(DEFAULT_PANEL_CORNER_RADIUS_DP)
    internal var bottomCornerRadiusDp by mutableFloatStateOf(DEFAULT_PANEL_CORNER_RADIUS_DP)
    internal var topMergeDp by mutableFloatStateOf(0f)
    internal var bottomMergeDp by mutableFloatStateOf(0f)
    internal var topTint by mutableStateOf(Color.Transparent)
    internal var bottomTint by mutableStateOf(Color.Transparent)
    internal var topSaturation by mutableFloatStateOf(DEFAULT_GLASS_SATURATION)
    internal var bottomSaturation by mutableFloatStateOf(DEFAULT_GLASS_SATURATION)
    internal var topContrast by mutableFloatStateOf(DEFAULT_GLASS_CONTRAST)
    internal var bottomContrast by mutableFloatStateOf(DEFAULT_GLASS_CONTRAST)
    internal var topBrightness by mutableFloatStateOf(DEFAULT_GLASS_BRIGHTNESS)
    internal var bottomBrightness by mutableFloatStateOf(DEFAULT_GLASS_BRIGHTNESS)
    internal var topEdgeHighlight by mutableFloatStateOf(DEFAULT_EDGE_HIGHLIGHT)
    internal var bottomEdgeHighlight by mutableFloatStateOf(DEFAULT_EDGE_HIGHLIGHT)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var topPanelStartInRootPx by mutableFloatStateOf(Float.NaN)
    internal var topPanelEndInRootPx by mutableFloatStateOf(Float.NaN)
    internal var bottomPanelStartInRootPx by mutableFloatStateOf(Float.NaN)
    internal var bottomPanelEndInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(
        edge: ArborBlurEdge,
        radiusDp: Float,
        panelHeightDp: Float,
        cornerRadiusDp: Float,
        mergeDp: Float,
        softness: Float,
        tint: Color,
        saturation: Float,
        contrast: Float,
        brightness: Float,
        edgeHighlight: Float,
    ) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val height = panelHeightDp.coerceAtLeast(1f)
        val normalizedSoftness = snapChromeEdgeSoftness(softness)
        val corner = if (normalizedSoftness == 0f) cornerRadiusDp.coerceAtLeast(0f) else 0f
        val merge = if (normalizedSoftness == 0f) 0f else mergeDp.coerceIn(0f, height * 2f)
        val normalizedSaturation = saturation.coerceIn(0.75f, 1.35f)
        val normalizedContrast = contrast.coerceIn(0.85f, 1.20f)
        val normalizedBrightness = brightness.coerceIn(0.85f, 1.15f)
        val normalizedHighlight = edgeHighlight.coerceIn(0f, 0.12f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topPanelHeightDp != height) topPanelHeightDp = height
                if (topSoftness != normalizedSoftness) topSoftness = normalizedSoftness
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
                if (topTint != tint) topTint = tint
                if (topSaturation != normalizedSaturation) topSaturation = normalizedSaturation
                if (topContrast != normalizedContrast) topContrast = normalizedContrast
                if (topBrightness != normalizedBrightness) topBrightness = normalizedBrightness
                if (topEdgeHighlight != normalizedHighlight) topEdgeHighlight = normalizedHighlight
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomPanelHeightDp != height) bottomPanelHeightDp = height
                if (bottomSoftness != normalizedSoftness) bottomSoftness = normalizedSoftness
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
                if (bottomTint != tint) bottomTint = tint
                if (bottomSaturation != normalizedSaturation) bottomSaturation = normalizedSaturation
                if (bottomContrast != normalizedContrast) bottomContrast = normalizedContrast
                if (bottomBrightness != normalizedBrightness) bottomBrightness = normalizedBrightness
                if (bottomEdgeHighlight != normalizedHighlight) bottomEdgeHighlight = normalizedHighlight
            }
        }
    }

    internal fun updateSource(topInRootPx: Float) {
        if (abs(sourceTopInRootPx - topInRootPx) >= 0.5f) sourceTopInRootPx = topInRootPx
    }

    internal fun updatePanelBounds(edge: ArborBlurEdge, startInRootPx: Float, endInRootPx: Float) {
        val start = minOf(startInRootPx, endInRootPx)
        val end = maxOf(startInRootPx, endInRootPx)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (!topPanelStartInRootPx.isFinite() || abs(topPanelStartInRootPx - start) >= 0.5f) topPanelStartInRootPx = start
                if (!topPanelEndInRootPx.isFinite() || abs(topPanelEndInRootPx - end) >= 0.5f) topPanelEndInRootPx = end
            }
            ArborBlurEdge.BOTTOM -> {
                if (!bottomPanelStartInRootPx.isFinite() || abs(bottomPanelStartInRootPx - start) >= 0.5f) bottomPanelStartInRootPx = start
                if (!bottomPanelEndInRootPx.isFinite() || abs(bottomPanelEndInRootPx - end) >= 0.5f) bottomPanelEndInRootPx = end
            }
        }
    }

    internal fun clear(edge: ArborBlurEdge) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRadiusDp = 0f
                topTint = Color.Transparent
                topPanelStartInRootPx = Float.NaN
                topPanelEndInRootPx = Float.NaN
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusDp = 0f
                bottomTint = Color.Transparent
                bottomPanelStartInRootPx = Float.NaN
                bottomPanelEndInRootPx = Float.NaN
            }
        }
    }
}

@Composable
fun rememberArborBackdropBlurState(): ArborBackdropBlurState = remember { ArborBackdropBlurState() }

/**
 * Applies a native Skia Gaussian blur once, masks it to the registered top and
 * bottom panels, then composites it over the untouched source. Unlike the old
 * sparse nine-tap shader, large radii do not reveal directional sample patterns.
 * Tint and edge highlight are drawn after the blur using the same panel bounds.
 */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val density = LocalDensity.current.density
    val topRadiusPx = state.topRadiusDp * density
    val bottomRadiusPx = state.bottomRadiusDp * density
    val blurRadiusPx = resolveNativeBlurRadiusPx(topRadiusPx, bottomRadiusPx)
    val blurActive = blurRadiusPx >= MIN_VISIBLE_RADIUS_PX
    val visualsActive = blurActive || state.topTint.alpha > 0f || state.bottomTint.alpha > 0f ||
        state.topEdgeHighlight > 0f || state.bottomEdgeHighlight > 0f

    var contentWidthPx by remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onGloballyPositioned { coordinates ->
        val nextWidth = coordinates.size.width.toFloat().coerceAtLeast(1f)
        val nextHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
        if (contentWidthPx != nextWidth) contentWidthPx = nextWidth
        if (contentHeightPx != nextHeight) contentHeightPx = nextHeight
        state.updateSource(coordinates.boundsInRoot().top)
    }
    if (!visualsActive || contentWidthPx <= 0f || contentHeightPx <= 0f) return@composed measured

    val topStartPx = state.topPanelStartInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?: 0f
    val topEndPx = state.topPanelEndInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?: (topStartPx + state.topPanelHeightDp * density)
    val bottomEndPx = state.bottomPanelEndInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?: contentHeightPx
    val bottomStartPx = state.bottomPanelStartInRootPx
        .takeIf { it.isFinite() }
        ?.minus(state.sourceTopInRootPx)
        ?: (bottomEndPx - state.bottomPanelHeightDp * density)

    val normalizedTopStart = topStartPx.coerceIn(-contentHeightPx, contentHeightPx * 2f)
    val normalizedTopEnd = max(topEndPx, normalizedTopStart + 1f).coerceIn(-contentHeightPx, contentHeightPx * 2f)
    val normalizedBottomEnd = bottomEndPx.coerceIn(-contentHeightPx, contentHeightPx * 2f)
    val normalizedBottomStart = minOf(bottomStartPx, normalizedBottomEnd - 1f).coerceIn(-contentHeightPx, contentHeightPx * 2f)

    val maskShader = if (blurActive) remember(
        blurRadiusPx,
        contentWidthPx,
        contentHeightPx,
        normalizedTopStart,
        normalizedTopEnd,
        normalizedBottomStart,
        normalizedBottomEnd,
        state.topSoftness,
        state.bottomSoftness,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
        state.topSaturation,
        state.bottomSaturation,
        state.topContrast,
        state.bottomContrast,
        state.topBrightness,
        state.bottomBrightness,
        topRadiusPx,
        bottomRadiusPx,
    ) {
        RuntimeShader(PANEL_MASK_SHADER).apply {
            setFloatUniform("uSize", contentWidthPx.coerceAtLeast(1f), contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uTopBounds", normalizedTopStart, normalizedTopEnd)
            setFloatUniform("uBottomBounds", normalizedBottomStart, normalizedBottomEnd)
            setFloatUniform("uEnabled", if (topRadiusPx >= MIN_VISIBLE_RADIUS_PX) 1f else 0f, if (bottomRadiusPx >= MIN_VISIBLE_RADIUS_PX) 1f else 0f)
            setFloatUniform("uStrength", (topRadiusPx / blurRadiusPx).coerceIn(0f, 1f), (bottomRadiusPx / blurRadiusPx).coerceIn(0f, 1f))
            setFloatUniform("uSoftness", state.topSoftness, state.bottomSoftness)
            setFloatUniform("uCorner", state.topCornerRadiusDp * density, state.bottomCornerRadiusDp * density)
            setFloatUniform("uMerge", state.topMergeDp * density, state.bottomMergeDp * density)
            setFloatUniform("uSaturation", state.topSaturation, state.bottomSaturation)
            setFloatUniform("uContrast", state.topContrast, state.bottomContrast)
            setFloatUniform("uBrightness", state.topBrightness, state.bottomBrightness)
        }
    } else null

    val composeEffect = if (maskShader != null) remember(maskShader, blurRadiusPx) {
        ArborRenderProfiler.recordBlurEffectBuild(4)
        val identity = RenderEffect.createOffsetEffect(0f, 0f)
        val gaussian = RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
        val mask = RenderEffect.createRuntimeShaderEffect(maskShader, "content")
        val maskedGaussian = RenderEffect.createChainEffect(mask, gaussian)
        RenderEffect.createBlendModeEffect(identity, maskedGaussian, BlendMode.SRC_OVER).asComposeRenderEffect()
    } else null

    val decorated = measured.drawWithContent {
        val started = if (blurActive && ArborRenderProfiler.enabled) System.nanoTime() else 0L
        drawContent()
        drawPanelOverlay(
            edge = ArborBlurEdge.TOP,
            start = normalizedTopStart,
            end = normalizedTopEnd,
            softness = state.topSoftness,
            mergeDistance = state.topMergeDp * density,
            cornerRadius = state.topCornerRadiusDp * density,
            tint = state.topTint,
            highlightAlpha = state.topEdgeHighlight,
        )
        drawPanelOverlay(
            edge = ArborBlurEdge.BOTTOM,
            start = normalizedBottomStart,
            end = normalizedBottomEnd,
            softness = state.bottomSoftness,
            mergeDistance = state.bottomMergeDp * density,
            cornerRadius = state.bottomCornerRadiusDp * density,
            tint = state.bottomTint,
            highlightAlpha = state.bottomEdgeHighlight,
        )
        if (blurActive && ArborRenderProfiler.enabled) {
            ArborRenderProfiler.recordBlurFrame(
                cpuNanos = System.nanoTime() - started,
                processedPixels = (size.width.toLong() * size.height.toLong() * 2L).coerceAtLeast(0L),
                sourceTraversals = 1,
                layerReplays = 0,
                downsampleLevels = 0,
                upsampleLevels = 0,
                captureUpdates = 0,
            )
        }
    }
    if (composeEffect == null) decorated else decorated.graphicsLayer { renderEffect = composeEffect }
}

internal fun arborBlurProgress(progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return p * p * p * (p * (p * 6f - 15f) + 10f)
}

/** Current 0-100% control: no minimum-radius jump and no quantized discontinuity. */
internal fun calculateBlurRadiusDp(
    strength: Float,
    maxRadiusDp: Float = DEFAULT_MAX_RADIUS_DP,
): Float = maxRadiusDp.coerceAtLeast(0f) * strength.coerceIn(0f, 1f)

internal fun resolveNativeBlurRadiusPx(topRadiusPx: Float, bottomRadiusPx: Float): Float =
    max(topRadiusPx, bottomRadiusPx).coerceAtLeast(0f)

internal fun calculateMergeDistanceDp(
    edgeSoftness: Float,
    maximumMergeDp: Float = MAXIMUM_MERGE_DISTANCE_DP,
): Float = maximumMergeDp.coerceAtLeast(0f) * edgeSoftnessActivation(edgeSoftness)

internal fun edgeSoftnessActivation(edgeSoftness: Float): Float =
    arborBlurProgress(effectiveChromeEdgeSoftness(edgeSoftness))

/** Registers one panel. The source layer draws both the blur mask and tint. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    strength: Float,
    edgeSoftness: Float,
    overlayOpacity: Float = 1f,
    tint: Color,
    edge: ArborBlurEdge = ArborBlurEdge.TOP,
    maxRadius: Dp = DEFAULT_MAX_RADIUS_DP.dp,
    panelHeight: Dp = if (edge == ArborBlurEdge.TOP) DEFAULT_TOP_PANEL_HEIGHT_DP.dp else DEFAULT_BOTTOM_PANEL_HEIGHT_DP.dp,
    cornerRadius: Dp = DEFAULT_PANEL_CORNER_RADIUS_DP.dp,
    maximumMergeDistance: Dp = MAXIMUM_MERGE_DISTANCE_DP.dp,
    saturation: Float = DEFAULT_GLASS_SATURATION,
    contrast: Float = DEFAULT_GLASS_CONTRAST,
    brightness: Float = DEFAULT_GLASS_BRIGHTNESS,
    edgeHighlight: Float = DEFAULT_EDGE_HIGHLIGHT,
): Modifier = composed {
    val normalizedSoftness = snapChromeEdgeSoftness(edgeSoftness)
    val radiusDp = calculateBlurRadiusDp(strength = strength, maxRadiusDp = maxRadius.value)
    val mergeDp = calculateMergeDistanceDp(
        edgeSoftness = normalizedSoftness,
        maximumMergeDp = maximumMergeDistance.value,
    )
    val exactTint = applyOverlayOpacity(tint, overlayOpacity)
    val panelHeightPx = with(LocalDensity.current) { panelHeight.toPx() }.coerceAtLeast(1f)

    SideEffect {
        state.update(
            edge = edge,
            radiusDp = radiusDp,
            panelHeightDp = panelHeight.value,
            cornerRadiusDp = cornerRadius.value,
            mergeDp = mergeDp,
            softness = normalizedSoftness,
            tint = exactTint,
            saturation = saturation,
            contrast = contrast,
            brightness = brightness,
            edgeHighlight = edgeHighlight,
        )
    }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        when (edge) {
            ArborBlurEdge.TOP -> state.updatePanelBounds(edge, bounds.top, bounds.top + panelHeightPx)
            ArborBlurEdge.BOTTOM -> state.updatePanelBounds(edge, bounds.bottom - panelHeightPx, bounds.bottom)
        }
    }
}

private fun DrawScope.drawPanelOverlay(
    edge: ArborBlurEdge,
    start: Float,
    end: Float,
    softness: Float,
    mergeDistance: Float,
    cornerRadius: Float,
    tint: Color,
    highlightAlpha: Float,
) {
    if (end <= start) return
    val softnessActive = softness > 0f && mergeDistance > 0f
    if (tint.alpha > 0f) {
        if (!softnessActive) {
            val extent = end - start
            val radius = cornerRadius.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
            val path = Path().apply {
                when (edge) {
                    ArborBlurEdge.TOP -> addRoundRect(
                        RoundRect(
                            0f, start, size.width, end,
                            CornerRadius.Zero, CornerRadius.Zero,
                            CornerRadius(radius, radius), CornerRadius(radius, radius),
                        ),
                    )
                    ArborBlurEdge.BOTTOM -> addRoundRect(
                        RoundRect(
                            0f, start, size.width, end,
                            CornerRadius(radius, radius), CornerRadius(radius, radius),
                            CornerRadius.Zero, CornerRadius.Zero,
                        ),
                    )
                }
            }
            clipPath(path) {
                drawRect(tint, topLeft = Offset(0f, start), size = Size(size.width, extent))
            }
        } else {
            val half = mergeDistance * 0.5f
            when (edge) {
                ArborBlurEdge.TOP -> {
                    val solidEnd = end - half
                    if (solidEnd > start) drawRect(tint, topLeft = Offset(0f, start), size = Size(size.width, solidEnd - start))
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to tint,
                            0.5f to tint.copy(alpha = tint.alpha * 0.5f),
                            1f to Color.Transparent,
                            startY = solidEnd,
                            endY = end + half,
                        ),
                        topLeft = Offset(0f, solidEnd),
                        size = Size(size.width, mergeDistance),
                    )
                }
                ArborBlurEdge.BOTTOM -> {
                    val gradientStart = start - half
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to tint.copy(alpha = tint.alpha * 0.5f),
                            1f to tint,
                            startY = gradientStart,
                            endY = start + half,
                        ),
                        topLeft = Offset(0f, gradientStart),
                        size = Size(size.width, mergeDistance),
                    )
                    val solidStart = start + half
                    if (end > solidStart) drawRect(tint, topLeft = Offset(0f, solidStart), size = Size(size.width, end - solidStart))
                }
            }
        }
    }

    val alpha = highlightAlpha.coerceIn(0f, 0.12f)
    if (alpha > 0f) {
        val y = if (edge == ArborBlurEdge.TOP) end else start
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** Overlay opacity is absolute: 0% is transparent and 100% is fully opaque. */
internal fun applyOverlayOpacity(tint: Color, opacity: Float): Color =
    tint.copy(alpha = opacity.coerceIn(0f, 1f))

internal fun quantizeBlurRadiusDp(radiusDp: Float): Float = radiusDp.coerceAtLeast(0f)

private const val MIN_VISIBLE_RADIUS_PX = 0.0001f
private const val DEFAULT_MAX_RADIUS_DP = 56f
private const val DEFAULT_PANEL_CORNER_RADIUS_DP = 28f
private const val MAXIMUM_MERGE_DISTANCE_DP = 68f
private const val DEFAULT_TOP_PANEL_HEIGHT_DP = 128f
private const val DEFAULT_BOTTOM_PANEL_HEIGHT_DP = 208f
private const val DEFAULT_GLASS_SATURATION = 1.10f
private const val DEFAULT_GLASS_CONTRAST = 1.025f
private const val DEFAULT_GLASS_BRIGHTNESS = 1.008f
private const val DEFAULT_EDGE_HIGHLIGHT = 0.035f

private val PANEL_MASK_SHADER = """
    uniform shader content;
    uniform float2 uSize;
    uniform float2 uTopBounds;
    uniform float2 uBottomBounds;
    uniform float2 uEnabled;
    uniform float2 uStrength;
    uniform float2 uSoftness;
    uniform float2 uCorner;
    uniform float2 uMerge;
    uniform float2 uSaturation;
    uniform float2 uContrast;
    uniform float2 uBrightness;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    float roundedTopPanelMask(float2 coord, float start, float end, float radius) {
        if (coord.y < start || coord.y > end) return 0.0;
        float extent = end - start;
        radius = clamp(radius, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5 || coord.y <= end - radius) return 1.0;
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

    float roundedBottomPanelMask(float2 coord, float start, float end, float radius) {
        if (coord.y < start || coord.y > end) return 0.0;
        float extent = end - start;
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
        float topMix = 0.0;
        if (uEnabled.x > 0.5) {
            if (uSoftness.x <= 0.0 || uMerge.x <= 0.0) {
                topMix = roundedTopPanelMask(coord, uTopBounds.x, uTopBounds.y, uCorner.x);
            } else {
                float halfSpan = uMerge.x * 0.5;
                topMix = 1.0 - smoother((coord.y - (uTopBounds.y - halfSpan)) / max(uMerge.x, 1.0));
                topMix *= step(uTopBounds.x - 0.5, coord.y);
            }
            topMix *= uStrength.x;
        }

        float bottomMix = 0.0;
        if (uEnabled.y > 0.5) {
            if (uSoftness.y <= 0.0 || uMerge.y <= 0.0) {
                bottomMix = roundedBottomPanelMask(coord, uBottomBounds.x, uBottomBounds.y, uCorner.y);
            } else {
                float halfSpan = uMerge.y * 0.5;
                bottomMix = smoother((coord.y - (uBottomBounds.x - halfSpan)) / max(uMerge.y, 1.0));
                bottomMix *= 1.0 - step(uBottomBounds.y + 0.5, coord.y);
            }
            bottomMix *= uStrength.y;
        }

        float mask = saturate(max(topMix, bottomMix));
        if (mask < 0.0001) return half4(0.0);

        float topWeight = step(bottomMix, topMix);
        float saturation = mix(uSaturation.y, uSaturation.x, topWeight);
        float contrast = mix(uContrast.y, uContrast.x, topWeight);
        float brightness = mix(uBrightness.y, uBrightness.x, topWeight);
        float4 adjusted = float4(content.eval(coord));
        float luminance = dot(adjusted.rgb, float3(0.2126, 0.7152, 0.0722));
        adjusted.rgb = mix(float3(luminance), adjusted.rgb, saturation);
        adjusted.rgb = (adjusted.rgb - float3(0.5)) * contrast + float3(0.5);
        adjusted.rgb *= brightness;
        return half4(adjusted * mask);
    }
""".trimIndent()
