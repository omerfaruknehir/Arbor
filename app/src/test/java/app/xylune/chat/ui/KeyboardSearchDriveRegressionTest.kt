package app.xylune.chat.ui

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSearchDriveRegressionTest {
    @Test
    fun keyboardOwnsBackBeforeDrawerOrPageNavigation() {
        assertFalse(appBackHandlerEnabled(ownerEnabled = true, imeVisible = true))
        assertTrue(appBackHandlerEnabled(ownerEnabled = true, imeVisible = false))
        assertFalse(pageBackEnabled(drawerVisible = false, imeVisible = true))
        assertFalse(pageBackEnabled(drawerVisible = true, imeVisible = false))
        assertTrue(pageBackEnabled(drawerVisible = false, imeVisible = false))
    }

    @Test
    fun authorizationDataWinsOverCanceledResultCode() {
        assertEquals(
            GoogleAuthorizationResultRoute.PARSE_RESULT,
            googleAuthorizationResultRoute(Activity.RESULT_CANCELED, hasData = true),
        )
        assertEquals(
            GoogleAuthorizationResultRoute.CANCELLED,
            googleAuthorizationResultRoute(Activity.RESULT_CANCELED, hasData = false),
        )
        assertEquals(
            GoogleAuthorizationResultRoute.MISSING_RESULT,
            googleAuthorizationResultRoute(Activity.RESULT_OK, hasData = false),
        )
    }

    @Test
    fun searchUsesCompactPinnedImeAwareLayout() {
        val source = java.io.File("src/main/java/app/xylune/chat/ui/SearchScreen.kt").readText()
        assertFalse(source.contains("CollapsingTranslucentTopBar"))
        assertFalse(source.contains("LargeTopAppBar"))
        assertTrue(source.contains("TopAppBar("))
        assertTrue(source.contains(".imePadding()"))
        assertTrue(source.indexOf("OutlinedTextField(") < source.indexOf("LazyColumn("))
    }

    @Test
    fun chatTopBarMapsEveryListMovementWhileSettingsKeepsNestedScroll() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertFalse(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertTrue(chat.contains("ChatChromeScrollSample("))
        assertTrue(chat.contains("projectChatChromeFromScroll("))
        assertTrue(chat.contains("user drags, fling settling, auto-follow scrollBy calls"))
        assertTrue(chat.contains("topAppBarState.heightOffset = projection.heightOffset"))
        assertFalse(chat.contains("snapshot?.topBarHeightOffset"))
        assertTrue(settings.contains("initialize the title once after measurement"))
    }

    @Test
    fun fullCollapseBoundaryRemainsCollapsed() {
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), 0f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), 0f)
    }

    @Test
    fun chatChromeProjectionTracksTheActualListPosition() {
        val expanded = projectChatChromeFromScroll(0, 0, 56, 176, -120f)
        assertEquals(0f, expanded.heightOffset, 0f)
        assertEquals(0f, expanded.contentOffset, 0f)

        val halfway = projectChatChromeFromScroll(0, 116, 56, 176, -120f)
        assertEquals(-60f, halfway.heightOffset, .0001f)
        assertEquals(-116f, halfway.contentOffset, .0001f)

        val laterItem = projectChatChromeFromScroll(1, 0, 56, 176, -120f)
        assertEquals(-120f, laterItem.heightOffset, 0f)
        assertEquals(-176f, laterItem.contentOffset, 0f)
    }
}
