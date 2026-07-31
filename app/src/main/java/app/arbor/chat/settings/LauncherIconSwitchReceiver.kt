package app.arbor.chat.settings

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
