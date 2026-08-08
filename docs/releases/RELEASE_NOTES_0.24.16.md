# Xylune 0.24.16

## Image composer blur

The Images workspace bottom blur no longer uses the oversized fixed 240 dp area or the normal chat composer's larger minimum. Image generation has a compact 88 dp blur floor and expands only to the image input area's actual measured height when reference images, validation text, queue status, or multiline input make it taller.

This keeps the translucent bottom chrome tight around the simpler image composer, which does not have the normal chat tool and mode rows.
