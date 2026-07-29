<p align="center">
  <img src="branding/arbor-banner.png" alt="Arbor — Native Android. Private by design." width="100%">
</p>

<p align="center">
  A fully native, local-first Android client for private AI chat and agent work.
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
</p>

Arbor connects directly to the model providers you configure. There is no WebView, hosted Arbor account, application backend, telemetry, or advertising. API keys are protected by Android Keystore-backed encryption, chats live in a local SQLCipher database, and imported files remain in the app's private storage.

Current version: **0.20.3**

## Why Arbor

### Native, private, and yours

- Kotlin, Jetpack Compose, and Material 3 from edge to edge—no wrapped website.
- Bring your own keys for OpenAI-compatible endpoints, Anthropic, Gemini, DeepSeek, OpenRouter, xAI, local servers, and custom providers.
- Encrypted local credentials and chat storage, with Android backup disabled.
- No Arbor server between your device and the provider you choose.

### A serious chat client

- Concurrent streaming chats with stop, queue, steer, retry, branch history, unread state, pinning, archiving, projects, and full-text search.
- Per-chat Thinking, Search, and Tools controls beside the composer, plus configurable context, output, system prompts, and automation models.
- Native Markdown, tables, LaTeX, syntax-highlighted code, diagrams, charts, image/PDF/text previews, attachments, OCR, token counts, and cost totals.
- Long-chat paging, context compression, automatic titles, response usage accounting, and provider-specific token estimation.

### Agent work without hiding the work

- Durable Working timelines for Python, Linux, search, page reading, package installation, file changes, and reruns.
- Embedded Python 3.12 with a persistent private workspace per conversation.
- Optional Ubuntu, Debian, or Alpine PRoot environments for broader command-line tooling.
- Explicit package and tool policies: ask, trusted-list approval, approval-model review, or user-selected automation.
- Generated native mini-apps and widgets built from an audited declarative component registry—never model-written Android code.

## Install

1. Download `Arbor-0.20.3-debug.apk` from the [latest GitHub Release](https://github.com/omerfaruknehir/Arbor/releases/latest).
2. Allow installation from your browser or file manager when Android asks.
3. Open Arbor → **Settings** → **Providers & models**.
4. Add a provider, connect and fetch its models, then select one in a chat.

The downloadable APK uses package ID `app.arbor.chat.debug` and Arbor's public, reproducible debug signer. It is suitable for direct testing and can update earlier GitHub/local debug builds signed with the same key. It is not Play-production-signed.

Local OpenAI-compatible servers default to `http://127.0.0.1:11434/v1`. On a physical phone, `127.0.0.1` means the phone itself. Arbor permits cleartext HTTP only for loopback and the Android emulator host alias; remote machines require HTTPS.

## Build

Requirements:

- JDK 17
- Android SDK 35 and Build Tools 35.0.0
- Linux, macOS, or Windows with Android Studio support

```bash
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. See [BUILDING.md](BUILDING.md) for the full APK/AAB/instrumentation command, ABI details, offline toolchain, verification, and protected release signing.

## Releases and CI

Every push and pull request runs unit tests, Android lint, APK/AAB compilation, and an Android 35 emulator smoke test through [Android CI](.github/workflows/android.yml).

Pushing a version tag such as `v0.20.3` runs the [release workflow](.github/workflows/release.yml). It checks that the tag matches the app version, repeats verification, builds the reproducibly signed debug APK, AAB, and instrumentation APK, generates SHA-256 checksums, uploads the build set, and creates the GitHub Release automatically.

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
| Packaged ABIs | `arm64-v8a`, `x86_64` |

## Security boundaries

Arbor's Python and Linux workspaces are private app storage, not operating-system sandboxes. Python runs inside the Arbor process. PRoot supplies Linux path and syscall compatibility under the same Android app UID; it is not a VM or privilege boundary. Do not run untrusted code.

Runtime package installation blocks unsafe command-line options by default and may reject packages without compatible Android wheels. Optional Linux distributions are downloaded only when selected, checked against pinned publisher SHA-256 values, and kept isolated from one another.

For dependency sources, bundled native component notices, hashes, and build recipes, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `third_party/`.

## Project

Arbor is created by [@omerfaruknehir](https://github.com/omerfaruknehir).

- [Changelog](CHANGELOG.md)
- [Latest release notes](RELEASE_NOTES_0.20.3.md)
- [Source repository](https://github.com/omerfaruknehir/Arbor)
- [Issue tracker](https://github.com/omerfaruknehir/Arbor/issues)

## License

No license grant is implied by this repository. Add an explicit project license before redistributing Arbor. Bundled third-party components retain their own licenses as documented in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
