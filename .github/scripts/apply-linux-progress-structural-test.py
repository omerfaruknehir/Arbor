from pathlib import Path

path = Path("app/src/test/java/app/xylune/chat/ui/OnboardingFlowTest.kt")
text = path.read_text()
old = '''        assertTrue(workspace.contains("Step \\$step of \\$total"))
        assertTrue(workspace.contains("Elapsed:"))
        assertTrue(workspace.contains("Linux data on disk:"))
'''
new = '''        assertTrue(workspace.contains("Step \\$step of \\$total"))
        assertTrue(workspace.contains("Elapsed \\${formatSetupDuration(elapsedMs)}"))
        assertTrue(workspace.contains(".height(10.dp)"))
        assertTrue(workspace.contains("repeat(total)"))
        assertTrue(workspace.contains("Linux data on disk:"))
'''
if text.count(old) != 1:
    raise SystemExit(f"Expected one Linux progress assertion block, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
