package app.arbor.chat.ui

import androidx.compose.ui.graphics.Color
import app.arbor.chat.settings.CHROME_EDGE_SOFTNESS_ZERO_SNAP_THRESHOLD
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

    @Test fun edgeSoftnessPersistenceIsContinuousAndZeroRemainsExact() {
        assertEquals(.06f, CHROME_EDGE_SOFTNESS_ZERO_SNAP_THRESHOLD, .0001f)
        assertEquals(0f, snapChromeEdgeSoftness(0f), .0001f)
        assertEquals(.02f, snapChromeEdgeSoftness(.02f), .0001f)
        assertEquals(.06f, snapChromeEdgeSoftness(.06f), .0001f)
        assertEquals(.061f, effectiveChromeEdgeSoftness(.061f), .0001f)
        assertEquals(1f, effectiveChromeEdgeSoftness(1f), .0001f)
        assertEquals(0f, edgeSoftnessActivation(0f), .0001f)
        assertTrue(edgeSoftnessActivation(.02f) > 0f)
        assertTrue(edgeSoftnessActivation(.5f) < edgeSoftnessActivation(.88f))
        assertEquals(68f, calculateMergeDistanceDp(1f), .0001f)
    }

    @Test fun rendererRestoresTheExact0178ThreeDirectionKernel() {
        assertEquals(.9238795f, BLUR_AXIS_A_X, 0f)
        assertEquals(.3826834f, BLUR_AXIS_A_Y, 0f)
        assertEquals(.1305262f, BLUR_AXIS_B_X, 0f)
        assertEquals(.9914449f, BLUR_AXIS_B_Y, 0f)
        assertEquals(-.7933533f, BLUR_AXIS_C_X, 0f)
        assertEquals(.6087614f, BLUR_AXIS_C_Y, 0f)
        val source = blurSource()
        assertEquals(3, Regex("RenderEffect\\.createRuntimeShaderEffect").findAll(source).count())
        assertTrue(source.contains("RuntimeShader(EDGE_BLUR_SHADER)"))
        assertTrue(source.contains("sampleStep * 1.476579653"))
        assertTrue(source.contains("sampleStep * 3.445529534"))
        assertTrue(source.contains("sampleStep * 5.414898846"))
        assertTrue(source.contains("sampleStep * 7.384912150"))
        assertTrue(source.contains("RenderEffect.createChainEffect(third, RenderEffect.createChainEffect(second, first))"))
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
            "Shader.TileMode",
            "BlendMode.DstIn",
        ).forEach { token -> assertFalse("Unexpected later blur token: $token", source.contains(token)) }
        assertTrue(source.contains("profiled.graphicsLayer { renderEffect = composeEffect }"))
    }

    @Test fun zeroSoftnessUsesRoundedPanelsAndNonzeroUsesSymmetricFlatFeather() {
        val source = blurSource()
        assertTrue(source.contains("if (normalizedSoftness == 0f) cornerRadiusDp"))
        assertTrue(source.contains("if (!softnessActive)"))
        assertTrue(source.contains("val half = mergeDistancePx * 0.5f"))
        assertTrue(source.contains("topExtent - halfSpan"))
        assertTrue(source.contains("bottomStart - halfSpan"))
        assertTrue(source.contains("uSoftness.x <= 0.0"))
        assertTrue(source.contains("uSoftness.y <= 0.0"))
    }

    @Test fun overlayOpacityIsAbsoluteAndIndependentFromBlur() {
        val tint = Color(0.2f, 0.4f, 0.6f, 0.5f)
        assertEquals(0f, applyOverlayOpacity(tint, -1f).alpha, .0001f)
        assertEquals(.5f, applyOverlayOpacity(tint, .5f).alpha, .002f)
        assertEquals(1f, applyOverlayOpacity(tint, 2f).alpha, .002f)
        val source = blurSource()
        assertTrue(source.contains("val exactTint = applyOverlayOpacity"))
        assertTrue(source.contains("if (exactTint.alpha > 0f)"))
        assertFalse(source.contains("PANEL_OPACITY_BOOST"))
    }

    @Test fun colorAdjustmentRunsOnceAfterTheThreeBlurPasses() {
        val source = blurSource()
        assertTrue(source.contains("buildShader(BLUR_AXIS_A_X, BLUR_AXIS_A_Y, false)"))
        assertTrue(source.contains("buildShader(BLUR_AXIS_B_X, BLUR_AXIS_B_Y, false)"))
        assertTrue(source.contains("buildShader(BLUR_AXIS_C_X, BLUR_AXIS_C_Y, true)"))
        assertTrue(source.contains("if (uAdjustColor > 0.5)"))
    }

    @Test fun profilerRemainsWiredToTheRestoredRenderer() {
        val source = blurSource()
        assertTrue(source.contains("recordBlurEffectBuild(3)"))
        assertTrue(source.contains("recordBlurFrame("))
        assertTrue(source.contains("sourceTraversals = 1"))
        assertTrue(source.contains("downsampleLevels = 0"))
        assertTrue(source.contains("upsampleLevels = 0"))
    }

    private fun blurSource() = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
}
