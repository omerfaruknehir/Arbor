from pathlib import Path
import subprocess
import sys

subprocess.run([sys.executable, "scripts/apply_cloud_linux_backup_0_20_24.py"], check=True)


def replace_once(path: str, old: str, new: str, label: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count == 1:
        file.write_text(text.replace(old, new, 1))
        return
    if count == 0 and new in text:
        return
    raise RuntimeError(f"{label}: expected one anchor in {path}, found {count}")


# Absolute symbolic links are normal inside Linux root filesystems and are safe
# to recreate as links. Hard links, unlike symlinks, must still resolve inside
# the restored archive root because tar extraction materializes their target.
runner = Path("app/src/main/python/sandbox_runner.py")
text = runner.read_text()
old = '''    if member.issym() or member.islnk():
        link = member.linkname.replace("\\\\", "/")
        if link.startswith("/"):
            raise ValueError("Linux environment archive contains an absolute link")
        resolved = os.path.normpath(os.path.join(os.path.dirname(normalized), link))
        if resolved == ".." or resolved.startswith("../"):
            raise ValueError("Linux environment archive contains a link outside its root")
'''
new = '''    if member.islnk():
        link = member.linkname.replace("\\\\", "/")
        if link.startswith("/"):
            raise ValueError("Linux environment archive contains an absolute hard link")
        resolved = os.path.normpath(os.path.join(os.path.dirname(normalized), link))
        if resolved == ".." or resolved.startswith("../"):
            raise ValueError("Linux environment archive contains a hard link outside its root")
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise RuntimeError("Could not harden portable Linux link extraction")
runner.write_text(text)

replace_once(
    "app/src/main/java/app/arbor/chat/transfer/CloudBackupClients.kt",
    'put("parents", kotlinx.serialization.json.buildJsonArray { add("appDataFolder") })',
    'put("parents", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("appDataFolder")) })',
    "Google Drive appDataFolder metadata",
)
replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    "internal data class PortableLinuxEnvironment(",
    "data class PortableLinuxEnvironment(",
    "portable Linux environment visibility",
)
replace_once(
    "app/src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt",
    "internal data class PreparedLinuxEnvironment(",
    "data class PreparedLinuxEnvironment(",
    "prepared Linux environment visibility",
)
replace_once(
    "app/src/main/java/app/arbor/chat/ui/TransferUi.kt",
    "@Composable\nprivate fun TransferHeading(",
    "@Composable\ninternal fun TransferHeading(",
    "shared transfer heading visibility",
)

Path("app/src/test/java/app/arbor/chat/ui/CloudLinuxBackupFeatureTest.kt").write_text(r'''package app.arbor.chat.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLinuxBackupFeatureTest {
    private fun source(path: String): String = File(path).readText()

    @Test
    fun driveAuthorizationRequestsOnlyAppDataScope() {
        val source = source("src/main/java/app/arbor/chat/ui/CloudBackupUi.kt")
        assertTrue(source.contains("Scope(Scopes.DRIVE_APPFOLDER)"))
        assertFalse(source.contains("Scopes.DRIVE_FILE"))
        assertFalse(source.contains("Scopes.DRIVE_READONLY"))
    }

    @Test
    fun portableBackupCanIncludeLinuxEnvironments() {
        val archive = source("src/main/java/app/arbor/chat/transfer/ArborArchiveManager.kt")
        val linux = source("src/main/java/app/arbor/chat/transfer/LinuxEnvironmentArchiveStore.kt")
        assertTrue(archive.contains("includeLinuxEnvironments: Boolean = false"))
        assertTrue(archive.contains("linuxEnvironments.prepareSnapshots()"))
        assertTrue(linux.contains(".restore-"))
        assertTrue(linux.contains("runtime.properties"))
    }

    @Test
    fun androidBackupExcludesSecretsAndLargePrivateData() {
        val manifest = source("src/main/AndroidManifest.xml")
        val rules = source("src/main/res/xml/data_extraction_rules.xml")
        assertTrue(manifest.contains("android:allowBackup"))
        assertTrue(manifest.contains("@xml/backup_rules"))
        assertTrue(rules.contains("arbor_secrets.xml"))
        assertTrue(rules.contains("ubuntu/"))
        assertTrue(rules.contains("linux-runtimes/"))
        assertTrue(rules.contains("attachments/"))
    }
}
''')

print("Cloud and Linux backup patch driver completed.")
