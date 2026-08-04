## 0.23.7 — 2026-08-04

- Replace overlapping setup transitions with a real opaque HorizontalPager that supports direct left/right swiping.
- Remove setup fades and scales so each page and its actions move as one plain slide.

## 0.23.6 — 2026-08-04

- Repair setup navigation animations by removing overlapping kept-alive wizard pages and animating step actions and progress coherently.
- Make Skip for now preserve the unfinished step and expose Finish setup in Settings.
- Defer setup after restoring app settings while retaining provider reconnection as a resumable step.

## 0.23.5 — 2026-08-04

- Keep predictive-back pages fully opaque, reduce the excessive page travel, and remove the short gesture dead zone while a prior page transition is settling.
- Show the exact bundled Xylune core prompt as selectable, non-editable text, including its revision and a clear distinction from request-specific runtime layers.
- Rewrite the bilingual privacy notice around Xylune's actual local/direct architecture, worldwide use, data the maintainer can genuinely access, and mandatory-rights-only response obligations.
- Add bilingual Terms of Use that separate Xylune from third-party AI providers, reject warranties and support SLAs where lawful, and preserve non-waivable consumer rights.
- Retain the six-file canonical GitHub release asset count and upload order introduced in 0.23.4.

## 0.23.2 — 2026-08-04

- Repair cloud restore from OneDrive, Dropbox, WebDAV/Nextcloud, and S3 by exposing only Xylune's private downloaded-backup cache through its non-exported FileProvider.
- Replace every launcher, themed, dynamic-palette, splash, About, license, widget, and notification Xylune mark with the approved X-and-leaf artwork derived from `branding/xylune-logo.svg`.
- Normalize every launcher variant to the same 108 × 108 viewport and identical foreground geometry, including the Android 12+ dynamic-color override, eliminating intermittent icon-size changes.
- Use the supplied current Google Drive, Microsoft OneDrive, Dropbox, and Nextcloud service marks in cloud restore while retaining a neutral storage symbol for S3-compatible services.

## 0.23.1 — 2026-08-03

- Publish bilingual GitHub Pages privacy, KVKK disclosure, terms, and data-deletion pages; standardize public releases on `app.xylune.chat` and document the exact Google and Microsoft signing identities.

- Complete direct cloud backup providers for OneDrive App Folder, Dropbox App Folder, WebDAV/Nextcloud, and S3-compatible storage while retaining Google Drive app-data and Android's scoped folder picker.
- Use OAuth Authorization Code with PKCE for OneDrive and Dropbox, encrypted local credential storage, HTTPS-only direct endpoints, least-privilege app-folder scopes, paginated backup browsing, resumable uploads, and confirmed deletion.
- Support first-run browsing, preview, and restore across every cloud provider, including multipart S3 uploads for Linux-inclusive backups; keep cloud credentials and sessions excluded from portable archives.
- Harden cloud transport by constraining authenticated WebDAV URLs to the configured HTTPS folder, parsing S3 continuation tokens correctly, rejecting repeated pagination cursors, removing the fixed whole-transfer deadline, and allowing pending OAuth sign-in to be cancelled.

## 0.23.0 — 2026-08-03

- Rebrand the complete application identity to Xylune, including the Android namespace, application ID, source packages, storage and transfer formats, widgets, native tools, release assets, documentation, and build metadata.
- Temporarily retain Arbor's existing A-shaped icon and repository banner while the Xylune visual mark is redesigned and approved; all non-visual product and internal identity changes remain part of this release.
- Rename internal protocols and identifiers without legacy compatibility because the project has no deployed user base, then validate the release build, lint, unit tests, instrumentation compilation, and offline license catalog.

## 0.20.35 — 2026-08-02

- Continue output-limited responses automatically for up to three additional segments, while stopping safely when a provider reports a limit without making progress.
- Make Resume honor the chat's current context, working-history, output-token, and token-counting limits instead of replaying the immutable limits captured when the response first started.
- Keep provider prefix continuation anchored to the exact partial assistant text; move Xylune's saved tool activity into a separate context item so hidden working metadata cannot become the continuation target.

## 0.20.30

- Repair widget-schema guidance, DeepSeek DSML tool-call handling, and collapsed chat-title action spacing.

## 0.20.29

- Added `compile_widget` as a first-class native model tool. Widget drafts stay inside tool calls until the compiler returns success.
- Compiler failures now return trusted structured phase/path/message diagnostics directly to the active model so it can revise and retry in the same response.
- Successful compiler results instruct the model to emit the exact tested source unchanged; Xylune's existing post-generation compiler remains a safety fallback.
- Added dedicated widget-compiler activity labels and prevented internal compiler results from being marked as untrusted external data.

# Changelog

## 0.20.28 — 2026-08-01

- Compile every AI-generated Home widget before display using typed parsing, capability validation, action execution, public HTTP JSON preflight, binding verification, and representative launcher rendering.
- Feed compile/runtime/network/layout diagnostics back into the bounded auxiliary-model repair loop and show only a candidate that passes; apply the same gate to manual edits.
- Safely follow declared HTTPS redirects, seed representative location/folder values to avoid false HTTP 400s, support `{{urlencode:key}}`, and reject deterministic endpoint or JSON-shape failures before pinning.
- Use real device density/font scale, larger typography and touch targets, and compact/expanded plus post-action render checks to prevent tiny, clipped, cramped, or empty widgets.

## 0.20.27 — 2026-08-01

- Preserve Home-widget capability awareness across follow-up messages with an explicit always-on model manifest and recent-conversation schema selection.
- Add an interactive install preview, clearer per-widget grant progress, grouped network approval, improved pin feedback, dedicated refresh controls, and richer launcher status.
- Remove duplicated bitmap actions and expose native button, toggle, choice, and list-item actions in launcher widgets.

## 0.20.26 — 2026-07-31

- Replace category-based generated widgets and the `mini_app` compatibility layer with separate `xylune-snippet/1` and `xylune-widget/1` program surfaces.
- Add a general component-tree and bounded action runtime for AI-composed snippets and Android Home-screen widgets, including custom styling, persistent state, canvas rendering, and live data bindings.
- Add per-widget capability manifests and grants for exact HTTPS origins, approximate or precise location, one selected document-provider folder, and scheduled refresh.
- Rebuild Google Drive backup as a connected-account flow with account selection, silent reuse, backup browsing, preview/restore, switching, disconnect/revoke, progress, and inline diagnostics.
- Move portable chat sharing to a dedicated top-bar button immediately before the chat overflow menu.
- Remove all legacy widget parsers, specialized widget layouts, aliases, storage migration, and backward compatibility.

## 0.20.25 — 2026-07-31

- Add local and least-privilege cloud restore actions directly to the first setup page, including Google Drive app storage and one explicitly selected document-provider folder.
- Let portable backups include theme/UI settings, new-chat defaults, developer settings, provider/model configuration, projects, system-prompt profiles, automation policy, and selected Linux distribution.
- Remap project and prompt-profile links while importing chats, without overwriting existing chats.
- Keep API keys, OAuth sessions, provider authorization headers, database encryption keys, cloud grants, drafts, and transient navigation state out of portable settings.
- Continue setup at provider connection after restore so credentials can be reconnected deliberately.

## 0.20.24 — 2026-07-31

- Let portable backups include installed Ubuntu, Debian, and Alpine environments, preserving rootfs permissions, symbolic links, hard links, packages, and configuration.
- Add a persistent Android document-provider folder target for Google Drive, OneDrive, Dropbox, Nextcloud, USB, and local storage; Xylune receives access only to the folder the user explicitly selects.
- Add direct Google Drive app-data backup using only the non-sensitive `drive.appdata` scope and Drive's hidden Xylune-only `appDataFolder`.
- Enable Android/Google One app backup only for small non-secret preferences. Chats, attachments, encrypted database material, credentials, workspaces, and Linux root filesystems remain excluded.
- Keep passwords optional for every local and cloud portable backup target.

## 0.20.23 — 2026-07-31

- Replace the single, mostly useless model choice in API-provider setup with a searchable multi-select list. Every discovered model starts selected, and Xylune stores only the models left selected.
- Add Android-native cloud/file backups through the Storage Access Framework, including Google Drive, OneDrive, Dropbox, Nextcloud, USB, and local destinations exposed by the device.
- Make archive passwords genuinely optional. Unencrypted backups and chat files remain allowed with a prominent disclosure instead of an artificial password requirement.
- Add a portable `.xylunechat` format with configurable attachments, reasoning, tool traces, system prompts, and request metadata; safe fields remain excluded by default.
- Let Xylune open shared chat and backup files, show a content/privacy preview, unlock encrypted archives, import non-destructive copies, and immediately continue an imported chat.
- Keep API keys and OAuth sessions out of portable files by design.

## 0.20.22 — 2026-07-31

- Make setup immediately acknowledge configured API, ChatGPT, and local providers, with Continue and Manage providers actions instead of presenting the unconfigured state again.
- Separate the Linux default toggle from distribution installation, show the live install state, require an explicit distro choice/install action when Linux is enabled, and provide a Continue without Linux path.
- Make the final setup summary report the actual provider count and Linux installation state.
- Keep the Android Dynamic launcher alias multicolored by removing its monochrome override, while using live Android system accent resources for both the launcher artwork and in-app Xylune mark.

## 0.20.21 — 2026-07-31

- Keep setup as a dedicated first-run/resume flow and remove the Setup assistant destination from Settings.
- Persist the live setup pager page, fractional swipe offset, and independent vertical scroll position for every setup page before launcher-icon restarts.
- Restore the exact setup viewport after icon changes and after temporary Provider/Linux detours.
- Drive each setup progress segment directly from the pager's continuous swipe/animation position instead of jumping only when a page settles.

## 0.20.20 — 2026-07-31

- Replace the unreliable deferred launcher-alias workaround with an intentional stateful restart: synchronously save the active screen, selected chat, settings/setup page, chat and Settings scroll positions, per-chat drafts, and staged file attachments before switching the icon, then reopen Xylune through the selected launcher alias.
- Add a system-owned alarm fallback so One UI can tear down every Xylune process during the alias change without losing the relaunch.
- Persist composer text independently for every conversation; staged attachments remain durable in Xylune private storage and reappear with their matching chat draft.
- Make the Android splash and launcher handoff use the selected palette and launcher artwork. Dynamic now reads the live wallpaper-derived Android system accent and neutral resources.
- Rebuild setup as a horizontally swipeable pager with animated transitions, persisted step restoration, and resumable Provider/Linux detours.
- Add a permanent Setup assistant entry in Settings so setup can always be reopened.

## 0.20.19 — 2026-07-31

- Stop mutating launcher aliases while Xylune is visible; icon choices are now persisted and committed only after the app leaves the screen.
- Flush pending icon changes from both MainActivity.onStop and Android's UI-hidden callback, with the component mutation still isolated in the launcher process.
- Acknowledge a pending icon only after the requested alias was applied, preventing lost updates across process teardown.
- Update Appearance and onboarding copy to describe the safe deferred refresh behavior.

## 0.20.18 — 2026-07-31

- Render the drawer, onboarding, and Xylune license entry from the exact active launcher-icon drawable instead of a separately approximated logo.
- Move launcher alias mutation into an isolated `:launcher_icon` process so OEM package-manager behavior cannot tear down the foreground Xylune process.
- Give completed user messages a static full-source rendering path and show the complete plain-text fallback while Markdown parsing finishes, preventing prefix-only message bubbles while preserving the editable source.
- Add regression coverage for icon fidelity, isolated alias switching, dynamic license branding, and completed-message rendering.

## 0.20.17 — 2026-07-31

- Route launcher aliases through a transparent trampoline so changing the icon cannot close the active MainActivity task.
- Reuse the existing single-task app screen when any launcher alias is tapped and apply alias changes atomically on Android 13+.
- Render the onboarding and drawer Xylune marks from the active Material color scheme, including wallpaper-derived Dynamic Color.
- Add regression coverage for alias task isolation, no-kill flags, atomic switching, and removal of static in-app green marks.

## 0.20.16 — 2026-07-31

- Add an opt-in launcher-icon setting that follows Xylune, Dynamic, Graphite, Ocean, Violet, or Sunset while retaining the classic Xylune icon when disabled.
- Add polished adaptive icons and activity-alias switching with Android's `DONT_KILL_APP` flag.
- Fix Dynamic palette previews borrowing colors from the currently selected scheme; previews now use the device's actual wallpaper-derived Material palette.
- Replace single-color preview dots with accurate three-color swatches in onboarding and Appearance.
- Add launcher alias, manifest, setup, and preview regression coverage.

## 0.20.15 — 2026-07-31

- Replace the shallow first-run screen with a five-step setup assistant covering appearance, provider readiness, local Python, optional Linux tooling, and a final readiness summary.
- Add live color-palette selection during setup and expand Appearance to Xylune, Dynamic, Graphite, Ocean, Violet, and Sunset palettes.
- Add AMOLED selection to setup and keep every choice immediately previewable and editable later.
- Make Linux onboarding explicit about download, storage, verification, retry behavior, rootless execution, and /workspace preservation.
- Fix edge Back gestures dismissing popups on pointer-down or pointer cancellation.
- Make dialog Back close the keyboard first; a second Back dismisses the dialog, while outside taps can no longer destroy in-progress edits.

## 0.20.6

- Add a repository-owned `licenses/` catalog with one metadata record per bundled runtime component, local icons, useful descriptions, official source links, SPDX expressions where applicable, and complete checked-in license documents.
- Generate the in-app catalog deterministically from local paths only; no legal text or metadata is fetched from the network at build time or runtime.
- Fail the build when component metadata is malformed, referenced local files are missing, IDs are duplicated, paths escape the catalog, or an `implementation` dependency has no catalog coverage.
- Replace the duplicate web-only license and third-party notice rows in About Xylune with one searchable **Licenses & notices** destination that works fully offline.
- Add a component detail sheet with version, included Maven modules, attribution notes, official project link, and selectable full license text.
- Classify PRoot as GPL-2.0-or-later, keep talloc correctly classified as LGPL-3.0-or-later, and retain the GPL v3 companion text without mislabeling talloc itself as GPL.
- Keep Google ML Kit vendor terms distinct from Apache-2.0 documentation sample licensing.
- Publish a new app version from `main` through GitHub Actions, including its version tag, APK, AAB, instrumentation APK, checksums, and release notes; same-version commits safely skip publication.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, attachments, and Linux workspace data.

## 0.20.5

- Correct the packaged talloc 2.4.3 shared library's license classification from the retained Termux package-level GPL-3.0 label to the library's upstream LGPL-3.0-or-later license.
- Add the exact upstream LGPL v3 text to repository and APK assets while retaining the GPL v3 text required by the LGPL v3 distribution terms.
- Explain the distinction between the packaged library and GPL-only ancillary Python/test material in the corresponding source archive.
- Add a directly accessible Third-party notices entry to About Xylune.
- Add CI provenance checks for the talloc source hash, upstream license headers, mirrored notices, packaged ABI libraries, and shared-library boundary.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, attachments, and Linux workspace data.

## 0.20.4

- Replace the misleading hard-coded `SDK 26–35` About row with separate minimum and target Android rows derived from the installed app metadata.
- Show Android 8.0 / API 26 as an open-ended minimum instead of implying that newer Android releases are unsupported.
- Update Xylune's compile and target SDK from Android 15 / API 35 to Android 16 / API 36, while retaining Android 8.0 / API 26 as the minimum.
- Keep Android 17 / API 37 as a deliberate future compatibility migration because it changes local-network permissions and native dynamic-code loading behavior relevant to Xylune.
- Add a two-step first-run welcome and provider setup flow that reappears when the app has no usable provider, with a session-only option to explore first.
- Replace the generic empty chat with an actionable provider setup state and disable message controls until a usable provider is connected.
- Warn inside the per-chat Tools menu when Linux is not installed and link directly to its workspace manager.
- Consolidate Python and Linux management into a tabbed Tool workspace, remove distribution install/remove controls from the terminal, and remove the duplicate shell runner and duplicated new-chat tool toggles.
- Make the Linux terminal execution-only, with workspace management as its stable parent in Back navigation.
- Rewrite the README in clearer product language and document the new onboarding, Local tools path, Android 16 target, and release install flow.
- Add the Apache License 2.0 with its explicit patent grant and attribution requirements.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.

## 0.20.3

- Replace the high-radius bilinear-paired backdrop kernel with 15 evenly spaced direct Gaussian samples per axis, removing the visible bands and lattice pattern that appeared near maximum blur strength.
- Keep the physical-device-proven three-axis RuntimeShader/RenderEffect path, exact shared panel bounds, seamless tint feather, rounded/flat geometry, and continuous 0–100% control.
- Add a branded repository banner, a concise feature-led README, current build instructions, and direct release/issue links.
- Add tag-driven GitHub Actions release automation that verifies tests and lint, builds installable debug APK/AAB artifacts, and publishes them to GitHub Releases.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.

## 0.20.2

- Expand the Thinking, Search, and Tools pill row's horizontal gesture viewport to the full composer width while preserving the requested 36 dp initial alignment.
- Keep horizontal pill scrolling isolated from the message field and vertical chat gestures.
- Keep completed and failed Working steps compact instead of auto-expanding raw code, output, and diagnostics.
- Replace the oversized nested script-run card with a concise failure summary and compact Retry action.
- Hide raw tool inputs, outputs, source paths, and copyable diagnostics unless both Developer settings and the new Tool diagnostics toggle are enabled.
- Move Developer settings out of the main Settings categories and place its entry at the bottom of About Xylune.
- Rebuild About Xylune with creator, source, issue, version, build, package, Android SDK, runtime, ABI, and privacy information.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.

## 0.20.1

- Replace the invisible complete-frame blur replay with Xylune's physical-device-proven direct RenderEffect path from 0.17.8, adapted to the current top/bottom panel bounds, rounded/flat geometry, and symmetric softness.
- Remove the one-pixel join between the solid and feathered panel regions by drawing each soft tint as one continuous gradient.
- Make fully opaque tint explain that it hides background blur instead of presenting a working blur control with no visible result.
- Replace Material 3's vertical bar thumb with a circular Xylune thumb while keeping Material gesture, accessibility, keyboard, RTL, cancellation, and drawer-priority behavior.
- Restore Thinking effort as a continuous finger-tracked slider with visible level marks, boundary haptics, and a damped nearest-level spring only after release.
- Shift the Thinking, Search, and Tools pill row 12 dp left in the composer.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.

## 0.20.0

- Replace the custom magnetic slider engine with Material 3's maintained slider interaction model, preserving Xylune haptics, semantic progress, RTL behavior, and drawer-gesture priority while removing pointer, spring, and state synchronization races.
- Replace Thinking effort's misleading discrete slider with explicit named choices and descriptions.
- Split Appearance edge geometry into a clear **Rounded / Flat** choice and an independently labeled **Edge softness** control without changing stored settings or the Room schema.
- Make Working cards honor their visibility setting, name the current action, summarize steps and failures, auto-open active/error details, and keep completed steps compact.
- Give search, source reading, code execution, and Linux work consistent human-readable running, completed, and failed statuses.
- Make background generation obvious in the composer, with queued-message status, a separate **Stop** action, explicit send-to-queue behavior, and a visible **Queue / Steer / Separate turn** menu.
- Keep Thinking, Search, and Tools visible beside the message box, and reserve `+` for attachments, photos, and camera capture.
- Clear the existing UI lint backlog for full-window popup sizing, width measurement, primitive state, URI parsing, modifier ordering, and inspector metadata.
- Preserve package identity, debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.

## 0.19.15

- Replace Xylune's layered Material-slider wrapper with one custom slider engine that owns pointer tracking, magnetic hysteresis, velocity, haptics, spring settling, accessibility, external-state synchronization, and rendering.
- Remove the hidden Material Slider state machine that could override Xylune's release animation, duplicate ticks, jump values, or race preference updates.
- Draw every Xylune slider with the same 48 dp touch target, 16 dp rounded track, Material-style interior ticks, and 44 dp pill thumb; range endpoints are represented only by the track caps.
- Keep continuous controls completely free of snap behavior, make stepped controls and Thinking move continuously under the finger and spring only after release, and retain the bounded rounded-hard/flat-hard lane for Edge Smoothness.
- Add stable magnetic-well selection with hysteresis so adjacent anchors cannot flicker or fight around their midpoint, plus a single final value commit after the visual spring completes.
- Preserve slider priority over drawer gestures, live setting previews during drag, system-respecting haptics, RTL mapping, accessibility progress actions, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Replace the old source-string regression checks with behavioral coverage for anchor normalization, non-overlapping wells, monotonic force mapping, hysteresis, bounded edge snapping, raw-value restoration, release velocity, interior ticks, and the single-state-machine architecture.

## 0.19.14

- Replace RenderEffect-on-root blur composition with a complete-frame GraphicsLayer capture and two replays—normal plus native-Gaussian masked—so scrolling cannot expose partially invalidated blur tiles without reducing blur radius, strength, quality, or update frequency.
- Draw tint and diagnostic overlays after the filtered replay so overlay pixels stay sharp and do not enter the Gaussian source.
- Remove the temporary top- and bottom-panel height preferences and Appearance sliders; use a fixed 120 dp chat top panel and 100 dp top panels on Settings, Search, Sandbox, and Linux terminal screens.
- Make the composer blur use its measured height with a 120 dp minimum, so multiline prompts, chips, queues, and attachments expand the bottom panel and Scaffold inset together.
- Restore Material-sized snap dots inside the slider track, including endpoint anchors constrained within the rounded caps, for Edge Smoothness and Thinking.
- Add subtle rate-limited streaming chunk haptics plus a distinct completion pulse without vibrating for every token.
- Preserve native Gaussian blur strength/quality, hard-edge anchor semantics, free post-flat softness values, drawer momentum, slider priority, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add regression coverage for complete-frame blur replay, fixed panel heights, measured composer expansion, in-track ticks, removed height settings, and streaming haptics.

## 0.19.13

- Force native Gaussian backdrop rendering through a complete offscreen source layer and widen the pixel-locked mask transition to reduce the remaining boundary shimmer during fast scrolling.
- Add a persistent 96–320 dp bottom-panel height setting, apply it live to the chat composer, and show an exact-height native-blur preview during slider movement.
- Strengthen slider magnetic wells while capping live pull short of the anchor so post-release snapping has visible travel through a lower-stiffness damped spring instead of appearing instantaneous.
- Carry a bounded fraction of drawer release velocity into the open/close spring so fast pulls preserve momentum while slow drags retain positional-threshold behavior.
- Move the collapsed chat model tag 5 dp closer to the title while preserving the existing expanded 108 dp baseline.
- Preserve slider priority over drawer gestures, native Gaussian composition, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add regression coverage for bottom-panel persistence/live preview, offscreen blur capture, stronger non-teleporting magnetism, drawer momentum, and collapsed header spacing.

## 0.19.12

- Separate the slider's local visual spring from preference persistence so post-release snapping animates smoothly and commits only the final endpoint instead of writing every animation frame.
- Increase magnetic attraction and use a lower-stiffness damped spring for a stronger but visibly continuous settle after release.
- Render Edge Smoothness and Thinking anchors with Material's normal active/inactive tick colors, tiny in-track marks, and no custom endpoint dots.
- Dismiss the Thinking effort popup when tapping outside it while preserving slider interaction inside the popup.
- Pixel-lock shared blur/tint panel bounds and antialias the hard mask boundary over one physical pixel to prevent edge coverage flicker while scrolling.
- Remove the normal white top/bottom boundary highlight and add an opt-in Developer Settings diagnostic guide with bright-red color and adjustable 1–8 dp thickness.
- Preserve native Gaussian composition, the two hard-edge anchor lane, free post-flat softness values, slider priority over drawer gestures, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add regression coverage for Material-style interior ticks, local-only spring frames, outside-tap popup dismissal, pixel-stable blur boundaries, and debug-only boundary guides.

## 0.19.11

- Restrict edge-softness snapping to the physical lane between rounded-hard 0% and flat-hard 0%; values after the flat anchor, including the first 0–5% of real feathering, never snap back to **Hard edges**.
- Always settle releases inside that bounded lane to whichever hard-edge endpoint is closer, with stronger live spring attraction and a firmer damped release.
- Move explicit snap indicators into the Material slider track so they look like normal built-in tick marks instead of dots floating over the whole control.
- Remove arbitrary 25% snapping from blur, overlay opacity, top-panel height, and performance-overlay opacity controls; keep snapping only for real discrete choices and the intentional edge geometry anchors.
- Clamp release velocity before spring animation and use the actual active magnetic anchor for haptic state, reducing thumb oscillation, duplicate ticks, and release glitches.
- Preserve Thinking as a continuous drag with intentional nearest-effort settling only after release, plus slider priority over drawer gestures.
- Add regression coverage for bounded edge snapping, free post-anchor softness, in-track ticks, and removal of arbitrary continuous-slider anchors.

## 0.19.10

- Replace weak release-only slider snapping with a visible spring well while dragging and a damped spring settle animation on release.
- Increase snap capture tolerance while preserving velocity-aware behavior, so slow precise drags settle reliably and fast flicks remain easier to pass through.
- Draw tick dots at every explicit Xylune snap point, including the rounded-hard and flat-hard edge anchors and all Thinking effort choices.
- Simplify the edge-softness readout to **Hard edges** across the complete two-anchor 0% lane; numeric percentages appear only after actual edge feathering begins.
- Keep the Thinking thumb continuous during drag, then spring it to the nearest effort only after release instead of teleporting between values.
- Preserve slider priority over drawer gestures, native Gaussian blur, configurable panel height, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add regression coverage for spring attraction, stronger release capture, anchor-dot placement, hard-edge labeling, and Thinking release behavior.

## 0.19.9

- Correct the native Gaussian compositor so blurred pixels replace the original panel region instead of visually accumulating as bloom, with complementary original/blur branches combined exactly once.
- Redesign edge softness as two distinct 0% anchors: rounded 0%, a continuous unsnapped shape-transition lane, flat 0%, then a continuous 0–100% feather range.
- Migrate existing edge-softness settings so prior nonzero softness retains the same visual feather amount in the new post-flat range.
- Remove live value warping from Xylune sliders by default; retain small release-only anchors and tactile proximity feedback without thumb oscillation or discontinuous jumps.
- Keep the Thinking slider physically continuous while its menu is open and select the nearest effort on release without snapping or resetting the thumb to integer values.
- Preserve slider priority over drawer gestures, configurable panel height, haptics, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add regression coverage for the two-zero-anchor mapping, preference-preserving control conversion, complementary Gaussian composition, and continuous Thinking slider state.

## 0.19.8

- Repair the native Gaussian backdrop renderer by replacing RuntimeShader child-image filter chaining with independent native Gaussian, color-filter, alpha-mask, and source-over effects for each panel.
- Preserve pattern-free high-radius blur while avoiding black, stale, or missing output on affected Android GPU/Skia paths.
- Keep blur, tint, feather, rounded corners, and highlights on the exact shared root-coordinate panel bounds introduced in 0.19.7.
- Give Xylune sliders horizontal gesture priority over the navigation drawer, including sliders near the left edge of the Settings root.
- Preserve the configurable 64–240 dp top-panel height, magnetic snapping, haptics, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add unit coverage for the native effect graph, color matrix, independent mask path, and slider gesture ownership, plus an Android instrumentation construction test for both panel edges.

## 0.19.7

- Replace the sparse multi-direction blur kernel with one native Gaussian blur and an exact panel mask, removing high-strength directional grids, repeated sampling bands, and lattice patterns.
- Move blur masking, tint, edge feather, and highlight into one shared root-coordinate geometry so the collapsing top bar cannot misalign blur and overlay regions.
- Add a persistent 64–240 dp top-panel height control for temporary visual tuning across chat, search, settings, sandbox, and terminal screens.
- Enable pull-to-open drawer gestures on the Settings root while preserving nested Settings-page Back gesture priority.
- Preserve the 0–100% blur/overlay controls, smooth magnetic sliders, haptics, package identity, signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, and attachments.
- Add regression coverage for native blur composition, pattern-prone shader removal, shared geometry, top-panel preference compatibility, and Settings drawer gestures.

## 0.19.6

- Restore the proven Xylune 0.17.8 three-direction AGSL backdrop blur and remove the later capture/composite path which could produce stale frames, black backgrounds, hard strength jumps, and block artifacts.
- Retain the current exact blur, overlay, edge-softness, merge-distance, saturation, contrast, brightness, and edge-highlight controls, including rounded exact-zero edge-softness behavior.
- Replace hard slider snapping with a continuous magnetic force curve, a tiny exact-settle core, and velocity-aware release behavior so fast flicks stay free while slow drags settle confidently.
- Add centralized system-respecting haptic feedback for sliders, drawer gestures, message actions, branch navigation, settings, sidebar navigation, composer controls, toggles, and confirmations.
- Add regression coverage for restored blur architecture, continuous stored values, magnetic attraction, release capture radii, discrete slider ticks, and haptic integration.
- Defer settings-screen drawer gestures to 0.19.7 because the complete 0.19.6 build had already passed when that request was added.

## 0.19.5

- Restore official OpenAI image models to the normal provider/model picker even when `/models` omits them, and repair existing catalog rows without changing Room schema or migrations.
- Replace the generic image-generation capability checkbox with automatic preset transport selection and a compact Chat/Image generation request-type selector only for truly custom OpenAI-compatible providers.
- Route official GPT Image models through `/images/generations` while keeping chat models on normal chat/completions transport.
- Render reasoning with Xylune's full Markdown, table, code, link, blockquote, and LaTeX renderer during streaming.
- Enforce a display-only reasoning boundary: fenced code, widgets, generated-content blocks, scripts, package requests, and tools are rendered but never executed from hidden thinking.
- Add regression coverage for catalog merging, normal-picker visibility, endpoint selection, custom request types, Markdown reasoning, and non-executable reasoning fences.

## 0.19.4

- Add first-class image-generation model capability across OpenAI-compatible API providers and ChatGPT OAuth providers.
- Route API image models through `/images/generations`, with GPT Image and DALL·E request/response handling, base64 decoding, URL fallback, usage parsing, and bounded response sizes.
- Add GPT Image 1 and GPT Image 1 Mini to the bundled OpenAI catalog and automatically recognize common image-model IDs during discovery.
- Add a manual **Image generation** capability in the model editor for compatible custom providers.
- Decode OAuth Responses `image_generation_call` output while excluding large image payloads from native replay state.
- Persist generated images as normal assistant attachments with inline preview, viewer, save/share, retry, and conversation persistence.
- Add Room migration 13→14 and regression coverage for API/OAuth image protocol handling and queued-generation snapshots.

## 0.19.3

- Convert ChatGPT OAuth into a normal multi-instance provider type with isolated encrypted sessions, models, usage data, refresh state, rename/reconnect/remove controls, and automatic migration of the existing account.
- Request a fresh authentication prompt when connecting a ChatGPT provider so adding another account does not silently reuse the previous browser session.
- Use server-reported quota reset duration/timestamps, show a live countdown plus exact local reset date/time, and remove inaccurate window-length-derived “7 day” labels.
- Add independent panel and text opacity controls for the performance overlay while keeping it fully click-through and gesture-transparent.
- Add regression coverage for per-provider OAuth isolation, account-switch login prompting, reset normalization/countdowns, and overlay input transparency.

## 0.19.2

- Add a native **Usage & limits** panel for ChatGPT OAuth accounts with current plan, primary/secondary quota windows, percent remaining, reset times, additional limits, credits, and manual refresh.
- Cache account usage briefly, preserve the last successful snapshot across transient refresh failures, and refresh OAuth tokens automatically before retrying quota requests.
- Stream apt/dpkg/apk package-install progress through Local Code Execution and inline AI package cards, including phase, percentage when available, current package, elapsed work, and bounded live logs.
- Parse APT status-fd events while removing protocol lines from the final human-readable install log.
- Add regression tests for ChatGPT usage payload variants and package-progress parsing.

## 0.19.0

- Add native one-tap Sign in with ChatGPT using OAuth authorization-code + PKCE and Xylune's own localhost callback; no extension, WebView, Node.js process, proxy, copied token, or API key is required.
- Encrypt OAuth tokens in the existing secure store, refresh sessions automatically, retry once after authentication expiry, and provide complete sign-out cleanup.
- Discover account-available Codex models and integrate them into the normal Xylune provider/model selector.
- Add direct Responses streaming with reasoning summaries, native tools, encrypted item replay, images, usage, and Responses-lite handling.
- Rebase on 0.18.4 and preserve its blur/edge repairs, package IDs, Room data, settings, credentials, workspaces, and debug signing compatibility.

## 0.18.4

- Replace quarter-resolution strong blur with a constant four-pass half-resolution pipeline.
- Feather only the content-facing edge of each chrome panel; never fade the physical top or bottom screen edge.
- Apply one cached directional alpha mask after blur, tint, and highlight are combined.
- Include the outward feather span in panel capture overscan.
- Preserve rounded exact-zero mode, flat nonzero mode, settings/data, signing, and performance isolation.

## 0.18.0

- Remove the blur-strength discontinuity around 22–23% by keeping a fixed three-level pyramid and varying tap distance continuously.
- Remove the 0%→nonzero jump by blending blur contribution continuously from the sharp source while preserving the zero-work bypass at exactly 0%.
- Make overlay opacity absolute with a separate final tint pass: 100% fully covers the nominal rounded panel body, independent of the theme tint's original alpha.
- Keep the full 68 dp sampling-softness band centered around the nominal rounded boundary while preserving full body coverage and fading only the outward fringe.
- Preserve the 0.17.26 drawer, navigation, recomposition, signing, data, and power-policy behavior.

## 0.17.27

- Replace the visually broken progressive-crop blur with a fixed-extent, panel-local dual-Kawase-style pyramid.
- Record Compose content once and reuse it for normal rendering plus top and bottom glass panels.
- Preserve fixed visual quality and the 0.17.26 drawer/navigation/recomposition performance isolation.
- Correct profiler semantics by separating display Hz, callbacks, rendered frames, and presented-frame availability.
- Add renderer architecture, pixel-work, settings-compatibility, power-policy, and visual stress-scene regressions.
- Correct overlay opacity so 100% produces a fully opaque tint rather than preserving the theme tint's lower built-in alpha.
- Center the full 68 dp edge-softness transition on the rounded panel boundary, with equal inside/outside support and no reduced maximum range.

## 0.17.26

- Preserve the exact 0.17.18 adaptive blur kernel while filtering only progressively cropped full-resolution top and bottom dependency regions.
- Isolate drawer drag offset from root composition so continuous drawer motion does not recompose XyluneApp and ChatScreen every frame.
- Keep navigation transition state out of kept-alive page composition and stabilize the page-content function.
- Preserve adaptive refresh and thermal policy; no quality reduction or forced performance mode.

## 0.17.25

- Restore the complete 0.17.18 adaptive three-pass backdrop-blur feature exactly, while retaining later profiler and unrelated fixes.

## 0.17.24

- Restore full-resolution 0.17.8 glass quality while filtering only exact cropped strip regions.
- Record scrolling content once and replay one display list instead of traversing Compose once per blur strip.
- Isolate profiler overlay recomposition from the application root.
- Correct false GPU attribution and expose source traversal, layer replay, capture rate, and effect rebuild metrics.
- Preserve adaptive display/thermal behavior; no forced 120 Hz or sustained-performance policy.

## 0.17.22

- Replaced the 3-stage high-sample custom backdrop blur with a hardware-accelerated Gaussian RenderEffect plus a low-cost AGSL panel mask.
- Blur no longer turns off or changes quality during scrolling, ordinary navigation, or predictive-back animation.
- Matching top/bottom blur radii share one Gaussian branch; unequal radii retain exact independent rendering.
- Removed boxed chat bottom-inset state from the scroll-sensitive viewport path.
- Documented observed build problems, fixes, and the cache-preserving build workflow in `skills.md`.
- Added `scripts/sign-debug-aab.sh` to produce and independently verify a signed debug AAB.

## 0.17.17

- Smooth low edge-softness behavior for gradual blur without changing the glass kernel.

## 0.17.14

- Replaced Blur and Gradual toggles with exact blur-amount and edge-softness sliders.
- Blur 0% now means no blur; overlay opacity 100% now means fully opaque.
- APK-only release workflow by default.

# Xylune 0.17.12

- Preserve the 0.17.8 three-axis glass-blur character while increasing real samples from 9 to 15 per pass.
- Keep the proven top blur range as the canonical top panel geometry.
- Measure the actual composer panel height and use it as the canonical bottom blur geometry instead of assuming a fixed 208 dp region.
- Paint tint overlays in the scrolling source coordinate space so blur and tint share identical ranges and rounded masks.
- Preserve live Python/shell output, Running states, deferred popup dismissal, Room data, workspaces, and signing compatibility.

# Xylune 0.17.10

- Native Gaussian glass blur with a shared blur/tint panel mask.
- Fixed top and bottom mask alignment.

# Xylune 0.17.9

- Replaced directional multi-pass blur with a 49-sample isotropic glass kernel to remove grids and streaks.
- Aligned top overlay geometry to the proven top blur mask and bottom blur geometry to the proven composer overlay bounds.
- Stream Python and Linux stdout/stderr while processes are running in chat cards, code blocks, and Tool workspaces.

# Xylune 0.17.8

- Add independent Blur and Gradual switches, yielding four panel modes: gradual blur, panel blur, gradual panel, and normal panel.
- Keep the existing 16 dp minimum blur in gradual mode and use the configured blur maximum immediately in uniform panel-blur mode.
- Improve gradual panel tint falloff with a longer low-alpha feather before the transparent edge.
- Defer dropdown-menu and anchored link-popup outside dismissal until pointer release so Android edge Back gestures are not interrupted on touch-down.
- Preserve 0.17.6 scrolling, navigation, data, workspace, and signing behavior.

# Xylune 0.17.6

- Keep gradual chrome blur active whenever the feature is enabled, with a 16 dp minimum radius before any chat scrolling occurs.
- Map the strength control from the minimum radius to the 56 dp maximum instead of allowing enabled blur to collapse to zero.
- Use quintic smootherstep for scroll-driven blur growth and for the shader's spatial edge falloff, producing gentler acceleration and less visible transition boundaries.
- Start the Mica-style tint overlay at 48% and ramp it on the same curve as the blur instead of appearing only after scrolling.
- Widen the top fade to 128 dp and the composer fade to 208 dp for a more gradual blur and overlay transition.
- Preserve the 0.17.5 three-direction blur, gesture fixes, chat scrolling optimizations, Room data, settings, workspaces, and signing compatibility.

# Xylune 0.17.5

- Replace the remaining two-line blur structure with three non-axis-aligned passes distributed around the image plane.
- Increase the maximum blur radius to 56 dp and strengthen the translucent surface tint for a more Mica-like result.
- Preserve the 0.17.4 device-working RuntimeShader/RenderEffect architecture and all prior navigation and scrolling fixes.

# Xylune 0.17.4

- Restore the proven two-pass AGSL backdrop blur after the 0.17.2 single-pass Poisson shader rendered as an unblurred pass-through on real devices.
- Rotate the two orthogonal Gaussian passes by 22.5 degrees so the blur remains isotropic without the horizontal/vertical grid pattern of the old axis-aligned implementation.
- Keep the stronger 36 dp maximum radius and widened chrome fades introduced in 0.17.2.
- Preserve the 0.17.3 gesture ownership fixes, chat scrolling optimizations, application IDs, Room schema/data, settings, conversations, provider credentials, workspaces, and debug signing compatibility.

# Xylune 0.17.3

- Reserve both Android system-gesture edges for Back on Settings, Search, Sandbox, and Terminal; pull-to-open remains available on Chat.
- Keep the drawer button functional on every secondary page without installing the conflicting full-screen drawer drag recognizer.
- Hand Back ownership to page navigation as soon as a closing drawer is visually gone, eliminating the animation-end gap that could fall through to Activity exit.
- Preserve application IDs, Room schema/data, settings, conversations, provider credentials, workspaces, and debug signing compatibility.

# Xylune 0.17.2

- Reuse native Markdown and table TextViews while LazyColumn recycles message slots, avoiding repeated Android view allocation and span/editor setup during fast scrolling.
- Move paging-key bookkeeping out of chat composition and perform it only when the Paging snapshot actually changes.
- Keep selected-model and branch metadata stable per visible message instead of rebuilding it during unrelated chat recompositions.
- Replace the two-pass axis-aligned blur with one stable single-pass rotated Poisson kernel, eliminating cross/grid artifacts while reducing texture fetches and render-effect allocation churn.
- Increase the maximum gradual blur radius from 24 dp to 36 dp and widen the chat chrome fade for a stronger result.
- Preserve application IDs, Room schema/data, settings, conversations, provider credentials, workspaces, and debug signing compatibility.

# Xylune 0.17.1

- Give code blocks, tables, diagrams, and other horizontal chat surfaces gesture priority over the left drawer.
- Keep Chat composed while Settings and other global pages are open, preserving its exact viewport and state for instant Back navigation.
- Remove expensive per-frame transition clipping, shadow, and scaling; shorten ordinary page settling.
- Cut gradual-blur texture sampling from seventeen to nine fetches per axis with bilinear-paired Gaussian taps.
- Preserve application IDs, Room schema/data, settings, conversations, provider credentials, workspaces, and debug signing compatibility.

# Xylune 0.17.0

- Make pull-to-open available from anywhere in the chat surface after a deliberate 6 dp horizontal movement, with tolerant accumulated-motion arbitration, a 30% settle threshold, and an 850 dp/s fling threshold. The starting touch location no longer gates the gesture, and crossing the movement threshold never triggers an independent open animation.
- Restore chat chrome blur activation to the actual list scroll position instead of the independently collapsed header state, and add modest extra top and bottom chat gutters for more comfortable end-of-list scrolling.
- Replace threshold-triggered drawer opening with one continuously finger-tracked offset, edge/vertical gesture arbitration, velocity-aware spring settling, interactive close, scrim tap, Back, and hamburger control.
- Persist every agent Python/Linux script and attempt under its conversation workspace; add bounded `workspace_read`, atomic SHA-guarded `apply_patch`, and source-free `rerun_script` tools with compact failure diagnostics.
- Add the authoritative generated-content capability registry and inject its compact contract into every model request, with relevant exact schemas/examples only for widget, chart, or diagram intent.
- Validate completed generated blocks through recognition, syntax, schema, semantic, security/limits, and renderer preparation, then repair only an invalid block in place with a persisted one-to-five-attempt cycle.
- Preserve the Room schema at version 13; no destructive migration or data reset is required.
- Add deterministic regression coverage for drawer physics/arbitration, run revisions and atomic patches, capability consistency, repair retry/exhaustion/persistence, and ordered streaming content.

# Xylune 0.16.60

- Stabilize the chat viewport through every Working-card expansion and collapse instead of correcting only after a manual collapse.
- Manual expansion pins the card header so the card grows downward; manual collapse keeps the header fixed and still centers very large cards after the animation.
- Automatic card changes pin the latest-message bottom while following, preserve downstream content when the card is above the detached viewport, and avoid touching the list when the card is below it.
- Suspend the nonlinear streaming follower while a Working-card mutation owns the viewport, preventing the two scroll controllers from fighting each other.
- Add regression tests for the Working-card anchor strategy.


# Xylune 0.16.59

- Added a streaming scroll-anchor guard. Room/Paging refreshes can no longer reset the visible list to item 0 and then make the nonlinear follower race back down.
- Preserved stable message keys across transient Paging refresh gaps.
- Restored pull-to-open for the conversation drawer with a 56 dp edge zone and a low 10 dp horizontal trigger.
- Kept the drawer gesture edge-only while closed so vertical chat/table scrolling is not captured; native pull-to-close remains enabled while open.

# Xylune 0.16.58

- Fixed random jumps to the top of a newly appended streaming item followed by a scroll back down.
- Removed hard list positioning from the active-generation reattachment path.
- Kept nonlinear auto-follow as the only streaming scroll controller.

# Xylune 0.16.57

- Replaced constant-velocity off-screen auto-follow with distance- and time-sensitive exponential acceleration.
- Raised the measured-tail correction ceiling from 2,800 px/s to 48,000 px/s and the off-screen seek ceiling to 72,000 px/s.
- Kept small final corrections gentle while allowing large table, tool, and file-card insertions to catch up almost immediately.

# Xylune 0.16.56

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

## 0.20.19 — 2026-07-31

- Stop mutating launcher aliases while Xylune is visible; icon choices are now persisted and committed only after the app leaves the screen.
- Flush pending icon changes from both MainActivity.onStop and Android's UI-hidden callback, with the component mutation still isolated in the launcher process.
- Acknowledge a pending icon only after the requested alias was applied, preventing lost updates across process teardown.
- Update Appearance and onboarding copy to describe the safe deferred refresh behavior.


## 0.20.14 — 2026-07-31

- Fix onboarding text contrast by giving the full setup surface an explicit theme-aware content color.
- Add live System, Light, and Dark theme selection to initial setup.
- Keep setup actions fixed and reachable while the page body scrolls on small screens.
- Add Back handling, skip paths on every step, and clear recovery language so setup cannot trap the user.
- Bound the provider-catalog startup wait to eight seconds and continue with a recoverable delayed-catalog state instead of an infinite spinner.
- Preserve the existing provider-based onboarding rule; skipping never permanently hides setup when no provider is configured.


## 0.20.13 — 2026-07-30

- Restore pull-to-open drawer gestures in Settings without stealing Android's left-edge Back gesture.
- Reserve only a 48 dp non-consuming Back edge; drawer swipes still work from the Settings content area.
- Attach explicit versioned source ZIP and TAR.GZ archives to releases and include them in SHA-256 checksums.
- Split release verification into isolated, memory-bounded Gradle invocations to prevent Kotlin compiler stalls.


## 0.20.12 — 2026-07-30

- Make the left-edge Android back gesture work from the main Settings menu.
- Limit pull-to-open drawer gestures to Chat; Settings keeps its explicit menu button.
- Add regression coverage for Settings back-gesture ownership.

## 0.20.11 — 2026-07-30

- Add a 60–200% scale control for the performance overlay.
- Replace the single “Likely” label with a ranked cause profile containing primary and secondary causes, confidence, severity, and measured evidence.
- Make the overlay explicitly share pointer input with the UI underneath while consuming nothing, so scrolling and taps work through it.

## 0.20.10 — 2026-07-30

- Treat steering as an intentional hand-off instead of a resumable interruption.
- Retry empty OpenAI-compatible streams before surfacing an error.
- Fix DeepSeek tool-call replay to use non-null assistant content.
- Make sending during generation steer by default; queue remains available by long-pressing Send.
- Replace the large interruption overlay and background-work card with compact status controls.
- Hide inactive option chips while a response is running and suppress empty finished assistant cards.


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
- Added persistent Follow-device, Light, and Dark theme modes while retaining Xylune, dynamic Material You, graphite, and AMOLED options.
- Simplified provider management: provider selection is separate from a compact connection summary, while endpoint, key, and custom headers stay in an edit sheet.
- Replaced the oversized model editor with a focused bottom sheet. Basic identity and token limits stay visible; capabilities use compact chips and optional pricing is collapsed by default.
- Improved model catalog readability with search, concise capability summaries, and cleaner navigation rows.
- Bumped the debug package to version code 21.

## 0.12.0

- Split Settings into **Chat**, **Global**, and **Providers** tabs, with current-chat controls kept separate from persistent defaults for future chats.
- Added persistent per-chat and new-chat-default thinking controls, including an enable switch and Minimal/Low/Medium/High effort selector mapped to OpenAI-compatible, Anthropic, and Gemini request formats.
- Simplified agent permissions to independent Web, Python, and Linux switches. Existing chats retain their own choices; every settings change also becomes the starting profile for newly created chats.
- Added Room migration 9→10 and immutable generation-snapshot fields for thinking state so queued, resumed, and retried work preserves the settings selected when it was submitted.
- Added native structured tool calls for OpenAI-compatible, Anthropic, and Gemini providers while retaining Xylune's fenced protocol as a compatibility fallback. Streaming tool arguments, provider-specific reasoning blocks, and multi-step tool results are preserved.
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
- Added view-model-owned Python and Linux runs which continue while navigating around Xylune, show an app-wide background status, warn after ten seconds, report elapsed time, enforce configurable hard deadlines, and expose cancellation. Python uses a cooperative stop marker; PRoot processes are forcibly terminated on cancellation.
- Agent Python/Linux tools now use bounded default deadlines, accept explicitly bounded longer deadlines, report timeout timing to the model, and forbid silent retry or direct apt/apk/pip/package-manager use outside the visible approval flow.
- Generalized Linux labels, package request fences, command linting, agent permissions, and durable package transactions while retaining older Ubuntu aliases for saved chats and model compatibility.

## 0.10.0

- Rebuilt provider onboarding around protocol-aware model discovery. Xylune validates credentials and endpoints, fetches searchable model lists from OpenAI-compatible, Anthropic, and Gemini APIs, registers every discovered model, keeps manual entry as a fallback, and can refresh an existing provider without recreating it.
- Added immutable request snapshots and an append-only per-call usage ledger so queued/resumed work retains its original endpoint, limits, capabilities, pricing, and billed usage even if conversation settings later change.
- Added recoverable streaming retries with connectivity constraints, cancellable provider calls, durable partial state, bounded error handling, deterministic same-chat queue positions, and cancellation of every same-chat worker during steering.
- Added a Room 7-to-8 migration which creates/backfills FTS, persists package transactions and generation usage, stores request snapshots, and advances context summaries with stable row cursors.
- Reworked context compression into bounded ordered batches and expanded context budgeting to reserve prompts, summaries, OCR, extracted file text, and complete recent request/answer groups.
- Replaced package-name-only pip checks with resolver dry-runs covering candidates and dependencies. pip and apt now compare the exact freshly simulated plan with the approved fingerprint, persist install state, prevent repeated auto-install loops, and recover interrupted continuations.
- Added a dedicated `send_file` agent tool. Workspace diffs no longer hoist unrelated files; each returned file or image appears at its exact response-timeline location with native preview, save, and share controls.
- Remastered raster viewing with clean inline previews, a full-screen black viewer, double-tap and button zoom, clamped pinch/pan, OCR overlays only when requested, and multi-page PDF navigation.
- Added first-use consent for live generated UI, an always-available local capability/risk review, an optional independent model security opinion, expiring Home-widget handoff state, and a separate confirmation before launcher pinning.
- Added calm Xylune, Material You, and graphite palettes with optional AMOLED surfaces; refreshed the launcher mark, provider/model switcher, attachment layout, package cards, and searchable result navigation.
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
- Added `xylune-ui` for chat-only questions, requirement forms, configuration, previews, quizzes, and mini-apps; it never exposes launcher pinning.
- Changed Home eligibility to opt-in. Even `xylune-widget` content must explicitly declare `surface: "home"`, `surface: "both"`, or legacy `home: true` before the pin action can appear.
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
- Added a complete persistent Home-screen calculator keypad with clear, backspace, sign, percentage, decimal, operators, and safe expression evaluation without opening Xylune.
- Added public HTTPS JSON data sources with explicit dot/array-path bindings, 1 MB response limits, redirect blocking, private/local-address rejection at DNS resolution, cached last-good values, visible refresh state, and manual refresh.
- Added per-widget WorkManager refresh jobs with Android's 15-minute periodic floor, network constraints, retryable manual refresh, lifecycle cleanup, and immediate initial loading.
- Added live stock/metric cards in chat with source disclosure, refresh, snapshot submission, numeric formatting, and matching pin-to-Home behavior.
- Added ordered native schedule and prayer-time cards with timezones, next-event countdowns, optional live time bindings, fallbacks, manual refresh, and next-event highlighting.
- Expanded the generated-widget contract and parser validation while continuing to reject JavaScript, HTML, arbitrary code, malformed times, unsafe URLs, and malformed JSON paths.
- Raised the generated widget's launcher target size so complex keypad and schedule layouts have usable touch targets.

## 0.7.0

- Replaced the edge-list Mermaid placeholder with a native flow/state/sequence diagram canvas supporting labels, chained edges, direction, dashed messages, scrolling, and expanded previews.
- Added native bar, line, area, scatter, pie, and donut charts from structured `xylune-chart` JSON while retaining simple `label: value` compatibility.
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
