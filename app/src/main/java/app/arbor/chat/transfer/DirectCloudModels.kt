package app.arbor.chat.transfer

import kotlinx.serialization.Serializable

@Serializable
enum class CloudOAuthProvider(val displayName: String) {
    ONEDRIVE("OneDrive"),
    DROPBOX("Dropbox"),
}

enum class DirectCloudProvider(val displayName: String) {
    ONEDRIVE("OneDrive"),
    DROPBOX("Dropbox"),
    WEBDAV("WebDAV / Nextcloud"),
    S3("S3-compatible storage"),
}

sealed interface CloudOAuthState {
    data class Unavailable(val reason: String) : CloudOAuthState
    data object Disconnected : CloudOAuthState
    data class Authorizing(val startedAt: Long) : CloudOAuthState
    data class Connected(val accountLabel: String?) : CloudOAuthState
    data class Error(val message: String) : CloudOAuthState
}

@Serializable
data class CloudOAuthSession(
    val provider: CloudOAuthProvider,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochMs: Long,
    val accountLabel: String? = null,
    val scope: String? = null,
)

@Serializable
internal data class PendingCloudOAuth(
    val provider: CloudOAuthProvider,
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val createdAtEpochMs: Long,
)

@Serializable
data class WebDavCloudConfig(
    val label: String,
    val folderUrl: String,
    val username: String,
    val password: String,
)

@Serializable
data class S3CloudConfig(
    val label: String,
    val endpoint: String,
    val region: String,
    val bucket: String,
    val prefix: String,
    val accessKeyId: String,
    val secretAccessKey: String,
    val sessionToken: String? = null,
    val pathStyle: Boolean = true,
)

data class DirectCloudConfigurationSnapshot(
    val webDav: WebDavCloudConfig?,
    val s3: S3CloudConfig?,
)
