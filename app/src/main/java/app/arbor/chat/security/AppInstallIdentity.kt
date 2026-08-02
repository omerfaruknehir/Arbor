package app.arbor.chat.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.annotation.RequiresApi
import java.security.MessageDigest

data class AppInstallIdentity(
    val packageName: String,
    val signingSha1: String,
    val signingSha256: String,
)

fun Context.currentAppInstallIdentity(): AppInstallIdentity {
    val signatures = currentSigningCertificates()
    val certificate = signatures.firstOrNull()?.toByteArray() ?: byteArrayOf()
    return AppInstallIdentity(
        packageName = packageName,
        signingSha1 = certificate.fingerprint("SHA-1"),
        signingSha256 = certificate.fingerprint("SHA-256"),
    )
}

@Suppress("DEPRECATION")
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

internal fun ByteArray.fingerprint(algorithm: String): String {
    if (isEmpty()) return "Unavailable"
    return MessageDigest.getInstance(algorithm)
        .digest(this)
        .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }
}

internal fun normalizeCertificateFingerprint(value: String): String =
    value.filter(Char::isLetterOrDigit).uppercase()
