package app.xylune.chat.ui

import android.content.Intent
import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal enum class LinkReferenceKind { LINK, SOURCE, FILE }

internal data class LinkReferencePreview(
    val kind: LinkReferenceKind,
    val label: String,
    val target: String,
    val description: String = "",
    val anchorBoundsInWindow: IntRect? = null,
)

private data class RemoteLinkMetadata(
    val title: String = "",
    val description: String = "",
    val siteName: String = "",
)

@Composable
internal fun AnchoredLinkPreview(
    reference: LinkReferencePreview,
    onDismiss: () -> Unit,
) {
    val anchor = reference.anchorBoundsInWindow ?: IntRect(0, 0, 1, 1)
    val density = LocalDensity.current
    val initialScale = remember(anchor, density) {
        val finalWidthPx = with(density) { 330.dp.toPx() }
        (anchor.width.toFloat() / finalWidthPx).coerceIn(0.58f, 0.86f)
    }
    var dismissRequested by remember(reference.target) { mutableStateOf(false) }
    val visibility = remember(reference.target) {
        MutableTransitionState(false).apply { targetState = true }
    }
    val requestDismiss: () -> Unit = { dismissRequested = true }

    LaunchedEffect(dismissRequested) {
        if (dismissRequested) {
            visibility.targetState = false
            delay(170)
            onDismiss()
        }
    }

    Popup(
        popupPositionProvider = SpanPopupPositionProvider(anchor),
        onDismissRequest = requestDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = scaleIn(
                animationSpec = tween(210),
                initialScale = initialScale,
                transformOrigin = TransformOrigin.Center,
            ) + fadeIn(animationSpec = tween(130)),
            exit = scaleOut(
                animationSpec = tween(160),
                targetScale = initialScale,
                transformOrigin = TransformOrigin.Center,
            ) + fadeOut(animationSpec = tween(120)),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.extraLarge,
                shadowElevation = 14.dp,
                tonalElevation = 4.dp,
                modifier = Modifier.width(330.dp).heightIn(max = 420.dp),
            ) {
                LinkPreviewDetails(reference, requestDismiss, Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
internal fun LinkPreviewDetails(
    reference: LinkReferencePreview,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    val context = LocalContext.current
    val openable = reference.kind != LinkReferenceKind.FILE &&
        (reference.target.startsWith("https://") || reference.target.startsWith("http://"))
    val host = remember(reference.target) {
        runCatching { reference.target.toUri().host }.getOrNull().orEmpty().removePrefix("www.")
    }
    var metadata by remember(reference.target) { mutableStateOf<RemoteLinkMetadata?>(null) }
    var loading by remember(reference.target) { mutableStateOf(openable) }

    LaunchedEffect(reference.target, openable) {
        if (!openable) {
            loading = false
            return@LaunchedEffect
        }
        metadata = runCatching { fetchRemoteMetadata(reference.target) }.getOrNull()
        loading = false
    }

    val title = metadata?.title?.takeIf(String::isNotBlank) ?: reference.label.ifBlank {
        if (reference.kind == LinkReferenceKind.FILE) "Referenced file" else host.ifBlank { "External link" }
    }
    val description = metadata?.description?.takeIf(String::isNotBlank)
        ?: reference.description.takeIf(String::isNotBlank)
        ?: when (reference.kind) {
            LinkReferenceKind.FILE -> "A file referenced by this answer."
            LinkReferenceKind.SOURCE -> "A source used to support the surrounding claim."
            LinkReferenceKind.LINK -> "An external page linked from this answer."
        }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = when (reference.kind) {
                        LinkReferenceKind.FILE -> MaterialTheme.colorScheme.secondaryContainer
                        LinkReferenceKind.SOURCE -> MaterialTheme.colorScheme.tertiaryContainer
                        LinkReferenceKind.LINK -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        imageVector = when (reference.kind) {
                            LinkReferenceKind.FILE -> Icons.AutoMirrored.Outlined.InsertDriveFile
                            LinkReferenceKind.SOURCE -> Icons.Outlined.TravelExplore
                            LinkReferenceKind.LINK -> Icons.AutoMirrored.Outlined.OpenInNew
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(9.dp).size(20.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (host.isNotBlank()) Text(host, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        } else if (loading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            }
        }

        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 5, overflow = TextOverflow.Ellipsis)

        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    reference.target,
                    Modifier.padding(start = 7.dp).weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onDismiss) { Text(uiText("Close")) }
            if (openable) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val target = reference.target
                    onDismiss()
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
                }) {
                    Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(17.dp))
                    Text(uiText("Open"), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private class SpanPopupPositionProvider(
    private val spanBounds: IntRect,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 12
        val preferredX = spanBounds.left + (spanBounds.width - popupContentSize.width) / 2
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = preferredX.coerceIn(margin, maxX)
        val below = spanBounds.bottom + 8
        val above = spanBounds.top - popupContentSize.height - 8
        val y = when {
            below + popupContentSize.height <= windowSize.height - margin -> below
            above >= margin -> above
            else -> (windowSize.height - popupContentSize.height) / 2
        }
        return IntOffset(x, y.coerceAtLeast(margin))
    }
}

private suspend fun fetchRemoteMetadata(url: String): RemoteLinkMetadata = withContext(Dispatchers.IO) {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 3_000
        readTimeout = 3_500
        requestMethod = "GET"
        setRequestProperty("User-Agent", "Xylune-LinkPreview/1.0")
        setRequestProperty("Accept", "text/html,application/xhtml+xml")
        setRequestProperty("Range", "bytes=0-262143")
    }
    try {
        val contentType = connection.contentType.orEmpty().lowercase()
        if (!contentType.contains("html")) return@withContext RemoteLinkMetadata()
        val reader = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8))
        val out = StringBuilder()
        val buffer = CharArray(4_096)
        while (out.length < 262_144) {
            val read = reader.read(buffer, 0, minOf(buffer.size, 262_144 - out.length))
            if (read <= 0) break
            out.append(buffer, 0, read)
        }
        val html = out.toString()
        RemoteLinkMetadata(
            title = htmlMeta(html, "og:title").ifBlank { htmlTitle(html) },
            description = htmlMeta(html, "og:description").ifBlank { htmlMeta(html, "description") },
            siteName = htmlMeta(html, "og:site_name"),
        )
    } finally {
        connection.disconnect()
    }
}

private fun htmlTitle(html: String): String = Regex("""(?is)<title[^>]*>(.*?)</title>""")
    .find(html)?.groupValues?.getOrNull(1).orEmpty().decodeHtml().trim().take(180)

private fun htmlMeta(html: String, key: String): String {
    val escaped = Regex.escape(key)
    val patterns = listOf(
        Regex("""(?is)<meta[^>]+(?:property|name)\s*=\s*["']$escaped["'][^>]+content\s*=\s*["'](.*?)["'][^>]*>"""),
        Regex("""(?is)<meta[^>]+content\s*=\s*["'](.*?)["'][^>]+(?:property|name)\s*=\s*["']$escaped["'][^>]*>"""),
    )
    return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
        .orEmpty().decodeHtml().trim().take(420)
}

@Suppress("DEPRECATION")
private fun String.decodeHtml(): String = Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString()
