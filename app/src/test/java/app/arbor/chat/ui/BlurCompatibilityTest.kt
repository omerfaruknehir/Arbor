package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurCompatibilityTest {
    @Test fun blurPreferencesRemainReadableWhileTemporaryHeightControlsAreRemoved() {
        val prefs = java.io.File("src/main/java/app/arbor/chat/settings/AppPreferences.kt").readText()
        assertTrue(prefs.contains("chrome_blur_strength"))
        assertTrue(prefs.contains("chrome_edge_softness"))
        assertTrue(prefs.contains("chrome_overlay_opacity"))
        assertTrue(prefs.contains("chrome_blur_enabled"))
        assertTrue(prefs.contains("chrome_gradual_enabled"))
        assertFalse(prefs.contains("chrome_top_panel_height_dp"))
        assertFalse(prefs.contains("chrome_bottom_panel_height_dp"))
        assertFalse(prefs.contains("chromeTopPanelHeightDp"))
        assertFalse(prefs.contains("chromeBottomPanelHeightDp"))
        assertFalse(prefs.contains("remove(KEY_CHROME_BLUR_STRENGTH"))
        assertFalse(prefs.contains("remove(KEY_CHROME_EDGE_SOFTNESS"))
        assertFalse(prefs.contains("remove(KEY_CHROME_OVERLAY_OPACITY"))
    }

    @Test fun nativeBlurUsesACompleteFrameReplayAndPixelLockedEdges() {
        val blur = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
        assertTrue(blur.contains("rememberGraphicsLayer()"))
        assertTrue(blur.contains("sourceLayer.record("))
        assertTrue(blur.contains("filteredLayer.record("))
        assertTrue(blur.contains("drawLayer(sourceLayer)"))
        assertTrue(blur.contains("drawLayer(filteredLayer)"))
        assertTrue(blur.contains("filteredLayer.renderEffect = panelEffect"))
        assertTrue(blur.contains("CompositingStrategy.Offscreen"))
        assertTrue(blur.contains("smoothstep(start - 1.0, start + 1.0"))
        assertTrue(blur.contains("smoothstep(end - 1.0, end + 1.0"))
        assertFalse(blur.contains("decorated.graphicsLayer"))
    }

    @Test fun panelHeightsAreFixedAndComposerExpandsWithMeasuredContent() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val blur = java.io.File("src/main/java/app/arbor/chat/ui/BackdropBlur.kt").readText()
        val search = java.io.File("src/main/java/app/arbor/chat/ui/SearchScreen.kt").readText()
        val sandbox = java.io.File("src/main/java/app/arbor/chat/ui/SandboxScreen.kt").readText()
        val terminal = java.io.File("src/main/java/app/arbor/chat/ui/LinuxTerminalScreen.kt").readText()

        assertFalse(settings.contains("Top panel height"))
        assertFalse(settings.contains("Bottom panel height"))
        assertFalse(settings.contains("BottomPanelHeightPreview"))
        assertTrue(chat.contains("topPanelHeight = CHAT_TOP_PANEL_HEIGHT_DP.dp"))
        assertTrue(chat.contains("panelHeight = CHAT_COMPOSER_MIN_PANEL_HEIGHT_DP.dp"))
        assertTrue(chat.contains("expandToMeasuredHeight = true"))
        assertTrue(settings.contains("blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp"))
        assertTrue(search.contains("blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp"))
        assertTrue(sandbox.contains("blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp"))
        assertTrue(terminal.contains("blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp"))
        assertTrue(blur.contains("internal const val CHAT_TOP_PANEL_HEIGHT_DP = 120f"))
        assertTrue(blur.contains("internal const val STANDARD_TOP_PANEL_HEIGHT_DP = 100f"))
        assertTrue(blur.contains("val measuredHeightPx"))
        assertTrue(blur.contains("max(panelHeightPx, measuredHeightPx)"))
    }

    @Test fun drawerAndNavigationRemainGraphicsLayerIsolated() {
        val root = java.io.File("src/main/java/app/arbor/chat/ui/ArborApp.kt").readText()
        val drawer = java.io.File("src/main/java/app/arbor/chat/ui/InteractiveNavigationDrawer.kt").readText()
        val navigation = java.io.File("src/main/java/app/arbor/chat/ui/PredictiveNavigation.kt").readText()
        assertTrue(root.contains("rememberInteractiveDrawerState"))
        assertTrue(drawer.contains("graphicsLayer"))
        assertTrue(drawer.contains("never from ArborApp composition"))
        assertTrue(navigation.contains("graphicsLayer"))
        assertTrue(root.contains("PerformanceOverlayHost"))
        assertTrue(root.contains("val snapshot by monitor.snapshot.collectAsState()"))
        assertFalse(root.contains("val performanceSnapshot by performanceMonitor.snapshot.collectAsState()"))
    }
}
