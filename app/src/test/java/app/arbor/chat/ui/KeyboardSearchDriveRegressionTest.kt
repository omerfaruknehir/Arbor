package app.arbor.chat.ui

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
        val source = java.io.File("src/main/java/app/arbor/chat/ui/SearchScreen.kt").readText()
        assertFalse(source.contains("CollapsingTranslucentTopBar"))
        assertFalse(source.contains("LargeTopAppBar"))
        assertTrue(source.contains("TopAppBar("))
        assertTrue(source.contains(".imePadding()"))
        assertTrue(source.indexOf("OutlinedTextField(") < source.indexOf("LazyColumn("))
    }

    @Test
    fun synchronizedTopBarsDoNotAlsoConsumeNestedScroll() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertFalse(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertFalse(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("if (limit < 0f)"))
    }

    @Test
    fun fullCollapseBoundaryRemainsCollapsed() {
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), 0f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), 0f)
    }
}
