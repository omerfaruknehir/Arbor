# Xylune 0.19.0

## Native Sign in with ChatGPT

- Adds a first-class **ChatGPT account** provider based on the Apache-2.0 `openai-oauth` protocol.
- Sign-in is one tap from **Settings → Providers**. Xylune opens the system browser and receives the OAuth callback itself on `localhost:1455`.
- No browser extension, WebView, Node.js runtime, local proxy, copied token, or manually entered API key is required.
- Uses OAuth authorization-code flow with PKCE and state validation.
- Stores access, refresh, and ID tokens in Xylune's encrypted secure store; tokens are never written to Room or exposed in provider settings.
- Refreshes expiring access tokens automatically and retries one failed request after a forced refresh.
- Sign-out removes the encrypted OAuth session and unregisters the provider without touching conversations.

## ChatGPT model and response support

- Discovers the models available to the signed-in ChatGPT account and adds them to Xylune's normal model catalog.
- Sends chats directly through the Codex Responses endpoint with streaming text, reasoning summaries, usage accounting, images, and native function tools.
- Preserves encrypted reasoning/output items across tool loops instead of reconstructing lossy assistant messages.
- Handles Responses-lite model metadata, including `additional_tools`, all-turn reasoning context, and the required internal response header.
- Keeps provider pricing at unconfigured/zero rather than pretending ChatGPT-plan usage has API-token pricing.

## Compatibility and security

- Rebased on Xylune 0.18.4, retaining its four-pass half-resolution blur and one-sided edge-softness repairs.
- Preserves package/application IDs, Room schema version, conversations, settings, workspaces, existing API credentials, and the retained debug signing certificate.
- Adds only a custom `xylune://oauth-complete` return intent; OAuth codes and tokens remain on the loopback connection and are not placed in that intent.
- This is an unofficial community integration and can stop working if the upstream ChatGPT/Codex authentication service changes.
