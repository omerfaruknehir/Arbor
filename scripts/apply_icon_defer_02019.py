from pathlib import Path
root=Path('.')

p=root/'app/src/main/java/app/arbor/chat/settings/LauncherIconManager.kt'
p.write_text('''package app.arbor.chat.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Queues launcher alias changes and commits them only after Arbor is hidden.
 *
 * Changing an activity-alias is a package-manager operation. Some launchers tear
 * down the visible task even when [PackageManager.DONT_KILL_APP] is supplied.
 * The foreground UI therefore only records the desired alias. [flushPending]
 * dispatches the actual mutation after MainActivity has stopped or Android has
 * reported that Arbor's UI is hidden.
 */
internal object LauncherIconManager {
    private const val TAG = "ArborLauncherIcon"
    private const val STATE_PREFERENCES = "arbor_launcher_icon_state"
    private const val KEY_PENDING_ALIAS = "pending_alias"
    internal const val EXTRA_DESIRED_ALIAS = "app.arbor.chat.extra.DESIRED_LAUNCHER_ALIAS"

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

    /** Records the requested launcher icon without touching component state. */
    fun apply(context: Context, matchPalette: Boolean, palette: ColorPalette): Boolean {
        val appContext = context.applicationContext
        val desiredClassName = aliasClassName(matchPalette, palette)
        return runCatching {
            val pending = statePreferences(appContext)
            if (enabledAlias(appContext) == desiredClassName) {
                pending.edit().remove(KEY_PENDING_ALIAS).commit()
            } else {
                pending.edit().putString(KEY_PENDING_ALIAS, desiredClassName).commit()
            }
        }.onFailure { error ->
            Log.w(TAG, "Could not queue launcher icon alias update", error)
        }.getOrDefault(false)
    }

    /**
     * Sends a queued mutation to the lightweight launcher process.
     * Call only after Arbor's visible activity has stopped.
     */
    fun flushPending(context: Context): Boolean {
        val appContext = context.applicationContext
        val desiredClassName = statePreferences(appContext)
            .getString(KEY_PENDING_ALIAS, null)
            ?.takeIf { it in allAliases }
            ?: return false
        return runCatching {
            appContext.sendBroadcast(
                Intent(appContext, LauncherIconSwitchReceiver::class.java)
                    .setPackage(appContext.packageName)
                    .putExtra(EXTRA_DESIRED_ALIAS, desiredClassName),
            )
            true
        }.onFailure { error ->
            Log.w(TAG, "Could not dispatch queued launcher icon update", error)
        }.getOrDefault(false)
    }

    internal fun markApplied(context: Context, desiredClassName: String) {
        val preferences = statePreferences(context.applicationContext)
        if (preferences.getString(KEY_PENDING_ALIAS, null) == desiredClassName) {
            preferences.edit().remove(KEY_PENDING_ALIAS).commit()
        }
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

    private fun statePreferences(context: Context) =
        context.getSharedPreferences(STATE_PREFERENCES, Context.MODE_PRIVATE)

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
''')

p=root/'app/src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt'
p.write_text('''package app.arbor.chat.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Performs a queued launcher component mutation after Arbor is no longer visible. */
class LauncherIconSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val desiredAlias = intent.getStringExtra(LauncherIconManager.EXTRA_DESIRED_ALIAS) ?: return
        if (LauncherIconManager.applyDirect(context, desiredAlias)) {
            LauncherIconManager.markApplied(context, desiredAlias)
        }
    }
}
''')

p=root/'app/src/main/java/app/arbor/chat/ArborApplication.kt'
s=p.read_text()
s=s.replace('import android.content.Context\n', 'import android.content.ComponentCallbacks2\nimport android.content.Context\n')
s=s.replace('import app.arbor.chat.settings.AppPreferences\n', 'import app.arbor.chat.settings.AppPreferences\nimport app.arbor.chat.settings.LauncherIconManager\n')
s=s.replace('class ArborApplication : Application() {\n    lateinit var container: AppContainer', 'class ArborApplication : Application() {\n    private var launcherIconProcess = false\n\n    lateinit var container: AppContainer')
s=s.replace('        super.onCreate()\n        if (isLauncherIconProcess()) return\n', '        super.onCreate()\n        launcherIconProcess = isLauncherIconProcess()\n        if (launcherIconProcess) return\n')
needle='''    private fun isLauncherIconProcess(): Boolean {'''
insert='''    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (!launcherIconProcess && level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            LauncherIconManager.flushPending(this)
        }
    }

'''
s=s.replace(needle, insert+needle)
p.write_text(s)

p=root/'app/src/main/java/app/arbor/chat/MainActivity.kt'
s=p.read_text()
s=s.replace('import app.arbor.chat.settings.ColorPalette\n', 'import app.arbor.chat.settings.ColorPalette\nimport app.arbor.chat.settings.LauncherIconManager\n')
needle='''    override fun onResume() {
        super.onResume()
        (application as ArborApplication).container.openAiOAuth.onBrowserReturned()
    }
'''
replacement=needle+'''\n    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            LauncherIconManager.flushPending(applicationContext)
        }
    }
'''
s=s.replace(needle,replacement)
p.write_text(s)

p=root/'app/src/main/java/app/arbor/chat/ui/SettingsScreen.kt'
s=p.read_text().replace(
    '"Arbor stays open while the launcher refreshes. Android themed icons can override app-selected colors."',
    '"The launcher icon updates after Arbor leaves the screen, so the active app is never interrupted. Android themed icons can override app-selected colors."',
)
p.write_text(s)

p=root/'app/src/main/java/app/arbor/chat/ui/OnboardingScreen.kt'
s=p.read_text().replace(
    '"Arbor stays open while the launcher refreshes. Android themed icons can override app-selected colors."',
    '"The launcher icon updates after Arbor leaves the screen, so setup is never interrupted. Android themed icons can override app-selected colors."',
)
p.write_text(s)

p=root/'app/src/test/java/app/arbor/chat/settings/LauncherIconManagerTest.kt'
s=p.read_text()
old='''    @Test
    fun `icon switching is atomic where supported and never requests a process kill`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        assertTrue(source.contains("setComponentEnabledSettings"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertTrue(source.contains("applyEnableFirst"))
        assertTrue(source.contains("LauncherIconSwitchReceiver::class.java"))
        assertTrue(source.contains("applyDirect"))

        val receiver = File("src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt").readText()
        assertTrue(receiver.contains("BroadcastReceiver"))
        assertTrue(receiver.contains("LauncherIconManager.applyDirect"))
    }
'''
new='''    @Test
    fun `foreground settings only queue icon changes and hidden lifecycle flushes them`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        val foregroundApply = source.substringAfter("fun apply(context:")
            .substringBefore("fun flushPending")
        assertTrue(foregroundApply.contains("KEY_PENDING_ALIAS"))
        assertFalse(foregroundApply.contains("sendBroadcast"))
        assertFalse(foregroundApply.contains("setComponentEnabledSetting"))
        assertTrue(source.contains("fun flushPending"))
        assertTrue(source.contains("LauncherIconSwitchReceiver::class.java"))

        val mainActivity = File("src/main/java/app/arbor/chat/MainActivity.kt").readText()
        assertTrue(mainActivity.contains("override fun onStop()"))
        assertTrue(mainActivity.contains("LauncherIconManager.flushPending"))

        val application = File("src/main/java/app/arbor/chat/ArborApplication.kt").readText()
        assertTrue(application.contains("TRIM_MEMORY_UI_HIDDEN"))
        assertTrue(application.contains("LauncherIconManager.flushPending"))
    }

    @Test
    fun `background icon switching is atomic and acknowledges only the applied alias`() {
        val source = File("src/main/java/app/arbor/chat/settings/LauncherIconManager.kt").readText()
        assertTrue(source.contains("setComponentEnabledSettings"))
        assertTrue(source.contains("PackageManager.DONT_KILL_APP"))
        assertTrue(source.contains("applyEnableFirst"))
        assertTrue(source.contains("markApplied"))

        val receiver = File("src/main/java/app/arbor/chat/settings/LauncherIconSwitchReceiver.kt").readText()
        assertTrue(receiver.contains("BroadcastReceiver"))
        assertTrue(receiver.contains("LauncherIconManager.applyDirect"))
        assertTrue(receiver.contains("LauncherIconManager.markApplied"))
    }
'''
if old not in s: raise SystemExit('test block not found')
p.write_text(s.replace(old,new))

p=root/'app/build.gradle.kts'
s=p.read_text().replace('versionCode = 144','versionCode = 145').replace('versionName = "0.20.18"','versionName = "0.20.19"')
p.write_text(s)

p=root/'CHANGELOG.md'
s=p.read_text()
entry='''## 0.20.19 — 2026-07-31

- Stop mutating launcher aliases while Arbor is visible; icon choices are now persisted and committed only after the app leaves the screen.
- Flush pending icon changes from both MainActivity.onStop and Android's UI-hidden callback, with the component mutation still isolated in the launcher process.
- Acknowledge a pending icon only after the requested alias was applied, preventing lost updates across process teardown.
- Update Appearance and onboarding copy to describe the safe deferred refresh behavior.

'''
if '## 0.20.19' not in s:
    s=s.replace('# Changelog\n\n','# Changelog\n\n'+entry)
p.write_text(s)

p=root/'docs/releases/RELEASE_NOTES_0.20.19.md'
p.write_text('''# Arbor 0.20.19

## Launcher icon stability

- Launcher icon changes no longer touch Android component state while Arbor is visible.
- The selected icon is applied after Arbor leaves the screen, preventing One UI and other launchers from tearing down the active task.
- Pending changes survive process teardown and are cleared only after the requested alias is successfully applied.
- The in-app preview still changes immediately.

## Validation

- Added regression coverage proving foreground settings only queue changes.
- Added lifecycle coverage for both activity-stop and UI-hidden flushing.
- Retained atomic Android 13+ alias switching and `DONT_KILL_APP` for the background mutation.
''')
