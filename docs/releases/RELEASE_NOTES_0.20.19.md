# Xylune 0.20.19

## Launcher icon stability

- Launcher icon changes no longer touch Android component state while Xylune is visible.
- The in-app preview changes immediately; the launcher icon itself is committed the next time Xylune leaves the screen.
- The selected icon is applied only after Xylune is hidden, preventing One UI and other launchers from tearing down the active task.
- Pending changes survive process teardown and are cleared only after the requested alias is successfully applied.

## Validation

- Added regression coverage proving foreground settings only queue changes.
- Added lifecycle coverage for both activity-stop and UI-hidden flushing.
- Retained atomic Android 13+ alias switching and `DONT_KILL_APP` for the background mutation.
