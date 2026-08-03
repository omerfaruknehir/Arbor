package app.arbor.chat.update

import android.content.Context
import androidx.core.content.edit
import app.arbor.chat.BuildConfig
import app.arbor.chat.security.AppInstallIdentity
import app.arbor.chat.security.currentAppInstallIdentity
import app.arbor.chat.security.normalizeCertificateFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface RepositoryUpdateState {
    data object Unsupported : RepositoryUpdateState
    data object Idle : RepositoryUpdateState
    data object Checking : RepositoryUpdateState
    data class UpToDate(
        val latestVersion: String,
        val checkedAt: Long,
    ) : RepositoryUpdateState
    data class Available(
        val release: RepositoryRelease,
        val checkedAt: Long,
    ) : RepositoryUpdateState
    data class Failed(
        val message: String,
        val checkedAt: Long,
    ) : RepositoryUpdateState
}

data class RepositoryRelease(
    val repository: String,
    val tagName: String,
    val versionName: String,
    val versionCode: Int?,
    val releasePageUrl: String,
    val apkDownloadUrl: String?,
    val publishedAt: String?,
    val notes: String,
    val directInstallCompatible: Boolean,
    val compatibilityMessage: String?,
)

internal data class RepositoryReleaseAsset(
    val name: String,
    val downloadUrl: String,
)

internal data class RepositoryReleaseManifest(
    val repository: String,
    val tag: String,
    val versionName: String,
    val versionCode: Int,
    val packageName: String,
    val apkAsset: String,
    val apkSha256: String,
    val signingCertificateSha256: String,
    val sourceCommit: String,
)

class RepositoryUpdateManager(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val repository = normalizeGitHubRepository(BuildConfig.SOURCE_REPOSITORY)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow<RepositoryUpdateState>(
        if (repository == null) RepositoryUpdateState.Unsupported else RepositoryUpdateState.Idle,
    )
    val state: StateFlow<RepositoryUpdateState> = _state.asStateFlow()

    suspend fun checkIfDue(now: Long = System.currentTimeMillis()) {
        if (repository == null) return
        val lastAttempt = preferences.getLong(KEY_LAST_ATTEMPT, 0L)
        if (now - lastAttempt < AUTO_CHECK_INTERVAL_MILLIS) return
        check(now)
    }

    suspend fun check(now: Long = System.currentTimeMillis()) {
        val source = repository ?: run {
            _state.value = RepositoryUpdateState.Unsupported
            return
        }
        mutex.withLock {
            _state.value = RepositoryUpdateState.Checking
            preferences.edit { putLong(KEY_LAST_ATTEMPT, now) }
            runCatching {
                withContext(Dispatchers.IO) { fetchLatestRelease(source) }
            }.onSuccess { release ->
                preferences.edit { putLong(KEY_LAST_SUCCESS, now) }
                _state.value = if (isRepositoryVersionNewer(
                        candidateVersion = release.versionName,
                        currentVersion = BuildConfig.VERSION_NAME,
                        candidateVersionCode = release.versionCode,
                        currentVersionCode = BuildConfig.VERSION_CODE,
                    )
                ) {
                    RepositoryUpdateState.Available(release, now)
                } else {
                    RepositoryUpdateState.UpToDate(release.versionName, now)
                }
            }.onFailure { error ->
                _state.value = RepositoryUpdateState.Failed(
                    message = updateFailureMessage(error),
                    checkedAt = now,
                )
            }
        }
    }

    fun shouldPrompt(tagName: String): Boolean =
        preferences.getString(KEY_LAST_PROMPTED_TAG, null) != tagName

    fun markPrompted(tagName: String) {
        preferences.edit { putString(KEY_LAST_PROMPTED_TAG, tagName) }
    }

    private fun fetchLatestRelease(source: String): RepositoryRelease {
        val releaseUrl = "https://api.github.com/repos/$source/releases/latest"
        val root = requestJson(releaseUrl)
        if (root["draft"]?.jsonPrimitive?.booleanOrNull == true) {
            throw IOException("The latest repository release is still a draft.")
        }
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("The repository returned a release without a tag.")
        val pageUrl = root["html_url"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.startsWith("https://github.com/$source/releases/") }
            ?: "https://github.com/$source/releases/tag/$tag"
        val assets = root["assets"]?.jsonArray.orEmpty().mapNotNull { element ->
            val asset = element.jsonObject
            val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            if (!url.startsWith("https://github.com/$source/releases/download/")) return@mapNotNull null
            RepositoryReleaseAsset(name, url)
        }
        val tagVersion = tag.removePrefix("v").removePrefix("V")
        val manifestAsset = assets.firstOrNull {
            it.name == "Arbor-$tagVersion-release.json" || it.name.endsWith("-release.json")
        }
        val manifest = manifestAsset?.let { asset ->
            runCatching { parseManifest(requestJson(asset.downloadUrl)) }.getOrNull()
        }
        val versionName = manifest?.versionName?.ifBlank { tagVersion } ?: tagVersion
        val apk = selectRepositoryReleaseApk(assets, versionName, manifest?.apkAsset)
        val identity = appContext.currentAppInstallIdentity()
        val compatible = manifest?.let { isRepositoryReleaseInstallCompatible(it, identity) } ?: false
        val compatibilityMessage = when {
            manifest == null -> "This release has no signed update manifest; open its release page instead of installing directly."
            manifest.packageName != identity.packageName ->
                "The release package ${manifest.packageName} does not match installed package ${identity.packageName}."
            normalizeCertificateFingerprint(manifest.signingCertificateSha256) !=
                normalizeCertificateFingerprint(identity.signingSha256) ->
                "The release is signed by a different certificate, so Android cannot install it over this build."
            apk == null -> "The release does not contain an Android APK asset."
            else -> null
        }
        return RepositoryRelease(
            repository = source,
            tagName = tag,
            versionName = versionName,
            versionCode = manifest?.versionCode,
            releasePageUrl = pageUrl,
            apkDownloadUrl = apk?.downloadUrl,
            publishedAt = root["published_at"]?.jsonPrimitive?.contentOrNull,
            notes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().take(4_000),
            directInstallCompatible = compatible && apk != null,
            compatibilityMessage = compatibilityMessage,
        )
    }

    private fun requestJson(url: String) = client.newCall(
        Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Arbor/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})")
            .build(),
    ).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            val detail = runCatching {
                json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
            throw IOException(
                when (response.code) {
                    403 -> "GitHub refused the update check, usually because its anonymous rate limit was reached."
                    404 -> "No published release was found in $repository."
                    else -> detail ?: "GitHub update check failed with HTTP ${response.code}."
                },
            )
        }
        json.parseToJsonElement(body).jsonObject
    }

    private fun parseManifest(root: kotlinx.serialization.json.JsonObject): RepositoryReleaseManifest =
        RepositoryReleaseManifest(
            repository = root["repository"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            tag = root["tag"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            versionName = root["versionName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            versionCode = root["versionCode"]?.jsonPrimitive?.intOrNull ?: 0,
            packageName = root["packageName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkAsset = root["apkAsset"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            apkSha256 = root["apkSha256"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            signingCertificateSha256 = root["signingCertificateSha256"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            sourceCommit = root["sourceCommit"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )

    companion object {
        private const val PREFERENCES = "arbor_repository_updates"
        private const val KEY_LAST_ATTEMPT = "last_attempt"
        private const val KEY_LAST_SUCCESS = "last_success"
        private const val KEY_LAST_PROMPTED_TAG = "last_prompted_tag"
        private const val AUTO_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

internal fun normalizeGitHubRepository(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    val direct = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    if (direct.matches(value)) return value.removeSuffix(".git")
    return Regex("github\\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?(?:[/?#].*)?$")
        .find(value)
        ?.groupValues
        ?.getOrNull(1)
        ?.removeSuffix(".git")
}

internal fun selectRepositoryReleaseApk(
    assets: List<RepositoryReleaseAsset>,
    versionName: String,
    manifestAssetName: String? = null,
): RepositoryReleaseAsset? {
    if (!manifestAssetName.isNullOrBlank()) {
        assets.firstOrNull { it.name == manifestAssetName }?.let { return it }
    }
    return assets.firstOrNull { it.name == "Arbor-$versionName-release.apk" }
        ?: assets.firstOrNull { it.name.endsWith("-release.apk", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

internal fun isRepositoryReleaseInstallCompatible(
    manifest: RepositoryReleaseManifest,
    identity: AppInstallIdentity,
): Boolean =
    manifest.packageName == identity.packageName &&
        normalizeCertificateFingerprint(manifest.signingCertificateSha256) ==
        normalizeCertificateFingerprint(identity.signingSha256) &&
        manifest.apkAsset.endsWith(".apk", ignoreCase = true)

internal fun isRepositoryVersionNewer(
    candidateVersion: String,
    currentVersion: String,
    candidateVersionCode: Int? = null,
    currentVersionCode: Int? = null,
): Boolean {
    if (candidateVersionCode != null && currentVersionCode != null && candidateVersionCode != currentVersionCode) {
        return candidateVersionCode > currentVersionCode
    }
    return compareRepositoryVersions(candidateVersion, currentVersion) > 0
}

internal fun compareRepositoryVersions(left: String, right: String): Int {
    val leftParsed = parseRepositoryVersion(left)
    val rightParsed = parseRepositoryVersion(right)
    val count = maxOf(leftParsed.first.size, rightParsed.first.size)
    repeat(count) { index ->
        val result = (leftParsed.first.getOrNull(index) ?: 0)
            .compareTo(rightParsed.first.getOrNull(index) ?: 0)
        if (result != 0) return result
    }
    val leftSuffix = leftParsed.second
    val rightSuffix = rightParsed.second
    return when {
        leftSuffix == rightSuffix -> 0
        leftSuffix == null -> 1
        rightSuffix == null -> -1
        else -> leftSuffix.compareTo(rightSuffix, ignoreCase = true)
    }
}

private fun parseRepositoryVersion(value: String): Pair<List<Int>, String?> {
    val normalized = value.trim().removePrefix("v").removePrefix("V")
    val main = normalized.substringBefore('-').substringBefore('+')
    val suffix = normalized.substringAfter('-', "")
        .substringBefore('+')
        .takeIf(String::isNotBlank)
    val numbers = main.split('.').map { component ->
        component.takeWhile(Char::isDigit).toIntOrNull() ?: 0
    }
    return numbers to suffix
}

private fun updateFailureMessage(error: Throwable): String =
    error.message?.takeIf(String::isNotBlank) ?: "Could not check the source repository for updates."
