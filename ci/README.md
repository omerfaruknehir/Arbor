# CI signing

`xylune-debug.keystore` is an intentionally public Android debug key. It signs only the installable test build with package ID `app.xylune.chat.debug`, giving local and GitHub Actions builds a stable signer so one debug release can update another.

It is not a secret and must never be used for the production package. Store builds use a separate protected release key configured through the `XYLUNE_KEYSTORE_*` values documented in [BUILDING.md](../BUILDING.md).

Debug certificate SHA-256:

```text
B9:D9:5D:F7:AD:06:61:55:93:41:62:32:27:CB:0C:C5:21:85:24:71:5A:F5:D7:B3:1A:F2:EC:D0:E7:D5:77:B9
```
