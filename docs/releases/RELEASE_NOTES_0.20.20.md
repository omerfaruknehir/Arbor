# Arbor 0.20.20

## Stateful launcher-icon restart

Arbor now treats launcher-icon changes as an intentional restart instead of pretending Android can always update activity aliases without killing the app. Before the switch, Arbor synchronously journals the active screen, selected chat, Settings or setup page, chat and Settings scroll positions, every per-chat draft, and staged file attachments. It then reopens through the newly enabled launcher alias. A system-owned alarm is registered first so the relaunch still happens when One UI kills all Arbor processes during the component change.

## Durable drafts

Composer text is stored separately for every conversation. Switching chats, process death, app updates, and icon changes no longer discard drafts. Staged files were already copied into Arbor private storage and represented by durable database rows; 0.20.20 restores those rows with the corresponding draft instead of treating attachments as transient UI state.

## Palette-correct icon and splash

Each launcher alias now targets a palette-themed splash activity. Android 12 and newer use the selected launcher artwork at the native splash layer. Dynamic uses Android's live wallpaper-derived system accent and neutral resources rather than a fixed placeholder palette. Arbor's custom handoff then resolves the full active light/dark/AMOLED color scheme before opening the restored screen.

## Setup navigation

Setup is now a horizontally swipeable pager with animated page transitions. The settled page is persisted, Provider and Linux setup open as temporary detours, and Back or successful provider connection returns to the same setup step. A Setup assistant entry in Settings makes the flow reopenable at any time.

## Validation focus

Regression coverage checks intentional restart coordination, system-owned relaunch fallback, per-chat draft/file persistence, chat and Settings scroll journaling, dynamic launcher resources, palette-specific splash targets, pager navigation, and resumable setup detours.
