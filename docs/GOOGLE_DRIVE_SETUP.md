# Google Drive app-data authorization

Arbor's direct Google Drive target uses only `https://www.googleapis.com/auth/drive.appdata` and stores backups in Drive's hidden `appDataFolder`. Google requires every Android build requesting this token to be registered by **package name and signing SHA-1**.

No OAuth client secret belongs in the APK or repository.

## Google Cloud configuration

1. Create or select a Google Cloud project.
2. Enable **Google Drive API** for that project.
3. Configure the OAuth consent screen and add the accounts/test users permitted by the project's publishing state.
4. In **APIs & Services → Credentials**, create an **OAuth client ID** of type **Android**.
5. Enter the package and signing SHA-1 for the build being distributed.
6. Reopen Arbor and select **Backup & transfer → Connect Google Drive**.

## Public GitHub release identity

When protected production-signing secrets are not supplied, Arbor's GitHub release workflow deliberately preserves update compatibility with its established public signer:

- Package: `app.arbor.chat.debug`
- Signing SHA-1: `59:54:74:CB:CC:00:73:74:65:3A:70:53:DF:37:92:DB:ED:16:AD:99`

Register that exact pair for APKs published by the repository's normal public release workflow.

## Protected production builds

A protected release uses package `app.arbor.chat` and the private release certificate supplied through `ARBOR_KEYSTORE_*`. Create a separate Android OAuth client using that private certificate's SHA-1. Do not reuse the public debug fingerprint.

## Forks and locally signed builds

Each distinct package/signing-certificate pair needs its own Android OAuth client. Arbor 0.22.4 and later displays the current package, SHA-1, and SHA-256 directly in the error card and provides a copy button, so the values do not need to be guessed.

## Why `UNREGISTERED_ON_API_CONSOLE` appears

Account selection can succeed before Google validates the requesting Android OAuth identity. If the package/SHA-1 pair is absent, belongs to another Cloud project, or the Drive API is not enabled, Google Play services returns `UNREGISTERED_ON_API_CONSOLE` instead of an access token.
