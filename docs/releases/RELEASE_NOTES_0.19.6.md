# Xylune 0.19.6

Xylune 0.19.6 restores the proven 0.17.8 three-direction AGSL backdrop blur while retaining the current exact blur, overlay, edge-softness, saturation, contrast, brightness, highlight, and merge-distance controls. The later capture/composite blur path is removed, eliminating its black-frame, stale-capture, hard-jump, and block-artifact failure modes.

Slider interaction is also rebuilt around a continuous magnetic force curve. Snap anchors attract smoothly, fast flicks retain their momentum, slow releases settle more deliberately, and values remain continuous outside a tiny settle core. Xylune now uses a central, system-respecting haptic vocabulary across sliders, drawer gestures, message actions, branch navigation, settings, sidebar navigation, composer controls, toggles, and confirmations.

The release preserves `app.xylune.chat.debug`, Room schema and migrations, user data, credentials, OAuth providers, workspaces, attachments, and the existing debug signer. Settings-screen drawer gestures are intentionally deferred to 0.19.7 because the 0.19.6 build passed before that request was added.
