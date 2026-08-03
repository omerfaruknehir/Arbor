# Google Drive app-data authorization

Xylune requests only `https://www.googleapis.com/auth/drive.appdata` and stores backups in Drive's hidden `appDataFolder`. No OAuth client secret belongs in the APK or repository.

## Google Cloud configuration

1. Enable Google Drive API.
2. Configure and publish the OAuth consent screen, or add permitted test users while in Testing.
3. Use these public URLs:
   - Homepage: `https://omerfaruknehir.github.io/Xylune/`
   - Privacy: `https://omerfaruknehir.github.io/Xylune/privacy/`
   - Terms: `https://omerfaruknehir.github.io/Xylune/terms/`
4. Create an OAuth client ID of type Android.
5. Register the package and signing SHA-1 below.

## Public GitHub release identity

- Package: `app.xylune.chat`
- SHA-1: `59:54:74:CB:CC:00:73:74:65:3A:70:53:DF:37:92:DB:ED:16:AD:99`
- SHA-256: `B9:D9:5D:F7:AD:06:61:55:93:41:62:32:27:CB:0C:C5:21:85:24:71:5A:F5:D7:B3:1A:F2:EC:D0:E7:D5:77:B9`

A protected release keeps package `app.xylune.chat` but uses its private signing certificate, so it needs another Android OAuth client for that certificate SHA-1.

Xylune displays its current package, SHA-1, and SHA-256 in the Google Drive diagnostic card when registration is missing.
