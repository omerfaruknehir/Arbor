# Xylune 0.23.3

This release repairs first-run setup and cloud restore feedback.

## Setup

- Reworked the five-step setup flow with clearer page titles, denser spacing, simpler primary actions, and an obvious **Later** path.
- Added Android predictive Back previews between setup steps.
- Added predictive Back motion to setup dialogs while preserving keyboard-first Back behavior.
- Clarified that backup restore, provider connection, Python, and Linux are optional and can be changed later.

## Cloud restore

- Replaced oversized cloud-provider artwork with consistently bounded compact icons.
- Added visible progress, cancellation, success, empty-state, and error feedback to every restore route.
- Continue OneDrive and Dropbox restore automatically after browser sign-in returns to Xylune.
- Explain unavailable OAuth configuration instead of silently disabling a provider.
- Treat a connected location with no backups as a normal empty state and provide **Check again**.
- Prevent the WebDAV and S3 configuration dialogs from being hidden behind the cloud chooser.
- Show a confirmation before opening a downloaded backup in the import preview.

## Validation

- Added regression coverage for cloud feedback, provider icon bounds, dialog ownership, and predictive setup navigation.
