# Xylune 0.20.7

This release repairs the offline license catalog artwork and adds an explicit
development disclosure and liability disclaimer.

## Offline license icons

- License component icons are still sourced only from the checked-in
  `licenses/icons/` directory and are copied into the APK during the build.
- The catalog generator now rejects icons that are outside `icons/`, empty, or
  in an unsupported format, then verifies the copied asset bytes before the APK
  build continues.
- The Compose screen now uses an explicit Coil SVG decoder for embedded
  `android_asset` URLs instead of relying on the default image loader to infer
  SVG support.
- Loading or decoding failures show a readable component-initial fallback tile
  instead of leaving an empty icon box.

## Development disclosure

The README and the in-app **About Xylune → Licenses & notices** area now state
that Xylune was made with full vibe coding and AI-assisted coding tools. They
also state that Xylune is provided **AS IS**, without warranties, that use is at
the user's own risk, and that the author and contributors are not responsible
to the maximum extent permitted by applicable law for data loss, device damage,
account loss, charges, security incidents, or other consequences.

The disclosure is itself bundled offline with the app and repeated on Xylune's
own catalog entry.

Existing package identity, debug signer, Room schema, migrations, chats,
credentials, OAuth sessions, workspaces, attachments, and Linux environments
are preserved.
