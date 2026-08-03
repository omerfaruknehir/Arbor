# Xylune 0.20.3

Xylune 0.20.3 removes the repeating bands and lattice pattern that could appear near maximum interface-panel blur strength.

The renderer keeps Xylune's reliable direct RuntimeShader/RenderEffect path and the exact panel geometry from 0.20.2, but replaces the sparse bilinear-paired kernel with 15 evenly spaced direct Gaussian samples on each of its three rotated axes. Blur strength remains continuous from 0–100%, and the seamless soft-edge/tint treatment is unchanged.

This release also refreshes the GitHub repository presentation and adds tag-driven GitHub Actions release automation. A pushed `v*` tag now runs unit tests and lint, builds the debug APK/AAB and instrumentation APK, uploads the build set, and publishes the installable APK with a GitHub Release.

## Install

Download `Xylune-0.20.3-debug.apk` from the release assets. It is debug-signed for direct testing and uses package ID `app.xylune.chat.debug`.

## Verification

- Unit tests
- Android lint
- Debug APK
- Debug AAB
- Debug instrumentation APK

The release APK is not Play-production-signed. Build with your own protected release key before store distribution.
