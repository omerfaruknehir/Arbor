# Arbor 0.20.25

## Restore during first-run setup

The first setup page can now open a local `.arborbackup`, Google Drive's hidden Arbor app storage, or a single app backup folder exposed by Google Drive, OneDrive, Dropbox, Nextcloud, USB, or another Android document provider. The cloud paths remain least-privilege: Arbor receives access only to its hidden Drive app folder or the one folder explicitly selected by the user.

## Portable app configuration

Portable backups can include theme and UI settings, new-chat defaults, developer settings, provider endpoints and model configuration, projects, system-prompt profiles, automation/package policy, and the selected Linux distribution. Project and prompt-profile links are remapped when chats are imported.

API keys, OAuth sessions, provider authorization headers, database encryption keys, cloud grants, drafts, and transient navigation state remain excluded. After restoring during setup, Arbor advances to provider setup so credentials can be reconnected deliberately.
