# Xylune 0.20.17

## Launcher icon switching

- Routes every launcher alias through a transparent zero-UI trampoline, so Xylune's real running task is rooted at `MainActivity` rather than an alias being enabled or disabled.
- Keeps `MainActivity` single-task and reuses the existing screen when any launcher icon is tapped.
- Applies alias-state changes atomically on Android 13 and newer, with the enable-first fallback retained on older supported Android versions.
- Keeps `PackageManager.DONT_KILL_APP` on every component-state change.
- Updates setup and Appearance wording to state that Xylune remains open while the launcher refreshes.

## In-app branding

- Replaces static green marks in onboarding and the conversation drawer with one runtime-drawn Xylune mark.
- Derives the mark background, stems, crossbar, and leaf from the active Material color scheme.
- Updates instantly for Xylune, Dynamic, Graphite, Ocean, Violet, Sunset, light/dark mode, and wallpaper-derived Dynamic Color.

## Verification

- Third-party license verification
- Offline license catalog generation
- Release unit tests
- Release lint
- Release APK and AAB builds
- Instrumentation APK build
- APK signature verification
