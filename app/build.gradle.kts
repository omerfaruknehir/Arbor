import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.artifacts.ExternalModuleDependency
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("com.chaquo.python")
}

fun normalizeGitHubRepository(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return ""
    val direct = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
    if (direct.matches(value)) return value.removeSuffix(".git")
    val match = Regex("""github\.com[:/]([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?)(?:\.git)?(?:[/?#].*)?$""")
        .find(value)
    return match?.groupValues?.getOrNull(1)?.removeSuffix(".git").orEmpty()
}

fun repositoryFromGitConfig(): String {
    val config = rootProject.file(".git/config")
    if (!config.isFile) return ""
    val remoteUrl = Regex("""(?m)^\s*url\s*=\s*(.+?)\s*$""")
        .findAll(config.readText())
        .map { it.groupValues[1] }
        .firstOrNull { it.contains("github.com") }
    return normalizeGitHubRepository(remoteUrl)
}

val sourceRepository = normalizeGitHubRepository(
    providers.gradleProperty("XYLUNE_SOURCE_REPOSITORY").orNull
        ?: System.getenv("XYLUNE_SOURCE_REPOSITORY")
        ?: System.getenv("GITHUB_REPOSITORY")
).ifBlank(::repositoryFromGitConfig)
val sourceCommit = (
    providers.gradleProperty("XYLUNE_SOURCE_COMMIT").orNull
        ?: System.getenv("XYLUNE_SOURCE_COMMIT")
        ?: System.getenv("GITHUB_SHA")
        ?: ""
).trim().take(64)
val microsoftClientId = (
    providers.gradleProperty("XYLUNE_MICROSOFT_CLIENT_ID").orNull
        ?: System.getenv("XYLUNE_MICROSOFT_CLIENT_ID")
        ?: ""
).trim()
val dropboxAppKey = (
    providers.gradleProperty("XYLUNE_DROPBOX_APP_KEY").orNull
        ?: System.getenv("XYLUNE_DROPBOX_APP_KEY")
        ?: ""
).trim()

val releaseStoreFile = providers.gradleProperty("XYLUNE_KEYSTORE_FILE").orNull ?: System.getenv("XYLUNE_KEYSTORE_FILE")
val releaseStorePassword = providers.gradleProperty("XYLUNE_KEYSTORE_PASSWORD").orNull ?: System.getenv("XYLUNE_KEYSTORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("XYLUNE_KEY_ALIAS").orNull ?: System.getenv("XYLUNE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("XYLUNE_KEY_PASSWORD").orNull ?: System.getenv("XYLUNE_KEY_PASSWORD")
val hasProtectedReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val licenseCatalogRoot = rootProject.file("licenses")
val generatedLicenseAssets = layout.buildDirectory.dir("generated/offlineLicenses/assets")

val generateOfflineLicenseCatalog by tasks.registering {
    group = "verification"
    description = "Validates the local licenses catalog and embeds it as deterministic offline assets."
    inputs.dir(licenseCatalogRoot)
    outputs.dir(generatedLicenseAssets)

    doLast {
        val componentsDir = licenseCatalogRoot.resolve("components")
        require(componentsDir.isDirectory) {
            "Missing licenses/components. See licenses/README.md for the catalog format."
        }

        fun requiredString(map: Map<*, *>, field: String, source: File): String =
            (map[field] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: error("${source.path}: '$field' must be a non-empty string")

        fun requiredStringList(map: Map<*, *>, field: String, source: File): List<String> =
            (map[field] as? List<*>)
                ?.map { it as? String ?: error("${source.path}: '$field' must contain only strings") }
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?: error("${source.path}: '$field' must be an array")

        val canonicalRoot = licenseCatalogRoot.canonicalFile
        fun checkedCatalogFile(relativePath: String, source: File): File {
            require(!File(relativePath).isAbsolute && relativePath.split('/').none { it == ".." }) {
                "${source.path}: catalog paths must be relative and may not escape licenses/"
            }
            val resolved = canonicalRoot.resolve(relativePath).canonicalFile
            require(resolved.toPath().startsWith(canonicalRoot.toPath()) && resolved.isFile) {
                "${source.path}: local catalog file '$relativePath' does not exist"
            }
            return resolved
        }

        val supportedIconExtensions = setOf("svg", "png", "webp", "jpg", "jpeg")
        val ids = mutableSetOf<String>()
        val coveredModules = mutableSetOf<String>()
        val referencedFiles = linkedSetOf<String>()
        val referencedIcons = linkedSetOf<String>()
        val normalizedComponents = componentsDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            .orEmpty()
            .sortedBy { it.name }
            .map { source ->
                val parsed = JsonSlurper().parse(source) as? Map<*, *>
                    ?: error("${source.path}: component metadata must be a JSON object")
                val id = requiredString(parsed, "id", source)
                require(ids.add(id)) { "${source.path}: duplicate component id '$id'" }
                val icon = requiredString(parsed, "icon", source)
                require(icon.startsWith("icons/")) {
                    "${source.path}: 'icon' must point inside licenses/icons/"
                }
                val iconFile = checkedCatalogFile(icon, source)
                require(iconFile.length() > 0L) {
                    "${source.path}: local icon '$icon' is empty"
                }
                require(iconFile.extension.lowercase() in supportedIconExtensions) {
                    "${source.path}: unsupported icon format '.${iconFile.extension}'"
                }
                referencedIcons += icon
                referencedFiles += icon

                val coordinates = requiredStringList(parsed, "coordinates", source)
                coordinates.forEach { coordinate ->
                    val parts = coordinate.split(':')
                    require(parts.size >= 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        "${source.path}: invalid Maven coordinate '$coordinate'"
                    }
                    coveredModules += "${parts[0]}:${parts[1]}"
                }

                val licenses = (parsed["licenses"] as? List<*>)
                    ?.mapIndexed { index, value ->
                        val license = value as? Map<*, *>
                            ?: error("${source.path}: licenses[$index] must be a JSON object")
                        val file = requiredString(license, "file", source)
                        checkedCatalogFile(file, source)
                        referencedFiles += file
                        linkedMapOf(
                            "name" to requiredString(license, "name", source),
                            "spdx" to (license["spdx"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            "file" to file,
                        ).filterValues { it != null }
                    }
                    ?.takeIf { it.isNotEmpty() }
                    ?: error("${source.path}: 'licenses' must contain at least one license document")

                linkedMapOf(
                    "id" to id,
                    "name" to requiredString(parsed, "name", source),
                    "version" to requiredString(parsed, "version", source),
                    "category" to requiredString(parsed, "category", source),
                    "description" to requiredString(parsed, "description", source),
                    "projectUrl" to requiredString(parsed, "projectUrl", source),
                    "icon" to icon,
                    "coordinates" to coordinates,
                    "licenses" to licenses,
                    "notice" to (parsed["notice"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                ).filterValues { it != null }
            }
            .sortedBy { it["name"].toString().lowercase() }

        require(normalizedComponents.isNotEmpty()) {
            "licenses/components must contain at least one component JSON file"
        }

        val declaredModules = configurations.getByName("implementation").dependencies
            .filterIsInstance<ExternalModuleDependency>()
            .mapNotNull { dependency ->
                dependency.group?.let { "$it:${dependency.name}" }
            }
            .toSortedSet()
        val undocumentedModules = declaredModules - coveredModules
        require(undocumentedModules.isEmpty()) {
            "Missing offline license metadata for runtime dependencies: ${undocumentedModules.joinToString()}"
        }

        val outputRoot = generatedLicenseAssets.get().asFile
        outputRoot.deleteRecursively()
        val outputLicenses = outputRoot.resolve("licenses").apply { mkdirs() }
        referencedFiles.forEach { relativePath ->
            val source = checkedCatalogFile(relativePath, canonicalRoot)
            val destination = outputLicenses.resolve(relativePath)
            destination.parentFile.mkdirs()
            source.copyTo(destination, overwrite = true)
            require(destination.isFile && source.readBytes().contentEquals(destination.readBytes())) {
                "Failed to embed offline license asset '$relativePath'"
            }
        }
        require(referencedIcons.all { outputLicenses.resolve(it).isFile }) {
            "One or more offline license icons were not embedded into the generated APK assets"
        }

        val catalog = linkedMapOf(
            "schemaVersion" to 1,
            "components" to normalizedComponents,
        )
        outputLicenses.resolve("catalog.json").writeText(
            JsonOutput.prettyPrint(JsonOutput.toJson(catalog)) + "\n",
        )
        logger.lifecycle(
            "Embedded ${normalizedComponents.size} offline license records and ${referencedIcons.size} local icons.",
        )
    }
}

android {
    namespace = "app.xylune.chat"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.xylune.chat"
        minSdk = 26
        targetSdk = 36
        versionCode = 209
        versionName = "0.24.20"
        buildConfigField("String", "SOURCE_REPOSITORY", "\"$sourceRepository\"")
        buildConfigField("String", "SOURCE_COMMIT", "\"$sourceCommit\"")
        buildConfigField("String", "MICROSOFT_CLIENT_ID", "\"$microsoftClientId\"")
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
        manifestPlaceholders["dropboxOAuthScheme"] =
            if (dropboxAppKey.isBlank()) "db-xylune-unconfigured" else "db-$dropboxAppKey"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        getByName("debug") {
            // Public test key: reproducible public builds and in-place upgrades.
            storeFile = rootProject.file("ci/xylune-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasProtectedReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasProtectedReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                // Public GitHub releases use Xylune's canonical package while retaining
                // the repository's reproducible public signing certificate.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*"
        )
    }
    sourceSets.getByName("main").assets.srcDir(generatedLicenseAssets)
}

chaquopy {
    defaultConfig {
        version = "3.12"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("androidx.room:room-paging:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.paging:paging-runtime-ktx:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")
    implementation("net.zetetic:sqlcipher-android:4.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    implementation("io.noties.markwon:ext-tasklist:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

tasks.named("preBuild").configure {
    dependsOn(generateOfflineLicenseCatalog)
}