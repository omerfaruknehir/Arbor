package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropBlurTest {
    @Test fun progressIsClampedAndMonotonic() {
        val values = listOf(-1f, 0f, .25f, .5f, .75f, 1f, 2f).map(::arborBlurProgress)
        assertEquals(values.first(), values[1], 0f)
        assertEquals(values[values.lastIndex - 1], values.last(), 0f)
        values.zipWithNext().forEach { (a, b) -> assertTrue(b >= a) }
    }

    @Test fun endpointsAreStable() {
        assertEquals(0f, arborBlurProgress(0f), .0001f)
        assertEquals(1f, arborBlurProgress(1f), .0001f)
    }

    @Test fun blurSliderHasExactEndpointsAndLinearSemantics() {
        assertEquals(0f, calculateBlurRadiusDp(0f), .0001f)
        assertEquals(28f, calculateBlurRadiusDp(.5f), .0001f)
        assertEquals(56f, calculateBlurRadiusDp(1f), .0001f)
        assertEquals(0f, calculateBlurRadiusDp(-1f), .0001f)
        assertEquals(56f, calculateBlurRadiusDp(2f), .0001f)
    }

    @Test fun edgeSoftnessHasExactHardAndSoftEndpoints() {
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
        assertEquals(12f, resolveFeatherDistancePx(12f, .20f, 4f, 100f), .0001f)
    }

    @Test fun topPanelPreservesTheKnownCorrectSourceGeometry() {
        assertEquals(ArborPanelRange(0f, 128f), resolveTopPanelRange(900f, 128f))
        assertEquals(ArborPanelRange(0f, 90f), resolveTopPanelRange(90f, 128f))
    }

    @Test fun bottomPanelUsesTheMeasuredOverlayGeometryInsteadOfFixed208Dp() {
        val range = resolveBottomPanelRange(
            sourceTopInRootPx = 24f,
            panelStartInRootPx = 690f,
            panelEndInRootPx = 810f,
            sourceHeightPx = 900f,
            fallbackExtentPx = 208f,
        )
        assertEquals(ArborPanelRange(666f, 786f), range)
        assertEquals(120f, range.extentPx, .0001f)
    }

    @Test fun bottomPanelHasASafeFallbackUntilComposerIsMeasured() {
        assertEquals(
            ArborPanelRange(692f, 900f),
            resolveBottomPanelRange(0f, Float.NaN, Float.NaN, 900f, 208f),
        )
    }

    @Test fun sampleDensityGrowsContinuouslyAndAddsDedicatedEdgeCoverage() {
        assertEquals(31, BLUR_SAMPLES_PER_PASS)
        assertEquals(1, blurSamplesPerPass(0f))
        assertEquals(15, blurSamplesPerPass(.20f))
        assertEquals(31, blurSamplesPerPass(.40f))
        assertEquals(37, blurSamplesPerPass(.50f))
        assertEquals(45, blurSamplesPerPass(.60f))
        assertEquals(61, blurSamplesPerPass(.80f))
        assertEquals(73, blurSamplesPerPass(1f))
        assertEquals(BLUR_MAX_SAMPLES_PER_PASS, blurSamplesPerPass(1f))

        val everyTwoPercent = (0..50).map { blurSamplesPerPass(it / 50f) }
        everyTwoPercent.zipWithNext().forEach { (previous, next) ->
            assertTrue(next >= previous)
            assertTrue(next - previous <= 2)
        }
        assertTrue(blurEffectiveSamplesPerPass(.40f) > 21f)
        assertEquals(73f, blurEffectiveSamplesPerPass(1f), .0001f)
        assertEquals(7, BLUR_EXTRA_EDGE_PAIRS)

        val lenA = BLUR_AXIS_A_X * BLUR_AXIS_A_X + BLUR_AXIS_A_Y * BLUR_AXIS_A_Y
        val lenB = BLUR_AXIS_B_X * BLUR_AXIS_B_X + BLUR_AXIS_B_Y * BLUR_AXIS_B_Y
        val lenC = BLUR_AXIS_C_X * BLUR_AXIS_C_X + BLUR_AXIS_C_Y * BLUR_AXIS_C_Y
        assertEquals(1f, lenA, .0001f)
        assertEquals(1f, lenB, .0001f)
        assertEquals(1f, lenC, .0001f)
    }

    @Test fun exact01718AdaptiveKernelRunsInProgressivelyCroppedFullResolutionPasses() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
        assertTrue(source.contains("adaptiveGlassBlur"))
        assertTrue(source.contains("basePairBudget = 25.0 * strength"))
        assertTrue(source.contains("corePairBudget = 4.0"))
        assertTrue(source.contains("edgePairBudget = 7.0"))
        assertTrue(source.contains("resolveAdaptiveBlurPassCaptures"))
        assertTrue(source.contains("recordAdaptivePassChain"))
        assertTrue(source.contains("rememberGraphicsLayer"))
        assertTrue(!source.contains("STRIP_GLASS_SHADER"))
        assertTrue(!source.contains("FIXED_BLUR_DOWNSAMPLE"))
        assertTrue(!source.contains("overlayModifier.graphicsLayer"))
    }

    @Test fun exact01718ShaderPayloadIsUnchanged() {
        val source = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
        val shader = source.substringAfter("private val EDGE_BLUR_SHADER = \"\"\"\n")
            .substringBefore("\n\"\"\".trimIndent()")
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(shader.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals("d48b6f6dd47f41c85f25433caa712a456fbf2ea3d04e47e3e2d30bccb0d414d9", hash)
    }

    @Test fun adaptivePassCapturesContainOnlyTheExactRemainingVerticalSupport() {
        val radius = 50f
        val captures = resolveAdaptiveBlurPassCaptures(
            panelRange = ArborPanelRange(100f, 300f),
            sourceHeightPx = 1_000f,
            radiusPx = radius,
        )!!
        assertEquals(100f - radius * BLUR_CHAIN_VERTICAL_SUPPORT_RADIUS_MULTIPLIER, captures.passA.sourceStartPx, .001f)
        assertEquals(300f + radius * BLUR_CHAIN_VERTICAL_SUPPORT_RADIUS_MULTIPLIER, captures.passA.sourceEndPx, .001f)
        assertEquals(
            100f - radius * (BLUR_PASS_B_VERTICAL_RADIUS_MULTIPLIER + BLUR_PASS_C_VERTICAL_RADIUS_MULTIPLIER),
            captures.passB.sourceStartPx,
            .001f,
        )
        assertEquals(100f - radius * BLUR_PASS_C_VERTICAL_RADIUS_MULTIPLIER, captures.passC.sourceStartPx, .001f)
        assertTrue(captures.passA.sourceExtentPx > captures.passB.sourceExtentPx)
        assertTrue(captures.passB.sourceExtentPx > captures.passC.sourceExtentPx)
    }

    @Test fun typicalTopAndBottomPanelsFilterFarLessThanThreeFullScreens() {
        val width = 1_080f
        val height = 2_340f
        val radius = 118f
        val top = resolveAdaptiveBlurPassCaptures(ArborPanelRange(0f, 384f), height, radius)!!
        val bottom = resolveAdaptiveBlurPassCaptures(ArborPanelRange(1_716f, height), height, radius)!!
        val croppedPixels = top.filteredPixels(width) + bottom.filteredPixels(width)
        val fullScreenPixels = width.toLong() * height.toLong() * BLUR_PASS_COUNT
        assertTrue(croppedPixels < fullScreenPixels * 0.70)
    }

    @Test fun overlayOpacityIsClampedAndPreservesRgb() {
        val tint = androidx.compose.ui.graphics.Color(0.2f, 0.4f, 0.6f, 0.5f)
        val hidden = applyOverlayOpacity(tint, -1f)
        val half = applyOverlayOpacity(tint, 0.5f)
        val full = applyOverlayOpacity(tint, 2f)
        assertEquals(0f, hidden.alpha, .0001f)
        assertEquals(0.5f, half.alpha, .002f)
        assertEquals(1f, full.alpha, .002f)
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
}
