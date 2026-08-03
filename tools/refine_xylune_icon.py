#!/usr/bin/env python3
"""Refine Xylune's mark by crossing the original Arbor A outer stems."""

from __future__ import annotations

import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

PATH_REPLACEMENTS = {
    # First X stroke: derived from the original A's left outer stem.
    "M30,28C42,43 58,64 78,82": "M28,82C38,66 70,43 80,27",
    "M30 28C42 43 58 64 78 82": "M28 82C38 66 70 43 80 27",
    # Second X stroke: derived from the original A's right outer stem.
    "M78,28C66,43 50,64 30,82": "M28,27C38,43 70,66 80,82",
    "M78 28C66 43 50 64 30 82": "M28 27C38 43 70 66 80 82",
    # Leaf branch and leaf, attached close to the upper-right stem.
    "M69,35C74,31 78,27 82,24": "M72,36C76,32 79,28 82,25",
    "M69 35C74 31 78 27 82 24": "M72 36C76 32 79 28 82 25",
    "M68,32C74,22 84,19 92,25C87,35 78,39 69,35Z": "M78,27C82,20 89,19 94,24C90,31 84,33 79,30Z",
    "M68 32C74 22 84 19 92 25C87 35 78 39 69 35Z": "M78 27C82 20 89 19 94 24C90 31 84 33 79 30Z",
}

GRADIENT_RE = re.compile(
    r'android:startX="30"\s*\n\s*android:startY="28"\s*\n\s*'
    r'android:endX="78"\s*\n\s*android:endY="82"'
)
GRADIENT_REPLACEMENT = (
    'android:startX="28"\n'
    '                android:startY="82"\n'
    '                android:endX="80"\n'
    '                android:endY="27"'
)


def tracked_files() -> list[Path]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [ROOT / item.decode("utf-8") for item in raw.split(b"\0") if item]


def should_edit(path: Path) -> bool:
    relative = path.relative_to(ROOT).as_posix()
    return (
        relative.startswith("app/src/main/res/drawable") and path.name.startswith("ic_xylune_") and path.suffix == ".xml"
    ) or relative in {
        "branding/xylune-logo.svg",
        "licenses/icons/xylune.svg",
    }


def main() -> None:
    banner = ROOT / "branding/xylune-banner.png"
    banner_before = banner.read_bytes()
    changed: list[str] = []

    for path in tracked_files():
        if not path.is_file() or not should_edit(path):
            continue
        text = path.read_text(encoding="utf-8")
        updated = text
        for old, new in PATH_REPLACEMENTS.items():
            updated = updated.replace(old, new)
        updated = GRADIENT_RE.sub(GRADIENT_REPLACEMENT, updated)
        if updated != text:
            path.write_text(updated, encoding="utf-8")
            changed.append(path.relative_to(ROOT).as_posix())

    if banner.read_bytes() != banner_before:
        raise RuntimeError("The repository banner changed during the icon-only refinement")

    if len(changed) < 8:
        raise RuntimeError(f"Expected to update all icon families, but changed only {len(changed)} files: {changed}")

    required = {
        "M28,82C38,66 70,43 80,27",
        "M28,27C38,43 70,66 80,82",
        "M72,36C76,32 79,28 82,25",
        "M78,27C82,20 89,19 94,24C90,31 84,33 79,30Z",
    }
    foreground = (ROOT / "app/src/main/res/drawable/ic_xylune_foreground.xml").read_text(encoding="utf-8")
    missing = sorted(token for token in required if token not in foreground)
    if missing:
        raise RuntimeError("Refined foreground is missing geometry: " + ", ".join(missing))

    stale = [token for token in PATH_REPLACEMENTS if token in foreground]
    if stale:
        raise RuntimeError("Old generated X geometry remains in the foreground: " + ", ".join(stale))

    print(f"Refined Xylune icon geometry in {len(changed)} files; banner preserved unchanged.")
    for relative in changed:
        print(relative)


if __name__ == "__main__":
    main()
