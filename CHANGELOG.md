# Arbor 0.17.0

- Make pull-to-open available from anywhere in the chat surface after a deliberate 6 dp horizontal movement, with tolerant accumulated-motion arbitration, a 30% settle threshold, and an 850 dp/s fling threshold. The starting touch location no longer gates the gesture, and crossing the movement threshold never triggers an independent open animation.
- Restore chat chrome blur activation to the actual list scroll position instead of the independently collapsed header state, and add modest extra top and bottom chat gutters for more comfortable end-of-list scrolling.
- Replace threshold-triggered drawer opening with one continuously finger-tracked offset, edge/vertical gesture arbitration, velocity-aware spring settling, interactive close, scrim tap, Back, and hamburger control.
- Persist every agent Python/Linux script and attempt under its conversation workspace; add bounded `workspace_read`, atomic SHA-guarded `apply_patch`, and source-free `rerun_script` tools with compact failure diagnostics.
- Add the authoritative generated-content capability registry and inject its compact contract into every model request, with relevant exact schemas/examples only for widget, chart, or diagram intent.
- Validate completed generated blocks through recognition, syntax, schema, semantic, security/limits, and renderer preparation, then repair only an invalid block in place with a persisted one-to-five-attempt cycle.
- Preserve the Room schema at version 13; no destructive migration or data reset is required.
- Add deterministic regression coverage for drawer physics/arbitration, run revisions and atomic patches, capability consistency, repair retry/exhaustion/persistence, and ordered streaming content.

# Arbor 0.16.60

- Stabilize the chat viewport through every Working-card expansion and collapse instead of correcting only after a manual collapse.
- Manual expansion pins the card header so the card grows downward; manual collapse keeps the header fixed and still centers very large cards after the animation.
- Automatic card changes pin the latest-message bottom while following, preserve downstream content when the card is above the detached viewport, and avoid touching the list when the card is below it.
- Suspend the nonlinear streaming follower while a Working-card mutation owns the viewport, preventing the two scroll controllers from fighting each other.
- Add regression tests for the Working-card anchor strategy.


# Arbor 0.16.59

- Added a streaming scroll-anchor guard. Room/Paging refreshes can no longer reset the visible list to item 0 and then make the nonlinear follower race back down.
- Preserved stable message keys across transient Paging refresh gaps.
- Restored pull-to-open for the conversation drawer with a 56 dp edge zone and a low 10 dp horizontal trigger.
- Kept the drawer gesture edge-only while closed so vertical chat/table scrolling is not captured; native pull-to-close remains enabled while open.

# Arbor 0.16.58

- Fixed random jumps to the top of a newly appended streaming item followed by a scroll back down.
- Removed hard list positioning from the active-generation reattachment path.
- Kept nonlinear auto-follow as the only streaming scroll controller.

# Arbor 0.16.57

- Replaced constant-velocity off-screen auto-follow with distance- and time-sensitive exponential acceleration.
- Raised the measured-tail correction ceiling from 2,800 px/s to 48,000 px/s and the off-screen seek ceiling to 72,000 px/s.
- Kept small final corrections gentle while allowing large table, tool, and file-card insertions to catch up almost immediately.

# Arbor 0.16.56

- Fixed live rich-message updates being stuck on the first captured streaming snapshot; tables and all following content now appear without reopening the chat.
- Increased eased auto-follow speed and large-insertion catch-up speed.

## 0.16.54

- Fixed auto-scroll no longer following streamed output.
- Render tables as bounded aligned grids during streaming instead of raw Markdown.
- Preserve freeze protection for huge generated tables.

## 0.16.53

- Fixed the apparent token-by-token line breaks by forcing every streaming fade/tail wrapper to inherit the full message width and propagate its constraints to the Android Markdown view.
- Replaced live Markwon table layout with a bounded, throttled plain-text preview; small tables are rendered normally once complete, while oversized completed tables stay on the safe lightweight path.
- Bypassed Markdown/table parsing entirely while a table is streaming, bounded detection and preview work, and increased catch-up batch size so generated tables cannot monopolize the UI thread.
- Eased auto-follow with frame-paced bounded scrolling, and delayed initial bottom positioning until the composer obstruction is measured.
- Raised horizontal gesture touch slop for tables, code and diagrams, and removed the aggressive full-screen drawer swipe so vertical chat/sidebar scrolling wins diagonal gestures.

## 0.16.52

- Prevented alternating reasoning/text provider chunks from becoming one rendered Markdown block per token; fragments are concatenated exactly with no inserted whitespace.
- Kept visible text and reasoning as independent aggregate timeline streams and repaired already-fragmented timelines at render time.
- Corrected streaming auto-follow to stop above the full composer and bottom gutter rather than at the physical screen edge.

## 0.16.51

- Rewrote the chat viewport around one chronological keyed list and one measured bottom-follow loop; removed reverse-layout correction, frozen snapshots, and competing scroll jobs.
- Added append-only Markdown block parsing so completed blocks stay stable and only the unfinished streaming tail is reparsed.
- Replaced growing timeline text duplication with compact aggregate-field ranges, and delta-only text persistence, avoiding full timeline JSON serialization/decoding on ordinary stream flushes.
- Removed code linting from rendering, editors, and tool execution while retaining syntax colouring.

## 0.16.50

- Fixed stale assistant retry siblings rendering as consecutive messages with duplicate Working cards and identical branch counters.
- Added transactional active-path repair from the conversation leaf before a chat is displayed.
- Kept every repaired alternative available through the inline branch controls; no branch content is deleted.

## 0.16.48

- Pinned manually expanded Working cards so they grow downward without moving their header.
- Centered large Working cards after manual collapse.
- Deferred offscreen automatic Working-card expansion/collapse until scrolling or visibility, eliminating below-view animation drift.
- Prevented no-op bottom gestures from detaching and reattaching the live message list.
- Stabilized partial Markdown table rows and locked table width during streaming to eliminate layout flicker.

## 0.16.47

- Prevent Markdown table boundaries from leaking blank lines into surrounding text blocks.
- Parse table columns structurally so escaped pipes and inline-code pipes do not create phantom cells or broken spacing.
- Surface OpenAI-compatible and Anthropic tool calls as soon as their streamed name or arguments arrive, including incremental code, command, query, URL, and path previews.
- Surface Gemini function calls immediately when the provider emits the function-call part.
- Reuse provisional tool events when execution begins, avoiding duplicate cards and preserving a single call lifecycle from preparing to running to complete.
- Disable expensive linting while tool-call code is still streaming, then lint once the arguments are complete.

## 0.16.46

- Keep a stable visible-message anchor throughout working-card collapse/expansion, streamed tool insertion, and Python-result insertion while detached from the bottom.
- Stop viewport compensation immediately during user drag/fling, then establish a fresh anchor after scrolling settles.
- Reduce persisted stream bursts from 512 characters/320 ms to 96 characters/90 ms and reveal them in adaptive token-sized micro-batches at the existing 30 Hz render cadence.
- Drain the final streaming backlog at the same cadence instead of dumping it when generation completes.
- Reuse the compiled code-fence matcher so smoother updates do not increase Markdown-regex setup work.

## 0.16.45

- Stable detached-chat viewport across tool insertions and working-card height changes.
- Smoother frame-aligned streaming and single-layer event fades.

## 0.16.41

- Smooth frame-paced chat auto-follow without per-token scroll jobs.
- Exact detached viewport freeze during generation; reaching the true bottom re-enables follow.
- Batched streaming Markdown updates and GPU-cheap consistent block fades.

# Changelog

## 0.16.29

- Replaced the predictive-back commit snap with a short render-layer completion phase.
- Faded the outgoing page only near the committed endpoint so the retained destination can become the sole visible layer before the atomic state swap.
- Preserved the 0.16.28 saveable-state and scroll-retention behavior.

## 0.16.26

- Restored the 0.16.19 UI and blur baseline.
- Increased the physical top reserve above chat messages.
- Replaced token-by-token animated scrolling with an explicit generation follow lock that releases on user scroll and re-engages only at the true bottom.
- Compensated active-response height changes while detached so streaming text, tools, and reasoning do not move the user's viewport upward.
- Unified streaming text, tool, reasoning, generated-block, and result appearance around one fade-only timing without animated remeasurement.

## 0.16.18

- Replaced reverse-layout oldest-message inference with a stable keyed header anchor inside the chat list.
- Made sticky-title collapse independent of paging indices and transient LazyColumn measurements, eliminating jumps and incorrect expanded states.
- Kept one fixed title with direct scroll-linked movement and no independent animation.

## 0.16.17

- Rebuilt the chat title as a single clipped, fixed collapsing header instead of a permanently full-height overlay.
- The header is fully expanded only at the oldest/top of the conversation, follows scroll position directly and linearly, and contracts to a compact toolbar without crossfading, independent animation, or floating over messages.
- Kept the model selector available in both expanded and compact states while clipping all header content to the live header height.

## 0.16.14

- Replaced Mermaid node-token regex parsing with a delimiter scanner, eliminating the Android 16 ICU `PatternSyntaxException` in native diagrams.
- Escaped Graphviz bracket patterns explicitly for Android's stricter regex engine.
- Added regression coverage for square, round, decision, and malformed Mermaid node delimiters.

## 0.16.13

- Corrected reverse-layout chat header collapse using the physical top content reserve; the title now expands only at the oldest/start of the chat and moves smoothly into the compact header toward the latest messages.
- Reworked Settings/Search/Sandbox/Terminal title motion around one persistent title and increased their top blur region from 64 dp to 88 dp without changing blur strength.
- Made Deep Research presentation immutable per response: only messages submitted with Deep Research enabled can show research state, and the roadmap/progress UI is rendered solely from explicit model-reported state rather than guessed tool counts.
- Added a task-specific research-state protocol and instructed models to report factual roadmap, progress, blocked steps, synthesis, and final report state.
- Replaced abrupt tool-round termination with a no-tools final synthesis pass, raised research tool capacity, and preserved gathered evidence when a model still refuses to finalize.
- Simplified web-search cards to the query and sources actually opened by the model.
- Made source/file pills smaller and shortened oversized labels; all links and reference pills now open an anchored preview with title, domain, description, destination, and explicit Open action.
- Disabled Android smart-selection on rendered Markdown links, fixing the Samsung/Android 16 `SmartSelectSprite` crash when tapping links inside tables.

## 0.16.12

- Rebuilt the chat header around one persistent title and model selector, fixing reverse-layout collapse, duplicate geometry, and the selector disappearing away from the beginning of a conversation.
- Shortened the top blur region to 64 dp throughout Chat, Search, Settings, and nested menus without reducing blur strength.
- Added a staged Deep Research roadmap and compact web-search cards that show the query and used source sites instead of raw tool details.
- Added tappable website/file reference pills and destination previews for all links.
- Treated Android stream interruption during intentional Ubuntu cancellation, timeout, or teardown as a normal shutdown instead of an app crash.

## 0.16.10

- Replaced the sparse high-radius blur with a dense seventeen-tap Gaussian pass to eliminate the visible grid pattern.
- Removed the full-width composer Surface and shadow; the input chrome now uses only a long feathered backdrop gradient.
- Replaced Material 3's crossfading chat title with one title that physically moves and scales into the collapsed header.

## 0.16.9

- Strengthened gradual blur and made the overlay itself fade smoothly.

## 0.16.8

- Fixed gradual interface blur and back navigation.

## 0.16.7

- Replaced the ineffective shared-layer blur toggle with a persistent per-surface backdrop layer, fixing deferred RenderEffect submission on Samsung/Android 16.
- Added blur overscan so the top bar and composer no longer retain sharp edge strips.
- Blur now has a visible baseline when enabled and increases smoothly with scrolling; tint opacity remains constant.
- Reduced chrome tint opacity so the blurred content remains visible, while preserving an opaque fallback when blur is disabled or unsupported.
- Added regression tests for clamped, monotonic blur progression.
- Bumped the debug package to version code 32.

## 0.15.0

- Rebuilt the composer around the compact Option B layout: tool controls sit above the message field, while the input row contains only Add, message entry, and Send/Stop.
- Removed the unexplained context progress strip and all controls below the input field.
- Kept only two persistent composer chips: a compact Think chip with an effort dropdown and a direct Search toggle. Files, photos, camera, Deep Research, Python, and Linux remain in the Add sheet.
- Shortened thinking labels to Min/Low/Med/High so the composer remains readable on narrow phones.
- Bumped the debug package to version code 22.

## 0.14.0

- Replaced the wide composer thinking controls with one compact persistent chip. Tapping the label toggles thinking; the arrow opens a Minimal/Low/Medium/High effort menu.
- Rebuilt Global Settings as a categorized home screen instead of a dense tab strip. Providers, new-chat defaults, automation, appearance, privacy, and about information now open as focused pages.
- Added persistent Follow-device, Light, and Dark theme modes while retaining Arbor, dynamic Material You, graphite, and AMOLED options.
- Simplified provider management: provider selection is separate from a compact connection summary, while endpoint, key, and custom headers stay in an edit sheet.
- Replaced the oversized model editor with a focused bottom sheet. Basic identity and token limits stay visible; capabilities use compact chips and optional pricing is collapsed by default.
- Improved model catalog readability with search, concise capability summaries, and cleaner navigation rows.
- Bumped the debug package to version code 21.

## 0.12.0

- Split Settings into **Chat**, **Global**, and **Providers** tabs, with current-chat controls kept separate from persistent defaults for future chats.
- Added persistent per-chat and new-chat-default thinking controls, including an enable switch and Minimal/Low/Medium/High effort selector mapped to OpenAI-compatible, Anthropic, and Gemini request formats.
- Simplified agent permissions to independent Web, Python, and Linux switches. Existing chats retain their own choices; every settings change also becomes the starting profile for newly created chats.
- Added Room migration 9→10 and immutable generation-snapshot fields for thinking state so queued, resumed, and retried work preserves the settings selected when it was submitted.
- Added native structured tool calls for OpenAI-compatible, Anthropic, and Gemini providers while retaining Arbor's fenced protocol as a compatibility fallback. Streaming tool arguments, provider-specific reasoning blocks, and multi-step tool results are preserved.
- Added safe local text extraction for DOCX, PPTX, and XLSX attachments, with archive-size, entry-count, XML-size, and path-safety bounds.
- Expanded protocol, settings inheritance, request snapshot, Office extraction, permission, and fragmented-stream regression tests.

## 0.11.1

- Added an independent Working-history token budget while preserving resumable partial state.
- Enforced the total context ceiling after Working-history and attachment accounting.
- Added explicit known/unknown cost accounting so unconfigured prices are not reported as free.
- Added Room migration 8→9, release-signing configuration, CI, instrumentation smoke coverage, and lint cleanups.

## 0.11.0

- Fixed the Android-only mini-app template-regex initializer crash reported from `MiniAppWidgetBlock.kt:101` by making both template delimiters explicit.
- Added durable generated-render recovery. Widget, chart, or diagram crashes automatically enable a persisted safe-rendering mode on the next launch; the offending source and chat remain intact, and full rendering can be retried from the crash dialog, placeholder card, or Settings without clearing app data.
- New conversations now remain in memory and do not enter Room or the sidebar until their first message or stored attachment. Repeated New-chat taps cannot create database spam, and the upgrade removes only legacy rows with no messages and no attachments.
- Expanded the optional Linux tool layer from Ubuntu-only to persisted Ubuntu 26.04, Debian 13, and Alpine 3.24.1 choices, with isolated root filesystems, pinned SHA-256 verification, apt/apk-aware preflight, exact approval fingerprints, already-installed detection, and no-change install blocking.
- Added view-model-owned Python and Linux runs which continue while navigating around Arbor, show an app-wide background status, warn after ten seconds, report elapsed time, enforce configurable hard deadlines, and expose cancellation. Python uses a cooperative stop marker; PRoot processes are forcibly terminated on cancellation.
- Agent Python/Linux tools now use bounded default deadlines, accept explicitly bounded longer deadlines, report timeout timing to the model, and forbid silent retry or direct apt/apk/pip/package-manager use outside the visible approval flow.
- Generalized Linux labels, package request fences, command linting, agent permissions, and durable package transactions while retaining older Ubuntu aliases for saved chats and model compatibility.

## 0.10.0

- Rebuilt provider onboarding around protocol-aware model discovery. Arbor validates credentials and endpoints, fetches searchable model lists from OpenAI-compatible, Anthropic, and Gemini APIs, registers every discovered model, keeps manual entry as a fallback, and can refresh an existing provider without recreating it.
- Added immutable request snapshots and an append-only per-call usage ledger so queued/resumed work retains its original endpoint, limits, capabilities, pricing, and billed usage even if conversation settings later change.
- Added recoverable streaming retries with connectivity constraints, cancellable provider calls, durable partial state, bounded error handling, deterministic same-chat queue positions, and cancellation of every same-chat worker during steering.
- Added a Room 7-to-8 migration which creates/backfills FTS, persists package transactions and generation usage, stores request snapshots, and advances context summaries with stable row cursors.
- Reworked context compression into bounded ordered batches and expanded context budgeting to reserve prompts, summaries, OCR, extracted file text, and complete recent request/answer groups.
- Replaced package-name-only pip checks with resolver dry-runs covering candidates and dependencies. pip and apt now compare the exact freshly simulated plan with the approved fingerprint, persist install state, prevent repeated auto-install loops, and recover interrupted continuations.
- Added a dedicated `send_file` agent tool. Workspace diffs no longer hoist unrelated files; each returned file or image appears at its exact response-timeline location with native preview, save, and share controls.
- Remastered raster viewing with clean inline previews, a full-screen black viewer, double-tap and button zoom, clamped pinch/pan, OCR overlays only when requested, and multi-page PDF navigation.
- Added first-use consent for live generated UI, an always-available local capability/risk review, an optional independent model security opinion, expiring Home-widget handoff state, and a separate confirmation before launcher pinning.
- Added calm Arbor, Material You, and graphite palettes with optional AMOLED surfaces; refreshed the launcher mark, provider/model switcher, attachment layout, package cards, and searchable result navigation.
- Ubuntu now inherits the active Android network's DNS servers when available instead of unconditionally bypassing VPN/private-DNS configuration.

## 0.9.2

- Replaced automatic diagnostics panels on AI-authored Markdown code with native, language-aware syntax coloring for Python, shell, Kotlin/Java, JavaScript/TypeScript, JSON, markup, SQL, YAML, and other common languages.
- Applied the same syntax coloring to Python and Ubuntu command panels inside ordered Working cards while keeping stdout, stderr, results, and files visually separate.
- Added a staggered fading token pulse while an assistant response is streaming, including a labeled animated empty-response state.
- Serialized conversation and automation setting writes against the latest database row so rapid toggles and text edits cannot overwrite one another with stale state.
- Persisted AMOLED mode across restarts, and made new chats inherit the active chat's model, agent-tool permissions (including Ubuntu), reasoning, context, and output settings.
- Replaced full-row operational chat updates with targeted leaf/title updates so generation cannot accidentally restore old settings.

## 0.9.1

- Separated conversation interaction from Android Home-screen widgets at both the fence and schema levels.
- Added `arbor-ui` for chat-only questions, requirement forms, configuration, previews, quizzes, and mini-apps; it never exposes launcher pinning.
- Changed Home eligibility to opt-in. Even `arbor-widget` content must explicitly declare `surface: "home"`, `surface: "both"`, or legacy `home: true` before the pin action can appear.
- Made `surface: "chat"` authoritative even if conflicting legacy Home metadata is present, and reject unknown surface values.
- Updated the agent contract to prohibit marking clarifying questions, implementation questionnaires, ordinary answer controls, or requested in-app screens as Home-screen widgets.
- Added regression tests for chat-only defaults, explicit Home/both eligibility, conflict handling, and invalid surfaces.
- Fixed streaming auto-scroll so returning to the latest message re-enables following, while manual browsing remains undisturbed; added a floating go-to-latest button whenever the chat is detached from the bottom.

## 0.9.0

- Replaced example-specific widget expansion with a general declarative native mini-app runtime shared by chat and Android Home-screen widgets.
- Added up to eight navigable screens, forty-eight persistent state values, safe `{{value}}`/`{{=expression}}` templates, numeric formulas, conditional visibility, and ordered action chains.
- Added native text, metric, input, slider, toggle, choice, button-grid, progress, list, table, chart, timer, divider, and spacer components.
- Added set, add, multiply, toggle, append, backspace, evaluate, navigate, reset, refresh, submit, and timer actions with per-action conditions and immediate chaining semantics.
- Added dynamically assembled Home-screen `RemoteViews` rows, persistent state actions, slider/toggle/choice controls, list-item actions, multi-screen navigation, progress, and Canvas-rendered bar/line/scatter/pie/donut charts.
- Added saved Home-screen submissions, row-count and schema-size bounds, live JSON state binding, background refresh integration, and Home-screen control fallbacks for components that launchers cannot edit directly.
- Updated the model contract to treat calculator, stocks, and prayer times as examples rather than the extent of programmable widgets.
- Added validation tests for multi-screen apps, chained state transitions, navigation, conditions, templates, unknown components, and invalid screen targets.

## 0.8.0

- Replaced the four-button Home-widget approximation with purpose-built native layouts for calculator, live metrics/stocks, and ordered schedule/prayer-time mini-apps.
- Added a complete persistent Home-screen calculator keypad with clear, backspace, sign, percentage, decimal, operators, and safe expression evaluation without opening Arbor.
- Added public HTTPS JSON data sources with explicit dot/array-path bindings, 1 MB response limits, redirect blocking, private/local-address rejection at DNS resolution, cached last-good values, visible refresh state, and manual refresh.
- Added per-widget WorkManager refresh jobs with Android's 15-minute periodic floor, network constraints, retryable manual refresh, lifecycle cleanup, and immediate initial loading.
- Added live stock/metric cards in chat with source disclosure, refresh, snapshot submission, numeric formatting, and matching pin-to-Home behavior.
- Added ordered native schedule and prayer-time cards with timezones, next-event countdowns, optional live time bindings, fallbacks, manual refresh, and next-event highlighting.
- Expanded the generated-widget contract and parser validation while continuing to reject JavaScript, HTML, arbitrary code, malformed times, unsafe URLs, and malformed JSON paths.
- Raised the generated widget's launcher target size so complex keypad and schedule layouts have usable touch targets.

## 0.7.0

- Replaced the edge-list Mermaid placeholder with a native flow/state/sequence diagram canvas supporting labels, chained edges, direction, dashed messages, scrolling, and expanded previews.
- Added native bar, line, area, scatter, pie, and donut charts from structured `arbor-chart` JSON while retaining simple `label: value` compatibility.
- Raster image attachments now render as full-width, uncropped inline previews; tapping opens a near-full-screen pinch-zoom and pan preview with correctly transformed OCR highlights.
- Returned files are inserted as explicit visible timeline events at their actual tool-output position instead of being hoisted above the assistant's entire response. Existing saved tool outputs are positioned from their attachment timestamps.
- AI-created images no longer receive unnecessary OCR, and old assistant-image OCR metadata is cleaned on upgrade. User photos always open as normal originals; OCR is an optional hidden overlay with a clear fallback warning for text-only models.
- Expanded generated chat widgets with calculators, converters, counters, ratings, progress, and programmable forms using safe numeric expressions and native controls.
- Added a generic Android Home-screen AppWidget provider. Eligible AI-generated widget definitions can request launcher pinning and expose up to four safe state actions without loading code or HTML.
- Expanded workspace-output MIME detection for generated documents, archives, structured text, SVG, and modern image formats.
- Updated the agent contract for downloadable workspace files, native diagram/chart definitions, programmable widget schemas, and Home-screen actions.

## 0.6.1

- Fixed Android SELinux hard-link failures in `dpkg` by enabling PRoot's `--link2symlink` compatibility extension.
- Ubuntu setup now verifies real write and link operations under `/var/lib/dpkg` before declaring the runtime ready.
- apt installation resumes interrupted `dpkg` configuration and repairs dependencies before applying the approved request.
- Auto-approved package cards now show a live transaction state and never display success until installation really exits successfully.
- Large apt dependency plans are summarized and collapsed by default instead of flooding the screen.
- Added CPython syntax linting, Ubuntu `bash -n`, JSON parsing, delimiter checks, and common style diagnostics.
- Agent Python and shell tools are lint-gated before execution.
- Ordinary fenced Markdown code blocks display lint status and diagnostics; runnable blocks disable execution on lint errors.
- Working steps now show code/commands in dedicated panels and split result, stdout, stderr, exit status, timing, and changed files.
- Tool workspace execution uses the same separated native output cards.

## 0.6.0

- Replaced the empty built-in provider catalog UI with an explicit Add provider workflow.
- New providers have a user-defined name, protocol type, base URL, key policy, API key, custom headers, and initial model.
- Existing providers with saved keys migrate into the registered-provider list; unused templates stay hidden.
- Keyless endpoints must be explicitly registered and marked key-optional, so the bundled Ollama template no longer leaks into selectors by default.
- Replaced wallpaper-derived dynamic colors with a consistent graphite-and-blue Material palette in light, dark, and AMOLED modes.
- Package approval and workspace cards now use neutral elevated surfaces instead of highly saturated tertiary colors.
- Reduced excessive corner rounding for a cleaner, less inflated interface.

## 0.5.1

- Auxiliary model selectors now show only enabled providers which are actually usable: a saved API key is required except for keyless local Ollama.
- Chat naming, context compression, and package-approval model choices are validated against registered models.
- Removing credentials automatically falls back to local naming/compression and user-confirmed package installation instead of leaving a broken model selection.

## 0.5.0

- Added an optional, checksum-verified Ubuntu Base 26.04 tooling layer powered by an APK-embedded Termux PRoot launcher for arm64-v8a and x86_64.
- Mounted each conversation workspace at `/workspace`, exposed Ubuntu shell execution to the agent and code-block Run action, and returned created files through the existing native attachment flow.
- Added native Ubuntu lifecycle management with download progress, self-test, apt-index setup, installed size, retry, refresh, and removal.
- Added one shared pip/apt package preflight and approval system with installed-version detection, apt dependency simulation, candidate versions, download/disk summaries, and disabled no-op installs.
- Added Ask every time, Trusted list, Approval model, and Auto-approve policies, separately selected approval provider/model, and an explicit advanced-source restriction switch.
- Added `ubuntu-packages` chat requests and automatic answer continuation after a successful approved pip or apt install.
- Added exact PRoot/talloc/libandroid-shmem source archives, build recipes, hashes, notices, and license texts alongside the redistributable source.

## 0.4.0

- Added projects, pinned chats, archive browsing, and native long-press management for rename, move, archive, pin, and confirmed deletion.
- Added separate `Off`, `Local • no API call`, and selected-model policies for chat naming and context compression.
- Added persistent incremental context summaries which preserve excluded requirements, files, tool state, and unresolved work while keeping the newest/resumable messages verbatim.
- Added an editable per-provider model catalog covering IDs, names, context/output limits, pricing, and capability flags.
- Reworked Python as a managed per-chat environment with serialized activation, persistent variables, deadlines, transactional verified installs, package inventory/removal, repair, and session reset.
- Added public-page fetching after web search with local/private-address blocking.
- Replaced the unreliable send dropdown with a haptic native bottom sheet for stop, steer, queue, and concurrent-turn actions.
- Made steering retain the interrupted Working state even beyond the normal pair limit and expose it across provider formats.
- Exposed saved edited/retried branches through an in-app history sheet.
- Added Save a copy to file cards, refreshed Material 3 shapes/hierarchy, and replaced the launcher/themed icon.
- Stopped default-catalog startup from overwriting user-edited provider endpoints and model metadata.

## 0.3.2

- Added runtime loading for native dependency wheels under `.packages/chaquopy/lib`, including OpenBLAS, libgfortran, and libc++.
- Native libraries are preloaded in dependency order before verification and every Python run.
- Import errors are reduced to the actionable cause instead of displaying NumPy's full troubleshooting essay.
- Working visibility now controls expansion only: cards are always retained and manually openable.

## 0.3.1

- Refreshed the embedded interpreter's finder caches after runtime installation.
- Added post-install import verification and distribution-to-import-name reporting, including `Pillow → PIL`-style mappings.
- Replaced aggregate Working display storage with a real ordered event timeline.
- Working cards now combine only adjacent reasoning/search/Python events; ordinary assistant text always splits groups.

## 0.3.0

- Fixed Android runtime package installation by selecting pip's Chaquopy-compatible metadata backend.
- Added direct agent Python and DuckDuckGo HTML search loops with per-chat controls.
- Added unified animated Working traces and three reasoning visibility modes.
- Fixed send-button long press and added explicit stop, queue, steer, and send-now actions.
- Added native interactive choice, checklist, and slider widgets.
- Added non-destructive message editing and assistant retry with retained history.
- Added automatic attachment cards for agent-created Python files.

## 0.2.0

- Replaced handwritten send-path SQL with Room's typed suspend transaction API.
- Declared the data-sync foreground-service type for Android 10+ generation workers.
- Added visible action errors and a local next-launch crash report with copy support.
- Added a version 1-to-2 database migration which preserves existing chats and secrets.
- Added permission-gated per-chat Python package installation and agent-readable installation events.
- Added evolving automatic chat titles and manual title regeneration from newer messages.
- Expanded search to conversation titles as well as message content/reasoning.
- Added responding, unread-count, and interrupted/error indicators to the conversation sidebar.

## 0.1.0

- Initial native Android build.

## 0.16.19
- Chat header now uses the same Material scroll behavior as Settings; fixed compact settings-title alignment.
