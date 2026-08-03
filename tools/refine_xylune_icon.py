#!/usr/bin/env python3
"""Temporarily restore Arbor's visual mark while keeping Xylune internals."""

from __future__ import annotations

import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_REF = "origin/main"


def git_bytes(*args: str) -> bytes:
    return subprocess.check_output(["git", *args], cwd=ROOT)


def restore_from_base(source: str, destination: str) -> None:
    target = ROOT / destination
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(git_bytes("show", f"{BASE_REF}:{source}"))


def main() -> None:
    source_paths = git_bytes(
        "ls-tree", "-r", "--name-only", BASE_REF, "--", "app/src/main/res"
    ).decode("utf-8").splitlines()

    arbor_icons = [
        path
        for path in source_paths
        if Path(path).name.startswith("ic_arbor_") and path.endswith(".xml")
    ]
    if len(arbor_icons) < 8:
        raise RuntimeError(f"Expected the complete Arbor icon family, found {len(arbor_icons)} files")

    restored: list[str] = []
    for source in arbor_icons:
        source_path = Path(source)
        destination = source_path.with_name(
            source_path.name.replace("ic_arbor_", "ic_xylune_", 1)
        ).as_posix()
        if not (ROOT / destination).exists():
            raise RuntimeError(f"Missing renamed Xylune icon target: {destination}")
        restore_from_base(source, destination)
        restored.append(destination)

    # Keep Xylune-named metadata paths, but use the proven Arbor artwork until
    # a replacement mark is explicitly approved.
    restore_from_base("branding/arbor-logo.svg", "branding/xylune-logo.svg")
    restore_from_base("licenses/icons/arbor.svg", "licenses/icons/xylune.svg")
    restored.extend(["branding/xylune-logo.svg", "licenses/icons/xylune.svg"])

    foreground = (ROOT / "app/src/main/res/drawable/ic_xylune_foreground.xml").read_text(
        encoding="utf-8"
    )
    required_arbor_geometry = {
        "M28,82C35,64 43,45 53,28",
        "M55,28C65,45 73,64 80,82",
        "M40,64C49,60 59,60 68,64",
        "M54,29C60,21 69,19 76,24C73,33 65,37 55,33Z",
    }
    missing = sorted(token for token in required_arbor_geometry if token not in foreground)
    if missing:
        raise RuntimeError("Restored foreground is missing Arbor geometry: " + ", ".join(missing))

    forbidden_x_geometry = {
        "M28,82C38,66 70,43 80,27",
        "M28,27C38,43 70,66 80,82",
    }
    stale = sorted(token for token in forbidden_x_geometry if token in foreground)
    if stale:
        raise RuntimeError("Unapproved X geometry remains: " + ", ".join(stale))

    print(f"Restored Arbor artwork into {len(restored)} Xylune asset targets.")
    for path in restored:
        print(path)


if __name__ == "__main__":
    main()
