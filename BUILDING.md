# Building Arbor

## Requirements

- Linux, macOS, or Windows with Android Studio support
- JDK 17
- Android SDK platform 35 and Build Tools 35.0.0
- Gradle 8.13 (the wrapper downloads this version)
- Internet access for the first dependency resolution, unless using the supplied populated cache

## Command line

Set `sdk.dir` in `local.properties` or export `ANDROID_HOME`, then run:

```bash
./gradlew --no-daemon testDebugUnitTest assembleDebug bundleDebug
```

Outputs:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/bundle/debug/app-debug.aab
```

The app packages Python for `arm64-v8a` and `x86_64`. Change `abiFilters` in `app/build.gradle.kts` if another ABI is required.

The same ABI folders contain the PRoot launcher, loader, talloc, and libandroid-shmem. They use legacy native-library packaging so Android extracts executable APK-embedded code, as required for target SDK 35. Exact corresponding sources, build recipes, license texts, and hashes are under `third_party/`; see `THIRD_PARTY_NOTICES.md` before replacing any binary.

## Release signing

The repository intentionally contains no release private key. Configure a Gradle signing config backed by environment variables or a private `keystore.properties`, and never commit the keystore or its passwords. Then run `assembleRelease` and `bundleRelease`.

## Toolchain archive

Extract `Android-Build-Tools-for-ChatGPT-Arbor-0.11.0-2026-07-17.tar.gz`. Its `env.sh` establishes the bundled JDK, Android SDK, Gradle, and cache paths. From the extracted directory:

```bash
source ./env.sh
cd /path/to/Arbor
gradle --offline --no-daemon testDebugUnitTest assembleDebug bundleDebug
```

The archive is a Linux x86_64 environment snapshot. The Android project source remains portable, but the bundled JDK/Gradle executables are platform-specific.

## Verification

Verify an APK with the bundled Android tools:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/debug/app-debug.apk
aapt dump badging app/build/outputs/apk/debug/app-debug.apk
```
