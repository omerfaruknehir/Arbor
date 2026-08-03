# Xylune 0.17.9

## Glass blur
- Uses a one-pass 49-sample isotropic radial Gaussian kernel instead of directional lines.
- Keeps a subtle glass saturation/luminance treatment without repeating patterns.
- Uses a stable blur radius through the merge feather to avoid bands.
- Top overlay follows the top blur geometry; bottom blur follows the actual composer overlay geometry.

## Live execution output
- Python and Linux stdout/stderr update while the process is running. Python uses unbuffered mode so `print()` lines appear immediately.
- Running cards show elapsed time and a bounded live tail.
- Applies to agent tools, runnable response code blocks, and Tool workspaces.
