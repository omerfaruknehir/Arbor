#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "app/build.gradle.kts"
content = path.read_text()
replacements = {
    'Regex("github\\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?(?:[/?#].*)?$")':
        'Regex("""github\\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?(?:[/?#].*)?$""")',
    'Regex("(?m)^\\s*url\\s*=\\s*(.+?)\\s*$")':
        'Regex("""(?m)^\\s*url\\s*=\\s*(.+?)\\s*$""")',
}
for old, new in replacements.items():
    if content.count(old) != 1:
        raise RuntimeError(f"Expected one generated Gradle regex: {old}")
    content = content.replace(old, new, 1)
path.write_text(content)
print("Corrected Gradle Kotlin regex literals")
