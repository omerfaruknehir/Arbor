package app.arbor.chat.settings

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import app.arbor.chat.R

/**
 * Applies one manifest-declared launcher alias as part of an intentional restart.
 *
 * Samsung/One UI can tear down the current task whenever activity-alias state is
 * changed, even with DONT_KILL_APP. Arbor therefore saves its complete UI state,
 * dispatches the component mutation to the isolated launcher process, closes the
 * old task deliberately, and lets that process relaunch Arbor from the snapshot.
 */
internal object LauncherIconManager {
    private const val TAG = "ArborLauncherIcon"
    internal const val EXTRA_DESIRED_ALIAS = "app.arbor.chat.extra.DESIRED_LAUNCHER_ALIAS"
    internal const val EXTRA_RELAUNCH_INTENT = "app.arbor.chat.extra.RELAUNCH_INTENT"

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

    @DrawableRes
    internal fun iconResource(matchPalette: Boolean, palette: ColorPalette): Int =
        when (aliasClassName(matchPalette, palette)) {
            SYSTEM_ALIAS -> R.mipmap.ic_launcher_system
            GRAPHITE_ALIAS -> R.mipmap.ic_launcher_graphite
            OCEAN_ALIAS -> R.mipmap.ic_launcher_ocean
            VIOLET_ALIAS -> R.mipmap.ic_launcher_violet
            SUNSET_ALIAS -> R.mipmap.ic_launcher_sunset
            else -> R.mipmap.ic_launcher
        }

    fun needsChange(context: Context, matchPalette: Boolean, palette: ColorPalette): Boolean =
        enabledAlias(context.applicationContext) != aliasClassName(matchPalette, palette)

    fun requestStatefulRestart(
        context: Context,
        desiredClassName: String,
        relaunchIntent: PendingIntent,
    ): Boolean {
        if (desiredClassName !in allAliases) return false
        return runCatching {
            context.applicationContext.sendBroadcast(
                Intent(context, LauncherIconSwitchReceiver::class.java)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_DESIRED_ALIAS, desiredClassName)
                    .putExtra(EXTRA_RELAUNCH_INTENT, relaunchIntent),
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not dispatch stateful launcher icon restart", error)
        }.getOrDefault(false)
    }

    internal fun applyDirect(context: Context, desiredClassName: String): Boolean {
        if (desiredClassName !in allAliases) return false
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applyAtomically(packageManager, appContext.packageName, desiredClassName)
            } else {
                applyEnableFirst(packageManager, appContext.packageName, desiredClassName)
            }
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not update launcher icon alias", error)
        }.getOrDefault(false)
    }

    private fun enabledAlias(context: Context): String? {
        val packageManager = context.packageManager
        return allAliases.firstOrNull { className ->
            isEnabled(
                packageManager = packageManager,
                component = ComponentName(context.packageName, className),
                manifestDefault = className == ARBOR_ALIAS,
            )
        }
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
