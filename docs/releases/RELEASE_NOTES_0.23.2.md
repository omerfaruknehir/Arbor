# Xylune 0.23.2

This hotfix repairs cloud restore and replaces generic provider placeholders in the first-run restore screen.

## Fixed

- OneDrive, Dropbox, WebDAV/Nextcloud, and S3-compatible backups can now be downloaded, inspected, and imported. Their downloaded archive directory is explicitly exposed through Xylune's private FileProvider.
- The restore action is labeled **Review & restore** so selecting a backup clearly opens the archive summary before import.
- Restore failures remain in the cloud dialog with the provider error instead of silently appearing to do nothing.

## Interface

- Google Drive, OneDrive, Dropbox, and Nextcloud/WebDAV now use recognizable provider marks.
- S3-compatible storage uses a neutral storage icon because it may refer to AWS S3, Cloudflare R2, Backblaze B2, MinIO, or another compatible service.

The package remains `app.xylune.chat` and the public update-compatible signing certificate is unchanged.
