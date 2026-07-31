from pathlib import Path

root = Path.cwd()


def edit(rel: str, old: str, new: str, count: int = 1) -> None:
    path = root / rel
    source = path.read_text()
    if source.count(old) < count:
        raise SystemExit(f"Missing expected source in {rel}: {old[:100]!r}")
    path.write_text(source.replace(old, new, count))


# Completed user messages render from their immutable full source instead of
# inheriting the assistant streaming parser's transient state.
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    "    displayOnly: Boolean = false,\n",
    "    displayOnly: Boolean = false,\n    staticContent: Boolean = false,\n",
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    '''    val markwon = remember(context.applicationContext) { ArborMarkwonCache.get(context.applicationContext) }
    Column(''',
    '''    val staticBlocks = remember(operationScope, renderedText, staticContent) {
        if (!staticContent) emptyList()
        else parseBlocks(renderedText, streaming = false).mapIndexed { index, block ->
            StableRichBlock("static-$index", block, liveTail = false)
        }
    }
    val visibleBlocks = if (staticContent) staticBlocks else blocks
    val markwon = remember(context.applicationContext) { ArborMarkwonCache.get(context.applicationContext) }
    Column(''',
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    "        blocks.forEach { parsed ->\n",
    "        visibleBlocks.forEach { parsed ->\n",
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    "    var parsedMarkdown by remember(markwon) { mutableStateOf<ParsedMarkdownSource?>(null) }",
    "    var parsedMarkdown by remember(markwon, markdown) { mutableStateOf<ParsedMarkdownSource?>(null) }",
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    '''            val ready = parsedMarkdown
            if (ready != null && (view.renderedSource != ready.source || view.renderedStyleKey != styleKey)) {''',
    '''            val ready = parsedMarkdown
            if (ready == null) {
                if (view.renderedSource != markdown || !view.renderedAsFallback || view.renderedStyleKey != styleKey) {
                    view.setText(markdownRenderFallbackText(markdown), TextView.BufferType.SPANNABLE)
                    view.renderedSource = markdown
                    view.renderedStyleKey = styleKey
                    view.renderedAsFallback = true
                }
            } else if (
                view.renderedSource != ready.source ||
                view.renderedStyleKey != styleKey ||
                view.renderedAsFallback
            ) {''',
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    '''                        onClick = onReference,
                    )
''',
    '''                        onClick = onReference,
                    )
                    view.renderedAsFallback = false
''',
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    '''                        markdownRenderFallbackText(ready.source),
                        TextView.BufferType.SPANNABLE,
                    )
                }
                view.renderedSource''',
    '''                        markdownRenderFallbackText(ready.source),
                        TextView.BufferType.SPANNABLE,
                    )
                    view.renderedAsFallback = true
                }
                view.renderedSource''',
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    "    var appliedStyleKey: Int = 0\n",
    "    var appliedStyleKey: Int = 0\n    var renderedAsFallback: Boolean = false\n",
)
edit(
    "app/src/main/java/app/arbor/chat/ui/RichMessage.kt",
    "        appliedStyleKey = 0\n",
    "        appliedStyleKey = 0\n        renderedAsFallback = false\n",
)
edit(
    "app/src/main/java/app/arbor/chat/ui/ChatScreen.kt",
    "                        streaming = animateStreaming,\n                        onRunPython",
    "                        streaming = animateStreaming,\n                        staticContent = user,\n                        onRunPython",
)
