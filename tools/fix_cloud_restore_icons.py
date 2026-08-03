#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Expected text not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content)


# Direct-cloud downloads are written below cache/cloud-backups/<provider>. The
# FileProvider previously exposed only drive-app-data, so OneDrive, Dropbox,
# WebDAV and S3 downloads failed before archive inspection could begin.
file_paths = ROOT / "app/src/main/res/xml/file_paths.xml"
text = file_paths.read_text()
if 'path="cloud-backups/"' not in text:
    text = text.replace(
        '    <cache-path name="drive_app_data" path="drive-app-data/" />\n',
        '    <cache-path name="drive_app_data" path="drive-app-data/" />\n'
        '    <cache-path name="cloud_backups" path="cloud-backups/" />\n',
    )
file_paths.write_text(text)

# Brand vectors use the providers' recognizable marks and brand colors. The
# paths are from Simple Icons (CC0-1.0); provider names and marks remain the
# trademarks of their respective owners.
write(
    ROOT / "app/src/main/res/drawable/ic_google_drive.xml",
    '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FBBC04"
        android:pathData="M4.433,22.396l4,-6.929H24l-4,6.929H4.433z" />
    <path
        android:fillColor="#34A853"
        android:pathData="M7.999,15.467l-3.998,6.929L0,15.467L7.785,1.98l3.999,6.931z" />
    <path
        android:fillColor="#4285F4"
        android:pathData="M23.783,15.092h-7.999L7.999,1.605h8.002l7.785,13.486z" />
</vector>
''',
)
write(
    ROOT / "app/src/main/res/drawable/ic_onedrive.xml",
    '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#0078D4"
        android:pathData="M19.453,9.95q0.961,0.058 1.787,0.468 0.826,0.41 1.442,1.066 0.615,0.657 0.966,1.512 0.352,0.856 0.352,1.816 0,1.008 -0.387,1.893 -0.386,0.885 -1.049,1.547 -0.662,0.662 -1.546,1.049 -0.885,0.387 -1.893,0.387H6q-1.242,0 -2.332,-0.475 -1.09,-0.475 -1.904,-1.29 -0.815,-0.814 -1.29,-1.903Q0,14.93 0,13.688q0,-0.985 0.31,-1.887 0.311,-0.903 0.862,-1.658 0.55,-0.756 1.324,-1.325 0.774,-0.568 1.711,-0.861 0.434,-0.129 0.85,-0.187 0.416,-0.06 0.861,-0.082h0.012q0.515,-0.786 1.207,-1.413 0.691,-0.627 1.5,-1.066 0.808,-0.44 1.705,-0.668 0.896,-0.229 1.845,-0.229 1.278,0 2.456,0.417 1.177,0.416 2.144,1.16 0.967,0.744 1.658,1.78 0.692,1.038 1.008,2.28zM12.188,5.813q-1.325,0 -2.52,0.544 -1.195,0.545 -2.04,1.565 0.446,0.117 0.85,0.299 0.405,0.181 0.792,0.416l4.78,2.86 2.731,-1.15q0.27,-0.117 0.545,-0.204 0.276,-0.088 0.58,-0.147 -0.293,-0.937 -0.855,-1.705 -0.563,-0.768 -1.319,-1.318 -0.755,-0.551 -1.658,-0.856 -0.902,-0.304 -1.886,-0.304zM2.414,16.395l9.914,-4.184 -3.832,-2.297q-0.586,-0.351 -1.23,-0.539 -0.645,-0.188 -1.325,-0.188 -0.914,0 -1.722,0.364 -0.809,0.363 -1.412,0.978 -0.604,0.616 -0.955,1.436 -0.352,0.82 -0.352,1.723 0,0.703 0.234,1.423 0.235,0.721 0.68,1.284zM19.125,18.188q0.563,0 1.078,-0.176 0.516,-0.176 0.961,-0.516l-7.23,-4.324 -10.301,4.336q0.527,0.328 1.13,0.504 0.604,0.175 1.237,0.175zM22.137,16.336q0.363,-0.727 0.363,-1.523 0,-0.774 -0.293,-1.407t-0.791,-1.072q-0.498,-0.44 -1.166,-0.68 -0.668,-0.24 -1.406,-0.24 -0.422,0 -0.838,0.1t-0.815,0.252q-0.398,0.152 -0.785,0.334 -0.386,0.181 -0.761,0.345z" />
</vector>
''',
)
write(
    ROOT / "app/src/main/res/drawable/ic_dropbox.xml",
    '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#0061FF"
        android:pathData="M6,1.807L0,5.629l6,3.822 6.001,-3.822L6,1.807zM18,1.807l-6,3.822 6,3.822 6,-3.822 -6,-3.822zM0,13.274l6,3.822 6.001,-3.822L6,9.452 0,13.274zM18,9.452l-6,3.822 6,3.822 6,-3.822 -6,-3.822zM6,18.371l6.001,3.822 6,-3.822 -6,-3.822L6,18.371z" />
</vector>
''',
)
write(
    ROOT / "app/src/main/res/drawable/ic_nextcloud.xml",
    '''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#0082C9"
        android:pathData="M12.018,6.537c-2.5,0 -4.6,1.712 -5.241,4.015 -0.56,-1.232 -1.793,-2.105 -3.225,-2.105A3.569,3.569 0,0 0,0 12a3.569,3.569 0,0 0,3.552 3.553c1.432,0 2.664,-0.874 3.224,-2.106 0.641,2.304 2.742,4.016 5.242,4.016 2.487,0 4.576,-1.693 5.231,-3.977 0.569,1.21 1.783,2.067 3.198,2.067A3.568,3.568 0,0 0,24 12a3.569,3.569 0,0 0,-3.553 -3.553c-1.416,0 -2.63,0.858 -3.199,2.067 -0.654,-2.284 -2.743,-3.978 -5.23,-3.977zM12.018,8.622c1.878,0 3.378,1.5 3.378,3.378 0,1.878 -1.5,3.378 -3.378,3.378A3.362,3.362 0,0 1,8.641 12c0,-1.878 1.5,-3.378 3.377,-3.378zM3.552,10.532c0.822,0 1.467,0.645 1.467,1.468s-0.644,1.467 -1.467,1.468A1.452,1.452 0,0 1,2.085 12c0,-0.823 0.644,-1.467 1.467,-1.467zM20.447,10.532c0.823,0 1.468,0.645 1.468,1.468s-0.645,1.468 -1.468,1.468A1.452,1.452 0,0 1,18.98 12c0,-0.823 0.644,-1.467 1.467,-1.467z" />
</vector>
''',
)

restore = ROOT / "app/src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt"
text = restore.read_text()
imports = {
    "import android.app.Activity\n": "import android.app.Activity\nimport androidx.annotation.DrawableRes\n",
    "import androidx.compose.material.icons.outlined.Refresh\n": "import androidx.compose.material.icons.outlined.Refresh\nimport androidx.compose.material.icons.outlined.Storage\n",
    "import androidx.compose.ui.Alignment\n": "import androidx.compose.ui.Alignment\nimport androidx.compose.ui.graphics.Color\n",
    "import androidx.compose.ui.platform.LocalContext\n": "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.res.painterResource\n",
    "import app.xylune.chat.transfer.XYLUNE_BACKUP_MIME\n": "import app.xylune.chat.R\nimport app.xylune.chat.transfer.XYLUNE_BACKUP_MIME\n",
}
for old, new in imports.items():
    if new.splitlines()[-1] not in text:
        if old not in text:
            raise SystemExit(f"Import anchor missing: {old!r}")
        text = text.replace(old, new, 1)

replacements = [
    (
        '                            Icon(Icons.Outlined.Cloud, null)\n                            Text(" Google Drive app storage", Modifier.padding(start = 6.dp))',
        '                            SetupProviderIcon(R.drawable.ic_google_drive, "Google Drive")\n                            Text("Google Drive app storage", Modifier.padding(start = 10.dp))',
    ),
    (
        '                            Icon(Icons.Outlined.Cloud, null)\n                            Text(\n                                if (oauthStates[CloudOAuthProvider.ONEDRIVE] is CloudOAuthState.Connected)',
        '                            SetupProviderIcon(R.drawable.ic_onedrive, "OneDrive")\n                            Text(\n                                if (oauthStates[CloudOAuthProvider.ONEDRIVE] is CloudOAuthState.Connected)',
    ),
    (
        '                            Icon(Icons.Outlined.Cloud, null)\n                            Text(\n                                if (oauthStates[CloudOAuthProvider.DROPBOX] is CloudOAuthState.Connected)',
        '                            SetupProviderIcon(R.drawable.ic_dropbox, "Dropbox")\n                            Text(\n                                if (oauthStates[CloudOAuthProvider.DROPBOX] is CloudOAuthState.Connected)',
    ),
    (
        '                            Icon(Icons.Outlined.Cloud, null)\n                            Text(\n                                if (directConfigurations.webDav == null) " Configure WebDAV / Nextcloud"',
        '                            SetupProviderIcon(R.drawable.ic_nextcloud, "Nextcloud / WebDAV")\n                            Text(\n                                if (directConfigurations.webDav == null) " Configure Nextcloud / WebDAV"',
    ),
    (
        '                            Icon(Icons.Outlined.Cloud, null)\n                            Text(\n                                if (directConfigurations.s3 == null) " Configure S3-compatible storage"',
        '                            Icon(Icons.Outlined.Storage, contentDescription = "S3-compatible storage")\n                            Text(\n                                if (directConfigurations.s3 == null) " Configure S3-compatible storage"',
    ),
    ('                                ) { Text("Preview") }', '                                ) { Text("Review & restore") }'),
]
for old, new in replacements:
    if old not in text:
        raise SystemExit(f"Restore UI anchor missing: {old[:120]!r}")
    text = text.replace(old, new, 1)

helper_anchor = "\nprivate fun setupBackupMetadata(entry: CloudBackupEntry): String = buildString {"
helper = '''

@Composable
private fun SetupProviderIcon(@DrawableRes drawable: Int, description: String) {
    Icon(
        painter = painterResource(drawable),
        contentDescription = description,
        tint = Color.Unspecified,
    )
}
'''
if "private fun SetupProviderIcon" not in text:
    if helper_anchor not in text:
        raise SystemExit("Setup provider icon helper anchor missing")
    text = text.replace(helper_anchor, helper + helper_anchor, 1)
restore.write_text(text)

# Add a regression test that covers the exact FileProvider root which broke
# OneDrive/Dropbox/WebDAV/S3 restore and requires distinct provider marks.
write(
    ROOT / "app/src/test/java/app/xylune/chat/ui/CloudRestoreRegressionTest.kt",
    '''package app.xylune.chat.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRestoreRegressionTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun directProviderDownloadsAreExposedToArchiveInspector() {
        val paths = source("src/main/res/xml/file_paths.xml")
        val clients = source("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt")
        assertTrue(clients.contains("cloud-backups/$provider"))
        assertTrue(paths.contains("name=\\\"cloud_backups\\\""))
        assertTrue(paths.contains("path=\\\"cloud-backups/\\\""))
    }

    @Test
    fun setupRestoreUsesDistinctProviderIconsAndClearAction() {
        val restore = source("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt")
        assertTrue(restore.contains("R.drawable.ic_google_drive"))
        assertTrue(restore.contains("R.drawable.ic_onedrive"))
        assertTrue(restore.contains("R.drawable.ic_dropbox"))
        assertTrue(restore.contains("R.drawable.ic_nextcloud"))
        assertTrue(restore.contains("Icons.Outlined.Storage"))
        assertTrue(restore.contains("Review & restore"))
    }
}
''',
)

# Patch release identity for the repair build.
build = ROOT / "app/build.gradle.kts"
build_text = build.read_text()
build_text = build_text.replace('versionCode = 170', 'versionCode = 171', 1)
build_text = build_text.replace('versionName = "0.23.1"', 'versionName = "0.23.2"', 1)
build.write_text(build_text)

changelog = ROOT / "CHANGELOG.md"
change_text = changelog.read_text()
entry = '''## 0.23.2 — 2026-08-03

- Fix first-run restore from OneDrive, Dropbox, WebDAV/Nextcloud, and S3-compatible storage by exposing downloaded cloud archives through Xylune's FileProvider before inspection and import.
- Replace repeated generic-cloud placeholders with recognizable Google Drive, OneDrive, Dropbox, and Nextcloud marks, retain a neutral storage icon for S3-compatible providers, and clarify the backup action as “Review & restore”.

'''
if not change_text.startswith("## 0.23.2"):
    changelog.write_text(entry + change_text)

print("Applied Xylune cloud restore and provider icon repairs")
