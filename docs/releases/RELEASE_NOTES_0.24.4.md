# Xylune 0.24.4

## Legal pages integrated with the app

Privacy Policy, Terms and Disclaimer, and Data Deletion now open the rendered Xylune Pages site instead of repository source files. The same documents are listed directly under **About Xylune**, and links opened by the app carry the resolved Material scheme, including Android dynamic color and AMOLED palettes.

## App-style Pages navigation

The website now uses centered large titles that remain sticky and collapse into a compact app bar as the page scrolls. The interpolation is driven directly by the browser's scroll timeline rather than scroll-event callbacks, with snap points around the transition so the title settles expanded or collapsed instead of resting at an arbitrary partial state.

A visible scheme selector is available on the desktop navigation rail, while the compact appearance panel remains available on smaller screens. Dark, light, system, and app-provided schemes update the site logo and favicon immediately. App-provided branding still respects Xylune's **Match launcher icon to palette** setting.

## Correctly ordered releases

The Pages site now includes a dedicated releases screen that parses release tags as semantic versions and sorts them numerically. This keeps 0.24.x above 0.23.x even when GitHub's historical publication timestamps are out of sequence. New stable releases are also explicitly marked as the latest GitHub release.

## Predictive Back crash fixed

A stale one-frame Predictive Back callback could return without collecting AndroidX's progress flow, causing `IllegalStateException: You must collect the progress flow`. Xylune now consumes that stale flow as a safe no-op while preserving the normal animated completion and cancellation paths.

Build metadata: `versionName 0.24.4`, `versionCode 193`.
