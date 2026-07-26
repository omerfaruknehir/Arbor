package app.arbor.chat.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.max
import kotlin.math.roundToInt

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
    val minimumFeatherPx = MINIMUM_FEATHER_DISTANCE_DP * density
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
            minimumFeatherPx = minimumFeatherPx,
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
            minimumFeatherPx = minimumFeatherPx,
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

    val topEffects = remember(topPlan, topVisual, topGeometry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildStablePanelEffects(topPlan, topVisual, topGeometry)
        } else null
    }
    val bottomEffects = remember(bottomPlan, bottomVisual, bottomGeometry) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            buildStablePanelEffects(bottomPlan, bottomVisual, bottomGeometry)
        } else null
    }
    SideEffect {
        topLayers.applyEffects(topEffects)
        bottomLayers.applyEffects(bottomEffects)
    }

    measured.drawWithContent {
        val topBlurActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && topPlan != null && topEffects?.deepBlur != null
        val bottomBlurActive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && bottomPlan != null && bottomEffects?.deepBlur != null
        val blurActive = topBlurActive || bottomBlurActive
        val topPanelVisible = topBlurActive || topVisual.tint.alpha > 0f
        val bottomPanelVisible = bottomBlurActive || bottomVisual.tint.alpha > 0f
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

            if (topBlurActive) {
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
            if (bottomBlurActive) {
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

        if (topPanelVisible) {
            val started = if (profilerActive) System.nanoTime() else 0L
            recordStablePanelComposite(
                layers = topLayers,
                plan = if (topBlurActive) topPlan else null,
                geometry = topGeometry,
                visual = topVisual,
            )
            translate(0f, topGeometry.startPx) { drawLayer(topLayers.panelComposite) }
            if (profilerActive) {
                blurCpuNanos += System.nanoTime() - started
                layerReplays += 2
            }
        }
        if (bottomPanelVisible) {
            val started = if (profilerActive) System.nanoTime() else 0L
            recordStablePanelComposite(
                layers = bottomLayers,
                plan = if (bottomBlurActive) bottomPlan else null,
                geometry = bottomGeometry,
                visual = bottomVisual,
            )
            translate(0f, bottomGeometry.startPx) { drawLayer(bottomLayers.panelComposite) }
            if (profilerActive) {
                blurCpuNanos += System.nanoTime() - started
                layerReplays += 2
            }
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
    panelComposite = rememberGraphicsLayer(),
)

private data class KawaseLayerSet(
    val capture: GraphicsLayer,
    val down1: GraphicsLayer,
    val down2: GraphicsLayer,
    val down3: GraphicsLayer,
    val up2: GraphicsLayer,
    val up1: GraphicsLayer,
    val finalFull: GraphicsLayer,
    val panelComposite: GraphicsLayer,
) {
    fun applyEffects(effects: StablePanelEffects?) {
        capture.renderEffect = null
        down1.renderEffect = null
        down2.renderEffect = null
        down3.renderEffect = effects?.deepBlur
        up2.renderEffect = null
        up1.renderEffect = null
        finalFull.renderEffect = effects?.colorAdjust
        finalFull.alpha = effects?.blurMix ?: 1f
        panelComposite.renderEffect = effects?.edgeSoftness
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
    visibleSupportPx: Float = 0f,
): KawasePanelPlan? {
    val sourceWidth = sourceWidthPx.coerceAtLeast(0f)
    val sourceHeight = sourceHeightPx.coerceAtLeast(0f)
    if (sourceWidth <= 0f || sourceHeight <= 0f || panelRange.extentPx <= 0f || radiusPx < MIN_VISIBLE_RADIUS_PX) {
        return null
    }
    val levelCount = resolveKawaseLevelCount(radiusPx)
    val support = calculateKawaseSupportPx(radiusPx, levelCount) + visibleSupportPx.coerceAtLeast(0f)
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

internal fun resolveKawaseLevelCount(@Suppress("UNUSED_PARAMETER") radiusPx: Float): Int = KAWASE_MAX_LEVELS

internal fun calculateKawaseSupportPx(radiusPx: Float, levelCount: Int): Float {
    val reconstructionSupport = (1 shl levelCount) * KAWASE_MAX_LEVEL_TAP_SUPPORT_PX
    return ceil(max(KAWASE_MINIMUM_SUPPORT_PX, radiusPx.coerceAtLeast(0f) * KAWASE_SUPPORT_MULTIPLIER + reconstructionSupport))
}

internal fun calculateKawaseTapOffsetPx(radiusPx: Float, levelCount: Int): Float {
    val divisor = if (levelCount >= 3) 72f else 58f
    return (radiusPx.coerceAtLeast(0f) / divisor)
        .coerceIn(KAWASE_MIN_TAP_OFFSET_PX, KAWASE_MAX_TAP_OFFSET_PX)
}

internal fun resolveBlurContribution(radiusPx: Float): Float =
    arborBlurProgress(radiusPx.coerceAtLeast(0f) / BLUR_FULL_CONTRIBUTION_RADIUS_PX)

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
    val localBodyPath: Path,
    val widthPx: Float,
    val startPx: Float,
    val endPx: Float,
    val bodyStartPx: Float,
    val bodyEndPx: Float,
    val cornerRadiusPx: Float,
    val featherDistancePx: Float,
    val edge: ArborBlurEdge,
) {
    val extentPx: Float get() = (endPx - startPx).coerceAtLeast(0f)
    val bodyStartLocalPx: Float get() = bodyStartPx - startPx
    val bodyEndLocalPx: Float get() = bodyEndPx - startPx
    val bodyExtentPx: Float get() = (bodyEndPx - bodyStartPx).coerceAtLeast(0f)
    val layerSize: IntSize get() = IntSize(
        width = ceil(widthPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1),
        height = ceil(extentPx.coerceAtLeast(1f)).toInt().coerceAtLeast(1),
    )
}

private fun buildPanelGeometry(widthPx: Float, heightPx: Float, visual: GlassVisualConfig): PanelGeometry {
    val nominalStart = visual.range.startPx.coerceIn(0f, heightPx)
    val nominalEnd = visual.range.endPx.coerceIn(nominalStart, heightPx)
    val nominalExtent = nominalEnd - nominalStart
    val featherSpan = resolveFeatherDistancePx(
        requestedDistancePx = visual.mergeDistancePx,
        softness = visual.softness,
        minimumFeatherPx = visual.minimumFeatherPx,
        maximumDistancePx = nominalExtent,
    )
    val bounds = resolveSymmetricFeatherBounds(
        edge = visual.edge,
        nominalStartPx = nominalStart,
        nominalEndPx = nominalEnd,
        featherSpanPx = featherSpan,
        sourceHeightPx = heightPx,
    )
    val nominalRadius = visual.cornerRadiusPx.coerceIn(0f, minOf(widthPx / 2f, nominalExtent / 2f))
    val bodyStartLocal = bounds.bodyStartPx - bounds.drawStartPx
    val bodyEndLocal = bounds.bodyEndPx - bounds.drawStartPx
    val localBodyPath = Path().apply {
        when (visual.edge) {
            ArborBlurEdge.TOP -> addRoundRect(
                RoundRect(
                    left = 0f,
                    top = bodyStartLocal,
                    right = widthPx,
                    bottom = bodyEndLocal,
                    topLeftCornerRadius = CornerRadius.Zero,
                    topRightCornerRadius = CornerRadius.Zero,
                    bottomRightCornerRadius = CornerRadius(nominalRadius, nominalRadius),
                    bottomLeftCornerRadius = CornerRadius(nominalRadius, nominalRadius),
                ),
            )
            ArborBlurEdge.BOTTOM -> addRoundRect(
                RoundRect(
                    left = 0f,
                    top = bodyStartLocal,
                    right = widthPx,
                    bottom = bodyEndLocal,
                    topLeftCornerRadius = CornerRadius(nominalRadius, nominalRadius),
                    topRightCornerRadius = CornerRadius(nominalRadius, nominalRadius),
                    bottomRightCornerRadius = CornerRadius.Zero,
                    bottomLeftCornerRadius = CornerRadius.Zero,
                ),
            )
        }
    }
    return PanelGeometry(
        localBodyPath = localBodyPath,
        widthPx = widthPx,
        startPx = bounds.drawStartPx,
        endPx = bounds.drawEndPx,
        bodyStartPx = bounds.bodyStartPx,
        bodyEndPx = bounds.bodyEndPx,
        cornerRadiusPx = nominalRadius,
        featherDistancePx = featherSpan,
        edge = visual.edge,
    )
}

/**
 * Blur and tint are recorded into one premultiplied panel layer before edge
 * softness is applied. This guarantees identical geometry and prevents a
 * transparent blur sample from becoming opaque black during composition.
 */
private fun DrawScope.recordStablePanelComposite(
    layers: KawaseLayerSet,
    plan: KawasePanelPlan?,
    geometry: PanelGeometry,
    visual: GlassVisualConfig,
) {
    if (geometry.extentPx <= 0f) return
    layers.panelComposite.record(size = geometry.layerSize) {
        clipPath(geometry.localBodyPath) {
            if (plan != null) {
                translate(0f, plan.capture.sourceStartPx - geometry.startPx) {
                    drawLayer(layers.finalFull)
                }
            }
            if (visual.tint.alpha > 0f && geometry.bodyExtentPx > 0f) {
                drawRect(
                    color = visual.tint,
                    topLeft = Offset(0f, geometry.bodyStartLocalPx),
                    size = androidx.compose.ui.geometry.Size(geometry.widthPx, geometry.bodyExtentPx),
                )
            }
            if (visual.edgeHighlight > 0f) {
                drawPath(
                    path = geometry.localBodyPath,
                    color = Color.White.copy(alpha = visual.edgeHighlight),
                    style = Stroke(width = 1f),
                )
            }
        }
    }
}

private data class StablePanelEffects(
    val deepBlur: androidx.compose.ui.graphics.RenderEffect?,
    val colorAdjust: androidx.compose.ui.graphics.RenderEffect?,
    val edgeSoftness: androidx.compose.ui.graphics.RenderEffect?,
    val blurMix: Float,
)

@RequiresApi(Build.VERSION_CODES.S)
private fun buildStablePanelEffects(
    plan: KawasePanelPlan?,
    visual: GlassVisualConfig,
    geometry: PanelGeometry,
): StablePanelEffects {
    val deepRadius = plan?.let { calculateDeepBlurRadiusPx(it.radiusPx, it.levelCount) } ?: 0f
    val deepBlur = if (deepRadius > 0.01f) {
        RenderEffect.createBlurEffect(deepRadius, deepRadius, Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else null
    val colorAdjust = if (plan != null) {
        RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(
                buildGlassColorMatrix(
                    saturation = visual.saturation,
                    contrast = visual.contrast,
                    brightness = visual.brightness,
                ),
            ),
        ).asComposeRenderEffect()
    } else null
    val edgeRadius = resolveEdgeBlurRadiusPx(geometry.featherDistancePx)
    val edgeSoftness = if (edgeRadius > 0.01f) {
        RenderEffect.createBlurEffect(edgeRadius, edgeRadius, Shader.TileMode.DECAL).asComposeRenderEffect()
    } else null
    ArborRenderProfiler.recordBlurEffectBuild(
        (if (deepBlur != null) 1 else 0) +
            (if (colorAdjust != null) 1 else 0) +
            (if (edgeSoftness != null) 1 else 0),
    )
    return StablePanelEffects(
        deepBlur = deepBlur,
        colorAdjust = colorAdjust,
        edgeSoftness = edgeSoftness,
        blurMix = plan?.let { resolveBlurContribution(it.radiusPx) } ?: 0f,
    )
}

internal fun calculateDeepBlurRadiusPx(radiusPx: Float, levelCount: Int): Float {
    require(levelCount in 1..KAWASE_MAX_LEVELS)
    return radiusPx.coerceAtLeast(0f) / (1 shl levelCount).toFloat()
}

/** A Gaussian's visible +/-3 sigma span equals the full edge-softness distance. */
internal fun resolveEdgeBlurRadiusPx(featherSpanPx: Float): Float = featherSpanPx.coerceAtLeast(0f) / 6f

internal fun buildGlassColorMatrix(
    saturation: Float,
    contrast: Float,
    brightness: Float,
): ColorMatrix {
    val saturationMatrix = ColorMatrix().apply { setSaturation(saturation.coerceIn(0.75f, 1.35f)) }
    val normalizedContrast = contrast.coerceIn(0.85f, 1.20f)
    val normalizedBrightness = brightness.coerceIn(0.85f, 1.15f)
    val scale = normalizedContrast * normalizedBrightness
    val translate = (0.5f - 0.5f * normalizedContrast) * 255f * normalizedBrightness
    val adjustment = ColorMatrix(
        floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    saturationMatrix.postConcat(adjustment)
    return saturationMatrix
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
    arborBlurProgress(edgeSoftness.coerceIn(0f, 1f))

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

internal fun resolveSymmetricFeatherHalfSpanPx(
    requestedDistancePx: Float,
    softness: Float,
    minimumFeatherPx: Float,
    maximumDistancePx: Float,
): Float = resolveFeatherDistancePx(
    requestedDistancePx = requestedDistancePx,
    softness = softness,
    minimumFeatherPx = minimumFeatherPx,
    maximumDistancePx = maximumDistancePx,
) * 0.5f

internal data class SymmetricFeatherBounds(
    val drawStartPx: Float,
    val drawEndPx: Float,
    val bodyStartPx: Float,
    val bodyEndPx: Float,
    val halfSpanPx: Float,
)

internal fun resolveSymmetricFeatherBounds(
    edge: ArborBlurEdge,
    nominalStartPx: Float,
    nominalEndPx: Float,
    featherSpanPx: Float,
    sourceHeightPx: Float,
): SymmetricFeatherBounds {
    val sourceHeight = sourceHeightPx.coerceAtLeast(0f)
    val start = nominalStartPx.coerceIn(0f, sourceHeight)
    val end = nominalEndPx.coerceIn(start, sourceHeight)
    val half = (featherSpanPx.coerceAtLeast(0f) * 0.5f).coerceAtMost((end - start) * 0.5f)
    return when (edge) {
        ArborBlurEdge.TOP -> SymmetricFeatherBounds(
            drawStartPx = start,
            drawEndPx = (end + half).coerceAtMost(sourceHeight),
            bodyStartPx = start,
            bodyEndPx = end,
            halfSpanPx = half,
        )
        ArborBlurEdge.BOTTOM -> SymmetricFeatherBounds(
            drawStartPx = (start - half).coerceAtLeast(0f),
            drawEndPx = end,
            bodyStartPx = start,
            bodyEndPx = end,
            halfSpanPx = half,
        )
    }
}

/** Registers a chrome panel. Blur and tint share one signed-distance panel geometry. */
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

/** Overlay opacity is absolute: 0% is transparent and 100% is fully opaque. */
internal fun applyOverlayOpacity(tint: Color, opacity: Float): Color =
    tint.copy(alpha = opacity.coerceIn(0f, 1f))

internal fun quantizeBlurRadiusDp(radiusDp: Float): Float = radiusDp.coerceAtLeast(0f)

private const val MIN_VISIBLE_RADIUS_PX = 0.0001f
private const val BLUR_FULL_CONTRIBUTION_RADIUS_PX = 12f
private const val DEFAULT_MAX_RADIUS_DP = 56f
private const val DEFAULT_PANEL_CORNER_RADIUS_DP = 28f
private const val DEFAULT_MERGE_DISTANCE_DP = 34f
private const val MAXIMUM_MERGE_DISTANCE_DP = 68f
private const val MINIMUM_FEATHER_DISTANCE_DP = 4f
private const val DEFAULT_TOP_FADE_DP = 128f
private const val DEFAULT_BOTTOM_FADE_DP = 208f
private const val DEFAULT_GLASS_SATURATION = 1.10f
private const val DEFAULT_GLASS_CONTRAST = 1.025f
private const val DEFAULT_GLASS_BRIGHTNESS = 1.008f
private const val DEFAULT_EDGE_HIGHLIGHT = 0.035f

internal const val KAWASE_MINIMUM_SUPPORT_PX = 12f
internal const val KAWASE_SUPPORT_MULTIPLIER = 1.35f
internal const val KAWASE_MAX_LEVEL_TAP_SUPPORT_PX = 2.75f
internal const val KAWASE_MIN_TAP_OFFSET_PX = 0f
internal const val KAWASE_MAX_TAP_OFFSET_PX = 2.65f
internal const val KAWASE_MAX_LEVELS = 3

