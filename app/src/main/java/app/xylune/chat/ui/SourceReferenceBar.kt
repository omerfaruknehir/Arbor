package app.xylune.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

/**
 * A compact bottom-of-response source strip. Pills expand in place into their
 * preview instead of spawning a detached popup, preserving the spatial link
 * between the source the user tapped and the details that appear.
 */
@Composable
internal fun SourceReferenceBar(
    sources: List<XyluneSourceReference>,
    modifier: Modifier = Modifier,
) {
    if (sources.isEmpty()) return

    var expandedTarget by remember { mutableStateOf<String?>(null) }
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
                verticalAlignment = Alignment.Top,
            ) {
                sources.forEachIndexed { index, source ->
                    val expanded = expandedTarget == source.target
                    SourceReferencePill(
                        index = index + 1,
                        source = source,
                        expanded = expanded,
                        onToggle = {
                            expandedTarget = if (expanded) null else source.target
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceReferencePill(
    index: Int,
    source: XyluneSourceReference,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val host = remember(source.target) {
        runCatching { source.target.toUri().host }
            .getOrNull()
            .orEmpty()
            .removePrefix("www.")
    }
    val label = source.label.ifBlank { host.ifBlank { "Source $index" } }
    val reference = remember(source.target, label) {
        LinkReferencePreview(
            kind = LinkReferenceKind.SOURCE,
            label = label,
            target = source.target,
            description = "A source used to support this response.",
        )
    }

    Surface(
        modifier = Modifier
            .widthIn(max = if (expanded) 340.dp else 230.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = if (expanded) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large,
        tonalElevation = if (expanded) 3.dp else 1.dp,
        shadowElevation = if (expanded) 6.dp else 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                Column(modifier = Modifier.widthIn(min = 56.dp, max = if (expanded) 268.dp else 176.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (expanded) 2 else 1,
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

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(Modifier.padding(horizontal = 10.dp))
                    LinkPreviewDetails(
                        reference = reference,
                        onDismiss = onToggle,
                        modifier = Modifier.padding(12.dp),
                        showHeader = false,
                    )
                }
            }
        }
    }
}
