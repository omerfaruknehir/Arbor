package app.xylune.chat.widgets

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
        val grants = storage.grants(id) ?: return Result.failure()
        val definition = XyluneProgramParser.parse(source, XyluneProgramSurface.WIDGET).getOrNull() ?: return Result.failure()
        if (!grantsSatisfy(definition, grants)) return Result.failure()
        val requested = inputData.getStringArray(KEY_SOURCES)?.toSet().orEmpty().ifEmpty { setOf("*") }
        if (definition.dataSources.isEmpty()) return Result.success()
        return runCatching {
            WidgetDataRuntime.refresh(
                context = applicationContext,
                definition = definition,
                grants = grants,
                currentState = storage.state(id).ifEmpty { definition.state },
                requestedSources = requested,
            )
        }.fold(
            onSuccess = { result ->
                storage.setState(id, result.state)
                storage.setUpdatedAt(id, result.updatedAtMillis)
                storage.setError(id, null)
                updateWidget(applicationContext, id, source, grants)
                Result.success()
            },
            onFailure = { error ->
                storage.setError(id, error.message ?: "Widget refresh failed")
                updateWidget(applicationContext, id, source, grants)
                Result.success()
            },
        )
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_SOURCES = "sources"
    }
}

internal object WidgetRefreshScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun sync(context: Context, id: Int, definition: XyluneProgramDefinition, grants: WidgetCapabilityGrants) {
        val interval = definition.refreshMinutes
        if (interval == null || !grants.backgroundRefresh || definition.dataSources.isEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork(periodicName(id))
            return
        }
        val needsNetwork = definition.dataSources.any { it.type == "http_json" }
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(interval, TimeUnit.MINUTES)
            .setInputData(input(id, setOf("*")))
            .apply { if (needsNetwork) setConstraints(network) }
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(periodicName(id), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow(context: Context, id: Int, sources: Set<String> = setOf("*")) {
        val storage = WidgetStorage(context)
        val source = storage.source(id) ?: return
        val definition = XyluneProgramParser.parse(source, XyluneProgramSurface.WIDGET).getOrNull() ?: return
        val needsNetwork = definition.dataSources.any { it.type == "http_json" && ("*" in sources || it.id in sources) }
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(input(id, sources))
            .apply { if (needsNetwork) setConstraints(network) }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(nowName(id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, id: Int) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicName(id))
        WorkManager.getInstance(context).cancelUniqueWork(nowName(id))
    }

    private fun input(id: Int, sources: Set<String>) = Data.Builder()
        .putInt(WidgetRefreshWorker.KEY_WIDGET_ID, id)
        .putStringArray(WidgetRefreshWorker.KEY_SOURCES, sources.toTypedArray())
        .build()

    private fun periodicName(id: Int) = "xylune_program_widget_periodic_$id"
    private fun nowName(id: Int) = "xylune_program_widget_refresh_$id"
}

private fun updateWidget(context: Context, id: Int, source: String, grants: WidgetCapabilityGrants) {
    AppWidgetManager.getInstance(context).updateAppWidget(id, XyluneHomeWidgetProvider.views(context, id, source, grants))
}
