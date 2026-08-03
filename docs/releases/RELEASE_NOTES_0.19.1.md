# Xylune 0.19.1

- Fix ChatGPT OAuth token exchange failing with `Unable to resolve host auth.openai.com` while Xylune is behind the browser.
- Return Xylune to the foreground before exchanging the OAuth authorization code.
- Add active-network DNS resolution and bounded DNS retries.
- Keep the extension-free native PKCE flow and all 0.19.0 provider behavior.
