# Xylune 0.24.6

This release fixes oversized and cropped Xylune artwork across Android system surfaces.

## Android icon sizing

- Add a consistent safe zone around every adaptive launcher foreground so the X-and-leaf mark is fully visible in launchers, recent-app/task views, and other masked system surfaces.
- Apply the same fitted geometry to Xylune, Dynamic/System, Graphite, Ocean, Violet, and Sunset launcher variants.
- Keep Android themed/monochrome icons aligned with the normal launcher artwork.

## Splash screen

- Stop passing adaptive launcher masks directly to the Android 12+ splash-screen API.
- Use dedicated palette-matched splash artwork with additional outer spacing, preventing the startup logo from appearing zoomed or cropped.

## Validation

- Add regression coverage for every adaptive launcher alias, monochrome artwork, and Android 12+ splash theme.
