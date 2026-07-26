package app.arbor.chat.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Debug-only visual stress scene for spotting stale captures, hard support edges,
 * directional patterns, banding, blockiness, and panel misalignment.
 */
@Composable
internal fun BlurVisualTestScene(modifier: Modifier = Modifier) {
    val blurState = rememberArborBackdropBlurState()
    val transition = rememberInfiniteTransition(label = "blur-visual-motion")
    val translation by transition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(tween(3_400), RepeatMode.Reverse),
        label = "backdrop-x",
    )
    val surface = MaterialTheme.colorScheme.surface

    Box(modifier.fillMaxSize().testTag("blur_visual_test_scene")) {
        Box(
            Modifier
                .fillMaxSize()
                .arborBackdropSource(blurState),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0D47A1),
                            Color(0xFF7B1FA2),
                            Color(0xFFE65100),
                            Color(0xFF00695C),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                val step = 9.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawRect(
                        color = Color.White.copy(alpha = if ((x / step).toInt() % 3 == 0) 0.22f else 0.10f),
                        topLeft = Offset(x, 0f),
                        size = Size(1.dp.toPx(), size.height),
                    )
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawRect(
                        color = Color.Black.copy(alpha = if ((y / step).toInt() % 4 == 0) 0.18f else 0.07f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, 1.dp.toPx()),
                    )
                    y += step * 1.37f
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(14) { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .offset(x = if (row % 2 == 0) 10.dp else (-10).dp)
                            .graphicsLayer { translationX = translation * if (row % 2 == 0) 1f else -0.72f },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(if (row % 3 == 0) 34.dp else 24.dp)
                                .background(
                                    color = when (row % 4) {
                                        0 -> Color(0xFFFFC107)
                                        1 -> Color(0xFF00BCD4)
                                        2 -> Color(0xFFE91E63)
                                        else -> Color(0xFF8BC34A)
                                    },
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            text = "Aa09  Arbor glass detail  ${row.toString().padStart(2, '0')}  ···  |||  ≡≡≡",
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (row % 2 == 0) 12.sp else 10.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(150.dp))
            }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(128.dp)
                .arborBackdropBlur(
                    state = blurState,
                    strength = 0.78f,
                    edgeSoftness = 0.58f,
                    overlayOpacity = 0.82f,
                    tint = surface.copy(alpha = 0.34f),
                    edge = ArborBlurEdge.TOP,
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f))
                .testTag("blur_visual_top_panel"),
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(208.dp)
                .arborBackdropBlur(
                    state = blurState,
                    strength = 0.78f,
                    edgeSoftness = 0.58f,
                    overlayOpacity = 0.82f,
                    tint = surface.copy(alpha = 0.46f),
                    edge = ArborBlurEdge.BOTTOM,
                )
                .border(1.dp, Color.White.copy(alpha = 0.18f))
                .testTag("blur_visual_bottom_panel"),
        )
    }
}
