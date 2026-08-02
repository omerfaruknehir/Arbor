#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/java/app/arbor/chat/ui/SearchScreen.kt"
content = path.read_text()
content = content.replace("import androidx.compose.foundation.layout.weight\n", "")
path.write_text(content)
print("Removed invalid explicit ColumnScope weight import")
