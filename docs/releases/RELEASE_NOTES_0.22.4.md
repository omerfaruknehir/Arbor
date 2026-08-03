# Xylune 0.22.4

- Replace the raw Google Drive `UNREGISTERED_ON_API_CONSOLE` message with an actionable OAuth-registration diagnostic.
- Show and copy the installed package name, signing SHA-1, signing SHA-256, required `drive.appdata` scope, and setup guide.
- Document the exact public GitHub release package/signing identity while keeping client secrets out of the app and repository.
- Embed the GitHub source repository and source commit into builds.
- Check that embedded repository's latest GitHub Release automatically once per day and manually from About Xylune.
- Make fork releases follow their own fork while rehosted APKs continue trusting the repository they were built from.
- Publish a signed release manifest containing package, version code, APK checksum, signing-certificate digest, and source commit.
- Offer direct APK downloads only when package and signing certificate match the installed build; otherwise open the release page safely.
