<p align="center">
  <img src="branding/arbor-banner.png" alt="Arbor — Native Android. Private by design." width="100%">
</p>

<p align="center">
  A native Android workspace for private AI chat, research, files, and local tools.
</p>

<p align="center">
  <a href="https://github.com/omerfaruknehir/Arbor/releases/latest"><strong>Download the latest APK</strong></a>
  ·
  <a href="BUILDING.md">Build from source</a>
  ·
  <a href="https://github.com/omerfaruknehir/Arbor/issues">Report an issue</a>
</p>

<p align="center">
  <a href="https://github.com/omerfaruknehir/Arbor/actions/workflows/android.yml"><img alt="Android checks" src="https://github.com/omerfaruknehir/Arbor/actions/workflows/android.yml/badge.svg"></a>
  <a href="https://github.com/omerfaruknehir/Arbor/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/omerfaruknehir/Arbor?display_name=tag&sort=semver"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="Apache License 2.0" src="https://img.shields.io/badge/License-Apache%202.0-blue"></a>
</p>

Arbor is a bring-your-own-provider AI client. It connects your phone directly to the services you choose—without a WebView, hosted Arbor account, application backend, telemetry, or advertising. API keys are protected with Android Keystore-backed encryption, chats live in a local SQLCipher database, and imported files remain in app-private storage.

Current version: **0.20.6**

## What makes Arbor different

### Android-native and private by design

- Built with Kotlin, Jetpack Compose, and Material 3—never a wrapped website.
- Connect ChatGPT, OpenAI-compatible APIs, Anthropic, Gemini, DeepSeek, OpenRouter, xAI, or a local model server.
- Keep credentials, conversations, workspaces, and attachments on your device.
- Talk directly to the selected provider; Arbor does not relay requests through its own server.

### A capable everyday chat client

- Run concurrent streaming chats with stop, queue, steer, retry, branches, unread state, pinning, archiving, projects, and full-text search.
- Adjust Thinking, Search, and Tools per chat, while keeping context, output, custom instructions, and automation defaults in Settings.
- Native Markdown, tables, LaTeX, syntax-highlighted code, diagrams, charts, image/PDF/text previews, attachments, OCR, token counts, and cost totals.
- Long-chat paging, context compression, automatic titles, response usage accounting, and provider-specific token estimation.

### Agent work you can inspect

- Follow a durable Working timeline for Python, Linux, search, page reading, package installation, file changes, and reruns.
- Embedded Python 3.12 with a persistent private workspace per conversation.
- Optional Ubuntu, Debian, or Alpine PRoot environments for broader command-line tooling.
- Explicit package and tool policies: ask, trusted-list approval, approval-model review, or user-selected automation.
- Generated native mini-apps and widgets built from an audited declarative component registry—never model-written Android code.

## Install

1. Download `Arbor-0.20.6-debug.apk` from the [latest GitHub Release](https://github.com/omerfaruknehir/Arbor/releases/latest).
2. Allow installation from your browser or file manager when Android asks.
3. Open Arbor and follow the welcome flow.
4. Connect a ChatGPT account, API provider, or local model server.
5. Start a chat. Optional Python and Linux tools can be prepared later from **Settings → Local tools**.

The downloadable APK uses package ID `app.arbor.chat.debug` and Arbor's public, reproducible debug signer. It is intended for direct testing and can update earlier GitHub or local debug builds signed with the same key. It is not signed for production store distribution.

Local OpenAI-compatible servers default to `http://127.0.0.1:11434/v1`. On a physical phone, `127.0.0.1` means the phone itself. Arbor permits cleartext HTTP only for loopback and the Android emulator host alias; remote machines require HTTPS.

## Build

Requirements:

- JDK 17
- Android SDK 36 and Build Tools 36.0.0
- Linux, macOS, or Windows with Android Studio support

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [BUILDING.md](BUILDING.md) for the full APK/AAB/instrumentation command, ABI details, offline toolchain, verification, and protected release signing.

## Releases and CI

Every push and pull request runs unit tests, Android lint, APK/AAB compilation, and an Android 35 emulator smoke test through [Android CI](.github/workflows/android.yml).

Pushing a version tag such as `v0.20.6` runs the [release workflow](.github/workflows/release.yml). It checks that the tag matches the app version, verifies bundled third-party license provenance, validates the local offline license catalog, repeats Android verification, builds the reproducibly signed debug APK, AAB, and instrumentation APK, generates SHA-256 checksums, uploads the build set, and creates the GitHub Release automatically.

Production distribution deliberately requires your own protected signing key. No production private key is stored in this repository.

## Architecture at a glance

| Area | Implementation |
|---|---|
| UI | Jetpack Compose + Material 3 |
| Storage | Room + SQLCipher |
| Credentials | Android Keystore-backed encrypted preferences |
| Networking | OkHttp with provider-specific adapters |
| Background work | WorkManager foreground jobs |
| Python | Embedded CPython 3.12 via Chaquopy |
| Linux tools | Optional per-chat PRoot distributions |
| Generated UI | Native Compose / audited `RemoteViews` primitives |
| Minimum Android | Android 8.0 / API 26 |
| Target Android | Android 16 / API 36 |
| Packaged ABIs | `arm64-v8a`, `x86_64` |

## Security boundaries

Arbor's Python and Linux workspaces are private app storage, not operating-system sandboxes. Python runs inside the Arbor process. PRoot supplies Linux path and syscall compatibility under the same Android app UID; it is not a VM or privilege boundary. Do not run untrusted code.

Runtime package installation blocks unsafe command-line options by default and may reject packages without compatible Android wheels. Optional Linux distributions are downloaded only when selected, checked against pinned publisher SHA-256 values, and kept isolated from one another.

For dependency sources, bundled native component notices, hashes, and build recipes, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), the build-validated [`licenses/`](licenses/) catalog, and `third_party/`. The same catalog and full texts are generated into Arbor's offline **About Arbor → Licenses & notices** screen.

## Project

Arbor is created by [@omerfaruknehir](https://github.com/omerfaruknehir).

- [Changelog](CHANGELOG.md)
- [Latest release notes](docs/releases/RELEASE_NOTES_0.20.6.md)
- [Source repository](https://github.com/omerfaruknehir/Arbor)
- [Issue tracker](https://github.com/omerfaruknehir/Arbor/issues)

## License

Arbor is licensed under the [Apache License 2.0](LICENSE). Bundled third-party components retain their own licenses as documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the local [`licenses/`](licenses/) catalog.
