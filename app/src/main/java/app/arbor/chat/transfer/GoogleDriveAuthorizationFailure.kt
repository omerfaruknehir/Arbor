package app.arbor.chat.transfer

import android.content.Context
import app.arbor.chat.security.AppInstallIdentity
import app.arbor.chat.security.currentAppInstallIdentity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

enum class GoogleDriveAuthorizationFailureKind {
    UNREGISTERED_ON_API_CONSOLE,
    CANCELED,
    OTHER,
}

data class GoogleDriveAuthorizationFailure(
    val kind: GoogleDriveAuthorizationFailureKind,
    val title: String,
    val userMessage: String,
    val technicalDetails: String,
    val identity: AppInstallIdentity,
    val setupGuideUrl: String?,
) {
    fun copyableSetupDetails(): String = buildString {
        appendLine("Arbor Google Drive OAuth registration")
        appendLine("Package: ${identity.packageName}")
        appendLine("Signing SHA-1: ${identity.signingSha1}")
        appendLine("Signing SHA-256: ${identity.signingSha256}")
        appendLine("Scope: https://www.googleapis.com/auth/drive.appdata")
        if (technicalDetails.isNotBlank()) appendLine("Google error: $technicalDetails")
        setupGuideUrl?.let { appendLine("Setup guide: $it") }
    }.trim()
}

internal fun classifyGoogleDriveAuthorizationFailure(
    message: String?,
    statusCode: Int? = null,
): GoogleDriveAuthorizationFailureKind {
    val normalized = message.orEmpty()
    return when {
        normalized.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true) ->
            GoogleDriveAuthorizationFailureKind.UNREGISTERED_ON_API_CONSOLE
        statusCode == CommonStatusCodes.CANCELED ||
            normalized.contains("canceled", ignoreCase = true) ||
            normalized.contains("cancelled", ignoreCase = true) ->
            GoogleDriveAuthorizationFailureKind.CANCELED
        else -> GoogleDriveAuthorizationFailureKind.OTHER
    }
}

fun Context.describeGoogleDriveAuthorizationFailure(
    error: Throwable,
    sourceRepository: String,
): GoogleDriveAuthorizationFailure {
    val identity = currentAppInstallIdentity()
    val statusCode = (error as? ApiException)?.statusCode
    val technical = error.message.orEmpty().ifBlank { error.javaClass.simpleName }
    val kind = classifyGoogleDriveAuthorizationFailure(technical, statusCode)
    val guide = sourceRepository
        .takeIf { it.matches(Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) }
        ?.let { "https://github.com/$it/blob/main/docs/GOOGLE_DRIVE_SETUP.md" }
    return when (kind) {
        GoogleDriveAuthorizationFailureKind.UNREGISTERED_ON_API_CONSOLE -> GoogleDriveAuthorizationFailure(
            kind = kind,
            title = "Google Drive setup required",
            userMessage = "Google accepted the account selection, but this build is not registered as an Android OAuth client. Enable the Google Drive API and register the package name with the signing SHA-1 shown below.",
            technicalDetails = technical,
            identity = identity,
            setupGuideUrl = guide,
        )
        GoogleDriveAuthorizationFailureKind.CANCELED -> GoogleDriveAuthorizationFailure(
            kind = kind,
            title = "Google Drive connection canceled",
            userMessage = "No Google Drive permission was granted.",
            technicalDetails = technical,
            identity = identity,
            setupGuideUrl = guide,
        )
        GoogleDriveAuthorizationFailureKind.OTHER -> GoogleDriveAuthorizationFailure(
            kind = kind,
            title = "Google Drive authorization failed",
            userMessage = technical.ifBlank { "Google Drive authorization failed." },
            technicalDetails = technical,
            identity = identity,
            setupGuideUrl = guide,
        )
    }
}
