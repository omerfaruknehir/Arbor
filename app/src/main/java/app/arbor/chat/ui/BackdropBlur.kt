package app.arbor.chat.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.ceil
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
    internal var topCornerRadiusDp by mutableFloatStateOf(DEFAULT_PANEL_CORNER_RADIUS_DP)
    internal var bottomCornerRadiusDp by mutableFloatStateOf(DEFAULT_PANEL_CORNER_RADIUS_DP)
    internal var topMergeDp by mutableFloatStateOf(DEFAULT_MERGE_DISTANCE_DP)
    internal var bottomMergeDp by mutableFloatStateOf(DEFAULT_MERGE_DISTANCE_DP)
    internal var topSoftness by mutableFloatStateOf(0.5f)
    internal var bottomSoftness by mutableFloatStateOf(0.5f)
    internal var topTint by mutableStateOf(Color.Transparent)
    internal var bottomTint by mutableStateOf(Color.Transparent)
    internal var sourceTopInRootPx by mutableFloatStateOf(0f)
    internal var bottomPanelStartInRootPx by mutableFloatStateOf(Float.NaN)
    internal var bottomPanelEndInRootPx by mutableFloatStateOf(Float.NaN)

    internal fun update(
        edge: ArborBlurEdge,
        radiusDp: Float,
        fadeDp: Float,
        cornerRadiusDp: Float,
        mergeDp: Float,
        softness: Float,
        tint: Color,
    ) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val fade = fadeDp.coerceAtLeast(1f)
        val corner = cornerRadiusDp.coerceAtLeast(0f)
        val merge = mergeDp.coerceIn(0f, fade)
        val normalizedSoftness = softness.coerceIn(0f, 1f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
                if (topSoftness != normalizedSoftness) topSoftness = normalizedSoftness
                if (topTint != tint) topTint = tint
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
                if (bottomSoftness != normalizedSoftness) bottomSoftness = normalizedSoftness
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
                topSoftness = 0f
                topTint = Color.Transparent
            }
            ArborBlurEdge.BOTTOM -> {
                bottomRadiusDp = 0f
                bottomSoftness = 0f
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
 * Preserves the 0.17.18 adaptive glass shader while filtering only the pixels
 * which can contribute to the visible top and bottom panels.
 *
 * Each edge uses the original three directions and the original adaptive
 * 1..73-sample shader. The passes are recorded into progressively smaller,
 * full-resolution layers:
 *
 *  1. pass A: panel + A/B/C vertical support
 *  2. pass B: panel + B/C support
 *  3. pass C: panel + C support
 *
 * This is mathematically the same dependency region as the old full-screen
 * chain. It avoids filtering unrelated chat pixels and records Compose content
 * only once per invalidated frame. Quality never changes because of scrolling,
 * navigation, velocity, FPS, battery state, or thermal state.
 */
fun Modifier.arborBackdropSource(state: ArborBackdropBlurState): Modifier = composed {
    val density = LocalDensity.current.density
    val topRadiusPx = state.topRadiusDp * density
    val bottomRadiusPx = state.bottomRadiusDp * density

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
    val topCaptures = resolveAdaptiveBlurPassCaptures(topRange, contentHeightPx, topRadiusPx)
    val bottomCaptures = resolveAdaptiveBlurPassCaptures(bottomRange, contentHeightPx, bottomRadiusPx)

    val sourceLayer = rememberGraphicsLayer()
    val topPassA = rememberGraphicsLayer()
    val topPassB = rememberGraphicsLayer()
    val topPassC = rememberGraphicsLayer()
    val bottomPassA = rememberGraphicsLayer()
    val bottomPassB = rememberGraphicsLayer()
    val bottomPassC = rememberGraphicsLayer()

    val topEffects = remember(
        topCaptures,
        topRange,
        topRadiusPx,
        contentWidthPx,
        state.topCornerRadiusDp,
        state.topMergeDp,
        state.topSoftness,
        density,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) topCaptures?.let {
            buildAdaptiveStripPassEffects(
                captures = it,
                panelRange = topRange,
                radiusPx = topRadiusPx,
                sourceWidthPx = contentWidthPx,
                cornerRadiusPx = state.topCornerRadiusDp * density,
                mergeDistancePx = state.topMergeDp * density,
                softness = state.topSoftness,
                maxBlurRadiusPx = DEFAULT_MAX_RADIUS_DP * density,
                minimumMergePx = MINIMUM_FEATHER_DISTANCE_DP * density,
                edge = ArborBlurEdge.TOP,
            )
        } else null
    }
    val bottomEffects = remember(
        bottomCaptures,
        bottomRange,
        bottomRadiusPx,
        contentWidthPx,
        state.bottomCornerRadiusDp,
        state.bottomMergeDp,
        state.bottomSoftness,
        density,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) bottomCaptures?.let {
            buildAdaptiveStripPassEffects(
                captures = it,
                panelRange = bottomRange,
                radiusPx = bottomRadiusPx,
                sourceWidthPx = contentWidthPx,
                cornerRadiusPx = state.bottomCornerRadiusDp * density,
                mergeDistancePx = state.bottomMergeDp * density,
                softness = state.bottomSoftness,
                maxBlurRadiusPx = DEFAULT_MAX_RADIUS_DP * density,
                minimumMergePx = MINIMUM_FEATHER_DISTANCE_DP * density,
                edge = ArborBlurEdge.BOTTOM,
            )
        } else null
    }
    SideEffect {
        topPassA.renderEffect = topEffects?.passA
        topPassB.renderEffect = topEffects?.passB
        topPassC.renderEffect = topEffects?.passC
        bottomPassA.renderEffect = bottomEffects?.passA
        bottomPassB.renderEffect = bottomEffects?.passB
        bottomPassC.renderEffect = bottomEffects?.passC
    }

    measured.drawWithContent {
        val topActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            topCaptures != null && topEffects != null
        val bottomActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            bottomCaptures != null && bottomEffects != null
        val blurActive = topActive || bottomActive
        val profilerActive = ArborRenderProfiler.enabled
        var blurCpuNanos = 0L
        var filteredPixels = 0L
        var layerReplays = 0

        if (blurActive) {
            val sourceSize = IntSize(
                width = size.width.roundToInt().coerceAtLeast(1),
                height = size.height.roundToInt().coerceAtLeast(1),
            )
            val sourceStarted = if (profilerActive) System.nanoTime() else 0L
            sourceLayer.record(size = sourceSize) {
                this@drawWithContent.drawContent()
            }
            if (profilerActive) blurCpuNanos += System.nanoTime() - sourceStarted

            if (topActive) {
                topCaptures?.let { captures ->
                    val started = if (profilerActive) System.nanoTime() else 0L
                    recordAdaptivePassChain(
                        sourceLayer = sourceLayer,
                        passA = topPassA,
                        passB = topPassB,
                        passC = topPassC,
                        captures = captures,
                        sourceWidthPx = contentWidthPx,
                    )
                    if (profilerActive) {
                        blurCpuNanos += System.nanoTime() - started
                        filteredPixels += captures.filteredPixels(contentWidthPx)
                        layerReplays += 3
                    }
                }
            }
            if (bottomActive) {
                bottomCaptures?.let { captures ->
                    val started = if (profilerActive) System.nanoTime() else 0L
                    recordAdaptivePassChain(
                        sourceLayer = sourceLayer,
                        passA = bottomPassA,
                        passB = bottomPassB,
                        passC = bottomPassC,
                        captures = captures,
                        sourceWidthPx = contentWidthPx,
                    )
                    if (profilerActive) {
                        blurCpuNanos += System.nanoTime() - started
                        filteredPixels += captures.filteredPixels(contentWidthPx)
                        layerReplays += 3
                    }
                }
            }

            drawLayer(sourceLayer)
            if (profilerActive) layerReplays++
        } else {
            drawContent()
        }

        if (topActive) {
            topCaptures?.let { captures ->
                val started = if (profilerActive) System.nanoTime() else 0L
                clipPath(arborPanelPath(topRange, ArborBlurEdge.TOP, state.topCornerRadiusDp * density)) {
                    translate(0f, captures.passC.sourceStartPx) {
                        drawLayer(topPassC)
                    }
                }
                if (profilerActive) {
                    blurCpuNanos += System.nanoTime() - started
                    layerReplays++
                }
            }
        }
        if (bottomActive) {
            bottomCaptures?.let { captures ->
                val started = if (profilerActive) System.nanoTime() else 0L
                clipPath(arborPanelPath(bottomRange, ArborBlurEdge.BOTTOM, state.bottomCornerRadiusDp * density)) {
                    translate(0f, captures.passC.sourceStartPx) {
                        drawLayer(bottomPassC)
                    }
                }
                if (profilerActive) {
                    blurCpuNanos += System.nanoTime() - started
                    layerReplays++
                }
            }
        }

        drawArborPanelOverlay(
            range = topRange,
            edge = ArborBlurEdge.TOP,
            tint = state.topTint,
            cornerRadiusPx = state.topCornerRadiusDp * density,
            mergeDistancePx = state.topMergeDp * density,
            softness = state.topSoftness,
            minimumFeatherPx = MINIMUM_FEATHER_DISTANCE_DP * density,
        )
        drawArborPanelOverlay(
            range = bottomRange,
            edge = ArborBlurEdge.BOTTOM,
            tint = state.bottomTint,
            cornerRadiusPx = state.bottomCornerRadiusDp * density,
            mergeDistancePx = state.bottomMergeDp * density,
            softness = state.bottomSoftness,
            minimumFeatherPx = MINIMUM_FEATHER_DISTANCE_DP * density,
        )

        if (profilerActive && blurActive) {
            ArborRenderProfiler.recordBlurFrame(
                cpuNanos = blurCpuNanos,
                filteredPixels = filteredPixels,
                sourceDraws = 1,
                layerReplays = layerReplays,
            )
        }
    }
}

private fun DrawScope.recordAdaptivePassChain(
    sourceLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    passA: androidx.compose.ui.graphics.layer.GraphicsLayer,
    passB: androidx.compose.ui.graphics.layer.GraphicsLayer,
    passC: androidx.compose.ui.graphics.layer.GraphicsLayer,
    captures: AdaptiveBlurPassCaptures,
    sourceWidthPx: Float,
) {
    passA.record(size = captures.passA.layerSize(sourceWidthPx)) {
        translate(0f, -captures.passA.sourceStartPx) {
            drawLayer(sourceLayer)
        }
    }
    passB.record(size = captures.passB.layerSize(sourceWidthPx)) {
        translate(0f, captures.passA.sourceStartPx - captures.passB.sourceStartPx) {
            drawLayer(passA)
        }
    }
    passC.record(size = captures.passC.layerSize(sourceWidthPx)) {
        translate(0f, captures.passB.sourceStartPx - captures.passC.sourceStartPx) {
            drawLayer(passB)
        }
    }
}

private fun DrawScope.arborPanelPath(
    range: ArborPanelRange,
    edge: ArborBlurEdge,
    cornerRadiusPx: Float,
): Path {
    val start = range.startPx.coerceIn(0f, size.height)
    val end = range.endPx.coerceIn(start, size.height)
    val extent = end - start
    val radius = cornerRadiusPx.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
    return Path().apply {
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
}

private fun DrawScope.drawArborPanelOverlay(
    range: ArborPanelRange,
    edge: ArborBlurEdge,
    tint: Color,
    cornerRadiusPx: Float,
    mergeDistancePx: Float,
    softness: Float,
    minimumFeatherPx: Float,
) {
    if (tint.alpha <= 0f || range.extentPx <= 0f) return
    val panelColor = tint
    val start = range.startPx.coerceIn(0f, size.height)
    val end = range.endPx.coerceIn(start, size.height)
    val extent = end - start
    if (extent <= 0f) return
    val radius = cornerRadiusPx.coerceIn(0f, minOf(size.width / 2f, extent / 2f))
    val softnessMix = edgeSoftnessActivation(softness)
    val merge = resolveFeatherDistancePx(
        requestedDistancePx = mergeDistancePx,
        softness = softness,
        minimumFeatherPx = minimumFeatherPx,
        maximumDistancePx = extent,
    )

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
                                0.52f to panelColor.copy(alpha = panelColor.alpha * (1f - 0.08f * softnessMix)),
                                0.78f to panelColor.copy(alpha = panelColor.alpha * (1f - 0.46f * softnessMix)),
                                1f to panelColor.copy(alpha = panelColor.alpha * (1f - softnessMix)),
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
                                0f to panelColor.copy(alpha = panelColor.alpha * (1f - softnessMix)),
                                0.22f to panelColor.copy(alpha = panelColor.alpha * (1f - 0.46f * softnessMix)),
                                0.48f to panelColor.copy(alpha = panelColor.alpha * (1f - 0.08f * softnessMix)),
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
    strength: Float,
    maxRadiusDp: Float = DEFAULT_MAX_RADIUS_DP,
): Float = maxRadiusDp.coerceAtLeast(0f) * strength.coerceIn(0f, 1f)

internal fun calculateMergeDistanceDp(
    edgeSoftness: Float,
    maximumMergeDp: Float = MAXIMUM_MERGE_DISTANCE_DP,
): Float = maximumMergeDp.coerceAtLeast(0f) * edgeSoftness.coerceIn(0f, 1f)


internal fun edgeSoftnessActivation(edgeSoftness: Float): Float =
    arborBlurProgress(edgeSoftness.coerceIn(0f, 1f) / LOW_SOFTNESS_RAMP_END)

internal fun resolveFeatherDistancePx(
    requestedDistancePx: Float,
    softness: Float,
    minimumFeatherPx: Float,
    maximumDistancePx: Float,
): Float {
    val normalized = softness.coerceIn(0f, 1f)
    if (normalized <= 0f || maximumDistancePx <= 0f) return 0f
    return maxOf(requestedDistancePx, minimumFeatherPx.coerceAtLeast(0f))
        .coerceIn(0f, maximumDistancePx)
}

/** Registers a chrome panel. Blur and tint are rendered together by the source. */
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
): Modifier = composed {
    val radiusDp = calculateBlurRadiusDp(
        strength = strength,
        maxRadiusDp = maxRadius.value,
    )
    val mergeDp = calculateMergeDistanceDp(
        edgeSoftness = edgeSoftness,
        maximumMergeDp = maximumMergeDistance.value,
    )

    SideEffect {
        state.update(
            edge = edge,
            radiusDp = radiusDp,
            fadeDp = overlayDistance.value,
            cornerRadiusDp = cornerRadius.value,
            mergeDp = mergeDp,
            softness = edgeSoftness.coerceIn(0f, 1f),
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
    tint.copy(alpha = opacity.coerceIn(0f, 1f))

internal fun quantizeBlurRadiusDp(radiusDp: Float): Float =
    ((radiusDp.coerceAtLeast(0f) / BLUR_RADIUS_STEP_DP).roundToInt() * BLUR_RADIUS_STEP_DP)

private const val BLUR_RADIUS_STEP_DP = 0.25f
private const val MIN_VISIBLE_RADIUS_PX = 0.35f
private const val DEFAULT_MAX_RADIUS_DP = 56f
private const val DEFAULT_PANEL_CORNER_RADIUS_DP = 28f
private const val DEFAULT_MERGE_DISTANCE_DP = 34f
private const val MAXIMUM_MERGE_DISTANCE_DP = 68f
private const val MINIMUM_FEATHER_DISTANCE_DP = 4f
private const val LOW_SOFTNESS_RAMP_END = 0.12f
private const val DEFAULT_TOP_FADE_DP = 128f
private const val DEFAULT_BOTTOM_FADE_DP = 208f

internal const val BLUR_BASE_MAX_PAIRS = 25
internal const val BLUR_EXTRA_CORE_PAIRS = 4
internal const val BLUR_EXTRA_EDGE_PAIRS = 7
internal const val BLUR_SAMPLES_PER_PASS = 31
internal const val BLUR_MAX_SAMPLES_PER_PASS = 73
internal const val BLUR_PASS_COUNT = 3
private const val BLUR_CORE_DENSITY_FULL_STRENGTH = 0.40f
private const val BLUR_EDGE_DENSITY_START_STRENGTH = 0.30f

internal fun blurPairBudget(strength: Float): Float {
    val normalized = strength.coerceIn(0f, 1f)
    val basePairs = BLUR_BASE_MAX_PAIRS * normalized
    val corePairs = BLUR_EXTRA_CORE_PAIRS *
        arborBlurProgress(normalized / BLUR_CORE_DENSITY_FULL_STRENGTH)
    val edgePairs = BLUR_EXTRA_EDGE_PAIRS * arborBlurProgress(
        (normalized - BLUR_EDGE_DENSITY_START_STRENGTH) /
            (1f - BLUR_EDGE_DENSITY_START_STRENGTH),
    )
    return basePairs + corePairs + edgePairs
}

internal fun blurEffectiveSamplesPerPass(strength: Float): Float =
    1f + 2f * blurPairBudget(strength)

internal fun blurSamplesPerPass(strength: Float): Int {
    if (strength <= 0f) return 1
    return 1 + 2 * ceil(blurPairBudget(strength).toDouble()).toInt()
}

internal data class BlurStripCapture(
    val sourceStartPx: Float,
    val sourceEndPx: Float,
) {
    val sourceExtentPx: Float get() = (sourceEndPx - sourceStartPx).coerceAtLeast(0f)

    fun layerSize(sourceWidthPx: Float): IntSize = IntSize(
        width = ceil(sourceWidthPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1),
        height = ceil(sourceExtentPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1),
    )
}

internal data class AdaptiveBlurPassCaptures(
    val passA: BlurStripCapture,
    val passB: BlurStripCapture,
    val passC: BlurStripCapture,
) {
    fun filteredPixels(sourceWidthPx: Float): Long =
        passA.layerSize(sourceWidthPx).let { it.width.toLong() * it.height.toLong() } +
            passB.layerSize(sourceWidthPx).let { it.width.toLong() * it.height.toLong() } +
            passC.layerSize(sourceWidthPx).let { it.width.toLong() * it.height.toLong() }
}

internal val BLUR_PASS_A_VERTICAL_RADIUS_MULTIPLIER: Float = kotlin.math.abs(BLUR_AXIS_A_Y)
internal val BLUR_PASS_B_VERTICAL_RADIUS_MULTIPLIER: Float = kotlin.math.abs(BLUR_AXIS_B_Y)
internal val BLUR_PASS_C_VERTICAL_RADIUS_MULTIPLIER: Float = kotlin.math.abs(BLUR_AXIS_C_Y)
internal val BLUR_CHAIN_VERTICAL_SUPPORT_RADIUS_MULTIPLIER: Float =
    BLUR_PASS_A_VERTICAL_RADIUS_MULTIPLIER +
        BLUR_PASS_B_VERTICAL_RADIUS_MULTIPLIER +
        BLUR_PASS_C_VERTICAL_RADIUS_MULTIPLIER

internal fun resolveAdaptiveBlurPassCaptures(
    panelRange: ArborPanelRange,
    sourceHeightPx: Float,
    radiusPx: Float,
): AdaptiveBlurPassCaptures? {
    val sourceHeight = sourceHeightPx.coerceAtLeast(0f)
    if (sourceHeight <= 0f || panelRange.extentPx <= 0f || radiusPx < MIN_VISIBLE_RADIUS_PX) return null

    fun capture(verticalSupport: Float): BlurStripCapture {
        val support = radiusPx.coerceAtLeast(0f) * verticalSupport
        val start = (panelRange.startPx - support).coerceIn(0f, sourceHeight)
        val end = (panelRange.endPx + support).coerceIn(start, sourceHeight)
        return BlurStripCapture(start, end)
    }

    return AdaptiveBlurPassCaptures(
        passA = capture(BLUR_CHAIN_VERTICAL_SUPPORT_RADIUS_MULTIPLIER),
        passB = capture(BLUR_PASS_B_VERTICAL_RADIUS_MULTIPLIER + BLUR_PASS_C_VERTICAL_RADIUS_MULTIPLIER),
        passC = capture(BLUR_PASS_C_VERTICAL_RADIUS_MULTIPLIER),
    )
}

private data class AdaptiveStripPassEffects(
    val passA: androidx.compose.ui.graphics.RenderEffect,
    val passB: androidx.compose.ui.graphics.RenderEffect,
    val passC: androidx.compose.ui.graphics.RenderEffect,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildAdaptiveStripPassEffects(
    captures: AdaptiveBlurPassCaptures,
    panelRange: ArborPanelRange,
    radiusPx: Float,
    sourceWidthPx: Float,
    cornerRadiusPx: Float,
    mergeDistancePx: Float,
    softness: Float,
    maxBlurRadiusPx: Float,
    minimumMergePx: Float,
    edge: ArborBlurEdge,
): AdaptiveStripPassEffects {
    ArborRenderProfiler.recordBlurEffectBuild()

    fun effect(capture: BlurStripCapture, directionX: Float, directionY: Float): androidx.compose.ui.graphics.RenderEffect {
        val localPanelStart = panelRange.startPx - capture.sourceStartPx
        val localPanelEnd = panelRange.endPx - capture.sourceStartPx
        val shader = RuntimeShader(EDGE_BLUR_SHADER).apply {
            when (edge) {
                ArborBlurEdge.TOP -> {
                    setFloatUniform("uBlur", radiusPx, 0f)
                    setFloatUniform("uPanelStart", localPanelStart, 0f)
                    setFloatUniform("uPanelEnd", localPanelEnd, 0f)
                    setFloatUniform("uCorner", cornerRadiusPx, 0f)
                    setFloatUniform("uMerge", mergeDistancePx, 0f)
                    setFloatUniform("uSoftness", softness.coerceIn(0f, 1f), 0f)
                }
                ArborBlurEdge.BOTTOM -> {
                    setFloatUniform("uBlur", 0f, radiusPx)
                    setFloatUniform("uPanelStart", 0f, localPanelStart)
                    setFloatUniform("uPanelEnd", 0f, localPanelEnd)
                    setFloatUniform("uCorner", 0f, cornerRadiusPx)
                    setFloatUniform("uMerge", 0f, mergeDistancePx)
                    setFloatUniform("uSoftness", 0f, softness.coerceIn(0f, 1f))
                }
            }
            setFloatUniform("uSize", sourceWidthPx.coerceAtLeast(1f), capture.sourceExtentPx.coerceAtLeast(1f))
            setFloatUniform("uMinMerge", minimumMergePx)
            setFloatUniform("uMaxBlurRadius", maxBlurRadiusPx.coerceAtLeast(0.001f))
            setFloatUniform("uDirection", directionX, directionY)
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    return AdaptiveStripPassEffects(
        passA = effect(captures.passA, BLUR_AXIS_A_X, BLUR_AXIS_A_Y),
        passB = effect(captures.passB, BLUR_AXIS_B_X, BLUR_AXIS_B_Y),
        passC = effect(captures.passC, BLUR_AXIS_C_X, BLUR_AXIS_C_Y),
    )
}

internal const val BLUR_AXIS_A_X = 0.9238795f
internal const val BLUR_AXIS_A_Y = 0.3826834f
internal const val BLUR_AXIS_B_X = 0.1305262f
internal const val BLUR_AXIS_B_Y = 0.9914449f
internal const val BLUR_AXIS_C_X = -0.7933533f
internal const val BLUR_AXIS_C_Y = 0.6087614f

/**
 * Continuous glass-kernel density.
 *
 * The base lattice follows the user's 2N% -> N+1 effective-sample rule. Four
 * extra core pairs preserve central density, while seven progressively enabled
 * midpoint pairs fill the outer 26% of the kernel. Pair activation is
 * fractional, so moving the slider never swaps abruptly between whole kernels.
 */
private val EDGE_BLUR_SHADER = """
    uniform shader content;
    uniform float2 uBlur;
    uniform float uMaxBlurRadius;
    uniform float2 uSize;
    uniform float2 uPanelStart;
    uniform float2 uPanelEnd;
    uniform float2 uCorner;
    uniform float2 uMerge;
    uniform float2 uSoftness;
    uniform float uMinMerge;
    uniform float2 uDirection;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    float gaussianWeight(float normalizedDistance) {
        float scaled = normalizedDistance / 0.45;
        return exp(-0.5 * scaled * scaled);
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

    half4 adaptiveGlassBlur(float2 coord, float radius) {
        float safeMaxRadius = max(uMaxBlurRadius, 0.001);
        float strength = saturate(radius / safeMaxRadius);
        float basePairBudget = 25.0 * strength;
        float corePairBudget = 4.0 * smoother(strength / 0.40);
        float edgePairBudget = 7.0 * smoother((strength - 0.30) / 0.70);
        float baseStep = safeMaxRadius / 25.0;

        half4 accum = half4(content.eval(coord));
        float weightSum = 1.0;

        float baseActivation1 = saturate(basePairBudget - 0.0);
        if (baseActivation1 > 0.0001) {
            float baseOffset1 = min(radius, baseStep * 1.0);
            float baseWeight1 = gaussianWeight(baseOffset1 / radius) * baseActivation1;
            float2 baseDelta1 = uDirection * baseOffset1;
            accum += half4(content.eval(coord + baseDelta1)) * baseWeight1;
            accum += half4(content.eval(coord - baseDelta1)) * baseWeight1;
            weightSum += 2.0 * baseWeight1;
        }
        float baseActivation2 = saturate(basePairBudget - 1.0);
        if (baseActivation2 > 0.0001) {
            float baseOffset2 = min(radius, baseStep * 2.0);
            float baseWeight2 = gaussianWeight(baseOffset2 / radius) * baseActivation2;
            float2 baseDelta2 = uDirection * baseOffset2;
            accum += half4(content.eval(coord + baseDelta2)) * baseWeight2;
            accum += half4(content.eval(coord - baseDelta2)) * baseWeight2;
            weightSum += 2.0 * baseWeight2;
        }
        float baseActivation3 = saturate(basePairBudget - 2.0);
        if (baseActivation3 > 0.0001) {
            float baseOffset3 = min(radius, baseStep * 3.0);
            float baseWeight3 = gaussianWeight(baseOffset3 / radius) * baseActivation3;
            float2 baseDelta3 = uDirection * baseOffset3;
            accum += half4(content.eval(coord + baseDelta3)) * baseWeight3;
            accum += half4(content.eval(coord - baseDelta3)) * baseWeight3;
            weightSum += 2.0 * baseWeight3;
        }
        float baseActivation4 = saturate(basePairBudget - 3.0);
        if (baseActivation4 > 0.0001) {
            float baseOffset4 = min(radius, baseStep * 4.0);
            float baseWeight4 = gaussianWeight(baseOffset4 / radius) * baseActivation4;
            float2 baseDelta4 = uDirection * baseOffset4;
            accum += half4(content.eval(coord + baseDelta4)) * baseWeight4;
            accum += half4(content.eval(coord - baseDelta4)) * baseWeight4;
            weightSum += 2.0 * baseWeight4;
        }
        float baseActivation5 = saturate(basePairBudget - 4.0);
        if (baseActivation5 > 0.0001) {
            float baseOffset5 = min(radius, baseStep * 5.0);
            float baseWeight5 = gaussianWeight(baseOffset5 / radius) * baseActivation5;
            float2 baseDelta5 = uDirection * baseOffset5;
            accum += half4(content.eval(coord + baseDelta5)) * baseWeight5;
            accum += half4(content.eval(coord - baseDelta5)) * baseWeight5;
            weightSum += 2.0 * baseWeight5;
        }
        float baseActivation6 = saturate(basePairBudget - 5.0);
        if (baseActivation6 > 0.0001) {
            float baseOffset6 = min(radius, baseStep * 6.0);
            float baseWeight6 = gaussianWeight(baseOffset6 / radius) * baseActivation6;
            float2 baseDelta6 = uDirection * baseOffset6;
            accum += half4(content.eval(coord + baseDelta6)) * baseWeight6;
            accum += half4(content.eval(coord - baseDelta6)) * baseWeight6;
            weightSum += 2.0 * baseWeight6;
        }
        float baseActivation7 = saturate(basePairBudget - 6.0);
        if (baseActivation7 > 0.0001) {
            float baseOffset7 = min(radius, baseStep * 7.0);
            float baseWeight7 = gaussianWeight(baseOffset7 / radius) * baseActivation7;
            float2 baseDelta7 = uDirection * baseOffset7;
            accum += half4(content.eval(coord + baseDelta7)) * baseWeight7;
            accum += half4(content.eval(coord - baseDelta7)) * baseWeight7;
            weightSum += 2.0 * baseWeight7;
        }
        float baseActivation8 = saturate(basePairBudget - 7.0);
        if (baseActivation8 > 0.0001) {
            float baseOffset8 = min(radius, baseStep * 8.0);
            float baseWeight8 = gaussianWeight(baseOffset8 / radius) * baseActivation8;
            float2 baseDelta8 = uDirection * baseOffset8;
            accum += half4(content.eval(coord + baseDelta8)) * baseWeight8;
            accum += half4(content.eval(coord - baseDelta8)) * baseWeight8;
            weightSum += 2.0 * baseWeight8;
        }
        float baseActivation9 = saturate(basePairBudget - 8.0);
        if (baseActivation9 > 0.0001) {
            float baseOffset9 = min(radius, baseStep * 9.0);
            float baseWeight9 = gaussianWeight(baseOffset9 / radius) * baseActivation9;
            float2 baseDelta9 = uDirection * baseOffset9;
            accum += half4(content.eval(coord + baseDelta9)) * baseWeight9;
            accum += half4(content.eval(coord - baseDelta9)) * baseWeight9;
            weightSum += 2.0 * baseWeight9;
        }
        float baseActivation10 = saturate(basePairBudget - 9.0);
        if (baseActivation10 > 0.0001) {
            float baseOffset10 = min(radius, baseStep * 10.0);
            float baseWeight10 = gaussianWeight(baseOffset10 / radius) * baseActivation10;
            float2 baseDelta10 = uDirection * baseOffset10;
            accum += half4(content.eval(coord + baseDelta10)) * baseWeight10;
            accum += half4(content.eval(coord - baseDelta10)) * baseWeight10;
            weightSum += 2.0 * baseWeight10;
        }
        float baseActivation11 = saturate(basePairBudget - 10.0);
        if (baseActivation11 > 0.0001) {
            float baseOffset11 = min(radius, baseStep * 11.0);
            float baseWeight11 = gaussianWeight(baseOffset11 / radius) * baseActivation11;
            float2 baseDelta11 = uDirection * baseOffset11;
            accum += half4(content.eval(coord + baseDelta11)) * baseWeight11;
            accum += half4(content.eval(coord - baseDelta11)) * baseWeight11;
            weightSum += 2.0 * baseWeight11;
        }
        float baseActivation12 = saturate(basePairBudget - 11.0);
        if (baseActivation12 > 0.0001) {
            float baseOffset12 = min(radius, baseStep * 12.0);
            float baseWeight12 = gaussianWeight(baseOffset12 / radius) * baseActivation12;
            float2 baseDelta12 = uDirection * baseOffset12;
            accum += half4(content.eval(coord + baseDelta12)) * baseWeight12;
            accum += half4(content.eval(coord - baseDelta12)) * baseWeight12;
            weightSum += 2.0 * baseWeight12;
        }
        float baseActivation13 = saturate(basePairBudget - 12.0);
        if (baseActivation13 > 0.0001) {
            float baseOffset13 = min(radius, baseStep * 13.0);
            float baseWeight13 = gaussianWeight(baseOffset13 / radius) * baseActivation13;
            float2 baseDelta13 = uDirection * baseOffset13;
            accum += half4(content.eval(coord + baseDelta13)) * baseWeight13;
            accum += half4(content.eval(coord - baseDelta13)) * baseWeight13;
            weightSum += 2.0 * baseWeight13;
        }
        float baseActivation14 = saturate(basePairBudget - 13.0);
        if (baseActivation14 > 0.0001) {
            float baseOffset14 = min(radius, baseStep * 14.0);
            float baseWeight14 = gaussianWeight(baseOffset14 / radius) * baseActivation14;
            float2 baseDelta14 = uDirection * baseOffset14;
            accum += half4(content.eval(coord + baseDelta14)) * baseWeight14;
            accum += half4(content.eval(coord - baseDelta14)) * baseWeight14;
            weightSum += 2.0 * baseWeight14;
        }
        float baseActivation15 = saturate(basePairBudget - 14.0);
        if (baseActivation15 > 0.0001) {
            float baseOffset15 = min(radius, baseStep * 15.0);
            float baseWeight15 = gaussianWeight(baseOffset15 / radius) * baseActivation15;
            float2 baseDelta15 = uDirection * baseOffset15;
            accum += half4(content.eval(coord + baseDelta15)) * baseWeight15;
            accum += half4(content.eval(coord - baseDelta15)) * baseWeight15;
            weightSum += 2.0 * baseWeight15;
        }
        float baseActivation16 = saturate(basePairBudget - 15.0);
        if (baseActivation16 > 0.0001) {
            float baseOffset16 = min(radius, baseStep * 16.0);
            float baseWeight16 = gaussianWeight(baseOffset16 / radius) * baseActivation16;
            float2 baseDelta16 = uDirection * baseOffset16;
            accum += half4(content.eval(coord + baseDelta16)) * baseWeight16;
            accum += half4(content.eval(coord - baseDelta16)) * baseWeight16;
            weightSum += 2.0 * baseWeight16;
        }
        float baseActivation17 = saturate(basePairBudget - 16.0);
        if (baseActivation17 > 0.0001) {
            float baseOffset17 = min(radius, baseStep * 17.0);
            float baseWeight17 = gaussianWeight(baseOffset17 / radius) * baseActivation17;
            float2 baseDelta17 = uDirection * baseOffset17;
            accum += half4(content.eval(coord + baseDelta17)) * baseWeight17;
            accum += half4(content.eval(coord - baseDelta17)) * baseWeight17;
            weightSum += 2.0 * baseWeight17;
        }
        float baseActivation18 = saturate(basePairBudget - 17.0);
        if (baseActivation18 > 0.0001) {
            float baseOffset18 = min(radius, baseStep * 18.0);
            float baseWeight18 = gaussianWeight(baseOffset18 / radius) * baseActivation18;
            float2 baseDelta18 = uDirection * baseOffset18;
            accum += half4(content.eval(coord + baseDelta18)) * baseWeight18;
            accum += half4(content.eval(coord - baseDelta18)) * baseWeight18;
            weightSum += 2.0 * baseWeight18;
        }
        float baseActivation19 = saturate(basePairBudget - 18.0);
        if (baseActivation19 > 0.0001) {
            float baseOffset19 = min(radius, baseStep * 19.0);
            float baseWeight19 = gaussianWeight(baseOffset19 / radius) * baseActivation19;
            float2 baseDelta19 = uDirection * baseOffset19;
            accum += half4(content.eval(coord + baseDelta19)) * baseWeight19;
            accum += half4(content.eval(coord - baseDelta19)) * baseWeight19;
            weightSum += 2.0 * baseWeight19;
        }
        float baseActivation20 = saturate(basePairBudget - 19.0);
        if (baseActivation20 > 0.0001) {
            float baseOffset20 = min(radius, baseStep * 20.0);
            float baseWeight20 = gaussianWeight(baseOffset20 / radius) * baseActivation20;
            float2 baseDelta20 = uDirection * baseOffset20;
            accum += half4(content.eval(coord + baseDelta20)) * baseWeight20;
            accum += half4(content.eval(coord - baseDelta20)) * baseWeight20;
            weightSum += 2.0 * baseWeight20;
        }
        float baseActivation21 = saturate(basePairBudget - 20.0);
        if (baseActivation21 > 0.0001) {
            float baseOffset21 = min(radius, baseStep * 21.0);
            float baseWeight21 = gaussianWeight(baseOffset21 / radius) * baseActivation21;
            float2 baseDelta21 = uDirection * baseOffset21;
            accum += half4(content.eval(coord + baseDelta21)) * baseWeight21;
            accum += half4(content.eval(coord - baseDelta21)) * baseWeight21;
            weightSum += 2.0 * baseWeight21;
        }
        float baseActivation22 = saturate(basePairBudget - 21.0);
        if (baseActivation22 > 0.0001) {
            float baseOffset22 = min(radius, baseStep * 22.0);
            float baseWeight22 = gaussianWeight(baseOffset22 / radius) * baseActivation22;
            float2 baseDelta22 = uDirection * baseOffset22;
            accum += half4(content.eval(coord + baseDelta22)) * baseWeight22;
            accum += half4(content.eval(coord - baseDelta22)) * baseWeight22;
            weightSum += 2.0 * baseWeight22;
        }
        float baseActivation23 = saturate(basePairBudget - 22.0);
        if (baseActivation23 > 0.0001) {
            float baseOffset23 = min(radius, baseStep * 23.0);
            float baseWeight23 = gaussianWeight(baseOffset23 / radius) * baseActivation23;
            float2 baseDelta23 = uDirection * baseOffset23;
            accum += half4(content.eval(coord + baseDelta23)) * baseWeight23;
            accum += half4(content.eval(coord - baseDelta23)) * baseWeight23;
            weightSum += 2.0 * baseWeight23;
        }
        float baseActivation24 = saturate(basePairBudget - 23.0);
        if (baseActivation24 > 0.0001) {
            float baseOffset24 = min(radius, baseStep * 24.0);
            float baseWeight24 = gaussianWeight(baseOffset24 / radius) * baseActivation24;
            float2 baseDelta24 = uDirection * baseOffset24;
            accum += half4(content.eval(coord + baseDelta24)) * baseWeight24;
            accum += half4(content.eval(coord - baseDelta24)) * baseWeight24;
            weightSum += 2.0 * baseWeight24;
        }
        float baseActivation25 = saturate(basePairBudget - 24.0);
        if (baseActivation25 > 0.0001) {
            float baseOffset25 = min(radius, baseStep * 25.0);
            float baseWeight25 = gaussianWeight(baseOffset25 / radius) * baseActivation25;
            float2 baseDelta25 = uDirection * baseOffset25;
            accum += half4(content.eval(coord + baseDelta25)) * baseWeight25;
            accum += half4(content.eval(coord - baseDelta25)) * baseWeight25;
            weightSum += 2.0 * baseWeight25;
        }

        float coreActivation1 = saturate(corePairBudget - 0.0);
        if (coreActivation1 > 0.0001) {
            float coreOffset1 = radius * 0.50;
            float coreWeight1 = gaussianWeight(0.50) * coreActivation1 * 0.85;
            float2 coreDelta1 = uDirection * coreOffset1;
            accum += half4(content.eval(coord + coreDelta1)) * coreWeight1;
            accum += half4(content.eval(coord - coreDelta1)) * coreWeight1;
            weightSum += 2.0 * coreWeight1;
        }
        float coreActivation2 = saturate(corePairBudget - 1.0);
        if (coreActivation2 > 0.0001) {
            float coreOffset2 = radius * 0.30;
            float coreWeight2 = gaussianWeight(0.30) * coreActivation2 * 0.85;
            float2 coreDelta2 = uDirection * coreOffset2;
            accum += half4(content.eval(coord + coreDelta2)) * coreWeight2;
            accum += half4(content.eval(coord - coreDelta2)) * coreWeight2;
            weightSum += 2.0 * coreWeight2;
        }
        float coreActivation3 = saturate(corePairBudget - 2.0);
        if (coreActivation3 > 0.0001) {
            float coreOffset3 = radius * 0.70;
            float coreWeight3 = gaussianWeight(0.70) * coreActivation3 * 0.85;
            float2 coreDelta3 = uDirection * coreOffset3;
            accum += half4(content.eval(coord + coreDelta3)) * coreWeight3;
            accum += half4(content.eval(coord - coreDelta3)) * coreWeight3;
            weightSum += 2.0 * coreWeight3;
        }
        float coreActivation4 = saturate(corePairBudget - 3.0);
        if (coreActivation4 > 0.0001) {
            float coreOffset4 = radius * 0.18;
            float coreWeight4 = gaussianWeight(0.18) * coreActivation4 * 0.85;
            float2 coreDelta4 = uDirection * coreOffset4;
            accum += half4(content.eval(coord + coreDelta4)) * coreWeight4;
            accum += half4(content.eval(coord - coreDelta4)) * coreWeight4;
            weightSum += 2.0 * coreWeight4;
        }

        float edgeActivation1 = saturate(edgePairBudget - 0.0);
        if (edgeActivation1 > 0.0001) {
            float edgeOffset1 = radius * 0.98;
            float edgeWeight1 = gaussianWeight(0.98) * edgeActivation1 * 1.15;
            float2 edgeDelta1 = uDirection * edgeOffset1;
            accum += half4(content.eval(coord + edgeDelta1)) * edgeWeight1;
            accum += half4(content.eval(coord - edgeDelta1)) * edgeWeight1;
            weightSum += 2.0 * edgeWeight1;
        }
        float edgeActivation2 = saturate(edgePairBudget - 1.0);
        if (edgeActivation2 > 0.0001) {
            float edgeOffset2 = radius * 0.94;
            float edgeWeight2 = gaussianWeight(0.94) * edgeActivation2 * 1.15;
            float2 edgeDelta2 = uDirection * edgeOffset2;
            accum += half4(content.eval(coord + edgeDelta2)) * edgeWeight2;
            accum += half4(content.eval(coord - edgeDelta2)) * edgeWeight2;
            weightSum += 2.0 * edgeWeight2;
        }
        float edgeActivation3 = saturate(edgePairBudget - 2.0);
        if (edgeActivation3 > 0.0001) {
            float edgeOffset3 = radius * 0.90;
            float edgeWeight3 = gaussianWeight(0.90) * edgeActivation3 * 1.15;
            float2 edgeDelta3 = uDirection * edgeOffset3;
            accum += half4(content.eval(coord + edgeDelta3)) * edgeWeight3;
            accum += half4(content.eval(coord - edgeDelta3)) * edgeWeight3;
            weightSum += 2.0 * edgeWeight3;
        }
        float edgeActivation4 = saturate(edgePairBudget - 3.0);
        if (edgeActivation4 > 0.0001) {
            float edgeOffset4 = radius * 0.86;
            float edgeWeight4 = gaussianWeight(0.86) * edgeActivation4 * 1.15;
            float2 edgeDelta4 = uDirection * edgeOffset4;
            accum += half4(content.eval(coord + edgeDelta4)) * edgeWeight4;
            accum += half4(content.eval(coord - edgeDelta4)) * edgeWeight4;
            weightSum += 2.0 * edgeWeight4;
        }
        float edgeActivation5 = saturate(edgePairBudget - 4.0);
        if (edgeActivation5 > 0.0001) {
            float edgeOffset5 = radius * 0.82;
            float edgeWeight5 = gaussianWeight(0.82) * edgeActivation5 * 1.15;
            float2 edgeDelta5 = uDirection * edgeOffset5;
            accum += half4(content.eval(coord + edgeDelta5)) * edgeWeight5;
            accum += half4(content.eval(coord - edgeDelta5)) * edgeWeight5;
            weightSum += 2.0 * edgeWeight5;
        }
        float edgeActivation6 = saturate(edgePairBudget - 5.0);
        if (edgeActivation6 > 0.0001) {
            float edgeOffset6 = radius * 0.78;
            float edgeWeight6 = gaussianWeight(0.78) * edgeActivation6 * 1.15;
            float2 edgeDelta6 = uDirection * edgeOffset6;
            accum += half4(content.eval(coord + edgeDelta6)) * edgeWeight6;
            accum += half4(content.eval(coord - edgeDelta6)) * edgeWeight6;
            weightSum += 2.0 * edgeWeight6;
        }
        float edgeActivation7 = saturate(edgePairBudget - 6.0);
        if (edgeActivation7 > 0.0001) {
            float edgeOffset7 = radius * 0.74;
            float edgeWeight7 = gaussianWeight(0.74) * edgeActivation7 * 1.15;
            float2 edgeDelta7 = uDirection * edgeOffset7;
            accum += half4(content.eval(coord + edgeDelta7)) * edgeWeight7;
            accum += half4(content.eval(coord - edgeDelta7)) * edgeWeight7;
            weightSum += 2.0 * edgeWeight7;
        }

        return accum / weightSum;
    }

    half4 main(float2 coord) {
        float topStart = uPanelStart.x;
        float topEnd = uPanelEnd.x;
        float topMask = roundedTopPanelMask(coord, topStart, topEnd, uCorner.x);
        float topSoftnessMix = smoother(uSoftness.x / 0.12);
        float topMerge = max(max(uMerge.x, uMinMerge), 1.0);
        float topFeather = smoother((topEnd - coord.y) / topMerge);
        float topMix = topMask * mix(1.0, topFeather, topSoftnessMix);

        float bottomStart = uPanelStart.y;
        float bottomEnd = uPanelEnd.y;
        float bottomMask = roundedBottomPanelMask(coord, bottomStart, bottomEnd, uCorner.y);
        float bottomSoftnessMix = smoother(uSoftness.y / 0.12);
        float bottomMerge = max(max(uMerge.y, uMinMerge), 1.0);
        float bottomFeather = smoother((coord.y - bottomStart) / bottomMerge);
        float bottomMix = bottomMask * mix(1.0, bottomFeather, bottomSoftnessMix);

        float radius = max(uBlur.x * topMix, uBlur.y * bottomMix);
        if (radius < 0.35) return content.eval(coord);
        return adaptiveGlassBlur(coord, radius);
    }
""".trimIndent()
