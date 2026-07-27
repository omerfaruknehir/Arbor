package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurCompatibilityTest {
    @Test fun existingBlurPreferenceKeysAndLegacyMigrationRemainReadable() {
        val prefs = java.io.File("src/main/java/app/arbor/chat/settings/AppPreferences.kt").readText()
        assertTrue(prefs.contains("chrome_blur_strength"))
        assertTrue(prefs.contains("chrome_edge_softness"))
        assertTrue(prefs.contains("chrome_overlay_opacity"))
        assertTrue(prefs.contains("chrome_top_panel_height_dp"))
        assertTrue(prefs.contains("chrome_blur_enabled"))
        assertTrue(prefs.contains("chrome_gradual_enabled"))
        assertFalse(prefs.contains("remove(KEY_CHROME_BLUR_STRENGTH"))
        assertFalse(prefs.contains("remove(KEY_CHROME_EDGE_SOFTNESS"))
        assertFalse(prefs.contains("remove(KEY_CHROME_OVERLAY_OPACITY"))
        assertFalse(prefs.contains("remove(KEY_CHROME_TOP_PANEL_HEIGHT_DP"))
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
