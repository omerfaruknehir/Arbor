# Xylune 0.20.15

Xylune 0.20.15 turns first launch into a complete, recoverable setup assistant instead of a provider-only gate.

## Setup and appearance

- Five clear steps: welcome, appearance, provider, local tools, and readiness.
- Fixed navigation and actions remain reachable on small screens.
- Every step has Back and a safe exit path; setup cannot soft-lock the app.
- Theme mode, six color palettes, and AMOLED black preview live during setup.
- Appearance remains editable later in Settings.

## Providers and local tools

- Provider setup is recommended but no longer blocks exploring the app.
- New-chat Python and Linux defaults can be selected during setup.
- The Linux manager now explains download/storage cost, verification stages, retry behavior, rootless limits, and /workspace preservation before installation.

## Popup and keyboard navigation

- System edge gestures are excluded from outside-tap dismissal.
- Outside dismissal waits for pointer release instead of reacting to pointer-down.
- Dialog Back hides the keyboard first and only dismisses on the next Back.
- Material dialogs use explicit actions rather than accidental outside-touch destruction.

## Verification

The one-shot release workflow runs release unit tests, release lint, license checks, APK/AAB assembly, instrumentation APK assembly, and APK signature verification before committing the source update.
