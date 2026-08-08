# Xylune 0.24.16

## Image composer blur

The Images workspace bottom blur no longer uses the oversized fixed 240 dp area or the normal chat composer's larger minimum. Image generation has a compact 88 dp blur floor and expands only to the image input area's actual measured height when reference images, validation text, queue status, or multiline input make it taller.

This keeps the translucent bottom chrome tight around the simpler image composer, which does not have the normal chat tool and mode rows.

## Cleaner composer controls

Thinking, search, and execution controls now live under the + menu instead of permanently consuming a row above the prompt. Their visible labels are compact: thinking shows only the current effort, search shows Search or Research, and tools show the active tool state without redundant prefixes or provider fallback details.

## Reliable popup dismissal

Thinking/search/tool menus, Xylune dialogs, and anchored link/source previews now use native focusable outside-tap dismissal. Tapping outside closes the popup instead of leaving it stuck on screen, while predictive Back remains keyboard-aware for dialogs.
