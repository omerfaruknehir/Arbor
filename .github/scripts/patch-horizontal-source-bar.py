from pathlib import Path

rich_path = Path("app/src/main/java/app/xylune/chat/ui/RichMessage.kt")
rich = rich_path.read_text()

old = "    val sourceFooterMarkdown = remember(renderedText) { sourceReferencesFooterMarkdown(renderedText) }\n"
new = "    val sourceReferences = remember(renderedText) { extractSourceReferences(renderedText) }\n"
if rich.count(old) != 1:
    raise SystemExit(f"Expected one source footer state anchor, found {rich.count(old)}")
rich = rich.replace(old, new, 1)

old = '''        if (!renderStreaming && sourceFooterMarkdown.isNotBlank()) {
            MarkdownBlock(
                markwon = markwon,
                markdown = sourceFooterMarkdown,
                key = "$operationScope:sources-footer",
                streaming = false,
            )
        }
'''
new = '''        if (!renderStreaming && sourceReferences.isNotEmpty()) {
            SourceReferenceBar(
                sources = sourceReferences,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
'''
if rich.count(old) != 1:
    raise SystemExit(f"Expected one rendered source footer block, found {rich.count(old)}")
rich = rich.replace(old, new, 1)

start = rich.index("internal fun sourceReferencesFooterMarkdown(markdown: String): String {")
end = rich.index("private object XyluneMarkwonCache", start)
rich = rich[:start] + rich[end:]
rich_path.write_text(rich)

test_path = Path("app/src/test/java/app/xylune/chat/ui/RichMessageReferenceTest.kt")
tests = test_path.read_text()
old = '''    @Test fun sourceFooterIsOrderedAndDeduplicatedByDestination() {
        val markdown = """First [[PNA|https://example.com/a]].
Second [[Example|https://example.com/a]] and [[Other|https://example.com/b]]."""
        val footer = sourceReferencesFooterMarkdown(markdown)
        assertTrue(footer.startsWith("**Sources**"))
        assertEquals(1, Regex("https://example.com/a").findAll(footer).count())
        assertTrue(footer.indexOf("PNA") < footer.indexOf("Other"))
    }
'''
new = '''    @Test fun sourceReferencesAreOrderedAndDeduplicatedByDestination() {
        val markdown = """First [[PNA|https://example.com/a]].
Second [[Example|https://example.com/a]] and [[Other|https://example.com/b]]."""
        val sources = extractSourceReferences(markdown)
        assertEquals(2, sources.size)
        assertEquals("PNA", sources[0].label)
        assertEquals("https://example.com/a", sources[0].target)
        assertEquals("Other", sources[1].label)
        assertEquals("https://example.com/b", sources[1].target)
    }
'''
if tests.count(old) != 1:
    raise SystemExit(f"Expected one source footer test, found {tests.count(old)}")
tests = tests.replace(old, new, 1)
test_path.write_text(tests)

regression_path = Path("app/src/test/java/app/xylune/chat/ui/SourceReferenceBarRegressionTest.kt")
regression_path.write_text('''package app.xylune.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceReferenceBarRegressionTest {
    private fun repositoryFile(path: String): File = sequenceOf(File(path), File("..", path))
        .firstOrNull(File::isFile)
        ?: error("Could not locate repository file: $path")

    @Test
    fun `bottom source bar stays horizontal scrollable and opens anchored previews`() {
        val source = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/SourceReferenceBar.kt",
        ).readText()
        val richMessage = repositoryFile(
            "app/src/main/java/app/xylune/chat/ui/RichMessage.kt",
        ).readText()

        assertTrue(source.contains("LowSensitivityHorizontalScroll"))
        assertTrue(source.contains("horizontalArrangement = Arrangement.spacedBy(8.dp)"))
        assertTrue(source.contains("AnchoredLinkPreview"))
        assertTrue(source.contains("anchorBoundsInWindow = anchor"))
        assertTrue(source.contains("widthIn(max = 230.dp)"))
        assertTrue(richMessage.contains("SourceReferenceBar("))
        assertTrue(!richMessage.contains("sourceReferencesFooterMarkdown"))
    }
}
''')

build_path = Path("app/build.gradle.kts")
build = build_path.read_text()
if build.count("versionCode = 197") != 1 or build.count('versionName = "0.24.8"') != 1:
    raise SystemExit("Unexpected current app version")
build = build.replace("versionCode = 197", "versionCode = 198", 1)
build = build.replace('versionName = "0.24.8"', 'versionName = "0.24.9"', 1)
build_path.write_text(build)

changelog_path = Path("CHANGELOG.md")
changelog = changelog_path.read_text()
changelog_path.write_text('''## 0.24.9 — 2026-08-06

- Replace the vertical Markdown source list with a dedicated horizontal source bar at the bottom of completed responses.
- Keep source cards compact in one scrollable lane, with numbered pills, labels, and domains.
- Open the same anchored title/description preview and explicit Open action from both inline citations and bottom source pills.
- Preserve source order and deduplicate repeated destinations.

''' + changelog)

release_notes = Path("docs/releases/RELEASE_NOTES_0.24.9.md")
release_notes.write_text('''# Xylune 0.24.9

## Horizontal source bar

Completed AI responses now end with a dedicated **Sources** bar instead of a vertical Markdown list. Source pills stay in one horizontal lane and can be swiped when they overflow the message width.

Each pill shows its first-use number, source label, and domain. Tapping it opens the same anchored preview used by inline citations, including fetched page title, description, destination, and an explicit **Open** button.

Repeated links are deduplicated while retaining the order in which sources first appeared in the answer.
''')
