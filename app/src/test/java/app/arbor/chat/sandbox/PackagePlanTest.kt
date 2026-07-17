package app.arbor.chat.sandbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PackagePlanTest {
    @Test
    fun alreadyInstalledPlanCannotEnableInstaller() {
        val plan = PackagePlan(
            PackageEcosystem.PIP,
            listOf(PackagePlanItem("requests>=2", "requests", "2.32.4", "2.32.4", PackageAction.ALREADY_INSTALLED)),
        )
        assertTrue(plan.isValid)
        assertFalse(plan.hasChanges)
    }

    @Test
    fun dependenciesArePartOfAptTransaction() {
        val plan = PackagePlan(
            PackageEcosystem.APT,
            listOf(
                PackagePlanItem("ffmpeg", "ffmpeg", action = PackageAction.INSTALL),
                PackagePlanItem("libavcodec", "libavcodec", action = PackageAction.INSTALL, detail = "Dependency"),
            ),
        )
        assertTrue(plan.hasChanges)
        assertTrue(plan.items.any { it.detail == "Dependency" })
    }

    @Test
    fun approvalFingerprintCoversResolvedDependencyVersions() {
        val first = PackagePlan(
            PackageEcosystem.PIP,
            listOf(
                PackagePlanItem("Pillow", "Pillow", candidateVersion = "11.2.1", action = PackageAction.INSTALL),
                PackagePlanItem("numpy", "numpy", candidateVersion = "2.2.0", action = PackageAction.INSTALL, detail = "Dependency"),
            ),
        )
        val same = first.copy(rawPreview = "resolver logging is intentionally excluded")
        val changed = first.copy(items = first.items.map { if (it.name == "numpy") it.copy(candidateVersion = "2.3.0") else it })

        assertEquals(first.fingerprint(), same.fingerprint())
        assertNotEquals(first.fingerprint(), changed.fingerprint())
    }
}
