# Arbor 0.19.3

## Multiple ChatGPT OAuth providers

ChatGPT OAuth is now a normal multi-instance provider type. Add any number of named ChatGPT providers, and each one keeps its own encrypted session, discovered models, quota snapshot, refresh lifecycle, and connection state.

- Adding a provider immediately opens native browser sign-in.
- Arbor requests a fresh authentication prompt so a second provider does not silently reuse the first account.
- Rename, reconnect, disconnect, or remove one provider without affecting the others.
- Existing 0.19.0–0.19.2 OAuth credentials migrate automatically into the original ChatGPT provider.
- Chats select each account through its own provider/model entries.

## Accurate usage reset times

Quota reset labels now use the server-reported reset duration/timestamp instead of deriving a reset from the quota-window length. The UI shows a live countdown and the exact local date/time, with second-level countdowns under one hour and no invalid minute rollover.

## Click-through performance overlay

Developer settings now expose independent panel and text opacity controls from 0% to 100%. The overlay remains visual-only and has no pointer-input or clickable modifier, so it does not consume taps, scrolling, drawer gestures, or back navigation.

## Compatibility

- Version code: 113
- Application ID: `app.arbor.chat.debug`
- Existing Room schemas, app data, providers, chats, workspaces, appearance settings, and debug-signing compatibility are preserved.
