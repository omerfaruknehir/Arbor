package app.arbor.chat

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import app.arbor.chat.settings.AppPreferences
import app.arbor.chat.settings.LauncherIconManager
import app.arbor.chat.ui.theme.resolvedArborColorScheme
import app.arbor.chat.ui.theme.resolvedDarkMode

/**
 * Palette-aware launcher handoff and splash surface.
 *
 * This process remains independent from MainActivity, so it can reopen Arbor
 * after One UI tears down the old task while refreshing a launcher alias.
 */
open class LauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val preferences = AppPreferences(this)
        val palette = preferences.palette.value
        val themeMode = preferences.themeMode.value
        val amoled = preferences.amoled.value
        val matchIcon = preferences.matchLauncherIconToPalette.value
        val colors = resolvedArborColorScheme(this, palette, themeMode, amoled)
        val dark = resolvedDarkMode(this, themeMode)

        window.setBackgroundDrawable(ColorDrawable(colors.background.toArgb()))
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }

        val density = resources.displayMetrics.density
        val iconSize = (104f * density).toInt()
        val icon = ImageView(this).apply {
            setImageResource(LauncherIconManager.iconResource(matchIcon, palette))
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = getString(R.string.app_name)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(colors.background.toArgb())
            addView(
                icon,
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER),
            )
        }
        setContentView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        root.postDelayed({
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    )
                },
            )
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 110L)
    }
}


class LauncherArborActivity : LauncherActivity()
class LauncherSystemActivity : LauncherActivity()
class LauncherGraphiteActivity : LauncherActivity()
class LauncherOceanActivity : LauncherActivity()
class LauncherVioletActivity : LauncherActivity()
class LauncherSunsetActivity : LauncherActivity()
