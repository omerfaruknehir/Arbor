# Arbor 0.16.19

- Replaced the chat title's list-anchor geometry with the same Material 3 `exitUntilCollapsed` scroll controller used by Settings.
- Chat title position is now determined only by accumulated nested-scroll distance; there is no message-index inference or separate animation clock.
- Removed the synthetic header spacer from the reverse-layout message list.
- Corrected the compact Settings title's vertical alignment by centering it in the standard 64 dp top-app-bar row.
