#!/usr/bin/env python3
"""Harden Xylune's direct cloud backup clients after the namespace port."""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "app/src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt"
UI = ROOT / "app/src/main/java/app/xylune/chat/ui/DirectCloudProvidersUi.kt"
TEST = ROOT / "app/src/test/java/app/xylune/chat/transfer/DirectCloudConfigurationTest.kt"
CHANGELOG = ROOT / "CHANGELOG.md"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected one {label} block, found {count}")
    return text.replace(old, new, 1)


def harden_client() -> None:
    text = CLIENT.read_text(encoding="utf-8")
    text = replace_once(text, "import java.net.URI\n", "", "obsolete URI import")
    text = replace_once(
        text,
        "    .callTimeout(15, TimeUnit.MINUTES)\n",
        "",
        "fixed whole-call timeout",
    )

    text = replace_once(
        text,
        '''        val values = mutableListOf<CloudBackupEntry>()
        var nextUrl: String? = initial
        while (nextUrl != null) {
            val pageUrl = requireNotNull(nextUrl)
            require(pageUrl.startsWith(GRAPH)) { "OneDrive returned an invalid pagination URL" }
''',
        '''        val values = mutableListOf<CloudBackupEntry>()
        val visitedPages = mutableSetOf<String>()
        var nextUrl: String? = initial
        while (nextUrl != null) {
            val pageUrl = requireNotNull(nextUrl)
            require(pageUrl.startsWith(GRAPH)) { "OneDrive returned an invalid pagination URL" }
            require(visitedPages.add(pageUrl)) { "OneDrive returned a repeated pagination URL" }
''',
        "OneDrive pagination",
    )

    text = replace_once(
        text,
        '''        val values = mutableListOf<CloudBackupEntry>()
        var cursor: String? = null
        do {
''',
        '''        val values = mutableListOf<CloudBackupEntry>()
        val seenCursors = mutableSetOf<String>()
        var cursor: String? = null
        do {
''',
        "Dropbox pagination setup",
    )
    text = replace_once(
        text,
        '''                cursor = if (root["has_more"]?.jsonPrimitive?.contentOrNull == "true") {
                    root["cursor"]?.jsonPrimitive?.contentOrNull
                } else null
            }
        } while (cursor != null)
''',
        '''                cursor = if (root["has_more"]?.jsonPrimitive?.contentOrNull == "true") {
                    root["cursor"]?.jsonPrimitive?.contentOrNull?.also { next ->
                        require(next.isNotBlank()) { "Dropbox returned an empty pagination cursor" }
                        require(seenCursors.add(next)) { "Dropbox returned a repeated pagination cursor" }
                    }
                } else null
            }
        } while (cursor != null)
''',
        "Dropbox pagination result",
    )

    text = replace_once(
        text,
        '''        val values = mutableListOf<CloudBackupEntry>()
        var continuationToken: String? = null
        do {
''',
        '''        val values = mutableListOf<CloudBackupEntry>()
        val seenContinuationTokens = mutableSetOf<String>()
        var continuationToken: String? = null
        do {
''',
        "S3 pagination setup",
    )
    text = replace_once(
        text,
        '''                values += page.entries
                page.nextContinuationToken
            }
        } while (continuationToken != null)
''',
        '''                values += page.entries
                page.nextContinuationToken?.also { next ->
                    require(next.isNotBlank()) { "S3 returned an empty continuation token" }
                    require(seenContinuationTokens.add(next)) { "S3 returned a repeated continuation token" }
                }
            }
        } while (continuationToken != null)
''',
        "S3 pagination result",
    )

    text = replace_once(
        text,
        '''private fun resolveWebDav(base: String, value: String): String {
    if (value.startsWith("https://")) return value
    return URI(base).resolve(value).toString()
}
''',
        '''internal fun resolveWebDav(base: String, value: String): String {
    val baseUrl = base.toHttpUrl()
    val resolved = baseUrl.resolve(value)
        ?: throw IllegalArgumentException("WebDAV returned an invalid backup URL")
    require(resolved.scheme == "https") { "WebDAV backup URLs must use HTTPS" }
    require(resolved.host == baseUrl.host && resolved.port == baseUrl.port) {
        "WebDAV returned a backup URL outside the configured server"
    }
    val folderPath = baseUrl.encodedPath.trimEnd('/') + "/"
    require(resolved.encodedPath.startsWith(folderPath)) {
        "WebDAV returned a backup URL outside the configured folder"
    }
    return resolved.toString()
}
''',
        "WebDAV URL resolver",
    )

    text = replace_once(
        text,
        "private data class S3ListPage(\n",
        "internal data class S3ListPage(\n",
        "S3 page visibility",
    )
    text = replace_once(
        text,
        "private fun parseS3Page(raw: String): S3ListPage {\n",
        "internal fun parseS3Page(raw: String): S3ListPage {\n",
        "S3 parser visibility",
    )
    text = replace_once(
        text,
        '''            XmlPullParser.TEXT -> if (inContents) {
                val text = parser.text.orEmpty().trim()
                when (currentTag) {
                    "Key" -> key = text
                    "Size" -> size = text.toLongOrNull() ?: 0L
                    "LastModified" -> modified = runCatching { Instant.parse(text).toEpochMilli() }.getOrDefault(0L)
                    "NextContinuationToken" -> continuationToken = text.takeIf(String::isNotBlank)
                }
            }
''',
        '''            XmlPullParser.TEXT -> {
                val text = parser.text.orEmpty().trim()
                when {
                    currentTag == "NextContinuationToken" ->
                        continuationToken = text.takeIf(String::isNotBlank)
                    inContents -> when (currentTag) {
                        "Key" -> key = text
                        "Size" -> size = text.toLongOrNull() ?: 0L
                        "LastModified" -> modified = runCatching {
                            Instant.parse(text).toEpochMilli()
                        }.getOrDefault(0L)
                    }
                }
            }
''',
        "S3 continuation parser",
    )

    CLIENT.write_text(text, encoding="utf-8")


def harden_ui() -> None:
    text = UI.read_text(encoding="utf-8")
    text = replace_once(
        text,
        '''            is CloudOAuthState.Authorizing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Waiting for ${provider.displayName} authorization…")
                }
            }
''',
        '''            is CloudOAuthState.Authorizing -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                    Text("Waiting for ${provider.displayName} authorization…", modifier = Modifier.weight(1f))
                }
                OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel sign-in")
                }
            }
''',
        "OAuth authorizing UI",
    )
    UI.write_text(text, encoding="utf-8")


def add_tests() -> None:
    text = TEST.read_text(encoding="utf-8")
    marker = "\n}\n"
    if not text.endswith(marker):
        raise RuntimeError("Cloud configuration test class ending was not recognized")
    additions = '''

    @Test
    fun webDavBackupUrlsStayInsideConfiguredHttpsFolder() {
        val base = "https://cloud.example/remote.php/dav/files/user/Xylune/"
        assertEquals(
            "https://cloud.example/remote.php/dav/files/user/Xylune/backup.xylune-backup",
            resolveWebDav(base, "backup.xylune-backup"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            resolveWebDav(base, "https://attacker.example/stolen.xylune-backup")
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveWebDav(base, "../outside/stolen.xylune-backup")
        }
    }

    @Test
    fun s3ContinuationTokenIsHandledOutsideContentsElements() {
        val client = java.io.File("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt").readText()
        val tokenBranch = client.indexOf("currentTag == \\"NextContinuationToken\\"")
        val contentsBranch = client.indexOf("inContents -> when", tokenBranch)
        assertTrue(tokenBranch >= 0)
        assertTrue(contentsBranch > tokenBranch)
    }

    @Test
    fun cloudPaginationRejectsRepeatedProviderCursors() {
        val client = java.io.File("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt").readText()
        assertTrue(client.contains("OneDrive returned a repeated pagination URL"))
        assertTrue(client.contains("Dropbox returned a repeated pagination cursor"))
        assertTrue(client.contains("S3 returned a repeated continuation token"))
    }

    @Test
    fun largeCloudBackupsHaveNoFixedWholeCallDeadline() {
        val client = java.io.File("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt").readText()
        assertTrue(!client.contains(".callTimeout("))
        assertTrue(client.contains(".readTimeout(10, TimeUnit.MINUTES)"))
        assertTrue(client.contains(".writeTimeout(10, TimeUnit.MINUTES)"))
    }

    @Test
    fun oauthAuthorizationCanBeCancelledFromTheProviderCard() {
        val ui = java.io.File("src/main/java/app/xylune/chat/ui/DirectCloudProvidersUi.kt").readText()
        assertTrue(ui.contains("Cancel sign-in"))
        assertTrue(ui.contains("OutlinedButton(onClick = onDisconnect"))
    }
'''
    text = text[:-len(marker)] + additions + marker
    TEST.write_text(text, encoding="utf-8")


def update_changelog() -> None:
    text = CHANGELOG.read_text(encoding="utf-8")
    anchor = (
        "- Support first-run browsing, preview, and restore across every cloud provider, including multipart S3 uploads for Linux-inclusive backups; keep cloud credentials and sessions excluded from portable archives.\n"
    )
    extra = (
        "- Harden cloud transport by constraining authenticated WebDAV URLs to the configured HTTPS folder, parsing S3 continuation tokens correctly, rejecting repeated pagination cursors, removing the fixed whole-transfer deadline, and allowing pending OAuth sign-in to be cancelled.\n"
    )
    if extra in text:
        return
    if anchor not in text:
        raise RuntimeError("Could not locate the 0.23.1 cloud changelog entry")
    CHANGELOG.write_text(text.replace(anchor, anchor + extra, 1), encoding="utf-8")


def validate() -> None:
    client = CLIENT.read_text(encoding="utf-8")
    ui = UI.read_text(encoding="utf-8")
    tests = TEST.read_text(encoding="utf-8")
    required = [
        "WebDAV returned a backup URL outside the configured server",
        "WebDAV returned a backup URL outside the configured folder",
        'currentTag == "NextContinuationToken"',
        "OneDrive returned a repeated pagination URL",
        "Dropbox returned a repeated pagination cursor",
        "S3 returned a repeated continuation token",
    ]
    missing = [token for token in required if token not in client]
    if missing:
        raise RuntimeError("Missing cloud hardening tokens: " + ", ".join(missing))
    if ".callTimeout(" in client or "java.net.URI" in client:
        raise RuntimeError("The fixed transfer deadline or obsolete URI resolver remains")
    if "Cancel sign-in" not in ui:
        raise RuntimeError("OAuth cancellation UI is missing")
    if "webDavBackupUrlsStayInsideConfiguredHttpsFolder" not in tests:
        raise RuntimeError("Cloud hardening tests are missing")


def main() -> None:
    harden_client()
    harden_ui()
    add_tests()
    update_changelog()
    validate()
    print("Hardened Xylune cloud backup pagination, WebDAV isolation, transfer deadlines, and OAuth cancellation.")


if __name__ == "__main__":
    main()
