# Arbor 0.20.18

## Fixed

- The Arbor mark in the navigation drawer now uses the exact same palette-specific artwork as the selected launcher icon.
- The Arbor entry in Offline licenses follows the active launcher icon instead of staying on the default green asset.
- Launcher icon changes are applied by a dedicated lightweight process, insulating the foreground app task from OEM component-toggle restarts.
- Completed user messages no longer remain stuck showing only an early prefix while their full source is still available in Edit.

## Build

- `versionName`: `0.20.18`
- `versionCode`: `144`
