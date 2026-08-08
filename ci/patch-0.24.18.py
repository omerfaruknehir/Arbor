from pathlib import Path

path = Path('app/build.gradle.kts')
text = path.read_text()
old = '        versionCode = 206\n        versionName = "0.24.17"'
new = '        versionCode = 207\n        versionName = "0.24.18"'
if text.count(old) != 1:
    raise SystemExit(f'Expected exactly one version block, found {text.count(old)}')
path.write_text(text.replace(old, new))
