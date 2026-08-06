#!/usr/bin/env python3
"""Keep README release references version-independent.

The README should point at GitHub's permanent `releases/latest` route rather
than embedding a version number or a versioned APK/release-notes filename.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

README = Path("README.md")
LATEST_RELEASE = "https://github.com/omerfaruknehir/Xylune/releases/latest"


def normalized_readme(text: str) -> str:
    text = re.sub(
        r"(?m)^Current version: \*\*[^\n*]+\*\*$",
        f"Current release: [latest GitHub Release]({LATEST_RELEASE})",
        text,
    )
    text = re.sub(
        r"(?m)^1\. Download `Xylune-[^`]+-release\.apk` from the "
        r"\[latest GitHub Release\]\([^\n)]+\)\.$",
        f"1. Open the [latest GitHub Release]({LATEST_RELEASE}) and download the APK asset.",
        text,
    )
    text = re.sub(
        r"(?m)^- \[Latest release notes\]\([^\n)]+\)$",
        f"- [Latest release notes]({LATEST_RELEASE})",
        text,
    )
    return text


def stale_version_references(text: str) -> list[str]:
    patterns = {
        "hard-coded current version": r"(?m)^Current version: \*\*[^\n*]+\*\*$",
        "versioned APK filename": r"Xylune-\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?-release\.apk",
        "versioned release-notes path": r"RELEASE_NOTES_\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?\.md",
    }
    return [label for label, pattern in patterns.items() if re.search(pattern, text)]


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true", help="rewrite README.md in place")
    mode.add_argument("--check", action="store_true", help="fail if README.md needs normalization")
    args = parser.parse_args()

    original = README.read_text(encoding="utf-8")
    normalized = normalized_readme(original)
    stale = stale_version_references(normalized)

    if stale:
        print("README still contains unsupported release-version references:", file=sys.stderr)
        for item in stale:
            print(f"- {item}", file=sys.stderr)
        return 1

    if args.write:
        if normalized != original:
            README.write_text(normalized, encoding="utf-8")
            print("Updated README release links.")
        else:
            print("README release links are already current.")
        return 0

    if normalized != original:
        print(
            "README release references are stale. Run "
            "`python3 ci/sync-readme-release-links.py --write`.",
            file=sys.stderr,
        )
        return 1

    print("README release links are version-independent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
