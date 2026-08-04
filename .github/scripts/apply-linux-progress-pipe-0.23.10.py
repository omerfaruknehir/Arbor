from pathlib import Path


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one match in {path}, found {count}: {old[:160]!r}")
    path.write_text(text.replace(old, new, 1))


runtime = Path("app/src/main/java/app/xylune/chat/sandbox/UbuntuRuntime.kt")
replace_once(
    runtime,
    '''private fun stripAptStatusLines(value: String): String = value.lineSequence()
    .filterNot { AptStatusLine.matches(it.trim()) }
    .joinToString("\\n")

data class UbuntuPackageInstallResult(
''',
    '''private fun stripAptStatusLines(value: String): String = value.lineSequence()
    .filterNot { AptStatusLine.matches(it.trim()) }
    .joinToString("\\n")

internal fun drainCappedText(
    reader: java.io.Reader,
    output: StringBuilder,
    limit: Int,
) {
    require(limit >= 0) { "Output limit must not be negative" }
    val buffer = CharArray(8_192)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        synchronized(output) {
            val appendCount = minOf(count, (limit - output.length).coerceAtLeast(0))
            if (appendCount > 0) output.append(buffer, 0, appendCount)
        }
    }
}

data class UbuntuPackageInstallResult(
''',
)
replace_once(
    runtime,
    '''        try {
            input.bufferedReader().use { reader ->
                val buffer = CharArray(8_192)
                while (true) {
                    val remaining = synchronized(output) { limit - output.length }
                    if (remaining <= 0) break
                    val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    synchronized(output) { output.append(buffer, 0, count) }
                }
            }
        } catch (error: Throwable) {
''',
    '''        try {
            input.bufferedReader().use { reader ->
                // Keep draining the child pipe after the retained log reaches its cap.
                // Closing stdout/stderr early makes package maintainer scripts fail with EIO.
                drainCappedText(reader, output, limit)
            }
        } catch (error: Throwable) {
''',
)

screen = Path("app/src/main/java/app/xylune/chat/ui/SandboxScreen.kt")
replace_once(
    screen,
    '''package app.xylune.chat.ui

import android.text.format.Formatter
''',
    '''package app.xylune.chat.ui

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
''',
)
replace_once(
    screen,
    '''import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
''',
    '''import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
''',
)
replace_once(
    screen,
    '''import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
''',
    '''import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
''',
)
replace_once(
    screen,
    '''import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
''',
    '''import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
''',
)
replace_once(
    screen,
    '''private enum class WorkspaceSection { PYTHON, LINUX }

@OptIn(ExperimentalMaterial3Api::class)
''',
    '''private enum class WorkspaceSection { PYTHON, LINUX }

private fun formatSetupDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s"
}

@OptIn(ExperimentalMaterial3Api::class)
''',
)
replace_once(
    screen,
    '''                    Text(ubuntuStatus.detail, style = MaterialTheme.typography.bodySmall)
                    if (ubuntuStatus.sizeBytes > 0) {
''',
    '''                    if (!linuxSetupActive) {
                        Text(ubuntuStatus.detail, style = MaterialTheme.typography.bodySmall)
                    }
                    if (ubuntuStatus.sizeBytes > 0) {
''',
)
old_progress = '''                    if (linuxSetupActive) {
                        ubuntuStatus.progress?.let { progress ->
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val step = ubuntuStatus.currentStep.coerceAtLeast(1)
                            val total = ubuntuStatus.totalSteps.coerceAtLeast(step)
                            Text("Step $step of $total", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            ubuntuStatus.progress?.let {
                                Text("${(it.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (ubuntuStatus.startedAtMs > 0L) {
                            Text(
                                "Elapsed: ${(clock - ubuntuStatus.startedAtMs).coerceAtLeast(0L) / 1_000}s • keep Xylune open until setup finishes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
'''
new_progress = '''                    if (linuxSetupActive) {
                        val measuredProgress = ubuntuStatus.progress?.coerceIn(0f, 1f)
                        val animatedProgress by animateFloatAsState(
                            targetValue = measuredProgress ?: 0f,
                            animationSpec = tween(durationMillis = 450),
                            label = "linux-setup-progress",
                        )
                        val step = ubuntuStatus.currentStep.coerceAtLeast(1)
                        val total = ubuntuStatus.totalSteps.coerceAtLeast(step)
                        val elapsedMs = if (ubuntuStatus.startedAtMs > 0L) {
                            (clock - ubuntuStatus.startedAtMs).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .78f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "Step $step of $total",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        measuredProgress?.let { "${(it * 100).toInt()}%" } ?: "Working…",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                if (measuredProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { animatedProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(10.dp)
                                            .clip(RoundedCornerShape(999.dp)),
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                }
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    repeat(total) { index ->
                                        val segmentStep = index + 1
                                        val segmentColor = when {
                                            segmentStep < step -> MaterialTheme.colorScheme.primary
                                            segmentStep == step -> MaterialTheme.colorScheme.primary.copy(alpha = .62f)
                                            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
                                        }
                                        Surface(
                                            color = segmentColor,
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.weight(1f).height(4.dp),
                                        ) {}
                                    }
                                }
                                Text(
                                    ubuntuStatus.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        "Elapsed ${formatSetupDuration(elapsedMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "Keep Xylune open",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
'''
replace_once(screen, old_progress, new_progress)

test = Path("app/src/test/java/app/xylune/chat/sandbox/PackageInstallProgressTest.kt")
replace_once(
    test,
    '''import org.junit.Assert.assertTrue
import org.junit.Test
''',
    '''import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Reader
''',
)
replace_once(
    test,
    '''    @Test
    fun fallsBackToHumanReadableAptOutput() {
''',
    '''    @Test
    fun cappedOutputStillDrainsTheEntireChildPipe() {
        val source = "x".repeat(32_768)
        var consumed = 0
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (consumed >= source.length) return -1
                val count = minOf(length, source.length - consumed)
                source.toCharArray(buffer, offset, consumed, consumed + count)
                consumed += count
                return count
            }

            override fun close() = Unit
        }
        val retained = StringBuilder()

        drainCappedText(reader, retained, 256)

        assertEquals(source.length, consumed)
        assertEquals(256, retained.length)
    }

    @Test
    fun fallsBackToHumanReadableAptOutput() {
''',
)

build = Path("app/build.gradle.kts")
replace_once(
    build,
    '''        versionCode = 178
        versionName = "0.23.9"
''',
    '''        versionCode = 179
        versionName = "0.23.10"
''',
)

changelog = Path("CHANGELOG.md")
changelog.write_text(
    '''## 0.23.10 — 2026-08-04

- Redesign Linux setup progress as a thicker rounded, animated indicator with visible stage segments, current step, percentage, current activity, and readable elapsed time.
- Keep draining Linux process stdout and stderr after the retained log reaches its memory cap, preventing package maintainer scripts such as `update-ca-certificates` from failing with `I/O error`.
- Add a regression test proving capped output capture consumes the complete child-process stream.

''' + changelog.read_text()
)

release_notes = Path("docs/releases/RELEASE_NOTES_0.23.10.md")
release_notes.write_text(
    '''# Xylune 0.23.10

## Linux setup progress

The installer now uses a thicker rounded progress bar with smooth value changes, eight compact stage segments, the current step, percentage, current package activity, and human-readable elapsed time.

## Ubuntu installation reliability

Xylune previously stopped reading a child process pipe as soon as one megabyte of output had been retained. Closing that pipe early could make verbose Debian package scripts fail with an `I/O error`; the screenshot from `update-ca-certificates` is consistent with that failure mode. Output retention remains capped, but the complete pipe is now drained until the process exits.
'''
)
