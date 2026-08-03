# Xylune 0.10.0

Build date: 2026-07-17

Xylune 0.10.0 is a native Android BYOK chat-client repair and UX release. It keeps the app's existing package name and encrypted Room schema while upgrading the database from schema 7 to 8.

## Provider setup and models

- **Add provider** now asks for a user-defined name, protocol, endpoint, and credentials, then connects to the API and fetches the model catalog itself.
- OpenAI-compatible catalogs are read from `GET /models`; Anthropic and Gemini catalogs are paginated according to their native cursor formats, bounded to 500 entries and 2 MiB per page.
- The fetched models are searchable. Xylune automatically chooses an initial model but registers the complete fetched catalog, so the user does not have to enter every model ID.
- Gemini token limits and thinking metadata are imported when the endpoint supplies them. Known bundled models keep their curated context, capability, and price metadata; unknown pricing remains visibly editable instead of being invented.
- Manual model entry remains available only for compatible endpoints which do not expose model discovery.
- Existing providers can refresh their model catalog, can be removed with credential deletion, and appear in chat/automation selectors only when registered and usable.

## Reliability, history, and long chats

- Every generation stores an immutable provider/model/endpoint/limit/capability/pricing snapshot before work is scheduled.
- Streaming provider calls are cancellation-aware and bounded. Recoverable connection failures preserve partial output, retry with connectivity/backoff, and keep a per-call usage ledger.
- Same-chat queue positions are monotonic and materialized transactionally. Steering cancels all prior workers for that chat before appending the steering branch.
- Context compression uses stable row cursors, bounded chronological batches, and advances only over messages actually compressed.
- Prompt, summary, attachment, OCR, extracted text, reasoning, and tool content now participate in the context budget; complete oldest request/answer groups are removed together.
- Database migration 7→8 creates and backfills message FTS, adds immutable request snapshots, usage rows, summary cursors, and durable package transactions.
- Editing, retrying, and replacement branches remain retained in history.

## Python, Ubuntu, and package approval

- pip preflight now runs the resolver in dry-run/report mode for the exact Android ABI and Python 3.12 wheel target. Candidate versions and transitive dependencies appear in the approval plan.
- A package with installed metadata but a failing import is now offered as a repair/reinstall instead of being incorrectly disabled as already installed.
- Approved pip installs pin every resolved non-direct candidate version. pip and apt re-run preflight immediately before installation and reject a changed fingerprint.
- Install state is durable, so recomposition or process recovery cannot repeatedly execute an already succeeded or failed auto-approved request.
- Python activation refreshes import caches, maps distributions to import modules such as `Pillow → PIL`, preloads Android-packaged native libraries, verifies imports in a staging environment, and leaves the last working environment intact on failure.
- apt checks installed packages, simulates the complete transaction, recovers interrupted dpkg state, and installs only after an exact reviewed-plan match.
- Ubuntu setup validates free space, rootfs integrity, PRoot write/link behavior, and Canonical SHA-256. It uses Android's current DNS servers when available so VPN/private-DNS configuration is respected.

## Files, images, working timeline, and UI

- The agent must call a dedicated `send_file` tool for each file it returns. This prevents workspace diffs from hoisting unrelated files and places each card exactly where it was sent in the response timeline.
- Only directly adjacent reasoning/search/code/file events form one **Working** group. Normal assistant text always ends the group; all events retain their chronological order.
- Raster images render cleanly inline. The full-screen viewer supports double-tap zoom, zoom controls, pinch zoom, and clamped two-dimensional panning so the image cannot be lost off-screen.
- User images remain normal photos. OCR is an optional disclosed fallback view for text-only models; assistant-created images do not get unnecessary OCR treatment.
- PDF preview has bounded multi-page navigation and an explicit render-error state. Other file cards expose preview where supported, save, and share.
- Streaming follow can re-stick after the user returns near the bottom, does not fight an active drag, and exposes a go-to-bottom button.
- Working code uses native syntax coloring; command, result, stdout, stderr, files, status, and timing remain visually separate.
- Reasoning is never deleted or hidden. The visibility setting controls whether its retained card is always expanded, expanded only while working, or collapsed by default.
- The default palette is calmer and less saturated, with Material You, graphite, and AMOLED alternatives. The adaptive launcher icon has a new Xylune conversation/tree mark.

## Generated interaction security

- Conversation-only `xylune-ui` mini-apps remain separate from explicitly Home-eligible `xylune-widget` definitions.
- Every generated definition has a local capability, benefit, risk, and caution review. A configured model may supply a second advisory security opinion, but local validation remains authoritative.
- Live network data requires first-use consent and is not fetched simply because a model emitted a definition. Home-screen pinning has a separate review/confirmation, and pending pin definitions expire.
- Generated UI remains a bounded native schema: no HTML, JavaScript, WebView, downloaded executable code, reflection, arbitrary intents, or hidden network access.

## Important boundaries

- The supplied APK/AAB are debug-signed development artifacts, not Play-production releases.
- Embedded Python runs inside Xylune's Android process and accepts only pure-Python or Chaquopy-compatible Android wheels. Ubuntu/PRoot is the broader Linux tool-compatibility layer, not a stronger security boundary.
- A blocking native Python extension cannot be forcibly killed safely in-process. Do not run untrusted code.
- No physical Android device or emulator was attached to this build environment. JVM tests, Python syntax compilation, Android lint, APK signature verification, and bundle archive verification are performed, but Chaquopy, PRoot, gestures, OCR, provider accounts, WorkManager process death, and launcher widgets still require on-device acceptance testing.
- Production signing, a full Mermaid grammar, arbitrary office-document rendering, provider-native structured tools for every API, and a bundled local image-captioning model are not included.

See `CHANGELOG.md`, `README.md`, `ARCHITECTURE.md`, `WIDGETS.md`, and `BUILDING.md` for details.
