package app.xylune.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
internal fun StreamingTokenPulse(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = streamingFadeIn(),
        exit = streamingFadeOut(),
    ) {
        val transition = rememberInfiniteTransition(label = "streaming-token-pulse")
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
            repeat(3) { index ->
                val opacity by transition.animateFloat(
                    initialValue = .18f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 620, delayMillis = index * 150, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "streaming-dot-$index",
                )
                Box(
                    Modifier
                        .size(if (label == null) 5.dp else 6.dp)
                        .alpha(opacity)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}
