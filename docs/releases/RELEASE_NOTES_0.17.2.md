# Xylune 0.17.2

## Chat scrolling

- Native Markdown, table-preview, and streaming text views now participate in Compose `AndroidView` reuse. Fast scrolling reuses existing `TextView` instances instead of repeatedly allocating views, selection editors, and span containers.
- Styling is cached per reused view, so unchanged colours and text metrics are not reapplied during unrelated recompositions.
- Paging key bookkeeping moved to a conflated snapshot observer. It now runs only when the loaded message snapshot changes rather than whenever Scaffold content recomposes.
- Selected-model and revision-branch data are retained per message slot instead of being rebuilt for every visible card.

## Blur

- The former two-pass axis-aligned Gaussian approximation was replaced by one 13-sample rotated Poisson kernel. This removes the cross/grid structure visible over high-contrast text and reduces the texture-sampling workload from 18 fetches to 13.
- RuntimeShader and RenderEffect objects are retained for the life of the content layer; scrolling now updates uniforms instead of allocating two shaders and a chained effect repeatedly.
- Blur-radius updates are quantized to quarter-dp steps to avoid sub-pixel state churn.
- Maximum radius increased from 24 dp to 36 dp. Chat top and composer fade regions were widened to 96 dp and 144 dp respectively.
- Sampling is clamped to the source bounds, preventing dark edge contamination.

## Compatibility and validation

- Package: `app.xylune.chat.debug`
- Version: `0.17.2-debug` (`versionCode 79`)
- Room schema remains 13; no destructive migration is introduced.
- Existing conversations, settings, credentials, workspaces, application IDs, and debug signing compatibility are preserved.
- `compileDebugKotlin`, 176 unit tests, `lintDebug`, `assembleDebug`, and `bundleDebug` pass.
