package app.arbor.chat.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.arbor.chat.R
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.ui.theme.PalettePreviewColors
import kotlin.math.min

@Composable
internal fun PaletteSwatch(colors: PalettePreviewColors, modifier: Modifier = Modifier) {
    Row(modifier) {
        PaletteDot(colors.primary)
        PaletteDot(colors.secondary, Modifier.offset(x = (-7).dp))
        PaletteDot(colors.tertiary, Modifier.offset(x = (-14).dp))
    }
}

@Composable
private fun PaletteDot(color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color,
        shape = CircleShape,
        shadowElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        modifier = modifier.size(24.dp),
    ) {}
}

/** Arbor's in-app mark, rendered from the active Material color scheme. */
@Composable
internal fun ArborMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val accessibility = if (contentDescription == null) Modifier else Modifier.semantics {
        this.contentDescription = contentDescription
    }
    Canvas(modifier.then(accessibility)) {
        val logoSize = min(size.width, size.height)
        if (logoSize <= 0f) return@Canvas
        val unit = logoSize / 108f
        val left = (size.width - logoSize) / 2f
        val top = (size.height - logoSize) / 2f
        fun point(x: Float, y: Float) = Offset(left + x * unit, top + y * unit)

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(colors.primaryContainer, colors.primary),
                start = point(15f, 8f),
                end = point(96f, 101f),
            ),
            topLeft = Offset(left, top),
            size = Size(logoSize, logoSize),
            cornerRadius = CornerRadius(24f * unit, 24f * unit),
        )

        val leftStem = Path().apply {
            moveTo(point(28f, 82f).x, point(28f, 82f).y)
            cubicTo(
                point(35f, 64f).x, point(35f, 64f).y,
                point(43f, 45f).x, point(43f, 45f).y,
                point(53f, 28f).x, point(53f, 28f).y,
            )
        }
        drawPath(
            path = leftStem,
            brush = Brush.linearGradient(
                colors = listOf(colors.onPrimaryContainer, colors.onPrimary),
                start = point(27f, 84f),
                end = point(55f, 25f),
            ),
            style = Stroke(width = 11f * unit, cap = StrokeCap.Round),
        )

        val rightStem = Path().apply {
            moveTo(point(55f, 28f).x, point(55f, 28f).y)
            cubicTo(
                point(65f, 45f).x, point(65f, 45f).y,
                point(73f, 64f).x, point(73f, 64f).y,
                point(80f, 82f).x, point(80f, 82f).y,
            )
        }
        drawPath(
            path = rightStem,
            color = colors.onPrimary,
            style = Stroke(width = 11f * unit, cap = StrokeCap.Round),
        )

        val crossbar = Path().apply {
            moveTo(point(40f, 64f).x, point(40f, 64f).y)
            cubicTo(
                point(49f, 60f).x, point(49f, 60f).y,
                point(59f, 60f).x, point(59f, 60f).y,
                point(68f, 64f).x, point(68f, 64f).y,
            )
        }
        drawPath(
            path = crossbar,
            color = colors.tertiary,
            style = Stroke(width = 8f * unit, cap = StrokeCap.Round),
        )

        val leaf = Path().apply {
            moveTo(point(54f, 29f).x, point(54f, 29f).y)
            cubicTo(
                point(60f, 21f).x, point(60f, 21f).y,
                point(69f, 19f).x, point(69f, 19f).y,
                point(76f, 24f).x, point(76f, 24f).y,
            )
            cubicTo(
                point(73f, 33f).x, point(73f, 33f).y,
                point(65f, 37f).x, point(65f, 37f).y,
                point(55f, 33f).x, point(55f, 33f).y,
            )
            close()
        }
        drawPath(leaf, color = colors.tertiary)
    }
}

@Composable
internal fun LauncherIconPreview(
    palette: ColorPalette,
    size: Dp = 54.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier = modifier.size(size),
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Image(
                painter = painterResource(palette.launcherPreviewDrawable),
                contentDescription = null,
                modifier = Modifier.matchParentSize().clip(MaterialTheme.shapes.large),
            )
        }
    }
}

@get:DrawableRes
private val ColorPalette.launcherPreviewDrawable: Int
    get() = when (this) {
        ColorPalette.ARBOR -> R.drawable.ic_arbor_mark
        ColorPalette.SYSTEM -> R.drawable.ic_arbor_mark_system
        ColorPalette.GRAPHITE -> R.drawable.ic_arbor_mark_graphite
        ColorPalette.OCEAN -> R.drawable.ic_arbor_mark_ocean
        ColorPalette.VIOLET -> R.drawable.ic_arbor_mark_violet
        ColorPalette.SUNSET -> R.drawable.ic_arbor_mark_sunset
    }
