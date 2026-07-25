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
import kotlin.math.roundToInt

enum class ArborBlurEdge { TOP, BOTTOM }

internal data class ArborPanelRange(val startPx: Float, val endPx: Float) {
    val extentPx: Float get() = (endPx - startPx).coerceAtLeast(0f)
}

internal fun resolveTopPanelRange(sourceHeightPx: Float, panelExtentPx: Float): ArborPanelRange =
    ArborPanelRange(0f, panelExtentPx.coerceIn(0f, sourceHeightPx.coerceAtLeast(0f)))

internal fun resolveBottomPanelRange(
    sourceTopInRootPx: Float,
    panelStartInRootPx: Float,
    panelEndInRootPx: Float,
    sourceHeightPx: Float,
    fallbackExtentPx: Float,
): ArborPanelRange {
    val sourceHeight = sourceHeightPx.coerceAtLeast(0f)
    if (panelStartInRootPx.isFinite() && panelEndInRootPx.isFinite()) {
        val start = (panelStartInRootPx - sourceTopInRootPx).coerceIn(0f, sourceHeight)
        val end = (panelEndInRootPx - sourceTopInRootPx).coerceIn(start, sourceHeight)
        return ArborPanelRange(start, end)
    }
    val end = sourceHeight
    return ArborPanelRange((end - fallbackExtentPx).coerceAtLeast(0f), end)
}

/** Shared configuration for the blur and tint applied behind app chrome. */
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
    internal var topTint by mutableStateOf(Color.Transparent)
    internal var bottomTint by mutableStateOf(Color.Transparent)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
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
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
                if (topGradual != gradual) topGradual = gradual
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
                if (topTint != tint) topTint = tint
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
                if (bottomGradual != gradual) bottomGradual = gradual
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
                if (bottomTint != tint) bottomTint = tint
            }
        }
    }

    internal fun updateSource(topInRootPx: Float) {
        if (abs(sourceTopInRootPx - topInRootPx) >= 0.5f) sourceTopInRootPx = topInRootPx
    }

    internal fun updateBottomPanel(
        panelTopInRootPx: Float,
        panelBottomInRootPx: Float,
        requestedExtentPx: Float,
    ) {
        val panelHeight = (panelBottomInRootPx - panelTopInRootPx).coerceAtLeast(0f)
        val actualExtent = requestedExtentPx.coerceIn(0f, panelHeight)
        val nextStart = panelBottomInRootPx - actualExtent
        if (!bottomPanelStartInRootPx.isFinite() || abs(bottomPanelStartInRootPx - nextStart) >= 0.5f) {
            bottomPanelStartInRootPx = nextStart
        }
        if (!bottomPanelEndInRootPx.isFinite() || abs(bottomPanelEndInRootPx - panelBottomInRootPx) >= 0.5f) {
            bottomPanelEndInRootPx = panelBottomInRootPx
        }
    }

    internal fun clear(edge: ArborBlurEdge) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                topRadiusDp = 0f
                topTint = Color.Transparent
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

/** Applies the 0.17.8 glass blur and paints its overlays in the same coordinates. */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    val density = LocalDensity.current.density
    val topRadiusPx = state.topRadiusDp * density
    val bottomRadiusPx = state.bottomRadiusDp * density
    val radiusActive = topRadiusPx >= MIN_VISIBLE_RADIUS_PX || bottomRadiusPx >= MIN_VISIBLE_RADIUS_PX

    var contentWidthPx by remember { mutableFloatStateOf(0f) }
    var contentHeightPx by remember { mutableFloatStateOf(0f) }
    val measured = this.onGloballyPositioned { coordinates ->
        val nextWidth = coordinates.size.width.toFloat().coerceAtLeast(1f)
        val nextHeight = coordinates.size.height.toFloat().coerceAtLeast(1f)
        if (contentWidthPx != nextWidth) contentWidthPx = nextWidth
        if (contentHeightPx != nextHeight) contentHeightPx = nextHeight
        state.updateSource(coordinates.boundsInRoot().top)
    }
    if (contentWidthPx <= 0f || contentHeightPx <= 0f) return@composed measured

    val topRange = resolveTopPanelRange(contentHeightPx, state.topFadeDp * density)
    val bottomRange = resolveBottomPanelRange(
        sourceTopInRootPx = state.sourceTopInRootPx,
        panelStartInRootPx = state.bottomPanelStartInRootPx,
        panelEndInRootPx = state.bottomPanelEndInRootPx,
        sourceHeightPx = contentHeightPx,
        fallbackExtentPx = state.bottomFadeDp * density,
    )

    val overlayModifier = measured.drawWithContent {
        drawContent()
        drawArborPanelOverlay(
            range = topRange,
            edge = ArborBlurEdge.TOP,
            tint = state.topTint,
            gradual = state.topGradual,
            cornerRadiusPx = state.topCornerRadiusDp * density,
            mergeDistancePx = state.topMergeDp * density,
        )
        drawArborPanelOverlay(
            range = bottomRange,
            edge = ArborBlurEdge.BOTTOM,
            tint = state.bottomTint,
            gradual = state.bottomGradual,
            cornerRadiusPx = state.bottomCornerRadiusDp * density,
            mergeDistancePx = state.bottomMergeDp * density,
        )
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !radiusActive) {
        return@composed overlayModifier
    }

    fun buildShader(directionX: Float, directionY: Float) = RuntimeShader(EDGE_BLUR_SHADER).apply {
        setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        setFloatUniform("uSize", contentWidthPx.coerceAtLeast(1f), contentHeightPx.coerceAtLeast(1f))
        setFloatUniform("uPanelStart", topRange.startPx, bottomRange.startPx)
        setFloatUniform("uPanelEnd", topRange.endPx, bottomRange.endPx)
        setFloatUniform("uGradual", if (state.topGradual) 1f else 0f, if (state.bottomGradual) 1f else 0f)
        setFloatUniform("uCorner", state.topCornerRadiusDp * density, state.bottomCornerRadiusDp * density)
        setFloatUniform("uMerge", state.topMergeDp * density, state.bottomMergeDp * density)
        setFloatUniform("uDirection", directionX, directionY)
    }

    val firstShader = remember(
        topRadiusPx, bottomRadiusPx, contentWidthPx, contentHeightPx,
        topRange, bottomRange, state.topGradual, state.bottomGradual,
        state.topCornerRadiusDp, state.bottomCornerRadiusDp,
        state.topMergeDp, state.bottomMergeDp,
    ) { buildShader(BLUR_AXIS_A_X, BLUR_AXIS_A_Y) }
    val secondShader = remember(
        topRadiusPx, bottomRadiusPx, contentWidthPx, contentHeightPx,
        topRange, bottomRange, state.topGradual, state.bottomGradual,
        state.topCornerRadiusDp, state.bottomCornerRadiusDp,
        state.topMergeDp, state.bottomMergeDp,
    ) { buildShader(BLUR_AXIS_B_X, BLUR_AXIS_B_Y) }
    val thirdShader = remember(
        topRadiusPx, bottomRadiusPx, contentWidthPx, contentHeightPx,
        topRange, bottomRange, state.topGradual, state.bottomGradual,
        state.topCornerRadiusDp, state.bottomCornerRadiusDp,
        state.topMergeDp, state.bottomMergeDp,
    ) { buildShader(BLUR_AXIS_C_X, BLUR_AXIS_C_Y) }
    val composeEffect = remember(firstShader, secondShader, thirdShader) {
        val first = RenderEffect.createRuntimeShaderEffect(firstShader, "content")
        val second = RenderEffect.createRuntimeShaderEffect(secondShader, "content")
        val third = RenderEffect.createRuntimeShaderEffect(thirdShader, "content")
        RenderEffect.createChainEffect(third, RenderEffect.createChainEffect(second, first)).asComposeRenderEffect()
    }

    // drawWithContent is deliberately outside the graphics layer: drawContent()
    // receives the blur, then the tint is painted unblurred using the same ranges.
    overlayModifier.graphicsLayer { renderEffect = composeEffect }
}

private fun DrawScope.drawArborPanelOverlay(
    range: ArborPanelRange,
    edge: ArborBlurEdge,
    tint: Color,
    gradual: Boolean,
    cornerRadiusPx: Float,
    mergeDistancePx: Float,
) {
    if (tint.alpha <= 0f || range.extentPx <= 0f) return
    val panelColor = tint.copy(alpha = (tint.alpha * PANEL_OPACITY_BOOST).coerceIn(0f, 1f))
    val start = range.startPx.coerceIn(0f, size.height)
    val end = range.endPx.coerceIn(start, size.height)
    val extent = end - start
    if (extent <= 0f) return
    val radius = cornerRadiusPx.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
    val merge = if (gradual) mergeDistancePx.coerceIn(1f, extent) else 0f

    val panelPath = Path().apply {
        when (edge) {
            ArborBlurEdge.TOP -> addRoundRect(
                RoundRect(
                    left = 0f,
                    top = start,
                    right = size.width,
                    bottom = end,
                    topLeftCornerRadius = CornerRadius.Zero,
                    topRightCornerRadius = CornerRadius.Zero,
                    bottomRightCornerRadius = CornerRadius(radius, radius),
                    bottomLeftCornerRadius = CornerRadius(radius, radius),
                ),
            )
            ArborBlurEdge.BOTTOM -> addRoundRect(
                RoundRect(
                    left = 0f,
                    top = start,
                    right = size.width,
                    bottom = end,
                    topLeftCornerRadius = CornerRadius(radius, radius),
                    topRightCornerRadius = CornerRadius(radius, radius),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
    }

    clipPath(panelPath) {
        when (edge) {
            ArborBlurEdge.TOP -> {
                val bodyEnd = (end - merge).coerceAtLeast(start)
                if (bodyEnd > start) {
                    drawRect(color = panelColor, topLeft = Offset(0f, start), size = Size(size.width, bodyEnd - start))
                }
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
                            endY = end,
                        ),
                        topLeft = Offset(0f, bodyEnd),
                        size = Size(size.width, end - bodyEnd),
                    )
                } else {
                    drawRect(color = panelColor, topLeft = Offset(0f, start), size = Size(size.width, extent))
                }
            }
            ArborBlurEdge.BOTTOM -> {
                val bodyStart = (start + merge).coerceAtMost(end)
                if (merge > 0f) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                0.22f to panelColor.copy(alpha = panelColor.alpha * 0.54f),
                                0.48f to panelColor.copy(alpha = panelColor.alpha * 0.92f),
                                1f to panelColor,
                            ),
                            startY = start,
                            endY = bodyStart,
                        ),
                        topLeft = Offset(0f, start),
                        size = Size(size.width, bodyStart - start),
                    )
                }
                if (bodyStart < end) {
                    drawRect(color = panelColor, topLeft = Offset(0f, bodyStart), size = Size(size.width, end - bodyStart))
                }
            }
        }
    }
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

/** Registers a chrome panel. Blur and tint are rendered together by the source. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    enabled: Boolean,
    gradual: Boolean = true,
    progress: Float,
    strength: Float,
    overlayOpacity: Float = 1f,
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
            fadeDp = overlayDistance.value,
            gradual = gradual,
            cornerRadiusDp = cornerRadius.value,
            mergeDp = mergeDistance.value,
            tint = applyOverlayOpacity(tint, overlayOpacity),
        )
    }
    DisposableEffect(state, edge) { onDispose { state.clear(edge) } }

    if (edge != ArborBlurEdge.BOTTOM) return@composed this
    val overlayExtentPx = with(LocalDensity.current) { overlayDistance.toPx() }
    this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        state.updateBottomPanel(bounds.top, bounds.bottom, overlayExtentPx)
    }
}

internal fun applyOverlayOpacity(tint: Color, opacity: Float): Color =
    tint.copy(alpha = tint.alpha * opacity.coerceIn(0f, 1f))

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

internal const val BLUR_SAMPLES_PER_PASS = 21

internal const val BLUR_AXIS_A_X = 0.9238795f
internal const val BLUR_AXIS_A_Y = 0.3826834f
internal const val BLUR_AXIS_B_X = 0.1305262f
internal const val BLUR_AXIS_B_Y = 0.9914449f
internal const val BLUR_AXIS_C_X = -0.7933533f
internal const val BLUR_AXIS_C_Y = 0.6087614f

/** Twenty-one real Gaussian samples per pass, preserving the 0.17.8 three-axis glass character. */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uSize;
    uniform float2 uPanelStart;
    uniform float2 uPanelEnd;
    uniform float2 uGradual;
    uniform float2 uCorner;
    uniform float2 uMerge;
    uniform float2 uDirection;

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
        float topStart = uPanelStart.x;
        float topEnd = uPanelEnd.x;
        float topMask = roundedTopPanelMask(coord, topStart, topEnd, uCorner.x);
        float topMerge = max(uMerge.x, 1.0);
        float topFeather = smoother((topEnd - coord.y) / topMerge);
        float topMix = topMask * mix(1.0, topFeather, uGradual.x);

        float bottomStart = uPanelStart.y;
        float bottomEnd = uPanelEnd.y;
        float bottomMask = roundedBottomPanelMask(coord, bottomStart, bottomEnd, uCorner.y);
        float bottomMerge = max(uMerge.y, 1.0);
        float bottomFeather = smoother((coord.y - bottomStart) / bottomMerge);
        float bottomMix = bottomMask * mix(1.0, bottomFeather, uGradual.y);

        float radius = max(uBlur.x * topMix, uBlur.y * bottomMix);
        if (radius < 0.35) return content.eval(coord);

        float2 sampleStep = uDirection * (radius / 10.5);
        half4 accum = half4(content.eval(coord)) * 0.090405884;
        accum += half4(content.eval(coord + sampleStep * 1.0)) * 0.088200974;
        accum += half4(content.eval(coord - sampleStep * 1.0)) * 0.088200974;
        accum += half4(content.eval(coord + sampleStep * 2.0)) * 0.081903680;
        accum += half4(content.eval(coord - sampleStep * 2.0)) * 0.081903680;
        accum += half4(content.eval(coord + sampleStep * 3.0)) * 0.072391373;
        accum += half4(content.eval(coord - sampleStep * 3.0)) * 0.072391373;
        accum += half4(content.eval(coord + sampleStep * 4.0)) * 0.060900880;
        accum += half4(content.eval(coord - sampleStep * 4.0)) * 0.060900880;
        accum += half4(content.eval(coord + sampleStep * 5.0)) * 0.048765613;
        accum += half4(content.eval(coord - sampleStep * 5.0)) * 0.048765613;
        accum += half4(content.eval(coord + sampleStep * 6.0)) * 0.037166970;
        accum += half4(content.eval(coord - sampleStep * 6.0)) * 0.037166970;
        accum += half4(content.eval(coord + sampleStep * 7.0)) * 0.026962117;
        accum += half4(content.eval(coord - sampleStep * 7.0)) * 0.026962117;
        accum += half4(content.eval(coord + sampleStep * 8.0)) * 0.018616764;
        accum += half4(content.eval(coord - sampleStep * 8.0)) * 0.018616764;
        accum += half4(content.eval(coord + sampleStep * 9.0)) * 0.012235106;
        accum += half4(content.eval(coord - sampleStep * 9.0)) * 0.012235106;
        accum += half4(content.eval(coord + sampleStep * 10.0)) * 0.007653580;
        accum += half4(content.eval(coord - sampleStep * 10.0)) * 0.007653580;
        return accum;
    }
""".trimIndent()
