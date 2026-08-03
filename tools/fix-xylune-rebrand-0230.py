#!/usr/bin/env python3
from pathlib import Path

path = Path(__file__).with_name("apply-xylune-rebrand-0230.py")
content = path.read_text()
old = 'git("rm", "branding/xylune-banner.png")'
new = 'git("rm", "-f", "branding/xylune-banner.png")'
if content.count(old) != 1:
    raise RuntimeError("Expected one banner removal command")
path.write_text(content.replace(old, new, 1))
print("Corrected staged banner removal")
