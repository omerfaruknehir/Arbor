# Xylune 0.16.6

- Replaced the ineffective self-blur modifier with a shared backdrop-capture layer that blurs the actual conversation or settings content behind translucent app bars and the composer on Android 12 and newer.
- Kept blur gradual: the radius grows smoothly with scroll while the surface tint remains constant.
- Preserved the Appearance toggle and strength control, with an opaque fallback when blur is disabled or unsupported.
- Added British English localisation. Under `en-GB`, the launcher, in-app brand surfaces, notifications, crash dialog, terminal, and widgets use **Xylune**.
- Added Android per-app language declarations for English and British English.
