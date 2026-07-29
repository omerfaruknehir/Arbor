# Arbor 0.20.0

Arbor 0.20.0 is an interaction overhaul focused on controls that behave predictably and chat work that explains itself.

Sliders now delegate dragging, touch slop, cancellation, keyboard access, RTL, and settling to Material 3 instead of maintaining a custom pointer-and-spring engine. Named choices such as Thinking effort use explicit menu options. Appearance now separates **Rounded / Flat** edge shape from continuous **Edge softness**, while preserving the existing stored setting.

Working cards now honor their visibility preference, name the action currently running, show readable per-step status, expand active and failed details automatically, and keep completed work compact. The composer makes background work and queued messages visible, gives **Stop** its own action, makes Queue/Steer/Separate turn discoverable, and keeps Thinking, Search, and Tools beside the message box. The `+` menu is reserved for attachments.

The release keeps the `app.arbor.chat.debug` package, debug signer, Room schema, migrations, existing chats, credentials, OAuth sessions, workspaces, and attachments compatible.
