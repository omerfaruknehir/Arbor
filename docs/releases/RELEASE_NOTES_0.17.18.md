# Xylune 0.17.18

## Developer settings

- Added a dedicated Developer settings destination under App settings.
- Added a master developer-mode switch.
- Added a persistent performance-overlay switch, compact/detailed mode, update interval, and corner position.
- Developer diagnostics are disabled by default and remain local to the device.

## Performance counter

- Uses Android Window frame metrics without requesting continuous animation.
- Compact overlay: FPS, rolling average frame time, and jank percentage.
- Detailed overlay: p95/p99 frame time, refresh rate, app CPU use, PSS, Java heap, GPU duration when reported by Android, missed-frame estimate, and dropped metric-report count.
- Memory collection is rate-limited to once per second to reduce measurement overhead.
- Overlay monitoring starts only while both Developer settings and Show performance overlay are enabled.

## Preserved behavior

- Keeps the Xylune 0.17.17 blur and low-edge-softness fix unchanged.
- No Room schema or database migration.
- Package IDs, stored chats, providers, credentials, workspaces, and debug signing compatibility remain unchanged.
