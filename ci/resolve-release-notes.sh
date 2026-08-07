#!/usr/bin/env bash
set -euo pipefail

version="${1:?Usage: resolve-release-notes.sh VERSION}"
specific="docs/releases/RELEASE_NOTES_${version}.md"

if [[ -s "$specific" ]]; then
  printf '%s\n' "$specific"
  exit 0
fi

output="${RUNNER_TEMP:-/tmp}/xylune-release-notes-${version}.md"
python3 - "$version" "$output" <<'PY'
from pathlib import Path
import re
import sys

version, output = sys.argv[1:]
text = Path("CHANGELOG.md").read_text()
pattern = re.compile(
    rf"(?ms)^##\s+{re.escape(version)}(?:\s+—[^\n]*)?\n.*?(?=^##\s+|\Z)"
)
match = pattern.search(text)
if not match:
    raise SystemExit(
        f"No docs/releases/RELEASE_NOTES_{version}.md and no CHANGELOG section for {version}. "
        "Refusing to publish the entire changelog as one release."
    )
Path(output).write_text(match.group(0).rstrip() + "\n")
PY

printf '%s\n' "$output"
