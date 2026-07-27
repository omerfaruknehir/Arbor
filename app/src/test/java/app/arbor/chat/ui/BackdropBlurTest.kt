package app.arbor.chat.ui

import androidx.compose.ui.graphics.Color
import app.arbor.chat.settings.CHROME_EDGE_SOFTNESS_FLAT_SNAP_POINT
import app.arbor.chat.settings.CHROME_EDGE_SOFTNESS_ROUNDED_SNAP_POINT
import app.arbor.chat.settings.chromeEdgeCornerTransition
import app.arbor.chat.settings.chromeEdgeControlPositionForSoftness
import app.arbor.chat.settings.displayedChromeEdgeSoftness
import app.arbor.chat.settings.effectiveChromeEdgeSoftness
import app.arbor.chat.settings.snapChromeEdgeSoftness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test fun progressIsClampedMonotonicAndContinuous() {
        val values = listOf(-1f, 0f, .01f, .25f, .5f, .75f, .99f, 1f, 2f).map(::arborBlurProgress)
        assertEquals(values.first(), values[1], 0f)
        assertEquals(values[values.lastIndex - 1], values.last(), 0f)
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
        assertEquals(0f, arborBlurProgress(0f), .0001f)
        assertEquals(1f, arborBlurProgress(1f), .0001f)
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

    @Test fun highStrengthBlurUsesPatternFreeNativeGaussianComposite() {
        val source = blurSource()
        assertTrue(source.contains("RenderEffect.createBlurEffect"))
        assertTrue(source.contains("Shader.TileMode.CLAMP"))
        assertTrue(source.contains("RenderEffect.createBlendModeEffect"))
        assertTrue(source.contains("BlendMode.SRC_OVER"))
        assertTrue(source.contains("BlendMode.PLUS"))
        assertTrue(source.contains("BlendMode.DST_IN"))
        assertTrue(source.contains("BlendMode.DST_OUT"))
        assertTrue(source.contains("identityOutsidePanels"))
        assertTrue(source.contains("RenderEffect.createShaderEffect(maskShader)"))
        assertTrue(source.contains("PANEL_ALPHA_MASK_SHADER"))
        assertFalse(source.contains("sampleStep *"))
        assertFalse(source.contains("uDirection"))
        assertFalse(source.contains("createRuntimeShaderEffect"))
        assertFalse(source.contains("createChainEffect"))
    }

    @Test fun brokenCaptureAndHalfResolutionCompositorIsGone() {
        val source = blurSource()
        listOf(
            "rememberGraphicsLayer",
            "sourceLayer.record",
            "recordKawasePanelChain",
            "halfBlurA",
            "halfBlurB",
            "halfBlurC",
            "halfBlurD",
            "resolveKawasePanelPlan",
        ).forEach { token -> assertFalse("Unexpected later blur token: $token", source.contains(token)) }
        assertTrue(source.contains("decorated.graphicsLayer { renderEffect = composeEffect }"))
    }

    @Test fun firstRangeMorphsRoundedToFlatAndSecondRangeAddsSymmetricFeather() {
        val source = blurSource()
        assertTrue(source.contains("chromeEdgeCornerTransition(normalizedSoftness)"))
        assertTrue(source.contains("if (!softnessActive)"))
        assertTrue(source.contains("val half = mergeDistance * 0.5f"))
        assertTrue(source.contains("uBounds.y - halfSpan"))
        assertTrue(source.contains("uBounds.x - halfSpan"))
        assertTrue(source.contains("uSoftness <= 0.0"))
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

    @Test fun colorAdjustmentUsesAStableNativeColorFilterBeforeMasking() {
        val source = blurSource()
        assertTrue(source.contains("RenderEffect.createColorFilterEffect"))
        assertTrue(source.contains("ColorMatrixColorFilter"))
        assertTrue(source.contains("buildGlassColorMatrix"))
        assertFalse(source.contains("content.eval(coord)"))

        val identity = glassColorMatrixValues(1f, 1f, 1f)
        assertEquals(20, identity.size)
        assertEquals(1f, identity[0], .0001f)
        assertEquals(1f, identity[6], .0001f)
        assertEquals(1f, identity[12], .0001f)
        assertEquals(1f, identity[18], .0001f)
        listOf(1, 2, 4, 5, 7, 9, 10, 11, 14, 15, 16, 17, 19).forEach { index ->
            assertEquals("identity[$index]", 0f, identity[index], .0001f)
        }
    }

    @Test fun blurAndOverlayUseTheSameRootCoordinateBounds() {
        val source = blurSource()
        assertTrue(source.contains("updatePanelBounds"))
        assertTrue(source.contains("setFloatUniform(\"uBounds\", startPx, endPx)"))
        assertTrue(source.contains("normalizedTopStart"))
        assertTrue(source.contains("normalizedTopEnd"))
        assertTrue(source.contains("drawPanelOverlay"))
        assertFalse(source.contains("coerceIn(1f, size.height"))
    }

    @Test fun profilerRemainsWiredToTheGaussianRenderer() {
        val source = blurSource()
        assertTrue(source.contains("recordBlurEffectBuild(panels.size * 6 + 5)"))
        assertTrue(source.contains("recordBlurFrame("))
        assertTrue(source.contains("sourceTraversals = 1"))
        assertTrue(source.contains("downsampleLevels = 0"))
        assertTrue(source.contains("upsampleLevels = 0"))
    }

    private fun blurSource() = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
}
