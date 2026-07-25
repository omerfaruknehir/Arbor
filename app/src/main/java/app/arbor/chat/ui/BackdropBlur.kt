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
    internal var bottomPanelStartInRootPx by mutableFloatStateOf(Float.NaN)
    internal var bottomPanelEndInRootPx by mutableFloatStateOf(Float.NaN)

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
            ArborBlurEdge.TOP -> topRadiusDp = 0f
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusDp = 0f
                bottomEdgeInRootPx = Float.NaN
                bottomPanelStartInRootPx = Float.NaN
                bottomPanelEndInRootPx = Float.NaN
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
    val topRange = alignedTopBlurRange(topFadePx, contentHeightPx)
    val bottomRange = alignedBottomBlurRange(
        sourceTopInRootPx = state.sourceTopInRootPx,
        panelStartInRootPx = state.bottomPanelStartInRootPx,
        panelEndInRootPx = state.bottomPanelEndInRootPx,
        fallbackEndInRootPx = state.bottomEdgeInRootPx,
        fallbackExtentPx = bottomFadePx,
        contentHeightPx = contentHeightPx,
    )

    val shader = remember(
        topRadiusPx,
        bottomRadiusPx,
        contentWidthPx,
        contentHeightPx,
        topRange,
        bottomRange,
        state.topGradual,
        state.bottomGradual,
        state.topCornerRadiusDp,
        state.bottomCornerRadiusDp,
        state.topMergeDp,
        state.bottomMergeDp,
    ) {
        RuntimeShader(EDGE_BLUR_SHADER).apply {
            setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
            setFloatUniform("uSize", contentWidthPx.coerceAtLeast(1f), contentHeightPx.coerceAtLeast(1f))
            setFloatUniform("uTopRange", topRange.first, topRange.second)
            setFloatUniform("uBottomRange", bottomRange.first, bottomRange.second)
            setFloatUniform("uGradual", if (state.topGradual) 1f else 0f, if (state.bottomGradual) 1f else 0f)
            setFloatUniform("uCorner", state.topCornerRadiusDp * density, state.bottomCornerRadiusDp * density)
            setFloatUniform("uMerge", state.topMergeDp * density, state.bottomMergeDp * density)
        }
    }
    val composeEffect = remember(shader) {
        RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
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

    val density = LocalDensity.current
    val overlayDistancePx = with(density) { overlayDistance.toPx() }
    val fadeDistancePx = with(density) { fadeDistance.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val mergeDistancePx = with(density) { mergeDistance.toPx() }
    var panelTopInRootPx by remember { mutableFloatStateOf(0f) }

    val aligned = this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        panelTopInRootPx = bounds.top
        if (edge == ArborBlurEdge.BOTTOM) {
            val actualExtent = overlayDistancePx.coerceIn(1f, coordinates.size.height.toFloat().coerceAtLeast(1f))
            state.updateBottomPanel(bounds.bottom - actualExtent, bounds.bottom)
        }
    }

    aligned.drawWithContent {
        val panelColor = tint.copy(alpha = (tint.alpha * PANEL_OPACITY_BOOST).coerceIn(0f, 1f))
        val range = when (edge) {
            ArborBlurEdge.TOP -> alignedTopOverlayRange(
                sourceTopInRootPx = state.sourceTopInRootPx,
                panelTopInRootPx = panelTopInRootPx,
                fadeDistancePx = fadeDistancePx,
                panelHeightPx = size.height,
            )
            ArborBlurEdge.BOTTOM -> {
                val extent = overlayDistancePx.coerceIn(1f, size.height.coerceAtLeast(1f))
                (size.height - extent) to size.height
            }
        }
        val panelStart = range.first
        val panelEnd = range.second
        val extent = (panelEnd - panelStart).coerceAtLeast(1f)
        val radius = cornerRadiusPx.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
        val merge = if (gradual) mergeDistancePx.coerceIn(1f, extent) else 0f

        val panelPath = Path().apply {
            when (edge) {
                ArborBlurEdge.TOP -> addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = panelStart,
                        right = size.width,
                        bottom = panelEnd,
                        topLeftCornerRadius = CornerRadius.Zero,
                        topRightCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius(radius, radius),
                        bottomLeftCornerRadius = CornerRadius(radius, radius),
                    ),
                )
                ArborBlurEdge.BOTTOM -> addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = panelStart,
                        right = size.width,
                        bottom = panelEnd,
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
                    val bodyEnd = (panelEnd - merge).coerceAtLeast(panelStart)
                    if (bodyEnd > panelStart) {
                        drawRect(color = panelColor, topLeft = Offset(0f, panelStart), size = Size(size.width, bodyEnd - panelStart))
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
                                endY = panelEnd,
                            ),
                            topLeft = Offset(0f, bodyEnd),
                            size = Size(size.width, panelEnd - bodyEnd),
                        )
                    } else {
                        drawRect(color = panelColor, topLeft = Offset(0f, panelStart), size = Size(size.width, extent))
                    }
                }
                ArborBlurEdge.BOTTOM -> {
                    val bodyStart = (panelStart + merge).coerceAtMost(panelEnd)
                    if (merge > 0f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.22f to panelColor.copy(alpha = panelColor.alpha * 0.54f),
                                    0.48f to panelColor.copy(alpha = panelColor.alpha * 0.92f),
                                    1f to panelColor,
                                ),
                                startY = panelStart,
                                endY = bodyStart,
                            ),
                            topLeft = Offset(0f, panelStart),
                            size = Size(size.width, bodyStart - panelStart),
                        )
                    }
                    if (bodyStart < panelEnd) {
                        drawRect(color = panelColor, topLeft = Offset(0f, bodyStart), size = Size(size.width, panelEnd - bodyStart))
                    }
                }
            }
        }
        drawContent()
    }
}


internal fun alignedTopBlurRange(fadeDistancePx: Float, contentHeightPx: Float): Pair<Float, Float> =
    0f to fadeDistancePx.coerceIn(1f, contentHeightPx.coerceAtLeast(1f))

internal fun alignedTopOverlayRange(
    sourceTopInRootPx: Float,
    panelTopInRootPx: Float,
    fadeDistancePx: Float,
    panelHeightPx: Float,
): Pair<Float, Float> {
    val height = panelHeightPx.coerceAtLeast(1f)
    val start = (sourceTopInRootPx - panelTopInRootPx).coerceIn(0f, height)
    val end = (start + fadeDistancePx.coerceAtLeast(1f)).coerceIn(start, height)
    return start to end
}

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

internal const val GLASS_KERNEL_SAMPLE_COUNT = 49
internal const val GLASS_KERNEL_WEIGHT_SUM = 1f

/**
 * One-pass isotropic Gaussian disk kernel. Samples follow a low-discrepancy
 * radial distribution rather than rows, columns, or a handful of directions,
 * so the blur stays glassy without visible grids or streaks.
 */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uSize;
    uniform float2 uTopRange;
    uniform float2 uBottomRange;
    uniform float2 uGradual;
    uniform float2 uCorner;
    uniform float2 uMerge;

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

    half4 sampleContent(float2 coord) {
        return half4(content.eval(clamp(coord, float2(0.0), max(uSize - float2(1.0), float2(0.0)))));
    }

    half4 main(float2 coord) {
        half4 original = sampleContent(coord);

        float topMask = roundedTopPanelMask(coord, uTopRange.x, uTopRange.y, uCorner.x);
        float topMerge = max(uMerge.x, 1.0);
        float topFeather = smoother((uTopRange.y - coord.y) / topMerge);
        float topMix = topMask * mix(1.0, topFeather, uGradual.x);

        float bottomMask = roundedBottomPanelMask(coord, uBottomRange.x, uBottomRange.y, uCorner.y);
        float bottomMerge = max(uMerge.y, 1.0);
        float bottomFeather = smoother((coord.y - uBottomRange.x) / bottomMerge);
        float bottomMix = bottomMask * mix(1.0, bottomFeather, uGradual.y);

        float panelMix = max(topMix, bottomMix);
        if (panelMix < 0.001) return original;

        // Keep one stable kernel radius through the feather. The panel edge is
        // blended by panelMix rather than by changing tap spacing, preventing
        // bands and moving sample patterns at the merge line.
        float radius = max(uBlur.x * step(0.001, topMask), uBlur.y * step(0.001, bottomMask));
        if (radius < 0.35) return original;

        half4 accum = sampleContent(coord) * 0.048563360;
        accum += sampleContent(coord + float2(0.0951553, 0.0369072) * radius) * 0.047477871;
        accum += sampleContent(coord + float2(-0.1647094, 0.0641936) * radius) * 0.045379139;
        accum += sampleContent(coord + float2(0.1008130, -0.2047439) * radius) * 0.043373180;
        accum += sampleContent(coord + float2(0.0756858, 0.2592071) * radius) * 0.041455893;
        accum += sampleContent(coord + float2(-0.2618163, -0.1587521) * radius) * 0.039623359;
        accum += sampleContent(coord + float2(0.3319838, -0.0661064) * radius) * 0.037871831;
        accum += sampleContent(coord + float2(-0.2175755, 0.2967787) * radius) * 0.036197728;
        accum += sampleContent(coord + float2(-0.0430074, -0.3929381) * radius) * 0.034597628;
        accum += sampleContent(coord + float2(0.3163278, 0.2775249) * radius) * 0.033068260;
        accum += sampleContent(coord + float2(-0.4447756, 0.0095550) * radius) * 0.031606496;
        accum += sampleContent(coord + float2(0.3380076, -0.3232659) * radius) * 0.030209349;
        accum += sampleContent(coord + float2(-0.0323101, 0.4884049) * radius) * 0.028873962;
        accum += sampleContent(coord + float2(-0.3191192, -0.3982206) * radius) * 0.027597605;
        accum += sampleContent(coord + float2(0.5240867, 0.0811364) * radius) * 0.026377669;
        accum += sampleContent(coord + float2(-0.4573028, 0.3048893) * radius) * 0.025211659;
        accum += sampleContent(coord + float2(0.1357016, -0.5518168) * radius) * 0.024097192;
        accum += sampleContent(coord + float2(0.2813436, 0.5143888) * radius) * 0.023031989;
        accum += sampleContent(coord + float2(-0.5714870, -0.1948999) * radius) * 0.022013874;
        accum += sampleContent(coord + float2(0.5686316, -0.2491481) * radius) * 0.021040763;
        accum += sampleContent(coord + float2(-0.2576885, 0.5829637) * radius) * 0.020110668;
        accum += sampleContent(coord + float2(-0.2089345, -0.6192170) * radius) * 0.019221687;
        accum += sampleContent(coord + float2(0.5861301, 0.3230606) * radius) * 0.018372004;
        accum += sampleContent(coord + float2(-0.6653725, 0.1613363) * radius) * 0.017559880;
        accum += sampleContent(coord + float2(0.3900326, -0.5809112) * radius) * 0.016783655;
        accum += sampleContent(coord + float2(0.1070086, 0.7063751) * radius) * 0.016041743;
        accum += sampleContent(coord + float2(-0.5672889, -0.4576388) * radius) * 0.015332627;
        accum += sampleContent(coord + float2(0.7415580, -0.0466379) * radius) * 0.014654857;
        accum += sampleContent(coord + float2(-0.5249309, 0.5453111) * radius) * 0.014007048;
        accum += sampleContent(coord + float2(0.0190526, -0.7703162) * radius) * 0.013387874;
        accum += sampleContent(coord + float2(0.5150981, 0.5909800) * radius) * 0.012796071;
        accum += sampleContent(coord + float2(-0.7921122, -0.0893026) * radius) * 0.012230428;
        accum += sampleContent(coord + float2(0.6548807, -0.4768451) * radius) * 0.011689789;
        accum += sampleContent(coord + float2(-0.1633166, 0.8064806) * radius) * 0.011173048;
        accum += sampleContent(coord + float2(-0.4308242, -0.7157564) * radius) * 0.010679150;
        accum += sampleContent(coord + float2(0.8130326, 0.2402666) * radius) * 0.010207084;
        accum += sampleContent(coord + float2(-0.7727644, 0.3773837) * radius) * 0.009755886;
        accum += sampleContent(coord + float2(0.3192978, -0.8114589) * radius) * 0.009324633;
        accum += sampleContent(coord + float2(0.3169469, 0.8251028) * radius) * 0.008912442;
        accum += sampleContent(coord + float2(-0.8015338, -0.3995334) * radius) * 0.008518473;
        accum += sampleContent(coord + float2(0.8720159, -0.2500097) * radius) * 0.008141919;
        accum += sampleContent(coord + float2(-0.4800823, 0.7831162) * radius) * 0.007782010;
        accum += sampleContent(coord + float2(-0.1771369, -0.9128011) * radius) * 0.007438011;
        accum += sampleContent(coord + float2(0.7561524, 0.5600449) * radius) * 0.007109218;
        accum += sampleContent(coord + float2(-0.9468143, 0.0989580) * radius) * 0.006794959;
        accum += sampleContent(coord + float2(0.6385214, -0.7206759) * radius) * 0.006494591;
        accum += sampleContent(coord + float2(0.0161624, 0.9734760) * radius) * 0.006207502;
        accum += sampleContent(coord + float2(-0.6768082, -0.7146192) * radius) * 0.005933103;
        accum += sampleContent(coord + float2(0.9922762, 0.0705067) * radius) * 0.005670833;

        // A very small saturation/luminance lift gives the blur a glass surface
        // character; the panel tint is composited separately with the exact same
        // geometry.
        half luminance = dot(accum.rgb, half3(0.2126, 0.7152, 0.0722));
        half3 glassRgb = mix(half3(luminance), accum.rgb, half(1.045));
        glassRgb = clamp(glassRgb * half(1.012) + half3(0.003), half3(0.0), half3(1.0));
        half4 glass = half4(glassRgb, original.a);
        return mix(original, glass, half(panelMix));
    }
""".trimIndent()
