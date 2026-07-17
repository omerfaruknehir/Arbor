# Arbor 0.11.0

Build date: 2026-07-17

Arbor 0.11.0 is a native Android reliability and tool-runtime release. It keeps the existing package name and Room schema version, so it installs over 0.10.0 without clearing chats, credentials, files, package environments, or settings.

## Crash-loop recovery

- Fixed the reported `ExceptionInInitializerError` at `MiniAppWidgetBlock.kt:101`. Android's regex runtime could reject the unescaped closing mini-app template delimiter during class initialization.
- A crash originating in a generated widget, chart, or diagram now writes a persistent safe-render flag before Android terminates the process.
- On the next launch Arbor leaves the message and its source intact but pauses generated rendering. The user can continue chatting, inspect the source, copy the redacted report, or retry full rendering from the crash dialog, the placeholder card, or Settings.
- Dismissing a crash report no longer risks immediately reopening the same renderer. Clearing all app data is not part of recovery.

## Empty-chat lifecycle

- Tapping New chat creates only an in-memory draft. It is not inserted into Room and does not appear in the sidebar until the first send or an attachment must be stored.
- Repeated New-chat taps reuse/replace that draft instead of creating rows.
- The 0.11 upgrade deletes only legacy conversations which contain neither a message nor an attachment. Existing content, archived chats, projects, usage, and history are untouched.

## Selectable Linux tools

- Tool workspaces now offer Ubuntu 26.04, Debian 13 (trixie), and Alpine 3.24.1. The selection persists across restarts.
- Each distribution has its own root filesystem and packages. Switching does not corrupt or silently replace another installed environment.
- Downloads are pinned by architecture and SHA-256. Setup validates the rootfs, PRoot launch, filesystem write/link behavior, DNS, and package-index refresh before reporting Ready.
- Package approval understands apt and apk, checks installed versions, simulates the transaction, shows dependencies, fingerprints the exact reviewed plan, and disables installation when nothing would change.
- Older `ubuntu_exec` and `ubuntu-packages` tool aliases remain accepted, while new prompts use the distribution-neutral `linux_exec` and `linux-packages` names.

## Long-running execution

- Manual Python and Linux commands are owned by the activity view model, so opening another chat or screen does not cancel them.
- Arbor shows an app-wide persistent running notice, elapsed seconds on the workspace screen, and a ten-second decision dialog with **Keep in background** and **Stop**.
- Python supports 1–600 second hard deadlines and cooperative user cancellation. PRoot shell commands support 1–3600 seconds and terminate the process tree when cancelled.
- Agent tools default to 45 seconds for Python and 60 seconds for Linux, accept bounded explicit deadlines, return exact elapsed/timeout data, and instruct the model to ask before a longer retry.
- Direct package-manager commands remain blocked in agent shell execution; installs must use the visible package approval card.

## Verification performed

- Kotlin/Java compilation for debug and release
- Room/KSP query validation
- JVM unit tests
- Python source compilation through the Chaquopy build
- Android lint and release lint-vital
- R8 release shrinking/resource optimization
- Debug and release APK assembly
- Release Android App Bundle assembly
- APK signature/badging checks and AAB archive validation

The supplied packages are development-signed. Use a protected production keystore before store publication. No physical Android device was attached to the build environment, so final acceptance of PRoot on each OEM, gesture behavior, provider credentials, OCR, launcher widgets, process death, and native Python-extension cancellation still belongs in on-device testing.
