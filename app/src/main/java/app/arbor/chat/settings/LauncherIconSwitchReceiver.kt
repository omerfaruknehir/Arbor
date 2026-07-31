package app.arbor.chat.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Performs launcher component mutations outside Arbor's foreground process. */
class LauncherIconSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val desiredAlias = intent.getStringExtra(LauncherIconManager.EXTRA_DESIRED_ALIAS) ?: return
        LauncherIconManager.applyDirect(context, desiredAlias)
    }
}
