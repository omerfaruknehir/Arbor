# Xylune 0.23.4

This maintenance release makes GitHub release packaging deterministic.

## Stable release assets

Every Xylune release now contains exactly these six files, in this canonical order:

1. `Xylune-<version>-release.apk`
2. `Xylune-<version>-release.aab`
3. `Xylune-<version>-source.zip`
4. `Xylune-<version>-source.tar.gz`
5. `Xylune-<version>-release.json`
6. `Xylune-<version>-SHA256.txt`

The release workflow now:

- rejects missing, extra, or renamed files before publishing;
- uploads assets one at a time instead of passing an unordered wildcard to GitHub;
- keeps the release as a draft until all six files are present and verified;
- generates the checksum list in the same stable order;
- uses the same explicit six-file list for the Actions artifact and GitHub release.
