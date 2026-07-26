package app.arbor.chat.ui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test fun progressIsClampedAndMonotonic() {
        val values = listOf(-1f, 0f, .25f, .5f, .75f, 1f, 2f).map(::arborBlurProgress)
        assertEquals(values.first(), values[1], 0f)
        assertEquals(values[values.lastIndex - 1], values.last(), 0f)
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test fun endpointsAndExistingSliderSemanticsRemainStable() {
        assertEquals(0f, arborBlurProgress(0f), .0001f)
        assertEquals(1f, arborBlurProgress(1f), .0001f)
        assertEquals(0f, calculateBlurRadiusDp(0f), .0001f)
        assertEquals(28f, calculateBlurRadiusDp(.5f), .0001f)
        assertEquals(56f, calculateBlurRadiusDp(1f), .0001f)
        assertEquals(0f, calculateMergeDistanceDp(0f), .0001f)
        assertEquals(34f, calculateMergeDistanceDp(.5f), .0001f)
        assertEquals(68f, calculateMergeDistanceDp(1f), .0001f)
    }

    @Test fun lowEdgeSoftnessRampsContinuouslyWithoutANarrowFeatherSpike() {
        assertEquals(0f, edgeSoftnessActivation(0f), .0001f)
        assertTrue(edgeSoftnessActivation(.02f) > 0f)
        assertTrue(edgeSoftnessActivation(.02f) < edgeSoftnessActivation(.06f))
        assertTrue(edgeSoftnessActivation(.06f) < edgeSoftnessActivation(.12f))
        assertEquals(1f, edgeSoftnessActivation(.12f), .0001f)
        assertEquals(0f, resolveFeatherDistancePx(0f, 0f, 4f, 100f), .0001f)
        assertEquals(4f, resolveFeatherDistancePx(1f, .02f, 4f, 100f), .0001f)
    }

    @Test fun panelGeometryStillUsesMeasuredTopAndBottomRanges() {
        assertEquals(ArborPanelRange(0f, 128f), resolveTopPanelRange(900f, 128f))
        assertEquals(
            ArborPanelRange(666f, 786f),
            resolveBottomPanelRange(24f, 690f, 810f, 900f, 208f),
        )
        assertEquals(
            ArborPanelRange(692f, 900f),
            resolveBottomPanelRange(0f, Float.NaN, Float.NaN, 900f, 208f),
        )
    }

    @Test fun blurIsBypassedForInvisibleOrInvalidPanels() {
        assertNull(resolveKawasePanelPlan(ArborPanelRange(0f, 100f), 1080f, 2340f, 0f))
        assertNull(resolveKawasePanelPlan(ArborPanelRange(10f, 10f), 1080f, 2340f, 40f))
        assertNull(resolveKawasePanelPlan(ArborPanelRange(0f, 100f), 0f, 2340f, 40f))
        assertNotNull(resolveKawasePanelPlan(ArborPanelRange(0f, 100f), 1080f, 2340f, 40f))
    }

    @Test fun captureIncludesTheCompleteFixedOverscanMargin() {
        val range = ArborPanelRange(500f, 900f)
        val plan = resolveKawasePanelPlan(range, 1080f, 2340f, 60f)!!
        assertEquals(range.startPx - plan.capture.supportPx, plan.capture.sourceStartPx, .001f)
        assertEquals(range.endPx + plan.capture.supportPx, plan.capture.sourceEndPx, .001f)
        assertTrue(plan.capture.supportPx >= 60f * KAWASE_SUPPORT_MULTIPLIER)
    }

    @Test fun everyPyramidLevelUsesOneConsistentSourceExtent() {
        val plan = resolveKawasePanelPlan(ArborPanelRange(400f, 900f), 1080f, 2340f, 80f)!!
        for (level in 0..plan.levelCount) {
            assertSame(plan.capture, plan.sourceExtentAtLevel(level))
        }
        assertTrue(kotlin.math.abs(plan.levelSize(0).width / 2 - plan.levelSize(1).width) <= 1)
        assertTrue(kotlin.math.abs(plan.levelSize(1).width / 2 - plan.levelSize(2).width) <= 1)
        assertEquals(3, plan.levelCount)
    }

    @Test fun rendererRecordsSourceOnceAndReplaysItForBothPanels() {
        val source = blurSource()
        assertEquals(1, Regex("sourceLayer\\.record\\(").findAll(source).count())
        assertTrue(source.contains("recordKawasePanelChain(sourceLayer, topLayers, plan)"))
        assertTrue(source.contains("recordKawasePanelChain(sourceLayer, bottomLayers, plan)"))
        assertTrue(source.contains("drawLayer(sourceLayer)"))
        assertEquals(1, Regex("this@drawWithContent\\.drawContent\\(\\)").findAll(source).count())
    }

    @Test fun visibleCropOccursOnlyAfterTheCompleteBlurChain() {
        val drawBlock = blurSource().substringAfter("measured.drawWithContent {").substringBefore("\n    }\n}")
        val topChain = drawBlock.indexOf("recordKawasePanelChain(sourceLayer, topLayers, plan)")
        val topCrop = drawBlock.indexOf("clipPath(topGeometry.path)")
        val bottomChain = drawBlock.indexOf("recordKawasePanelChain(sourceLayer, bottomLayers, plan)")
        val bottomCrop = drawBlock.indexOf("clipPath(bottomGeometry.path)")
        assertTrue(topChain >= 0 && topCrop > topChain)
        assertTrue(bottomChain >= 0 && bottomCrop > bottomChain)
    }

    @Test fun progressivePerPassCroppingAndOldFullResolutionKernelAreGone() {
        val source = blurSource()
        assertFalse(source.contains("resolveAdaptiveBlurPassCaptures"))
        assertFalse(source.contains("recordAdaptivePassChain"))
        assertFalse(source.contains("adaptiveGlassBlur"))
        assertFalse(source.contains("BLUR_MAX_SAMPLES_PER_PASS"))
        assertFalse(source.contains("73.0"))
        assertTrue(source.contains("KAWASE_RESAMPLE_SHADER"))
        assertTrue(source.contains("KAWASE_COMPOSITE_SHADER"))
    }

    @Test fun scrollAndMotionCannotSelectALowerQualityPath() {
        val source = blurSource()
        listOf("scrollVelocity", "isScrolling", "isFlinging", "thermalState", "animationState", "qualityScale").forEach {
            assertFalse("Motion-dependent quality token found: $it", source.contains(it))
        }
        assertTrue(source.contains("resolveKawaseLevelCount(radiusPx)"))
    }

    @Test fun effectsAreRememberedAndNotConstructedInsideThePerFrameDrawBlock() {
        val source = blurSource()
        assertTrue(source.contains("val topEffects = remember(topPlan, topVisual)"))
        assertTrue(source.contains("val bottomEffects = remember(bottomPlan, bottomVisual)"))
        val drawBlock = source.substringAfter("measured.drawWithContent {").substringBefore("\n    }\n}")
        assertFalse(drawBlock.contains("RuntimeShader("))
        assertFalse(drawBlock.contains("RenderEffect.create"))
        assertFalse(drawBlock.contains("Path()"))
        assertFalse(drawBlock.contains("floatArrayOf("))
        assertFalse(drawBlock.contains("listOf("))
    }

    @Test fun typicalS23PlusPanelsProcessFarFewerPixelsThanThreeFullScreens() {
        val width = 1_080f
        val height = 2_340f
        val radius = 118f
        val top = resolveKawasePanelPlan(ArborPanelRange(0f, 384f), width, height, radius)!!
        val bottom = resolveKawasePanelPlan(ArborPanelRange(1_716f, height), width, height, radius)!!
        val panelPixels = top.processedPixels() + bottom.processedPixels()
        val oldFullScreenPixels = width.toLong() * height.toLong() * 3L
        assertTrue("$panelPixels should be materially below $oldFullScreenPixels", panelPixels < oldFullScreenPixels * 0.45)
    }

    @Test fun visualConfigurationIsDeterministicForIdenticalGeometryAndSettings() {
        val a = resolveKawasePanelPlan(ArborPanelRange(200f, 700f), 1080f, 2340f, 72f)
        val b = resolveKawasePanelPlan(ArborPanelRange(200f, 700f), 1080f, 2340f, 72f)
        assertEquals(a, b)
        assertEquals(a?.processedPixels(), b?.processedPixels())
    }

    @Test fun overlayOpacityScalesTheDesignedTintInsteadOfFlatteningItToOpaqueGray() {
        val tint = Color(0.2f, 0.4f, 0.6f, 0.5f)
        val hidden = applyOverlayOpacity(tint, -1f)
        val half = applyOverlayOpacity(tint, 0.5f)
        val full = applyOverlayOpacity(tint, 2f)
        assertEquals(0f, hidden.alpha, .0001f)
        assertEquals(0.25f, half.alpha, .002f)
        assertEquals(0.5f, full.alpha, .002f)
        assertEquals(tint.red, half.red, .0001f)
        assertEquals(tint.green, half.green, .0001f)
        assertEquals(tint.blue, half.blue, .0001f)
    }

    @Test fun radiusQuantizationSuppressesSubPixelStateChurn() {
        assertEquals(0f, quantizeBlurRadiusDp(-2f), 0f)
        assertEquals(0f, quantizeBlurRadiusDp(.12f), 0f)
        assertEquals(.25f, quantizeBlurRadiusDp(.13f), 0f)
        assertEquals(18.5f, quantizeBlurRadiusDp(18.49f), 0f)
    }

    private fun blurSource(): String = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
}
