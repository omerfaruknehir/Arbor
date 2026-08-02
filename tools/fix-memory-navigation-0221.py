from pathlib import Path

memory = Path("app/src/main/java/app/arbor/chat/chat/MemoryManagement.kt")
text = memory.read_text()
old = "        val candidates = buildList {\n"
new = "        val candidates: List<MemoryEntity> = buildList {\n"
if text.count(old) != 1:
    raise SystemExit(f"memory candidate declaration: expected one match, found {text.count(old)}")
memory.write_text(text.replace(old, new, 1))

settings = Path("app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt")
text = settings.read_text()
old = "private val LocalSettingsTopAppBarState = compositionLocalOf<TopAppBarState?> { null }\n"
new = "@OptIn(ExperimentalMaterial3Api::class)\nprivate val LocalSettingsTopAppBarState = compositionLocalOf<TopAppBarState?> { null }\n"
if text.count(old) != 1:
    raise SystemExit(f"top app bar local: expected one match, found {text.count(old)}")
text = text.replace(old, new, 1)
old = "@Composable\ninternal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {\n"
new = "@OptIn(ExperimentalMaterial3Api::class)\n@Composable\ninternal fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {\n"
if text.count(old) != 1:
    raise SystemExit(f"settings page opt-in: expected one match, found {text.count(old)}")
settings.write_text(text.replace(old, new, 1))
