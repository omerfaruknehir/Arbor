# Arbor 0.20.18

## Fixed

- Changing the palette/icon setting no longer mutates launcher components while an Arbor screen is visible. The selected icon is applied after the app naturally moves to the background and active generation finishes, avoiding Samsung One UI foreground task termination or interrupted work.
- In-app Arbor branding now uses the exact geometry and palette colors from the real launcher artwork.
- The Arbor entry in Licenses & notices now follows the active palette instead of loading the static SVG.
- User messages no longer remain stuck showing a stale short prefix while Markdown parsing or Paging refreshes.

## Validation

- Release unit tests
- Release lint
- Release APK and AAB builds
- Instrumentation APK build
- APK signature verification
- Android emulator smoke test
