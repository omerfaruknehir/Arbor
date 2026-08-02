package app.arbor.chat.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryNavigationBehaviorTest {
    @Test fun settingsTitleMatchesRestoredScroll() {
        assertEquals(0f, settingsTopBarHeightOffset(0, -120f), 0.001f)
        assertEquals(-40f, settingsTopBarHeightOffset(40, -120f), 0.001f)
        assertEquals(-120f, settingsTopBarHeightOffset(500, -120f), 0.001f)
    }

    @Test fun chatTitleCollapsesFromActualListPosition() {
        assertEquals(0f, chatTopBarHeightOffsetForScroll(0, 20, 56, 176, -100f), 0.001f)
        assertEquals(-100f, chatTopBarHeightOffsetForScroll(1, 0, 56, 176, -100f), 0.001f)
    }

    @Test fun predictiveBackTracksDrawerAndPageWaits() {
        assertEquals(300f, DrawerPhysics.predictiveBackOffset(300f, 0f), 0.001f)
        assertEquals(150f, DrawerPhysics.predictiveBackOffset(300f, .5f), 0.001f)
        assertEquals(0f, DrawerPhysics.predictiveBackOffset(300f, 1f), 0.001f)
        assertFalse(pageBackEnabled(true))
        assertTrue(pageBackEnabled(false))
    }
}
