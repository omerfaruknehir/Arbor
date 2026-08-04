from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path} but found {count}: {old!r}")
    path.write_text(text.replace(old, new, 1))


onboarding = Path("app/src/main/java/app/xylune/chat/ui/OnboardingScreen.kt")
replace_once(
    onboarding,
    """                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 0,
""",
    """                modifier = Modifier.weight(1f).fillMaxWidth(),
                pageSpacing = 16.dp,
                beyondViewportPageCount = 0,
""",
)

chat = Path("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")
replace_once(
    chat,
    "import androidx.compose.ui.unit.IntOffset\n",
    "import androidx.compose.ui.unit.IntOffset\nimport androidx.compose.ui.zIndex\n",
)
replace_once(
    chat,
    """                        onSetUpProvider = viewModel::openProviderSetup,
                        modifier = Modifier.padding(
""",
    """                        onSetUpProvider = viewModel::openProviderSetup,
                        modifier = Modifier.zIndex(1f).padding(
""",
)

tests = Path("app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt")
replace_once(
    tests,
    "        assertTrue(source.contains(\"userScrollEnabled = true\"))\n",
    """        assertTrue(source.contains("userScrollEnabled = true"))
        assertTrue(source.contains("pageSpacing = 16.dp"))
""",
)
replace_once(
    tests,
    """    fun `chat exposes provider and Linux setup states`() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        assertTrue(chat.contains("Connect a model provider"))
        assertTrue(chat.contains("Set up a provider to start"))
        assertTrue(chat.contains("Linux workspace not installed"))
        assertTrue(chat.contains("Manage Linux workspace"))
    }
""",
    """    fun `chat exposes provider and Linux setup states`() {
        val chat = java.io.File("src/main/java/app/xylune/chat/ui/ChatScreen.kt").readText()
        val viewModel = java.io.File("src/main/java/app/xylune/chat/ui/ChatViewModel.kt").readText()
        assertTrue(chat.contains("Connect a model provider"))
        assertTrue(chat.contains("Set up a provider to start"))
        assertTrue(chat.contains("onSetUpProvider = viewModel::openProviderSetup"))
        assertTrue(chat.contains("modifier = Modifier.zIndex(1f).padding("))
        assertTrue(viewModel.contains("fun openProviderSetup()"))
        assertTrue(viewModel.contains("openSettingsRoute(SettingsRoute.PROVIDERS)"))
        assertTrue(viewModel.contains("screen.value = Screen.SETTINGS"))
        assertTrue(chat.contains("Linux workspace not installed"))
        assertTrue(chat.contains("Manage Linux workspace"))
    }
""",
)

gradle = Path("app/build.gradle.kts")
replace_once(
    gradle,
    '        versionCode = 176\n        versionName = "0.23.7"\n',
    '        versionCode = 177\n        versionName = "0.23.8"\n',
)

changelog = Path("CHANGELOG.md")
changelog.write_text(
    """## 0.23.8 — 2026-08-04

- Add a visible 16 dp gap between setup pager pages while preserving direct swipe navigation and opaque page surfaces.
- Keep the empty-chat provider action above the empty message list so its setup button receives taps and opens Providers & models.

"""
    + changelog.read_text()
)

Path("docs/releases/RELEASE_NOTES_0.23.8.md").write_text(
    """# Xylune 0.23.8

## Setup pager spacing

- Adds a 16 dp gap between adjacent setup pages, making the swipe boundary visually clear without reintroducing fades or overlapping content.
- Keeps each page fully opaque and directly draggable.

## Empty-chat provider setup

- Fixes the empty-chat provider action being covered by the empty `LazyColumn` hit target.
- Both the central **Set up a provider** action and the model-selector setup entry now lead to **Settings → Providers & models**.

## Validation

- Adds regression checks for pager spacing, empty-state hit-test ordering, and direct provider-settings navigation.
"""
)

Path(".github/workflows/diagnose-empty-provider.yml").unlink()
Path(".github/scripts/apply-setup-spacing-empty-provider.py").unlink()
