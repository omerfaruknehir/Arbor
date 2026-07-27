# Arbor 0.19.1

- Fix ChatGPT OAuth token exchange failing with `Unable to resolve host auth.openai.com` while Arbor is behind the browser.
- Return Arbor to the foreground before exchanging the OAuth authorization code.
- Add active-network DNS resolution and bounded DNS retries.
- Keep the extension-free native PKCE flow and all 0.19.0 provider behavior.
