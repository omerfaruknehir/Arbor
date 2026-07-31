package app.arbor.chat.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Switches between manifest-declared launcher aliases without restarting Arbor.
 *
 * Android does not allow an arbitrary launcher drawable to be replaced at runtime,
 * so every supported palette has a polished adaptive-icon alias. The desired alias
 * is enabled before the previous one is disabled, preventing a no-icon window while
 * launchers refresh their cache.
 */
internal object LauncherIconManager {
    private const val TAG = "ArborLauncherIcon"

    internal const val ARBOR_ALIAS = "app.arbor.chat.LauncherArbor"
    internal const val SYSTEM_ALIAS = "app.arbor.chat.LauncherSystem"
    internal const val GRAPHITE_ALIAS = "app.arbor.chat.LauncherGraphite"
    internal const val OCEAN_ALIAS = "app.arbor.chat.LauncherOcean"
    internal const val VIOLET_ALIAS = "app.arbor.chat.LauncherViolet"
    internal const val SUNSET_ALIAS = "app.arbor.chat.LauncherSunset"

    internal val allAliases = listOf(
        ARBOR_ALIAS,
        SYSTEM_ALIAS,
        GRAPHITE_ALIAS,
        OCEAN_ALIAS,
        VIOLET_ALIAS,
        SUNSET_ALIAS,
    )

    internal fun aliasClassName(matchPalette: Boolean, palette: ColorPalette): String {
        if (!matchPalette) return ARBOR_ALIAS
        return when (palette) {
            ColorPalette.ARBOR -> ARBOR_ALIAS
            ColorPalette.SYSTEM -> SYSTEM_ALIAS
            ColorPalette.GRAPHITE -> GRAPHITE_ALIAS
            ColorPalette.OCEAN -> OCEAN_ALIAS
            ColorPalette.VIOLET -> VIOLET_ALIAS
            ColorPalette.SUNSET -> SUNSET_ALIAS
        }
    }

    fun apply(context: Context, matchPalette: Boolean, palette: ColorPalette): Boolean {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        val desiredClassName = aliasClassName(matchPalette, palette)
        return runCatching {
            // Enable first so launchers never observe Arbor with no launcher component.
            setEnabled(
                packageManager = packageManager,
                component = ComponentName(appContext.packageName, desiredClassName),
                enabled = true,
                manifestDefault = desiredClassName == ARBOR_ALIAS,
            )
            allAliases.asSequence()
                .filterNot { it == desiredClassName }
                .forEach { className ->
                    setEnabled(
                        packageManager = packageManager,
                        component = ComponentName(appContext.packageName, className),
                        enabled = false,
                        manifestDefault = className == ARBOR_ALIAS,
                    )
                }
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not update launcher icon alias", error)
        }.getOrDefault(false)
    }

    private fun setEnabled(
        packageManager: PackageManager,
        component: ComponentName,
        enabled: Boolean,
        manifestDefault: Boolean,
    ) {
        val current = when (packageManager.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
            else -> false
        }
        if (current == enabled) return
        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
