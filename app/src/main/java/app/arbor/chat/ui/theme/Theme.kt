package app.arbor.chat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.settings.ThemeMode

private val ArborLight = lightColorScheme(
    primary = Color(0xFF286448),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5F1CC),
    onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E8D7),
    onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3D6472),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC1EAFB),
    onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FAF7),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDDE5DE),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972),
    outlineVariant = Color(0xFFC1C9C2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F1),
    surfaceContainer = Color(0xFFEBEEEB),
    surfaceContainerHigh = Color(0xFFE5E9E5),
    surfaceContainerHighest = Color(0xFFDFE3DF),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    inversePrimary = Color(0xFF99D5B1),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val ArborDark = darkColorScheme(
    primary = Color(0xFF99D5B1),
    onPrimary = Color(0xFF003921),
    primaryContainer = Color(0xFF0D5033),
    onPrimaryContainer = Color(0xFFB5F1CC),
    secondary = Color(0xFFB5CCBC),
    onSecondary = Color(0xFF213529),
    secondaryContainer = Color(0xFF374B3E),
    onSecondaryContainer = Color(0xFFD1E8D7),
    tertiary = Color(0xFFA5CDDD),
    onTertiary = Color(0xFF073541),
    tertiaryContainer = Color(0xFF254C59),
    onTertiaryContainer = Color(0xFFC1EAFB),
    background = Color(0xFF101411),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C2),
    outline = Color(0xFF8B938C),
    outlineVariant = Color(0xFF414942),
    surfaceContainerLowest = Color(0xFF0B0F0C),
    surfaceContainerLow = Color(0xFF181C19),
    surfaceContainer = Color(0xFF1C201D),
    surfaceContainerHigh = Color(0xFF262A27),
    surfaceContainerHighest = Color(0xFF313532),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    inversePrimary = Color(0xFF286448),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val GraphiteLight = ArborLight.copy(
    primary = Color(0xFF425F86), primaryContainer = Color(0xFFD5E3FF), onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF595E68), secondaryContainer = Color(0xFFDEE2EC), onSecondaryContainer = Color(0xFF171B22),
)

private val GraphiteDark = ArborDark.copy(
    primary = Color(0xFFA9C7F8), onPrimary = Color(0xFF0D3058), primaryContainer = Color(0xFF29486F), onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFC3C6D0), secondaryContainer = Color(0xFF41464F), onSecondaryContainer = Color(0xFFDEE2EC),
)

private val ArborShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun ArborTheme(
    amoled: Boolean = false,
    palette: ColorPalette = ColorPalette.ARBOR,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var colors = when (palette) {
        ColorPalette.ARBOR -> if (dark) ArborDark else ArborLight
        ColorPalette.GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
        ColorPalette.SYSTEM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) ArborDark else ArborLight
    }
    if (amoled && dark) colors = colors.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF08090C),
        surfaceContainer = Color(0xFF0D0F13),
        surfaceContainerHigh = Color(0xFF15171C),
        surfaceContainerHighest = Color(0xFF1D2025),
    )
    val activity = context as? Activity
    activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark }
    MaterialTheme(colorScheme = colors, shapes = ArborShapes, content = content)
}
