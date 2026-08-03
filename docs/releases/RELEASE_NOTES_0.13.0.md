# Xylune 0.13.0

Xylune 0.13.0 moves common chat controls out of Settings and into the composer.

## Composer

- Persistent Thinking switch and Minimal/Low/Medium/High effort slider.
- `+` sheet for files, photos, camera capture, Web search, Deep Research, Python, and Linux.
- Web, research, and tool choices remain per chat and become the starting defaults for future chats.

## Settings

- Global Settings is reachable from the navigation drawer only.
- Global tabs are Defaults, Automation, App, and Providers.
- The chat overflow opens a compact advanced configuration sheet for context/output/Working/system-prompt settings.

## Deep Research

Deep Research persists per chat, automatically enables Web search, plans in Working, performs repeated focused search and page-reading rounds, compares date-sensitive or conflicting claims, uses uploaded files, and asks the model for a structured sourced report. The execution budget remains bounded and all ordinary URL/security restrictions still apply.

## Optional hybrid token counting

Hybrid counting is disabled by default. When enabled, Anthropic and Gemini use provider-side count endpoints. OpenAI-compatible and recognized local model families use local family-aware estimates, with the generic estimator as a final fallback. A failed preflight count never prevents generation, and provider-reported usage remains authoritative after the response.

## Compatibility

- Database migration: 10 → 11.
- Debug version code: 20.
- Minimum Android: API 26.
- Target Android: API 35.
