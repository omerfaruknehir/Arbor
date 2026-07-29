# Building Arbor

These instructions build Arbor 0.20.5 (`versionCode 131`). The debug variant has application ID `app.arbor.chat.debug` and version name `0.20.5-debug`; its signing setup is unchanged from prior Arbor debug builds.

## Requirements

- Linux, macOS, or Windows with Android Studio support
- JDK 17
- Android SDK platform 36 and Build Tools 36.0.0
- Gradle 8.13 (the wrapper downloads this version)
- Internet access for the first dependency resolution, unless using the supplied populated cache

## Command line

Set `sdk.dir` in `local.properties` or export `ANDROID_HOME`, then run:

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug bundleDebug assembleDebugAndroidTest
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/bundle/debug/app-debug.aab
```

The app packages Python for `arm64-v8a` and `x86_64`. Change `abiFilters` in `app/build.gradle.kts` if another ABI is required.

The same ABI folders contain the PRoot launcher, loader, talloc, and libandroid-shmem. Arbor keeps legacy native-library packaging so Android extracts the APK-embedded launcher components used by its target-SDK 36 runtime path. The packaged talloc shared library is LGPL-3.0-or-later; the retained historical Termux recipe uses an over-broad GPL-3.0 package label that does not override the license headers and `LICENSE` file in the exact talloc source archive. Exact corresponding sources, build recipes, license texts, and hashes are under `third_party/`; see `THIRD_PARTY_NOTICES.md` before replacing any binary.

Debug builds use the intentionally public key documented in [`ci/README.md`](ci/README.md). Its stable signer lets APKs from local builds and GitHub Releases update each other. It is never used for the production package.

## Release signing

The repository intentionally contains no release private key. Configure these environment variables or equivalent Gradle properties, then run `assembleRelease bundleRelease`:

```bash
export ARBOR_KEYSTORE_FILE=/absolute/path/arbor-release.jks
export ARBOR_KEYSTORE_PASSWORD='...'
export ARBOR_KEY_ALIAS='...'
export ARBOR_KEY_PASSWORD='...'
./gradlew assembleRelease bundleRelease
```

Never commit the keystore or passwords. The GitHub Actions release job accepts the same values through protected repository/environment secrets.

## Toolchain archive

Extract `Android-Build-Tools-for-ChatGPT-Arbor-0.9.2-2026-07-16.tar.gz`. Its `env.sh` establishes the bundled JDK, Android SDK, Gradle, and cache paths. From the extracted directory:

```bash
source ./env.sh
cd /path/to/Arbor
gradle --offline --no-daemon testDebugUnitTest lintDebug assembleDebug bundleDebug assembleDebugAndroidTest
```

The archive is a Linux x86_64 environment snapshot. The Android project source remains portable, but the bundled JDK/Gradle executables are platform-specific.

## Verification

Verify an APK with the bundled Android tools:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```
