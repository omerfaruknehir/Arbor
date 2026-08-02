#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text()


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content)


def replace_once(path: str, old: str, new: str) -> None:
    content = read(path)
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}: {old[:180]!r}")
    write(path, content.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Build provenance and version.
# ---------------------------------------------------------------------------
build = "app/build.gradle.kts"
replace_once(
    build,
    "val releaseStoreFile = providers.gradleProperty(\"ARBOR_KEYSTORE_FILE\").orNull ?: System.getenv(\"ARBOR_KEYSTORE_FILE\")\n",
    '''fun normalizeGitHubRepository(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return ""
    val direct = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    if (direct.matches(value)) return value.removeSuffix(".git")
    val match = Regex("github\\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\\.git)?(?:[/?#].*)?$")
        .find(value)
    return match?.groupValues?.getOrNull(1)?.removeSuffix(".git").orEmpty()
}

fun repositoryFromGitConfig(): String {
    val config = rootProject.file(".git/config")
    if (!config.isFile) return ""
    val remoteUrl = Regex("(?m)^\\s*url\\s*=\\s*(.+?)\\s*$")
        .findAll(config.readText())
        .map { it.groupValues[1] }
        .firstOrNull { it.contains("github.com") }
    return normalizeGitHubRepository(remoteUrl)
}

val sourceRepository = normalizeGitHubRepository(
    providers.gradleProperty("ARBOR_SOURCE_REPOSITORY").orNull
        ?: System.getenv("ARBOR_SOURCE_REPOSITORY")
        ?: System.getenv("GITHUB_REPOSITORY")
).ifBlank(::repositoryFromGitConfig)
val sourceCommit = (
    providers.gradleProperty("ARBOR_SOURCE_COMMIT").orNull
        ?: System.getenv("ARBOR_SOURCE_COMMIT")
        ?: System.getenv("GITHUB_SHA")
        ?: ""
).trim().take(64)

val releaseStoreFile = providers.gradleProperty("ARBOR_KEYSTORE_FILE").orNull ?: System.getenv("ARBOR_KEYSTORE_FILE")
''',
)
replace_once(build, "versionCode = 167", "versionCode = 168")
replace_once(build, 'versionName = "0.22.3"', 'versionName = "0.22.4"')
replace_once(
    build,
    '''        versionName = "0.22.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
''',
    '''        versionName = "0.22.4"
        buildConfigField("String", "SOURCE_REPOSITORY", "\\\"$sourceRepository\\\"")
        buildConfigField("String", "SOURCE_COMMIT", "\\\"$sourceCommit\\\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
''',
)

# ---------------------------------------------------------------------------
# Shared installed-app identity, used by OAuth diagnostics and update safety.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/arbor/chat/security/AppInstallIdentity.kt",
    r'''package app.arbor.chat.security

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

data class AppInstallIdentity(
    val packageName: String,
    val signingSha1: String,
    val signingSha256: String,
)

fun Context.currentAppInstallIdentity(): AppInstallIdentity {
    val signatures = currentSigningCertificates()
    val certificate = signatures.firstOrNull()?.toByteArray().orEmpty()
    return AppInstallIdentity(
        packageName = packageName,
        signingSha1 = certificate.fingerprint("SHA-1"),
        signingSha256 = certificate.fingerprint("SHA-256"),
    )
}

@Suppress("DEPRECATION")
private fun Context.currentSigningCertificates(): Array<Signature> {
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

internal fun ByteArray.fingerprint(algorithm: String): String {
    if (isEmpty()) return "Unavailable"
    return MessageDigest.getInstance(algorithm)
        .digest(this)
        .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }
}

internal fun normalizeCertificateFingerprint(value: String): String =
    value.filter(Char::isLetterOrDigit).uppercase()
''',
)

# ---------------------------------------------------------------------------
# Google Drive OAuth registration diagnostics.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/arbor/chat/transfer/GoogleDriveAuthorizationFailure.kt",
    r'''package app.arbor.chat.transfer

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
''',
)

# ---------------------------------------------------------------------------
# Repository-aware update service.
# ---------------------------------------------------------------------------
write(
    "app/src/main/java/app/arbor/chat/update/RepositoryUpdateManager.kt",
    r'''package app.arbor.chat.update

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
''',
)

# ---------------------------------------------------------------------------
# App graph and ViewModel integration.
# ---------------------------------------------------------------------------
app_container = "app/src/main/java/app/arbor/chat/ArborApplication.kt"
replace_once(
    app_container,
    "import app.arbor.chat.transfer.ScopedCloudFolderStore\n",
    "import app.arbor.chat.transfer.ScopedCloudFolderStore\nimport app.arbor.chat.update.RepositoryUpdateManager\n",
)
replace_once(
    app_container,
    "    val googleDriveAppData = GoogleDriveAppDataClient(application)\n",
    "    val googleDriveAppData = GoogleDriveAppDataClient(application)\n    val repositoryUpdates = RepositoryUpdateManager(application)\n",
)

view_model = "app/src/main/java/app/arbor/chat/ui/ChatViewModel.kt"
replace_once(
    view_model,
    "import app.arbor.chat.transfer.IncomingArchiveState\n",
    "import app.arbor.chat.transfer.IncomingArchiveState\nimport app.arbor.chat.update.RepositoryUpdateState\n",
)
replace_once(
    view_model,
    "    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)\n",
    "    val notices = MutableSharedFlow<String>(extraBufferCapacity = 8)\n    val repositoryUpdateState: StateFlow<RepositoryUpdateState> = container.repositoryUpdates.state\n",
)
replace_once(
    view_model,
    '''        viewModelScope.launch {
            merge(
''',
    '''        viewModelScope.launch {
            container.repositoryUpdates.checkIfDue()
        }
        viewModelScope.launch {
            merge(
''',
)
replace_once(
    view_model,
    '''    fun postNotice(message: String) {
        notices.tryEmit(message)
    }

''',
    '''    fun postNotice(message: String) {
        notices.tryEmit(message)
    }

    fun checkForUpdates() {
        viewModelScope.launch { container.repositoryUpdates.check() }
    }

    fun shouldPromptRepositoryUpdate(tagName: String): Boolean =
        container.repositoryUpdates.shouldPrompt(tagName)

    fun markRepositoryUpdatePrompted(tagName: String) =
        container.repositoryUpdates.markPrompted(tagName)

''',
)

# ---------------------------------------------------------------------------
# Automatic update prompt and duplicate-notice cleanup.
# ---------------------------------------------------------------------------
arbor_app = "app/src/main/java/app/arbor/chat/ui/ArborApp.kt"
replace_once(
    arbor_app,
    "import android.app.Activity\n",
    "import android.app.Activity\nimport android.content.Intent\nimport android.net.Uri\n",
)
replace_once(
    arbor_app,
    "import app.arbor.chat.settings.PerformanceOverlayPosition\n",
    "import app.arbor.chat.settings.PerformanceOverlayPosition\nimport app.arbor.chat.update.RepositoryUpdateState\n",
)
replace_once(
    arbor_app,
    "    val incomingArchive by viewModel.incomingArchive.collectAsState()\n",
    "    val incomingArchive by viewModel.incomingArchive.collectAsState()\n    val repositoryUpdateState by viewModel.repositoryUpdateState.collectAsState()\n",
)
# Remove the second duplicate notice collector and replace it with update prompting.
replace_once(
    arbor_app,
    '''    LaunchedEffect(viewModel) {
        viewModel.notices.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(pythonRun?.startedAt, pythonRun?.running, linuxRun?.startedAt, linuxRun?.running) {
''',
    '''    LaunchedEffect(repositoryUpdateState) {
        val available = repositoryUpdateState as? RepositoryUpdateState.Available
            ?: return@LaunchedEffect
        val release = available.release
        if (!viewModel.shouldPromptRepositoryUpdate(release.tagName)) return@LaunchedEffect
        val target = if (release.directInstallCompatible && release.apkDownloadUrl != null) {
            release.apkDownloadUrl
        } else {
            release.releasePageUrl
        }
        val result = snackbar.showSnackbar(
            message = "Arbor ${release.versionName} is available from ${release.repository}",
            actionLabel = if (release.directInstallCompatible) "Download" else "Open release",
            duration = SnackbarDuration.Long,
        )
        viewModel.markRepositoryUpdatePrompted(release.tagName)
        if (result == SnackbarResult.ActionPerformed) {
            runCatching {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
            }.onFailure { viewModel.postNotice("Could not open the update link") }
        }
    }
    LaunchedEffect(pythonRun?.startedAt, pythonRun?.running, linuxRun?.startedAt, linuxRun?.running) {
''',
)

# ---------------------------------------------------------------------------
# About page: dynamic source repository and update controls.
# ---------------------------------------------------------------------------
settings = "app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt"
replace_once(
    settings,
    "import app.arbor.chat.ui.theme.palettePreviewColors\n",
    "import app.arbor.chat.ui.theme.palettePreviewColors\nimport app.arbor.chat.update.RepositoryUpdateState\n",
)
replace_once(
    settings,
    '''                        SettingsRoute.ABOUT -> AboutSettingsPage(
                            developerEnabled = developerSettings.enabled,
''',
    '''                        SettingsRoute.ABOUT -> AboutSettingsPage(
                            viewModel = viewModel,
                            developerEnabled = developerSettings.enabled,
''',
)
replace_once(
    settings,
    '''private fun AboutSettingsPage(
    developerEnabled: Boolean,
''',
    '''private fun AboutSettingsPage(
    viewModel: ChatViewModel,
    developerEnabled: Boolean,
''',
)
replace_once(
    settings,
    '''    val applicationInfo = LocalContext.current.applicationInfo
    val uriHandler = LocalUriHandler.current
    SectionTitle("$appName ${BuildConfig.VERSION_NAME}", "Native Android BYOK model workspace.")
''',
    '''    val applicationInfo = LocalContext.current.applicationInfo
    val uriHandler = LocalUriHandler.current
    val updateState by viewModel.repositoryUpdateState.collectAsState()
    val sourceRepository = BuildConfig.SOURCE_REPOSITORY.takeIf(String::isNotBlank)
    val sourceUrl = sourceRepository?.let { "https://github.com/$it" }
    SectionTitle("$appName ${BuildConfig.VERSION_NAME}", "Native Android BYOK model workspace.")
''',
)
replace_once(
    settings,
    '''        SettingsDestination(
            icon = Icons.Outlined.Code,
            title = "Source code",
            subtitle = "github.com/omerfaruknehir/Arbor",
            onClick = { uriHandler.openUri("https://github.com/omerfaruknehir/Arbor") },
        )
''',
    '''        SettingsDestination(
            icon = Icons.Outlined.Code,
            title = "Build source",
            subtitle = sourceRepository ?: "No GitHub source was embedded in this build",
            onClick = {
                if (sourceUrl != null) uriHandler.openUri(sourceUrl)
                else viewModel.postNotice("This build has no GitHub source provenance")
            },
        )
''',
)
replace_once(
    settings,
    '''    SettingsGroup("Build information") {
        AboutInfoRow("Version", BuildConfig.VERSION_NAME)
''',
    '''    SettingsGroup("Updates") {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (val state = updateState) {
                RepositoryUpdateState.Unsupported -> {
                    Text("Automatic checks are disabled because this build has no embedded GitHub repository origin.")
                    Text(
                        "GitHub release workflows embed their own owner/repository. Fork builds therefore follow the fork they came from.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RepositoryUpdateState.Idle -> {
                    Text("Updates are checked against ${sourceRepository ?: "the build repository"}.")
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(" Check for updates")
                    }
                }
                RepositoryUpdateState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Checking ${sourceRepository ?: "the source repository"}…")
                    }
                }
                is RepositoryUpdateState.UpToDate -> {
                    Text("Arbor is up to date", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Latest release: ${state.latestVersion} · checked ${DateFormat.getDateTimeInstance().format(Date(state.checkedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(" Check again")
                    }
                }
                is RepositoryUpdateState.Available -> {
                    val release = state.release
                    Text("Arbor ${release.versionName} is available", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Source: ${release.repository}" + (release.publishedAt?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    release.compatibilityMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = {
                            val target = if (release.directInstallCompatible && release.apkDownloadUrl != null) {
                                release.apkDownloadUrl
                            } else release.releasePageUrl
                            uriHandler.openUri(target)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Cloud, null)
                        Text(if (release.directInstallCompatible) " Download update" else " Open release page")
                    }
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(" Check again")
                    }
                }
                is RepositoryUpdateState.Failed -> {
                    Text("Update check failed", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text(state.message, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = viewModel::checkForUpdates, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Refresh, null)
                        Text(" Retry")
                    }
                }
            }
        }
    }

    SettingsGroup("Build information") {
        AboutInfoRow("Version", BuildConfig.VERSION_NAME)
''',
)
replace_once(
    settings,
    '''        AboutInfoRow("Package", BuildConfig.APPLICATION_ID)
''',
    '''        AboutInfoRow("Package", BuildConfig.APPLICATION_ID)
        AboutInfoRow("Source repository", sourceRepository ?: "Not embedded")
        if (BuildConfig.SOURCE_COMMIT.isNotBlank()) AboutInfoRow("Source commit", BuildConfig.SOURCE_COMMIT.take(12))
''',
)

# ---------------------------------------------------------------------------
# Drive UI diagnostics.
# ---------------------------------------------------------------------------
cloud = "app/src/main/java/app/arbor/chat/ui/CloudBackupUi.kt"
replace_once(
    cloud,
    "import android.accounts.Account\n",
    "import android.accounts.Account\nimport android.content.ClipData\nimport android.content.ClipboardManager\n",
)
replace_once(
    cloud,
    "import androidx.compose.ui.platform.LocalContext\n",
    "import androidx.compose.ui.platform.LocalContext\nimport androidx.compose.ui.platform.LocalUriHandler\n",
)
replace_once(
    cloud,
    "import app.arbor.chat.transfer.ArchiveOptions\n",
    "import app.arbor.chat.BuildConfig\nimport app.arbor.chat.transfer.ArchiveOptions\nimport app.arbor.chat.transfer.GoogleDriveAuthorizationFailure\nimport app.arbor.chat.transfer.GoogleDriveAuthorizationFailureKind\nimport app.arbor.chat.transfer.describeGoogleDriveAuthorizationFailure\n",
)
replace_once(
    cloud,
    '''    val context = LocalContext.current
    val scope = rememberCoroutineScope()
''',
    '''    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
''',
)
replace_once(
    cloud,
    '''    var driveError by remember { mutableStateOf<String?>(null) }

    fun refreshFolderBackups() {
''',
    '''    var driveError by remember { mutableStateOf<String?>(null) }
    var driveAuthorizationFailure by remember { mutableStateOf<GoogleDriveAuthorizationFailure?>(null) }

    fun recordAuthorizationFailure(error: Throwable) {
        val failure = context.describeGoogleDriveAuthorizationFailure(
            error = error,
            sourceRepository = BuildConfig.SOURCE_REPOSITORY,
        )
        driveAuthorizationFailure = failure
        driveError = failure.userMessage
    }

    fun refreshFolderBackups() {
''',
)
replace_once(
    cloud,
    '''            driveError = null
            runCatching {
''',
    '''            driveError = null
            driveAuthorizationFailure = null
            runCatching {
''',
)
replace_once(
    cloud,
    '''    fun acceptAuthorization(action: GoogleBackupAction, authorization: AuthorizationResult) {
        val token = authorization.accessToken
''',
    '''    fun acceptAuthorization(action: GoogleBackupAction, authorization: AuthorizationResult) {
        driveAuthorizationFailure = null
        val token = authorization.accessToken
''',
)
replace_once(
    cloud,
    '''                    .onFailure { error ->
                        driveError = error.message ?: "Google Drive authorization failed"
                    }
''',
    '''                    .onFailure(::recordAuthorizationFailure)
''',
)
replace_once(
    cloud,
    '''        driveError = null
        pendingGoogleAction = action
''',
    '''        driveError = null
        driveAuthorizationFailure = null
        pendingGoogleAction = action
''',
)
replace_once(
    cloud,
    '''            .addOnFailureListener {
                pendingGoogleAction = null
                googleConnected = false
                driveError = it.message ?: "Google Drive authorization failed"
            }
''',
    '''            .addOnFailureListener { error ->
                pendingGoogleAction = null
                googleConnected = false
                recordAuthorizationFailure(error)
            }
''',
)
replace_once(
    cloud,
    '''                driveError = null
                busy = null
''',
    '''                driveError = null
                driveAuthorizationFailure = null
                busy = null
''',
)
replace_once(
    cloud,
    '''            driveError?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Google Drive error", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        OutlinedButton(onClick = { authorizeGoogle(GoogleBackupAction.Connect, selectAccount = true) }) {
                            Text("Reconnect")
                        }
                    }
                }
            }
''',
    '''            driveError?.let { message ->
                val failure = driveAuthorizationFailure
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            failure?.title ?: "Google Drive error",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        if (failure?.kind == GoogleDriveAuthorizationFailureKind.UNREGISTERED_ON_API_CONSOLE) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = .55f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Package", style = MaterialTheme.typography.labelSmall)
                                    Text(failure.identity.packageName, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                    Text("Signing SHA-1", style = MaterialTheme.typography.labelSmall)
                                    Text(failure.identity.signingSha1, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
                                        ClipData.newPlainText("Arbor Google Drive OAuth setup", failure.copyableSetupDetails()),
                                    )
                                    viewModel.postNotice("Google Drive registration details copied")
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Copy setup details") }
                            failure.setupGuideUrl?.let { guide ->
                                OutlinedButton(
                                    onClick = { uriHandler.openUri(guide) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Open setup guide") }
                            }
                        }
                        OutlinedButton(
                            onClick = { authorizeGoogle(GoogleBackupAction.Connect, selectAccount = true) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Reconnect") }
                    }
                }
            }
''',
)
# Cloud UI now uses FontFamily.
replace_once(
    cloud,
    "import androidx.compose.ui.text.font.FontWeight\n",
    "import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight\n",
)

# ---------------------------------------------------------------------------
# Release workflow provenance and signed update manifest.
# ---------------------------------------------------------------------------
release = ".github/workflows/release.yml"
replace_once(
    release,
    '''      ARBOR_VERSION: ${{ needs.detect_version.outputs.version }}
      ARBOR_TAG: ${{ needs.detect_version.outputs.tag }}
''',
    '''      ARBOR_VERSION: ${{ needs.detect_version.outputs.version }}
      ARBOR_TAG: ${{ needs.detect_version.outputs.tag }}
      ARBOR_SOURCE_REPOSITORY: ${{ github.repository }}
      ARBOR_SOURCE_COMMIT: ${{ github.sha }}
''',
)
replace_once(
    release,
    '''          cp app/build/outputs/apk/release/app-release.apk "release/Arbor-${ARBOR_VERSION}-release.apk"
          cp app/build/outputs/bundle/release/app-release.aab "release/Arbor-${ARBOR_VERSION}-release.aab"
          git archive --format=zip --prefix="Arbor-${ARBOR_VERSION}/" \\
''',
    '''          apk_asset="Arbor-${ARBOR_VERSION}-release.apk"
          cp app/build/outputs/apk/release/app-release.apk "release/$apk_asset"
          cp app/build/outputs/bundle/release/app-release.aab "release/Arbor-${ARBOR_VERSION}-release.aab"
          apk_sha256="$(sha256sum "release/$apk_asset" | awk '{print $1}')"
          cert_sha256="$(\
            "$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --print-certs "release/$apk_asset" \
              | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' \
              | head -n 1\
          )"
          package_name="$(\
            "$ANDROID_HOME/build-tools/36.0.0/aapt" dump badging "release/$apk_asset" \
              | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" \
              | head -n 1\
          )"
          version_code="$(sed -n 's/.*versionCode = \\([0-9][0-9]*\\).*/\\1/p' app/build.gradle.kts | head -n 1)"
          export apk_asset apk_sha256 cert_sha256 package_name version_code
          python3 - <<'PY'
          import json
          import os
          from pathlib import Path

          version = os.environ["ARBOR_VERSION"]
          manifest = {
              "schemaVersion": 1,
              "repository": os.environ["GITHUB_REPOSITORY"],
              "tag": os.environ["ARBOR_TAG"],
              "versionName": version,
              "versionCode": int(os.environ["version_code"]),
              "packageName": os.environ["package_name"],
              "apkAsset": os.environ["apk_asset"],
              "apkSha256": os.environ["apk_sha256"],
              "signingCertificateSha256": os.environ["cert_sha256"],
              "sourceCommit": os.environ["GITHUB_SHA"],
          }
          Path(f"release/Arbor-{version}-release.json").write_text(
              json.dumps(manifest, indent=2, sort_keys=True) + "\\n"
          )
          PY
          git archive --format=zip --prefix="Arbor-${ARBOR_VERSION}/" \\
''',
)

# ---------------------------------------------------------------------------
# Exact setup documentation, including the public GitHub release signer SHA-1.
# ---------------------------------------------------------------------------
keytool_output = subprocess.check_output(
    [
        "keytool", "-list", "-v",
        "-keystore", str(ROOT / "ci/arbor-debug.keystore"),
        "-alias", "androiddebugkey",
        "-storepass", "android",
        "-keypass", "android",
    ],
    text=True,
    stderr=subprocess.STDOUT,
)
sha1_match = re.search(r"SHA1:\s*([0-9A-F:]+)", keytool_output, re.IGNORECASE)
if not sha1_match:
    raise RuntimeError("Could not determine public Arbor signing SHA-1")
public_sha1 = sha1_match.group(1).upper()
write(
    "docs/GOOGLE_DRIVE_SETUP.md",
    f'''# Google Drive app-data authorization

Arbor's direct Google Drive target uses only `https://www.googleapis.com/auth/drive.appdata` and stores backups in Drive's hidden `appDataFolder`. Google requires every Android build requesting this token to be registered by **package name and signing SHA-1**.

No OAuth client secret belongs in the APK or repository.

## Google Cloud configuration

1. Create or select a Google Cloud project.
2. Enable **Google Drive API** for that project.
3. Configure the OAuth consent screen and add the accounts/test users permitted by the project's publishing state.
4. In **APIs & Services → Credentials**, create an **OAuth client ID** of type **Android**.
5. Enter the package and signing SHA-1 for the build being distributed.
6. Reopen Arbor and select **Backup & transfer → Connect Google Drive**.

## Public GitHub release identity

When protected production-signing secrets are not supplied, Arbor's GitHub release workflow deliberately preserves update compatibility with its established public signer:

- Package: `app.arbor.chat.debug`
- Signing SHA-1: `{public_sha1}`

Register that exact pair for APKs published by the repository's normal public release workflow.

## Protected production builds

A protected release uses package `app.arbor.chat` and the private release certificate supplied through `ARBOR_KEYSTORE_*`. Create a separate Android OAuth client using that private certificate's SHA-1. Do not reuse the public debug fingerprint.

## Forks and locally signed builds

Each distinct package/signing-certificate pair needs its own Android OAuth client. Arbor 0.22.4 and later displays the current package, SHA-1, and SHA-256 directly in the error card and provides a copy button, so the values do not need to be guessed.

## Why `UNREGISTERED_ON_API_CONSOLE` appears

Account selection can succeed before Google validates the requesting Android OAuth identity. If the package/SHA-1 pair is absent, belongs to another Cloud project, or the Drive API is not enabled, Google Play services returns `UNREGISTERED_ON_API_CONSOLE` instead of an access token.
''',
)

# ---------------------------------------------------------------------------
# Tests.
# ---------------------------------------------------------------------------
write(
    "app/src/test/java/app/arbor/chat/update/RepositoryUpdateManagerTest.kt",
    r'''package app.arbor.chat.update

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
''',
)
write(
    "app/src/test/java/app/arbor/chat/transfer/GoogleDriveAuthorizationFailureTest.kt",
    r'''package app.arbor.chat.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveAuthorizationFailureTest {
    @Test
    fun unregisteredConsoleStatusIsRecognized() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.UNREGISTERED_ON_API_CONSOLE,
            classifyGoogleDriveAuthorizationFailure(
                "8: [8] Unknown error [status=UNREGISTERED_ON_API_CONSOLE].",
            ),
        )
    }

    @Test
    fun cancellationAndOrdinaryErrorsRemainDistinct() {
        assertEquals(
            GoogleDriveAuthorizationFailureKind.CANCELED,
            classifyGoogleDriveAuthorizationFailure("Connection cancelled"),
        )
        assertEquals(
            GoogleDriveAuthorizationFailureKind.OTHER,
            classifyGoogleDriveAuthorizationFailure("Network unavailable"),
        )
    }
}
''',
)
write(
    "app/src/test/java/app/arbor/chat/ui/RepositoryUpdateIntegrationTest.kt",
    r'''package app.arbor.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryUpdateIntegrationTest {
    @Test
    fun releaseWorkflowEmbedsRepositoryAndSignedManifest() {
        val workflow = java.io.File("../.github/workflows/release.yml").readText()
        assertTrue(workflow.contains("ARBOR_SOURCE_REPOSITORY: ${{ github.repository }}"))
        assertTrue(workflow.contains("signingCertificateSha256"))
        assertTrue(workflow.contains("release.json"))
    }

    @Test
    fun aboutPageUsesEmbeddedBuildSource() {
        val settings = java.io.File("src/main/java/app/arbor/chat/ui/SettingsScreen.kt").readText()
        assertTrue(settings.contains("BuildConfig.SOURCE_REPOSITORY"))
        assertTrue(settings.contains("Check for updates"))
    }
}
''',
)

# Release notes.
write(
    "docs/releases/RELEASE_NOTES_0.22.4.md",
    '''# Arbor 0.22.4

- Replace the raw Google Drive `UNREGISTERED_ON_API_CONSOLE` message with an actionable OAuth-registration diagnostic.
- Show and copy the installed package name, signing SHA-1, signing SHA-256, required `drive.appdata` scope, and setup guide.
- Document the exact public GitHub release package/signing identity while keeping client secrets out of the app and repository.
- Embed the GitHub source repository and source commit into builds.
- Check that embedded repository's latest GitHub Release automatically once per day and manually from About Arbor.
- Make fork releases follow their own fork while rehosted APKs continue trusting the repository they were built from.
- Publish a signed release manifest containing package, version code, APK checksum, signing-certificate digest, and source commit.
- Offer direct APK downloads only when package and signing certificate match the installed build; otherwise open the release page safely.
''',
)

print(f"Applied Arbor 0.22.4 patch; public Google OAuth SHA-1 is {public_sha1}")
