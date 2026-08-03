package app.xylune.chat.generation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.xylune.chat.XyluneApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GenerationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STOP) return
        val assistantId = intent.getStringExtra(GenerationWorker.KEY_ASSISTANT_ID) ?: return
        val result = goAsync()
        val container = (context.applicationContext as XyluneApplication).container
        container.scheduler.stop(assistantId)
        CoroutineScope(Dispatchers.IO).launch {
            try { container.repository.markInterrupted(assistantId, "Stopped from notification") }
            finally { result.finish() }
        }
    }

    companion object {
        const val ACTION_STOP = "app.xylune.chat.STOP_GENERATION"
    }
}
