# Xylune 0.19.7

Xylune 0.19.7 removes the high-strength blur patterns seen in 0.19.6. The sparse directional sampling shader is replaced by a single native Skia Gaussian blur, masked to Xylune’s chrome panels and composited over the untouched content. Large blur radii no longer expose repeated directional samples or grid-like bands.

The top blur and tint now share one exact root-coordinate geometry. Their bounds are measured once and reused for the blur mask, overlay tint, feather, rounded edge, and highlight, so collapsing the top app bar can no longer make the two layers diverge. Appearance settings include a temporary persistent **Top panel height** control from 64 to 240 dp so the preferred geometry can be tuned and reported back.

Pull-to-open navigation-drawer gestures are enabled on the Settings root. Nested Settings pages retain their horizontal Back priority and continue to block drawer ownership when Back should win.

The release preserves `app.xylune.chat.debug`, Room schema and migrations, user data, credentials, OAuth providers, workspaces, attachments, magnetic sliders, haptics, and the existing debug signer.
