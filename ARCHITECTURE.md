# Architecture

Arbor uses a single-activity Compose UI with an offline-first data layer.

| Area | Implementation |
|---|---|
| UI | Jetpack Compose, Material 3, adaptive navigation |
| Long chats | Paging 3 over Room; stable search-position jumps; reverse-layout lazy list |
| Search | SQLite FTS5 with insert/update/delete triggers |
| Secrets | Android Keystore + EncryptedSharedPreferences |
| Chat database | Room over SQLCipher |
| Background generation | One unique WorkManager foreground job per assistant node |
| Network | OkHttp streaming SSE adapters |
| Providers | OpenAI-compatible, Anthropic, Gemini |
| Context | Complete newest request/answer groups bounded by pair, token, prompt, summary, attachment, and OCR limits; optional hybrid preflight counting uses provider-exact or local-family tiers and interrupted state is retained for resume |
| Attachments | App-private files, FileProvider sharing, native previews, ML Kit OCR |
| Python | Chaquopy Python 3.12 with per-conversation private workspaces |
| Ubuntu tools | Optional Canonical Ubuntu Base 26.04 rootfs under APK-embedded PRoot; current chat bind-mounted at `/workspace` |
| Package policy | Shared pip/apt preflight; ask, trusted-list, model-review, or auto-approve decision modes |
| Agent tools | Native OpenAI-compatible/Anthropic/Gemini calls with fenced fallback; direct Python, Linux commands, DuckDuckGo HTML search, page fetching, and explicit ordered file sending; encrypted tool traces |
| Revisions | Active-path paging over retained DAG rows; edits and retries supersede rather than delete history |
| Response ordering | Append-only per-message event timeline; only adjacent non-text events form a Working group |


Everyday chat configuration is composer-owned: thinking/effort is visible beside the input, and the `+` sheet owns attachments, camera capture, Web search, Deep Research, Python, and Linux. Global defaults, automation policy, app appearance/privacy, and provider management live only in the navigation-drawer Settings screen. The chat overflow exposes a compact advanced sheet for context, output, compression, Working visibility, and system-prompt controls.

Deep Research is a persisted per-conversation agent mode. It forces Web availability, adds a research contract to the assembled system context, and raises the bounded tool-round ceiling from six to fourteen. It does not bypass private-address blocking, package approval, provider limits, or the normal encrypted timeline/snapshot machinery.

Optional hybrid token counting runs before provider submission. Anthropic and Gemini count endpoints are treated as exact for the submitted provider request. OpenAI-compatible and recognized model families use local family-aware estimates, then the generic estimator. Count failures cannot block a request; provider-returned usage supersedes the preflight value in the immutable usage ledger.

Messages contain stable node IDs, parent IDs, and branch IDs. A steering request cancels the current worker, preserves its partial text/reasoning as an interrupted assistant node, then appends the steer as a child user node. Resume reuses the interrupted assistant state; DeepSeek uses prefix continuation where supported, while other providers use a conservative continuation prompt fallback.

An assistant may request one Arbor tool at a time. The worker removes the protocol fence from visible content, executes the enabled tool, records status/input/output on the same assistant node, and feeds the untrusted result back to the model. Up to six consecutive calls are allowed normally; Deep Research permits up to fourteen bounded rounds. Python and Ubuntu steps report changed paths but do not attach them automatically; the assistant must invoke `send_file` for each file it intends to return, and Arbor inserts that card as an ordered timeline event.

Text, reasoning, search, Python, and Ubuntu events are appended to one ordered timeline as they occur. The renderer groups consecutive non-text events, but a text event always closes the current Working group. Legacy responses retain their older aggregate reasoning representation because their original interleaving cannot be reconstructed safely.

Android 10+ blocks `execve` of downloaded app-data binaries, so PRoot and its loader/dependencies are packaged as extracted APK native libraries. The Ubuntu rootfs remains data, is downloaded from Canonical, and is verified before Python's safe tar extractor activates it. PRoot then loads rootfs executables while preserving Android's app UID and kernel restrictions.

Recoverable transport failures persist partial output and retry with WorkManager backoff up to the configured attempt ceiling. Each provider round and completed tool event is durably identified so a resumed worker does not silently duplicate a recorded side effect. Multiple conversations use independent work names and may stream concurrently; steering cancels every worker tagged for that conversation before appending the replacement branch.

Every provider round appends its own usage row. Provider-reported token usage replaces heuristic estimates when returned, and chat totals are derived across the ledger so partial and resumed calls remain countable. Cost is stored in integer micro-dollars using the immutable request snapshot's cached-input, uncached-input, and output rates.
