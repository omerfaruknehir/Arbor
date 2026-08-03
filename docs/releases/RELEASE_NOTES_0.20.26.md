# Xylune 0.20.26

## Snippets and widgets

- `xylune-snippet/1` is now the only in-chat interactive format.
- `xylune-widget/1` is now the only Android Home-screen format.
- Both use one general component tree and bounded action language instead of named widget categories.
- Each pinned widget receives its own explicit network, location, folder, and background-refresh grants.
- Legacy `xylune-ui`, `ui`, `xylune-form`, `widget`, `mini_app`, specialized widget types, layouts, and storage are intentionally unsupported.

## Google Drive backup

- Connect once and see the selected Google account.
- Create, browse, preview, and restore appDataFolder backups from one screen.
- Silent authorization reuse avoids repeated consent prompts; switch-account and disconnect/revoke controls are explicit.
- Errors remain visible in the backup screen with a direct reconnect action.

## Chat sharing

- A portable-chat share button now sits in the chat top bar immediately left of the overflow menu.
