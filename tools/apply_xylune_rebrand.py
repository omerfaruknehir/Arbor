#!/usr/bin/env python3
"""Apply the one-time Arbor -> Xylune rebrand across the Android project."""

from __future__ import annotations

import json
import os
import re
import shutil
import struct
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET_VERSION = "0.23.0"
TARGET_VERSION_CODE_MINIMUM = 169

REPLACEMENTS: tuple[tuple[str, str], ...] = (
    ("ARBOR", "XYLUNE"),
    ("Arbor", "Xylune"),
    ("arbor", "xylune"),
)

BINARY_SUFFIXES = {
    ".aab",
    ".apk",
    ".class",
    ".dex",
    ".gif",
    ".gz",
    ".ico",
    ".jar",
    ".jpeg",
    ".jpg",
    ".jks",
    ".keystore",
    ".pdf",
    ".png",
    ".so",
    ".tar",
    ".ttf",
    ".webp",
    ".woff",
    ".woff2",
    ".zip",
}

SKIPPED_PREFIXES = (
    ".git/",
    ".gradle/",
    "app/build/",
    "build/",
    "tools/xylune-rebrand-audit/",
    "tools/xylune-rebrand-failure/",
)

SKIPPED_FILES = {
    ".github/workflows/xylune-rebrand-snapshot.yml",
    "tools/apply_xylune_rebrand.py",
}

OLD_GEOMETRY = {
    "M28,82C35,64 43,45 53,28": "M30,28C42,43 58,64 78,82",
    "M55,28C65,45 73,64 80,82": "M78,28C66,43 50,64 30,82",
    "M40,64C49,60 59,60 68,64": "M69,35C74,31 78,27 82,24",
    "M54,29C60,21 69,19 76,24C73,33 65,37 55,33Z": "M68,32C74,22 84,19 92,25C87,35 78,39 69,35Z",
    "M28 82C35 64 43 45 53 28": "M30 28C42 43 58 64 78 82",
    "M55 28C65 45 73 64 80 82": "M78 28C66 43 50 64 30 82",
    "M40 64C49 60 59 60 68 64": "M69 35C74 31 78 27 82 24",
    "M54 29C60 21 69 19 76 24C73 33 65 37 55 33Z": "M68 32C74 22 84 19 92 25C87 35 78 39 69 35Z",
}


def run(*args: str) -> str:
    return subprocess.check_output(args, cwd=ROOT, text=True)


def renamed(value: str) -> str:
    for old, new in REPLACEMENTS:
        value = value.replace(old, new)
    return value


def should_skip(relative: str) -> bool:
    return relative in SKIPPED_FILES or any(relative.startswith(prefix) for prefix in SKIPPED_PREFIXES)


def tracked_files() -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [item.decode("utf-8") for item in raw.split(b"\0") if item]


def replace_text_content(paths: list[str]) -> int:
    changed = 0
    for relative in paths:
        if should_skip(relative):
            continue
        path = ROOT / relative
        if not path.is_file() or path.suffix.lower() in BINARY_SUFFIXES:
            continue
        data = path.read_bytes()
        if b"\0" in data:
            continue
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            continue
        updated = renamed(text)
        if updated != text:
            path.write_bytes(updated.encode("utf-8"))
            changed += 1
    return changed


def move_renamed_paths(paths: list[str]) -> int:
    moves: list[tuple[Path, Path]] = []
    for relative in paths:
        if should_skip(relative):
            continue
        destination_relative = renamed(relative)
        if destination_relative == relative:
            continue
        source = ROOT / relative
        destination = ROOT / destination_relative
        if source.exists():
            moves.append((source, destination))

    moved = 0
    for source, destination in sorted(moves, key=lambda pair: len(pair[0].parts), reverse=True):
        if not source.exists():
            continue
        destination.parent.mkdir(parents=True, exist_ok=True)
        if destination.exists():
            raise RuntimeError(f"Refusing to overwrite existing path during rebrand: {destination}")
        shutil.move(str(source), str(destination))
        moved += 1
    return moved


def update_vector_geometry() -> int:
    changed = 0
    resource_root = ROOT / "app/src/main/res"
    for path in sorted(resource_root.rglob("*.xml")):
        text = path.read_text(encoding="utf-8")
        updated = text
        for old, new in OLD_GEOMETRY.items():
            updated = updated.replace(old, new)
        updated = updated.replace(
            'android:startX="27"\n                android:startY="84"\n                android:endX="55"\n                android:endY="25"',
            'android:startX="30"\n                android:startY="28"\n                android:endX="78"\n                android:endY="82"',
        )
        if updated != text:
            path.write_text(updated, encoding="utf-8")
            changed += 1
    return changed


def xylune_logo_svg() -> str:
    return """<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bg" x1="15" y1="8" x2="96" y2="101" gradientUnits="userSpaceOnUse">
      <stop stop-color="#083A2C"/>
      <stop offset="1" stop-color="#0C684F"/>
    </linearGradient>
    <linearGradient id="mint" x1="30" y1="28" x2="78" y2="82" gradientUnits="userSpaceOnUse">
      <stop stop-color="#DDFBEA"/>
      <stop offset="1" stop-color="#86DFB8"/>
    </linearGradient>
  </defs>
  <rect width="108" height="108" rx="24" fill="url(#bg)"/>
  <path d="M30 28C42 43 58 64 78 82" fill="none" stroke="url(#mint)" stroke-width="11" stroke-linecap="round"/>
  <path d="M78 28C66 43 50 64 30 82" fill="none" stroke="#F1FFF7" stroke-width="11" stroke-linecap="round"/>
  <path d="M69 35C74 31 78 27 82 24" fill="none" stroke="#F4C761" stroke-width="5" stroke-linecap="round"/>
  <path d="M68 32C74 22 84 19 92 25C87 35 78 39 69 35Z" fill="#F4C761"/>
</svg>
"""


def write_brand_assets() -> None:
    logo = xylune_logo_svg()
    targets = (
        ROOT / "branding/xylune-logo.svg",
        ROOT / "licenses/icons/xylune.svg",
    )
    for path in targets:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(logo, encoding="utf-8")
    write_banner(ROOT / "branding/xylune-banner.png")
    (ROOT / "branding/README.md").write_text(
        """# Xylune brand assets

**Xylune** is pronounced **“Zy-loon.”** The name subtly references xylem and keeps the project's botanical ancestry without using a generic AI label.

The mark is a soft, intersecting **X** formed from two organic stems. The gold leaf remains attached to the upper-right branch. Launcher, monochrome, dynamic-color, in-app, license-catalog, and repository artwork all use the same geometry.

- `xylune-logo.svg`: canonical square mark
- `xylune-banner.png`: repository banner
- Android vector resources: `app/src/main/res/drawable*/ic_xylune_*`
""",
        encoding="utf-8",
    )


def png_dimensions(path: Path) -> tuple[int, int] | None:
    try:
        data = path.read_bytes()[:24]
    except OSError:
        return None
    if len(data) >= 24 and data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR":
        return struct.unpack(">II", data[16:24])
    return None


def write_banner(path: Path) -> None:
    try:
        from PIL import Image, ImageDraw, ImageFilter, ImageFont
    except ImportError as error:
        raise RuntimeError("Pillow is required to regenerate the Xylune repository banner") from error

    dimensions = png_dimensions(path) or (1600, 900)
    width, height = dimensions
    if width < 800 or height < 300:
        width, height = 1600, 900

    scale = 2
    canvas_w, canvas_h = width * scale, height * scale
    image = Image.new("RGB", (canvas_w, canvas_h))
    pixels = image.load()
    top = (5, 25, 20)
    bottom = (10, 82, 63)
    for y in range(canvas_h):
        t = y / max(1, canvas_h - 1)
        eased = t * t * (3 - 2 * t)
        color = tuple(round(a + (b - a) * eased) for a, b in zip(top, bottom))
        for x in range(canvas_w):
            side_glow = max(0.0, 1.0 - abs((x / canvas_w) - 0.32) / 0.55)
            pixels[x, y] = tuple(min(255, round(c + side_glow * boost)) for c, boost in zip(color, (3, 12, 9)))

    glow = Image.new("RGBA", (canvas_w, canvas_h), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse(
        (-int(0.12 * canvas_w), -int(0.40 * canvas_h), int(0.55 * canvas_w), int(0.78 * canvas_h)),
        fill=(117, 223, 177, 58),
    )
    glow_draw.ellipse(
        (int(0.60 * canvas_w), int(0.25 * canvas_h), int(1.13 * canvas_w), int(1.22 * canvas_h)),
        fill=(244, 199, 97, 28),
    )
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(20, int(canvas_h * 0.10))))
    image = Image.alpha_composite(image.convert("RGBA"), glow)
    draw = ImageDraw.Draw(image)

    logo_size = int(canvas_h * 0.48)
    logo_left = int(canvas_w * 0.075)
    logo_top = (canvas_h - logo_size) // 2
    radius = int(logo_size * 0.22)
    draw.rounded_rectangle(
        (logo_left, logo_top, logo_left + logo_size, logo_top + logo_size),
        radius=radius,
        fill=(8, 58, 44, 242),
        outline=(151, 233, 198, 40),
        width=max(2, int(logo_size * 0.008)),
    )

    def point(x: float, y: float) -> tuple[int, int]:
        return (
            logo_left + round(x / 108 * logo_size),
            logo_top + round(y / 108 * logo_size),
        )

    stroke = max(10, round(11 / 108 * logo_size))
    draw.line([point(30, 28), point(78, 82)], fill=(156, 233, 196, 255), width=stroke)
    draw.line([point(78, 28), point(30, 82)], fill=(241, 255, 247, 255), width=stroke)
    stem_width = max(5, round(5 / 108 * logo_size))
    draw.line([point(69, 35), point(82, 24)], fill=(244, 199, 97, 255), width=stem_width)
    leaf = [point(68, 32), point(73, 24), point(82, 20), point(92, 25), point(87, 34), point(78, 39), point(69, 35)]
    draw.polygon(leaf, fill=(244, 199, 97, 255))

    font_candidates = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    )
    regular_candidates = (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    )

    def load_font(candidates: tuple[str, ...], size: int):
        for candidate in candidates:
            if Path(candidate).is_file():
                return ImageFont.truetype(candidate, size=size)
        return ImageFont.load_default()

    title_font = load_font(font_candidates, int(canvas_h * 0.145))
    subtitle_font = load_font(regular_candidates, int(canvas_h * 0.044))
    pronunciation_font = load_font(regular_candidates, int(canvas_h * 0.034))
    text_left = logo_left + logo_size + int(canvas_w * 0.055)
    title_top = int(canvas_h * 0.33)
    draw.text((text_left, title_top), "Xylune", font=title_font, fill=(242, 255, 248, 255))
    title_box = draw.textbbox((text_left, title_top), "Xylune", font=title_font)
    subtitle_top = title_box[3] + int(canvas_h * 0.025)
    draw.text(
        (text_left, subtitle_top),
        "Native Android. Private by design.",
        font=subtitle_font,
        fill=(194, 235, 216, 235),
    )
    pronunciation_top = subtitle_top + int(canvas_h * 0.065)
    draw.text(
        (text_left, pronunciation_top),
        "Pronounced “Zy-loon”",
        font=pronunciation_font,
        fill=(244, 199, 97, 225),
    )

    image = image.convert("RGB").resize((width, height), Image.Resampling.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def update_versions_and_docs() -> None:
    gradle = ROOT / "app/build.gradle.kts"
    text = gradle.read_text(encoding="utf-8")

    def bump_code(match: re.Match[str]) -> str:
        current = int(match.group(1))
        return f"versionCode = {max(current + 1, TARGET_VERSION_CODE_MINIMUM)}"

    text, code_count = re.subn(r"versionCode\s*=\s*(\d+)", bump_code, text, count=1)
    text, name_count = re.subn(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{TARGET_VERSION}"',
        text,
        count=1,
    )
    if code_count != 1 or name_count != 1:
        raise RuntimeError("Could not update Android version metadata")
    gradle.write_text(text, encoding="utf-8")

    component = ROOT / "licenses/components/xylune.json"
    metadata = json.loads(component.read_text(encoding="utf-8"))
    metadata["version"] = TARGET_VERSION
    component.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    readme = ROOT / "README.md"
    text = readme.read_text(encoding="utf-8")
    text = re.sub(r"Current version: \*\*[^*]+\*\*", f"Current version: **{TARGET_VERSION}**", text, count=1)
    text = re.sub(r"Xylune-[0-9]+(?:\.[0-9]+)+-release\.apk", f"Xylune-{TARGET_VERSION}-release.apk", text)
    tagline = "  Xylune (pronounced <strong>“Zy-loon”</strong>) is a native Android workspace for private AI chat, research, files, and local tools."
    text = text.replace(
        "  A native Android workspace for private AI chat, research, files, and local tools.",
        tagline,
    )
    readme.write_text(text, encoding="utf-8")

    changelog = ROOT / "CHANGELOG.md"
    current = changelog.read_text(encoding="utf-8")
    heading = f"## {TARGET_VERSION} — 2026-08-03"
    if heading not in current:
        entry = f"""{heading}

- Rebrand the complete application from Arbor to Xylune, including the Android namespace, application ID, source packages, storage and transfer formats, widgets, native tools, release assets, documentation, and build metadata.
- Replace the original A-shaped mark with an organic X while retaining the gold leaf; use the same geometry for launcher variants, dynamic color, monochrome icons, in-app marks, the license catalog, and repository artwork.
- Rename internal protocols and identifiers without legacy compatibility because the project has no deployed user base, then validate the release build, lint, unit tests, instrumentation compilation, and offline license catalog.

"""
        changelog.write_text(entry + current, encoding="utf-8")


def validate_source_shape() -> None:
    expected = (
        "app/src/main/java/app/xylune/chat/XyluneApplication.kt",
        "app/src/main/java/app/xylune/chat/ui/XyluneApp.kt",
        "app/src/main/java/app/xylune/chat/data/XyluneDatabase.kt",
        "app/src/main/java/app/xylune/chat/widgets/XyluneHomeWidgetProvider.kt",
        "app/src/main/res/drawable/ic_xylune_foreground.xml",
        "app/src/main/res/drawable/ic_xylune_monochrome.xml",
        "app/src/main/res/drawable/ic_xylune_mark.xml",
        "app/src/main/res/xml/xylune_home_widget_info.xml",
        "app/src/main/res/layout/xylune_home_widget_program.xml",
        "branding/xylune-logo.svg",
        "branding/xylune-banner.png",
        "licenses/components/xylune.json",
        "licenses/icons/xylune.svg",
        "ci/xylune-debug.keystore",
    )
    missing = [relative for relative in expected if not (ROOT / relative).is_file()]
    if missing:
        raise RuntimeError("Expected rebranded files are missing:\n" + "\n".join(missing))

    manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    required_manifest_values = (
        'android:name=".XyluneApplication"',
        'android:scheme="xylune"',
        'application/vnd.xylune.chat',
        '.widgets.XyluneHomeWidgetProvider',
        '@xml/xylune_home_widget_info',
    )
    for value in required_manifest_values:
        if value not in manifest:
            raise RuntimeError(f"Manifest is missing rebranded value: {value}")

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    for value in ('namespace = "app.xylune.chat"', 'applicationId = "app.xylune.chat"', f'versionName = "{TARGET_VERSION}"'):
        if value not in gradle:
            raise RuntimeError(f"Gradle metadata is missing: {value}")

    icon = (ROOT / "app/src/main/res/drawable/ic_xylune_foreground.xml").read_text(encoding="utf-8")
    if "M30,28C42,43 58,64 78,82" not in icon or "M78,28C66,43 50,64 30,82" not in icon:
        raise RuntimeError("The launcher foreground was renamed but its A geometry was not replaced with Xylune's X")


def main() -> None:
    os.chdir(ROOT)
    before = tracked_files()
    changed_text = replace_text_content(before)
    moved = move_renamed_paths(before)
    changed_vectors = update_vector_geometry()
    write_brand_assets()
    update_versions_and_docs()
    validate_source_shape()
    print(
        f"Xylune rebrand applied: {changed_text} text files updated, "
        f"{moved} tracked paths moved, {changed_vectors} vector resources redrawn."
    )


if __name__ == "__main__":
    main()
