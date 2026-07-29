# Arbor 0.20.1

Arbor 0.20.1 repairs the visible panel and control regressions found in 0.20.0.

The backdrop renderer now uses Arbor's physical-device-proven direct RenderEffect path from 0.17.8, adapted to the current shared panel geometry. It replaces the complete-frame replay which could build successfully without producing visible blur on-device. Soft panel tint is drawn as one continuous gradient, eliminating the line between its solid and feathered regions. Appearance also explains when a 100% opaque tint is hiding blur.

Sliders keep Material's maintained gesture and accessibility handling but use a circular thumb instead of the line-like default. Thinking effort is again a slider: it follows the finger continuously, marks the supported named levels, gives boundary haptics, and springs to the nearest level only after release. The Thinking, Search, and Tools pills now begin 12 dp farther left.

The release keeps the `app.arbor.chat.debug` package, debug signer, Room schema, migrations, existing chats, credentials, OAuth sessions, workspaces, and attachments compatible.
