package app.xylune.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.roundToInt

/**
 * Compact bottom-of-response source strip. The row never reflows when a source
 * is opened: a separate overlay starts at the pill's exact bounds and performs
 * the visual container transform above the message layout.
 */
@Composable
internal fun SourceReferenceBar(
    sources: List<XyluneSourceReference>,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return

    var pendingSource by remember { mutableStateOf<Pair<Int, LinkReferencePreview>?>(null) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        LowSensitivityHorizontalScroll(
            modifier = Modifier.fillMaxWidth(),
            touchSlopMultiplier = 1.2f,
        ) {
            Row(
                modifier = Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                sources.forEachIndexed { sourceIndex, source ->
                    val displayIndex = sourceIndex + 1
                    SourceReferencePill(
                        index = displayIndex,
                        source = source,
                        onClick = { anchor ->
                            pendingSource = displayIndex to LinkReferencePreview(
                                kind = LinkReferenceKind.SOURCE,
                                label = source.label,
                                target = source.target,
                                description = "A source used to support this response.",
                                anchorBoundsInWindow = anchor,
                            )
                        },
                    )
                }
            }
        }
    }

    pendingSource?.let { (index, reference) ->
        MorphingSourcePreview(
            index = index,
            reference = reference,
            onDismiss = { pendingSource = null },
        )
    }
}

@Composable
private fun SourceReferencePill(
    index: Int,
    source: XyluneSourceReference,
    onClick: (IntRect) -> Unit,
) {
    var anchorBounds by remember(source.target) { mutableStateOf(IntRect.Zero) }
    val host = remember(source.target) {
        runCatching { source.target.toUri().host }
            .getOrNull()
            .orEmpty()
            .removePrefix("www.")
    }
    val label = source.label.ifBlank { host.ifBlank { "Source $index" } }

    Surface(
        modifier = Modifier
            .widthIn(max = 230.dp)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                anchorBounds = IntRect(
                    left = bounds.left.roundToInt(),
                    top = bounds.top.roundToInt(),
                    right = bounds.right.roundToInt(),
                    bottom = bounds.bottom.roundToInt(),
                )
            }
            .clickable { onClick(anchorBounds) },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(modifier = Modifier.widthIn(min = 56.dp, max = 176.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (host.isNotBlank() && !label.contains(host, ignoreCase = true)) {
                    Text(
                        text = host,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
