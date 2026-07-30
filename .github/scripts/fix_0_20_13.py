from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old!r}")
    file.write_text(text.replace(old, new, 1))


arbor = "app/src/main/java/app/arbor/chat/ui/ArborApp.kt"
replace_once(
    arbor,
    "import androidx.compose.foundation.layout.BoxWithConstraints\n",
    "import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.BoxWithConstraints\n",
)
replace_once(
    arbor,
    "import androidx.compose.foundation.layout.fillMaxSize\n",
    "import androidx.compose.foundation.layout.fillMaxHeight\nimport androidx.compose.foundation.layout.fillMaxSize\n",
)
replace_once(
    arbor,
    "                    Screen.SETTINGS -> SettingsScreen(viewModel, compactOpenDrawer)",
    """                    Screen.SETTINGS -> Box(Modifier.fillMaxSize()) {
                        SettingsScreen(viewModel, compactOpenDrawer)
                        SettingsLeftBackEdgeGuard()
                    }""",
)
replace_once(
    arbor,
    """                // Only Chat owns the left-edge drawer gesture. Settings uses both
                // system back edges; its menu button remains the explicit drawer entry point.
                gesturesEnabled = drawerSwipeEnabled(screen),""",
    """                // Chat and Settings retain pull-to-open. In Settings, a narrow
                // non-consuming priority strip reserves the actual system Back edge.
                gesturesEnabled = drawerSwipeEnabled(screen),""",
)
replace_once(
    arbor,
    "internal fun drawerSwipeEnabled(screen: Screen): Boolean = screen == Screen.CHAT",
    "internal fun drawerSwipeEnabled(screen: Screen): Boolean = screen == Screen.CHAT || screen == Screen.SETTINGS",
)
replace_once(
    arbor,
    "internal fun performanceOverlayAlignment(position: PerformanceOverlayPosition): Alignment = when (position) {",
    """@Composable
private fun SettingsLeftBackEdgeGuard() {
    // The drawer can still be pulled from the Settings content, but the first
    // 48 dp are owned by Android Back. This node only registers geometry; it
    // consumes no pointer input and therefore cannot block taps or scrolling.
    Box(
        Modifier
            .fillMaxHeight()
            .width(48.dp)
            .horizontalGesturePriority(),
    )
}

internal fun performanceOverlayAlignment(position: PerformanceOverlayPosition): Alignment = when (position) {""",
)

test = "app/src/test/java/app/arbor/chat/ui/NavigationBackTest.kt"
replace_once(
    test,
    """    @Test fun drawerSwipeIsLimitedToChatSoSettingsCanUseBothBackEdges() {
        assertTrue(drawerSwipeEnabled(Screen.CHAT))
        assertFalse(drawerSwipeEnabled(Screen.SETTINGS))
        assertFalse(drawerSwipeEnabled(Screen.SEARCH))
        assertFalse(drawerSwipeEnabled(Screen.SANDBOX))
        assertFalse(drawerSwipeEnabled(Screen.TERMINAL))
    }
""",
    """    @Test fun drawerSwipeWorksAtChatAndSettingsRoots() {
        assertTrue(drawerSwipeEnabled(Screen.CHAT))
        assertTrue(drawerSwipeEnabled(Screen.SETTINGS))
        assertFalse(drawerSwipeEnabled(Screen.SEARCH))
        assertFalse(drawerSwipeEnabled(Screen.SANDBOX))
        assertFalse(drawerSwipeEnabled(Screen.TERMINAL))
    }

    @Test fun settingsReservesOnlyTheRealLeftBackEdge() {
        val root = java.io.File("src/main/java/app/arbor/chat/ui/ArborApp.kt").readText()
        assertTrue(root.contains("SettingsLeftBackEdgeGuard"))
        assertTrue(root.contains(".width(48.dp)"))
        assertTrue(root.contains(".horizontalGesturePriority()"))
    }
""",
)

replace_once("app/build.gradle.kts", "versionCode = 138", "versionCode = 139")
replace_once("app/build.gradle.kts", 'versionName = "0.20.12"', 'versionName = "0.20.13"')

changelog = Path("CHANGELOG.md")
text = changelog.read_text()
marker = "# Changelog\n"
section = """

## 0.20.13 — 2026-07-30

- Restore pull-to-open drawer gestures in Settings without stealing Android's left-edge Back gesture.
- Reserve only a 48 dp non-consuming Back edge; drawer swipes still work from the Settings content area.
- Attach explicit versioned source ZIP and TAR.GZ archives to releases and include them in SHA-256 checksums.
- Split release verification into isolated, memory-bounded Gradle invocations to prevent Kotlin compiler stalls.
"""
if "## 0.20.13 — 2026-07-30" not in text:
    if marker not in text:
        raise SystemExit("CHANGELOG heading not found")
    changelog.write_text(text.replace(marker, marker + section, 1))

readme = Path("README.md")
readme.write_text(readme.read_text().replace("0.20.12", "0.20.13"))

Path("docs/releases/RELEASE_NOTES_0.20.13.md").write_text("""# Arbor 0.20.13

## Fixed

- Pull-to-open drawer gestures work again from Settings.
- Android Back still owns the actual left screen edge. Start a drawer swipe slightly inside the Settings content; start Back from the physical edge.
- The edge reservation consumes no pointer input, so scrolling and taps remain unaffected.
- Release verification uses isolated, memory-bounded Gradle invocations to avoid Kotlin compiler stalls.

## Release assets

- Optimized release APK
- Release AAB
- Explicit `Arbor-0.20.13-source.zip`
- Explicit `Arbor-0.20.13-source.tar.gz`
- SHA-256 checksums covering every attached asset

Developer settings and the performance overlay remain available in the optimized release build.
""")
