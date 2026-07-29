# Arbor 0.17.1

Built directly on Arbor 0.17.0.

- Horizontal scrollable content owns horizontal gestures before the left drawer.
- Chat remains composed behind Settings/Search/tool pages, so Back restores the live list and Markdown tree instead of loading the conversation again.
- Navigation drops costly per-frame rounded clipping, shadows, and scaling.
- Backdrop blur uses nine bilinear-paired samples per axis instead of seventeen.

Compatibility: versionCode 78, versionName 0.17.1; package IDs and Room schema 13 unchanged. Existing conversations, settings, credentials, workspaces, and debug signing remain compatible.
