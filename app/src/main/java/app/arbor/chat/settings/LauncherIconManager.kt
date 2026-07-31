package app.arbor.chat.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit

/**
 * Queues launcher aliases while Arbor is visible and applies them only after the
 * app moves to the background. Samsung/One UI may restart an app process when a
 * launcher component changes even with DONT_KILL_APP, so no component mutation
 * is performed from a visible Activity.
 */
internal object LauncherIconManager {
    private const val TAG = "ArborLauncherIcon"
    private const val STATE_PREFS = "arbor_launcher_icon_state"
    private const val KEY_PENDING_ALIAS = "pending_alias"

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

    fun request(context: Context, matchPalette: Boolean, palette: ColorPalette) {
        val desiredClassName = aliasClassName(matchPalette, palette)
        context.applicationContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .edit(commit = true) { putString(KEY_PENDING_ALIAS, desiredClassName) }
    }

    /** Called from ProcessLifecycleOwner.onStop, never while Arbor is visible. */
    fun applyPending(context: Context): Boolean {
        val appContext = context.applicationContext
        val state = appContext.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        val desiredClassName = state.getString(KEY_PENDING_ALIAS, null) ?: return true
        if (desiredClassName !in allAliases) {
            state.edit(commit = true) { remove(KEY_PENDING_ALIAS) }
            return false
        }

        // Clear first. Some OEM launchers terminate the background process from
        // inside the PackageManager call; leaving the marker would cause a loop.
        state.edit(commit = true) { remove(KEY_PENDING_ALIAS) }
        return runCatching {
            val packageManager = appContext.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applyAtomically(packageManager, appContext.packageName, desiredClassName)
            } else {
                applyEnableFirst(packageManager, appContext.packageName, desiredClassName)
            }
            true
        }.onFailure { error ->
            state.edit(commit = true) { putString(KEY_PENDING_ALIAS, desiredClassName) }
            Log.w(TAG, "Could not update launcher icon alias", error)
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyAtomically(
        packageManager: PackageManager,
        packageName: String,
        desiredClassName: String,
    ) {
        val changes = allAliases.mapNotNull { className ->
            val component = ComponentName(packageName, className)
            val enabled = className == desiredClassName
            val manifestDefault = className == ARBOR_ALIAS
            if (isEnabled(packageManager, component, manifestDefault) == enabled) return@mapNotNull null
            PackageManager.ComponentEnabledSetting(
                component,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        if (changes.isNotEmpty()) packageManager.setComponentEnabledSettings(changes)
    }

    private fun applyEnableFirst(
        packageManager: PackageManager,
        packageName: String,
        desiredClassName: String,
    ) {
        setEnabled(
            packageManager = packageManager,
            component = ComponentName(packageName, desiredClassName),
            enabled = true,
            manifestDefault = desiredClassName == ARBOR_ALIAS,
        )
        allAliases.asSequence()
            .filterNot { it == desiredClassName }
            .forEach { className ->
                setEnabled(
                    packageManager = packageManager,
                    component = ComponentName(packageName, className),
                    enabled = false,
                    manifestDefault = className == ARBOR_ALIAS,
                )
            }
    }

    private fun isEnabled(
        packageManager: PackageManager,
        component: ComponentName,
        manifestDefault: Boolean,
    ): Boolean = when (packageManager.getComponentEnabledSetting(component)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> manifestDefault
        else -> false
    }

    private fun setEnabled(
        packageManager: PackageManager,
        component: ComponentName,
        enabled: Boolean,
        manifestDefault: Boolean,
    ) {
        if (isEnabled(packageManager, component, manifestDefault) == enabled) return
        packageManager.setComponentEnabledSetting(
            component,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
