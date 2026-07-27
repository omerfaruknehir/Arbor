package app.arbor.chat.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Which chrome edge owns a backdrop panel. */
enum class ArborBlurEdge { TOP, BOTTOM }

/**
 * State for the restored Arbor 0.17.8 three-direction AGSL blur.
 *
 * The renderer deliberately applies the blur directly to the scrolling content
 * layer. It does not capture and replay cropped GraphicsLayers, which was the
 * source of the stale frames, black backgrounds, hard jumps and block artifacts
 * in the later half-resolution compositor.
 */
@Stable
class ArborBackdropBlurState internal constructor() {
    internal var topRadiusDp by mutableFloatStateOf(0f)
    internal var bottomRadiusDp by mutableFloatStateOf(0f)
    internal var topFadeDp by mutableFloatStateOf(DEFAULT_TOP_FADE_DP)
    internal var bottomFadeDp by mutableFloatStateOf(DEFAULT_BOTTOM_FADE_DP)
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
    internal var bottomEdgeInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(
        edge: ArborBlurEdge,
        radiusDp: Float,
        fadeDp: Float,
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
        val fade = fadeDp.coerceAtLeast(1f)
        val normalizedSoftness = snapChromeEdgeSoftness(softness)
        val corner = if (normalizedSoftness == 0f) cornerRadiusDp.coerceAtLeast(0f) else 0f
        val merge = if (normalizedSoftness == 0f) 0f else mergeDp.coerceIn(0f, fade * 2f)
        val normalizedSaturation = saturation.coerceIn(0.75f, 1.35f)
        val normalizedContrast = contrast.coerceIn(0.85f, 1.20f)
        val normalizedBrightness = brightness.coerceIn(0.85f, 1.15f)
        val normalizedHighlight = edgeHighlight.coerceIn(0f, 0.12f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
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
                if (bottomFadeDp != fade) bottomFadeDp = fade
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

    internal fun updateBottomEdge(bottomInRootPx: Float) {
        if (!bottomEdgeInRootPx.isFinite() || abs(bottomEdgeInRootPx - bottomInRootPx) >= 0.5f) {
            bottomEdgeInRootPx = bottomInRootPx
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
                bottomEdgeInRootPx = Float.NaN
            }
        }
    }
}

@Composable
fun rememberArborBackdropBlurState(): ArborBackdropBlurState = remember { ArborBackdropBlurState() }

/**
 * Applies the restored 0.17.8 three-pass multi-axis AGSL blur directly to the
 * content. Edge softness remains continuous and is symmetric around the panel
 * boundary; zero softness retains rounded panel geometry.
 */
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

    fun buildShader(
        directionX: Float,
        directionY: Float,
        applyColorAdjustment: Boolean,
    ) = RuntimeShader(EDGE_BLUR_SHADER).apply {
        setFloatUniform("uBlur", topRadiusPx, bottomRadiusPx)
        setFloatUniform("uFade", topFadePx, bottomFadePx)
        setFloatUniform("uSize", contentWidthPx.coerceAtLeast(1f), contentHeightPx.coerceAtLeast(1f))
        setFloatUniform("uBottomEdge", bottomEdgePx)
        setFloatUniform("uSoftness", state.topSoftness, state.bottomSoftness)
        setFloatUniform("uCorner", state.topCornerRadiusDp * density, state.bottomCornerRadiusDp * density)
        setFloatUniform("uMerge", state.topMergeDp * density, state.bottomMergeDp * density)
        setFloatUniform("uDirection", directionX, directionY)
        setFloatUniform("uSaturation", state.topSaturation, state.bottomSaturation)
        setFloatUniform("uContrast", state.topContrast, state.bottomContrast)
        setFloatUniform("uBrightness", state.topBrightness, state.bottomBrightness)
        setFloatUniform("uAdjustColor", if (applyColorAdjustment) 1f else 0f)
    }

    val shaderKey = arrayOf(
        topRadiusPx, bottomRadiusPx, topFadePx, bottomFadePx,
        contentWidthPx, contentHeightPx, bottomEdgePx,
        state.topSoftness, state.bottomSoftness,
        state.topCornerRadiusDp, state.bottomCornerRadiusDp,
        state.topMergeDp, state.bottomMergeDp,
        state.topSaturation, state.bottomSaturation,
        state.topContrast, state.bottomContrast,
        state.topBrightness, state.bottomBrightness,
    )
    val firstShader = remember(*shaderKey) { buildShader(BLUR_AXIS_A_X, BLUR_AXIS_A_Y, false) }
    val secondShader = remember(*shaderKey) { buildShader(BLUR_AXIS_B_X, BLUR_AXIS_B_Y, false) }
    val thirdShader = remember(*shaderKey) { buildShader(BLUR_AXIS_C_X, BLUR_AXIS_C_Y, true) }
    val composeEffect = remember(firstShader, secondShader, thirdShader) {
        ArborRenderProfiler.recordBlurEffectBuild(3)
        val first = RenderEffect.createRuntimeShaderEffect(firstShader, "content")
        val second = RenderEffect.createRuntimeShaderEffect(secondShader, "content")
        val third = RenderEffect.createRuntimeShaderEffect(thirdShader, "content")
        RenderEffect.createChainEffect(third, RenderEffect.createChainEffect(second, first)).asComposeRenderEffect()
    }

    val profiled = measured.drawWithContent {
        val started = if (ArborRenderProfiler.enabled) System.nanoTime() else 0L
        drawContent()
        if (ArborRenderProfiler.enabled) {
            ArborRenderProfiler.recordBlurFrame(
                cpuNanos = System.nanoTime() - started,
                processedPixels = (size.width.toLong() * size.height.toLong() * 3L).coerceAtLeast(0L),
                sourceTraversals = 1,
                layerReplays = 0,
                downsampleLevels = 0,
                upsampleLevels = 0,
                captureUpdates = 0,
            )
        }
    }
    profiled.graphicsLayer { renderEffect = composeEffect }
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

/** Registers one chrome panel and draws the exact-opacity tint above the blurred body. */
fun Modifier.arborBackdropBlur(
    state: ArborBackdropBlurState,
    strength: Float,
    edgeSoftness: Float,
    overlayOpacity: Float = 1f,
    tint: Color,
    edge: ArborBlurEdge = ArborBlurEdge.TOP,
    maxRadius: Dp = DEFAULT_MAX_RADIUS_DP.dp,
    fadeDistance: Dp = if (edge == ArborBlurEdge.TOP) DEFAULT_TOP_FADE_DP.dp else DEFAULT_BOTTOM_FADE_DP.dp,
    overlayDistance: Dp = fadeDistance,
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

    SideEffect {
        state.update(
            edge = edge,
            radiusDp = radiusDp,
            fadeDp = overlayDistance.value,
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

    val anchored = if (edge == ArborBlurEdge.BOTTOM) {
        this.onGloballyPositioned { coordinates -> state.updateBottomEdge(coordinates.boundsInRoot().bottom) }
    } else this

    val density = LocalDensity.current
    val overlayDistancePx = with(density) { overlayDistance.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val mergeDistancePx = with(density) { mergeDp.dp.toPx() }
    val highlightAlpha = edgeHighlight.coerceIn(0f, 0.12f)

    anchored.drawWithContent {
        val extent = overlayDistancePx.coerceIn(1f, size.height.coerceAtLeast(1f))
        val softnessActive = normalizedSoftness > 0f && mergeDistancePx > 0f
        if (!softnessActive) {
            val radius = cornerRadiusPx.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
            val path = Path().apply {
                when (edge) {
                    ArborBlurEdge.TOP -> addRoundRect(
                        RoundRect(
                            0f, 0f, size.width, extent,
                            CornerRadius.Zero, CornerRadius.Zero,
                            CornerRadius(radius, radius), CornerRadius(radius, radius),
                        ),
                    )
                    ArborBlurEdge.BOTTOM -> {
                        val startY = (size.height - extent).coerceAtLeast(0f)
                        addRoundRect(
                            RoundRect(
                                0f, startY, size.width, size.height,
                                CornerRadius(radius, radius), CornerRadius(radius, radius),
                                CornerRadius.Zero, CornerRadius.Zero,
                            ),
                        )
                    }
                }
            }
            clipPath(path) { if (exactTint.alpha > 0f) drawRect(exactTint) }
        } else if (exactTint.alpha > 0f) {
            val half = mergeDistancePx * 0.5f
            when (edge) {
                ArborBlurEdge.TOP -> {
                    val start = (extent - half).coerceAtLeast(0f)
                    val end = extent + half
                    if (start > 0f) drawRect(exactTint, size = Size(size.width, start))
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to exactTint,
                            0.5f to exactTint.copy(alpha = exactTint.alpha * 0.5f),
                            1f to Color.Transparent,
                            startY = start,
                            endY = end,
                        ),
                        topLeft = Offset(0f, start),
                        size = Size(size.width, mergeDistancePx),
                    )
                }
                ArborBlurEdge.BOTTOM -> {
                    val boundary = size.height - extent
                    val start = boundary - half
                    val end = boundary + half
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to exactTint.copy(alpha = exactTint.alpha * 0.5f),
                            1f to exactTint,
                            startY = start,
                            endY = end,
                        ),
                        topLeft = Offset(0f, start),
                        size = Size(size.width, mergeDistancePx),
                    )
                    if (end < size.height) {
                        drawRect(exactTint, topLeft = Offset(0f, end), size = Size(size.width, size.height - end))
                    }
                }
            }
        }

        if (highlightAlpha > 0f) {
            val y = when (edge) {
                ArborBlurEdge.TOP -> extent
                ArborBlurEdge.BOTTOM -> size.height - extent
            }
            drawLine(
                color = Color.White.copy(alpha = highlightAlpha),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        drawContent()
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
private const val DEFAULT_TOP_FADE_DP = 128f
private const val DEFAULT_BOTTOM_FADE_DP = 208f
private const val DEFAULT_GLASS_SATURATION = 1.10f
private const val DEFAULT_GLASS_CONTRAST = 1.025f
private const val DEFAULT_GLASS_BRIGHTNESS = 1.008f
private const val DEFAULT_EDGE_HIGHLIGHT = 0.035f

// Exact directions and nine-tap Gaussian kernel from Arbor 0.17.8.
internal const val BLUR_AXIS_A_X = 0.9238795f
internal const val BLUR_AXIS_A_Y = 0.3826834f
internal const val BLUR_AXIS_B_X = 0.1305262f
internal const val BLUR_AXIS_B_Y = 0.9914449f
internal const val BLUR_AXIS_C_X = -0.7933533f
internal const val BLUR_AXIS_C_Y = 0.6087614f

private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float2 uFade;
    uniform float2 uSize;
    uniform float uBottomEdge;
    uniform float2 uSoftness;
    uniform float2 uCorner;
    uniform float2 uMerge;
    uniform float2 uDirection;
    uniform float2 uSaturation;
    uniform float2 uContrast;
    uniform float2 uBrightness;
    uniform float uAdjustColor;

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
        float topMix;
        if (uSoftness.x <= 0.0 || uMerge.x <= 0.0) {
            topMix = roundedTopPanelMask(coord, topExtent, uCorner.x);
        } else {
            float halfSpan = uMerge.x * 0.5;
            topMix = 1.0 - smoother((coord.y - (topExtent - halfSpan)) / max(uMerge.x, 1.0));
        }

        float bottomEdge = clamp(uBottomEdge, 0.0, uSize.y);
        float bottomExtent = max(uFade.y, 1.0);
        float bottomStart = bottomEdge - bottomExtent;
        float bottomMix;
        if (uSoftness.y <= 0.0 || uMerge.y <= 0.0) {
            bottomMix = roundedBottomPanelMask(coord, bottomStart, bottomEdge, uCorner.y);
        } else {
            float halfSpan = uMerge.y * 0.5;
            bottomMix = smoother((coord.y - (bottomStart - halfSpan)) / max(uMerge.y, 1.0));
            bottomMix *= 1.0 - step(bottomEdge + 0.5, coord.y);
        }

        float topRadius = uBlur.x * saturate(topMix);
        float bottomRadius = uBlur.y * saturate(bottomMix);
        float radius = max(topRadius, bottomRadius);
        if (radius < 0.0001) return content.eval(coord);

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

        if (uAdjustColor > 0.5) {
            float topWeight = step(bottomRadius, topRadius);
            float saturation = mix(uSaturation.y, uSaturation.x, topWeight);
            float contrast = mix(uContrast.y, uContrast.x, topWeight);
            float brightness = mix(uBrightness.y, uBrightness.x, topWeight);
            half luminance = dot(accum.rgb, half3(0.2126, 0.7152, 0.0722));
            accum.rgb = mix(half3(luminance), accum.rgb, half(saturation));
            accum.rgb = (accum.rgb - half3(0.5)) * half(contrast) + half3(0.5);
            accum.rgb *= half(brightness);
        }
        return accum;
    }
""".trimIndent()
