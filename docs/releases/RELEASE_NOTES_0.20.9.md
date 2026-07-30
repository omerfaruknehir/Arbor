# Arbor 0.20.9

This release fixes raster license icons and changes the public distributable builds from debug variants to optimized release variants.

## Raster license icons

- Stop sending embedded PNG, JPEG, and WebP license artwork through Coil's `file:///android_asset` fetch path.
- Read each embedded icon directly from `AssetManager`.
- Decode raster artwork with Android's `BitmapFactory` and render the resulting bitmap directly in Compose.
- Continue decoding SVG artwork through Coil's explicit `SvgDecoder` from the same embedded bytes.
- Retain the first-letter tile only for genuinely missing or undecodable assets.
- Add an Android instrumentation test that opens every catalog icon and verifies that all raster files decode successfully.

This repairs the Chaquopy, OkHttp, and SQLCipher icons, which previously fell back to their first letters despite being present in the APK.

## Optimized release builds

- GitHub Releases now publish R8-minified, resource-shrunk release APK and AAB artifacts instead of debug APK/AAB artifacts.
- CI verifies `testReleaseUnitTest`, `lintRelease`, `assembleRelease`, and `bundleRelease` and validates the produced APK signature.
- The public release build remains signed with Arbor's reproducible public update key and keeps package ID `app.arbor.chat.debug`, preserving in-place updates and existing app data from earlier GitHub debug builds.
- The Android package is non-debuggable. Arbor's in-app Developer settings remain available because they are product settings rather than Android debugger functionality.
- Protected signing variables still produce the production package ID `app.arbor.chat` with the protected production key.

Room schema, migrations, chats, credentials, OAuth sessions, workspaces, attachments, Python environments, and Linux environments are unchanged.
