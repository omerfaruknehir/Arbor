package app.arbor.chat.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

internal class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getInt(KEY_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()
        val storage = WidgetStorage(applicationContext)
        val source = storage.source(id) ?: return Result.failure()
        val definition = ArborWidgetParser.parse(source).getOrNull() ?: return Result.failure()
        val dataSource = definition.dataSource ?: return Result.success()
        storage.setState(id, STATE_LOADING, "true")
        updateWidget(applicationContext, id, source)
        return runCatching { WidgetLiveDataClient.fetch(dataSource) }.fold(
            onSuccess = { result ->
                result.values.forEach { (key, value) -> storage.setState(id, liveKey(key), value) }
                storage.setState(id, STATE_UPDATED, result.updatedAtMillis.toString())
                storage.setState(id, STATE_ERROR, "")
                storage.setState(id, STATE_LOADING, "false")
                updateWidget(applicationContext, id, source)
                Result.success()
            },
            onFailure = { error ->
                storage.setState(id, STATE_ERROR, error.message?.take(240) ?: "Refresh failed")
                storage.setState(id, STATE_LOADING, "false")
                updateWidget(applicationContext, id, source)
                Result.success()
            },
        )
    }

    companion object { const val KEY_WIDGET_ID = "widget_id" }
}

internal object WidgetRefreshScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sync(context: Context, id: Int, definition: ArborWidgetDefinition) {
        val dataSource = definition.dataSource
        if (dataSource == null) {
            WorkManager.getInstance(context).cancelUniqueWork(periodicName(id))
            return
        }
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(dataSource.refreshMinutes, TimeUnit.MINUTES)
            .setInputData(input(id))
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(periodicName(id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow(context: Context, id: Int) {
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(input(id))
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(nowName(id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(id))
        WorkManager.getInstance(context).cancelUniqueWork(nowName(id))
    }

    private fun input(id: Int) = Data.Builder().putInt(WidgetRefreshWorker.KEY_WIDGET_ID, id).build()
    private fun periodicName(id: Int) = "arbor_widget_periodic_$id"
    private fun nowName(id: Int) = "arbor_widget_refresh_$id"
}

private fun updateWidget(context: Context, id: Int, source: String) {
    AppWidgetManager.getInstance(context).updateAppWidget(id, ArborHomeWidgetProvider.views(context, id, source))
}

internal fun liveKey(id: String) = "live_$id"
internal const val STATE_LOADING = "_live_loading"
internal const val STATE_UPDATED = "_live_updated"
internal const val STATE_ERROR = "_live_error"
