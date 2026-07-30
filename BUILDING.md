# Building Arbor

These instructions build Arbor 0.20.9 (`versionCode 135`). The normal distributable variant is an optimized release build with R8 minification and resource shrinking enabled.

## Requirements

- Linux, macOS, or Windows with Android Studio support
- JDK 17
- Android SDK platform 36 and Build Tools 36.0.0
- Gradle 8.13 (the wrapper downloads this version)
- Internet access for the first dependency resolution, unless using the supplied populated cache

## Command line

Set `sdk.dir` in `local.properties` or export `ANDROID_HOME`, then run:

```bash
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease bundleRelease assembleDebugAndroidTest
```

The normal build runs `:app:generateOfflineLicenseCatalog` automatically.
Run it directly when editing `licenses/`; it validates the local catalog,
referenced icons and documents, and coverage of every app runtime dependency.
The Android instrumentation suite also opens every embedded icon and verifies
that raster files decode and SVG files contain valid SVG markup.

Outputs:

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

The app packages Python for `arm64-v8a` and `x86_64`. Change `abiFilters` in `app/build.gradle.kts` if another ABI is required.

The same ABI folders contain the PRoot launcher, loader, talloc, and libandroid-shmem. Arbor keeps legacy native-library packaging so Android extracts the APK-embedded launcher components used by its target-SDK 36 runtime path. The packaged talloc shared library is LGPL-3.0-or-later; the retained historical Termux recipe uses an over-broad GPL-3.0 package label that does not override the license headers and `LICENSE` file in the exact talloc source archive. Exact corresponding sources, build recipes, license texts, and hashes are under `third_party/`; see `THIRD_PARTY_NOTICES.md` before replacing any binary.

## Public release signing and update compatibility

When no protected release key is configured, the `release` build type uses Arbor's intentionally public reproducible key and package ID `app.arbor.chat.debug`. This keeps the optimized GitHub release APK update-compatible with older GitHub debug APKs and preserves their app data. The build itself is still non-debuggable, minified, and resource-shrunk. Arbor's in-app Developer settings are normal product functionality and remain available.

The public key is documented in [`ci/README.md`](ci/README.md). It is not suitable for store or production distribution.

## Protected production signing

Configure these environment variables or equivalent Gradle properties, then run `assembleRelease bundleRelease`:

```bash
export ARBOR_KEYSTORE_FILE=/absolute/path/arbor-release.jks
export ARBOR_KEYSTORE_PASSWORD='...'
export ARBOR_KEY_ALIAS='...'
export ARBOR_KEY_PASSWORD='...'
./gradlew assembleRelease bundleRelease
```

With all four values present, the build uses the protected release signer and production package ID `app.arbor.chat`. Never commit the keystore or passwords. The manually dispatched protected-signing GitHub Actions job accepts the same values through repository/environment secrets.

## Toolchain archive

Extract `Android-Build-Tools-for-ChatGPT-Arbor-0.9.2-2026-07-16.tar.gz`. Its `env.sh` establishes the bundled JDK, Android SDK, Gradle, and cache paths. From the extracted directory:

```bash
source ./env.sh
cd /path/to/Arbor
gradle --offline --no-daemon testReleaseUnitTest lintRelease assembleRelease bundleRelease assembleDebugAndroidTest
```

The archive is a Linux x86_64 environment snapshot. The Android project source remains portable, but the bundled JDK/Gradle executables are platform-specific.

## Verification

Verify an APK with the bundled Android tools:

```bash
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
aapt dump badging app/build/outputs/apk/release/app-release.apk
```
