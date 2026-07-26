# Arbor build, profiler, and rendering repair notes

This is Arbor's durable engineering log. Record only observed failures, confirmed root causes, applied fixes, verification commands, and remaining runtime risk. Build success is not device-performance proof.

## 0.17.23: device regression after 0.17.22

### Problem: 0.17.22 dropped to roughly 30 FPS and the blur looked wrong

**Device observation:** The Galaxy S23+ build was reported at about 30 FPS, and Android's platform Gaussian did not preserve Arbor's previous glass appearance.

**What 0.17.22 got wrong:**

- `RenderEffect.createBlurEffect` was applied to the entire scrolling viewport.
- The later top/bottom mask only discarded pixels after the upstream full-resolution blur work had already happened.
- Android therefore allocated and filtered a full offscreen layer every frame while the chat or page moved.
- A platform Gaussian is not visually equivalent to Arbor's earlier 0.17.8 three-direction, nine-tap glass kernel.

**Confirmed lesson:** Fewer shader instructions do not automatically mean a faster renderer. Filtered pixel area, offscreen-layer size, memory bandwidth, composition cost, and texture traffic can dominate arithmetic cost.

### Implemented renderer replacement

- Capture only the top and bottom glass source strips, not the full viewport.
- Extend each capture by the complete three-pass kernel support radius so strip edges do not clamp or smear.
- Render each strip at a fixed 0.5× linear resolution.
- Restore the 0.17.8 three-direction kernel with nine bilinear reads per pass and its exact weights.
- Chain three fixed passes; do not adapt sample count, radius, or quality while scrolling or navigating.
- Composite the two filtered strips back into the full-resolution scene while keeping panel tint and geometry crisp.
- Skip inactive/invalid strips instead of constructing pointless render layers.
- Guard all runtime-shader construction behind API 33, not only the draw call.

**Non-negotiable rule:** Never improve frame rate by silently reducing blur radius, sample count, resolution, or effect quality during scrolling, page transitions, drawer motion, or predictive Back.

### Strip renderer regression checks

- `topStripIncludesTheFullThreePassKernelSupport`
- `bottomStripIncludesTheFullThreePassKernelSupport`
- `stripCaptureSkipsInactiveBlurAndInvalidGeometry`
- `stripCaptureClampsItsFixedResolutionScale`
- `exact0178KernelShapeIsFixed`
- `blurQualityIsNeverBypassedForNavigationOrScroll`

## Developer cause profiler

### Purpose

The ordinary FPS counter says that a frame is slow but cannot attribute the cause. Developer settings now include **Cause profiler**, which adds Android and Arbor-specific attribution while reproducing a problem.

### Metrics collected

- Choreographer FPS, average/p95/p99 frame interval, jank, and missed-vsync estimate.
- Android `FrameMetrics` stages: total, input, animation, layout/measure, UI draw/recording, render sync, render command issue, buffer swap, and GPU duration when the device reports it.
- Arbor blur counters: CPU recording time per blur frame, filtered megapixels per second, source draws per frame, and effect rebuilds per second.
- App-root and chat recompositions per second.
- ART allocation throughput and blocking-GC rate.
- App CPU, PSS, Java heap, active screen, and refresh rate.
- A rule-based `Likely:` diagnosis such as GPU rendering with blur active, layout/measure, draw recording, command submission, buffer swap, recomposition churn, extra blur source draws, or allocation/GC pressure.

### Profiler limitations

- `Likely:` is attribution from measured counters, not omniscient proof.
- GPU duration is device/API dependent and can be unavailable.
- Compose recomposition counters indicate frequency, not which exact composable invalidated.
- The overlay and instrumentation add some overhead; disable Cause profiler after capturing the problem.
- For final proof, correlate the overlay with Perfetto/System Trace and GPU profiling on the real device.

### How to use it

1. Open **Settings → Developer settings**.
2. Enable developer settings.
3. Enable **Cause profiler**. This automatically enables the detailed overlay.
4. Reproduce one action at a time: chat fling, page navigation, drawer motion, predictive Back, or streaming update.
5. Record `Likely:`, GPU ms, `FM/L/D/Cmd/Sw`, blur MP/s/source draws/effect rebuilds, recompositions/s, allocation MB/s, and blocking GC/s.

## Build failures encountered

### 1. Newer Library entries retained only checksums

**Problem:** 0.17.20 and 0.17.21 source archives were not recoverable even though checksum files remained.

**Fix:** Use the newest complete recoverable source and increment the release instead of overwriting prior artifacts.

### 2. Command wrapper timeout killed healthy Gradle work

**Symptom:** Cold Kotlin/KSP/Chaquopy builds exceeded the command execution window without a compiler error.

**Root cause:** The outer command runner timed out and terminated the process; Gradle itself had not failed.

**Fix:** Run long gates through a detached wrapper which writes a log and an exit-status file, then inspect those files. Never classify a wrapper timeout as a source failure unless Gradle emitted a diagnostic.

Example:

```bash
nohup bash -c './gradlew --offline --daemon :app:compileDebugKotlin > build-compile.log 2>&1; echo $? > build-compile.exit' >/dev/null 2>&1 &
```

### 3. D8 was OOM-killed at `mergeExtDexDebug`

**Observed failure:** The 4 GiB cgroup killed Gradle while D8 merged Arbor's bundled Python, ML, SQLCipher, and native runtime dependencies. Unit tests had already passed; this was a packaging-memory failure, not a Kotlin-source failure.

**Fix:**

- `org.gradle.jvmargs=-Xmx1536m -XX:MaxMetaspaceSize=512m`
- `org.gradle.workers.max=1`
- `kotlin.compiler.execution.strategy=in-process`
- Run compile, focused tests, full tests, lint, and APK assembly as separate stages.
- Stop stale Gradle daemons before D8 packaging if memory is tight.
- Do not combine lint, APK, and AAB packaging in one process under a 4 GiB cgroup.

### 4. The build container itself was OOM-reset

**Observed failure:** A later serialized gate still caused the 4 GiB container to be killed. The temporary working tree and extracted toolchain vanished, while persisted `/mnt/data` archives and toolchain chunks survived.

**Confirmed lesson:** A working directory is not a checkpoint. Save a source ZIP or patch to `/mnt/data` before any memory-heavy final gate. After every significant repair, make a checkpoint before running D8, lint, or bundle packaging.

**Recovery:** Reconstruct from the last preserved source archive plus retained reference sources, then reapply the logged patch. This event must stay in this file so future builds checkpoint first.

### 5. Lint found API 33 shader construction outside the guard

**Problem:** Draw-time use was guarded for Android 13+, but `RuntimeShader`/runtime-effect construction still occurred before the API check. Android 8–12 could crash despite the apparent guard.

**Fix:** Move construction inside the API 33 branch and annotate the shader-builder function with `@RequiresApi(Build.VERSION_CODES.TIRAMISU)`.

**Lesson:** Guard object construction as well as use. Lint must remain a separate final gate.

### 6. Kotlin `if` expression inferred `Unit`

**Problem:** A conditional renderer branch used an `if` as an expression without a valid `else`, causing a type mismatch during focused compilation.

**Fix:** Return `null` explicitly from the inactive branch so the expression has a stable nullable type.

### 7. Chaquopy host Python warning

**Warning:** `Couldn't find Python 3.12` during Python-source merging.

**Meaning:** Host-side `.pyc` precompilation is unavailable; the packaged Python runtime can still build and run.

**Optional fix:** Configure an exact host Python 3.12 executable only when deterministic host `.pyc` generation is required.

### 8. Native libraries could not be stripped

**Warning:** Several prebuilt Python, SQLCipher, ML Kit, and Arbor native libraries cannot be stripped in the debug build.

**Meaning:** They are packaged unchanged. This is informational unless release-size work requires rebuilding those binaries.

### 9. Gradle's debug AAB was not verifiably signed

**Problem:** `bundleDebug` succeeded, but `jarsigner -verify` reported the generated AAB as unsigned.

**Fix:** Sign the final AAB explicitly with `jarsigner` and verify it independently. APK success does not prove AAB signature validity.

### 10. Scroll-sensitive primitive state was boxed

**Problem:** `mutableStateOf(Int)` boxed chat inset updates.

**Fix:** Use `mutableIntStateOf` for primitive hot-path state.

## Fastest reliable build workflow

### One-time toolchain setup

```bash
cat /mnt/data/toolchain-chunks/Android-Build-Tools-for-ChatGPT-Arbor-0.9.2-2026-07-16.chunk-*.bin \
  > /mnt/data/Android-Build-Tools-for-ChatGPT-Arbor-0.9.2-2026-07-16.tar.gz
sha256sum /mnt/data/Android-Build-Tools-for-ChatGPT-Arbor-0.9.2-2026-07-16.tar.gz
# Expected: fed46723984f074fa7203fddcd603d09ca55caff8bc9da2e12bbe8bc25ae349d
mkdir -p /mnt/data/android-build-tools-restored
tar -xzf /mnt/data/Android-Build-Tools-for-ChatGPT-Arbor-0.9.2-2026-07-16.tar.gz \
  -C /mnt/data/android-build-tools-restored
source /mnt/data/android-build-tools-restored/Android-Build-Tools-for-ChatGPT-Arbor-0.5.0/env.sh
```

Keep the persistent Gradle home and use `--offline`.

### Edit loop

```bash
./gradlew --offline --daemon :app:compileDebugKotlin
./gradlew --offline --daemon :app:testDebugUnitTest \
  --tests app.arbor.chat.ui.BackdropBlurTest \
  --tests app.arbor.chat.ui.PerformanceOverlayTest
./gradlew --offline --daemon :app:assembleDebug
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

### Final low-memory gate

Run each command separately:

```bash
./gradlew --offline --daemon :app:testDebugUnitTest
./gradlew --offline --daemon :app:lintDebug
./gradlew --offline --no-daemon :app:assembleDebug
```

Build/sign an AAB only when it is actually needed, in a separate process.

### Rules that materially reduce build time

1. Never run `clean` during normal iteration.
2. Preserve `.gradle/`, `app/build/`, and the toolchain Gradle home.
3. Compile Kotlin before packaging; it gives fast source diagnostics without D8.
4. Run changed test classes first; run the full suite once before delivery.
5. Keep dependency and build-script edits to a minimum because they invalidate broad caches.
6. Keep one daemon during edits; stop it only before memory-heavy final packaging when required.
7. Do not increase workers blindly. For Arbor's large runtime graph, parallel D8 work can be slower and can trigger cgroup OOM.
8. Keep Gradle build cache enabled.
9. Keep configuration cache disabled until Chaquopy/KSP compatibility is explicitly verified.
10. Checkpoint the source ZIP before lint/D8/bundle gates.
11. Use detached logging for commands longer than the execution wrapper limit.
12. Do not build AABs during ordinary APK iteration.

## Verification policy

A release is not “fixed” until all applicable gates complete:

- Focused renderer/profiler tests.
- Full unit suite.
- Android lint with zero errors.
- APK assembly.
- `aapt2 dump badging` identity/version check.
- `zipalign -c -v 4`.
- `apksigner verify --verbose --print-certs`.
- Real-device FPS and visual-quality check at 120 Hz.
- Cause-profiler capture for any remaining frame drop.

## Failure logging template

```text
### <short problem name>
Command:
Exact error/warning:
Failure or warning:
Root cause confirmed by:
Fix applied:
Files changed:
Verification command:
Verification result:
Remaining device/runtime risk:
```

Never record a problem as solved before its verification command completes.

## 0.17.23 build verification

- Cold Kotlin compile: `BUILD SUCCESSFUL` in 1m 4s.
- Focused `BackdropBlurTest` + `PerformanceOverlayTest`: passed.
- Full unit suite: 201 tests, 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings.
- APK assembly: `BUILD SUCCESSFUL` in 1m 45s.
- Low-memory D8 configuration completed without `oom_kill`, although cgroup `memory.current` reached approximately 4.29 GB and `memory.events:max` increased while dex merging was active. Keep the one-worker staged build; there is effectively no headroom for parallel final gates.
- APK identity: `app.arbor.chat.debug`, version code 100, version name `0.17.23-debug`.
- APK is zip-aligned and verified with APK Signature Scheme v2 using the existing Android debug certificate.
- AAB was intentionally not built in this iteration because it was not needed for device profiling and would add another memory-heavy packaging gate.
- Real-device blur quality and 120 Hz performance remain unverified until installed on the Galaxy S23+.

## 0.17.23 worker-count benchmark — 2026-07-26

### Environment measured

- `nproc`: 5 logical CPUs exposed by the container.
- cgroup RAM limit: 4,294,967,296 bytes (4 GiB).
- cgroup swap limit: 8 GiB.
- Gradle JVM: `-Xmx1536m -XX:MaxMetaspaceSize=512m`.
- Kotlin compiler: in-process.
- Command: `gradle --offline --no-daemon --max-workers=2 assembleDebug`.
- Source checkout had no `app/build` directory; persistent dependency/task caches were retained.

### Result

- Build result: `BUILD SUCCESSFUL`.
- Gradle-reported wall time: 23 seconds.
- `/usr/bin/time` wall time: 23.34 seconds.
- CPU utilization: 282% average, confirming useful parallel work.
- Process maximum RSS: 1,227,792 KiB.
- Peak cgroup memory: 4,294,963,200 bytes, effectively 100% of the 4 GiB limit.
- Peak swap: 0 bytes.
- cgroup OOM kills: 0.
- Rebuilt APK SHA-256 exactly matched the previously delivered APK:
  `71e13cd6970783443bb431b7d521778bb3cd893cdbc3e03ff74f53fc075b35e0`.

### Conclusion

Two workers are beneficial for cached compilation/resource work, but two-worker APK packaging has virtually no RAM headroom in this container. Do not make 3–5 workers the default merely because the CPUs exist. Arbor bundles Chaquopy, ML Kit, SQLCipher, and large native/runtime graphs; D8 and asset packaging can transiently consume the entire cgroup.

Use this split policy:

- Kotlin compile, focused tests, full unit tests, and lint: `--max-workers=2`.
- APK/AAB packaging when caches are cold or memory history is unknown: `--max-workers=1`.
- APK packaging with `--max-workers=2` is allowed only as an explicit measured fast path, with cgroup memory monitoring and automatic fallback to one worker.
- Do not use 3+ workers under a 4 GiB hard limit unless the Gradle heap and Android packaging graph are re-profiled; the measured two-worker build already reached 99.9999% of the limit.

### Fast safe helper

Use `scripts/build-fast-safe.sh`. It uses two workers for CPU-friendly stages, one worker for the memory-heavy packaging stage, and supports an explicitly monitored `ARBOR_PACKAGE_WORKERS=2` override.

## 0.17.24 device-profile findings — 2026-07-26

### 0.17.23 profiler falsely blamed the GPU

**Observed device capture:** Galaxy S23+ at a reported 120 Hz showed approximately 98 FPS, 11.0 ms average FrameMetrics total duration, p95 25.0 ms, p99 66.8 ms, 4.0% jank, 2.5 ms GPU duration, 2.2 ms draw, 1.4 ms command issue, 0.7 ms swap, 0.12 ms Arbor blur CPU recording, and roughly two source draws per blur frame.

**Incorrect result:** `Likely: GPU rendering (blur active)`.

**Root cause:** The detector selected the largest measured stage whenever total frame duration crossed the refresh deadline. At 120 Hz, a 2.5 ms GPU stage was the largest individual stage but still consumed only about 30% of the 8.33 ms budget. The remaining delay was unaccounted frame pacing/scheduling variance, not demonstrated GPU saturation.

**Fix:** A stage is now called causal only when it consumes at least 62% of the frame budget. When total duration or p95 misses the deadline but every measured stage remains below that threshold, report `Frame pacing / scheduling stalls`. Add a regression test using the exact device-capture shape.

**Lesson:** Never infer a GPU bottleneck from "largest stage" alone. Compare absolute stage time against the active refresh budget.

### Blur source was traversed once per active strip

**Observed signal:** The profiler reported `src×2.0` on Settings with blur active. The 0.17.23 renderer called `drawContent()` while recording each strip and then called it again for the normal body. On screens with both top and bottom glass, that could become three Compose/display-list traversals per invalidated frame.

**Fix:** Record one unfiltered `GraphicsLayer` source display list per invalidated frame. Draw the normal body from that layer and replay the same layer into the top and bottom filtered strip layers. Arbor-owned profiling now reports source traversals separately from layer replays and capture updates.

**Expected device signal:** `src×1.0`. Replays can be greater than one because replaying one recorded layer is the intended cheap path.

### Profiler overlay was allowed to invalidate the app root

**Problem:** `ArborApp` collected the profiler `StateFlow` at the top of the root composable. Every profiler update could recompose the root navigation/drawer host and contaminate the workload being measured.

**Fix:** Move snapshot collection into a leaf-only `PerformanceOverlayHost`. Profiler text updates now recompose only the overlay subtree.

**Lesson:** Diagnostic UI must be isolated from the measured UI. A profiler that periodically invalidates the application root can create the frame pacing problem it reports.

### Half-resolution strip blur damaged the visual result

**Problem:** 0.17.23 used a fixed 0.5x strip input. Upscaling blurred text and high-contrast settings rows produced visibly coarse, smeared glass even though the sample kernel matched 0.17.8.

**Fix:** Restore full-resolution input for the original 0.17.8 three-direction, nine-tap kernel. Keep energy use bounded by filtering only cropped strips and by reducing vertical capture support from a conservative `3 × radius` to the exact chained-kernel vertical footprint:

```text
maxTapOffset × (|axisA.y| + |axisB.y| + |axisC.y|)
= 1.8304333 × radius
```

This preserves every possible chained vertical sample while shrinking the strip render target compared with the old conservative bound.

### Power policy

Do not add any of the following to chase a nominal FPS number:

- forced 120 Hz / `setFrameRate(120)` requests,
- sustained-performance mode,
- performance hint sessions that request higher clocks,
- frame-rate overrides tied to blur activity,
- disabling adaptive refresh or thermal policy.

The target is lower work per frame and lower variance at the system-selected refresh rate. Real-device verification must compare GPU time, CPU use, capture updates per second, jank, and visual quality—not FPS alone.

## 0.17.24 build verification

- Kotlin compilation completed successfully with two workers.
- Focused blur/profiler tests passed.
- Full unit suite: 205 tests across 35 suites, 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings, 1 informational finding.
- APK assembly completed successfully with one packaging worker; final warm verification was fully up to date in 11 seconds.
- APK identity: `app.arbor.chat.debug`, version code 101, version name `0.17.24-debug`.
- APK is zip-aligned and verifies with APK Signature Scheme v2 using the existing Android debug certificate SHA-256 `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`.
- Source regression tests reject forced refresh rate, preferred display mode, sustained-performance mode, and PerformanceHintManager clock requests.
- Real-device 120 Hz performance, battery cost, and final visual quality remain unverified until this APK is tested on the Galaxy S23+.


## 0.17.25 — exact 0.17.18 blur restoration

### User correction

The user explicitly requested the 0.17.18 blur feature back. Do not reinterpret this as “make the current renderer resemble 0.17.18.” Restore the actual implementation from the preserved 0.17.18 source archive.

### Correct restoration procedure

1. Use `Arbor-0.17.18-source.zip` as the authoritative blur reference.
2. Restore `app/src/main/java/app/arbor/chat/ui/BackdropBlur.kt` and its matching `BackdropBlurTest.kt`.
3. Preserve the exact shader source, uniforms, three chained RuntimeShader RenderEffects, sample activation rules, panel masks, overlay gradients, edge-softness curve, radii, and axis constants.
4. Keep later unrelated fixes and developer-profiler infrastructure.
5. Profiler hooks may surround the renderer, but must not change shader math, sample locations, effect order, panel geometry, resolution, or quality.

### Restored 0.17.18 kernel

- Three non-axis-aligned passes.
- Continuous adaptive density tied to blur strength.
- 25 base sample pairs, four core pairs, seven edge pairs.
- Up to 73 samples per pass at full strength.
- Full-viewport chained RenderEffect architecture.
- 56 dp maximum radius and 0.25 dp radius quantization.
- Original low-edge-softness ramp and panel tint geometry.

### Important trade-off

This is an exact visual/behavioral restoration, not the later strip optimization. The full-screen three-pass path can be materially more expensive. Do not silently downsample it, substitute a native Gaussian, reduce samples during motion, or force device clocks/refresh rate. Let the profiler report the real cost.


### 0.17.25 verification outcome

- Focused tests: `BackdropBlurTest` and `PerformanceOverlayTest` passed.
- Full unit suite: 200 tests across 35 suites; 0 failures, 0 errors, 0 skipped.
- Lint: 0 errors, 12 warnings, 1 informational finding.
- APK assembly: successful with one packaging worker under the 4 GiB cgroup.
- APK identity: `app.arbor.chat.debug`, version code 102, version name `0.17.25-debug`.
- APK signing: v2 verified with the existing Android debug certificate SHA-256 `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`.
- Zip alignment: verified.
- The restored AGSL shader literal is byte-for-byte identical to the shader in the preserved 0.17.18 source. Only profiler calls surrounding the renderer were added; those calls do not modify shader math or visual output.

### Build worker lesson reinforced

Use two workers for compile/tests/lint when the cache is warm, but keep APK packaging at one worker. During lint, two workers again approached the 4 GiB limit. Do not make three or four workers the default merely because more logical CPUs are visible.


## 0.17.26 — motion-path profiling and exact-quality optimization

### Device evidence and root causes

The user captured the same Galaxy S23+ at 120 Hz in three continuous-motion scenarios:

1. **Chat scrolling:** about 66 FPS, 15.4 ms average, p95 41.7 ms, p99 66.7 ms, 44.1% jank, GPU 16.4 ms, approximately 265 filtered MP/s, one blur source traversal, and about 35 captures/s. This is a demonstrated shader/render-target cost: GPU time alone exceeds the 8.33 ms 120 Hz budget.
2. **Drawer opening/closing:** about 95 FPS, 10.0 ms average, GPU 10.2 ms, no active blur work, but both `app` and `chat` recomposed at approximately 91.3/s. The high-frequency drawer offset was observed by root composition.
3. **Rapid Settings navigation/back:** about 78 FPS, 11.9 ms average, GPU 15.7 ms, about 30 filtered MP/s, four captures/s, and Chat recomposed at approximately 45.8/s. Transition state and an unstable content lambda invalidated kept-alive pages.

Do not collapse these into one generic “GPU problem.” Each motion path had a different dominant software cause.

### Exact 0.17.18 blur with cropped full-resolution dependency regions

The 0.17.18 shader payload remains authoritative and unchanged. Its raw shader-body SHA-256 is:

```text
d48b6f6dd47f41c85f25433caa712a456fbf2ea3d04e47e3e2d30bccb0d414d9
```

The optimization changes render-target geometry, not visual quality:

- Record the Compose source once into a reusable `GraphicsLayer`.
- Preserve pass order A → B → C and every shader uniform/sample.
- For each visible top/bottom panel, create progressively smaller full-resolution layers:
  - pass A includes the panel plus the vertical support required by A, B, and C;
  - pass B includes the panel plus support required by B and C;
  - pass C includes the panel plus support required by C.
- Draw only the final pass inside the exact original rounded panel mask.
- Keep full screen width so horizontal boundary behavior remains unchanged.
- Do not downsample, reduce sample density, substitute a Gaussian, or bypass blur during motion.

For a representative 1080×2340 viewport with the measured panel geometry and 118 px blur radius, the three progressive top/bottom regions process about 57.2% of the pixels used by three full-screen passes. This is a geometry estimate, not a device FPS claim.

### Drawer state isolation

Never expose continuously changing drag offset as a root-composition dependency.

- Store offset in a dedicated high-frequency state read only from pointer handlers and graphics-layer/draw blocks.
- Expose a separate boolean visible state that changes only when crossing the fully closed boundary.
- Root Back handling may observe the boolean; it must not observe raw offset.

Expected profiler result: during continuous drawer dragging, `app` and `chat` recompositions should fall substantially from the observed ~91/s.

### Navigation kept-alive page isolation

- Do not inject transition-active/progress state through a `CompositionLocal` into kept-alive pages.
- Apply transition translation/scale/alpha in parent render layers.
- Remember the screen-content composable so `rememberUpdatedState(content)` is not fed a new function object on unrelated root updates.

Expected profiler result: the parked Chat page should no longer recompose around ~46/s during rapid Settings navigation.

### Verification requirements

- Regression-test the exact 0.17.18 shader hash.
- Test that pass A/B/C captures contain exactly the remaining vertical support needed by the chain.
- Test that typical top/bottom geometry processes materially fewer pixels than three full screens.
- Source-test that drawer offset is not read by root composition.
- Source-test that navigation transition state is not propagated into kept-alive page composition.
- Continue to reject forced 120 Hz, preferred display-mode overrides, sustained-performance mode, and clock/performance-hint requests.

### Device validation still required

Do not claim 120 FPS, lower power use, or identical real-device blur output until the Galaxy S23+ repeats the same three scenarios. The most useful comparison signals are:

- scrolling: GPU ms and filtered MP/s versus 16.4 ms / 265 MP/s;
- drawer: app/chat recompositions per second versus ~91.3/s;
- navigation: parked Chat recompositions per second versus ~45.8/s;
- p95/p99 and jank, not average FPS alone.

### 0.17.26 verification outcome

- Kotlin compilation: successful.
- Focused blur, drawer, navigation, and profiler regression tests: successful.
- Full unit suite: 205 tests across 35 suites; 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings, 1 informational finding.
- APK assembly: successful in 32 seconds with one packaging worker.
- APK identity: `app.arbor.chat.debug`, version code 103, version name `0.17.26-debug`, min SDK 26, target/compile SDK 35.
- APK is zip-aligned and verifies with APK Signature Scheme v2.
- Debug certificate SHA-256 remains `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`.
- The exact 0.17.18 shader-payload hash regression passed.
- Remaining warnings are the same unrelated API/style findings carried by prior releases; no lint errors were introduced.
- Real-device performance and power behavior remain unverified until the user repeats the three captured motion scenarios on the Galaxy S23+.
