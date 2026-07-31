package app.arbor.chat.ui

import androidx.annotation.DrawableRes
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.arbor.chat.R
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.ui.theme.PalettePreviewColors

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

/** Palette used by every in-app Arbor icon. It mirrors the launcher alias exactly. */
internal val LocalArborIconPalette = staticCompositionLocalOf { ColorPalette.ARBOR }

/** Exact drawable-backed copy of the currently selected launcher icon artwork. */
@Composable
internal fun ArborMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val palette = LocalArborIconPalette.current
    Image(
        painter = painterResource(palette.launcherPreviewDrawable),
        contentDescription = contentDescription,
        modifier = modifier,
    )
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
internal val ColorPalette.launcherPreviewDrawable: Int
    get() = when (this) {
        ColorPalette.ARBOR -> R.drawable.ic_arbor_mark
        ColorPalette.SYSTEM -> R.drawable.ic_arbor_mark_system
        ColorPalette.GRAPHITE -> R.drawable.ic_arbor_mark_graphite
        ColorPalette.OCEAN -> R.drawable.ic_arbor_mark_ocean
        ColorPalette.VIOLET -> R.drawable.ic_arbor_mark_violet
        ColorPalette.SUNSET -> R.drawable.ic_arbor_mark_sunset
    }
