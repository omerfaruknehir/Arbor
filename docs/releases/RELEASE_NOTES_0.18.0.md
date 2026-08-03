# Xylune 0.18.0

Version: `0.18.0` (`versionCode 105`). Debug package: `app.xylune.chat.debug`; debug version name: `0.18.0-debug`.

## Corrected glass controls

- Blur strength is continuous through the old 22–23% pyramid threshold.
- Exactly 0% still bypasses blur processing; moving above zero fades blur contribution in continuously instead of revealing a pre-blurred pyramid in one step.
- Overlay opacity is absolute. At 100%, a separate final Compose tint pass fills the nominal rounded panel body at alpha 1.0 instead of inheriting the theme surface tint's 0.34/0.46 alpha.
- Edge softness uses the complete 68 dp sampling/reconstruction span around the nominal rounded boundary. The nominal tint/blur body remains fully covered, and only the outward fringe fades.

## Preserved behavior

- Existing preference keys and stored values.
- Package/application IDs, Room schemas and migrations, conversations, credentials, workspaces, and debug signing compatibility.
- 0.17.26 drawer/navigation/recomposition isolation.
- Android dynamic refresh and normal DVFS; no forced frame-rate or high-power API.

Device-side visual, thermal, and frame-rate validation is still required on the Galaxy S23+.
