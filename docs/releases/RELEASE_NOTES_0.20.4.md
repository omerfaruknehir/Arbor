# Arbor 0.20.4

Arbor 0.20.4 fixes the confusing Android version information in About Arbor and moves the project to Android 16 / API 36.

The old `SDK 26–35` label mixed up the minimum and target SDKs, making API 35 look like the newest Android version Arbor could run on. About Arbor now reads the installed package metadata and reports these as separate values:

- **Minimum Android:** Android 8.0+ · API 26+
- **Target Android:** Android 16 · API 36
- **Running on:** the device's actual Android release and API level

This is not an Android 17 / API 37 target migration. Android 17 changes local-network access and native dynamic-code loading for apps that target API 37, both of which need dedicated testing in Arbor before that opt-in.

The first-run experience is now intentional instead of dropping an unconfigured user into a disabled chat:

- A welcome page explains Arbor's privacy model and optional tools.
- A setup page routes directly to ChatGPT, API, and local-provider configuration.
- Chat shows a clear setup action when no usable provider is connected.
- The composer stays disabled until a provider is ready.

Local tools are easier to understand and manage:

- Python and Linux now have focused tabs in one Tool workspace.
- Linux distribution selection, install, status, packages, and removal live only in that manager.
- The Linux terminal is execution-only and returns to the workspace with Back.
- The per-chat Tools menu warns when Linux is missing and links to setup.
- Duplicate new-chat tool toggles and the duplicate Linux command runner were removed.

This release also rewrites the README for clarity and adds the Apache License 2.0 for Arbor's own source. Bundled third-party components remain under their documented licenses.

## Install

Download `Arbor-0.20.4-debug.apk` from the release assets. It is debug-signed for direct testing and uses package ID `app.arbor.chat.debug`.

## Verification

- Unit tests
- Android lint
- Debug APK
- Debug AAB
- Debug instrumentation APK

The release APK is not Play-production-signed. Build with your own protected release key before store distribution.
