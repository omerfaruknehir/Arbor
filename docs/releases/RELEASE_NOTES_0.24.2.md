# Xylune 0.24.2

## A model catalog that stays put

The model catalog is now a full-screen surface rather than a draggable bottom sheet. Long vertical gestures belong exclusively to the model list, so browsing hundreds of OpenRouter models no longer pulls the entire picker down or accidentally dismisses it. Search, provider selection, filter controls, favorites, and the result count stay visible while the list uses the remaining screen space.

## Filters can be combined

Capability and catalog filters are independent toggles. Selecting **Vision** and **Tools**, for example, shows models that support both; adding **Free** narrows that result to zero-cost models. Favorites and recents can be combined with the same capability filters. **Clear** returns to the complete catalog.

## Expanded-title model selection repaired at the hit-test boundary

The previous visual z-index adjustment did not solve the complete bug. In the expanded state, the model pill was rendered below a compact 64 dp parent, outside the region Compose considered eligible for pointer input. Its nested z-index also could not escape the stacking order of that parent against the large app bar.

The title/model overlay now matches the already measured app bar, keeping the translated model pill inside a real hit-test ancestor without increasing the Scaffold inset. The overlay itself is placed above the app bar, so the model selector receives taps consistently in both expanded and collapsed states.

## Legal documents match the actual project boundary

The bilingual privacy policy is now a concise factual notice rather than contract-style language. It says exactly what the architecture does: Xylune has no application backend; the maintainer does not receive or have technical access to local chats, credentials, backups, or provider-held copies; and user-selected AI and storage traffic goes directly from the device to the chosen provider. It also identifies GitHub as the independent operator of repository accounts, hosting, logs, and public Issues.

The former large Terms of Use is replaced by short Terms and Disclaimer. It covers independent providers, AI-output risk, local execution, backups, GitHub's role, and the absence of any support or response-time commitment. Unnecessary extra risk-transfer and artificial monetary-cap clauses are removed. The Apache License 2.0 remains the primary software warranty and liability document, while non-waivable legal rights remain unaffected.

Build metadata: `versionName 0.24.2`, `versionCode 191`.
