# Xylune 0.20.13

## Fixed

- Pull-to-open drawer gestures work again from Settings.
- Android Back still owns the actual left screen edge. Start a drawer swipe slightly inside the Settings content; start Back from the physical edge.
- The edge reservation consumes no pointer input, so scrolling and taps remain unaffected.
- Release verification uses isolated, memory-bounded Gradle invocations to avoid Kotlin compiler stalls.

## Release assets

- Optimized release APK
- Release AAB
- Explicit `Xylune-0.20.13-source.zip`
- Explicit `Xylune-0.20.13-source.tar.gz`
- SHA-256 checksums covering every attached asset

Developer settings and the performance overlay remain available in the optimized release build.
