package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavigationBackTest {
    @Test fun secondaryScreensReturnToStableParents() {
        assertEquals(Screen.CHAT, backDestination(Screen.SEARCH))
        assertEquals(Screen.CHAT, backDestination(Screen.SETTINGS))
        assertEquals(Screen.SETTINGS, backDestination(Screen.SANDBOX))
        assertEquals(Screen.SETTINGS, backDestination(Screen.TERMINAL))
    }
    @Test fun chatIsTheActivityRoot() { assertNull(backDestination(Screen.CHAT)) }
}
