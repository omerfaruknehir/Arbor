package app.xylune.chat.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.net.toUri
import kotlin.math.max

/**
 * Visual container transform for a source pill.
 *
 * The real pill in the message row never changes size. A full-window overlay is
 * laid exactly over its captured bounds and then transformed into the preview,
 * giving the visual effect of the pill itself expanding without any reflow.
 */
@Composable
internal fun MorphingSourcePreview(
    index: Int,
    reference: LinkReferencePreview,
    onDismiss: () -> Unit,
) {
    val anchor = reference.anchorBoundsInWindow ?: return
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val progress = remember(reference.target) { Animatable(0f) }
    var dismissing by remember(reference.target) { mutableStateOf(false) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    val minimumBackEdgePx = with(density) { 32.dp.roundToPx() }
    val leftBackEdgePx = max(
        WindowInsets.systemGestures.getLeft(density, layoutDirection),
        minimumBackEdgePx,
    )
    val rightBackEdgePx = max(
        WindowInsets.systemGestures.getRight(density, layoutDirection),
        minimumBackEdgePx,
    )
    val requestDismiss: () -> Unit = {
        if (!dismissing) dismissing = true
    }

    LaunchedEffect(rootSize, cardSize, dismissing) {
        if (!dismissing && rootSize.width > 0 && rootSize.height > 0 && cardSize.width > 0 && cardSize.height > 0) {
            progress.animateTo(1f, tween(durationMillis = 240))
        }
    }
    LaunchedEffect(dismissing) {
        if (dismissing) {
            progress.animateTo(0f, tween(durationMillis = 180))
            onDismiss()
        }
    }

    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = requestDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false,
        ),
    ) {
        XylunePopupBackHandler(onDismissRequest = requestDismiss)

        val targetPosition = popupTargetPosition(anchor, rootSize, cardSize)
        val cardBounds = IntRect(
            left = targetPosition.x,
            top = targetPosition.y,
            right = targetPosition.x + cardSize.width,
            bottom = targetPosition.y + cardSize.height,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootSize = it }
                .pointerInput(
                    cardBounds,
                    leftBackEdgePx,
                    rightBackEdgePx,
                    requestDismiss,
                ) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val startedInBackEdge = down.position.x <= leftBackEdgePx ||
                            down.position.x >= size.width - rightBackEdgePx
                        val boundsReady = cardBounds.width > 0 && cardBounds.height > 0
                        val startedInsideCard = boundsReady &&
                            down.position.x >= cardBounds.left &&
                            down.position.x <= cardBounds.right &&
                            down.position.y >= cardBounds.top &&
                            down.position.y <= cardBounds.bottom
                        val startX = down.position.x
                        val startY = down.position.y
                        var maxTravelSquared = 0f

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                            val tracked = event.changes.firstOrNull { it.id == down.id }
                            if (tracked != null) {
                                val dx = tracked.position.x - startX
                                val dy = tracked.position.y - startY
                                maxTravelSquared = max(maxTravelSquared, dx * dx + dy * dy)
                            }
                            if (event.changes.none { it.pressed }) break
                        }

                        val slop = viewConfiguration.touchSlop
                        val wasTap = maxTravelSquared <= slop * slop
                        if (boundsReady && !startedInBackEdge && !startedInsideCard && wasTap) {
                            requestDismiss()
                        }
                    }
                },
        ) {
            val p = progress.value.coerceIn(0f, 1f)
            val startScaleX = if (cardSize.width > 0) {
                (anchor.width.toFloat() / cardSize.width.toFloat()).coerceIn(0.05f, 1f)
            } else {
                1f
            }
            val startScaleY = if (cardSize.height > 0) {
                (anchor.height.toFloat() / cardSize.height.toFloat()).coerceIn(0.05f, 1f)
            } else {
                1f
            }
            val scaleX = lerp(startScaleX, 1f, p)
            val scaleY = lerp(startScaleY, 1f, p)
            val anchorCenterX = anchor.left + anchor.width / 2f
            val anchorCenterY = anchor.top + anchor.height / 2f
            val targetCenterX = targetPosition.x + cardSize.width / 2f
            val targetCenterY = targetPosition.y + cardSize.height / 2f
            val translationX = (anchorCenterX - targetCenterX) * (1f - p)
            val translationY = (anchorCenterY - targetCenterY) * (1f - p)
            val detailsAlpha = ((p - 0.22f) / 0.78f).coerceIn(0f, 1f)
            val pillAlpha = (1f - p * 2.5f).coerceIn(0f, 1f)
            val host = remember(reference.target) {
                runCatching { reference.target.toUri().host }
                    .getOrNull()
                    .orEmpty()
                    .removePrefix("www.")
            }
            val label = reference.label.ifBlank { host.ifBlank { "Source $index" } }

            Surface(
                modifier = Modifier
                    .offset { targetPosition }
                    .width(330.dp)
                    .heightIn(max = 420.dp)
                    .onSizeChanged { cardSize = it }
                    .graphicsLayer {
                        transformOrigin = TransformOrigin.Center
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        this.translationX = translationX
                        this.translationY = translationY
                    },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(lerp(16f, 28f, p).dp),
                tonalElevation = lerp(1f, 4f, p).dp,
                shadowElevation = lerp(0f, 14f, p).dp,
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .graphicsLayer { alpha = pillAlpha }
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
                            Box(contentAlignment = Alignment.Center) {
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

                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = detailsAlpha }
                            .padding(16.dp),
                    ) {
                        LinkPreviewDetails(
                            reference = reference,
                            onDismiss = requestDismiss,
                        )
                    }
                }
            }
        }
    }
}

private fun popupTargetPosition(
    anchor: IntRect,
    windowSize: IntSize,
    popupSize: IntSize,
): IntOffset {
    if (windowSize.width <= 0 || windowSize.height <= 0 || popupSize.width <= 0 || popupSize.height <= 0) {
        return IntOffset(anchor.left, anchor.top)
    }
    val margin = 12
    val preferredX = anchor.left + (anchor.width - popupSize.width) / 2
    val maxX = (windowSize.width - popupSize.width - margin).coerceAtLeast(margin)
    val x = preferredX.coerceIn(margin, maxX)
    val below = anchor.bottom + 8
    val above = anchor.top - popupSize.height - 8
    val y = when {
        below + popupSize.height <= windowSize.height - margin -> below
        above >= margin -> above
        else -> (windowSize.height - popupSize.height) / 2
    }
    return IntOffset(x, y.coerceAtLeast(margin))
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)
