# Xylune 0.23.1

- Add a unified cloud-provider layer alongside Android's scoped folder picker.
- Add OneDrive app-folder support using Authorization Code + PKCE and `Files.ReadWrite.AppFolder`.
- Add Dropbox App-folder support using scoped permissions, PKCE, refresh tokens, and resumable uploads.
- Add direct HTTPS WebDAV and Nextcloud backup browsing, upload, preview, restore, and deletion.
- Add AWS Signature Version 4 support for S3, Cloudflare R2, Backblaze B2, MinIO, and compatible storage.
- Encrypt OAuth sessions, WebDAV credentials, and S3 credentials with Android Keystore and exclude them from portable backups.
- Add provider-specific connection tests, errors, backup lists, previews, and confirmed deletion.
- Configure public Microsoft client IDs and Dropbox app keys through GitHub Actions repository variables rather than secrets.
- Document the exact external setup required for Google Cloud, Microsoft Entra, Dropbox, WebDAV, and S3.
