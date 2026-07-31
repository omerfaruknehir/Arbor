package app.arbor.chat.settings

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

/** Applies the icon in the isolated process, then reopens the saved Arbor session. */
class LauncherIconSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val desiredAlias = intent.getStringExtra(LauncherIconManager.EXTRA_DESIRED_ALIAS) ?: return
        val relaunch = intent.pendingIntentExtra(LauncherIconManager.EXTRA_RELAUNCH_INTENT) ?: return
        val pendingResult = goAsync()
        Thread {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            try {
                // PackageManager changes can kill every Arbor process on some One UI builds.
                // Register a system-owned fallback before touching component state so the
                // relaunch survives even that full package teardown.
                alarmManager?.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + RELAUNCH_FALLBACK_DELAY_MS,
                    relaunch,
                )
                LauncherIconManager.applyDirect(context, desiredAlias)
                Thread.sleep(RELAUNCH_DIRECT_DELAY_MS)
                runCatching { relaunch.send() }
                    .onSuccess { alarmManager?.cancel(relaunch) }
                    .onFailure {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_MAIN).apply {
                                    component = ComponentName(context.packageName, desiredAlias)
                                    addCategory(Intent.CATEGORY_LAUNCHER)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                },
                            )
                        }
                    }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun Intent.pendingIntentExtra(key: String): PendingIntent? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, PendingIntent::class.java)
        else getParcelableExtra(key)

    private companion object {
        const val RELAUNCH_DIRECT_DELAY_MS = 180L
        const val RELAUNCH_FALLBACK_DELAY_MS = 750L
    }
}
