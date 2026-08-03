# Xylune 0.17.0 release notes

## Highlights

- The phone navigation drawer can now be pulled from anywhere in the chat surface. A deliberate 6 dp horizontal movement starts finger tracking; vertical intent continues to belong to chat scrolling. It uses a 30% position or 850 dp/s fling threshold. Reversal, scrim opacity, drawer/content translation, settling, scrim tap, Back, and hamburger actions all use one clamped drawer offset.
- Top chrome blur now derives from actual chat-list scroll position, so it begins only as content scrolls beneath the chrome rather than inheriting the header's collapsed state. Chat content also has 44 dp of extra top breathing room and 34 dp at the bottom.
- Agent Python and Linux executions now create durable run records in the existing conversation workspace. The model can inspect bounded line ranges, apply an atomic SHA-256-guarded unified patch, and rerun the same canonical source without resending it.
- `GeneratedContentCapabilityRegistry` is the canonical contract for native chat mini-apps, Home-screen widgets, native charts, and the supported Mermaid/DOT subset. Every request receives a compact summary; relevant turns receive only the exact needed schema and valid examples.
- Invalid completed generated blocks are repaired in place up to a bounded configurable limit (default three, range one to five). Surrounding text stays visible, repeated invalid candidates cannot loop forever, and exhausted repairs remain safe and editable as declarative text.

## Compatibility and data

- Application ID/package naming and debug signing behavior are unchanged.
- Room remains at schema version 13. No database migration, destructive fallback, data clearing, or credential/settings/workspace reset is introduced.
- The generated repair limit is stored in the existing app preferences. Run/repair records use the established per-conversation workspace abstraction and do not contain provider credentials or arbitrary environment secrets.
- The app remains fully native: no WebView, HTML/JavaScript mini-app, generated JavaScript, or untrusted executable UI path was added.

## Testing

The supplied offline Android toolchain completed Kotlin and Java debug compilation, `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `bundleDebug`, `assembleDebugAndroidTest`, `lintVitalRelease`, `assembleRelease`, and `bundleRelease` with exit status 0. The JVM suite contains 173 tests: 173 passed, 0 failed, 0 errors, and 0 skipped. Android lint reported 0 errors, 10 warnings, and 2 informational findings; lint-vital also completed successfully. The build and verification transcript is supplied as `Xylune-0.17.0-build-and-test.log`.

Artifact checks confirmed a non-empty debug APK and AAB, ZIP integrity, debug APK application ID `app.xylune.chat.debug`, version code 77, version name `0.17.0-debug`, and a valid Android debug signature.

Physical-device testing was not performed in this build environment.
