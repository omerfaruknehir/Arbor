# Arbor 0.17.22

## Full-quality blur performance repair

- Replaced the three chained custom blur shaders (up to 73 neighbourhood samples per pass) with Android's hardware-accelerated Gaussian `RenderEffect` pipeline.
- Added a lightweight AGSL panel mask so only the registered top and bottom glass regions replace the original content.
- Coalesces top and bottom into one Gaussian branch whenever their requested blur radii match; separate branches are retained only when required for visual correctness.
- Removed the navigation-transition blur bypass. Blur radius, edge softness, tint, and panel geometry remain unchanged while scrolling and during ordinary or predictive page animations.
- Kept tint rendering outside the blur layer so chrome tint remains crisp.
- Added regression tests for branch coalescing, exact unequal radii, inactive panels, and the absence of navigation/scroll quality downgrades.
- Replaced the boxed chat bottom-inset state with Compose primitive `Int` state to avoid allocation in viewport updates.
- Added `skills.md` with the actual build failures, fixes, device verification requirements, and cache-preserving fast build workflow.
- Added a verified `jarsigner` workflow for producing a debug AAB with the same stable Android debug certificate as the APK.

Version: `0.17.22` (`versionCode 99`).
