from pathlib import Path
import subprocess
import sys

changelog = Path("CHANGELOG.md")
anchor = "# Changelog\n\n"
marker = "# __ARBOR_CHANGELOG_DUPLICATE__\n\n"
original = changelog.read_text()
first = original.find(anchor)
if first < 0:
    raise RuntimeError("CHANGELOG heading is missing")
protected = original[: first + len(anchor)] + original[first + len(anchor) :].replace(anchor, marker)
changelog.write_text(protected)
try:
    subprocess.run([sys.executable, "scripts/apply_transfer_features_0_20_23.py"], check=True)
finally:
    if changelog.exists():
        changelog.write_text(changelog.read_text().replace(marker, anchor))

archive = Path("app/src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt")
text = archive.read_text()
constants_anchor = 'const val ARBOR_BACKUP_EXTENSION = ".arborbackup"\n'
top_level_constants = '''

private const val ENVELOPE_SCHEMA = "arbor-archive-envelope-v1"
private const val MANIFEST_SCHEMA = "arbor-portable-archive-v1"
private const val PBKDF_ITERATIONS = 240_000
'''
if "private const val ENVELOPE_SCHEMA" not in text.split("class ArborArchiveManager", 1)[0]:
    if text.count(constants_anchor) != 1:
        raise RuntimeError("Could not add archive schema constants")
    text = text.replace(constants_anchor, constants_anchor + top_level_constants, 1)
text = text.replace('        const val ENVELOPE_SCHEMA = "arbor-archive-envelope-v1"\n', "")
text = text.replace('        const val MANIFEST_SCHEMA = "arbor-portable-archive-v1"\n', "")
text = text.replace("        const val PBKDF_ITERATIONS = 240_000\n", "")
text = text.replace("entry.size in 1..MAX_MANIFEST_BYTES", "entry.size in 1L..MAX_MANIFEST_BYTES")
archive.write_text(text)

print("Transfer feature source patch completed.")
