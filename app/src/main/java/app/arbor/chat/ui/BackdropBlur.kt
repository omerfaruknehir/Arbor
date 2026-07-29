package app.arbor.chat.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import app.arbor.chat.settings.chromeEdgeCornerTransition
import app.arbor.chat.settings.effectiveChromeEdgeSoftness
import app.arbor.chat.settings.snapChromeEdgeSoftness
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

/** Which chrome edge owns a backdrop panel. */
enum class ArborBlurEdge { TOP, BOTTOM }

/** Runtime-only developer visualization. Normal chrome never draws boundary lines. */
internal object ArborBackdropDebugOverlay {
    var enabled by mutableStateOf(false)
    var thicknessDp by mutableFloatStateOf(3f)

    fun update(enabled: Boolean, thicknessDp: Float) {
        this.enabled = enabled
        this.thicknessDp = thicknessDp.coerceIn(1f, 8f)
    }
}

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
        edgeHighlight: Float,
    ) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val height = panelHeightDp.coerceAtLeast(1f)
        val normalizedSoftness = snapChromeEdgeSoftness(softness)
        val corner = cornerRadiusDp.coerceAtLeast(0f) * (1f - chromeEdgeCornerTransition(normalizedSoftness))
        val merge = mergeDp.coerceIn(0f, height * 2f)
        val normalizedHighlight = edgeHighlight.coerceIn(0f, 0.12f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topPanelHeightDp != height) topPanelHeightDp = height
                if (topSoftness != normalizedSoftness) topSoftness = normalizedSoftness
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
                if (topTint != tint) topTint = tint
                if (topEdgeHighlight != normalizedHighlight) topEdgeHighlight = normalizedHighlight
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomPanelHeightDp != height) bottomPanelHeightDp = height
                if (bottomSoftness != normalizedSoftness) bottomSoftness = normalizedSoftness
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
                if (bottomTint != tint) bottomTint = tint
                if (bottomEdgeHighlight != normalizedHighlight) bottomEdgeHighlight = normalizedHighlight
            }
        }
    }

    internal fun updateSource(topInRootPx: Float) {
        val stableTop = round(topInRootPx)
        if (abs(sourceTopInRootPx - stableTop) >= 0.5f) sourceTopInRootPx = stableTop
    }

    internal fun updatePanelBounds(edge: ArborBlurEdge, startInRootPx: Float, endInRootPx: Float) {
        // Keep the blur/tint boundary on physical pixel centers. Fractional
        // layout coordinates can alternate coverage while scrolling and show
        // up as a one-pixel flicker at the panel edge.
        val start = round(minOf(startInRootPx, endInRootPx))
        val end = round(maxOf(startInRootPx, endInRootPx))
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
 * Applies Arbor's device-proven three-axis edge blur directly to the content
 * layer. The graph keeps the physical-device-reliable RenderEffect path from
 * 0.17.8, while each axis uses the denser direct-sample kernel from 0.17.12 so
 * large radii do not expose the gaps between bilinear-paired taps.
 *
 * The panel tint is an outer draw modifier, so it stays sharp while the content
 * below it receives the chained blur effect.
 */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@composed this

    val density = LocalDensity.current.density
    val topRadiusPx = state.topRadiusDp * density
    val bottomRadiusPx = state.bottomRadiusDp * density
    val topBlurActive = topRadiusPx >= MIN_VISIBLE_RADIUS_PX
    val bottomBlurActive = bottomRadiusPx >= MIN_VISIBLE_RADIUS_PX
    val blurActive = topBlurActive || bottomBlurActive
    val debugBoundaryEnabled = ArborBackdropDebugOverlay.enabled
    val debugBoundaryThicknessDp = ArborBackdropDebugOverlay.thicknessDp
    val visualsActive = blurActive || state.topTint.alpha > 0f || state.bottomTint.alpha > 0f ||
        state.topEdgeHighlight > 0f || state.bottomEdgeHighlight > 0f || debugBoundaryEnabled

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

    val blurEffect = if (blurActive) remember(
        topRadiusPx,
        bottomRadiusPx,
        contentWidthPx,
        contentHeightPx,
        normalizedTopStart,
        normalizedTopEnd,
        normalizedBottomStart,
        normalizedBottomEnd,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
    ) {
        buildPanelEdgeBlurEffect(
            topRadiusPx = topRadiusPx,
            bottomRadiusPx = bottomRadiusPx,
            topStartPx = normalizedTopStart,
            topEndPx = normalizedTopEnd,
            bottomStartPx = normalizedBottomStart,
            bottomEndPx = normalizedBottomEnd,
            contentWidthPx = contentWidthPx,
            contentHeightPx = contentHeightPx,
            density = density,
            topCornerRadiusDp = state.topCornerRadiusDp,
            bottomCornerRadiusDp = state.bottomCornerRadiusDp,
            topMergeDp = state.topMergeDp,
            bottomMergeDp = state.bottomMergeDp,
        ).asComposeRenderEffect()
    } else null

    // drawWithContent is placed outside graphicsLayer in the modifier chain:
    // drawContent() receives the filtered child, while tint and diagnostics are
    // drawn afterward and never enter the blur kernel.
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
            debugBoundary = debugBoundaryEnabled,
            debugThickness = debugBoundaryThicknessDp * density,
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
            debugBoundary = debugBoundaryEnabled,
            debugThickness = debugBoundaryThicknessDp * density,
        )
        if (blurActive && ArborRenderProfiler.enabled) {
            ArborRenderProfiler.recordBlurFrame(
                cpuNanos = System.nanoTime() - started,
                processedPixels = (size.width.toLong() * size.height.toLong() * 3L).coerceAtLeast(0L),
                sourceTraversals = 1,
                layerReplays = if (blurEffect != null) 1 else 0,
                downsampleLevels = 0,
                upsampleLevels = 0,
                captureUpdates = 0,
            )
        }
    }
    if (blurEffect == null) decorated else decorated.graphicsLayer { renderEffect = blurEffect }
}

/**
 * Builds the exact native RenderEffect graph used by the source modifier.
 * Keeping the graph in a callable function lets the instrumented test ask the
 * platform to compile both RuntimeShader and chain nodes on a real device.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun buildPanelEdgeBlurEffect(
    topRadiusPx: Float,
    bottomRadiusPx: Float,
    topStartPx: Float,
    topEndPx: Float,
    bottomStartPx: Float,
    bottomEndPx: Float,
    contentWidthPx: Float,
    contentHeightPx: Float,
    density: Float,
    topCornerRadiusDp: Float,
    bottomCornerRadiusDp: Float,
    topMergeDp: Float,
    bottomMergeDp: Float,
): RenderEffect {
    fun shader(directionX: Float, directionY: Float) = RuntimeShader(PANEL_EDGE_BLUR_SHADER).apply {
        setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        setFloatUniform("uTopBounds", topStartPx, topEndPx)
        setFloatUniform("uBottomBounds", bottomStartPx, bottomEndPx)
        setFloatUniform("uSize", contentWidthPx.coerceAtLeast(1f), contentHeightPx.coerceAtLeast(1f))
        setFloatUniform("uCorner", topCornerRadiusDp * density, bottomCornerRadiusDp * density)
        setFloatUniform("uMerge", topMergeDp * density, bottomMergeDp * density)
        setFloatUniform("uDirection", directionX, directionY)
    }

    val first = RenderEffect.createRuntimeShaderEffect(
        shader(BLUR_AXIS_A_X, BLUR_AXIS_A_Y),
        "content",
    )
    val second = RenderEffect.createRuntimeShaderEffect(
        shader(BLUR_AXIS_B_X, BLUR_AXIS_B_Y),
        "content",
    )
    val third = RenderEffect.createRuntimeShaderEffect(
        shader(BLUR_AXIS_C_X, BLUR_AXIS_C_Y),
        "content",
    )
    ArborRenderProfiler.recordBlurEffectBuild(3)
    return RenderEffect.createChainEffect(
        third,
        RenderEffect.createChainEffect(second, first),
    )
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
    edgeHighlight: Float = DEFAULT_EDGE_HIGHLIGHT,
    expandToMeasuredHeight: Boolean = false,
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
            edgeHighlight = edgeHighlight,
        )
    }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        val measuredHeightPx = (bounds.bottom - bounds.top).coerceAtLeast(1f)
        val effectiveHeightPx = if (expandToMeasuredHeight) {
            max(panelHeightPx, measuredHeightPx)
        } else {
            panelHeightPx
        }
        when (edge) {
            ArborBlurEdge.TOP -> state.updatePanelBounds(edge, bounds.top, bounds.top + effectiveHeightPx)
            ArborBlurEdge.BOTTOM -> state.updatePanelBounds(edge, bounds.bottom - effectiveHeightPx, bounds.bottom)
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
    debugBoundary: Boolean,
    debugThickness: Float,
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
                    val gradientEnd = end + half
                    val extent = (gradientEnd - start).coerceAtLeast(1f)
                    val solidStop = ((end - half - start) / extent).coerceIn(0f, 1f)
                    val middleStop = ((end - start) / extent).coerceIn(solidStop, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to tint,
                                solidStop to tint,
                                middleStop to tint.copy(alpha = tint.alpha * 0.5f),
                                1f to Color.Transparent,
                            ),
                            startY = start,
                            endY = gradientEnd,
                        ),
                        topLeft = Offset(0f, start),
                        size = Size(size.width, extent),
                    )
                }
                ArborBlurEdge.BOTTOM -> {
                    val gradientStart = start - half
                    val extent = (end - gradientStart).coerceAtLeast(1f)
                    val middleStop = ((start - gradientStart) / extent).coerceIn(0f, 1f)
                    val solidStop = ((start + half - gradientStart) / extent).coerceIn(middleStop, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                middleStop to tint.copy(alpha = tint.alpha * 0.5f),
                                solidStop to tint,
                                1f to tint,
                            ),
                            startY = gradientStart,
                            endY = end,
                        ),
                        topLeft = Offset(0f, gradientStart),
                        size = Size(size.width, extent),
                    )
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
    if (debugBoundary) {
        val y = if (edge == ArborBlurEdge.TOP) end else start
        drawLine(
            color = Color.Red,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = debugThickness.coerceAtLeast(1f),
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
internal const val CHAT_TOP_PANEL_HEIGHT_DP = 120f
internal const val STANDARD_TOP_PANEL_HEIGHT_DP = 100f
internal const val CHAT_COMPOSER_MIN_PANEL_HEIGHT_DP = 120f
private const val DEFAULT_TOP_PANEL_HEIGHT_DP = STANDARD_TOP_PANEL_HEIGHT_DP
private const val DEFAULT_BOTTOM_PANEL_HEIGHT_DP = CHAT_COMPOSER_MIN_PANEL_HEIGHT_DP
private const val DEFAULT_EDGE_HIGHLIGHT = 0f

// Three normalized directions spaced 60 degrees apart and rotated away from
// the display axes. These are the device-proven 0.17.8 directions.
internal const val BLUR_AXIS_A_X = 0.9238795f
internal const val BLUR_AXIS_A_Y = 0.3826834f
internal const val BLUR_AXIS_B_X = 0.1305262f
internal const val BLUR_AXIS_B_Y = 0.9914449f
internal const val BLUR_AXIS_C_X = -0.7933533f
internal const val BLUR_AXIS_C_Y = 0.6087614f

private val PANEL_EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uTopBounds;
    uniform float2 uBottomBounds;
    uniform float2 uSize;
    uniform float2 uCorner;
    uniform float2 uMerge;
    uniform float2 uDirection;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    float roundedTopPanelMask(float2 coord, float start, float end, float radius) {
        float vertical = smoothstep(start - 1.0, start + 1.0, coord.y) *
            (1.0 - smoothstep(end - 1.0, end + 1.0, coord.y));
        if (vertical <= 0.0) return 0.0;
        float extent = end - start;
        radius = clamp(radius, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5 || coord.y <= end - radius) return vertical;
        if (coord.x < radius) {
            float d = length(coord - float2(radius, end - radius));
            return vertical * (1.0 - smoothstep(radius - 0.75, radius + 0.75, d));
        }
        if (coord.x > uSize.x - radius) {
            float d = length(coord - float2(uSize.x - radius, end - radius));
            return vertical * (1.0 - smoothstep(radius - 0.75, radius + 0.75, d));
        }
        return vertical;
    }

    float roundedBottomPanelMask(float2 coord, float start, float end, float radius) {
        float vertical = smoothstep(start - 1.0, start + 1.0, coord.y) *
            (1.0 - smoothstep(end - 1.0, end + 1.0, coord.y));
        if (vertical <= 0.0) return 0.0;
        float extent = end - start;
        radius = clamp(radius, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5 || coord.y >= start + radius) return vertical;
        if (coord.x < radius) {
            float d = length(coord - float2(radius, start + radius));
            return vertical * (1.0 - smoothstep(radius - 0.75, radius + 0.75, d));
        }
        if (coord.x > uSize.x - radius) {
            float d = length(coord - float2(uSize.x - radius, start + radius));
            return vertical * (1.0 - smoothstep(radius - 0.75, radius + 0.75, d));
        }
        return vertical;
    }

    float topPanelMix(float2 coord) {
        if (uBlur.x < 0.35) return 0.0;
        if (uMerge.x <= 0.5) {
            return roundedTopPanelMask(coord, uTopBounds.x, uTopBounds.y, uCorner.x);
        }
        float halfSpan = uMerge.x * 0.5;
        float feather = 1.0 - smoother(
            (coord.y - (uTopBounds.y - halfSpan)) / max(uMerge.x, 1.0)
        );
        return saturate(feather * smoothstep(
            uTopBounds.x - 1.0,
            uTopBounds.x + 1.0,
            coord.y
        ));
    }

    float bottomPanelMix(float2 coord) {
        if (uBlur.y < 0.35) return 0.0;
        if (uMerge.y <= 0.5) {
            return roundedBottomPanelMask(coord, uBottomBounds.x, uBottomBounds.y, uCorner.y);
        }
        float halfSpan = uMerge.y * 0.5;
        float feather = smoother(
            (coord.y - (uBottomBounds.x - halfSpan)) / max(uMerge.y, 1.0)
        );
        return saturate(feather * (
            1.0 - smoothstep(
                uBottomBounds.y - 1.0,
                uBottomBounds.y + 1.0,
                coord.y
            )
        ));
    }

    half4 main(float2 coord) {
        float radius = max(
            uBlur.x * topPanelMix(coord),
            uBlur.y * bottomPanelMix(coord)
        );
        if (radius < 0.35) return content.eval(coord);

        // Direct, evenly spaced taps keep the maximum sampling gap bounded.
        // The former bilinear-paired offsets were cheaper, but at a 56 dp
        // radius their wide gaps became visible as repeating bands/lattices.
        float2 sampleStep = uDirection * (radius / 7.5);
        half4 accum = half4(content.eval(coord)) * 0.117695797;
        accum += half4(content.eval(coord + sampleStep * 1.0)) * 0.112988605;
        accum += half4(content.eval(coord - sampleStep * 1.0)) * 0.112988605;
        accum += half4(content.eval(coord + sampleStep * 2.0)) * 0.099966786;
        accum += half4(content.eval(coord - sampleStep * 2.0)) * 0.099966786;
        accum += half4(content.eval(coord + sampleStep * 3.0)) * 0.081512498;
        accum += half4(content.eval(coord - sampleStep * 3.0)) * 0.081512498;
        accum += half4(content.eval(coord + sampleStep * 4.0)) * 0.061254792;
        accum += half4(content.eval(coord - sampleStep * 4.0)) * 0.061254792;
        accum += half4(content.eval(coord + sampleStep * 5.0)) * 0.042423190;
        accum += half4(content.eval(coord - sampleStep * 5.0)) * 0.042423190;
        accum += half4(content.eval(coord + sampleStep * 6.0)) * 0.027077836;
        accum += half4(content.eval(coord - sampleStep * 6.0)) * 0.027077836;
        accum += half4(content.eval(coord + sampleStep * 7.0)) * 0.015928394;
        accum += half4(content.eval(coord - sampleStep * 7.0)) * 0.015928394;
        return accum;
    }
""".trimIndent()
