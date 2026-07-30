# Arbor 0.20.14

## Setup and onboarding

- Fixed the dark-theme welcome title and other inherited content colors.
- Added live System, Light, and Dark theme selection during setup.
- Reworked setup into three clear steps with progress, predictable Back behavior, and fixed bottom actions.
- Added a reachable skip/continue path at every stage. Skipping is session-only when no provider exists, so setup is never permanently lost.
- Replaced the unbounded provider-catalog startup spinner with an eight-second grace period and a recoverable delayed-catalog state.
- Improved small-screen behavior by scrolling only the page body while keeping primary actions visible.

## Compatibility

- Existing chats, providers, credentials, settings, workspaces, package ID, Room schema, and signing/update compatibility are unchanged.
- Developer settings and the performance overlay remain available in the optimized release build.

## Assets

- Optimized release APK
- Release AAB
- Versioned source ZIP and TAR.GZ archives
- SHA-256 checksums for all attached assets
