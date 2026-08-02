from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    if old not in text:
        raise SystemExit(f"Expected patch anchor not found in {path}: {old[:180]!r}")
    file.write_text(text.replace(old, new, 1))


source = "app/src/main/java/app/arbor/chat/widgets/WidgetDataSources.kt"

replace_once(
    source,
    '''import okhttp3.Request
import okhttp3.Response
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
''',
    '''import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
''',
)

replace_once(
    source,
    '''                        val transient = when (error) {
                            is WidgetHttpFailure -> error.isTransient
                            is UnknownHostException, is ConnectException, is SocketTimeoutException -> true
                            else -> false
                        }
''',
    '''                        val transient = when (error) {
                            is WidgetHttpFailure -> error.isTransient
                            // DNS failures, TLS truncation, premature EOF, connection resets,
                            // and timeouts are transport failures. A widget with complete
                            // fallbacks must remain compilable when any of these occur.
                            is IOException -> true
                            else -> false
                        }
''',
)

replace_once(
    source,
    '''            val content = body.source().readUtf8(MAX_BODY_BYTES + 1)
            require(content.toByteArray().size <= MAX_BODY_BYTES) { "${source.id} is larger than 1 MB" }
''',
    '''            // BufferedSource.readUtf8(byteCount) requires *exactly* byteCount bytes and
            // throws EOFException for every normal response smaller than the 1 MB ceiling.
            // Read incrementally instead: stop cleanly at EOF, but consume one extra byte
            // when present so oversized/chunked responses are still rejected safely.
            val content = readWidgetHttpBody(
                source = body.source(),
                maxBytes = MAX_BODY_BYTES,
                tooLargeMessage = "${source.id} is larger than 1 MB",
            )
''',
)

replace_once(
    source,
    '''    private fun sourceSafe(value: String): String = value.take(120)
    private val INDEX = Regex("\\[(\\d+)]")
}''',
    '''    private fun sourceSafe(value: String): String = value.take(120)
    private val INDEX = Regex("\\[(\\d+)]")
}

/**
 * Reads at most [maxBytes] without using BufferedSource.readUtf8(byteCount), whose
 * exact-length contract throws EOFException for ordinary shorter HTTP bodies.
 *
 * One additional byte is consumed when available so unknown-length and chunked
 * responses cannot bypass the size ceiling.
 */
internal fun readWidgetHttpBody(
    source: BufferedSource,
    maxBytes: Long,
    tooLargeMessage: String = "Widget HTTP response is larger than $maxBytes bytes",
): String {
    require(maxBytes >= 0L && maxBytes < Long.MAX_VALUE) { "Invalid widget HTTP body limit" }
    val buffer = Buffer()
    val probeLimit = maxBytes + 1L
    while (buffer.size < probeLimit) {
        val read = source.read(buffer, minOf(8_192L, probeLimit - buffer.size))
        if (read == -1L) break
    }
    require(buffer.size <= maxBytes) { tooLargeMessage }
    return buffer.readUtf8()
}
''',
)


test = Path("app/src/test/java/app/arbor/chat/widgets/WidgetDataSourcesTest.kt")
if test.exists():
    raise SystemExit(f"Regression test already exists: {test}")
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(
    '''package app.arbor.chat.widgets

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataSourcesTest {
    @Test
    fun shortJsonBodyStopsAtEofWithoutThrowing() {
        val payload = "{\\\"latitude\\\":41.0082,\\\"longitude\\\":28.9784}"
        val source = Buffer().writeUtf8(payload)

        assertEquals(payload, readWidgetHttpBody(source, maxBytes = 1_024))
    }

    @Test
    fun exactLimitBodyIsAccepted() {
        val payload = "12345678"
        val source = Buffer().writeUtf8(payload)

        assertEquals(payload, readWidgetHttpBody(source, maxBytes = 8))
    }

    @Test
    fun oneByteOverLimitIsRejectedWithoutReadingUnboundedData() {
        val source = Buffer().writeUtf8("123456789more-data-that-must-not-be-consumed")

        val failure = runCatching {
            readWidgetHttpBody(source, maxBytes = 8)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("larger than 8 bytes") == true)
        assertEquals("more-data-that-must-not-be-consumed", source.readUtf8())
    }
}
'''
)

replace_once(
    "app/build.gradle.kts",
    '''        versionCode = 161
        versionName = "0.20.35"
''',
    '''        versionCode = 162
        versionName = "0.20.36"
''',
)

notes = Path("docs/releases/RELEASE_NOTES_0.20.36.md")
if notes.exists():
    raise SystemExit(f"Release notes already exist: {notes}")
notes.write_text(
    '''# Arbor 0.20.36

This release repairs the widget compiler network layer falsely reporting `EOFException` for every normal HTTPS JSON response, including Open-Meteo.

## Fixed

- Replaces the exact-length `readUtf8(1 MB + 1)` call which required every response to contain more than 1 MB and therefore threw `EOFException` for ordinary short API bodies.
- Reads response bodies incrementally until real EOF while retaining the strict 1 MB safety ceiling.
- Probes one byte beyond the ceiling so chunked and unknown-length oversized responses are still rejected without unbounded buffering.
- Treats genuine I/O failures, including premature EOF and TLS truncation, as transient during compiler preflight when complete offline fallbacks exist.
- Adds regression tests for short JSON, exact-limit, and oversized response bodies.
'''
)
