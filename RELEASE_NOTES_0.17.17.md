# Arbor 0.17.17

- Fixes the visual contour that appeared when gradual blur edge softness was set near zero.
- Removes the abrupt hard-edge/feather branch in the AGSL blur mask.
- Uses a continuous smootherstep activation across the low-softness range.
- Applies a 4 dp anti-aliasing feather floor only while softness is active; exact zero remains a hard edge.
- Uses the same continuous activation for the Mica/tint merge so blur and overlay remain aligned.
- Preserves the 0.17.16 three-axis adaptive glass kernel and all existing data, package, signing and workspace compatibility.
