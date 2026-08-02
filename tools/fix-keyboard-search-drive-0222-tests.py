#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/test/java/app/arbor/chat/ui/BlurCompatibilityTest.kt"
content = path.read_text()
old = '        assertTrue(search.contains("blurArea = STANDARD_TOP_PANEL_HEIGHT_DP.dp"))\n'
new = '''        assertTrue(search.contains("TopAppBar("))
        assertFalse(search.contains("CollapsingTranslucentTopBar"))
'''
if content.count(old) != 1:
    raise RuntimeError("Expected one legacy Search blur assertion")
path.write_text(content.replace(old, new, 1))
print("Updated Search header regression expectation")
