#!/usr/bin/env python3
from __future__ import annotations

import base64
import gzip
import io
import re
from pathlib import Path
import xml.etree.ElementTree as ET

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
APP_RES = ROOT / "app/src/main/res"
DRAWABLE = APP_RES / "drawable"
DRAWABLE_NODPI = APP_RES / "drawable-nodpi"
BRANDING_PROVIDERS = ROOT / "branding/provider-icons"

PROVIDER_SVG_GZIP_B64 = {'Dropbox_Icon.svg': 'H4sIAAAAAAAC/02Q3WrDMAyF7/sUQru2YufP7ohbGHRPsD1AWdLE4DilCUnp009u0rELISx9OjpWdbz3HubmNrohWFQkEZrwM9QutBa/vz6FQRinc6jPfgiNxTDg8bCrxrmFxdVTZzHPrncElgmjxW6aru9JsiwLLRkNtzZJpZQJ4wiza5aP4W5RgoQ8g5x3/dusELrGtd3EkpIlDzuoruepg9pir1IqQHrxzIYUhwYN8Sk0GcFPztmjX4lU0d5vbApbU2y0jjSUZB49cylIZg0ZYCQK5txWHKUoaR9nSu7olRUqJ/108aL+xiJlII4xKxhWoErK/IsRGS9mA3NKZrWmOTZLxSximRkDsfL6DsLFeW/xTUp9OhWY8OnjMQ+/UEa5MbgBAAA=', 'Google_Drive_icon_(2026).svg': 'H4sIAAAAAAAC/51Uy27bMBC89ysI5hIDIcXlm4Gda3NI0UORS2+KHpZQWTIkNU7+viuJqVPDMYpABz40mt0ZDbkenrfkUOdjtaFeCEqqot5W44Y6DVzZYCkp66bZ0LZrC0pedk07bGg1jvvbJDkcDvygeNdvEymESJArQm5xehF294WQ9S4dfpE639CUvvUA1h97AKORD0dJySsO+Gr65LGtR2zi91D0P/ZpVnxvH4diZkTOfTpWseWrsiwpQfpvVnERiHIZaG6lZdJwpRQxwCGoZaWJNdxDIKLRhgt863yEkwXAFLfGEeO4EMCU5AHi4t4IHqzLmAxcGkUE044bZZkCbq1bsGzB/qTJrD2ZlMyz7SwK9fTN9VW6omTs03You363obt07OuXa42daWGUvBH4HFfMSg7aOgE3zFtsTypjV2esmLmfVosbUlgehCHgJBeyYgG4975hELgAM3eLdqALQWvmAjfOx67PkGaRlKFXAe2bSYUlaInQxASug37GHfmAZgglSZBcS1kpj7UfAGIbQmN9tC+Af+fSmXp5rBcs4hyTWMYjB6Y1mLmeiUKATEKkv5/qav/X9+085EU5xApN3RZp/7VP87poxzmPTxg7wMAF/OU4lTgVU4QwhtM2JgW3X6dtAC6Bkm38+lI0sdQwdnvSleVQYLpnvmmHZV3T9XNei4BsyXk8nKKLzOGJfTMq+VfHx+KyKA5/mI7iDKY1avMYWYjiJAbjk9rAnHSbhtTjcfxAG56vE7zNg7uA1/4Er8DbI/7/3cijG9LjIVjswKlb3FBu3pzM8HgV6E+aYU7NEMVTZj4W50/xeBWFy+LWyZLo9XS73v0BRX30u9YFAAA=', 'Microsoft_OneDrive_Icon_(2025_-_present).svg': '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', 'Nextcloud_Logo.svg': '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'}

PROVIDER_TARGETS = {
    "Google_Drive_icon_(2026).svg": "ic_google_drive.png",
    "Microsoft_OneDrive_Icon_(2025_-_present).svg": "ic_onedrive.png",
    "Dropbox_Icon.svg": "ic_dropbox.png",
    "Nextcloud_Logo.svg": "ic_nextcloud.png",
}

PROVIDER_SOURCE_URLS = {
    "Google_Drive_icon_(2026).svg": "https://upload.wikimedia.org/wikipedia/commons/5/5f/Google_Drive_icon_%282026%29.svg",
    "Microsoft_OneDrive_Icon_(2025_-_present).svg": "https://upload.wikimedia.org/wikipedia/commons/e/e7/Microsoft_OneDrive_Icon_%282025_-_present%29.svg",
    "Dropbox_Icon.svg": "https://upload.wikimedia.org/wikipedia/commons/7/78/Dropbox_Icon.svg",
    "Nextcloud_Logo.svg": "https://upload.wikimedia.org/wikipedia/commons/6/60/Nextcloud_Logo.svg",
}

LEFT_PATH = "M33.549193,80.863216C45.542258,64.507039 58.821502,47.408289 73.585895,32.881898"
RIGHT_PATH = "M33.99223,32.881898C48.756623,47.408289 62.035867,64.507039 74.028932,80.863216"
LEAF_PATH = "M39.107895,30.166046C43.79571,20.768808 52.715523,17.003434 60.890902,20.847009C59.491039,30.710867 51.981892,36.353531 40.896179,34.109428Z"
ROUNDED_BACKGROUND_PATH = "M24,0H84C97.3,0 108,10.7 108,24V84C108,97.3 97.3,108 84,108H24C10.7,108 0,97.3 0,84V24C0,10.7 10.7,0 24,0Z"
OLD_CROSSBAR = "M40,64C49,60 59,60 68,64"


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.rstrip() + "\n")


def write_provider_sources() -> None:
    BRANDING_PROVIDERS.mkdir(parents=True, exist_ok=True)
    for name, encoded in PROVIDER_SVG_GZIP_B64.items():
        (BRANDING_PROVIDERS / name).write_bytes(gzip.decompress(base64.b64decode(encoded)))
    lines = [
        "# Cloud provider marks",
        "",
        "These source SVGs are bundled only to identify user-selected cloud services in Xylune.",
        "The names and marks remain trademarks of their respective owners; their inclusion does not imply endorsement.",
        "Android PNG resources are generated from these checked-in SVGs so release builds do not fetch artwork from the network.",
        "",
        "Sources:",
    ]
    for name, url in PROVIDER_SOURCE_URLS.items():
        lines.append(f"- `{name}`: {url}")
    write(BRANDING_PROVIDERS / "README.md", "\n".join(lines))


def nextcloud_symbol_only(raw: bytes) -> bytes:
    root = ET.fromstring(raw)
    first_path = next((node for node in root.iter() if node.tag.rsplit("}", 1)[-1] == "path"), None)
    if first_path is None or not first_path.attrib.get("d"):
        raise RuntimeError("Nextcloud source SVG contains no symbol path")
    d = first_path.attrib["d"]
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 132.6422 61.3">'
        f'<path fill="#0082C9" d="{d}"/>'
        '</svg>'
    ).encode()


def render_provider_icon(source: Path, target: Path) -> None:
    raw = source.read_bytes()
    if source.name == "Nextcloud_Logo.svg":
        raw = nextcloud_symbol_only(raw)
    png = cairosvg.svg2png(bytestring=raw, output_width=1024)
    image = Image.open(io.BytesIO(png)).convert("RGBA")
    bbox = image.getbbox()
    if bbox is None:
        raise RuntimeError(f"Provider icon rendered empty: {source}")
    image = image.crop(bbox)
    max_visible = 176
    scale = min(max_visible / image.width, max_visible / image.height)
    size = (max(1, round(image.width * scale)), max(1, round(image.height * scale)))
    image = image.resize(size, Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (192, 192), (0, 0, 0, 0))
    canvas.alpha_composite(image, ((192 - image.width) // 2, (192 - image.height) // 2))
    target.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(target, format="PNG", optimize=True)


def replace_provider_icons() -> None:
    DRAWABLE_NODPI.mkdir(parents=True, exist_ok=True)
    for source_name, target_name in PROVIDER_TARGETS.items():
        render_provider_icon(BRANDING_PROVIDERS / source_name, DRAWABLE_NODPI / target_name)
        legacy = DRAWABLE / target_name.replace(".png", ".xml")
        if legacy.exists():
            legacy.unlink()


def extract_foreground_palette(text: str) -> tuple[str, str, str, str]:
    gradients = re.findall(r"<gradient\b.*?/>", text, flags=re.S)
    gradient = next((value for value in gradients if "android:startColor" in value and "android:endColor" in value), None)
    if gradient is None:
        raise RuntimeError("Foreground vector has no gradient")
    start = re.search(r'android:startColor="([^"]+)"', gradient)
    end = re.search(r'android:endColor="([^"]+)"', gradient)
    strokes = re.findall(r'android:strokeColor="([^"]+)"', text)
    fills = [value for value in re.findall(r'android:fillColor="([^"]+)"', text) if "transparent" not in value]
    if start is None or end is None or not strokes or not fills:
        raise RuntimeError("Could not read foreground palette")
    return start.group(1), end.group(1), strokes[0], fills[-1]


def foreground_xml(start: str, end: str, right: str, leaf: str) -> str:
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@android:color/transparent"
        android:strokeWidth="11.5517"
        android:strokeLineCap="round"
        android:pathData="{LEFT_PATH}">
        <aapt:attr name="android:strokeColor">
            <gradient
                android:type="linear"
                android:startX="31.9912"
                android:startY="82.6202"
                android:endX="76.4301"
                android:endY="30.3824"
                android:startColor="{start}"
                android:endColor="{end}" />
        </aapt:attr>
    </path>
    <path
        android:fillColor="{leaf}"
        android:pathData="{LEAF_PATH}" />
    <path
        android:fillColor="@android:color/transparent"
        android:strokeColor="{right}"
        android:strokeWidth="11.5517"
        android:strokeLineCap="round"
        android:pathData="{RIGHT_PATH}" />
</vector>'''


def monochrome_xml() -> str:
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="@android:color/transparent" android:strokeColor="#000000"
        android:strokeWidth="11.5517" android:strokeLineCap="round" android:pathData="{LEFT_PATH}" />
    <path android:fillColor="#000000" android:pathData="{LEAF_PATH}" />
    <path android:fillColor="@android:color/transparent" android:strokeColor="#000000"
        android:strokeWidth="11.5517" android:strokeLineCap="round" android:pathData="{RIGHT_PATH}" />
</vector>'''


def background_path_block(text: str) -> str:
    match = re.search(r"<path\b.*?</path>", text, flags=re.S)
    if match is None:
        match = re.search(r"<path\b.*?/>", text, flags=re.S)
    if match is None:
        raise RuntimeError("Background vector has no path")
    return re.sub(
        r'android:pathData="[^"]+"',
        f'android:pathData="{ROUNDED_BACKGROUND_PATH}"',
        match.group(0),
        count=1,
    )


def mark_xml(background: str, start: str, end: str, right: str, leaf: str) -> str:
    foreground = foreground_xml(start, end, right, leaf)
    body = foreground.split(">", 1)[1].rsplit("</vector>", 1)[0].strip()
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    {background}
    {body}
</vector>'''


def replace_xylune_artwork() -> None:
    source = (ROOT / "branding/xylune-logo.svg").read_text().replace(" ", "").replace("\n", "")
    for token in ("33.549193", "33.99223", "39.107895"):
        if token not in source:
            raise RuntimeError("branding/xylune-logo.svg is not the approved X-and-leaf artwork")

    resource_dirs = [DRAWABLE, APP_RES / "drawable-v31"]
    palettes: dict[Path, tuple[str, str, str, str]] = {}
    for directory in resource_dirs:
        if not directory.exists():
            continue
        for foreground in sorted(directory.glob("ic_xylune_foreground*.xml")):
            palette = extract_foreground_palette(foreground.read_text())
            palettes[foreground] = palette
            write(foreground, foreground_xml(*palette))

    write(DRAWABLE / "ic_xylune_monochrome.xml", monochrome_xml())

    for directory in resource_dirs:
        if not directory.exists():
            continue
        for mark in sorted(directory.glob("ic_xylune_mark*.xml")):
            suffix = mark.stem.removeprefix("ic_xylune_mark")
            foreground = directory / f"ic_xylune_foreground{suffix}.xml"
            background = directory / f"ic_xylune_background{suffix}.xml"
            if foreground not in palettes:
                if not foreground.exists():
                    raise RuntimeError(f"Missing foreground for {mark}")
                palettes[foreground] = extract_foreground_palette(foreground.read_text())
            if not background.exists():
                raise RuntimeError(f"Missing background for {mark}")
            write(mark, mark_xml(background_path_block(background.read_text()), *palettes[foreground]))

    for adaptive in sorted((APP_RES / "mipmap-anydpi").glob("ic_launcher*.xml")):
        text = adaptive.read_text()
        if "<adaptive-icon" not in text:
            continue
        if "<monochrome" not in text:
            text = text.replace(
                "</adaptive-icon>",
                '    <monochrome android:drawable="@drawable/ic_xylune_monochrome" />\n</adaptive-icon>',
            )
        write(adaptive, text)


def patch_palette_visuals() -> None:
    path = ROOT / "app/src/main/java/app/xylune/chat/ui/PaletteVisuals.kt"
    text = path.read_text()
    if "import androidx.compose.ui.layout.ContentScale" not in text:
        text = text.replace(
            "import androidx.compose.ui.graphics.Color\n",
            "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.layout.ContentScale\n",
        )
    text = text.replace(
        "contentDescription = contentDescription,\n        modifier = modifier,",
        "contentDescription = contentDescription,\n        contentScale = ContentScale.Fit,\n        modifier = modifier,",
    )
    text = text.replace(
        "contentDescription = null,\n                modifier = Modifier.matchParentSize()",
        "contentDescription = null,\n                contentScale = ContentScale.Fit,\n                modifier = Modifier.matchParentSize()",
    )
    write(path, text)


def update_notices() -> None:
    section = """## Cloud provider service marks

Xylune includes Google Drive, Microsoft OneDrive, Dropbox, and Nextcloud marks solely to identify the corresponding user-selected services. These marks and names remain the property and trademarks of their respective owners. Their inclusion does not imply sponsorship or endorsement. Source artwork and provenance are recorded under `branding/provider-icons/`.
"""
    for path in (ROOT / "THIRD_PARTY_NOTICES.md", ROOT / "app/src/main/assets/THIRD_PARTY_NOTICES.md"):
        if not path.exists():
            continue
        text = path.read_text()
        if "## Cloud provider service marks" not in text:
            text = text.rstrip() + "\n\n" + section
        write(path, text)


def update_release_text() -> None:
    changelog = ROOT / "CHANGELOG.md"
    text = changelog.read_text()
    entry = """## 0.23.2 — 2026-08-04

- Repair cloud restore from OneDrive, Dropbox, WebDAV/Nextcloud, and S3 by exposing only Xylune's private downloaded-backup cache through its non-exported FileProvider.
- Replace every launcher, themed, dynamic-palette, splash, About, license, widget, and notification Xylune mark with the approved X-and-leaf artwork derived from `branding/xylune-logo.svg`.
- Normalize every launcher variant to the same 108 × 108 viewport and identical foreground geometry, including the Android 12+ dynamic-color override, eliminating intermittent icon-size changes.
- Use the supplied current Google Drive, Microsoft OneDrive, Dropbox, and Nextcloud service marks in cloud restore while retaining a neutral storage symbol for S3-compatible services.

"""
    if not text.startswith("## 0.23.2"):
        text = entry + text
    write(changelog, text)

    notes = ROOT / "docs/releases/RELEASE_NOTES_0.23.2.md"
    note_text = notes.read_text() if notes.exists() else "# Xylune 0.23.2\n"
    addition = """
## Branding and provider artwork

- Replaced the former A-derived artwork throughout the app with the approved Xylune X-and-leaf logo from `branding/xylune-logo.svg`.
- Preserved Xylune, Dynamic, Graphite, Ocean, Violet, and Sunset color schemes while making every adaptive, monochrome, and in-app mark use one normalized geometry.
- Fixed the Dynamic launcher icon occasionally appearing at a different scale by aligning base and Android 12+ resources to the same viewport and paths.
- Updated cloud restore to the supplied current Google Drive, Microsoft OneDrive, Dropbox, and Nextcloud marks.
"""
    if "## Branding and provider artwork" not in note_text:
        note_text = note_text.rstrip() + "\n" + addition
    write(notes, note_text)


def add_regression_test() -> None:
    test = ROOT / "app/src/test/java/app/xylune/chat/ui/XyluneBrandingRegressionTest.kt"
    write(test, f'''package app.xylune.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XyluneBrandingRegressionTest {{
    private fun source(path: String): String = File(path).readText()

    @Test
    fun everyXyluneVectorUsesApprovedGeometry() {{
        val roots = listOf(File("src/main/res/drawable"), File("src/main/res/drawable-v31"))
        val vectors = roots.flatMap {{ root ->
            root.listFiles().orEmpty().filter {{ it.name.startsWith("ic_xylune_foreground") || it.name.startsWith("ic_xylune_mark") }}
        }}
        assertTrue(vectors.isNotEmpty())
        vectors.forEach {{ file ->
            val value = file.readText()
            assertTrue(file.path, value.contains("{LEFT_PATH}"))
            assertTrue(file.path, value.contains("{RIGHT_PATH}"))
            assertTrue(file.path, value.contains("{LEAF_PATH}"))
            assertFalse(file.path, value.contains("{OLD_CROSSBAR}"))
            assertTrue(file.path, value.contains("android:viewportWidth=\\\"108\\\""))
            assertTrue(file.path, value.contains("android:viewportHeight=\\\"108\\\""))
        }}
    }}

    @Test
    fun launcherVariantsShareMonochromeAndPreviewScale() {{
        File("src/main/res/mipmap-anydpi").listFiles().orEmpty()
            .filter {{ it.name.startsWith("ic_launcher") && it.extension == "xml" }}
            .forEach {{ file -> assertTrue(file.path, file.readText().contains("ic_xylune_monochrome")) }}
        assertTrue(source("src/main/java/app/xylune/chat/ui/PaletteVisuals.kt").contains("ContentScale.Fit"))
    }}

    @Test
    fun cloudProvidersUseGeneratedOfficialArtwork() {{
        val nodpi = File("src/main/res/drawable-nodpi")
        listOf("ic_google_drive.png", "ic_onedrive.png", "ic_dropbox.png", "ic_nextcloud.png")
            .forEach {{ name -> assertTrue(name, File(nodpi, name).length() > 0L) }}
        val legacy = File("src/main/res/drawable")
        listOf("ic_google_drive.xml", "ic_onedrive.xml", "ic_dropbox.xml", "ic_nextcloud.xml")
            .forEach {{ name -> assertFalse(name, File(legacy, name).exists()) }}
    }}
}}
''')


def verify_result() -> None:
    for directory in (DRAWABLE, APP_RES / "drawable-v31"):
        if not directory.exists():
            continue
        for path in directory.glob("ic_xylune_*.xml"):
            text = path.read_text()
            if OLD_CROSSBAR in text:
                raise RuntimeError(f"Old A crossbar remains in {path}")
    for target in PROVIDER_TARGETS.values():
        path = DRAWABLE_NODPI / target
        if not path.is_file() or path.stat().st_size == 0:
            raise RuntimeError(f"Missing rendered provider icon {path}")


def main() -> None:
    write_provider_sources()
    replace_provider_icons()
    replace_xylune_artwork()
    patch_palette_visuals()
    update_notices()
    update_release_text()
    add_regression_test()
    verify_result()


if __name__ == "__main__":
    main()
