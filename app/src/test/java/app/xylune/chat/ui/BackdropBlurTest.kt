package app.xylune.chat.ui

import androidx.compose.ui.graphics.Color
import app.xylune.chat.settings.CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT
import app.xylune.chat.settings.CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT
import app.xylune.chat.settings.chromeEdgeCornerTransition
import app.xylune.chat.settings.chromeEdgeControlPositionForSoftness
import app.xylune.chat.settings.displayedChromeEdgeSoftness
import app.xylune.chat.settings.effectiveChromeEdgeSoftness
import app.xylune.chat.settings.snapChromeEdgeSoftness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test fun progressIsClampedMonotonicAndContinuous() {
        val values = listOf(-1f, 0f, .01f, .25f, .5f, .75f, .99f, 1f, 2f).map(::xyluneBlurProgress)
        assertEquals(values.first(), values[1], 0f)
        assertEquals(values[values.lastIndex - 1], values.last(), 0f)
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
        assertEquals(0f, xyluneBlurProgress(0f), .0001f)
        assertEquals(1f, xyluneBlurProgress(1f), .0001f)
    }

    @Test fun currentBlurSliderHasNoMinimumRadiusOrTwentyPercentJump() {
        assertEquals(0f, calculateBlurRadiusDp(0f), .0001f)
        assertEquals(.56f, calculateBlurRadiusDp(.01f), .0001f)
        assertEquals(11.144f, calculateBlurRadiusDp(.199f), .001f)
        assertEquals(11.256f, calculateBlurRadiusDp(.201f), .001f)
        assertEquals(28f, calculateBlurRadiusDp(.5f), .0001f)
        assertEquals(56f, calculateBlurRadiusDp(1f), .0001f)
        assertEquals(.13f, quantizeBlurRadiusDp(.13f), 0f)
    }

    @Test fun edgeSoftnessUsesRoundedTransitionFlatAndFeatherRanges() {
        assertEquals(0f, CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT, 0f)
        assertEquals(.20f, CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT, 0f)
        assertEquals(0f, snapChromeEdgeSoftness(0f), 0f)
        assertEquals(.10f, snapChromeEdgeSoftness(.10f), 0f)
        assertEquals(0f, effectiveChromeEdgeSoftness(.10f), 0f)
        assertEquals(0f, effectiveChromeEdgeSoftness(.20f), 0f)
        assertEquals(0f, displayedChromeEdgeSoftness(0f), 0f)
        assertEquals(0f, displayedChromeEdgeSoftness(.10f), 0f)
        assertEquals(0f, displayedChromeEdgeSoftness(.20f), 0f)
        assertEquals(.20f, chromeEdgeControlPositionForSoftness(0f), 0f)
        assertEquals(.60f, chromeEdgeControlPositionForSoftness(.50f), .0001f)
        assertEquals(1f, chromeEdgeControlPositionForSoftness(1f), 0f)
        assertEquals(.5f, effectiveChromeEdgeSoftness(.60f), .0001f)
        assertEquals(1f, effectiveChromeEdgeSoftness(1f), 0f)
        assertEquals(0f, chromeEdgeCornerTransition(0f), 0f)
        assertTrue(chromeEdgeCornerTransition(.10f) > 0f)
        assertTrue(chromeEdgeCornerTransition(.10f) < 1f)
        assertEquals(1f, chromeEdgeCornerTransition(.20f), 0f)
        assertEquals(0f, edgeSoftnessActivation(.20f), 0f)
        assertTrue(edgeSoftnessActivation(.21f) > 0f)
        assertEquals(68f, calculateMergeDistanceDp(1f), .0001f)
    }

    @Test fun blurUsesTheDeviceProvenDirectRuntimeShaderChain() {
        val source = blurSource()
        assertTrue(source.contains("RenderEffect.createRuntimeShaderEffect"))
        assertTrue(source.contains("RenderEffect.createChainEffect"))
        assertTrue(source.contains("PANEL_EDGE_BLUR_SHADER"))
        assertTrue(source.contains("uniform shader content"))
        assertTrue(source.contains("uDirection"))
        assertTrue(source.contains("BLUR_AXIS_A_X"))
        assertTrue(source.contains("sampleStep *"))
        assertFalse(source.contains("RenderEffect.createBlurEffect"))
        assertFalse(source.contains("RenderEffect.createShaderEffect"))
        assertFalse(source.contains("rememberGraphicsLayer"))
    }

    @Test fun blurFiltersTheContentLayerAndKeepsTintOutsideTheEffect() {
        val source = blurSource()
        assertTrue(source.contains("val decorated = measured.drawWithContent"))
        assertTrue(source.contains("drawPanelOverlay("))
        assertTrue(source.contains("decorated.graphicsLayer { renderEffect = blurEffect }"))
        assertFalse(source.contains("sourceLayer.record"))
        assertFalse(source.contains("filteredLayer.record"))
        assertFalse(source.contains("drawLayer(sourceLayer)"))
    }

    @Test fun firstRangeMorphsRoundedToFlatAndSecondRangeAddsSymmetricFeather() {
        val source = blurSource()
        assertTrue(source.contains("chromeEdgeCornerTransition(normalizedSoftness)"))
        assertTrue(source.contains("if (!softnessActive)"))
        assertTrue(source.contains("val half = mergeDistance * 0.5f"))
        assertTrue(source.contains("uTopBounds.y - halfSpan"))
        assertTrue(source.contains("uBottomBounds.x - halfSpan"))
        assertTrue(source.contains("uMerge.x <= 0.5"))
    }

    @Test fun overlayOpacityIsAbsoluteAndIndependentFromBlur() {
        val tint = Color(0.2f, 0.4f, 0.6f, 0.5f)
        assertEquals(0f, applyOverlayOpacity(tint, -1f).alpha, .0001f)
        assertEquals(.5f, applyOverlayOpacity(tint, .5f).alpha, .002f)
        assertEquals(1f, applyOverlayOpacity(tint, 2f).alpha, .002f)
        val source = blurSource()
        assertTrue(source.contains("val exactTint = applyOverlayOpacity"))
        assertTrue(source.contains("if (tint.alpha > 0f)"))
        assertFalse(source.contains("PANEL_OPACITY_BOOST"))
    }

    @Test fun blurKernelUsesDenseDirectSamplesAtHighStrength() {
        val source = blurSource()
        assertTrue(source.contains("content.eval(coord)"))
        assertTrue(source.contains("radius / 7.5"))
        (1..7).forEach { tap ->
            assertTrue(source.contains("sampleStep * $tap.0"))
        }
        assertFalse(source.contains("1.476579653"))
        assertFalse(source.contains("3.445529534"))
        assertTrue(source.contains("gaps became visible as repeating bands/lattices"))
        assertTrue(source.contains("RenderEffect.createChainEffect(second, first)"))
    }

    @Test fun blurAndOverlayUseTheSameRootCoordinateBounds() {
        val source = blurSource()
        assertTrue(source.contains("updatePanelBounds"))
        assertTrue(source.contains("topStartPx = normalizedTopStart"))
        assertTrue(source.contains("bottomStartPx = normalizedBottomStart"))
        assertTrue(source.contains("setFloatUniform(\"uTopBounds\", topStartPx, topEndPx)"))
        assertTrue(source.contains("setFloatUniform(\"uBottomBounds\", bottomStartPx, bottomEndPx)"))
        assertTrue(source.contains("normalizedTopStart"))
        assertTrue(source.contains("normalizedTopEnd"))
        assertTrue(source.contains("drawPanelOverlay"))
        assertFalse(source.contains("coerceIn(1f, size.height"))
    }

    @Test fun softTintUsesOneContinuousGradientWithoutASolidGradientJoin() {
        val source = blurSource()
        assertTrue(source.contains("colorStops = arrayOf("))
        assertTrue(source.contains("solidStop to tint"))
        assertTrue(source.contains("middleStop to tint.copy(alpha = tint.alpha * 0.5f)"))
        assertFalse(source.contains("if (solidEnd > start) drawRect(tint"))
        assertFalse(source.contains("if (end > solidStart) drawRect(tint"))
    }


    @Test fun blurBoundaryIsPixelStableAndNormalChromeHasNoEdgeLine() {
        val source = blurSource()
        assertTrue(source.contains("val stableTop = round(topInRootPx)"))
        assertTrue(source.contains("val start = round(minOf(startInRootPx, endInRootPx))"))
        assertTrue(source.contains("smoothstep(end - 1.0, end + 1.0, coord.y)"))
        assertTrue(source.contains("private const val DEFAULT_EDGE_HIGHLIGHT = 0f"))
        assertTrue(source.contains("XyluneBackdropDebugOverlay"))
        assertTrue(source.contains("color = Color.Red"))
    }

    @Test fun profilerRemainsWiredToTheDirectRenderer() {
        val source = blurSource()
        assertTrue(source.contains("recordBlurEffectBuild(3)"))
        assertTrue(source.contains("recordBlurFrame("))
        assertTrue(source.contains("sourceTraversals = 1"))
        assertTrue(source.contains("layerReplays = if (blurEffect != null) 1 else 0"))
        assertTrue(source.contains("captureUpdates = 0"))
        assertTrue(source.contains("downsampleLevels = 0"))
        assertTrue(source.contains("upsampleLevels = 0"))
    }

    private fun blurSource() = java.io.File("src/main/java/app/xylune/chat/ui/BackdropBlur.kt").readText()
}
