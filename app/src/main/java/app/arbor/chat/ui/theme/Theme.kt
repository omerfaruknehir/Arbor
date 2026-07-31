package app.arbor.chat.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.arbor.chat.settings.ColorPalette
import app.arbor.chat.settings.ThemeMode

private val ArborLight = lightColorScheme(
    primary = Color(0xFF286448), onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F1CC), onPrimaryContainer = Color(0xFF002112),
    secondary = Color(0xFF4E6356), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1E8D7), onSecondaryContainer = Color(0xFF0B1F14),
    tertiary = Color(0xFF3D6472), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1EAFB), onTertiaryContainer = Color(0xFF001F29),
    background = Color(0xFFF7FAF7), onBackground = Color(0xFF181D1A),
    surface = Color(0xFFF7FAF7), onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDDE5DE), onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717972), outlineVariant = Color(0xFFC1C9C2),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF1F4F1),
    surfaceContainer = Color(0xFFEBEEEB), surfaceContainerHigh = Color(0xFFE5E9E5),
    surfaceContainerHighest = Color(0xFFDFE3DF),
    inverseSurface = Color(0xFF2E3036), inverseOnSurface = Color(0xFFF0F0F7), inversePrimary = Color(0xFF99D5B1),
    error = Color(0xFFBA1A1A), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

private val ArborDark = darkColorScheme(
    primary = Color(0xFF99D5B1), onPrimary = Color(0xFF003921),
    primaryContainer = Color(0xFF0D5033), onPrimaryContainer = Color(0xFFB5F1CC),
    secondary = Color(0xFFB5CCBC), onSecondary = Color(0xFF213529),
    secondaryContainer = Color(0xFF374B3E), onSecondaryContainer = Color(0xFFD1E8D7),
    tertiary = Color(0xFFA5CDDD), onTertiary = Color(0xFF073541),
    tertiaryContainer = Color(0xFF254C59), onTertiaryContainer = Color(0xFFC1EAFB),
    background = Color(0xFF101411), onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF101411), onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF414942), onSurfaceVariant = Color(0xFFC1C9C2),
    outline = Color(0xFF8B938C), outlineVariant = Color(0xFF414942),
    surfaceContainerLowest = Color(0xFF0B0F0C), surfaceContainerLow = Color(0xFF181C19),
    surfaceContainer = Color(0xFF1C201D), surfaceContainerHigh = Color(0xFF262A27),
    surfaceContainerHighest = Color(0xFF313532),
    inverseSurface = Color(0xFFE2E2E9), inverseOnSurface = Color(0xFF2E3036), inversePrimary = Color(0xFF286448),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val GraphiteLight = ArborLight.copy(
    primary = Color(0xFF425F86), primaryContainer = Color(0xFFD5E3FF), onPrimaryContainer = Color(0xFF001C3A),
    secondary = Color(0xFF595E68), secondaryContainer = Color(0xFFDEE2EC), onSecondaryContainer = Color(0xFF171B22),
    tertiary = Color(0xFF745B46), tertiaryContainer = Color(0xFFFFDCC4), onTertiaryContainer = Color(0xFF2B1608),
)
private val GraphiteDark = ArborDark.copy(
    primary = Color(0xFFA9C7F8), onPrimary = Color(0xFF0D3058), primaryContainer = Color(0xFF29486F), onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFC3C6D0), secondaryContainer = Color(0xFF41464F), onSecondaryContainer = Color(0xFFDEE2EC),
    tertiary = Color(0xFFE5BFA6), tertiaryContainer = Color(0xFF5A402D), onTertiaryContainer = Color(0xFFFFDCC4),
)

private val OceanLight = ArborLight.copy(
    primary = Color(0xFF00677A), onPrimary = Color.White, primaryContainer = Color(0xFFAAEDFF), onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF49636A), secondaryContainer = Color(0xFFCDE7EE), onSecondaryContainer = Color(0xFF041F25),
    tertiary = Color(0xFF565E7D), tertiaryContainer = Color(0xFFDDE1FF), onTertiaryContainer = Color(0xFF121A37),
)
private val OceanDark = ArborDark.copy(
    primary = Color(0xFF54D6F2), onPrimary = Color(0xFF00363F), primaryContainer = Color(0xFF004E5D), onPrimaryContainer = Color(0xFFAAEDFF),
    secondary = Color(0xFFB1CBD2), secondaryContainer = Color(0xFF324B52), onSecondaryContainer = Color(0xFFCDE7EE),
    tertiary = Color(0xFFBEC6EA), tertiaryContainer = Color(0xFF3F4664), onTertiaryContainer = Color(0xFFDDE1FF),
)

private val VioletLight = ArborLight.copy(
    primary = Color(0xFF67508F), onPrimary = Color.White, primaryContainer = Color(0xFFEADDFF), onPrimaryContainer = Color(0xFF22005D),
    secondary = Color(0xFF625B70), secondaryContainer = Color(0xFFE8DEF8), onSecondaryContainer = Color(0xFF1E192B),
    tertiary = Color(0xFF7E5260), tertiaryContainer = Color(0xFFFFD9E3), onTertiaryContainer = Color(0xFF31101D),
)
private val VioletDark = ArborDark.copy(
    primary = Color(0xFFD1BCFF), onPrimary = Color(0xFF38205F), primaryContainer = Color(0xFF4F3776), onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCBC2DB), secondaryContainer = Color(0xFF4A4458), onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8), tertiaryContainer = Color(0xFF633B48), onTertiaryContainer = Color(0xFFFFD9E3),
)

private val SunsetLight = ArborLight.copy(
    primary = Color(0xFF9B4425), onPrimary = Color.White, primaryContainer = Color(0xFFFFDBCF), onPrimaryContainer = Color(0xFF390C00),
    secondary = Color(0xFF77574D), secondaryContainer = Color(0xFFFFDBCF), onSecondaryContainer = Color(0xFF2C160F),
    tertiary = Color(0xFF6B5D2F), tertiaryContainer = Color(0xFFF4E1A7), onTertiaryContainer = Color(0xFF211B00),
)
private val SunsetDark = ArborDark.copy(
    primary = Color(0xFFFFB59C), onPrimary = Color(0xFF5C1A07), primaryContainer = Color(0xFF7C2D12), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFE7BDB0), secondaryContainer = Color(0xFF5D4037), onSecondaryContainer = Color(0xFFFFDBCF),
    tertiary = Color(0xFFD7C58D), tertiaryContainer = Color(0xFF514619), onTertiaryContainer = Color(0xFFF4E1A7),
)


val LocalArborColorPalette = staticCompositionLocalOf { ColorPalette.ARBOR }

data class PalettePreviewColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

private fun paletteColorScheme(
    palette: ColorPalette,
    dark: Boolean,
    context: Context,
): ColorScheme = when (palette) {
    ColorPalette.ARBOR -> if (dark) ArborDark else ArborLight
    ColorPalette.GRAPHITE -> if (dark) GraphiteDark else GraphiteLight
    ColorPalette.OCEAN -> if (dark) OceanDark else OceanLight
    ColorPalette.VIOLET -> if (dark) VioletDark else VioletLight
    ColorPalette.SUNSET -> if (dark) SunsetDark else SunsetLight
    ColorPalette.SYSTEM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) ArborDark else ArborLight
}

@Composable
fun palettePreviewColors(
    palette: ColorPalette,
    themeMode: ThemeMode,
): PalettePreviewColors {
    val context = LocalContext.current
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = paletteColorScheme(palette, dark, context)
    return PalettePreviewColors(
        primary = scheme.primary,
        secondary = scheme.secondary,
        tertiary = scheme.tertiary,
    )
}

private val ArborShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp),
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
    var colors = paletteColorScheme(palette, dark, context)
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
    activity?.window?.let {
        WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !dark
    }
    CompositionLocalProvider(LocalArborColorPalette provides palette) {
        MaterialTheme(colorScheme = colors, shapes = ArborShapes, content = content)
    }
}
