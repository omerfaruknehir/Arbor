package app.arbor.chat.update

import app.arbor.chat.security.AppInstallIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryUpdateManagerTest {
    @Test
    fun repositoryOriginNormalizationAcceptsGitHubFormsOnly() {
        assertEquals("owner/repo", normalizeGitHubRepository("owner/repo"))
        assertEquals("owner/repo", normalizeGitHubRepository("https://github.com/owner/repo.git"))
        assertEquals("owner/repo", normalizeGitHubRepository("git@github.com:owner/repo.git"))
        assertNull(normalizeGitHubRepository("https://example.com/owner/repo"))
        assertNull(normalizeGitHubRepository(""))
    }

    @Test
    fun semanticVersionComparisonHandlesStableAndPrereleaseBuilds() {
        assertTrue(isRepositoryVersionNewer("0.22.4", "0.22.3"))
        assertTrue(isRepositoryVersionNewer("1.0.0", "1.0.0-beta"))
        assertFalse(isRepositoryVersionNewer("1.0.0-beta", "1.0.0"))
        assertFalse(isRepositoryVersionNewer("0.22.3", "0.22.4"))
        assertTrue(isRepositoryVersionNewer("0.1.0", "99.0.0", candidateVersionCode = 200, currentVersionCode = 199))
    }

    @Test
    fun releaseAssetSelectionPrefersManifestAndExactVersion() {
        val assets = listOf(
            RepositoryReleaseAsset("random.apk", "https://example/random"),
            RepositoryReleaseAsset("Arbor-0.22.4-release.apk", "https://example/exact"),
            RepositoryReleaseAsset("custom-release.apk", "https://example/custom"),
        )
        assertEquals(
            "custom-release.apk",
            selectRepositoryReleaseApk(assets, "0.22.4", "custom-release.apk")?.name,
        )
        assertEquals(
            "Arbor-0.22.4-release.apk",
            selectRepositoryReleaseApk(assets, "0.22.4")?.name,
        )
    }

    @Test
    fun directInstallRequiresPackageAndSigningCertificateMatch() {
        val manifest = RepositoryReleaseManifest(
            repository = "owner/repo",
            tag = "v0.22.4",
            versionName = "0.22.4",
            versionCode = 168,
            packageName = "app.arbor.chat.debug",
            apkAsset = "Arbor-0.22.4-release.apk",
            apkSha256 = "abc",
            signingCertificateSha256 = "AA:BB:CC",
            sourceCommit = "deadbeef",
        )
        assertTrue(
            isRepositoryReleaseInstallCompatible(
                manifest,
                AppInstallIdentity("app.arbor.chat.debug", "11", "AABBCC"),
            ),
        )
        assertFalse(
            isRepositoryReleaseInstallCompatible(
                manifest,
                AppInstallIdentity("app.arbor.chat", "11", "AABBCC"),
            ),
        )
        assertFalse(
            isRepositoryReleaseInstallCompatible(
                manifest,
                AppInstallIdentity("app.arbor.chat.debug", "11", "DDEEFF"),
            ),
        )
    }
}
