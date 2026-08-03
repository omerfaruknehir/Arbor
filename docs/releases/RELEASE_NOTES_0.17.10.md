# Xylune 0.17.10

- Replaces the custom sampled blur kernel with Android/Skia native Gaussian blur.
- Uses one canonical rounded mask for both blur and tint, eliminating cross-coordinate geometry drift.
- Keeps the correct top blur geometry and the correct measured bottom panel geometry.
- Preserves live Python and shell stdout/stderr streaming from 0.17.9.
