# Xylune 0.20.16

## Theme-matched launcher icons

- Add an opt-in **Match launcher icon to palette** switch in Appearance and first-run setup.
- Provide polished adaptive launcher icons for Xylune, Dynamic, Graphite, Ocean, Violet, and Sunset while preserving Xylune's recognizable mark.
- Change aliases without killing the app, enabling the next icon before disabling the previous one so launchers never see an iconless state.
- Keep the classic Xylune green icon whenever matching is disabled.
- Reconcile the selected alias after app updates and explain launcher refresh delay and Android themed-icon overrides.

## Accurate palette previews

- Render every palette preview from its own real Material color scheme rather than borrowing colors from the currently active scheme.
- Make Dynamic preview use the device's actual wallpaper-derived palette on Android 12+, independent of the selected Xylune palette.
- Replace single-color dots with compact three-color swatches in setup and Appearance.

## Verification

- Added launcher-alias mapping and manifest regression tests.
- Release unit tests, lint, APK, AAB, instrumentation APK, and APK signature verification are required before publication.
