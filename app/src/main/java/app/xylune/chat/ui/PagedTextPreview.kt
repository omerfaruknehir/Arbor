package app.xylune.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.xylune.chat.data.AttachmentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

private const val TEXT_PAGE_BYTES = 48 * 1024
private const val EXTRACTED_PAGE_CHARS = 24_000

internal fun AttachmentEntity.isDiskTextPreviewable(): Boolean =
    mimeType.startsWith("text/") || displayName.substringAfterLast('.', "").lowercase() in setOf(
        "txt", "log", "md", "markdown", "json", "jsonl", "xml", "yaml", "yml", "toml",
        "csv", "tsv", "kt", "java", "py", "c", "cpp", "h", "hpp", "rs", "js", "ts",
        "css", "sql", "sh", "bash", "zsh", "properties", "ini", "conf",
    )

@Composable
internal fun DiskBackedTextPreview(file: File) {
    var offsets by remember(file.absolutePath) { mutableStateOf(listOf(0L)) }
    var pageIndex by remember(file.absolutePath) { mutableIntStateOf(0) }
    val offset = offsets.getOrElse(pageIndex) { 0L }
    val page by produceState<TextFilePage?>(null, file.absolutePath, offset, file.lastModified(), file.length()) {
        value = withContext(Dispatchers.IO) { readTextPage(file, offset) }
    }
    val current = page
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Selectable file preview"), fontWeight = FontWeight.SemiBold)
        when {
            current == null -> Text(uiText("Loading preview…"))
            current.error != null -> Text(uiText("Preview unavailable: ${current.error}"), color = MaterialTheme.colorScheme.error)
            else -> {
                SelectionContainer {
                    Text(
                        current.text,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        enabled = pageIndex > 0,
                    ) { Icon(Icons.AutoMirrored.Outlined.NavigateBefore, uiText("Previous text page")) }
                    Text(
                        uiText("Bytes ${current.start + 1}-${current.endExclusive} of ${current.totalBytes}"),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    IconButton(
                        onClick = {
                            val next = current.nextOffset ?: return@IconButton
                            val existing = offsets.indexOf(next)
                            if (existing >= 0) pageIndex = existing
                            else {
                                offsets = offsets + next
                                pageIndex = offsets.lastIndex
                            }
                        },
                        enabled = current.nextOffset != null,
                    ) { Icon(Icons.AutoMirrored.Outlined.NavigateNext, uiText("Next text page")) }
                }
                Text(
                    uiText("Only this page is held in memory. Save or share the file for full-file processing."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PagedExtractedTextPreview(text: String) {
    val pageCount = ((text.length + EXTRACTED_PAGE_CHARS - 1) / EXTRACTED_PAGE_CHARS).coerceAtLeast(1)
    var pageIndex by remember(text.length, text.take(64)) { mutableIntStateOf(0) }
    val safeIndex = pageIndex.coerceIn(0, pageCount - 1)
    val start = safeIndex * EXTRACTED_PAGE_CHARS
    val end = minOf(text.length, start + EXTRACTED_PAGE_CHARS)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Selectable extracted content"), fontWeight = FontWeight.SemiBold)
        SelectionContainer { Text(text.substring(start, end), style = MaterialTheme.typography.bodySmall) }
        if (pageCount > 1) Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { pageIndex-- }, enabled = safeIndex > 0) {
                Icon(Icons.AutoMirrored.Outlined.NavigateBefore, uiText("Previous extracted-text page"))
            }
            Text(uiText("Page ${safeIndex + 1} of $pageCount"), style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = { pageIndex++ }, enabled = safeIndex + 1 < pageCount) {
                Icon(Icons.AutoMirrored.Outlined.NavigateNext, uiText("Next extracted-text page"))
            }
        }
    }
}

private fun readTextPage(file: File, requestedOffset: Long): TextFilePage {
    if (!file.isFile) return TextFilePage(error = "File no longer exists")
    return runCatching {
        RandomAccessFile(file, "r").use { input ->
            val total = input.length()
            val start = requestedOffset.coerceIn(0L, total)
            input.seek(start)
            val target = minOf(TEXT_PAGE_BYTES.toLong(), total - start).toInt()
            val output = ByteArrayOutputStream(target.coerceAtLeast(0))
            val buffer = ByteArray(8 * 1024)
            var remaining = target
            while (remaining > 0) {
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
                remaining -= count
            }
            var bytes = output.toByteArray()
            if (start + bytes.size < total) {
                val newline = bytes.indexOfLast { it == '\n'.code.toByte() }
                if (newline >= bytes.size / 2) bytes = bytes.copyOf(newline + 1)
            }
            val end = start + bytes.size
            TextFilePage(
                text = bytes.toString(Charsets.UTF_8),
                start = start,
                endExclusive = end,
                totalBytes = total,
                nextOffset = end.takeIf { it < total },
            )
        }
    }.getOrElse { TextFilePage(error = it.message ?: it::class.java.simpleName) }
}

private data class TextFilePage(
    val text: String = "",
    val start: Long = 0,
    val endExclusive: Long = 0,
    val totalBytes: Long = 0,
    val nextOffset: Long? = null,
    val error: String? = null,
)
