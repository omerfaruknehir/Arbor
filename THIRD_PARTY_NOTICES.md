# Xylune third-party runtime notices

Xylune embeds the Android launcher components below so its target-SDK 36 app can start a selected user-space Linux tooling environment without executing downloaded Android code. Linux root filesystems are **not** bundled in the APK: Xylune downloads the selected Ubuntu 26.04 archive from Canonical, Debian 13 PRoot-Distro artifact from Termux, or Alpine 3.24.1 minirootfs from Alpine Linux, and verifies a pinned published SHA-256 before extraction.

## PRoot (Termux fork)

- Version: `5.1.107.84`
- Project: <https://github.com/termux/proot>
- License: GPL-2.0-or-later
- Packaged binaries: Termux main repository packages for `aarch64` and `x86_64`
- Package SHA-256: `59ace3b02894a9b87348eb5ccf246ed52ec64465021839422a151d7128acfe97` (aarch64), `98f30502dcc3c455ed5562e7fe0b8c04619b2b08633b3701a7750a86c6287e5d` (x86_64)
- Corresponding source: `third_party/sources/termux-proot-v5.1.107.84.zip` (`a44ddbf18bc72c9780d56948b03aeda6d285392503ece0cae17cfc02e7bc7928`)

Xylune's packaged launcher changes the dynamic dependency string `libtalloc.so.2` to `libtalloc.so` so Android's APK native-library extractor will install it. No PRoot program logic is changed. The original Termux build recipe is included under `third_party/termux-recipes/`.

## talloc

- Version: `2.4.3`
- Project: <https://talloc.samba.org/>
- Packaged library license: LGPL-3.0-or-later
- Package SHA-256: `ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da` (aarch64), `7ca2eaae2e53b28228a01301bc410b62845403d6317c25b8e0a7f40681de0628` (x86_64)
- Corresponding source: `third_party/sources/talloc-2.4.3.tar.gz` (`dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd`)

The exact upstream archive's `LICENSE`, `talloc.c`, and `talloc.h` identify the
library as LGPL-3.0-or-later. The retained historical Termux recipe labels the
whole source package GPL-3.0 because the archive also contains GPL-only
ancillary Python/test material; that material is not part of Xylune's packaged
`libtalloc.so`. The GNU GPL v3 text remains alongside the LGPL v3 text because
LGPL v3 sections 3 and 4 require distributors of linked object code to
accompany it with both documents. This does not relicense talloc as GPL-3.0.

## libandroid-shmem

- Version: `0.7`
- Project: <https://github.com/termux/libandroid-shmem>
- License: BSD 3-Clause
- Package SHA-256: `0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6` (aarch64), `ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797` (x86_64)
- Corresponding source: `third_party/sources/libandroid-shmem-v0.7.tar.gz` (`1e5ff8459bc0a8c229dd8a94b27d119987e09ef3414331c2b5ebfff20b98e867`)

Verified native-source license texts remain under `third_party/licenses/`.
The build-validated `licenses/` catalog combines those native notices with
Android and Python runtime notices, local icons, descriptions, official source
links, SPDX classifications where applicable, and complete license documents.
It is generated into the APK for the offline **About Xylune → Licenses &
notices** screen. `ci/verify-third-party-licenses.sh` checks the talloc source
hash, upstream license, packaged notices, packaged ABI libraries, and
shared-library boundary on every verified build.

## Ubuntu Base 26.04

- Download origin: <https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/>
- arm64 SHA-256: `b2b46a37324ea1954e93f293fe6d7c2241daf2fc298c4022e6e4caceeed74cab`
- amd64 SHA-256: `046fcabb7f16f45a80ae11824664f2a07e01386c6fb1ed9dc1e225a66a6553a2`

Ubuntu, Debian, or Alpine packages installed later remain subject to their individual package licenses.

## OpenAI OAuth compatibility implementation

Xylune 0.19.0 includes a clean-room Kotlin/Android implementation interoperable with
`EvanZhouDev/openai-oauth`, based on its documented OAuth and Codex transport behavior.
The upstream project is Copyright 2026 Evan Zhou and OpenAI OAuth contributors and is
licensed under the Apache License, Version 2.0. No JavaScript package or browser extension
is bundled. See: https://github.com/EvanZhouDev/openai-oauth
