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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Which chrome edge owns a backdrop panel. */
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
    internal var topSaturation by mutableFloatStateOf(DEFAULT_GLASS_SATURATION)
    internal var bottomSaturation by mutableFloatStateOf(DEFAULT_GLASS_SATURATION)
    internal var topContrast by mutableFloatStateOf(DEFAULT_GLASS_CONTRAST)
    internal var bottomContrast by mutableFloatStateOf(DEFAULT_GLASS_CONTRAST)
    internal var topBrightness by mutableFloatStateOf(DEFAULT_GLASS_BRIGHTNESS)
    internal var bottomBrightness by mutableFloatStateOf(DEFAULT_GLASS_BRIGHTNESS)
    internal var topEdgeHighlight by mutableFloatStateOf(DEFAULT_EDGE_HIGHLIGHT)
    internal var bottomEdgeHighlight by mutableFloatStateOf(DEFAULT_EDGE_HIGHLIGHT)
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
        saturation: Float,
        contrast: Float,
        brightness: Float,
        edgeHighlight: Float,
    ) {
        val radius = quantizeBlurRadiusDp(radiusDp)
        val fade = fadeDp.coerceAtLeast(1f)
        val corner = cornerRadiusDp.coerceAtLeast(0f)
        val merge = mergeDp.coerceIn(0f, fade)
        val normalizedSoftness = softness.coerceIn(0f, 1f)
        val normalizedSaturation = saturation.coerceIn(0.75f, 1.35f)
        val normalizedContrast = contrast.coerceIn(0.85f, 1.20f)
        val normalizedBrightness = brightness.coerceIn(0.85f, 1.15f)
        val normalizedHighlight = edgeHighlight.coerceIn(0f, 0.12f)
        when (edge) {
            ArborBlurEdge.TOP -> {
                if (topRadiusDp != radius) topRadiusDp = radius
                if (topFadeDp != fade) topFadeDp = fade
                if (topCornerRadiusDp != corner) topCornerRadiusDp = corner
                if (topMergeDp != merge) topMergeDp = merge
                if (topSoftness != normalizedSoftness) topSoftness = normalizedSoftness
                if (topSaturation != normalizedSaturation) topSaturation = normalizedSaturation
                if (topContrast != normalizedContrast) topContrast = normalizedContrast
                if (topBrightness != normalizedBrightness) topBrightness = normalizedBrightness
                if (topEdgeHighlight != normalizedHighlight) topEdgeHighlight = normalizedHighlight
                if (topTint != tint) topTint = tint
            }
            ArborBlurEdge.BOTTOM -> {
                if (bottomRadiusDp != radius) bottomRadiusDp = radius
                if (bottomFadeDp != fade) bottomFadeDp = fade
                if (bottomCornerRadiusDp != corner) bottomCornerRadiusDp = corner
                if (bottomMergeDp != merge) bottomMergeDp = merge
                if (bottomSoftness != normalizedSoftness) bottomSoftness = normalizedSoftness
                if (bottomSaturation != normalizedSaturation) bottomSaturation = normalizedSaturation
                if (bottomContrast != normalizedContrast) bottomContrast = normalizedContrast
                if (bottomBrightness != normalizedBrightness) bottomBrightness = normalizedBrightness
                if (bottomEdgeHighlight != normalizedHighlight) bottomEdgeHighlight = normalizedHighlight
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
 * Shared-source, panel-local dual-Kawase backdrop renderer.
 *
 * The Compose subtree is recorded exactly once for a frame in [sourceLayer].
 * Each visible panel replays that source into one fixed-overscan capture. Every
 * downsample and upsample level represents the same capture extent at a
 * different resolution; no pass progressively crops the source. The visible
 * rounded-panel crop is applied only when the completed full-resolution result
 * is composited over the normal source replay.
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
    val topPlan = resolveKawasePanelPlan(topRange, contentWidthPx, contentHeightPx, topRadiusPx)
    val bottomPlan = resolveKawasePanelPlan(bottomRange, contentWidthPx, contentHeightPx, bottomRadiusPx)

    val topVisual = remember(
        topRange,
        state.topCornerRadiusDp,
        state.topMergeDp,
        state.topSoftness,
        state.topTint,
        state.topSaturation,
        state.topContrast,
        state.topBrightness,
        state.topEdgeHighlight,
        density,
    ) {
        GlassVisualConfig(
            edge = ArborBlurEdge.TOP,
            range = topRange,
            cornerRadiusPx = state.topCornerRadiusDp * density,
            mergeDistancePx = state.topMergeDp * density,
            softness = state.topSoftness,
            tint = state.topTint,
            saturation = state.topSaturation,
            contrast = state.topContrast,
            brightness = state.topBrightness,
            edgeHighlight = state.topEdgeHighlight,
            minimumFeatherPx = MINIMUM_FEATHER_DISTANCE_DP * density,
        )
    }
    val bottomVisual = remember(
        bottomRange,
        state.bottomCornerRadiusDp,
        state.bottomMergeDp,
        state.bottomSoftness,
        state.bottomTint,
        state.bottomSaturation,
        state.bottomContrast,
        state.bottomBrightness,
        state.bottomEdgeHighlight,
        density,
    ) {
        GlassVisualConfig(
            edge = ArborBlurEdge.BOTTOM,
            range = bottomRange,
            cornerRadiusPx = state.bottomCornerRadiusDp * density,
            mergeDistancePx = state.bottomMergeDp * density,
            softness = state.bottomSoftness,
            tint = state.bottomTint,
            saturation = state.bottomSaturation,
            contrast = state.bottomContrast,
            brightness = state.bottomBrightness,
            edgeHighlight = state.bottomEdgeHighlight,
            minimumFeatherPx = MINIMUM_FEATHER_DISTANCE_DP * density,
        )
    }
    val topGeometry = remember(contentWidthPx, contentHeightPx, topVisual) {
        buildPanelGeometry(contentWidthPx, contentHeightPx, topVisual)
    }
    val bottomGeometry = remember(contentWidthPx, contentHeightPx, bottomVisual) {
        buildPanelGeometry(contentWidthPx, contentHeightPx, bottomVisual)
    }

    val sourceLayer = rememberGraphicsLayer()
    val topLayers = rememberKawaseLayers()
    val bottomLayers = rememberKawaseLayers()

    val topEffects = remember(topPlan, topVisual) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            topPlan?.let { buildKawaseEffects(it, topVisual) }
        } else null
    }
    val bottomEffects = remember(bottomPlan, bottomVisual) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bottomPlan?.let { buildKawaseEffects(it, bottomVisual) }
        } else null
    }
    SideEffect {
        topLayers.applyEffects(topEffects)
        bottomLayers.applyEffects(bottomEffects)
    }

    measured.drawWithContent {
        val topActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && topPlan != null && topEffects != null
        val bottomActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bottomPlan != null && bottomEffects != null
        val blurActive = topActive || bottomActive
        val profilerActive = ArborRenderProfiler.enabled
        var blurCpuNanos = 0L
        var processedPixels = 0L
        var layerReplays = 0
        var downsampleLevels = 0
        var upsampleLevels = 0
        var captureUpdates = 0

        if (blurActive) {
            val sourceSize = IntSize(
                width = size.width.roundToInt().coerceAtLeast(1),
                height = size.height.roundToInt().coerceAtLeast(1),
            )
            val sourceStarted = if (profilerActive) System.nanoTime() else 0L
            sourceLayer.record(size = sourceSize) { this@drawWithContent.drawContent() }
            if (profilerActive) blurCpuNanos += System.nanoTime() - sourceStarted

            if (topActive) {
                val plan = requireNotNull(topPlan)
                val started = if (profilerActive) System.nanoTime() else 0L
                recordKawasePanelChain(sourceLayer, topLayers, plan)
                if (profilerActive) {
                    blurCpuNanos += System.nanoTime() - started
                    processedPixels += plan.processedPixels()
                    layerReplays += plan.recordingReplayCount
                    downsampleLevels += plan.levelCount
                    upsampleLevels += plan.levelCount
                    captureUpdates++
                }
            }
            if (bottomActive) {
                val plan = requireNotNull(bottomPlan)
                val started = if (profilerActive) System.nanoTime() else 0L
                recordKawasePanelChain(sourceLayer, bottomLayers, plan)
                if (profilerActive) {
                    blurCpuNanos += System.nanoTime() - started
                    processedPixels += plan.processedPixels()
                    layerReplays += plan.recordingReplayCount
                    downsampleLevels += plan.levelCount
                    upsampleLevels += plan.levelCount
                    captureUpdates++
                }
            }

            drawLayer(sourceLayer)
            if (profilerActive) layerReplays++
        } else {
            drawContent()
        }

        if (topActive) {
            val plan = requireNotNull(topPlan)
            val started = if (profilerActive) System.nanoTime() else 0L
            clipPath(topGeometry.path) {
                translate(0f, plan.capture.sourceStartPx) { drawLayer(topLayers.finalFull) }
            }
            if (profilerActive) {
                blurCpuNanos += System.nanoTime() - started
                layerReplays++
            }
        } else {
            drawFallbackPanelOverlay(topGeometry, topVisual.tint)
        }
        if (bottomActive) {
            val plan = requireNotNull(bottomPlan)
            val started = if (profilerActive) System.nanoTime() else 0L
            clipPath(bottomGeometry.path) {
                translate(0f, plan.capture.sourceStartPx) { drawLayer(bottomLayers.finalFull) }
            }
            if (profilerActive) {
                blurCpuNanos += System.nanoTime() - started
                layerReplays++
            }
        } else {
            drawFallbackPanelOverlay(bottomGeometry, bottomVisual.tint)
        }

        if (profilerActive && blurActive) {
            ArborRenderProfiler.recordBlurFrame(
                cpuNanos = blurCpuNanos,
                processedPixels = processedPixels,
                sourceTraversals = 1,
                layerReplays = layerReplays,
                downsampleLevels = downsampleLevels,
                upsampleLevels = upsampleLevels,
                captureUpdates = captureUpdates,
            )
        }
    }
}

@Composable
private fun rememberKawaseLayers(): KawaseLayerSet = KawaseLayerSet(
    capture = rememberGraphicsLayer(),
    down1 = rememberGraphicsLayer(),
    down2 = rememberGraphicsLayer(),
    down3 = rememberGraphicsLayer(),
    up2 = rememberGraphicsLayer(),
    up1 = rememberGraphicsLayer(),
    finalFull = rememberGraphicsLayer(),
)

private data class KawaseLayerSet(
    val capture: GraphicsLayer,
    val down1: GraphicsLayer,
    val down2: GraphicsLayer,
    val down3: GraphicsLayer,
    val up2: GraphicsLayer,
    val up1: GraphicsLayer,
    val finalFull: GraphicsLayer,
) {
    fun applyEffects(effects: KawaseEffects?) {
        capture.renderEffect = null
        down1.renderEffect = effects?.down1
        down2.renderEffect = effects?.down2
        down3.renderEffect = effects?.down3
        up2.renderEffect = effects?.up2
        up1.renderEffect = effects?.up1
        finalFull.renderEffect = effects?.finalFull
    }
}

private fun DrawScope.recordKawasePanelChain(
    sourceLayer: GraphicsLayer,
    layers: KawaseLayerSet,
    plan: KawasePanelPlan,
) {
    layers.capture.record(size = plan.levelSize(0)) {
        translate(0f, -plan.capture.sourceStartPx) { drawLayer(sourceLayer) }
    }
    layers.down1.record(size = plan.levelSize(1)) {
        scale(0.5f, 0.5f, pivot = Offset.Zero) { drawLayer(layers.capture) }
    }
    layers.down2.record(size = plan.levelSize(2)) {
        scale(0.5f, 0.5f, pivot = Offset.Zero) { drawLayer(layers.down1) }
    }
    if (plan.levelCount == 3) {
        layers.down3.record(size = plan.levelSize(3)) {
            scale(0.5f, 0.5f, pivot = Offset.Zero) { drawLayer(layers.down2) }
        }
        layers.up2.record(size = plan.levelSize(2)) {
            scale(2f, 2f, pivot = Offset.Zero) { drawLayer(layers.down3) }
        }
        layers.up1.record(size = plan.levelSize(1)) {
            scale(2f, 2f, pivot = Offset.Zero) { drawLayer(layers.up2) }
        }
    } else {
        layers.up1.record(size = plan.levelSize(1)) {
            scale(2f, 2f, pivot = Offset.Zero) { drawLayer(layers.down2) }
        }
    }
    layers.finalFull.record(size = plan.levelSize(0)) {
        scale(2f, 2f, pivot = Offset.Zero) { drawLayer(layers.up1) }
    }
}

internal data class BlurCaptureExtent(
    val sourceStartPx: Float,
    val sourceEndPx: Float,
    val sourceWidthPx: Float,
    val supportPx: Float,
) {
    val sourceExtentPx: Float get() = (sourceEndPx - sourceStartPx).coerceAtLeast(0f)
}

internal data class KawasePanelPlan(
    val panelRange: ArborPanelRange,
    val capture: BlurCaptureExtent,
    val levelCount: Int,
    val radiusPx: Float,
    val baseTapOffsetPx: Float,
) {
    init { require(levelCount in 2..3) }

    /** All levels map to this exact full-resolution source extent. */
    fun sourceExtentAtLevel(level: Int): BlurCaptureExtent {
        require(level in 0..levelCount)
        return capture
    }

    fun levelSize(level: Int): IntSize {
        require(level in 0..levelCount)
        val divisor = 1 shl level
        return IntSize(
            width = ceil(capture.sourceWidthPx.coerceAtLeast(1f) / divisor).toInt().coerceAtLeast(1),
            height = ceil(capture.sourceExtentPx.coerceAtLeast(1f) / divisor).toInt().coerceAtLeast(1),
        )
    }

    fun processedPixels(): Long {
        var total = 0L
        for (level in 1..levelCount) total += levelSize(level).pixelCount()
        for (level in levelCount - 1 downTo 0) total += levelSize(level).pixelCount()
        return total
    }

    val recordingReplayCount: Int get() = 1 + levelCount + levelCount
}

private fun IntSize.pixelCount(): Long = width.toLong() * height.toLong()

internal fun resolveKawasePanelPlan(
    panelRange: ArborPanelRange,
    sourceWidthPx: Float,
    sourceHeightPx: Float,
    radiusPx: Float,
): KawasePanelPlan? {
    val sourceWidth = sourceWidthPx.coerceAtLeast(0f)
    val sourceHeight = sourceHeightPx.coerceAtLeast(0f)
    if (sourceWidth <= 0f || sourceHeight <= 0f || panelRange.extentPx <= 0f || radiusPx < MIN_VISIBLE_RADIUS_PX) {
        return null
    }
    val levelCount = resolveKawaseLevelCount(radiusPx)
    val support = calculateKawaseSupportPx(radiusPx, levelCount)
    val start = (panelRange.startPx - support).coerceIn(0f, sourceHeight)
    val end = (panelRange.endPx + support).coerceIn(start, sourceHeight)
    return KawasePanelPlan(
        panelRange = panelRange,
        capture = BlurCaptureExtent(
            sourceStartPx = start,
            sourceEndPx = end,
            sourceWidthPx = sourceWidth,
            supportPx = support,
        ),
        levelCount = levelCount,
        radiusPx = radiusPx,
        baseTapOffsetPx = calculateKawaseTapOffsetPx(radiusPx, levelCount),
    )
}

internal fun resolveKawaseLevelCount(radiusPx: Float): Int =
    if (radiusPx < KAWASE_THREE_LEVEL_RADIUS_PX) 2 else 3

internal fun calculateKawaseSupportPx(radiusPx: Float, levelCount: Int): Float {
    val reconstructionSupport = (1 shl levelCount) * KAWASE_MAX_LEVEL_TAP_SUPPORT_PX
    return ceil(max(KAWASE_MINIMUM_SUPPORT_PX, radiusPx.coerceAtLeast(0f) * KAWASE_SUPPORT_MULTIPLIER + reconstructionSupport))
}

internal fun calculateKawaseTapOffsetPx(radiusPx: Float, levelCount: Int): Float {
    val divisor = if (levelCount == 3) 92f else 68f
    return (KAWASE_MIN_TAP_OFFSET_PX + radiusPx.coerceAtLeast(0f) / divisor)
        .coerceIn(KAWASE_MIN_TAP_OFFSET_PX, KAWASE_MAX_TAP_OFFSET_PX)
}

private data class GlassVisualConfig(
    val edge: ArborBlurEdge,
    val range: ArborPanelRange,
    val cornerRadiusPx: Float,
    val mergeDistancePx: Float,
    val softness: Float,
    val tint: Color,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val edgeHighlight: Float,
    val minimumFeatherPx: Float,
)

private data class PanelGeometry(
    val path: Path,
    val startPx: Float,
    val endPx: Float,
    val bodyStartPx: Float,
    val bodyEndPx: Float,
    val fadeBrush: Brush?,
)

private fun buildPanelGeometry(widthPx: Float, heightPx: Float, visual: GlassVisualConfig): PanelGeometry {
    val start = visual.range.startPx.coerceIn(0f, heightPx)
    val end = visual.range.endPx.coerceIn(start, heightPx)
    val extent = end - start
    val radius = visual.cornerRadiusPx.coerceIn(0f, minOf(widthPx / 2f, extent / 2f))
    val path = Path().apply {
        when (visual.edge) {
            ArborBlurEdge.TOP -> addRoundRect(
                RoundRect(
                    left = 0f,
                    top = start,
                    right = widthPx,
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
                    right = widthPx,
                    bottom = end,
                    topLeftCornerRadius = CornerRadius(radius, radius),
                    topRightCornerRadius = CornerRadius(radius, radius),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
    }
    val softnessMix = edgeSoftnessActivation(visual.softness)
    val merge = resolveFeatherDistancePx(
        requestedDistancePx = visual.mergeDistancePx,
        softness = visual.softness,
        minimumFeatherPx = visual.minimumFeatherPx,
        maximumDistancePx = extent,
    )
    val bodyEnd = (end - merge).coerceAtLeast(start)
    val bodyStart = (start + merge).coerceAtMost(end)
    val fadeBrush = if (merge > 0f && visual.tint.alpha > 0f) {
        when (visual.edge) {
            ArborBlurEdge.TOP -> Brush.verticalGradient(
                0f to visual.tint,
                0.52f to visual.tint.copy(alpha = visual.tint.alpha * (1f - 0.08f * softnessMix)),
                0.78f to visual.tint.copy(alpha = visual.tint.alpha * (1f - 0.46f * softnessMix)),
                1f to visual.tint.copy(alpha = visual.tint.alpha * (1f - softnessMix)),
                startY = bodyEnd,
                endY = end,
            )
            ArborBlurEdge.BOTTOM -> Brush.verticalGradient(
                0f to visual.tint.copy(alpha = visual.tint.alpha * (1f - softnessMix)),
                0.22f to visual.tint.copy(alpha = visual.tint.alpha * (1f - 0.46f * softnessMix)),
                0.48f to visual.tint.copy(alpha = visual.tint.alpha * (1f - 0.08f * softnessMix)),
                1f to visual.tint,
                startY = start,
                endY = bodyStart,
            )
        }
    } else null
    return PanelGeometry(path, start, end, bodyStart, bodyEnd, fadeBrush)
}

private fun DrawScope.drawFallbackPanelOverlay(geometry: PanelGeometry, tint: Color) {
    if (tint.alpha <= 0f || geometry.endPx <= geometry.startPx) return
    clipPath(geometry.path) {
        when {
            geometry.fadeBrush == null -> drawRect(
                color = tint,
                topLeft = Offset(0f, geometry.startPx),
                size = Size(size.width, geometry.endPx - geometry.startPx),
            )
            geometry.bodyEndPx < geometry.endPx -> {
                if (geometry.bodyEndPx > geometry.startPx) {
                    drawRect(
                        color = tint,
                        topLeft = Offset(0f, geometry.startPx),
                        size = Size(size.width, geometry.bodyEndPx - geometry.startPx),
                    )
                }
                drawRect(
                    brush = geometry.fadeBrush,
                    topLeft = Offset(0f, geometry.bodyEndPx),
                    size = Size(size.width, geometry.endPx - geometry.bodyEndPx),
                )
            }
            else -> {
                drawRect(
                    brush = geometry.fadeBrush,
                    topLeft = Offset(0f, geometry.startPx),
                    size = Size(size.width, geometry.bodyStartPx - geometry.startPx),
                )
                if (geometry.bodyStartPx < geometry.endPx) {
                    drawRect(
                        color = tint,
                        topLeft = Offset(0f, geometry.bodyStartPx),
                        size = Size(size.width, geometry.endPx - geometry.bodyStartPx),
                    )
                }
            }
        }
    }
}

private data class KawaseEffects(
    val down1: androidx.compose.ui.graphics.RenderEffect,
    val down2: androidx.compose.ui.graphics.RenderEffect,
    val down3: androidx.compose.ui.graphics.RenderEffect?,
    val up2: androidx.compose.ui.graphics.RenderEffect?,
    val up1: androidx.compose.ui.graphics.RenderEffect,
    val finalFull: androidx.compose.ui.graphics.RenderEffect,
)

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildKawaseEffects(plan: KawasePanelPlan, visual: GlassVisualConfig): KawaseEffects {
    val effectCount = plan.levelCount * 2
    ArborRenderProfiler.recordBlurEffectBuild(effectCount)

    fun resampleEffect(level: Int, upsample: Boolean): androidx.compose.ui.graphics.RenderEffect {
        val angle = KAWASE_LEVEL_ANGLES[(level + if (upsample) 1 else 0) % KAWASE_LEVEL_ANGLES.size]
        val axisX = cos(angle)
        val axisY = sin(angle)
        val shader = RuntimeShader(KAWASE_RESAMPLE_SHADER).apply {
            setFloatUniform("uOffset", plan.baseTapOffsetPx + level * 0.18f + if (upsample) 0.20f else 0f)
            setFloatUniform("uAxisX", axisX, axisY)
            setFloatUniform("uAxisY", -axisY, axisX)
            setFloatUniform("uCenterWeight", if (upsample) 2.5f else 4f)
            setFloatUniform("uCardinalWeight", if (upsample) 1.35f else 1.8f)
            setFloatUniform("uDiagonalWeight", if (upsample) 0.75f else 1f)
        }
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }

    val localPanelStart = visual.range.startPx - plan.capture.sourceStartPx
    val localPanelEnd = visual.range.endPx - plan.capture.sourceStartPx
    val finalShader = RuntimeShader(KAWASE_COMPOSITE_SHADER).apply {
        val angle = KAWASE_LEVEL_ANGLES[0]
        val axisX = cos(angle)
        val axisY = sin(angle)
        setFloatUniform("uOffset", plan.baseTapOffsetPx + 0.15f)
        setFloatUniform("uAxisX", axisX, axisY)
        setFloatUniform("uAxisY", -axisY, axisX)
        setFloatUniform("uSize", plan.capture.sourceWidthPx, plan.capture.sourceExtentPx)
        setFloatUniform("uPanelStart", localPanelStart)
        setFloatUniform("uPanelEnd", localPanelEnd)
        setFloatUniform("uCorner", visual.cornerRadiusPx)
        setFloatUniform(
            "uMerge",
            resolveFeatherDistancePx(
                requestedDistancePx = visual.mergeDistancePx,
                softness = visual.softness,
                minimumFeatherPx = visual.minimumFeatherPx,
                maximumDistancePx = visual.range.extentPx,
            ).coerceAtLeast(1f),
        )
        setFloatUniform("uSoftness", edgeSoftnessActivation(visual.softness))
        setFloatUniform("uEdge", if (visual.edge == ArborBlurEdge.TOP) 0f else 1f)
        setFloatUniform("uTint", visual.tint.red, visual.tint.green, visual.tint.blue, visual.tint.alpha)
        setFloatUniform("uSaturation", visual.saturation)
        setFloatUniform("uContrast", visual.contrast)
        setFloatUniform("uBrightness", visual.brightness)
        setFloatUniform("uEdgeHighlight", visual.edgeHighlight)
    }

    return KawaseEffects(
        down1 = resampleEffect(level = 1, upsample = false),
        down2 = resampleEffect(level = 2, upsample = false),
        down3 = if (plan.levelCount == 3) resampleEffect(level = 3, upsample = false) else null,
        up2 = if (plan.levelCount == 3) resampleEffect(level = 2, upsample = true) else null,
        up1 = resampleEffect(level = 1, upsample = true),
        finalFull = RenderEffect.createRuntimeShaderEffect(finalShader, "content").asComposeRenderEffect(),
    )
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
    saturation: Float = DEFAULT_GLASS_SATURATION,
    contrast: Float = DEFAULT_GLASS_CONTRAST,
    brightness: Float = DEFAULT_GLASS_BRIGHTNESS,
    edgeHighlight: Float = DEFAULT_EDGE_HIGHLIGHT,
): Modifier = composed {
    val radiusDp = calculateBlurRadiusDp(strength = strength, maxRadiusDp = maxRadius.value)
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
            saturation = saturation,
            contrast = contrast,
            brightness = brightness,
            edgeHighlight = edgeHighlight,
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

/** Existing opacity values now scale each panel's designed tint instead of replacing it with opaque gray. */
internal fun applyOverlayOpacity(tint: Color, opacity: Float): Color =
    tint.copy(alpha = tint.alpha * opacity.coerceIn(0f, 1f))

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
private const val DEFAULT_GLASS_SATURATION = 1.10f
private const val DEFAULT_GLASS_CONTRAST = 1.025f
private const val DEFAULT_GLASS_BRIGHTNESS = 1.008f
private const val DEFAULT_EDGE_HIGHLIGHT = 0.035f

internal const val KAWASE_THREE_LEVEL_RADIUS_PX = 36f
internal const val KAWASE_MINIMUM_SUPPORT_PX = 12f
internal const val KAWASE_SUPPORT_MULTIPLIER = 1.35f
internal const val KAWASE_MAX_LEVEL_TAP_SUPPORT_PX = 2.75f
internal const val KAWASE_MIN_TAP_OFFSET_PX = 0.85f
internal const val KAWASE_MAX_TAP_OFFSET_PX = 2.65f
internal const val KAWASE_MAX_LEVELS = 3
private val KAWASE_LEVEL_ANGLES = floatArrayOf(0.19634955f, 0.5890486f, 0.9817477f, 1.3744467f)

private val KAWASE_RESAMPLE_SHADER = """
    uniform shader content;
    uniform float uOffset;
    uniform float2 uAxisX;
    uniform float2 uAxisY;
    uniform float uCenterWeight;
    uniform float uCardinalWeight;
    uniform float uDiagonalWeight;

    half4 main(float2 coord) {
        float2 dx = uAxisX * uOffset;
        float2 dy = uAxisY * uOffset;
        half4 sum = content.eval(coord) * uCenterWeight;
        sum += content.eval(coord + dx) * uCardinalWeight;
        sum += content.eval(coord - dx) * uCardinalWeight;
        sum += content.eval(coord + dy) * uCardinalWeight;
        sum += content.eval(coord - dy) * uCardinalWeight;
        sum += content.eval(coord + dx + dy) * uDiagonalWeight;
        sum += content.eval(coord + dx - dy) * uDiagonalWeight;
        sum += content.eval(coord - dx + dy) * uDiagonalWeight;
        sum += content.eval(coord - dx - dy) * uDiagonalWeight;
        float weight = uCenterWeight + 4.0 * uCardinalWeight + 4.0 * uDiagonalWeight;
        return sum / weight;
    }
""".trimIndent()

private val KAWASE_COMPOSITE_SHADER = """
    uniform shader content;
    uniform float uOffset;
    uniform float2 uAxisX;
    uniform float2 uAxisY;
    uniform float2 uSize;
    uniform float uPanelStart;
    uniform float uPanelEnd;
    uniform float uCorner;
    uniform float uMerge;
    uniform float uSoftness;
    uniform float uEdge;
    uniform float4 uTint;
    uniform float uSaturation;
    uniform float uContrast;
    uniform float uBrightness;
    uniform float uEdgeHighlight;

    float smoother(float value) {
        float x = saturate(value);
        return x * x * x * (x * (x * 6.0 - 15.0) + 10.0);
    }

    half4 tent(float2 coord) {
        float2 dx = uAxisX * uOffset;
        float2 dy = uAxisY * uOffset;
        half4 sum = content.eval(coord) * 2.5;
        sum += content.eval(coord + dx) * 1.35;
        sum += content.eval(coord - dx) * 1.35;
        sum += content.eval(coord + dy) * 1.35;
        sum += content.eval(coord - dy) * 1.35;
        sum += content.eval(coord + dx + dy) * 0.75;
        sum += content.eval(coord + dx - dy) * 0.75;
        sum += content.eval(coord - dx + dy) * 0.75;
        sum += content.eval(coord - dx - dy) * 0.75;
        return sum / 10.9;
    }

    float roundedPanelMask(float2 coord) {
        if (coord.y < uPanelStart || coord.y > uPanelEnd) return 0.0;
        float extent = uPanelEnd - uPanelStart;
        float radius = clamp(uCorner, 0.0, min(uSize.x * 0.5, extent * 0.5));
        if (radius < 0.5) return 1.0;
        if (uEdge < 0.5) {
            if (coord.y <= uPanelEnd - radius) return 1.0;
            if (coord.x < radius) {
                float d = length(coord - float2(radius, uPanelEnd - radius));
                return 1.0 - smoothstep(radius - 0.8, radius + 0.8, d);
            }
            if (coord.x > uSize.x - radius) {
                float d = length(coord - float2(uSize.x - radius, uPanelEnd - radius));
                return 1.0 - smoothstep(radius - 0.8, radius + 0.8, d);
            }
        } else {
            if (coord.y >= uPanelStart + radius) return 1.0;
            if (coord.x < radius) {
                float d = length(coord - float2(radius, uPanelStart + radius));
                return 1.0 - smoothstep(radius - 0.8, radius + 0.8, d);
            }
            if (coord.x > uSize.x - radius) {
                float d = length(coord - float2(uSize.x - radius, uPanelStart + radius));
                return 1.0 - smoothstep(radius - 0.8, radius + 0.8, d);
            }
        }
        return 1.0;
    }

    half4 main(float2 coord) {
        float mask = roundedPanelMask(coord);
        if (mask <= 0.0) return half4(0.0);

        float edgeDistance = uEdge < 0.5 ? (uPanelEnd - coord.y) : (coord.y - uPanelStart);
        float feather = smoother(edgeDistance / max(uMerge, 1.0));
        float panelAlpha = mask * mix(1.0, feather, uSoftness);
        if (panelAlpha <= 0.001) return half4(0.0);

        float3 rgb = float3(tent(coord).rgb);
        float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
        rgb = mix(float3(luma), rgb, uSaturation);
        rgb = (rgb - 0.5) * uContrast + 0.5;
        rgb *= uBrightness;
        rgb = mix(rgb, uTint.rgb, saturate(uTint.a));

        float highlightBand = (1.0 - smoothstep(2.0, 5.0, edgeDistance)) * smoothstep(0.2, 1.2, edgeDistance);
        rgb += highlightBand * uEdgeHighlight;
        rgb = clamp(rgb, 0.0, 1.0);
        return half4(rgb * panelAlpha, panelAlpha);
    }
""".trimIndent()
