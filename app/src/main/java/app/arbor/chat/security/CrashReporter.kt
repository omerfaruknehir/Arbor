package app.arbor.chat.security

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CrashReporter(context: Context) {
    private val reportFile = File(context.filesDir, "last-crash.txt")
    private val renderSafeModeFile = File(context.filesDir, "render-safe-mode.flag")
    private val _renderSafeMode = MutableStateFlow(renderSafeModeFile.exists())
    val renderSafeMode: StateFlow<Boolean> = _renderSafeMode.asStateFlow()

    init {
        val previous = runCatching { reportFile.takeIf(File::isFile)?.readText().orEmpty() }.getOrDefault("")
        if (isGeneratedRendererCrash(previous)) setRenderSafeMode(true)
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = redact(StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString())
                reportFile.writeText(
                    "Arbor crash at ${Instant.now()}\n" +
                        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
                        "Device ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                    "Thread ${thread.name.take(80)}\n\n${trace.take(MAX_REPORT_CHARS)}",
                )
                if (isGeneratedRendererCrash(trace)) setRenderSafeMode(true)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): String? = reportFile.takeIf(File::exists)?.readText()?.takeIf(String::isNotBlank)

    fun clear() {
        if (reportFile.exists()) reportFile.writeText("")
    }

    fun setRenderSafeMode(enabled: Boolean) {
        _renderSafeMode.value = enabled
        runCatching {
            if (enabled) renderSafeModeFile.writeText("Generated rendering disabled after a crash.\n")
            else renderSafeModeFile.delete()
        }
    }

    private fun isGeneratedRendererCrash(value: String): Boolean =
        "app.arbor.chat.widgets." in value ||
            "app.arbor.chat.ui.NativeDiagram" in value ||
            "app.arbor.chat.ui.NativeChart" in value

    private fun redact(value: String): String = value
        .replace(Regex("(?i)(authorization\\s*[:=]\\s*(?:bearer\\s+)?)[^\\s,}]+"), "$1[redacted]")
        .replace(Regex("(?i)([?&](?:key|api_key|token|access_token)=)[^&\\s]+"), "$1[redacted]")
        .replace(Regex("(?i)(\\\"(?:apiKey|api_key|prompt|messages|content)\\\"\\s*:\\s*\\\")[^\\\"]{1,800}"), "$1[redacted]")
        .replace(Regex("\\b(?:sk|key)-[A-Za-z0-9_-]{12,}"), "[redacted-key]")

    companion object { private const val MAX_REPORT_CHARS = 24_000 }
}
