#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    (ROOT / path).write_text(content)


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:160]!r}")
    write(path, content.replace(old, new, 1))


replace_once("app/build.gradle.kts", "versionCode = 166", "versionCode = 167")
replace_once("app/build.gradle.kts", 'versionName = "0.22.2"', 'versionName = "0.22.3"')

chat = "app/src/main/java/app/arbor/chat/ui/ChatScreen.kt"
replace_once(
    chat,
    '''            } else {
                snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx)
            }
            initialPositioned = true
        }
    }

    LaunchedEffect(messageListState, conversation?.id, topAppBarState, initialPositioned, chromeStartPx, chromeEndPx) {
        if (!initialPositioned) return@LaunchedEffect
        snapshotFlow {
            Triple(
                messageListState.firstVisibleItemIndex,
                messageListState.firstVisibleItemScrollOffset,
                topAppBarState.heightOffsetLimit,
            )
        }
            .distinctUntilChanged()
            .collect { (index, offset, limit) ->
                if (limit < 0f) {
                    topAppBarState.heightOffset = chatTopBarHeightOffsetForScroll(
                        firstVisibleItemIndex = index,
                        firstVisibleItemScrollOffset = offset,
                        startPx = chromeStartPx,
                        endPx = chromeEndPx,
                        heightOffsetLimit = limit,
                    ).coerceIn(limit, 0f)
                    topAppBarState.contentOffset = -(index * chromeEndPx + offset).toFloat()
                }
            }
    }
''',
    '''            } else {
                snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx)
            }

            // Restore chrome once after the list anchor is restored. From this
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
        }
    }
''',
)
replace_once(
    chat,
    '''    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
''',
    '''    Scaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
''',
)

settings = "app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt"
replace_once(
    settings,
    '''        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0),
''',
    '''        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0),
''',
)
replace_once(
    settings,
    '''        LaunchedEffect(route, scrollState, topAppBarState) {
            val state = topAppBarState ?: return@LaunchedEffect
            snapshotFlow { scrollState.value to state.heightOffsetLimit }
                .distinctUntilChanged()
                .collect { (offset, limit) ->
                    // A zero limit is a transient pre-measure/resize state. Writing
                    // heightOffset then would expand an already-collapsed title.
                    if (limit < 0f) {
                        state.heightOffset = settingsTopBarHeightOffset(offset, limit)
                    }
                    state.contentOffset = -offset.coerceAtLeast(0).toFloat()
                }
        }
''',
    '''        LaunchedEffect(route, revision, scrollState, topAppBarState) {
            val state = topAppBarState ?: return@LaunchedEffect
            // A restored ScrollState has already consumed its historical offset,
            // so initialize the title once after measurement. Live gesture deltas
            // are then owned only by Material's nested-scroll connection.
            val limit = snapshotFlow { state.heightOffsetLimit }.first { it < 0f }
            state.heightOffset = settingsTopBarHeightOffset(scrollState.value, limit)
            state.contentOffset = -scrollState.value.coerceAtLeast(0).toFloat()
        }
''',
)

regression = "app/src/test/java/app/arbor/chat/ui/KeyboardSearchDriveRegressionTest.kt"
replace_once(
    regression,
    '''    fun synchronizedTopBarsDoNotAlsoConsumeNestedScroll() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertFalse(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertFalse(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("if (limit < 0f)"))
    }
''',
    '''    fun topBarsUseNestedScrollAsTheirOnlyLiveScrollOwner() {
        val chat = java.io.File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertTrue(chat.contains("Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)"))
        assertTrue(settings.contains("Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)"))
        assertFalse(chat.contains("Triple(\\n                messageListState.firstVisibleItemIndex"))
        assertFalse(settings.contains("snapshotFlow { scrollState.value to state.heightOffsetLimit }"))
        assertTrue(chat.contains("Restore chrome once after the list anchor is restored"))
        assertTrue(settings.contains("initialize the title once after measurement"))
    }
''',
)

notes = "docs/releases/RELEASE_NOTES_0.22.3.md"
write(notes, '''# Arbor 0.22.3

- Correct app-bar scroll physics so content tracks the finger one-to-one while the title collapses or expands.
- Restore Material nested-scroll consumption for chat and Settings.
- Remove continuous list-offset-to-app-bar synchronization that added app-bar inset movement on top of the list's own scroll.
- Restore saved title collapse once when reopening a chat or Settings page, then leave live movement to a single scroll owner.
''')

print("Applied Arbor 0.22.3 scroll physics patch")
