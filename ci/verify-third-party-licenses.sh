#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

talloc_archive="third_party/sources/talloc-2.4.3.tar.gz"
talloc_license="third_party/licenses/LGPL-3.0-or-later.txt"
asset_license="app/src/main/assets/licenses/LGPL-3.0-or-later.txt"
catalog_license="licenses/texts/LGPL-3.0-or-later.txt"
expected_archive_sha256="dc46c40b9f46bb34dd97fe41f548b0e8b247b77a918576733c528e83abd854dd"

actual_archive_sha256="$(sha256sum "$talloc_archive" | cut -d ' ' -f 1)"
if [[ "$actual_archive_sha256" != "$expected_archive_sha256" ]]; then
  echo "talloc source archive SHA-256 mismatch" >&2
  exit 1
fi

tar -xOf "$talloc_archive" talloc-2.4.3/LICENSE | cmp -s - "$talloc_license" || {
  echo "Repository talloc license is not the exact upstream LICENSE" >&2
  exit 1
}

cmp -s "$talloc_license" "$asset_license" || {
  echo "APK-facing talloc license differs from the verified repository copy" >&2
  exit 1
}

cmp -s "$talloc_license" "$catalog_license" || {
  echo "Offline catalog talloc license differs from the verified repository copy" >&2
  exit 1
}

grep -Fq '"spdx": "LGPL-3.0-or-later"' licenses/components/talloc.json || {
  echo "Offline catalog does not classify talloc as LGPL-3.0-or-later" >&2
  exit 1
}

grep -Fq '"spdx": "GPL-2.0-or-later"' licenses/components/proot.json || {
  echo "Offline catalog does not classify PRoot as GPL-2.0-or-later" >&2
  exit 1
}

for source_file in talloc.c talloc.h; do
  tar -xOf "$talloc_archive" "talloc-2.4.3/$source_file" \
    | grep -F "Lesser General Public License" >/dev/null || {
      echo "$source_file does not carry the expected LGPL notice" >&2
      exit 1
    }
done

for notice in THIRD_PARTY_NOTICES.md app/src/main/assets/THIRD_PARTY_NOTICES.md; do
  grep -Fq "Packaged library license: LGPL-3.0-or-later" "$notice" || {
    echo "$notice does not classify the packaged talloc library as LGPL-3.0-or-later" >&2
    exit 1
  }
  if grep -Fq "License: GPL-3.0 in the Termux package metadata" "$notice"; then
    echo "$notice contains the obsolete talloc GPL-3.0 classification" >&2
    exit 1
  fi
done

for abi in arm64-v8a x86_64; do
  talloc_binary="app/src/main/jniLibs/$abi/libtalloc.so"
  proot_binary="app/src/main/jniLibs/$abi/libarbor_proot.so"
  test -f "$talloc_binary"
  test -f "$proot_binary"
  readelf -d "$proot_binary" | grep -F "Shared library: [libtalloc.so]" >/dev/null || {
    echo "$abi PRoot does not use the replaceable shared talloc boundary" >&2
    exit 1
  }
done

echo "Verified talloc 2.4.3 as LGPL-3.0-or-later for both packaged ABIs."
