#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "app/src/main/java/app/arbor/chat/security/AppInstallIdentity.kt"
content = path.read_text()

old_import = "import android.os.Build\n"
new_import = "import android.os.Build\nimport androidx.annotation.RequiresApi\n"
if content.count(old_import) != 1:
    raise RuntimeError("Expected one Build import")
content = content.replace(old_import, new_import, 1)

old = '''@Suppress("DEPRECATION")
private fun Context.currentSigningCertificates(): Array<out Signature> {
    val flags = PackageManager.GET_SIGNING_CERTIFICATES
    val info = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageInfo(
            packageName,
            PackageManager.PackageInfoFlags.of(flags.toLong()),
        )
    } else {
        packageManager.getPackageInfo(packageName, flags)
    }
    val signingInfo = if (Build.VERSION.SDK_INT >= 28) info.signingInfo else null
    return when {
        signingInfo == null -> info.signatures.orEmpty()
        signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.orEmpty()
        else -> signingInfo.signingCertificateHistory.orEmpty()
    }
}
'''
new = '''@Suppress("DEPRECATION")
private fun Context.currentSigningCertificates(): Array<out Signature> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        currentSigningCertificatesApi28()
    } else {
        packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            .signatures
            .orEmpty()
    }

@RequiresApi(Build.VERSION_CODES.P)
private fun Context.currentSigningCertificatesApi28(): Array<out Signature> {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        currentPackageInfoApi33()
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
    }
    val signingInfo = requireNotNull(info.signingInfo)
    return if (signingInfo.hasMultipleSigners()) {
        signingInfo.apkContentsSigners.orEmpty()
    } else {
        signingInfo.signingCertificateHistory.orEmpty()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun Context.currentPackageInfoApi33() = packageManager.getPackageInfo(
    packageName,
    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
)
'''
if content.count(old) != 1:
    raise RuntimeError("Expected one generated signing certificate function")
content = content.replace(old, new, 1)
path.write_text(content)
print("Guarded signing certificate APIs for minSdk 26")
