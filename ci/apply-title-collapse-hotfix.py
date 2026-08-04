#!/usr/bin/env python3
from __future__ import annotations

import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
STABLE_COMMIT = "92bce7f1c5f0d96436b36416bf4d1e65f816f220"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def git_show(path: str) -> str:
    result = subprocess.run(
        ["git", "show", f"{STABLE_COMMIT}:{path}"],
        cwd=REPO,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


chat_path = REPO / "app/src/main/java/app/xylune/chat/ui/ChatScreen.kt"
chat = git_show("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")

old_restore = '''            // Restore chrome once after the list anchor is restored. From this
            // point onward Material's nested-scroll connection is the only live
            // owner of app-bar movement, so one finger pixel remains one consumed
            // scroll pixel instead of also shifting content through changing inset.
            val limit = snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
            val restoredHeightOffset = snapshot?.topBarHeightOffset
                ?: chatTopBarHeightOffsetForScroll(
                    firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                    startPx = chromeStartPx,
                    endPx = chromeEndPx,
                    heightOffsetLimit = limit,
                )
            topAppBarState.heightOffset = restoredHeightOffset.coerceIn(limit, 0f)
            topAppBarState.contentOffset = -(
                messageListState.firstVisibleItemIndex * chromeEndPx +
                    messageListState.firstVisibleItemScrollOffset
                ).toFloat()
            initialPositioned = true
'''
new_restore = '''            // Restore chrome once after the list anchor settles. Material nested
            // scroll is the only live owner after this point; continuously deriving
            // chrome from LazyColumn coordinates creates a feedback loop because
            // the changing app-bar height also changes the list's top inset.
            val limit = snapshotFlow { topAppBarState.heightOffsetLimit }.first { it < 0f }
            val restoredHeightOffset = if (snapshot == null || snapshot.atLatest) {
                // A non-empty chat opened at its latest message uses compact chrome,
                // even when the short list has no physical scroll range.
                limit
            } else {
                chatTopBarHeightOffsetForScroll(
                    firstVisibleItemIndex = messageListState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = messageListState.firstVisibleItemScrollOffset,
                    startPx = chromeStartPx,
                    endPx = chromeEndPx,
                    heightOffsetLimit = limit,
                )
            }
            topAppBarState.heightOffset = restoredHeightOffset.coerceIn(limit, 0f)
            topAppBarState.contentOffset = restoredHeightOffset
            initialPositioned = true
'''
chat = replace_once(chat, old_restore, new_restore, "restore state")

chat = replace_once(
    chat,
    '''                onImmediateSend = {
                    manualFollowHold = false
                    followMode = ChatFollowMode.FOLLOWING
                },''',
    '''                onImmediateSend = {
                    manualFollowHold = false
                    followMode = ChatFollowMode.FOLLOWING
                    val limit = topAppBarState.heightOffsetLimit
                    if (limit < 0f) {
                        // Sending compacts the header even if this short conversation
                        // cannot consume enough LazyColumn scroll to collapse it.
                        topAppBarState.heightOffset = limit
                        topAppBarState.contentOffset = limit
                    }
                },''',
    "send boundary",
)

chat = replace_once(
    chat,
    '''                messageListState.scrollToItem(uiIndex.coerceAtLeast(0))
                searchFocusHandled = true''',
    '''                messageListState.scrollToItem(uiIndex.coerceAtLeast(0))
                val limit = topAppBarState.heightOffsetLimit
                if (limit < 0f) {
                    val targetOffset = if (uiIndex <= 0) 0f else limit
                    topAppBarState.heightOffset = targetOffset
                    topAppBarState.contentOffset = targetOffset
                }
                searchFocusHandled = true''',
    "search boundary",
)

chat = replace_once(
    chat,
    '''                    manualFollowHold = false
                    followMode = ChatFollowMode.FOLLOWING
                    listScope.launch { snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx) }''',
    '''                    manualFollowHold = false
                    followMode = ChatFollowMode.FOLLOWING
                    val limit = topAppBarState.heightOffsetLimit
                    if (limit < 0f) {
                        topAppBarState.heightOffset = limit
                        topAppBarState.contentOffset = limit
                    }
                    listScope.launch { snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx) }''',
    "latest boundary",
)

if "Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)" not in chat:
    raise RuntimeError("Material nested-scroll owner missing")
if "ChatChromeScrollSample(" in chat or "projectChatChromeFromScroll(" in chat:
    raise RuntimeError("0.23.15 projection loop still present")
chat_path.write_text(chat)

# Restore the stable top-bar implementation and document the ownership model.
topbar_path = REPO / "app/src/main/java/app/xylune/chat/ui/ChatCollapsingTranslucentTopBar.kt"
topbar = git_show("app/src/main/java/app/xylune/chat/ui/ChatCollapsingTranslucentTopBar.kt")
topbar = replace_once(
    topbar,
    ''' * Chat counterpart of [CollapsingTranslucentTopBar]. The Material top-app-bar
 * state owns the collapse distance, exactly as it does on Settings screens.
 * There is no message-index, anchor-item, timer, or independent animation state.''',
    ''' * Chat counterpart of [CollapsingTranslucentTopBar]. Material nested scroll owns
 * live gesture and fling motion. Deliberate programmatic navigation synchronizes the
 * state only at its boundary, without feeding layout changes back into Scaffold.''',
    "top-bar comment",
)
topbar_path.write_text(topbar)

# Source-level regression checks keep the single-owner contract explicit.
test_path = REPO / "app/src/test/java/app/xylune/chat/ui/KeyboardSearchDriveRegressionTest.kt"
test_path.write_text('''package app.xylune.chat.ui

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
    fun chatTopBarHasOneLiveOwnerAndExplicitProgrammaticBoundaries() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/xylune/chat/ui/SettingsScreen.kt").readText()
        assertTrue(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertFalse(chat.contains("ChatChromeScrollSample("))
        assertFalse(chat.contains("projectChatChromeFromScroll("))
        assertTrue(chat.contains("snapshot == null || snapshot.atLatest"))
        assertTrue(chat.contains("Sending compacts the header"))
        assertTrue(chat.contains("val targetOffset = if (uiIndex <= 0) 0f else limit"))
        assertTrue(settings.contains("initialize the title once after measurement"))
    }

    @Test
    fun fullCollapseBoundaryRemainsCollapsed() {
        assertEquals(1f, calculateTopChromeProgress(0, 176, 56, 176), 0f)
        assertEquals(1f, calculateTopChromeProgress(1, 0, 56, 176), 0f)
    }
}
''')

# Release metadata.
gradle_path = REPO / "app/build.gradle.kts"
gradle = gradle_path.read_text()
gradle = replace_once(gradle, "versionCode = 184", "versionCode = 185", "version code")
gradle = replace_once(gradle, 'versionName = "0.23.15"', 'versionName = "0.23.16"', "version name")
gradle_path.write_text(gradle)

changelog_path = REPO / "CHANGELOG.md"
changelog = changelog_path.read_text()
if not changelog.startswith("## 0.23.16"):
    changelog_path.write_text(
        '''## 0.23.16 — 2026-08-04

- Remove the continuous LazyColumn-to-top-bar projection that caused a layout feedback loop, jitter, jumps, and unrelated title-state changes.
- Restore Material nested scroll as the sole live gesture and fling owner.
- Synchronize opening/restoration, Send, search jumps, and Go to latest explicitly, including short chats with no physical scroll range.

'''
        + changelog
    )

release_notes = REPO / "docs/releases/RELEASE_NOTES_0.23.16.md"
release_notes.write_text('''# Xylune 0.23.16

## Chat title collapse hotfix

This release removes the 0.23.15 feedback loop between the message-list position and the changing top-bar inset. Finger scrolling and flings are again handled by one Material nested-scroll owner, so the title no longer jitters, jumps, or reacts to unrelated message-height changes.

Programmatic boundaries are synchronized explicitly: opening a non-empty chat at its latest message, Send, search navigation, and Go to latest all select the correct compact or expanded state even when a short conversation has no physical scroll distance.
''')

publish_path = REPO / ".github/workflows/publish-0.23.11.yml"
publish = publish_path.read_text().replace("0.23.15", "0.23.16")
publish = replace_once(publish, "versionCode = 184", "versionCode = 185", "publish version")
old_checks = '''          grep -F 'ChatChromeScrollSample(' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          grep -F 'projectChatChromeFromScroll(' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          grep -F 'user drags, fling settling, auto-follow scrollBy calls' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          ! grep -F 'Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          ! grep -F 'snapshot?.topBarHeightOffset' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
'''
new_checks = '''          grep -F 'Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          ! grep -F 'ChatChromeScrollSample(' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          ! grep -F 'projectChatChromeFromScroll(' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          grep -F 'snapshot == null || snapshot.atLatest' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
          grep -F 'Sending compacts the header' app/src/main/java/app/xylune/chat/ui/ChatScreen.kt
'''
publish = replace_once(publish, old_checks, new_checks, "release checks")
publish_path.write_text(publish)

print("Applied Xylune 0.23.16 title-collapse hotfix")
