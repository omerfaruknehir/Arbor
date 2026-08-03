#!/usr/bin/env python3
"""Port the completed direct-cloud provider work onto the post-rebrand Xylune tree.

The cloud provider implementation was developed immediately before the full
Arbor -> Xylune namespace migration. This script performs a path-aware,
three-way merge against that implementation rather than copying old files over
newer Xylune sources.
"""

from __future__ import annotations

import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OLD_BASE = "e0d61ac8d15e7615a339d0e424c0c01e350f6e9c"
CLOUD_HEAD = "630925236acbb379ae3873c8bef44b8c7d096a94"
TARGET_VERSION_CODE = 170
TARGET_VERSION_NAME = "0.23.1"

PATHS = {
    ".github/workflows/android.yml": ".github/workflows/android.yml",
    ".github/workflows/release.yml": ".github/workflows/release.yml",
    "app/build.gradle.kts": "app/build.gradle.kts",
    "app/src/main/AndroidManifest.xml": "app/src/main/AndroidManifest.xml",
    "app/src/main/java/app/arbor/chat/ArborApplication.kt": "app/src/main/java/app/xylune/chat/XyluneApplication.kt",
    "app/src/main/java/app/arbor/chat/MainActivity.kt": "app/src/main/java/app/xylune/chat/MainActivity.kt",
    "app/src/main/java/app/arbor/chat/security/SecureStore.kt": "app/src/main/java/app/xylune/chat/security/SecureStore.kt",
    "app/src/main/java/app/arbor/chat/transfer/CloudBackupClients.kt": "app/src/main/java/app/xylune/chat/transfer/CloudBackupClients.kt",
    "app/src/main/java/app/arbor/chat/transfer/CloudOAuthManager.kt": "app/src/main/java/app/xylune/chat/transfer/CloudOAuthManager.kt",
    "app/src/main/java/app/arbor/chat/transfer/DirectCloudBackupClients.kt": "app/src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt",
    "app/src/main/java/app/arbor/chat/transfer/DirectCloudConfigStore.kt": "app/src/main/java/app/xylune/chat/transfer/DirectCloudConfigStore.kt",
    "app/src/main/java/app/arbor/chat/transfer/DirectCloudModels.kt": "app/src/main/java/app/xylune/chat/transfer/DirectCloudModels.kt",
    "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt": "app/src/main/java/app/xylune/chat/ui/ChatViewModel.kt",
    "app/src/main/java/app/arbor/chat/ui/CloudBackupUi.kt": "app/src/main/java/app/xylune/chat/ui/CloudBackupUi.kt",
    "app/src/main/java/app/arbor/chat/ui/DirectCloudProvidersUi.kt": "app/src/main/java/app/xylune/chat/ui/DirectCloudProvidersUi.kt",
    "app/src/main/java/app/arbor/chat/ui/SetupRestoreUi.kt": "app/src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt",
    "app/src/test/java/app/arbor/chat/transfer/DirectCloudConfigurationTest.kt": "app/src/test/java/app/xylune/chat/transfer/DirectCloudConfigurationTest.kt",
    "docs/CLOUD_PROVIDERS_SETUP.md": "docs/CLOUD_PROVIDERS_SETUP.md",
    "docs/releases/RELEASE_NOTES_0.22.5.md": "docs/releases/RELEASE_NOTES_0.23.1.md",
}

REPLACEMENTS = (
    ("app.arbor.chat", "app.xylune.chat"),
    ("app/arbor/chat", "app/xylune/chat"),
    ("ArborApplication", "XyluneApplication"),
    ("ArborArchiveManager", "XyluneArchiveManager"),
    ("ARBOR_", "XYLUNE_"),
    ("Arbor", "Xylune"),
    ("arbor", "xylune"),
)


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(
        list(args),
        cwd=ROOT,
        check=check,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )


def show(ref: str, path: str) -> bytes | None:
    result = run("git", "show", f"{ref}:{path}", check=False)
    if result.returncode == 0:
        return result.stdout
    if b"exists on disk, but not in" in result.stderr or b"does not exist" in result.stderr or b"Path '" in result.stderr:
        return None
    return None


def transform(raw: bytes, old_path: str) -> bytes:
    text = raw.decode("utf-8")
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    if old_path == "docs/releases/RELEASE_NOTES_0.22.5.md":
        text = text.replace("0.22.5", TARGET_VERSION_NAME)
    return text.encode("utf-8")


def preserve_current_version(candidate: bytes, current: bytes) -> bytes:
    current_text = current.decode("utf-8")
    candidate_text = candidate.decode("utf-8")
    version_code = re.search(r"versionCode\s*=\s*(\d+)", current_text)
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', current_text)
    if version_code:
        candidate_text = re.sub(
            r"versionCode\s*=\s*\d+",
            f"versionCode = {version_code.group(1)}",
            candidate_text,
            count=1,
        )
    if version_name:
        candidate_text = re.sub(
            r'versionName\s*=\s*"[^"]+"',
            f'versionName = "{version_name.group(1)}"',
            candidate_text,
            count=1,
        )
    return candidate_text.encode("utf-8")


def merge_file(destination: Path, base: bytes, theirs: bytes) -> None:
    ours = destination.read_bytes()
    if destination.as_posix().endswith("app/build.gradle.kts"):
        base = preserve_current_version(base, ours)
        theirs = preserve_current_version(theirs, ours)

    with tempfile.TemporaryDirectory(prefix="xylune-cloud-merge-") as temp:
        temp_root = Path(temp)
        ours_path = temp_root / "ours"
        base_path = temp_root / "base"
        theirs_path = temp_root / "theirs"
        ours_path.write_bytes(ours)
        base_path.write_bytes(base)
        theirs_path.write_bytes(theirs)
        result = subprocess.run(
            ["git", "merge-file", "-p", str(ours_path), str(base_path), str(theirs_path)],
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        destination.write_bytes(result.stdout)
        if result.returncode == 1:
            raise RuntimeError(f"Three-way merge conflict in {destination.relative_to(ROOT)}")
        if result.returncode > 1:
            raise RuntimeError(
                f"git merge-file failed for {destination.relative_to(ROOT)}: "
                + result.stderr.decode("utf-8", errors="replace")
            )


def set_release_version() -> None:
    path = ROOT / "app/build.gradle.kts"
    text = path.read_text(encoding="utf-8")
    text, code_count = re.subn(
        r"versionCode\s*=\s*\d+",
        f"versionCode = {TARGET_VERSION_CODE}",
        text,
        count=1,
    )
    text, name_count = re.subn(
        r'versionName\s*=\s*"[^"]+"',
        f'versionName = "{TARGET_VERSION_NAME}"',
        text,
        count=1,
    )
    if code_count != 1 or name_count != 1:
        raise RuntimeError("Could not set the Xylune cloud-backup release version")
    path.write_text(text, encoding="utf-8")


def update_changelog() -> None:
    path = ROOT / "CHANGELOG.md"
    text = path.read_text(encoding="utf-8")
    heading = f"## {TARGET_VERSION_NAME} — 2026-08-03"
    if heading in text:
        return
    entry = f"""{heading}

- Complete direct cloud backup providers for OneDrive App Folder, Dropbox App Folder, WebDAV/Nextcloud, and S3-compatible storage while retaining Google Drive app-data and Android's scoped folder picker.
- Use OAuth Authorization Code with PKCE for OneDrive and Dropbox, encrypted local credential storage, HTTPS-only direct endpoints, least-privilege app-folder scopes, paginated backup browsing, resumable uploads, and confirmed deletion.
- Support first-run browsing, preview, and restore across every cloud provider, including multipart S3 uploads for Linux-inclusive backups; keep cloud credentials and sessions excluded from portable archives.

"""
    path.write_text(entry + text, encoding="utf-8")


def validate_sources() -> None:
    required_paths = [
        "app/src/main/java/app/xylune/chat/transfer/CloudOAuthManager.kt",
        "app/src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt",
        "app/src/main/java/app/xylune/chat/transfer/DirectCloudConfigStore.kt",
        "app/src/main/java/app/xylune/chat/transfer/DirectCloudModels.kt",
        "app/src/main/java/app/xylune/chat/ui/DirectCloudProvidersUi.kt",
        "app/src/test/java/app/xylune/chat/transfer/DirectCloudConfigurationTest.kt",
        "docs/CLOUD_PROVIDERS_SETUP.md",
        "docs/releases/RELEASE_NOTES_0.23.1.md",
    ]
    missing = [path for path in required_paths if not (ROOT / path).is_file()]
    if missing:
        raise RuntimeError("Missing ported cloud files: " + ", ".join(missing))

    checked = [ROOT / destination for destination in PATHS.values()]
    checked.extend([ROOT / "CHANGELOG.md"])
    forbidden = ("app.arbor.chat", "app/arbor/chat", "ARBOR_MICROSOFT_CLIENT_ID", "ARBOR_DROPBOX_APP_KEY")
    errors: list[str] = []
    for path in checked:
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if "<<<<<<<" in text or "=======" in text or ">>>>>>>" in text:
            errors.append(f"merge markers remain in {path.relative_to(ROOT)}")
        for token in forbidden:
            if token in text:
                errors.append(f"{token!r} remains in {path.relative_to(ROOT)}")
    if errors:
        raise RuntimeError("\n".join(errors))

    build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    required_build_tokens = (
        f"versionCode = {TARGET_VERSION_CODE}",
        f'versionName = "{TARGET_VERSION_NAME}"',
        "XYLUNE_MICROSOFT_CLIENT_ID",
        "XYLUNE_DROPBOX_APP_KEY",
    )
    absent = [token for token in required_build_tokens if token not in build]
    if absent:
        raise RuntimeError("Build configuration is missing: " + ", ".join(absent))


def main() -> None:
    # The workflow fetches both refs explicitly, but fail clearly if it did not.
    run("git", "cat-file", "-e", f"{OLD_BASE}^{{commit}}")
    run("git", "cat-file", "-e", f"{CLOUD_HEAD}^{{commit}}")

    changed: list[str] = []
    for old_path, new_path in PATHS.items():
        theirs_raw = show(CLOUD_HEAD, old_path)
        if theirs_raw is None:
            raise RuntimeError(f"Cloud source is missing {old_path}")
        theirs = transform(theirs_raw, old_path)
        base_raw = show(OLD_BASE, old_path)
        destination = ROOT / new_path
        destination.parent.mkdir(parents=True, exist_ok=True)

        if base_raw is None:
            destination.write_bytes(theirs)
        else:
            base = transform(base_raw, old_path)
            if destination.exists():
                merge_file(destination, base, theirs)
            else:
                destination.write_bytes(theirs)
        changed.append(new_path)

    set_release_version()
    update_changelog()
    validate_sources()

    print(f"Ported direct cloud backup support into {len(changed)} Xylune paths.")
    for path in changed:
        print(path)


if __name__ == "__main__":
    main()
