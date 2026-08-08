# Xylune 0.24.21

- Fixed a regression where tapping a source pill could open an invisible focusable overlay and effectively lock the rest of the app.
- Source previews now size their overlay from the real host window instead of relying on popup `fillMaxSize()` measurement.
- Added invalid-anchor/window guards plus a fail-safe dismissal if the preview card cannot be measured.
- Tap-away still waits for finger release and ignores predictive-Back edge gestures, while an unmeasured overlay can no longer trap input.
