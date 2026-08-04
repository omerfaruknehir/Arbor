from pathlib import Path

path = Path("app/src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt")
text = path.read_text()
old = '''    val rawPercent = Regex("""(?<!\\d)(100|[0-9]{1,2})%""").findAll(combined).lastOrNull()
        ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
    val latest = combined.lineSequence().map(String::trim).lastOrNull(String::isNotBlank).orEmpty()
    return PackageInstallProgress(
        phase = inferPackagePhase(latest, fallbackPhase),
        percent = rawPercent?.let { rangeStart + it.coerceIn(0f, 1f) * (rangeEnd - rangeStart) },
'''
new = '''    val rawPercent = Regex("""(?<!\\d)(100|[0-9]{1,2})%""").findAll(combined).lastOrNull()
        ?.groupValues?.getOrNull(1)?.toFloatOrNull()?.div(100f)
    val counterPercent = Regex("""\\((\\d+)/(\\d+)\\)""").findAll(combined).lastOrNull()?.let { match ->
        val current = match.groupValues.getOrNull(1)?.toFloatOrNull() ?: return@let null
        val total = match.groupValues.getOrNull(2)?.toFloatOrNull()?.takeIf { it > 0f } ?: return@let null
        (current / total).coerceIn(0f, 1f)
    }
    val latest = combined.lineSequence().map(String::trim).lastOrNull(String::isNotBlank).orEmpty()
    return PackageInstallProgress(
        phase = inferPackagePhase(latest, fallbackPhase),
        percent = (rawPercent ?: counterPercent)?.let { rangeStart + it.coerceIn(0f, 1f) * (rangeEnd - rangeStart) },
'''
if text.count(old) != 1:
    raise SystemExit(f"Expected one package progress parser, found {text.count(old)}")
path.write_text(text.replace(old, new, 1))
