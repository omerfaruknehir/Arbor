package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationBackTest {
    @Test fun secondaryScreensReturnToStableParents() {
        assertEquals(Screen.CHAT, backDestination(Screen.SEARCH))
        assertEquals(Screen.CHAT, backDestination(Screen.SETTINGS))
        assertEquals(Screen.SETTINGS, backDestination(Screen.SANDBOX))
        assertEquals(Screen.SETTINGS, backDestination(Screen.TERMINAL))
    }
    @Test fun chatIsTheActivityRoot() { assertNull(backDestination(Screen.CHAT)) }

    @Test fun drawerSwipeIsEnabledAtChatAndSettingsRoots() {
        assertTrue(drawerSwipeEnabled(Screen.CHAT))
        assertTrue(drawerSwipeEnabled(Screen.SETTINGS))
        assertFalse(drawerSwipeEnabled(Screen.SEARCH))
        assertFalse(drawerSwipeEnabled(Screen.SANDBOX))
        assertFalse(drawerSwipeEnabled(Screen.TERMINAL))
    }

    @Test fun pageBackTakesOverAsSoonAsTheDrawerIsNoLongerVisible() {
        assertFalse(pageBackEnabled(drawerVisible = true))
        assertTrue(pageBackEnabled(drawerVisible = false))
    }

    @Test fun screenDepthMatchesNavigationHierarchy() {
        assertEquals(0, screenDepth(Screen.CHAT))
        assertEquals(1, screenDepth(Screen.SEARCH))
        assertEquals(1, screenDepth(Screen.SETTINGS))
        assertEquals(2, screenDepth(Screen.SANDBOX))
        assertEquals(2, screenDepth(Screen.TERMINAL))
    }
    @Test fun transitionStateDoesNotInvalidateKeptAlivePages() {
        val navigation = java.io.File("src/main/java/app/arbor/chat/ui/PredictiveNavigation.kt").readText()
        val root = java.io.File("src/main/java/app/arbor/chat/ui/ArborApp.kt").readText()
        assertTrue(!navigation.contains("LocalNavigationTransitionActive"))
        assertTrue(!navigation.contains("CompositionLocalProvider"))
        assertTrue(root.contains("val screenContent: @Composable (Screen) -> Unit = remember"))
        assertTrue(root.contains("val openDrawer = remember(drawerState)"))
    }

}
