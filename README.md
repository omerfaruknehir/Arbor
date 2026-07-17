# Arbor

Arbor is a fully native Android BYOK chat client built with Kotlin, Jetpack Compose, and Material 3. It has no WebView, hosted account, telemetry, ads, or application backend. API keys and chat data stay on the device; requests go directly to the endpoints configured by the user.

This repository is version `0.12.0`, an installable and deliberately honest foundation for a larger agent client.

## Included

- Concurrent streaming conversations backed by WorkManager foreground jobs, with stop, queue, steer, interrupted-response recovery, and notification controls.
- Typed transactional message creation, Android 14+ foreground-service declarations, visible action errors, and crash-loop recovery which pauses generated renderers without deleting chats or app data.
- Provider adapters for OpenAI-compatible APIs (DeepSeek, OpenAI, OpenRouter, xAI, local servers, and custom endpoints), Anthropic Messages, and Gemini GenerateContent. Provider setup validates the endpoint and credentials, fetches the provider's current model catalog, lets the user select from a searchable list, and retains manual entry only as a fallback. Existing providers can refresh their catalog at any time.
- Encrypted local storage: SQLCipher/Room for chats and Android Keystore-backed encrypted preferences for API keys.
- Long-chat paging, SQLite FTS5 search, message DAG metadata, token/cost totals, context-pair and token ceilings, and per-response output limits.
- Global title/message/reasoning search plus responding, attention, and unread badges in the conversation list.
- Native long-press chat management with pin, rename, project assignment, archive/unarchive, and confirmed deletion. Projects can be created, renamed, filtered, and removed without deleting their chats.
- Automatic titles which evolve from recent user requests, with an explicit **Regenerate chat name** action.
- DeepSeek V4 Flash and V4 Pro defaults. The prices in `DefaultCatalog.kt` are USD per million tokens and distinguish cache-hit input, cache-miss input, and output.
- Native rendering for CommonMark-style Markdown, tables, tasks, strikethrough, LaTeX, fenced code with copy/run actions, Mermaid-style flow/sequence diagrams, and bar/line/area/scatter/pie/donut charts. Diagrams and charts have larger native previews and never use a WebView.
- Expandable streamed reasoning, provider usage accounting, native file cards placed at their real response-timeline position, full-width inline raster images, pinch-zoom/pan image preview, PDF/text preview, saving, sharing, and user attachments above message text. AI-created images stay clean; user-photo OCR is a disclosed fallback layer for models without vision and is hidden until requested.
- On-device ML Kit OCR for images and the first 12 PDF pages. OCR JSON includes page text, element coordinates, block/line indices, and confidence where supplied.
- Embedded Python 3.12 with a persistent private directory per conversation. Imported files are mirrored under `incoming/` in that workspace.
- Policy-gated runtime Python package installation with live importer-cache refresh, Android native dependency preloading, post-install import verification, and distribution-to-module mappings such as `Pillow → PIL`. Agents request packages using a `python-requirements` fence; Arbor preflights it before applying the user's ask/trusted/model/auto policy. Pure-Python and compatible Chaquopy Android wheels are supported.
- Managed per-chat Python environments with serialized interpreter activation, persistent variables, execution deadlines, transactional install verification, package inventory/removal, repair, and session reset. A failed install leaves the previous environment intact.
- Optional selectable Ubuntu 26.04, Debian 13, or Alpine 3.24.1 tooling for arm64-v8a phones and x86_64 emulators. Arbor downloads only the chosen rootfs, verifies a pinned publisher SHA-256, self-tests PRoot, refreshes apt/apk metadata, keeps distributions isolated, persists the selection, and mounts the current chat at `/workspace`.
- Agent and tap-to-run Linux shell commands for broader third-party command-line tools. Manual runs continue while navigating, have configurable hard deadlines, show an app-wide running indicator and ten-second warning, and can be stopped. An agent returns a created file with an explicit `send_file` tool at its correct response-timeline position.
- Unified pip/apt/apk preflight cards which detect already-satisfied packages, show installs/upgrades and dependencies in one transaction, and disable installation when there is nothing to change.
- Configurable package policies: always ask, auto-approve a trusted package list, ask a separately selected approval model, or auto-approve every valid plan. Strict package-source restrictions can be explicitly relaxed in Settings.
- Model-driven agent loops for direct Python execution, DuckDuckGo HTML search, and public HTTP(S) page reading, with per-conversation allow/deny controls and private-address fetch blocking. Tool protocol is removed from the transcript and results are retained in the encrypted Working trace.
- An ordered response timeline which groups only adjacent reasoning, search, and Python events into animated **Working** cards. Normal assistant text splits groups in its original position. Cards are never removed: options are **Always expanded**, **Expanded while working**, and **Always collapsed**.
- Language-aware native syntax coloring for assistant Markdown code and Python/Ubuntu Working steps, with code, result, stdout, stderr, and changed files kept in distinct panels. Active responses use a subtle staggered fading token pulse.
- Separate native interaction surfaces: `arbor-ui` is always conversation-only for questions, requirements, forms, previews, and other transient interaction. `arbor-widget` can offer launcher pinning only when an explicitly requested definition also declares `surface: "home"` or `"both"`; Home eligibility defaults off.
- Recoverable streaming auto-scroll: browsing older messages pauses following, returning near the latest response resumes it, and a floating button jumps directly to the bottom.
- A general native mini-app runtime. Models can compose up to eight screens from text, metrics, inputs, sliders, toggles, choices, button grids, progress, lists, tables, charts, timers, dividers, live bindings, and conditional elements. Persistent state, templates, safe formulas, navigation, and ordered action chains enable calculators, trackers, quizzes, dashboards, scoreboards, budgeting tools, schedules, and other designs without adding a hardcoded UI type. Chat definitions render with Compose; explicitly Home-eligible definitions can additionally render as dynamically assembled `RemoteViews`.
- Convenience widget definitions remain available for choices, checklists, converters, calculators, stocks/live JSON, and schedule/prayer-time views. Live definitions use explicit JSON-path bindings, HTTPS-only public endpoints, response limits, cached last-good values, manual refresh, and 15-minute-or-longer WorkManager refresh policies.
- User-message editing and assistant retry. Replaced paths are superseded rather than deleted, preserving the complete revision/branch history in the encrypted database.
- An in-app edited-message history viewer for those saved superseded branches.
- Agent-created or modified files are listed in tool output; only files explicitly returned with the `send_file` tool appear as native preview/share cards, at the exact position where the agent sent them.
- Context compression for messages outside the verbatim pair/token window. It can be off, deterministic/local with no API call, or driven by a separately selected provider/model. Summaries are persisted, inspectable by size/count, and can be cleared.
- Separate automation policies and model selectors for evolving chat titles and context compression, plus an editable model catalog for context/output limits, capability flags, and pricing.
- Phone/tablet adaptive Compose UI, edge-to-edge layout, a calmer Arbor green-neutral palette, optional Material You or graphite palettes, optional AMOLED surfaces, revised Material 3 hierarchy, and a remastered adaptive/themed launcher mark.
- Persistent, race-safe settings split into **Chat**, **Global**, and **Providers** tabs. Each conversation stores its own model, thinking, Web/Python/Linux permissions, context, Working, output, and system-prompt choices. The last selected chat options also become the persistent defaults for new chats without rewriting existing conversations. Unsaved empty chats stay in memory and never clutter the sidebar.

## Important boundaries

Arbor's Python and Linux workspaces are private application storage, not operating-system security boundaries. Python executes inside the app process, while PRoot provides Linux path/syscall compatibility under the same Android app UID. Both can access resources available to Arbor. Deadlines reliably interrupt Python bytecode, but a blocking native extension may return later. Do not run untrusted code.

Strict runtime Python installation accepts names and version constraints; Settings can allow direct PEP 508 references. Pip command-line options remain blocked. Not every PyPI project publishes an Android-compatible wheel, so packages with unsupported native extensions fail transactionally. The selectable apt/apk Linux layer is the broader compatibility route for Linux tools and libraries.

A full Mermaid grammar, visual Office-document rendering, Android system image descriptions, exact model-specific preflight tokenizers, Bedrock/Azure signing adapters, and Play production signing are not implemented yet. DOCX, PPTX, and XLSX text is extracted locally with bounded OOXML parsing. Native structured tool calls are implemented for OpenAI-compatible, Anthropic, and Gemini providers, with the portable fenced protocol retained as a fallback. The native diagram renderer intentionally supports the most useful flow and sequence subset. Android launchers render `RemoteViews`, not arbitrary generated Compose/custom views, so Home-screen mini-apps dynamically assemble audited native primitives from a safe declarative state machine; they cannot run model-written UI code. Home-screen text fields are read-only and should be paired with generated choices/keypads because launchers do not provide arbitrary text entry. Home-screen timers update when the widget is refreshed or interacted with, not every second. Live widgets require a compatible public JSON API and do not embed provider credentials. Arbor uses native structured tool calls where the included provider protocol supports them and falls back to its portable fenced protocol when an endpoint or model does not. Unsupported images are represented to text-only models through OCR/extracted data; no local captioning model is bundled.

The supplied APK/AAB are debug-signed so they are immediately testable. Use your own protected release key before publishing. API behavior changes over time; provider defaults may need editing when vendors change endpoints or schemas.

## Quick start

1. Install the APK on Android 8.0 or later (`arm64-v8a` and `x86_64` are packaged).
2. Open Settings → **Providers**, tap **Add provider**, choose its protocol, give it a name, and enter its endpoint and key. Tap **Connect & fetch models**, select the models to register, then save it securely. Manual model IDs are available only as a fallback for endpoints without model discovery.
3. Tap **Use … in this conversation**, choose a model from the top chip, and send a message.
4. Hold the Send button while a response is running to stop, queue, steer, or start a separate turn. A normal tap with drafted text queues safely while the current turn is working.
5. Open **Tool workspaces**, choose Ubuntu, Debian, or Alpine, and install it when broader Linux tools are useful; the layer is optional and does not inflate first-launch data.

Local servers use `http://127.0.0.1:11434/v1` by default. On a physical phone, `127.0.0.1` is the phone itself. Arbor permits cleartext HTTP only for loopback and the Android emulator host alias; use HTTPS for a server on another machine.

## Build

See [BUILDING.md](BUILDING.md). The project requires JDK 17 and Android SDK 35. The companion build-tools archive contains the exact JDK, Gradle, SDK, and populated dependency cache used for the supplied artifacts.

## Privacy and security notes

- Android backup is disabled.
- API keys are encrypted with an Android Keystore master key.
- The database passphrase is randomly created and Keystore-encrypted.
- Cleartext HTTP is restricted to loopback and the Android emulator host alias; every non-local endpoint must use HTTPS.
- File imports are copied into private app storage. Deleting a conversation deletes its database attachment records; secure deletion of flash blocks cannot be guaranteed by Android storage.

## License

No license grant is implied by this delivery. Add the license you want before redistributing the source.

Bundled runtime components retain their own licenses. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md); complete corresponding source archives and Termux build recipes are included under `third_party/`.
