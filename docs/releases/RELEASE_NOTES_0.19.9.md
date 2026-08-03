# Xylune 0.19.9

Xylune 0.19.9 repairs the native Skia Gaussian backdrop so it behaves as a true replacement blur rather than a bloom-like additive layer. The renderer builds complementary branches—untouched content outside the panel mask and Gaussian-filtered content inside it—and combines those premultiplied branches exactly once. Top and bottom filtered panels are resolved before the final replacement, preserving shared geometry and avoiding double attenuation through feathered pixels.

The **Edge softness** control now has two semantic 0% anchors. The first anchor is a rounded, hard-edged panel. The non-snapping lane between anchors changes only the panel geometry from rounded to flat while remaining at 0% softness. The second anchor is a flat, hard-edged panel at 0%. Only the range after that second anchor increases actual symmetric feathering from 0% to 100%. Existing nonzero softness values are migrated into the post-flat lane so their previous feather strength is retained.

Xylune sliders no longer warp their delivered value during pointer movement by default. They remain continuous under the finger, provide tactile anchor proximity and tick feedback, and use only a small velocity-aware release capture where explicit snap points are configured. The Thinking selector remains physically continuous while open and chooses the nearest effort on release without snapping or re-keying the thumb to an integer.

The release preserves slider priority over drawer gestures, the configurable top-panel height, package identity, signer, Room schema and migrations, chats, provider credentials, OAuth sessions, workspaces, attachments, and existing settings.
