# Xylune 0.20.8

This release replaces the license-catalog placeholder and unnecessarily rasterized artwork with the supplied original project icons.

## License catalog icons

- Keep Android, Coil, Kotlin, Markwon, Python, Termux, and ML Kit artwork as SVG vectors in the APK instead of converting them to PNG.
- Use the supplied Markwon `M` with four asterisks and convert only its font glyphs to SVG paths for deterministic offline rendering.
- Convert the supplied ML Kit Android VectorDrawable XML path-for-path into a standalone SVG.
- Remove the optional Python drop shadow while retaining the original Python vector shapes and gradients.
- Continue using the supplied raster sources only where the source files are actually raster: Chaquopy, OkHttp, and SQLCipher.
- Use Xylune's own adaptive launcher artwork for Xylune's catalog entry and the Termux icon for the bundled Termux-derived local-tool libraries.
- Remove obsolete generic document, network, and lock placeholder assets.

## Compatibility

Package identity, public debug signer, Room schema, migrations, chats, credentials, OAuth sessions, workspaces, attachments, and Linux environments are unchanged.
