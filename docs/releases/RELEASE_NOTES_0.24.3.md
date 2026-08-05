# Xylune 0.24.3

## A simpler Xylune website

The GitHub Pages site now uses Xylune's own restrained Material design instead of the stock repository theme. The selected V2 layout keeps one focused content column, a stable navigation rail on larger screens, a compact mobile menu, and the legal pages in the same visual system.

The small Appearance panel offers dark, light, and system themes. When Privacy, Terms, or Data Deletion is opened from Xylune, the app also passes its resolved Material color scheme in the URL. That includes Android dynamic color, Xylune palettes, light and dark modes, and AMOLED surfaces. Internal website links retain the supplied palette, while visitors can still override it from the page.

## OpenRouter image generation repaired

Model discovery now merges OpenRouter's general catalog with its dedicated image-model catalog, so image-output capability comes from provider metadata instead of model-name guessing. General catalog metadata remains authoritative when the same model appears in both lists, and an unavailable optional image catalog no longer breaks ordinary model discovery.

OpenRouter image requests no longer send OpenAI-specific `size=auto`, quality, background, or output-format fields that the selected model may reject. The composer switches into a clear image-generation state, hides text-only tool and thinking controls, uses an image prompt and action, and prevents unsupported attachment-based image editing.

Image-output models are no longer marked **Free** merely because their text-token fields are zero. OpenRouter can price image input or output separately, so the catalog now avoids that misleading classification.

Build metadata: `versionName 0.24.3`, `versionCode 192`.
