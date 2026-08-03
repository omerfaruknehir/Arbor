#!/usr/bin/env python3
from pathlib import Path

path = Path(__file__).with_name("apply-xylune-rebrand-0230.py")
content = path.read_text()
replacements = {
    'git("rm", "branding/xylune-banner.png")':
        'git("rm", "-f", "branding/xylune-banner.png")',
    'REPLACEMENTS = (\n    ("omerfaruknehir/Arbor", "omerfaruknehir/Xylune"),':
        'REPLACEMENTS = (\n    ("Arbour", "Xylune"),\n    ("arbour", "xylune"),\n    ("ARBOUR", "XYLUNE"),\n    ("omerfaruknehir/Arbor", "omerfaruknehir/Xylune"),',
    "Xylune starts with a clean application identity; no Arbor package, storage, or backup compatibility is retained.":
        "Xylune starts with a clean application identity; no legacy package, storage, or backup compatibility is retained.",
    "Xylune is the complete successor identity to Arbor, pronounced **“Zy-loon.”**":
        "Xylune is pronounced **“Zy-loon.”**",
    "without Arbor import aliases.":
        "without legacy import aliases.",
    "No Arbor compatibility aliases are retained because the project had no external users before the rename.":
        "No legacy compatibility aliases are retained because the project had no external users before this identity was established.",
}
for old, new in replacements.items():
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one rebrand fragment, found {count}: {old}")
    content = content.replace(old, new, 1)
path.write_text(content)
print("Corrected banner removal, British spelling, and legacy brand references")
