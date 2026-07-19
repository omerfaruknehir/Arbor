# Arbor 0.16.24

- Fixed the 0.16.23 frosted-chrome tint covering content beneath app bars and the composer.
- Reduced the tint multiplier from 68–100% to 10–30%; the real blur remains active underneath.
- Disabling gradual blur now removes the tint instead of leaving a full-strength surface overlay.
- Retained the alpha-safe, edge-clamped 17-tap two-pass blur kernel.
