package app.arbor.chat.ui

import androidx.compose.ui.graphics.Color
import app.arbor.chat.settings.CHROME_EDGE_SOFTNESS_ZERO_SNAP_THRESHOLD
import app.arbor.chat.settings.effectiveChromeEdgeSoftness
import app.arbor.chat.settings.snapChromeEdgeSoftness
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
        assertTrue(calculateMergeDistanceDp(.5f) > 0f)
        assertTrue(calculateMergeDistanceDp(.5f) < 68f)
        assertEquals(68f, calculateMergeDistanceDp(1f), .0001f)
    }

    @Test fun edgeSoftnessHasAnEasyExactZeroSnapZoneAndFullRemainingRange() {
        assertEquals(.06f, CHROME_EDGE_SOFTNESS_ZERO_SNAP_THRESHOLD, .0001f)
        assertEquals(0f, snapChromeEdgeSoftness(0f), .0001f)
        assertEquals(0f, snapChromeEdgeSoftness(.059f), .0001f)
        assertEquals(0f, snapChromeEdgeSoftness(.06f), .0001f)
        assertTrue(snapChromeEdgeSoftness(.061f) > 0f)
        assertEquals(0f, effectiveChromeEdgeSoftness(.06f), .0001f)
        assertTrue(effectiveChromeEdgeSoftness(.061f) > 0f)
        assertEquals(1f, effectiveChromeEdgeSoftness(1f), .0001f)
        assertEquals(0f, edgeSoftnessActivation(.06f), .0001f)
        assertTrue(edgeSoftnessActivation(.12f) > 0f)
        assertTrue(edgeSoftnessActivation(.12f) < edgeSoftnessActivation(.5f))
        assertTrue(edgeSoftnessActivation(.5f) < edgeSoftnessActivation(.88f))
        assertEquals(1f, edgeSoftnessActivation(1f), .0001f)
        assertEquals(0f, resolveFeatherDistancePx(1f, .02f, 4f, 100f), .0001f)
        assertTrue(resolveFeatherDistancePx(1f, .061f, 4f, 100f) > 0f)
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

    @Test fun captureSupportCanIncludeAnExplicitOutsideMargin() {
        val radius = 60f
        val blurOnly = calculateKawaseSupportPx(radius, resolveKawaseLevelCount(radius))
        val plan = resolveKawasePanelPlan(
            panelRange = ArborPanelRange(500f, 900f),
            sourceWidthPx = 1080f,
            sourceHeightPx = 2340f,
            radiusPx = radius,
            visibleSupportPx = 34f,
        )!!
        assertEquals(blurOnly + 34f, plan.capture.supportPx, .001f)
        val source = blurSource()
        assertTrue(source.contains("visibleSupportPx = topGeometry.outwardFeatherPx"))
        assertTrue(source.contains("visibleSupportPx = bottomGeometry.outwardFeatherPx"))
    }

    @Test fun everyPyramidLevelUsesOneConsistentSourceExtent() {
        val plan = resolveKawasePanelPlan(ArborPanelRange(400f, 900f), 1080f, 2340f, 80f)!!
        for (level in 0..plan.levelCount) assertSame(plan.capture, plan.sourceExtentAtLevel(level))
        assertTrue(kotlin.math.abs(plan.levelSize(0).width / 2 - plan.levelSize(1).width) <= 1)
        assertEquals(1, plan.levelCount)
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
        val source = blurSource()
        val topChain = source.indexOf("recordKawasePanelChain(sourceLayer, topLayers, plan)")
        val topComposite = source.indexOf("layers = topLayers,", topChain)
        val bottomChain = source.indexOf("recordKawasePanelChain(sourceLayer, bottomLayers, plan)")
        val bottomComposite = source.indexOf("layers = bottomLayers,", bottomChain)
        assertTrue(topChain >= 0 && topComposite > topChain)
        assertTrue(bottomChain >= 0 && bottomComposite > bottomChain)
        assertTrue(source.contains("clipPath(geometry.localBodyPath)"))
    }

    @Test fun progressiveCroppingAndArtifactProneRuntimeShadersAreGone() {
        val source = blurSource()
        assertFalse(source.contains("resolveAdaptiveBlurPassCaptures"))
        assertFalse(source.contains("recordAdaptivePassChain"))
        assertFalse(source.contains("RuntimeShader"))
        assertFalse(source.contains("KAWASE_RESAMPLE_SHADER"))
        assertFalse(source.contains("KAWASE_COMPOSITE_SHADER"))
        assertTrue(source.contains("Shader.TileMode.CLAMP"))
        assertFalse(source.contains("Shader.TileMode.DECAL"))
        assertTrue(source.contains("BlendMode.DstIn"))
    }

    @Test fun scrollAndMotionCannotSelectALowerQualityPath() {
        val source = blurSource()
        listOf("scrollVelocity", "isScrolling", "isFlinging", "thermalState", "animationState", "qualityScale").forEach {
            assertFalse("Motion-dependent quality token found: $it", source.contains(it))
        }
        assertTrue(source.contains("resolveKawaseLevelCount(radiusPx)"))
        assertEquals(1, resolveKawaseLevelCount(1f))
        assertEquals(1, resolveKawaseLevelCount(200f))
    }

    @Test fun effectsAreRememberedAndNotConstructedInsideThePerFrameDrawBlock() {
        val source = blurSource()
        assertTrue(source.contains("val topEffects = remember(topPlan, topVisual, topGeometry)"))
        assertTrue(source.contains("val bottomEffects = remember(bottomPlan, bottomVisual, bottomGeometry)"))
        val drawBlock = source.substringAfter("measured.drawWithContent {").substringBefore("if (profilerActive && blurActive)")
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

    @Test fun edgeSmoothingKeepsTheFullCenteredBandWithoutErodingThePanelBody() {
        assertEquals(34f, resolveSymmetricFeatherHalfSpanPx(68f, 1f, 4f, 200f), .0001f)
        val top = resolveSymmetricFeatherBounds(ArborBlurEdge.TOP, 0f, 500f, 68f, 1_000f)
        assertEquals(500f, top.bodyEndPx, .0001f)
        assertEquals(534f, top.drawEndPx, .0001f)
        val bottom = resolveSymmetricFeatherBounds(ArborBlurEdge.BOTTOM, 500f, 1_000f, 68f, 1_000f)
        assertEquals(466f, bottom.drawStartPx, .0001f)
        assertEquals(500f, bottom.bodyStartPx, .0001f)
    }

    @Test fun edgeSoftnessMasksOnlyTheInnerPanelBoundary() {
        val source = blurSource()
        assertTrue(source.contains("layers.panelComposite.record(size = geometry.layerSize)"))
        assertTrue(source.contains("panelComposite.compositingStrategy = CompositingStrategy.Offscreen"))
        assertTrue(source.contains("drawRect(brush = mask, blendMode = BlendMode.DstIn)"))
        assertFalse(source.contains("panelComposite.renderEffect = effects?.edgeSoftness"))
        assertFalse(source.contains("Shader.TileMode.DECAL"))

        val top = resolveDirectionalFeatherMask(
            edge = ArborBlurEdge.TOP,
            bodyStartLocalPx = 0f,
            bodyEndLocalPx = 500f,
            halfSpanPx = 34f,
            layerExtentPx = 534f,
        )!!
        assertEquals(466f, top.startPx, .0001f)
        assertEquals(534f, top.endPx, .0001f)
        assertTrue(top.startsOpaque)
        assertTrue("The physical top edge must remain fully covered", top.startPx > 0f)

        val bottom = resolveDirectionalFeatherMask(
            edge = ArborBlurEdge.BOTTOM,
            bodyStartLocalPx = 34f,
            bodyEndLocalPx = 534f,
            halfSpanPx = 34f,
            layerExtentPx = 534f,
        )!!
        assertEquals(0f, bottom.startPx, .0001f)
        assertEquals(68f, bottom.endPx, .0001f)
        assertFalse(bottom.startsOpaque)
    }

    @Test fun exactZeroKeepsRoundedPanelsAndAnyNonzeroModeIsFlat() {
        assertEquals(28f, resolvePanelCornerRadiusPx(28f, 0f, 1080f, 128f), .0001f)
        assertEquals(28f, resolvePanelCornerRadiusPx(28f, .059f, 1080f, 128f), .0001f)
        assertEquals(0f, resolvePanelCornerRadiusPx(28f, .061f, 1080f, 128f), .0001f)
        assertEquals(0f, resolvePanelCornerRadiusPx(28f, 1f, 1080f, 128f), .0001f)
    }

    @Test fun visualConfigurationIsDeterministicForIdenticalGeometryAndSettings() {
        val a = resolveKawasePanelPlan(ArborPanelRange(200f, 700f), 1080f, 2340f, 72f)
        val b = resolveKawasePanelPlan(ArborPanelRange(200f, 700f), 1080f, 2340f, 72f)
        assertEquals(a, b)
        assertEquals(a?.processedPixels(), b?.processedPixels())
    }

    @Test fun overlayOpacityIsAbsoluteAndReachesFullyOpaqueAtOneHundredPercent() {
        val tint = Color(0.2f, 0.4f, 0.6f, 0.5f)
        val hidden = applyOverlayOpacity(tint, -1f)
        val half = applyOverlayOpacity(tint, 0.5f)
        val full = applyOverlayOpacity(tint, 2f)
        assertEquals(0f, hidden.alpha, .0001f)
        assertEquals(0.5f, half.alpha, .002f)
        assertEquals(1f, full.alpha, .002f)
        assertEquals(tint.red, half.red, .0001f)
        assertEquals(tint.green, half.green, .0001f)
        assertEquals(tint.blue, half.blue, .0001f)
        val source = blurSource()
        assertTrue(source.contains("color = visual.tint"))
        assertTrue(source.contains("clipPath(geometry.localBodyPath)"))
        assertFalse(source.contains("PANEL_TINT_SHADER"))
    }

    @Test fun tintCompositionIsIndependentOfBlurActivation() {
        val source = blurSource()
        assertTrue(source.contains("plan = if (topBlurActive) topPlan else null"))
        assertTrue(source.contains("plan = if (bottomBlurActive) bottomPlan else null"))
        assertTrue(source.contains("if (visual.tint.alpha > 0f)"))
        assertTrue(source.contains("val topPanelVisible = topBlurActive || topVisual.tint.alpha > 0f"))
        assertTrue(source.contains("val bottomPanelVisible = bottomBlurActive || bottomVisual.tint.alpha > 0f"))
    }

    @Test fun blurAndTintUseExactlyTheSamePremultipliedPanelGeometry() {
        val source = blurSource()
        assertEquals(1, Regex("panelComposite\\.record\\(").findAll(source).count())
        assertTrue(source.contains("clipPath(geometry.localBodyPath)"))
        assertTrue(source.contains("drawLayer(layers.finalFull)"))
        assertTrue(source.contains("drawRect(color = visual.tint)"))
        assertTrue(source.contains("Brush.verticalGradient"))
        assertTrue(source.contains("drawRect(brush = mask, blendMode = BlendMode.DstIn)"))
        assertFalse(source.contains("PANEL_SIGNED_DISTANCE_AGSL"))
    }

    @Test fun transparentSamplesRemainPremultipliedAndCannotBecomeOpaqueBlack() {
        val source = blurSource()
        assertFalse(source.contains("filtered.rgb / alpha"))
        assertFalse(source.contains("half4(0.0)"))
        assertFalse(source.contains("RuntimeShader"))
        assertTrue(source.contains("Shader.TileMode.CLAMP"))
        assertTrue(source.contains("Blur and tint are recorded into one premultiplied panel layer"))
    }

    @Test fun strongBlurUsesFourCascadedHalfResolutionPasses() {
        assertEquals(0f, calculateHalfBlurPassRadiusPx(0f), .0001f)
        assertEquals(16f, calculateHalfBlurPassRadiusPx(64f), .0001f)
        val source = blurSource()
        assertTrue(source.contains("halfBlurA.renderEffect = effects?.halfBlurPass"))
        assertTrue(source.contains("halfBlurB.renderEffect = effects?.halfBlurPass"))
        assertTrue(source.contains("halfBlurC.renderEffect = effects?.halfBlurPass"))
        assertTrue(source.contains("halfBlurD.renderEffect = effects?.halfBlurPass"))
        assertTrue(source.contains("drawLayer(layers.halfBlurC)"))
        assertFalse(source.contains("down2"))
        assertFalse(source.contains("quarterBlur"))
        assertEquals(1, resolveKawaseLevelCount(1f))
        assertEquals(1, resolveKawaseLevelCount(500f))
    }

    @Test fun blurRadiusAndTapOffsetRemainContinuousFromZero() {
        assertEquals(0f, quantizeBlurRadiusDp(-2f), 0f)
        assertEquals(.12f, quantizeBlurRadiusDp(.12f), 0f)
        assertEquals(.13f, quantizeBlurRadiusDp(.13f), 0f)
        assertEquals(18.49f, quantizeBlurRadiusDp(18.49f), 0f)
        assertEquals(0f, calculateKawaseTapOffsetPx(0f, 1), .0001f)
        assertTrue(calculateKawaseTapOffsetPx(.01f, 1) > 0f)
        assertTrue(calculateKawaseTapOffsetPx(.01f, 1) < calculateKawaseTapOffsetPx(1f, 1))
        assertEquals(0f, resolveBlurContribution(0f), .0001f)
        assertTrue(resolveBlurContribution(.01f) > 0f)
        assertTrue(resolveBlurContribution(.01f) < resolveBlurContribution(1f))
        assertEquals(1f, resolveBlurContribution(12f), .0001f)
    }

    @Test fun blurTopologyAndPassRadiusRemainContinuousBeyondTwentyPercent() {
        val below = 56f * .199f
        val above = 56f * .201f
        assertEquals(1, resolveKawaseLevelCount(below))
        assertEquals(1, resolveKawaseLevelCount(above))
        assertTrue(
            kotlin.math.abs(
                calculateHalfBlurPassRadiusPx(above) -
                    calculateHalfBlurPassRadiusPx(below),
            ) < .1f,
        )
        assertEquals(14f, calculateHalfBlurPassRadiusPx(56f), .0001f)
    }

    private fun blurSource(): String = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
}
