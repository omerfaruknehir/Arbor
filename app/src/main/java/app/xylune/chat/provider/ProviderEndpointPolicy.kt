package app.xylune.chat.provider

import java.net.URI

object ProviderEndpointPolicy {
    fun validate(raw: String): String {
        val value = raw.trim().trimEnd('/')
        val uri = runCatching { URI(value) }.getOrElse { throw IllegalArgumentException("Provider URL is invalid") }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null) { "Provider URL must be an absolute URL without credentials or a fragment" }
        val local = uri.host.equals("localhost", true) || uri.host == "127.0.0.1" || uri.host == "::1" || uri.host == "10.0.2.2"
        require(uri.scheme == "https" || (uri.scheme == "http" && local)) {
            "Remote providers require HTTPS. Cleartext HTTP is limited to this device or the Android emulator."
        }
        return value
    }
}
