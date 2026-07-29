# Arbor 0.20.6

Arbor's legal notices are now useful inside the app instead of being scattered
across repository links.

## Offline licenses

- Open **Settings → About Arbor → Licenses & notices** to browse the components
  included in the installed build.
- Search by library, category, Maven module, or license.
- Open any component for its purpose, version, official project page,
  attribution note, and selectable full license text.
- The catalog, icons, and documents are bundled in the APK and work without a
  network connection.

## Build integrity

The repository's `licenses/` directory is now the source of truth. Builds
generate the APK catalog only from those local files and fail when metadata is
invalid, a referenced icon or document is missing, or a shipped Gradle runtime
dependency has not been documented.

The release workflow now detects a new app version on `main`, verifies and
builds it, creates the matching version tag, and publishes APK, AAB,
instrumentation APK, checksums, and these release notes. Same-version commits
do not produce duplicate releases.

This release also corrects PRoot's expression to GPL-2.0-or-later, preserves
talloc's LGPL-3.0-or-later classification and required GPL v3 companion text,
and avoids presenting Google ML Kit's binary terms as Apache-2.0.

Existing chats, credentials, OAuth sessions, workspaces, attachments, and
Linux environments are preserved.
