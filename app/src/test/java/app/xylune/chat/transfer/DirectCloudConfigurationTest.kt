package app.xylune.chat.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectCloudConfigurationTest {
    @Test
    fun webDavRequiresHttpsAndNormalizesFolderSlash() {
        val value = validateWebDavConfig(
            WebDavCloudConfig("Home", "https://cloud.example/dav/xylune", "omer", "app-password"),
        )
        assertEquals("https://cloud.example/dav/xylune/", value.folderUrl)
        assertThrows(IllegalArgumentException::class.java) {
            validateWebDavConfig(WebDavCloudConfig("Bad", "http://cloud.example/dav", "u", "p"))
        }
    }

    @Test
    fun s3NormalizesPrefixAndRequiresCredentials() {
        val value = validateS3Config(
            S3CloudConfig("R2", "https://example.r2.cloudflarestorage.com/", "auto", "xylune-backups", "/mobile/", "key", "secret"),
        )
        assertEquals("https://example.r2.cloudflarestorage.com", value.endpoint)
        assertEquals("mobile", value.prefix)
        assertThrows(IllegalArgumentException::class.java) {
            validateS3Config(value.copy(secretAccessKey = ""))
        }
    }

    @Test
    fun providerSetupUsesPublicBuildVariablesNotClientSecrets() {
        val build = java.io.File("build.gradle.kts").readText()
        val release = java.io.File("../.github/workflows/release.yml").readText()
        assertTrue(build.contains("XYLUNE_MICROSOFT_CLIENT_ID"))
        assertTrue(build.contains("XYLUNE_DROPBOX_APP_KEY"))
        assertTrue(release.contains("vars.XYLUNE_MICROSOFT_CLIENT_ID"))
        assertTrue(release.contains("vars.XYLUNE_DROPBOX_APP_KEY"))
        assertTrue(!release.contains("MICROSOFT_CLIENT_SECRET"))
        assertTrue(!release.contains("DROPBOX_APP_SECRET"))
    }

    @Test
    fun firstRunRestoreIncludesEveryDirectProvider() {
        val setup = java.io.File("src/main/java/app/xylune/chat/ui/SetupRestoreUi.kt").readText()
        assertTrue(setup.contains("SetupCloudSource.ONEDRIVE"))
        assertTrue(setup.contains("SetupCloudSource.DROPBOX"))
        assertTrue(setup.contains("SetupCloudSource.WEBDAV"))
        assertTrue(setup.contains("SetupCloudSource.S3"))
        assertTrue(setup.contains("downloadDirectCloudBackup"))
    }

    @Test
    fun s3UsesMultipartForLargeBackups() {
        val client = java.io.File("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt").readText()
        assertTrue(client.contains("multipartUpload(source, key)"))
        assertTrue(client.contains("CompleteMultipartUpload"))
        assertTrue(client.contains("S3_MAX_PARTS"))
    }

    @Test
    fun cloudListingsFollowProviderPagination() {
        val google = java.io.File("src/main/java/app/xylune/chat/transfer/CloudBackupClients.kt").readText()
        val direct = java.io.File("src/main/java/app/xylune/chat/transfer/DirectCloudBackupClients.kt").readText()
        assertTrue(google.contains("nextPageToken"))
        assertTrue(direct.contains("@odata.nextLink"))
        assertTrue(direct.contains("NextContinuationToken"))
    }

    @Test
    fun everyCloudBackupSurfaceSupportsDeletion() {
        val legacy = java.io.File("src/main/java/app/xylune/chat/ui/CloudBackupUi.kt").readText()
        val direct = java.io.File("src/main/java/app/xylune/chat/ui/DirectCloudProvidersUi.kt").readText()
        assertTrue(legacy.contains("deleteGoogleDriveBackup"))
        assertTrue(legacy.contains("deleteConnectedFolderBackup"))
        assertTrue(direct.contains("deleteDirectCloudBackup"))
    }

    @Test
    fun manifestRoutesNativeProviderCallbacks() {
        val manifest = java.io.File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:scheme=\"msauth\""))
        assertTrue(manifest.contains("android:scheme=\"\${dropboxOAuthScheme}\""))
    }

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
        val tokenBranch = client.indexOf("currentTag == \"NextContinuationToken\"")
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

}
