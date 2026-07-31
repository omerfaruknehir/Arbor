# Arbor 0.20.24

## Linux environment backup

Portable backups can now include installed Ubuntu, Debian, and Alpine root filesystems. Arbor stores each environment as a permission-preserving nested tar archive and restores it through a staging directory before activation.

## Least-privilege cloud backup

- **App-only cloud folder:** choose one folder through Android's system document picker. Arbor receives persistent access only to that folder. This works with document providers exposed by Google Drive, OneDrive, Dropbox, Nextcloud, USB storage, and local storage.
- **Google Drive app storage:** authorize the non-sensitive `drive.appdata` scope only. Arbor writes backups to Drive's hidden appDataFolder and never requests My Drive access.
- **Android/Google One app backup:** enabled only for small, non-secret preferences. The encrypted database, its device-bound key, credentials, attachments, workspaces, and Linux files remain excluded.

Direct Google Drive app-data backup requires the Drive API and an Android OAuth client configured for Arbor's package name and signing certificate in Google Cloud Console.
