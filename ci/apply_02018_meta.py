from pathlib import Path

root = Path.cwd()


def edit(rel: str, old: str, new: str, count: int = 1) -> None:
    path = root / rel
    source = path.read_text()
    if source.count(old) < count:
        raise SystemExit(f"Missing expected source in {rel}: {old[:100]!r}")
    path.write_text(source.replace(old, new, count))


edit(
    "app/build.gradle.kts",
    '        versionCode = 143\n        versionName = "0.20.17"',
    '        versionCode = 144\n        versionName = "0.20.18"',
)
edit(
    "CHANGELOG.md",
    "# Changelog\n\n",
    '''# Changelog

## 0.20.18 — 2026-07-31

- Render the drawer, onboarding, and Arbor license entry from the exact active launcher-icon drawable instead of a separately approximated logo.
- Move launcher alias mutation into an isolated `:launcher_icon` process so OEM package-manager behavior cannot tear down the foreground Arbor process.
- Give completed user messages a static full-source rendering path and show the complete plain-text fallback while Markdown parsing finishes, preventing prefix-only message bubbles while preserving the editable source.
- Add regression coverage for icon fidelity, isolated alias switching, dynamic license branding, and completed-message rendering.

''',
)
(root / "docs/releases/RELEASE_NOTES_0.20.18.md").write_text('''# Arbor 0.20.18

## Fixed

- The Arbor mark in the navigation drawer now uses the exact same palette-specific artwork as the selected launcher icon.
- The Arbor entry in Offline licenses follows the active launcher icon instead of staying on the default green asset.
- Launcher icon changes are applied by a dedicated lightweight process, insulating the foreground app task from OEM component-toggle restarts.
- Completed user messages no longer remain stuck showing only an early prefix while their full source is still available in Edit.

## Build

- `versionName`: `0.20.18`
- `versionCode`: `144`
''')

path = root / "app/src/test/java/app/arbor/chat/ui/ArborMarkTest.kt"
path.write_text('''package app.arbor.chat.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArborMarkTest {
    @Test
    fun `in app Arbor marks reuse exact launcher artwork`() {
        val visuals = File("src/main/java/app/arbor/chat/ui/PaletteVisuals.kt").readText()
        assertTrue(visuals.contains("LocalArborIconPalette"))
        assertTrue(visuals.contains("painterResource(palette.launcherPreviewDrawable)"))
        assertFalse(visuals.contains("Canvas("))
        assertFalse(visuals.contains("drawPath("))

        val main = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        assertTrue(main.contains("LocalArborIconPalette provides"))
        assertTrue(main.contains("matchLauncherIconToPalette"))
    }

    @Test
    fun `drawer onboarding and licenses share the dynamic Arbor mark`() {
        val onboarding = File("src/main/java/app/arbor/chat/ui/OnboardingScreen.kt").readText()
        val sidebar = File("src/main/java/app/arbor/chat/ui/ConversationSidebar.kt").readText()
        val licenses = File("src/main/java/app/arbor/chat/ui/LicenseCatalogScreen.kt").readText()
        assertTrue(onboarding.contains("ArborMark("))
        assertTrue(sidebar.contains("ArborMark("))
        assertTrue(licenses.contains("component.id == \\"arbor\\""))
        assertTrue(licenses.contains("ArborMark("))
    }
}
''')

edit(
    "app/src/test/java/app/arbor/chat/settings/LauncherIconManagerTest.kt",
    '''        assertTrue(source.contains("applyEnableFirst"))
    }
}''',
    '''        assertTrue(source.contains("applyEnableFirst"))
        assertTrue(source.contains("LauncherIconSwitchReceiver::class.java"))
        assertTrue(source.contains("applyDirect"))

        val receiver = File("src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt").readText()
        assertTrue(receiver.contains("BroadcastReceiver"))
        assertTrue(receiver.contains("LauncherIconManager.applyDirect"))
    }
}''',
)

(root / "app/src/test/java/app/arbor/chat/ui/UserMessageRenderingTest.kt").write_text('''package app.arbor.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UserMessageRenderingTest {
    @Test
    fun `completed user messages bypass streaming parser state`() {
        val chat = File("src/main/java/app/arbor/chat/ui/ChatScreen.kt").readText()
        val rich = File("src/main/java/app/arbor/chat/ui/RichMessage.kt").readText()
        assertTrue(chat.contains("staticContent = user"))
        assertTrue(rich.contains("val visibleBlocks = if (staticContent) staticBlocks else blocks"))
    }

    @Test
    fun `markdown view displays the complete fallback until parsing finishes`() {
        val rich = File("src/main/java/app/arbor/chat/ui/RichMessage.kt").readText()
        assertTrue(rich.contains("remember(markwon, markdown)"))
        assertTrue(rich.contains("markdownRenderFallbackText(markdown)"))
        assertTrue(rich.contains("renderedAsFallback"))
    }
}
''')
