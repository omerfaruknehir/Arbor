# Arbor 0.17.25

## Exact 0.17.18 blur restoration

- Restored the complete Arbor 0.17.18 backdrop-blur implementation.
- Restored its full-screen three-pass AGSL/RenderEffect chain, adaptive continuous sample-density kernel, three non-axis-aligned directions, panel masks, edge-softness behavior, rounded panel corners, tint overlays, and 56 dp maximum radius.
- Restored the original continuous sample budget: up to 73 texture samples per pass at full strength, with the 25 base pairs, four core pairs, and seven outer-edge pairs used by 0.17.18.
- Removed the 0.17.23/0.17.24 cropped-strip and fixed nine-tap renderer from the active path.
- Kept the later developer cause profiler and unrelated application fixes. Profiler hooks only measure the restored renderer; they do not alter its shader, masks, sample positions, or effect chain.

## Compatibility

- Package IDs, Room data, conversations, providers, credentials, workspaces, settings, and existing debug signing compatibility are preserved.
