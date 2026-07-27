## 0.17.27: fixed-extent multi-resolution glass renderer

### Why 0.17.26 looked wrong despite preserving the 0.17.18 shader hash

A shader hash identifies only the shader source. It does **not** identify the complete rendered operation. The 0.17.18 shader was evaluated against full-screen, full-resolution inputs. In 0.17.26, the same shader text was evaluated in three successively cropped render regions. Each pass therefore saw different texture coordinates, clamp boundaries, available support pixels, and compositing geometry. The effective convolution changed even though the AGSL text and its hash did not.

**Permanent warning:** shader identity does not guarantee visual identity when sampling geometry, source extent, edge behavior, scale, filtering, color space, or compositing order changes.

### Final lightweight blur architecture

- Record the underlying Compose subtree once per rendered frame into one shared source `GraphicsLayer`.
- Replay that source once for the normal screen and once into each active panel capture. Top and bottom panels never call `drawContent()` independently.
- Capture only the panel-local full-width source region plus one fixed support/overscan margin.
- Keep that capture extent invariant through the entire panel pipeline. Every level maps to the same source interval at a different resolution.
- Use a bounded two- or three-level dual-Kawase-style pyramid:
  1. fixed-extent full-resolution capture,
  2. 2× downsample levels with rotated nine-tap tent sampling,
  3. controlled upsample levels,
  4. full-resolution final composite.
- Apply saturation, contrast, brightness, tint, edge highlight, gradual merge, edge softness, and rounded geometry in the final full-resolution composition.
- Apply the visible panel clip only after the entire blur chain is complete.
- Cache `RuntimeShader`, `RenderEffect`, `GraphicsLayer`, panel path, brush, and geometry objects with `remember`; scrolling only re-records/replays layers.
- Bypass the blur chain when no panel has a valid non-zero blur plan.
- Never select quality from scroll velocity, fling state, navigation state, FPS, or thermal state.

### Pixel-work and layer-reuse rules

1. Compose source traversals should remain approximately `1.0/frame` while blur is active.
2. A panel capture must include the full support radius once; downstream passes must not shrink or re-crop it.
3. Downsample/upsample levels may reduce pixel dimensions, but they must preserve the same logical source extent.
4. The normal screen is a cheap replay of the shared source layer, not a second Compose traversal.
5. Processed blur pixels count effect-output surfaces, not the normal source replay.
6. Do not allocate shader strings, arrays, lists, paths, brushes, or large geometry objects in `drawWithContent`.
7. Do not use a 50–73-tap full-resolution loop when a compact bilinear pyramid can provide smooth diffusion with far less bandwidth.
8. Do not add full-screen offscreen blur surfaces for panel-local chrome.

For representative 1080×2340 Galaxy S23+ geometry used by regression tests, the two active panel pyramids process less than 45% of the pixels required by three full-screen passes. This is a deterministic geometry comparison, not a device-FPS claim.

### Performance-profiler interpretation

The overlay now distinguishes:

- **Display Hz:** active display refresh rate.
- **Callback/s:** Choreographer callback rate; this is not proof of physical presentation.
- **Render fps:** FrameMetrics-reported app window frames, bounded by Display Hz so it cannot claim 130 physically displayed frames on a 120 Hz panel.
- **Present:** shown as unavailable when no trustworthy presented-frame source exists.
- **FM avg / p95 / p99:** FrameMetrics total durations.
- **GPU, input, animation, layout, draw, sync, command, swap:** measured stages where Android exposes them.
- **srcTrav×:** Compose source traversals per blur frame.
- **replay×:** graphics-layer replays per blur frame.
- **cap/s:** individual panel capture updates per second.
- **fx/s:** shader/effect rebuilds per second.
- **MP/s:** total processed blur-effect pixels per second.
- **levels D/U:** downsample and upsample levels actually executed.
- **BlurCPU:** CPU recording/replay time.
- **Recomp/s, allocation MB/s, blocking GC/s:** application-side pressure signals.

`Likely:` compares stages with the active frame budget. The largest stage is not automatically the bottleneck; a 2.5 ms GPU stage is healthy at 120 Hz even if another scheduling stall misses the frame.

### 0.17.27 build failures and actual fixes

- **Wrapper distribution download failed with `UnknownHostException`.** The project was correct; the disposable shell lacked direct network resolution and the Gradle 8.13 wrapper distribution was not in the fresh home. Restore the preserved offline Android toolchain instead of changing Gradle.
- **Long Gradle calls appeared to time out after healthy KSP work.** The outer command runner disconnected and canceled the single-use daemon. Run long gates through a detached log/exit wrapper, then inspect Gradle's real exit code and diagnostics.
- **Cold Chaquopy merge warned that host Python 3.12 was unavailable.** This only disables host `.pyc` precompilation; it is not an Android packaging failure.

Additional compiler, test, lint, packaging, and verification outcomes for this release are recorded in `Arbor-0.17.27-verification.txt`.

### Fastest safe workflow under the 4 GiB build limit

```bash
source /mnt/data/android-toolchain/Android-Build-Tools-for-ChatGPT-Arbor-0.5.0/env.sh

# Edit/compile, tests, and lint: two workers.
gradle --offline --no-daemon --max-workers=2 :app:compileDebugKotlin
gradle --offline --no-daemon --max-workers=2 :app:testDebugUnitTest
gradle --offline --no-daemon --max-workers=2 :app:lintDebug

# D8/APK packaging: one worker, separate process.
gradle --offline --no-daemon --max-workers=1 :app:assembleDebug
```

Do not run `clean`, lint, tests, D8, and bundle packaging together. Preserve the project build directory and Gradle cache. Build an AAB only when required. A temporary local source checkpoint may be created before D8, but delete it after the final verified source ZIP and APK exist; never retain a `source-checkpoint` ZIP or checkpoint checksum in the persistent Library.

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

## 0.17.27 verification outcome

- Exact implementation baseline: `Arbor-0.17.26-source.zip`, SHA-256 `cb86c1ae9fb7063e29a8bb031a041e93074b7fbf6e32101c0e8b007fc2e9d724`.
- `Arbor-0.17.18-source.zip` was consulted only as the visual-history reference; its renderer was not used as the implementation baseline.
- Production Kotlin and debug instrumentation Kotlin compiled successfully. The moving-backdrop visual stress scene compiled, but no emulator or physical device was connected, so no screenshot claim is made.
- Focused blur/profiler/compatibility tests passed.
- Full unit suite: 213 tests across 36 suites; 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings, 1 informational finding.
- APK assembly: successful with one packaging worker. The revised follow-up build required stopping the retained test/lint daemon before the single-use packaging JVM; the final run completed without an OOM kill.
- APK identity: `app.arbor.chat.debug`, version code 104, version name `0.17.27-debug`, min SDK 26, target/compile SDK 35.
- APK zip alignment: verified.
- APK Signature Scheme v2: verified. The signing certificate is unchanged from 0.17.26: `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`.
- Room schemas and the main manifest are unchanged. Application/package identifiers, preference keys, migrations, and signing compatibility are therefore preserved.
- Representative 1080×2340 top/bottom geometry at a 118 px radius and a 68 dp symmetric feather at density 3.0 processes 2,792,610 effect-output pixels versus 7,581,600 for three full-screen passes: approximately 36.8% of the old pixel work, or 63.2% less. This remains a geometry result, not a measured device-FPS or battery result.
- Static source inspection and regression tests found no refresh-rate forcing, preferred-display-mode override, sustained-performance request, `PerformanceHintManager`, rendering wake lock, or motion-time quality reduction.

### Additional failures caught during verification

1. **Focused-test source was compiled while assertions were still being edited.** The resulting malformed string diagnostics were test-source failures, not renderer failures. Final literal assertions compile and pass.
2. **Two integer size checks incorrectly used a floating-point delta overload.** They were replaced with explicit ±1 pixel tolerance checks.
3. **A compatibility test looked for `graphicsLayer` in `ArborApp.kt`.** The actual 0.17.26 isolation correctly lives in `InteractiveNavigationDrawer.kt` and `PredictiveNavigation.kt`; the test now checks the real ownership boundary rather than forcing code into the root.
4. **Lint and D8 exceeded short command-wrapper windows.** Detached scripts with log and exit files showed real success. Neither was fixed by increasing memory, workers, or clocks.

### Remaining device validation

The build is structurally and statically verified, but visual smoothness, stale-layer behavior, sustained scrolling FPS, thermal behavior, and battery impact must still be checked on the Samsung Galaxy S23+. Do not report visual identity with 0.17.18, sustained 120 FPS, or perfect efficiency from source tests alone.

### Checkpoint cleanup

The temporary local 0.17.27 checkpoint was deleted after the APK and final source ZIP verified. Persistent Library cleanup moved the remaining Arbor 0.17.25/0.17.26 checkpoint ZIPs and BlurLab 0.1.0–0.1.7 checkpoint ZIP/checksum pairs to Trash. Normal source archives, APKs, verification reports, and release checksums were preserved.

## 0.17.27 follow-up: overlay opacity and symmetric edge softness

### Overlay opacity was not absolute

**Observed problem:** The settings slider reached 100%, but `applyOverlayOpacity` multiplied that value by the theme tint's existing alpha. A top tint with alpha 0.34 therefore remained 34% opaque at a nominal 100% setting.

**Fix:** Preserve the tint RGB values but replace alpha with the clamped slider value. The mapping is now literal: 0% is transparent, 50% is alpha 0.5, and 100% is alpha 1.0. The edge feather may still lower final alpha near the panel boundary by design.

### Edge softness must not erode the nominal panel body

**Observed problem:** A centered alpha fade multiplied into the whole panel mask makes the inner half of the nominal panel partially transparent. Even when the mathematical midpoint is correct, the result looks like the blur area has been washed away.

**Final rule:** Keep the complete 68 dp smoothing span around the nominal rounded boundary for sampling and reconstruction, but separate it from body coverage. The nominal rounded body remains fully covered. The outward half becomes a fading fringe, while the sampling scale is modulated symmetrically on both sides of the boundary.

Implementation requirements:

- Evaluate signed distance to the nominal rounded top/bottom edge.
- Use `abs(signedDistance)` to center the sampling-softness profile on the boundary.
- Keep the nominal body mask at full coverage; do not multiply it by an inward alpha fade.
- Draw tint in a separate final Compose pass using a nominal rounded `bodyPath`; draw only the outward fringe through the expanded path.
- Add the outward half-span to fixed capture overscan before any Kawase level is recorded.
- Keep every downsample and upsample level mapped to that same capture extent.
- Use the same tint pass at zero and nonzero blur strengths so crossing zero cannot change panel opacity or geometry.
- Rebuild effects only when size/settings change; scrolling must not rebuild shaders or effects.

### Packaging reset and actual fix

A first follow-up `assembleDebug` attempt reset the 4 GiB container even with one packaging worker. The renderer had already compiled and its focused/full tests and lint had passed. The actual cause was memory overlap: the retained test/lint Gradle daemon was still resident while the no-daemon packaging invocation forked its single-use JVM.

**Correct workflow:** run tests and lint with two workers, execute `gradle --stop`, confirm no Gradle/Kotlin daemon remains, then run `assembleDebug --no-daemon --max-workers=1`. The reconstructed packaging run completed successfully without an OOM kill. Do not respond to this failure by reducing render quality, changing the shader, increasing clocks, or raising worker count.

## 0.18.0: continuous controls and non-eroding edge treatment

### Blur discontinuities

The old 22–23% jump was caused by changing pyramid depth at a radius threshold. Even with identical shader text, changing level count changes reconstruction bandwidth and therefore the visible kernel. Arbor 0.18.0 keeps the bounded three-level pyramid fixed and varies the Kawase tap offset continuously.

The separate jump between exactly 0% and the first nonzero value came from bypassing the renderer at zero, then exposing a pyramid whose resample passes still had fixed per-level offsets. The corrected renderer keeps the exact-zero bypass, starts the radius-dependent tap offset at zero, and blends the processed backdrop contribution from zero to full strength continuously.

### Absolute overlay opacity

Overlay opacity is no longer a multiplier on the theme tint's original alpha. The stored 0–1 value becomes the final tint alpha directly, so 100% means alpha 1.0. Tint is drawn after blur in a separate Compose pass through the nominal rounded body path. This guarantees that a fully opaque tint cannot leak backdrop color through the panel body and makes tint geometry identical at zero and nonzero blur strengths.

### Edge softness

The full 68 dp softness range is retained. The softness profile straddles the nominal rounded boundary for sampling and edge reconstruction, while body coverage remains full. Do not implement softness by multiplying the complete panel mask with an inward fade: that visually shortens the panel and washes the blur area away. Capture support must include the outward half of the smoothing band.

### Release identity

Arbor 0.18.0 uses `versionCode 105`, preserves `app.arbor.chat.debug`, and retains the existing debug signing certificate and persistent-data compatibility.

### 0.18.0 verification outcome

- Full unit suite: 36 suites, 215 tests, 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings, 1 informational finding.
- APK assembly: passed with one worker; 20 seconds Gradle time (21.2 seconds measured wall time) and approximately 1.03 GiB maximum resident memory.
- APK identity: `app.arbor.chat.debug`, version code 105, version name `0.18.0-debug`, min SDK 26, target/compile SDK 35.
- ZIP alignment: passed. APK Signature Scheme v2: passed.
- Debug signing certificate SHA-256 remained `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`, identical to 0.17.27.
- Main manifest and all exported Room schema files were byte-identical to the 0.17.27 source baseline.
- No device-side sustained-FPS, thermal, battery, or final visual-quality claim is made without installing this APK on the Galaxy S23+.


## 0.18.1: unified panel geometry and transparent-sample repair

### Why the softened overlay and blur had different shapes

Arbor 0.18.0 still used two independent geometry implementations:

- blur coverage came from an AGSL signed-distance function for the nominal rounded panel;
- overlay coverage came from a Compose `bodyPath` plus a vertical fringe gradient clipped by a separately expanded rounded path.

Those implementations agreed along the straight horizontal edge but diverged around rounded corners. Increasing edge softness also changed the expanded path radius while the vertical gradient remained one-dimensional, so the softened tint could not match the blur mask.

**Final rule:** blur and overlay must not approximate each other. Both shaders interpolate the same `PANEL_SIGNED_DISTANCE_AGSL` source and call the same `panelCoverage` function. The nominal rounded edge is `signedDistance == 0`, and the full softness span is centered with `smoothstep(-halfFeather, halfFeather, signedDistance)`.

The outer Compose path now serves only as a conservative clip for the common signed-distance result. It does not define a separate visual fade. API 33+ tint is rendered through a panel-local solid-color graphics layer with `PANEL_TINT_SHADER`; API 26–32 retain a hard nominal-body fallback because RuntimeShader is unavailable.

### Why blur could turn the backdrop black

Kawase taps outside a recorded layer return transparent black. The previous passes averaged premultiplied RGB with those invalid samples and the final shader then read the darkened RGB without compensating for alpha. This was most visible near capture and screen boundaries, but repeated downsample/upsample passes could spread it farther into the panel.

**Repair:**

- Every resample and final tent pass uses `safeEval`.
- A tap whose alpha is effectively zero falls back to the valid center sample instead of contributing black.
- The final tent result is un-premultiplied with `filtered.rgb / alpha` before color adjustment.
- The blur layer remains premultiplied only at final composition, using the shared panel coverage and blur contribution.

Do not solve this by adding a gray/white tint, increasing overlay opacity, or shrinking the capture. Those hide the symptom and destroy underlying color fidelity.

### Edge-softness semantics

- The stored 0–1 setting still maps to the complete 0–68 dp span.
- Half of that span lies inside and half outside the nominal signed-distance boundary.
- At the nominal edge, blur and tint coverage are both 0.5 when softness is active.
- Deep inside the panel, coverage reaches 1.0; beyond the outward half-span, coverage reaches 0.0.
- Rounded corners, straight edges, blur, tint, and highlight all use the same signed distance.

### 0.18.1 verification outcome

- Release identity: `versionName 0.18.1`, `versionCode 106`.
- Full unit suite: 36 suites, 217 tests, 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings.
- Debug instrumentation Kotlin compilation: passed.
- APK assembly: passed with one packaging worker after stopping retained Gradle daemons.
- Package: `app.arbor.chat.debug`; min SDK 26; target/compile SDK 35.
- ZIP alignment and APK Signature Scheme v2 verification: passed.
- Debug certificate SHA-256 remains `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`, identical to 0.18.0.
- Runtime AGSL output still requires installation on the Galaxy S23+ for final visual confirmation. Host unit tests and APK verification cannot prove device GPU-driver rendering.

## 0.18.2: artifact-free premultiplied panel composition

### Why 0.18.1 still produced artifacts

The renderer still combined several individually plausible operations that were unstable together:

- every downsample and upsample stage used rotated custom AGSL taps;
- the final blur shader generated its own alpha mask;
- Compose clipped that result again with a separately constructed path;
- tint was masked and composited on another path/effect;
- transparent samples could pass through RGB un-premultiplication and become dark fringes.

Those independent masks and sampling domains produced corner seams, directional patterns, unstable edge coverage, and occasional black contamination. Matching formulas or shader source was not enough because the actual layer boundaries and alpha operations differed.

### Final renderer architecture

- Record the Compose source once.
- Capture only fixed-overscan top and bottom panel regions.
- Build a fixed three-level 1x -> 1/2x -> 1/4x -> 1/8x pyramid.
- Apply one low-resolution Android `RenderEffect` blur with `Shader.TileMode.CLAMP` at the deepest level.
- Reconstruct to full resolution through bilinear graphics-layer scaling.
- Apply saturation, contrast, and brightness with a `ColorMatrixColorFilter`, not a runtime shader.
- Clip blurred backdrop and absolute-opacity tint together into one nominal rounded panel layer.
- Apply edge softness once to that combined premultiplied layer with `Shader.TileMode.DECAL`.
- Draw the resulting panel layer without any second mask or crop.

### Geometry and alpha rules

- Blur, tint, corner geometry, highlight, and edge softness must share one local `Path` and one composite layer.
- Never independently feather blur and tint.
- Never apply both shader alpha masking and a second Compose clip to the same visible edge.
- Keep colors premultiplied through all blur stages; do not divide RGB by alpha in the renderer.
- Backdrop blur uses `CLAMP`; softened panel alpha uses `DECAL`.
- The edge-softness setting defines the full visible +/-3 sigma transition span. It does not reduce the nominal panel body.
- A 100% overlay is alpha 1.0 in the panel body before edge softness is applied.

### Performance implications

The fixed pyramid still processes panel-local pixels and records the source once. Removing six custom runtime-shader resample/composite effects reduces shader rebuild complexity, eliminates rotated multi-tap patterns, and leaves Android's optimized blur implementation operating only at 1/8 resolution. Edge softness is panel-local and applied after blur+tint are combined.

### 0.18.2 build-signing failure and actual fix

**Observed failure:** The first assembled 0.18.2 APK verified correctly but had certificate SHA-256 `2102d09c...`, which did not match Arbor's established debug certificate `b9d95df7...`.

**Root cause:** The offline toolchain's stable debug keystore existed under its dedicated `android-user-home`, but `ANDROID_USER_HOME` was not exported for the one-worker packaging process. Android Gradle Plugin therefore generated/selected another default debug keystore under the temporary build user home.

**Fix:** Verify every APK certificate against the previous release. Re-sign the already zip-aligned APK with the preserved `androiddebugkey` using `apksigner`, explicitly enabling v2 and disabling v1/v3/v4. Future builds should export the toolchain `ANDROID_USER_HOME` before Gradle packaging so the correct key is selected automatically.

**Non-negotiable rule:** A successful `assembleDebug` and a valid v2 signature do not prove upgrade compatibility. The signer certificate digest must be compared with the previous installable APK before delivery.

## 0.18.3: stable strong blur and explicit edge modes

### Why blur became visibly coarse above roughly 20%

0.18.2 always used a fixed pyramid, but its deepest blur surface was only 1/8 of the panel resolution. As the requested radius increased, the final reconstruction enlarged low-frequency pixels from that surface until the bilinear reconstruction itself became visible. This was not a literal 20% branch; it was a quality limit of the 1/8-resolution source becoming obvious at stronger radii.

### Strong-blur repair

- The blur path is fixed at two downsample levels: full -> 1/2 -> 1/4.
- Two equal Android blur passes are cascaded at 1/4 resolution.
- Each pass uses `requestedRadius / (4 * sqrt(2))`, preserving the combined Gaussian sigma while avoiding the coarse 1/8 reconstruction.
- Both passes use `Shader.TileMode.CLAMP`.
- The level count remains exactly two at every nonzero slider value. No radius, scroll, animation, thermal, or navigation threshold changes the blur topology.
- The source is still recorded once and each panel still uses one fixed-overscan capture extent.

For the representative 1080x2340 two-panel test geometry, the revised pipeline processes about 32.5% of the pixels used by three full-screen passes. This is a geometry calculation, not a device frame-rate claim.

### Edge-softness mode semantics

- Slider values from 0% through 6% snap to exact zero. This provides a practical touch target for the zero mode.
- Exact zero uses the normal rounded top/bottom panel corners and no edge feather.
- Any stored value above the snap-zone selects flat panel geometry.
- The remaining slider range is remapped continuously onto the complete 0..1 softness range, so 100% still reaches the full 68 dp feather.
- The feather distance starts continuously after the snap-zone; there is no forced 4 px minimum jump.
- The stored preference key remains `chrome_edge_softness`; existing settings and app data are preserved.

### 0.18.3 verification outcome

- Release identity: `versionName 0.18.3`, `versionCode 108`.
- Full unit suite: 36 suites, 219 tests, 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings, 1 informational finding.
- Debug instrumentation Kotlin compilation: passed.
- APK assembly: passed with one packaging worker.
- Package: `app.arbor.chat.debug`; min SDK 26; target/compile SDK 35.
- ZIP alignment and APK Signature Scheme v2 verification: passed.
- Debug certificate SHA-256 remains `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`, identical to 0.18.2.
- Main manifest and Room schema files are byte-identical to 0.18.2.
- Final blur appearance still requires installation on the Galaxy S23+; host tests cannot prove device GPU/compositor output.


## 0.18.4: remove coarse strong blur and screen-edge fading

### Why the blur still broke above about 20%

0.18.3 removed the 1/8 surface, but it still reconstructed the visible panel from a 1/4-resolution image. The requested blur radius continued increasing linearly while the reconstruction grid stayed fixed. At stronger values, enlarged quarter-resolution color cells and bilinear interpolation became visible as deformation, uneven diffusion, and other artifacts. There was no literal 20% branch; the slider crossed the visual limit of the reconstruction surface.

**Final repair:**

- Use exactly one downsample level for every nonzero value.
- Run four equal Gaussian passes at 1/2 resolution.
- Set each pass radius to `fullResolutionRadius / 4`. Four passes combine by root-sum-square at half resolution and reconstruct to the requested full-resolution sigma.
- Reconstruct directly from 1/2 resolution to full resolution.
- Never switch topology based on radius or motion.

The representative panel-local pixel calculation remains materially below three full-screen passes, while retaining four times as many reconstruction pixels as a quarter-resolution source.

### Why edge softness faded the screen top

0.18.3 applied an Android blur effect with `Shader.TileMode.DECAL` to the entire completed panel layer. DECAL correctly fades alpha outside every layer boundary, but the top panel's layer boundary also included the physical top of the screen. The effect therefore softened the wrong edge and produced a transparent/faded strip in the status-bar region. The bottom panel had the equivalent risk at the physical screen bottom.

**Final repair:**

- Do not blur the completed panel alpha.
- Record blur, tint, and highlight into one premultiplied offscreen panel layer.
- For nonzero softness, apply one cached vertical gradient with `BlendMode.DstIn`.
- Top panel mask: opaque from the screen top, then transitions from opaque to transparent only across the lower content-facing edge.
- Bottom panel mask: transitions from transparent to opaque only across the upper content-facing edge, then remains opaque through the screen bottom.
- Keep the transition centered on the nominal content-facing edge.
- Include the outward half-span in capture support.
- Exact zero uses the rounded path and no gradient mask.

**Rule:** Edge softness must identify which edge is semantically soft. Never apply a generic blur/DECAL effect to the complete panel when some panel sides coincide with physical screen boundaries.

### 0.18.4 build failures and actual fixes

1. **Wrong `CompositingStrategy` namespace**
   - Symptom: Kotlin reported that `androidx.compose.ui.graphics.CompositingStrategy` could not be assigned to `GraphicsLayer.compositingStrategy`.
   - Cause: direct `GraphicsLayer` objects use `androidx.compose.ui.graphics.layer.CompositingStrategy`, while `graphicsLayer {}` modifiers expose the similarly named type from `androidx.compose.ui.graphics`.
   - Fix: import `androidx.compose.ui.graphics.layer.CompositingStrategy` and set `panelComposite.compositingStrategy = CompositingStrategy.Offscreen`.

2. **Timed command wrapper left a high-memory Gradle daemon behind**
   - Symptom: the command wrapper ended during Kotlin compilation, no result file appeared, and the daemon retained roughly 2.4 GiB RSS near the cgroup limit.
   - Cause: the wrapper/client was terminated while the daemon remained alive; the incomplete log was not a compiler failure.
   - Fix: terminate the orphan daemon, run each gate in a fresh single-use daemon via a detached `setsid` command, and wait for an explicit exit-status file before starting another memory-heavy gate.

3. **D8 memory safety**
   - Tests and lint were completed and allowed to exit before packaging.
   - `assembleDebug` ran alone with `--no-daemon --no-parallel --max-workers=1`.
   - The build reached `mergeExtDexDebug`, native packaging, signing, and APK assembly without an OOM reset.

### 0.18.4 verification outcome

- Release identity: `versionName 0.18.4`, `versionCode 109`.
- Full unit suite: 36 suites, 219 tests, 0 failures, 0 errors, 0 skipped.
- Android lint: 0 errors, 12 warnings, 1 informational finding.
- Debug instrumentation Kotlin compilation: passed.
- APK assembly: passed with one packaging worker.
- Package: `app.arbor.chat.debug`; min SDK 26; target/compile SDK 35.
- ZIP alignment and APK Signature Scheme v2 verification: passed.
- Debug certificate SHA-256 remains `b9d95df7ad0661559341623227cb0cc5218524715af5d7b31af2ecd0e7d577b9`, identical to 0.18.3.
- Main manifest and all Room schema files are byte-identical to 0.18.3.
- Representative 1080x2340 top/bottom panel geometry processes 3,254,040 pixels per frame versus 7,581,600 for three full-screen passes, or about 42.9% of that reference workload.
- Final visual output still requires installation and direct testing on the Galaxy S23+; host tests cannot prove Samsung GPU/compositor appearance.
