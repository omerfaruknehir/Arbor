# Arbor 0.19.8

Arbor 0.19.8 keeps the native Skia Gaussian replacement from 0.19.7, but repairs the effect graph that could produce missing, black, or stale blur output on affected Android renderers. Each top or bottom panel now uses an independent native pipeline: Gaussian blur, glass color adjustment, a standalone alpha-mask shader, and source-over composition with the untouched content. The mask no longer consumes the Gaussian as a RuntimeShader child image filter.

The high-radius blur remains pattern-free because it still uses Skia’s native Gaussian kernel rather than sparse directional taps. Blur, overlay tint, feathering, rounded geometry, and edge highlights continue to use the same measured root-coordinate bounds, including the temporary persistent **Top panel height** control from 64 to 240 dp.

Arbor sliders now register their complete bounds as horizontal-priority regions. A pointer sequence that begins on a slider is reserved for that slider before the Settings drawer recognizer evaluates horizontal intent, including controls positioned near the left screen edge. Magnetic attraction, velocity-aware settling, and haptic feedback are unchanged.

The release preserves `app.arbor.chat.debug`, version-compatible signing, Room schema and migrations, chats, provider credentials, OAuth sessions, workspaces, attachments, and existing settings.
