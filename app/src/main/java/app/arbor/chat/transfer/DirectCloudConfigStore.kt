package app.arbor.chat.transfer

import app.arbor.chat.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DirectCloudConfigStore(private val secureStore: SecureStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DirectCloudConfigurationSnapshot> = _state.asStateFlow()

    fun saveWebDav(config: WebDavCloudConfig) {
        val normalized = validateWebDavConfig(config)
        secureStore.setCloudRecord(KEY_WEBDAV, json.encodeToString(normalized))
        _state.value = _state.value.copy(webDav = normalized)
    }

    fun clearWebDav() {
        secureStore.setCloudRecord(KEY_WEBDAV, null)
        _state.value = _state.value.copy(webDav = null)
    }

    fun saveS3(config: S3CloudConfig) {
        val normalized = validateS3Config(config)
        secureStore.setCloudRecord(KEY_S3, json.encodeToString(normalized))
        _state.value = _state.value.copy(s3 = normalized)
    }

    fun clearS3() {
        secureStore.setCloudRecord(KEY_S3, null)
        _state.value = _state.value.copy(s3 = null)
    }

    private fun load(): DirectCloudConfigurationSnapshot = DirectCloudConfigurationSnapshot(
        webDav = secureStore.cloudRecord(KEY_WEBDAV)?.let { raw ->
            runCatching { validateWebDavConfig(json.decodeFromString<WebDavCloudConfig>(raw)) }.getOrNull()
        },
        s3 = secureStore.cloudRecord(KEY_S3)?.let { raw ->
            runCatching { validateS3Config(json.decodeFromString<S3CloudConfig>(raw)) }.getOrNull()
        },
    )

    companion object {
        private const val KEY_WEBDAV = "provider_webdav_v1"
        private const val KEY_S3 = "provider_s3_v1"
    }
}

internal fun validateWebDavConfig(value: WebDavCloudConfig): WebDavCloudConfig {
    val url = value.folderUrl.trim().trimEnd('/') + "/"
    require(url.startsWith("https://")) { "WebDAV requires an HTTPS folder URL" }
    require(value.username.isNotBlank()) { "WebDAV username is required" }
    require(value.password.isNotBlank()) { "WebDAV password or app password is required" }
    return value.copy(
        label = value.label.trim().ifBlank { "WebDAV" }.take(80),
        folderUrl = url,
        username = value.username.trim(),
    )
}

internal fun validateS3Config(value: S3CloudConfig): S3CloudConfig {
    val endpoint = value.endpoint.trim().trimEnd('/')
    require(endpoint.startsWith("https://")) { "S3 endpoint must use HTTPS" }
    require(value.region.isNotBlank()) { "S3 region is required" }
    require(value.bucket.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._-]{1,62}$"))) { "S3 bucket name is invalid" }
    require(value.accessKeyId.isNotBlank()) { "S3 access key is required" }
    require(value.secretAccessKey.isNotBlank()) { "S3 secret key is required" }
    val prefix = value.prefix.trim().trim('/').let { if (it.isBlank()) "arbor" else it }
    return value.copy(
        label = value.label.trim().ifBlank { "S3" }.take(80),
        endpoint = endpoint,
        region = value.region.trim(),
        bucket = value.bucket.trim(),
        prefix = prefix,
        accessKeyId = value.accessKeyId.trim(),
        sessionToken = value.sessionToken?.trim()?.takeIf(String::isNotBlank),
    )
}
