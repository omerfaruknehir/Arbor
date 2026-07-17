# Arbor 0.10.0 audit resolution

This file records how the 2026-07-16 Arbor 0.9.2 source audit was handled. It distinguishes implemented repairs from boundaries which cannot honestly be declared production-verified without a real Android test matrix.

| Audit area | 0.10.0 disposition |
|---|---|
| Request model/endpoint drift | Fixed with immutable serialized generation snapshots. |
| Network cancellation and steering | Provider calls are coroutine-cancellable; steering cancels every same-chat worker. Python's in-process native-extension kill boundary remains documented. |
| Recoverable partial streams | Fixed with durable interrupted state, bounded retry attempts, connectivity constraints, and WorkManager backoff. |
| Lost usage on partial/resumed calls | Fixed with an append-only per-provider-round usage ledger. |
| Compression truncation/cursor skips | Fixed with bounded chronological batches and stable `(createdAt,rowId)` cursors. |
| Missing upgraded-database FTS | Fixed in migration 7→8 with table/trigger creation and backfill. |
| Python process isolation | Not claimed. Import/install behavior is transactional and repaired, but Python still runs under Arbor's app process and UID. |
| pip review did not resolve dependencies | Fixed with resolver reports, exact plan fingerprints, import-health repair detection, pinned candidates, staging verification, and rollback. Android-wheel availability remains an ecosystem limit. |
| Queue/concurrency races | Fixed with monotonic positions, Room transactions, all-active-stream queries, and same-chat cancellation tags. |
| Attachment memory/storage | Fixed with file/chat/app quotas, free-space checks, bounded native encoding/OCR/context, cleanup, and streamed/downsampled handling where applicable. |
| Tool durability and file placement | Persisted timeline/tool state and duplicate guards added; permission is rechecked before side effects. `send_file` now controls exact file-card placement. Native API tool calling is still a future compatibility enhancement. |
| Cleartext/key/error leakage | Remote HTTPS enforced, Gemini key moved to a header, error bodies bounded, crash data redacted, and backups disabled. |
| Provider onboarding/model discovery | Rebuilt with named registered providers, connection/catalog fetching, pagination, refresh, keyless-local support, removal, and filtered selectors. |
| Context budgeting/finish states | Prompt/summary/files/OCR are reserved, historical resume is capped, and length stops remain resumable. |
| Lifecycle/process recreation | Activity ViewModel ownership, saved chat/navigation/draft state, staged attachments, and unified share/deep-link selection repaired. |
| Package continuation/recomposition | Durable package transaction states prevent repeated automatic installs and recover answer continuation. |
| Ubuntu integrity/DNS | Rootfs marker and tool validation, free-space checks, exact apt plan comparison, and active Android DNS inheritance added. Rootfs packages remain global to the app. |
| Web fetch/SSRF | HTTPS-only public fetch, bounded response streaming, redirect checks, DNS/private-address validation, and rebinding checks added. |
| Files/OCR/PDF | Ordered file events, clean image display, optional OCR, clear truncation metadata, and multi-page PDF navigation added. Office preview remains format-dependent. |
| Rendering/search/scroll | Persistence/notification throttling increased, finalized-only FTS updates added, match-position paging added, syntax coloring retained, and follow/re-stick/go-bottom behavior repaired. |
| Generated-widget privacy | First-use live-network consent, local security manifest, optional second opinion, pin review, expiry, and strict chat/Home separation added. |
| Palette/settings reset | Persisted palette/AMOLED and serialized settings writes added; provider/model selectors no longer destructively reset unavailable saved selections. |

## Verification still required on Android

Room migration instrumentation, configuration/process death, foreground notification behavior, real provider streaming/rate limits, Chaquopy native packages, PRoot/apt, large-image memory pressure, OCR/PDF rendering, FileProvider sharing, accessibility, and Home-widget pin/update/reboot behavior require representative arm64 devices and x86_64 emulators. These are acceptance gates for a production claim, not silent omissions.
