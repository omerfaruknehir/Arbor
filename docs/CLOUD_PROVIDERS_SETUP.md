# Cloud provider setup

Arbor supports six cloud paths:

1. Android's scoped folder picker
2. Google Drive `appDataFolder`
3. OneDrive application folder
4. Dropbox App folder
5. Nextcloud or generic WebDAV
6. S3-compatible object storage

No provider refresh token, user password, S3 access key, or storage secret belongs in GitHub Actions. Those values are entered by the user and encrypted locally with Android Keystore.

## GitHub Actions configuration

Two provider values are public application identifiers and are embedded in the APK. Configure them as **Repository variables**, not secrets:

- `ARBOR_MICROSOFT_CLIENT_ID`
- `ARBOR_DROPBOX_APP_KEY`

Repository path: **Settings → Secrets and variables → Actions → Variables**.

Release-signing keystore passwords remain GitHub **Secrets**. OAuth client secrets are neither needed nor safe in a native Android application.

## Google Drive

The repository owner must create or select one Google Cloud project:

1. Enable Google Drive API.
2. Configure the OAuth consent screen.
3. Create an Android OAuth client for every officially distributed package/signing pair.
4. Register the exact package and SHA-1 shown by Arbor's diagnostic card.
5. Keep the requested scope limited to `https://www.googleapis.com/auth/drive.appdata`.

The normal public GitHub release currently uses package `app.arbor.chat.debug`. Protected production releases use `app.arbor.chat` and need their private release certificate SHA-1 registered separately in the same Cloud project.

Google Android OAuth clients have no client secret to embed.

## OneDrive

Create one Microsoft Entra app registration:

1. Choose supported account types. For personal OneDrive plus work/school accounts, allow organizational directories and personal Microsoft accounts.
2. Add the Microsoft Graph delegated permission `Files.ReadWrite.AppFolder`.
3. Add the Android platform using Arbor's package name and signature hash. Arbor shows the exact `msauth://...` redirect URI in the provider card.
4. Enable public-client/native flows.
5. Put the Application (client) ID in repository variable `ARBOR_MICROSOFT_CLIENT_ID`.

Arbor uses Authorization Code + PKCE and requests `offline_access`; do not create or embed a client secret.

## Dropbox

Create one scoped Dropbox API app:

1. Choose **App folder** access, not Full Dropbox.
2. Enable `account_info.read`, `files.metadata.read`, `files.content.read`, and `files.content.write`.
3. Add redirect URI `db-APP_KEY://2/token`, replacing `APP_KEY` with the app key.
4. Put the app key in repository variable `ARBOR_DROPBOX_APP_KEY`.

Arbor uses Authorization Code + PKCE with refresh tokens. A Dropbox app secret is not used by the Android app.

## Nextcloud / WebDAV

No developer project or repository variable is required. Each user enters:

- a dedicated HTTPS WebDAV folder URL
- username
- password or, preferably, an app password

A typical Nextcloud URL is:

```text
https://cloud.example.com/remote.php/dav/files/USERNAME/Arbor/
```

Arbor refuses unencrypted HTTP endpoints and stores credentials in encrypted local preferences.

## S3-compatible storage

No repository-level cloud account is required. Each user enters an HTTPS endpoint, region, bucket, prefix, access key, and secret key. The client uses AWS Signature Version 4 and supports AWS S3, Cloudflare R2, Backblaze B2 S3, MinIO, and compatible services.

Use a dedicated key restricted to the selected bucket and prefix. A minimal policy should allow only listing that prefix and getting, putting, and deleting objects inside it. Never put a user's S3 keys in GitHub Actions.
