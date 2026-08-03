# Retained Termux recipes

These files are retained as build provenance for the exact Termux packages
whose binaries Xylune redistributes. They are historical upstream recipes, not
Xylune's authoritative license classification.

In particular, `libtalloc-build.sh` labels the complete talloc source package
as GPL-3.0. The pinned talloc 2.4.3 archive also contains GPL-only ancillary
Python/test files, but its `LICENSE`, `talloc.c`, and `talloc.h` identify the
shared C library shipped by Xylune as LGPL-3.0-or-later.

Do not edit a retained recipe merely to make its metadata agree with Xylune's
binary-level notice. Update `THIRD_PARTY_NOTICES.md` and the provenance verifier
when a packaged component or source archive changes.
