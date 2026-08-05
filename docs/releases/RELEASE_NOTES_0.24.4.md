# Xylune 0.24.4

## Legal pages integrated with the app

Privacy Policy, Terms and Disclaimer, and Data Deletion now open the rendered Xylune Pages site instead of repository source files. The same documents are listed directly under **About Xylune**, and links opened by the app carry the resolved Material scheme, including Android dynamic color and AMOLED palettes.

## App-style Pages navigation

The website now uses the same single-title motion as Xylune's Android app: the title starts lower, slightly larger, and left-aligned, then moves into the compact 64 px app-bar row while scaling to its final size. The interpolation is driven directly by a CSS scroll timeline rather than scroll-event callbacks.

Title snapping is confined to the two expanded/collapsed positions at the top of the page. The rest of every legal or release document remains normal free scrolling instead of inheriting sticky snap behavior.

## Predictable scheme and branding controls

A labeled **Color scheme** selector is visible in the desktop navigation rail, while the full appearance panel remains available from the palette button on smaller screens. App, Dark, Light, and Auto choices use equal-width visible buttons; when the App option is unavailable, no empty fourth slot remains.

Dark, Light, and Auto keep the canonical Xylune logo and favicon. Only an App theme received from Xylune may recolor them, and only when **Match launcher icon to palette** was enabled in the app. The generated app-theme logo uses normalized palette colors so it remains recognizably Xylune rather than changing unpredictably with the website's own light/dark selection.

## Correctly ordered releases

The Pages site now includes a dedicated releases screen that parses release tags as semantic versions and sorts them numerically. This keeps 0.24.x above 0.23.x even when GitHub's historical publication timestamps are out of sequence. New stable releases are also explicitly marked as the latest GitHub release.

## Predictive Back crash fixed

A stale one-frame Predictive Back callback could return without collecting AndroidX's progress flow, causing `IllegalStateException: You must collect the progress flow`. Xylune now consumes that stale flow as a safe no-op while preserving the normal animated completion and cancellation paths.

Build metadata: `versionName 0.24.4`, `versionCode 193`.
