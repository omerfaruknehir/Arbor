# Arbor 0.20.5

Arbor 0.20.5 corrects the bundled talloc library's license classification and
adds an automated provenance check.

## Licensing correction

- Classify the packaged talloc 2.4.3 shared library as
  **LGPL-3.0-or-later**, matching the exact upstream archive's `LICENSE`,
  `talloc.c`, and `talloc.h`.
- Explain why the retained historical Termux recipe's package-wide GPL-3.0
  label is broader than the library binary shipped by Arbor.
- Include the LGPL v3 text in both the repository and APK assets.
- Add a dedicated **Third-party notices** entry to About Arbor so the bundled
  runtime licenses and corresponding-source references are directly reachable.
- Retain the GNU GPL v3 text alongside it because LGPL v3 requires
  distributors of linked object code to provide both documents; its presence
  does not classify `libtalloc.so` as GPL-3.0.
- Keep PRoot separately and accurately disclosed under GPL-2.0.

## Regression protection

- Add a CI verifier for the pinned talloc source SHA-256, upstream license
  identity, mirrored notices, APK-facing license copy, packaged ABI libraries,
  and dynamic dependency boundary.
- Run the verifier in normal Android CI, manually requested signed builds, and
  tagged GitHub releases before any Android artifact is built.

## Compatibility

Package identity, debug signing key, minimum and target SDKs, Room schema,
chats, settings, credentials, OAuth sessions, workspaces, attachments, and
Linux workspace data are unchanged.

Download `Arbor-0.20.5-debug.apk` from the release assets. It is debug-signed
for direct testing and uses package ID `app.arbor.chat.debug`.
