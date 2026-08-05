<p align="center">
  <img src="branding/xylune-banner.png" alt="Temporary Arbor banner retained during the Xylune rebrand." width="100%">
</p>

<p align="center">
  Xylune (pronounced <strong>“Zy-loon”</strong>) is a native Android workspace for private AI chat, research, files, and local tools.
</p>

<p align="center">
  <a href="https://github.com/omerfaruknehir/Xylune/releases/latest"><strong>Download the latest APK</strong></a>
  ·
  <a href="BUILDING.md">Build from source</a>
  ·
  <a href="https://github.com/omerfaruknehir/Xylune/issues">Report an issue</a>
  ·
  <a href="PRIVACY.md">Privacy</a>
  ·
  <a href="TERMS.md">Terms</a>
</p>

<p align="center">
  <a href="https://github.com/omerfaruknehir/Xylune/actions/workflows/android.yml"><img alt="Android checks" src="https://github.com/omerfaruknehir/Xylune/actions/workflows/android.yml/badge.svg"></a>
  <a href="https://github.com/omerfaruknehir/Xylune/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/omerfaruknehir/Xylune?display_name=tag&sort=semver"></a>
  <img alt="Android 8+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <a href="LICENSE"><img alt="Apache License 2.0" src="https://img.shields.io/badge/License-Apache%202.0-blue"></a>
</p>

Xylune is a bring-your-own-provider AI client. It connects your phone directly to the services you choose—without a WebView, hosted Xylune account, application backend, telemetry, or advertising. API keys are protected with Android Keystore-backed encryption, chats live in a local SQLCipher database, and imported files remain in app-private storage.

Current version: **0.24.1**

## Development disclosure and disclaimer

Xylune was made with **full vibe coding**: features and changes were primarily directed in natural language and implemented with AI-assisted coding tools, with human review and testing. That process does not guarantee correctness, security, availability, or fitness for any purpose, and the app may contain serious defects.

Xylune is provided **“AS IS”**, without warranties of any kind. Use, modify, and distribute it at your own risk. Xylune is a client rather than an AI-model host: the maintainer does not create, train, host, pre-review, or endorse individual third-party model outputs. Review the source and output, keep backups, and do not rely on Xylune for safety-critical or irreplaceable work. The detailed allocation of responsibility and all mandatory-rights exceptions are in the [Terms of Use](TERMS.md).

## What makes Xylune different

### Android-native and private by design

- Built with Kotlin, Jetpack Compose, and Material 3—never a wrapped website.
- Connect ChatGPT, OpenAI-compatible APIs, Anthropic, Gemini, DeepSeek, OpenRouter, xAI, or a local model server.
- Keep credentials, conversations, workspaces, and attachments on your device.
- Talk directly to the selected provider; Xylune does not relay requests through its own server.

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

1. Download `Xylune-0.24.1-release.apk` from the [latest GitHub Release](https://github.com/omerfaruknehir/Xylune/releases/latest).
2. Allow installation from your browser or file manager when Android asks.
3. Open Xylune and follow the welcome flow.
4. Connect a ChatGPT account, API provider, or local model server.
5. Start a chat. Optional Python and Linux tools can be prepared later from **Settings → Local execution**.

The public APK is an R8-minified, resource-shrunk **release build**. It intentionally keeps package ID `app.xylune.chat` and Xylune's public reproducible signer so it can update the earlier GitHub debug builds without deleting chats or settings. The Android build is not debuggable, but Xylune's in-app **Developer settings** remain available. Protected production signing switches to the production package ID `app.xylune.chat`.

Local OpenAI-compatible servers default to `http://127.0.0.1:11434/v1`. On a physical phone, `127.0.0.1` means the phone itself. Xylune permits cleartext HTTP only for loopback and the Android emulator host alias; remote machines require HTTPS.

## Build

Requirements:

- JDK 17
- Android SDK 36 and Build Tools 36.0.0
- Linux, macOS, or Windows with Android Studio support

```bash
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease bundleRelease
```

Without protected signing variables, the release APK is signed with the repository's public update key and remains package-compatible with prior GitHub builds. The APK is written to `app/build/outputs/apk/release/app-release.apk`. See [BUILDING.md](BUILDING.md) for APK/AAB/instrumentation commands, ABI details, offline toolchain use, verification, and protected production signing.

## Releases and CI

Every push and pull request runs unit tests, Android lint, an optimized release APK/AAB build, and an Android 35 emulator smoke test through [Android CI](.github/workflows/android.yml).

When `main` introduces a new app version, the [release workflow](.github/workflows/release.yml) verifies bundled third-party license provenance, validates and decode-tests the local offline license catalog, repeats Android verification, builds the optimized release APK and AAB, verifies the APK signature, generates SHA-256 checksums, creates the matching version tag, and publishes the GitHub Release automatically. Same-version commits are detected and skipped.

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

Xylune's Python and Linux workspaces are private app storage, not operating-system sandboxes. Python runs inside the Xylune process. PRoot supplies Linux path and syscall compatibility under the same Android app UID; it is not a VM or privilege boundary. Do not run untrusted code.

Runtime package installation blocks unsafe command-line options by default and may reject packages without compatible Android wheels. Optional Linux distributions are downloaded only when selected, checked against pinned publisher SHA-256 values, and kept isolated from one another.

For dependency sources, bundled native component notices, hashes, and build recipes, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), the build-validated [`licenses/`](licenses/) catalog, and `third_party/`. The same catalog, local icons, and full texts are generated into Xylune's offline **About Xylune → Licenses & notices** screen.

## Project

Xylune is created by [@omerfaruknehir](https://github.com/omerfaruknehir).

- [Changelog](CHANGELOG.md)
- [Latest release notes](docs/releases/RELEASE_NOTES_0.24.1.md)
- [Source repository](https://github.com/omerfaruknehir/Xylune)
- [Issue tracker](https://github.com/omerfaruknehir/Xylune/issues)

## License

Xylune is licensed under the [Apache License 2.0](LICENSE). Bundled third-party components retain their own licenses as documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the local [`licenses/`](licenses/) catalog.
