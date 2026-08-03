# Xylune 0.20.10

This release simplifies active-response controls and fixes two generation-state bugs.

## Steering and response state

- Sending while Xylune is working now steers the current response by default.
- The replaced partial response is preserved as complete instead of being shown as an interruption with a misleading **Resume** action.
- Queue remains available by long-pressing Send.
- User-requested pauses and genuine failures use one compact recovery control.

## Empty responses and DeepSeek

- OpenAI-compatible streams which complete without text, reasoning, tools, or images are retried twice before failing.
- DeepSeek tool-call history now serializes blank assistant content as an empty string rather than JSON `null`.
- Empty finished assistant cards are no longer rendered as model-name-only rows.

## Composer cleanup

- The duplicate active-response overflow control and concurrent-turn option were removed.
- The background-work banner is now a single compact row.
- Think, Search, and Tools controls are hidden while a response is already running because changes cannot affect that in-flight request.

The FPS/developer overlay and all Developer settings remain available in optimized release builds.

Normal Android CI validates unit tests, lint, the optimized release APK/AAB, and device instrumentation before publication.
