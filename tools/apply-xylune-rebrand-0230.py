#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

TEXT_EXCLUDED_PREFIXES = (
    "app/src/main/python/pip/_vendor/",
    "third_party/",
    "licenses/texts/",
)

REPLACEMENTS = (
    ("omerfaruknehir/Arbor", "omerfaruknehir/Xylune"),
    ("app.arbor.chat", "app.xylune.chat"),
    ("app/arbor/chat", "app/xylune/chat"),
    ("ARBOR", "XYLUNE"),
    ("Arbor", "Xylune"),
    ("arbor", "xylune"),
)


def git(*args: str, capture: bool = False) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE if capture else None,
    )
    return result.stdout if capture else ""


def renamed(value: str) -> str:
    for old, new in REPLACEMENTS:
        value = value.replace(old, new)
    return value


def tracked_files() -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [item.decode() for item in raw.split(b"\0") if item]


def move_branded_paths() -> None:
    paths = tracked_files()
    moves: list[tuple[str, str]] = []
    for old in paths:
        new = renamed(old)
        if new != old:
            moves.append((old, new))
    for old, new in sorted(moves, key=lambda pair: pair[0].count("/"), reverse=True):
        source = ROOT / old
        if not source.exists():
            continue
        destination = ROOT / new
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            raise RuntimeError(f"Refusing path collision: {old} -> {new}")
        git("mv", old, new)


def looks_text(path: Path) -> bool:
    try:
        sample = path.read_bytes()[:8192]
    except OSError:
        return False
    return b"\0" not in sample


def replace_first_party_text() -> None:
    for relative in tracked_files():
        if relative.startswith(TEXT_EXCLUDED_PREFIXES):
            continue
        path = ROOT / relative
        if not path.is_file() or not looks_text(path):
            continue
        try:
            content = path.read_text()
        except UnicodeDecodeError:
            continue
        updated = renamed(content)
        if updated != content:
            path.write_text(updated)


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    content = target.read_text()
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:120]!r}")
    target.write_text(content.replace(old, new, 1))


def set_version() -> None:
    path = ROOT / "app/build.gradle.kts"
    content = path.read_text()
    content, code_count = re.subn(r"versionCode\s*=\s*\d+", "versionCode = 170", content, count=1)
    content, name_count = re.subn(r'versionName\s*=\s*"[^"]+"', 'versionName = "0.23.0"', content, count=1)
    if code_count != 1 or name_count != 1:
        raise RuntimeError("Could not update Xylune version")
    path.write_text(content)


def vector_header(aapt: bool = True) -> str:
    aapt_attr = '\n    xmlns:aapt="http://schemas.android.com/aapt"' if aapt else ""
    return (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"'
        f'{aapt_attr}\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
    )


def x_artwork(primary_start: str, primary_end: str, secondary: str, leaf: str) -> str:
    return f'''    <path
        android:fillColor="@android:color/transparent"
        android:strokeWidth="11"
        android:strokeLineCap="round"
        android:pathData="M29,29L79,79">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="29"
                android:startY="29"
                android:endX="79"
                android:endY="79"
                android:startColor="{primary_start}"
                android:endColor="{primary_end}" />
        </aapt:attr>
    </path>
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="{secondary}"
        android:strokeWidth="11"
        android:strokeLineCap="round"
        android:pathData="M79,29L29,79" />
    <path
        android:fillColor="{leaf}"
        android:pathData="M70,34C75,21 87,17 96,24C92,37 82,43 70,38Z" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="{secondary}"
        android:strokeWidth="2.5"
        android:strokeLineCap="round"
        android:pathData="M74,36C82,32 88,27 93,23" />
'''


def extract_hex_colors(content: str) -> list[str]:
    return re.findall(r"#[0-9A-Fa-f]{6,8}", content)


def rebuild_brand_vectors() -> None:
    drawable = ROOT / "app/src/main/res/drawable"

    for path in drawable.glob("ic_xylune_foreground*.xml"):
        colors = extract_hex_colors(path.read_text())
        unique: list[str] = []
        for color in colors:
            if color not in unique:
                unique.append(color)
        defaults = ["#86DFB8", "#DDFBEA", "#F1FFF7", "#F4C761"]
        palette = (unique + defaults)[:4]
        path.write_text(vector_header() + x_artwork(*palette) + "</vector>\n")

    monochrome = drawable / "ic_xylune_monochrome.xml"
    if monochrome.exists():
        monochrome.write_text(
            vector_header(aapt=False)
            + '''    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#000000"
        android:strokeWidth="11"
        android:strokeLineCap="round"
        android:pathData="M29,29L79,79" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="#000000"
        android:strokeWidth="11"
        android:strokeLineCap="round"
        android:pathData="M79,29L29,79" />
    <path
        android:fillColor="#000000"
        android:pathData="M70,34C75,21 87,17 96,24C92,37 82,43 70,38Z" />
'''
            + "</vector>\n"
        )

    for path in drawable.glob("ic_xylune_mark*.xml"):
        colors = extract_hex_colors(path.read_text())
        unique: list[str] = []
        for color in colors:
            if color not in unique:
                unique.append(color)
        defaults = ["#083A2C", "#0C684F", "#86DFB8", "#DDFBEA", "#F1FFF7", "#F4C761"]
        palette = (unique + defaults)[:6]
        bg_start, bg_end, primary_start, primary_end, secondary, leaf = palette
        path.write_text(
            vector_header()
            + f'''    <path android:pathData="M24,0H84C97.3,0 108,10.7 108,24V84C108,97.3 97.3,108 84,108H24C10.7,108 0,97.3 0,84V24C0,10.7 10.7,0 24,0Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="15"
                android:startY="8"
                android:endX="96"
                android:endY="101"
                android:startColor="{bg_start}"
                android:endColor="{bg_end}" />
        </aapt:attr>
    </path>
'''
            + x_artwork(primary_start, primary_end, secondary, leaf)
            + "</vector>\n"
        )


def write_branding_assets() -> None:
    old_png = ROOT / "branding/xylune-banner.png"
    if old_png.exists():
        git("rm", "branding/xylune-banner.png")

    banner = ROOT / "branding/xylune-banner.svg"
    banner.parent.mkdir(parents=True, exist_ok=True)
    banner.write_text('''<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="480" viewBox="0 0 1600 480" role="img" aria-labelledby="title desc">
  <title id="title">Xylune</title>
  <desc id="desc">Xylune wordmark with a geometric X and leaf.</desc>
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#071c2a"/>
      <stop offset="0.5" stop-color="#0b423c"/>
      <stop offset="1" stop-color="#315675"/>
    </linearGradient>
    <linearGradient id="xstroke" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#84e0bb"/>
      <stop offset="1" stop-color="#edfdf6"/>
    </linearGradient>
    <linearGradient id="glass" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color="#ffffff" stop-opacity=".18"/>
      <stop offset="1" stop-color="#ffffff" stop-opacity=".04"/>
    </linearGradient>
    <filter id="shadow" x="-30%" y="-30%" width="160%" height="160%">
      <feGaussianBlur in="SourceAlpha" stdDeviation="18"/>
      <feOffset dy="16"/>
      <feColorMatrix values="0 0 0 0 0 0 0 0 0 0.08 0 0 0 0 0.12 0 0 0 .45 0"/>
      <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
  </defs>
  <rect width="1600" height="480" rx="52" fill="url(#bg)"/>
  <circle cx="1320" cy="80" r="310" fill="#72d8b4" opacity=".08"/>
  <circle cx="220" cy="520" r="340" fill="#8ea8ff" opacity=".08"/>
  <g filter="url(#shadow)">
    <rect x="150" y="80" width="320" height="320" rx="86" fill="url(#glass)" stroke="#ffffff" stroke-opacity=".2"/>
    <path d="M235 165L385 315" fill="none" stroke="url(#xstroke)" stroke-width="34" stroke-linecap="round"/>
    <path d="M385 165L235 315" fill="none" stroke="#f5fff9" stroke-width="34" stroke-linecap="round"/>
    <path d="M358 174C374 133 415 120 445 143C431 184 398 202 358 187Z" fill="#f0c56b"/>
    <path d="M370 181C397 168 416 151 436 139" fill="none" stroke="#f8fff9" stroke-width="7" stroke-linecap="round"/>
  </g>
  <text x="560" y="255" fill="#f4fff9" font-family="Inter,Segoe UI,Roboto,sans-serif" font-size="142" font-weight="700" letter-spacing="-5">Xylune</text>
  <text x="568" y="322" fill="#cde6df" font-family="Inter,Segoe UI,Roboto,sans-serif" font-size="34" font-weight="500" letter-spacing="3">NATIVE ANDROID · PRIVATE BY DESIGN</text>
</svg>\n''')
    git("add", "branding/xylune-banner.svg")

    license_icon = ROOT / "licenses/icons/xylune.svg"
    license_icon.write_text('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <defs><linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#083A2C"/><stop offset="1" stop-color="#0C684F"/></linearGradient></defs>
  <rect width="108" height="108" rx="24" fill="url(#b)"/>
  <path d="M29 29L79 79" fill="none" stroke="#9AE6C4" stroke-width="11" stroke-linecap="round"/>
  <path d="M79 29L29 79" fill="none" stroke="#F1FFF7" stroke-width="11" stroke-linecap="round"/>
  <path d="M70 34C75 21 87 17 96 24C92 37 82 43 70 38Z" fill="#F4C761"/>
  <path d="M74 36C82 32 88 27 93 23" fill="none" stroke="#F1FFF7" stroke-width="2.5" stroke-linecap="round"/>
</svg>\n''')


def update_readme() -> None:
    path = ROOT / "README.md"
    content = path.read_text()
    content = content.replace("branding/xylune-banner.png", "branding/xylune-banner.svg")
    content = re.sub(r"Current version: \*\*[^*]+\*\*", "Current version: **0.23.0**", content, count=1)
    content = re.sub(r"Xylune-0\.\d+\.\d+-release\.apk", "Xylune-0.23.0-release.apk", content)
    content = content.replace(
        "The public APK is an R8-minified, resource-shrunk **release build**. It intentionally keeps package ID `app.xylune.chat.debug` and Xylune's public reproducible signer so it can update the earlier GitHub debug builds without deleting chats or settings.",
        "The public APK is an R8-minified, resource-shrunk **release build** using package ID `app.xylune.chat.debug`. Xylune starts with a clean application identity; no Arbor package, storage, or backup compatibility is retained.",
    )
    path.write_text(content)


def update_license_component() -> None:
    path = ROOT / "licenses/components/xylune.json"
    data = json.loads(path.read_text())
    data["id"] = "xylune"
    data["name"] = "Xylune"
    data["version"] = "0.23.0"
    data["projectUrl"] = "https://github.com/omerfaruknehir/Xylune"
    data["icon"] = "icons/xylune.svg"
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def add_release_notes() -> None:
    path = ROOT / "docs/releases/RELEASE_NOTES_0.23.0.md"
    path.write_text('''# Xylune 0.23.0

Xylune is the complete successor identity to Arbor, pronounced **“Zy-loon.”** The name is derived subtly from xylem and preserves the project's botanical ancestry without presenting as a generic AI brand.

- Rename the application, Android namespace, package IDs, Kotlin package tree, classes, resources, themes, authorities, widgets, preferences, database/storage identifiers, backup/share formats, OAuth callbacks, build variables, workflows, documentation, and release artifacts to Xylune.
- Replace the A-shaped identity with a geometric X and preserve the leaf as a secondary signature across adaptive, round, monochrome, dynamic-palette, splash, drawer, About, widget, and license artwork.
- Move public builds to `app.xylune.chat.debug` and protected builds to `app.xylune.chat`.
- Move portable formats to the Xylune MIME types, extensions, and schema identifiers without Arbor import aliases.
- Point source links and repository-aware updates to `omerfaruknehir/Xylune`.
- Preserve the complete cloud-provider implementation while renaming its app folders, OAuth redirects, build variables, and documentation.
''')
    git("add", str(path.relative_to(ROOT)))


def add_brand_definition() -> None:
    path = ROOT / "docs/BRAND.md"
    path.write_text('''# Xylune brand

## Name

**Xylune** — pronounced **“Zy-loon.”**

The name is subtly derived from xylem, preserving the project's botanical ancestry while sounding modern and product-focused rather than explicitly AI-themed.

## Mark

The primary mark is a geometric **X**. A small leaf grows from the upper-right arm as a secondary signature. The X must remain recognizable at launcher, monochrome, notification, widget, splash, About, and license-icon sizes.

## Identity rules

- Product and repository name: `Xylune`
- Android namespace and production package: `app.xylune.chat`
- Public GitHub package: `app.xylune.chat.debug`
- Build variables use the `XYLUNE_` prefix.
- Portable files, MIME types, database names, providers, authorities, and OAuth callbacks use the Xylune identity.
- No Arbor compatibility aliases are retained because the project had no external users before the rename.
''')
    git("add", str(path.relative_to(ROOT)))


def assert_no_first_party_arbor() -> None:
    offenders: list[str] = []
    for relative in tracked_files():
        if relative.startswith(TEXT_EXCLUDED_PREFIXES):
            continue
        path = ROOT / relative
        if "Arbor" in relative or "arbor" in relative or "ARBOR" in relative:
            offenders.append(relative)
            continue
        if not path.is_file() or not looks_text(path):
            continue
        try:
            content = path.read_text()
        except UnicodeDecodeError:
            continue
        if re.search(r"Arbor|arbor|ARBOR", content):
            offenders.append(relative)
    if offenders:
        raise RuntimeError("Old brand remains in first-party files:\n" + "\n".join(sorted(set(offenders))[:100]))


def main() -> None:
    move_branded_paths()
    replace_first_party_text()
    set_version()
    rebuild_brand_vectors()
    write_branding_assets()
    update_readme()
    update_license_component()
    add_release_notes()
    add_brand_definition()
    git("add", "-A")
    assert_no_first_party_arbor()
    print("Applied complete internal Xylune 0.23.0 rebrand")


if __name__ == "__main__":
    main()
