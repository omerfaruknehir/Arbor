#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    content = target.read_text()
    count = content.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected one match, found {count}")
    target.write_text(content.replace(old, new, 1))

# Kotlin string literals must escape the GitHub Actions dollar sign.
replace_once(
    "app/src/test/java/app/arbor/chat/ui/RepositoryUpdateIntegrationTest.kt",
    'assertTrue(workflow.contains("ARBOR_SOURCE_REPOSITORY: ${{ github.repository }}"))',
    'assertTrue(workflow.contains("ARBOR_SOURCE_REPOSITORY: \\${{ github.repository }}"))',
)

print("Corrected Arbor 0.22.4 integration details")
