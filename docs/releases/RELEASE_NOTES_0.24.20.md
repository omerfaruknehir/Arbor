# Xylune 0.24.20

- Restores reliable tap-away dismissal for modal popups by deciding outside taps on finger release while ignoring predictive-Back edge gestures.
- Keeps keyboard-first Back behavior: a completed Back gesture hides the IME before dismissing the surrounding modal surface.
- Keeps source pills fixed in the message layout while a separate overlay container-transforms from the pill's exact bounds into the source preview.
- Restores normal tap-away dismissal for lightweight anchored menus.
