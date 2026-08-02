#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "app/src/main/java/app/arbor/chat/security/AppInstallIdentity.kt"
content = path.read_text()
replacements = {
    "val certificate = signatures.firstOrNull()?.toByteArray().orEmpty()":
        "val certificate = signatures.firstOrNull()?.toByteArray() ?: byteArrayOf()",
    "private fun Context.currentSigningCertificates(): Array<Signature> {":
        "private fun Context.currentSigningCertificates(): Array<out Signature> {",
}
for old, new in replacements.items():
    if content.count(old) != 1:
        raise RuntimeError(f"Expected one signing identity fragment: {old}")
    content = content.replace(old, new, 1)
path.write_text(content)
print("Corrected Android signing identity types")
