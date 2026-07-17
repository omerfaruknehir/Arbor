package app.arbor.chat.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import app.arbor.chat.MainActivity
import app.arbor.chat.R
import java.text.DateFormat
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Date
import java.util.UUID

enum class WidgetPinResult { REQUESTED, UNSUPPORTED, INVALID }

object WidgetPinning {
    @Suppress("UnspecifiedImmutableFlag")
    fun request(context: Context, source: String): WidgetPinResult {
        val definition = ArborWidgetParser.parse(source).getOrNull() ?: return WidgetPinResult.INVALID
        if (!definition.homeEnabled || source.length > MAX_WIDGET_SOURCE) return WidgetPinResult.INVALID
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return WidgetPinResult.UNSUPPORTED
        val token = UUID.randomUUID().toString()
        WidgetStorage(context).savePending(token, source)
        val callbackIntent = Intent(context, WidgetPinReceiver::class.java).putExtra(EXTRA_TOKEN, token)
        val callback = PendingIntent.getBroadcast(
            context, token.hashCode(), callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val preview = ArborHomeWidgetProvider.views(context, AppWidgetManager.INVALID_APPWIDGET_ID, source, preview = true)
        val extras = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview) }
        return if (manager.requestPinAppWidget(ComponentName(context, ArborHomeWidgetProvider::class.java), extras, callback)) {
            WidgetPinResult.REQUESTED
        } else WidgetPinResult.UNSUPPORTED
    }
}

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || token.isBlank()) return
        val storage = WidgetStorage(context)
        val source = storage.takePending(token) ?: return
        val definition = ArborWidgetParser.parse(source).getOrNull() ?: return
        storage.save(id, source)
        WidgetRefreshScheduler.sync(context, id, definition)
        AppWidgetManager.getInstance(context).updateAppWidget(id, ArborHomeWidgetProvider.views(context, id, source))
        if (definition.dataSource != null) WidgetRefreshScheduler.refreshNow(context, id)
    }
}

class ArborHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val storage = WidgetStorage(context)
        appWidgetIds.forEach { id ->
            val source = storage.source(id)
            val definition = source?.let { ArborWidgetParser.parse(it).getOrNull() }
            if (definition != null) WidgetRefreshScheduler.sync(context, id, definition)
            manager.updateAppWidget(id, views(context, id, source))
            if (definition?.dataSource != null && storage.state(id, STATE_UPDATED) == null) {
                WidgetRefreshScheduler.refreshNow(context, id)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val storage = WidgetStorage(context)
        appWidgetIds.forEach { id ->
            WidgetRefreshScheduler.cancel(context, id)
            storage.delete(id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val storage = WidgetStorage(context)
        val source = storage.source(id) ?: return
        val definition = ArborWidgetParser.parse(source).getOrNull() ?: return
        when (intent.action) {
            ACTION_WIDGET_ACTION -> {
                val index = intent.getIntExtra(EXTRA_ACTION_INDEX, -1)
                if (index !in 0..3) return
                applyAction(storage, id, definition, homeActions(definition).getOrNull(index))
            }
            ACTION_CALCULATOR -> applyCalculator(storage, id, definition, intent.getStringExtra(EXTRA_CALCULATOR_KEY).orEmpty())
            ACTION_REFRESH -> WidgetRefreshScheduler.refreshNow(context, id)
            ACTION_MINI_APP -> applyMiniAppAction(context, storage, id, definition, intent)
            else -> return
        }
        AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, id, source))
    }

    companion object {
        fun views(context: Context, id: Int, source: String?, preview: Boolean = false): RemoteViews {
            val definition = source?.let { ArborWidgetParser.parse(it).getOrNull() }
            if (definition == null) return emptyViews(context)
            return when (definition.type) {
                "calculator" -> calculatorViews(context, id, definition, preview)
                "live_data", "stock" -> liveViews(context, id, definition, preview)
                "schedule", "prayer_times" -> scheduleViews(context, id, definition, preview)
                "mini_app" -> miniAppViews(context, id, definition, preview)
                else -> genericViews(context, id, definition, preview)
            }
        }

        private fun emptyViews(context: Context) = RemoteViews(context.packageName, R.layout.arbor_home_widget).apply {
            setTextViewText(R.id.widget_title, "Arbor widget")
            setTextViewText(R.id.widget_subtitle, "Pin a generated mini-app from a conversation")
            setTextViewText(R.id.widget_result, "Ready")
            ACTION_IDS.forEach { setViewVisibility(it, View.GONE) }
        }

        private fun genericViews(context: Context, id: Int, definition: ArborWidgetDefinition, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.arbor_home_widget)
            val storage = WidgetStorage(context)
            val (result, subtitle) = widgetResult(storage, id, definition, preview)
            views.setTextViewText(R.id.widget_title, definition.title)
            views.setTextViewText(R.id.widget_subtitle, subtitle)
            views.setTextViewText(R.id.widget_result, result)
            bindOpen(context, views, id, preview)
            val actions = homeActions(definition)
            ACTION_IDS.forEachIndexed { index, viewId ->
                val action = actions.getOrNull(index)
                views.setViewVisibility(viewId, if (action == null) View.GONE else View.VISIBLE)
                if (action != null) {
                    views.setTextViewText(viewId, action.label)
                    bindBroadcast(context, views, viewId, id, index, ACTION_WIDGET_ACTION, preview) {
                        putExtra(EXTRA_ACTION_INDEX, index)
                    }
                }
            }
            return views
        }

        private fun calculatorViews(context: Context, id: Int, definition: ArborWidgetDefinition, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.arbor_home_widget_calculator)
            val storage = WidgetStorage(context)
            val fallback = definition.fields.firstOrNull()?.value.orEmpty()
            val expression = if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) fallback else storage.state(id, CALCULATOR_EXPRESSION).orEmpty()
            val error = !preview && id != AppWidgetManager.INVALID_APPWIDGET_ID && storage.state(id, CALCULATOR_ERROR) == "true"
            views.setTextViewText(R.id.widget_title, definition.title)
            views.setTextViewText(R.id.calculator_expression, if (error) "Error" else expression.ifBlank { "0" })
            bindOpen(context, views, id, preview)
            CALCULATOR_KEYS.forEachIndexed { index, (viewId, key, label) ->
                views.setTextViewText(viewId, label)
                bindBroadcast(context, views, viewId, id, index, ACTION_CALCULATOR, preview) {
                    putExtra(EXTRA_CALCULATOR_KEY, key)
                }
            }
            return views
        }

        private fun liveViews(context: Context, id: Int, definition: ArborWidgetDefinition, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.arbor_home_widget_live)
            val storage = WidgetStorage(context)
            fun state(name: String): String? = if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) null else storage.state(id, name)
            val bindings = definition.dataSource?.bindings.orEmpty()
            val formatted = bindings.map { binding -> binding to state(liveKey(binding.id))?.let { formatBinding(binding, it) } }
            views.setTextViewText(R.id.widget_title, definition.title)
            views.setTextViewText(R.id.live_symbol, definition.symbol.ifBlank { definition.description.ifBlank { "Live data" } })
            views.setTextViewText(R.id.live_primary, formatted.firstOrNull()?.second ?: "—")
            views.setTextViewText(R.id.live_metric_2, formatted.getOrNull(1)?.let { "${it.first.label}\n${it.second ?: "—"}" }.orEmpty())
            views.setTextViewText(R.id.live_metric_3, formatted.getOrNull(2)?.let { "${it.first.label}\n${it.second ?: "—"}" }.orEmpty())
            views.setTextViewText(R.id.live_status, liveStatus(storage, id, definition, preview))
            views.setTextViewText(R.id.live_refresh, "↻")
            bindOpen(context, views, id, preview)
            bindBroadcast(context, views, R.id.live_refresh, id, 80, ACTION_REFRESH, preview)
            return views
        }

        private fun scheduleViews(context: Context, id: Int, definition: ArborWidgetDefinition, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.arbor_home_widget_schedule)
            val storage = WidgetStorage(context)
            val effective = definition.schedule.map { item ->
                val live = if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) null else storage.state(id, liveKey(item.id))
                item.copy(time = TIME_VALUE.find(live.orEmpty())?.value ?: item.time)
            }
            val next = nextScheduleItem(effective, definition.timezone)
            views.setTextViewText(R.id.widget_title, definition.title)
            views.setTextViewText(R.id.schedule_next, next?.let { "Next: ${it.item.label} ${it.item.time} • ${durationLabel(it.remaining)}" } ?: "Schedule")
            SCHEDULE_ROWS.forEachIndexed { index, viewId ->
                val item = effective.getOrNull(index)
                views.setViewVisibility(viewId, if (item == null) View.GONE else View.VISIBLE)
                if (item != null) {
                    val detail = item.detail.takeIf(String::isNotBlank)?.let { "  ·  $it" }.orEmpty()
                    views.setTextViewText(viewId, "${item.time}   ${item.label}$detail")
                    views.setTextColor(viewId, context.getColor(if (item.id == next?.item?.id) R.color.widget_accent else R.color.widget_text))
                }
            }
            views.setViewVisibility(R.id.schedule_refresh, if (definition.dataSource == null) View.GONE else View.VISIBLE)
            if (definition.dataSource != null) {
                views.setTextViewText(R.id.schedule_refresh, "↻")
                bindBroadcast(context, views, R.id.schedule_refresh, id, 81, ACTION_REFRESH, preview)
            }
            bindOpen(context, views, id, preview)
            return views
        }

        private fun miniAppViews(context: Context, id: Int, definition: ArborWidgetDefinition, preview: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.arbor_home_widget_mini_app)
            val app = definition.miniApp ?: return views
            val storage = WidgetStorage(context)
            val state = miniState(storage, id, definition, preview)
            val screenIndex = app.screens.indexOfFirst { it.id == state[ArborMiniAppRuntime.SCREEN_STATE] }.takeIf { it >= 0 } ?: 0
            val screen = app.screens[screenIndex]
            views.setTextViewText(R.id.widget_title, definition.title)
            val submission = if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) null else storage.state(id, MINI_SUBMISSION)
            views.setTextViewText(
                R.id.mini_app_screen_title,
                submission?.takeIf(String::isNotBlank)?.let { "Saved: ${it.take(80)}" } ?: screen.title.ifBlank { definition.description },
            )
            views.setViewVisibility(R.id.mini_app_refresh, if (definition.dataSource == null) View.GONE else View.VISIBLE)
            if (definition.dataSource != null) {
                views.setTextViewText(R.id.mini_app_refresh, "↻")
                bindBroadcast(context, views, R.id.mini_app_refresh, id, 82, ACTION_REFRESH, preview)
            }
            views.removeAllViews(R.id.mini_app_body)
            var rows = 0
            var charts = 0
            fun add(row: RemoteViews) {
                if (rows++ < MAX_HOME_ROWS) views.addView(R.id.mini_app_body, row)
            }
            screen.components.forEachIndexed { componentIndex, component ->
                if (rows >= MAX_HOME_ROWS || !ArborMiniAppRuntime.visible(component.visibleWhen, state)) return@forEachIndexed
                when (component.type) {
                    "text" -> add(miniTextRow(context, ArborMiniAppRuntime.render(component.text.ifBlank { component.value }, state), ""))
                    "metric", "input", "timer" -> add(miniMetricRow(context, component.label, miniComponentValue(component, state)))
                    "slider" -> {
                        add(miniTextRow(context, component.label.ifBlank { component.id }, miniComponentValue(component, state)))
                        add(miniButtonsRow(context, id, preview, screenIndex, componentIndex, listOf(
                            MiniHomeButton("−", "slider_minus", 0), MiniHomeButton("+", "slider_plus", 1),
                        )))
                    }
                    "toggle" -> add(miniButtonsRow(context, id, preview, screenIndex, componentIndex, listOf(
                        MiniHomeButton("${component.label.ifBlank { component.id }}: ${if (miniTruthy(state[component.id])) "On" else "Off"}", "toggle", 0),
                    )))
                    "choice" -> component.options.chunked(4).forEachIndexed { chunkIndex, options ->
                        add(miniButtonsRow(context, id, preview, screenIndex, componentIndex, options.mapIndexed { index, option ->
                            MiniHomeButton(if (state[component.id] == option) "• $option" else option, "choice", chunkIndex * 4 + index)
                        }))
                    }
                    "buttons" -> component.buttons.withIndex().filter { ArborMiniAppRuntime.visible(it.value.visibleWhen, state) }.chunked(4).forEach { buttons ->
                        add(miniButtonsRow(context, id, preview, screenIndex, componentIndex, buttons.map {
                            MiniHomeButton(ArborMiniAppRuntime.render(it.value.label, state), "button", it.index)
                        }))
                    }
                    "progress" -> add(miniProgressRow(context, component, state))
                    "list", "table" -> component.items.withIndex().filter { ArborMiniAppRuntime.visible(it.value.visibleWhen, state) }.forEach { item ->
                        add(miniTextRow(
                            context,
                            ArborMiniAppRuntime.render(item.value.label, state),
                            ArborMiniAppRuntime.render(item.value.value, state),
                        ).also { row ->
                            if (item.value.actions.isNotEmpty()) bindMiniBroadcast(context, row, R.id.mini_row_root, id, preview, screenIndex, componentIndex, "item", item.index)
                        })
                    }
                    "chart" -> if (charts++ < MAX_HOME_CHARTS) add(RemoteViews(context.packageName, R.layout.arbor_widget_row_chart).apply {
                            setImageViewBitmap(R.id.mini_chart, renderMiniChart(context, component, state))
                        })
                    "divider" -> add(RemoteViews(context.packageName, R.layout.arbor_widget_row_divider))
                    "spacer" -> Unit
                }
            }
            bindMiniNavigation(context, views, id, preview, app, screenIndex)
            bindOpen(context, views, id, preview)
            return views
        }

        private fun miniTextRow(context: Context, label: String, value: String) =
            RemoteViews(context.packageName, R.layout.arbor_widget_row_text).apply {
                setTextViewText(R.id.mini_row_label, label)
                setTextViewText(R.id.mini_row_value, value)
                setViewVisibility(R.id.mini_row_value, if (value.isBlank()) View.GONE else View.VISIBLE)
            }

        private fun miniMetricRow(context: Context, label: String, value: String) =
            RemoteViews(context.packageName, R.layout.arbor_widget_row_metric).apply {
                setTextViewText(R.id.mini_metric_label, label)
                setTextViewText(R.id.mini_metric_value, value)
                setViewVisibility(R.id.mini_metric_label, if (label.isBlank()) View.GONE else View.VISIBLE)
            }

        private fun miniProgressRow(context: Context, component: ArborMiniAppComponent, state: Map<String, String>): RemoteViews {
            val value = miniNumericValue(component, state)
            val progress = (((value - component.min) / (component.max - component.min).coerceAtLeast(0.000001)) * 1_000).toInt().coerceIn(0, 1_000)
            return RemoteViews(context.packageName, R.layout.arbor_widget_row_progress).apply {
                setTextViewText(R.id.mini_progress_label, "${component.label}: ${compactHome(value)} / ${compactHome(component.max)}")
                setProgressBar(R.id.mini_progress, 1_000, progress, false)
            }
        }

        private fun miniButtonsRow(
            context: Context,
            id: Int,
            preview: Boolean,
            screenIndex: Int,
            componentIndex: Int,
            buttons: List<MiniHomeButton>,
        ): RemoteViews = RemoteViews(context.packageName, R.layout.arbor_widget_row_buttons).apply {
            MINI_BUTTON_IDS.forEachIndexed { index, viewId ->
                val button = buttons.getOrNull(index)
                setViewVisibility(viewId, if (button == null) View.GONE else View.VISIBLE)
                if (button != null) {
                    setTextViewText(viewId, button.label)
                    bindMiniBroadcast(context, this, viewId, id, preview, screenIndex, componentIndex, button.kind, button.childIndex)
                }
            }
        }

        private fun bindMiniNavigation(
            context: Context,
            views: RemoteViews,
            id: Int,
            preview: Boolean,
            app: ArborMiniAppDefinition,
            currentIndex: Int,
        ) {
            views.setViewVisibility(R.id.mini_app_navigation, if (app.screens.size <= 1) View.GONE else View.VISIBLE)
            if (app.screens.size <= 1) return
            if (app.screens.size <= MINI_NAV_IDS.size) {
                MINI_NAV_IDS.forEachIndexed { index, viewId ->
                    val screen = app.screens.getOrNull(index)
                    views.setViewVisibility(viewId, if (screen == null) View.GONE else View.VISIBLE)
                    if (screen != null) {
                        views.setTextViewText(viewId, if (index == currentIndex) "• ${screen.title.ifBlank { screen.id }}" else screen.title.ifBlank { screen.id })
                        bindMiniBroadcast(context, views, viewId, id, preview, currentIndex, -1, "nav", index)
                    }
                }
            } else {
                val labels = listOf("‹", "${currentIndex + 1}/${app.screens.size}", "›", "⌂")
                val kinds = listOf("nav_previous", "none", "nav_next", "nav_home")
                MINI_NAV_IDS.forEachIndexed { index, viewId ->
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, labels[index])
                    if (kinds[index] != "none") bindMiniBroadcast(context, views, viewId, id, preview, currentIndex, -1, kinds[index], index)
                }
            }
        }

        private fun bindMiniBroadcast(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            id: Int,
            preview: Boolean,
            screenIndex: Int,
            componentIndex: Int,
            kind: String,
            childIndex: Int,
        ) {
            if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val intent = Intent(context, ArborHomeWidgetProvider::class.java)
                .setAction(ACTION_MINI_APP)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_MINI_SCREEN, screenIndex)
                .putExtra(EXTRA_MINI_COMPONENT, componentIndex)
                .putExtra(EXTRA_MINI_KIND, kind)
                .putExtra(EXTRA_MINI_CHILD, childIndex)
            val offset = 1_000 + screenIndex * 1_000 + (componentIndex + 1) * 30 + childIndex.coerceAtLeast(0)
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(context, id * 10_000 + offset, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }

        private fun applyMiniAppAction(context: Context, storage: WidgetStorage, id: Int, definition: ArborWidgetDefinition, intent: Intent) {
            val app = definition.miniApp ?: return
            val screenIndex = intent.getIntExtra(EXTRA_MINI_SCREEN, 0).coerceIn(app.screens.indices)
            val componentIndex = intent.getIntExtra(EXTRA_MINI_COMPONENT, -1)
            val childIndex = intent.getIntExtra(EXTRA_MINI_CHILD, -1)
            val kind = intent.getStringExtra(EXTRA_MINI_KIND).orEmpty()
            val screen = app.screens[screenIndex]
            val component = screen.components.getOrNull(componentIndex)
            val actions = when (kind) {
                "button" -> component?.buttons?.getOrNull(childIndex)?.actions
                "item" -> component?.items?.getOrNull(childIndex)?.actions
                "toggle" -> component?.let { listOf(ArborMiniAppAction("toggle", target = it.id)) }
                "choice" -> component?.options?.getOrNull(childIndex)?.let { listOf(ArborMiniAppAction("set", target = component.id, value = it)) }
                "slider_minus" -> component?.let { listOf(ArborMiniAppAction("add", target = it.id, value = (-it.step).toString())) }
                "slider_plus" -> component?.let { listOf(ArborMiniAppAction("add", target = it.id, value = it.step.toString())) }
                "nav" -> app.screens.getOrNull(childIndex)?.let { listOf(ArborMiniAppAction("navigate", screen = it.id)) }
                "nav_previous" -> listOf(ArborMiniAppAction("navigate", screen = app.screens[(screenIndex - 1 + app.screens.size) % app.screens.size].id))
                "nav_next" -> listOf(ArborMiniAppAction("navigate", screen = app.screens[(screenIndex + 1) % app.screens.size].id))
                "nav_home" -> listOf(ArborMiniAppAction("navigate", screen = app.screens.first().id))
                else -> null
            } ?: return
            val defaults = app.initialState + (ArborMiniAppRuntime.SCREEN_STATE to app.screens.first().id)
            val current = miniState(storage, id, definition, preview = false)
            val transition = ArborMiniAppRuntime.apply(actions, current, defaults)
            val nextState = transition.state.toMutableMap()
            if (kind in setOf("slider_minus", "slider_plus") && component != null) {
                nextState[component.id] = compactHome(
                    (nextState[component.id]?.toDoubleOrNull() ?: component.min).coerceIn(component.min, component.max),
                )
            }
            if (actions.any { it.operation == "reset" }) storage.clearState(id)
            nextState.forEach { (key, value) -> storage.setState(id, key, value) }
            transition.submitMessage?.let { storage.setState(id, MINI_SUBMISSION, it) }
            if (transition.refreshRequested) WidgetRefreshScheduler.refreshNow(context, id)
        }

        private fun miniState(storage: WidgetStorage, id: Int, definition: ArborWidgetDefinition, preview: Boolean): Map<String, String> {
            val app = definition.miniApp ?: return emptyMap()
            val defaults = app.initialState + (ArborMiniAppRuntime.SCREEN_STATE to app.screens.first().id)
            if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) return defaults
            val keys = defaults.keys.toMutableSet()
            app.screens.flatMap { it.components }.forEach { component ->
                keys += component.id
                keys += "${component.id}_running"
                keys += "${component.id}_started_at"
                keys += "${component.id}_started_value"
                component.buttons.flatMap { it.actions }.forEach { if (it.target.isNotBlank()) keys += it.target }
                component.items.flatMap { it.actions }.forEach { if (it.target.isNotBlank()) keys += it.target }
            }
            val result = keys.associateWith { key -> storage.state(id, key) ?: defaults[key].orEmpty() }.toMutableMap()
            definition.dataSource?.bindings.orEmpty().forEach { binding ->
                storage.state(id, liveKey(binding.id))?.let { result[binding.id] = it }
            }
            app.screens.flatMap { it.components }.filter { it.type == "timer" && result["${it.id}_running"] == "true" }.forEach { timer ->
                val startedAt = result["${timer.id}_started_at"]?.toLongOrNull() ?: System.currentTimeMillis()
                val startedValue = result["${timer.id}_started_value"]?.toLongOrNull() ?: result[timer.id]?.toLongOrNull() ?: 0L
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0)
                val current = if (timer.value == "countdown") (startedValue - elapsed).coerceAtLeast(0) else startedValue + elapsed
                result[timer.id] = current.toString()
                if (timer.value == "countdown" && current == 0L) result["${timer.id}_running"] = "false"
            }
            return result
        }

        private fun miniComponentValue(component: ArborMiniAppComponent, state: Map<String, String>): String {
            if (component.type == "timer") {
                val seconds = state[component.id]?.toLongOrNull()?.coerceAtLeast(0) ?: 0
                return "%02d:%02d:%02d".format(seconds / 3_600, (seconds % 3_600) / 60, seconds % 60)
            }
            val raw = component.expression.takeIf(String::isNotBlank)?.let {
                SafeExpression.evaluate(it, ArborMiniAppRuntime.numericState(state)).getOrNull()?.let { number -> SafeExpression.format(number, component.decimals) }
            } ?: ArborMiniAppRuntime.render(component.value.ifBlank { "{{${component.id}}}" }, state)
            return component.prefix + raw + component.suffix
        }

        private fun miniNumericValue(component: ArborMiniAppComponent, state: Map<String, String>): Double =
            component.expression.takeIf(String::isNotBlank)?.let { SafeExpression.evaluate(it, ArborMiniAppRuntime.numericState(state)).getOrNull() }
                ?: state[component.id]?.toDoubleOrNull()
                ?: ArborMiniAppRuntime.render(component.value, state).toDoubleOrNull() ?: 0.0

        private fun renderMiniChart(context: Context, component: ArborMiniAppComponent, state: Map<String, String>): Bitmap {
            val bitmap = createBitmap(480, 140)
            val canvas = Canvas(bitmap)
            val values = component.items.filter { ArborMiniAppRuntime.visible(it.visibleWhen, state) }.map { item ->
                val raw = ArborMiniAppRuntime.render(item.value, state)
                raw.toDoubleOrNull() ?: SafeExpression.evaluate(raw, ArborMiniAppRuntime.numericState(state)).getOrNull() ?: 0.0
            }
            if (values.isEmpty()) return bitmap
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.widget_accent); strokeWidth = 6f; style = Paint.Style.FILL }
            val chartType = component.value.lowercase()
            if (chartType in setOf("pie", "donut")) {
                val total = values.sumOf { kotlin.math.abs(it) }.coerceAtLeast(0.000001)
                var start = -90f
                values.forEachIndexed { index, value ->
                    paint.color = Color.HSVToColor(floatArrayOf((210f + index * 47f) % 360f, .65f, .88f))
                    val sweep = (kotlin.math.abs(value) / total * 360).toFloat()
                    canvas.drawArc(RectF(170f, 4f, 310f, 136f), start, sweep, true, paint)
                    start += sweep
                }
                if (chartType == "donut") {
                    paint.color = context.getColor(R.color.widget_background)
                    canvas.drawCircle(240f, 70f, 35f, paint)
                }
            } else {
                val max = values.maxOrNull()?.coerceAtLeast(0.0) ?: 1.0
                val min = values.minOrNull()?.coerceAtMost(0.0) ?: 0.0
                val span = (max - min).coerceAtLeast(0.000001)
                val step = 440f / values.size.coerceAtLeast(1)
                if (chartType in setOf("line", "area", "scatter")) {
                    paint.style = Paint.Style.STROKE
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = 20f + step * (index + .5f)
                        val y = 126f - ((value - min) / span * 112).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        if (chartType == "scatter") canvas.drawCircle(x, y, 5f, paint)
                    }
                    if (chartType != "scatter") canvas.drawPath(path, paint)
                } else {
                    values.forEachIndexed { index, value ->
                        val left = 20f + step * index + 4f
                        val top = 126f - ((value - min) / span * 112).toFloat()
                        canvas.drawRoundRect(left, top, left + step - 8f, 126f, 6f, 6f, paint)
                    }
                }
            }
            return bitmap
        }

        private fun miniTruthy(value: String?): Boolean = value.equals("true", true) || value?.toDoubleOrNull()?.let { it != 0.0 } == true

        private data class MiniHomeButton(val label: String, val kind: String, val childIndex: Int)

        private fun bindOpen(context: Context, views: RemoteViews, id: Int, preview: Boolean) {
            if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val open = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
        }

        private fun bindBroadcast(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            id: Int,
            requestOffset: Int,
            action: String,
            preview: Boolean,
            extras: Intent.() -> Unit = {},
        ) {
            if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val intent = Intent(context, ArborHomeWidgetProvider::class.java)
                .setAction(action)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .apply(extras)
            views.setOnClickPendingIntent(
                viewId,
                PendingIntent.getBroadcast(context, id * 100 + requestOffset, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }

        private fun liveStatus(storage: WidgetStorage, id: Int, definition: ArborWidgetDefinition, preview: Boolean): String {
            if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) return "Refresh every ${definition.dataSource?.refreshMinutes ?: 30} min"
            if (storage.state(id, STATE_LOADING) == "true") return "Refreshing…"
            val error = storage.state(id, STATE_ERROR).orEmpty()
            val updated = storage.state(id, STATE_UPDATED)?.toLongOrNull()
            if (error.isNotBlank()) return "Could not refresh • tap ↻"
            return updated?.let { "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))}" }
                ?: "Tap ↻ to load"
        }

        private fun formatBinding(binding: ArborWidgetBinding, raw: String): String {
            val numeric = raw.replace(",", "").toDoubleOrNull()
            val value = numeric?.let { SafeExpression.format(it, binding.decimals) } ?: raw.take(80)
            return binding.prefix + value + binding.suffix
        }

        private fun widgetResult(storage: WidgetStorage, id: Int, definition: ArborWidgetDefinition, preview: Boolean): Pair<String, String> {
            fun state(name: String, fallback: String) = if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) fallback else storage.state(id, name) ?: fallback
            return when (definition.type) {
                "converter" -> {
                    val amount = state("amount", definition.value.takeIf { it != 0.0 }?.toString() ?: "1").toDoubleOrNull() ?: 0.0
                    val swapped = state("_swapped", "false") == "true"
                    val rate = if (swapped) 1.0 / definition.rate else definition.rate
                    val from = if (swapped) definition.toUnit else definition.fromUnit
                    val to = if (swapped) definition.fromUnit else definition.toUnit
                    "${compactHome(amount * rate)} $to" to "${compactHome(amount)} $from • rate ${compactHome(rate)}"
                }
                "counter", "slider" -> {
                    val value = state("value", definition.value.toString()).toDoubleOrNull() ?: definition.value
                    compactHome(value) to definition.type.replaceFirstChar(Char::uppercase)
                }
                "choice" -> state("_choice", "Choose an option") to "Tap an option below"
                "checklist" -> state("_choice", "Open Arbor to select") to "Interactive checklist"
                "rating" -> {
                    val rating = state("value", definition.value.toString()).toDoubleOrNull() ?: definition.value
                    "★".repeat(rating.toInt().coerceIn(0, definition.max.toInt().coerceIn(1, 10))) to "Rating • ${compactHome(rating)}/${compactHome(definition.max)}"
                }
                "progress" -> compactHome(definition.value) to "Progress • maximum ${compactHome(definition.max)}"
                else -> {
                    val variables = definition.fields.associate { field ->
                        val fallback = field.value.toDoubleOrNull() ?: 0.0
                        field.id to (state(field.id, fallback.toString()).toDoubleOrNull() ?: fallback)
                    }
                    val output = definition.outputs.firstOrNull()
                    val value = output?.let { SafeExpression.evaluate(it.expression, variables).getOrNull() }
                    (if (value == null) "Open Arbor" else output.prefix + SafeExpression.format(value, output.decimals) + output.suffix) to
                        (output?.label ?: definition.description.ifBlank { "Generated form" })
                }
            }
        }

        private fun applyAction(storage: WidgetStorage, id: Int, definition: ArborWidgetDefinition, action: ArborWidgetAction?) {
            if (action == null) return
            val target = action.target.ifBlank { primaryTarget(definition) }
            if (action.operation == "reset") {
                storage.clearState(id)
                return
            }
            if (target == "_swap") {
                storage.setState(id, "_swapped", (storage.state(id, "_swapped") != "true").toString())
                return
            }
            if (target == "_choice") {
                storage.setState(id, target, action.label)
                return
            }
            val fallback = defaultValue(definition, target)
            val current = storage.state(id, target)?.toDoubleOrNull() ?: fallback
            val next = when (action.operation) {
                "add" -> current + action.value
                "multiply" -> current * action.value
                "set" -> action.value
                "toggle" -> if (current == 0.0) 1.0 else 0.0
                else -> current
            }
            storage.setState(id, target, next.toString())
        }

        private fun applyCalculator(storage: WidgetStorage, id: Int, definition: ArborWidgetDefinition, key: String) {
            var expression = storage.state(id, CALCULATOR_EXPRESSION) ?: definition.fields.firstOrNull()?.value.orEmpty()
            storage.setState(id, CALCULATOR_ERROR, "false")
            expression = when (key) {
                "clear" -> ""
                "backspace" -> expression.dropLast(1)
                "equals" -> SafeExpression.evaluate(expression).getOrNull()?.let(::compactHome) ?: run {
                    storage.setState(id, CALCULATOR_ERROR, "true")
                    expression
                }
                "percent" -> SafeExpression.evaluate(expression).getOrNull()?.div(100.0)?.let(::compactHome) ?: expression
                "sign" -> when {
                    expression.isBlank() -> "-"
                    expression.startsWith("-(") && expression.endsWith(")") -> expression.substring(2, expression.length - 1)
                    else -> "-($expression)"
                }
                in CALCULATOR_TOKENS -> (expression + key).take(500)
                else -> expression
            }
            storage.setState(id, CALCULATOR_EXPRESSION, expression)
        }

        private fun homeActions(definition: ArborWidgetDefinition): List<ArborWidgetAction> {
            if (definition.actions.isNotEmpty()) return definition.actions.take(4)
            return when (definition.type) {
                "converter" -> listOf(
                    ArborWidgetAction("−${compactHome(definition.step)}", "amount", "add", -definition.step),
                    ArborWidgetAction("+${compactHome(definition.step)}", "amount", "add", definition.step),
                    ArborWidgetAction("Swap", "_swap", "toggle"),
                    ArborWidgetAction("Reset", operation = "reset"),
                )
                "counter", "slider" -> listOf(
                    ArborWidgetAction("−", "value", "add", -definition.step),
                    ArborWidgetAction("+", "value", "add", definition.step),
                    ArborWidgetAction("Reset", operation = "reset"),
                )
                "rating" -> (1..definition.max.toInt().coerceIn(1, 4)).map { ArborWidgetAction("$it★", "value", "set", it.toDouble()) }
                "choice" -> definition.options.take(4).map { ArborWidgetAction(it, "_choice", "set") }
                "form" -> listOf(
                    ArborWidgetAction("−", primaryTarget(definition), "add", -definition.fields.firstOrNull { it.id == primaryTarget(definition) }?.step.orOne()),
                    ArborWidgetAction("+", primaryTarget(definition), "add", definition.fields.firstOrNull { it.id == primaryTarget(definition) }?.step.orOne()),
                    ArborWidgetAction("Reset", operation = "reset"),
                )
                else -> emptyList()
            }
        }

        private fun primaryTarget(definition: ArborWidgetDefinition): String = when (definition.type) {
            "converter" -> "amount"
            "counter", "slider", "rating" -> "value"
            else -> definition.fields.firstOrNull { it.kind in setOf("number", "slider", "toggle") }?.id.orEmpty()
        }

        private fun defaultValue(definition: ArborWidgetDefinition, target: String): Double = when (target) {
            "amount", "value" -> definition.value
            else -> definition.fields.firstOrNull { it.id == target }?.value?.toDoubleOrNull() ?: 0.0
        }

        private data class NextSchedule(val item: ArborWidgetScheduleItem, val remaining: Duration)

        private fun nextScheduleItem(items: List<ArborWidgetScheduleItem>, timezone: String): NextSchedule? {
            val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.systemDefault() }
            val now = ZonedDateTime.now(zone)
            return items.mapNotNull { item ->
                val time = runCatching { LocalTime.parse(item.time) }.getOrNull() ?: return@mapNotNull null
                var occurrence = now.toLocalDate().atTime(time).atZone(zone)
                if (!occurrence.isAfter(now)) occurrence = occurrence.plusDays(1)
                NextSchedule(item, Duration.between(now, occurrence))
            }.minByOrNull { it.remaining }
        }

        private fun durationLabel(duration: Duration): String {
            val minutes = duration.toMinutes().coerceAtLeast(0)
            val hours = minutes / 60
            return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
        }

        private val ACTION_IDS = intArrayOf(R.id.widget_action_1, R.id.widget_action_2, R.id.widget_action_3, R.id.widget_action_4)
        private val MINI_BUTTON_IDS = intArrayOf(R.id.mini_button_1, R.id.mini_button_2, R.id.mini_button_3, R.id.mini_button_4)
        private val MINI_NAV_IDS = intArrayOf(R.id.mini_nav_1, R.id.mini_nav_2, R.id.mini_nav_3, R.id.mini_nav_4)
        private val SCHEDULE_ROWS = intArrayOf(R.id.schedule_row_1, R.id.schedule_row_2, R.id.schedule_row_3, R.id.schedule_row_4, R.id.schedule_row_5, R.id.schedule_row_6)
        private val CALCULATOR_KEYS = listOf(
            Triple(R.id.calc_clear, "clear", "C"), Triple(R.id.calc_backspace, "backspace", "⌫"), Triple(R.id.calc_percent, "percent", "%"), Triple(R.id.calc_divide, "/", "÷"),
            Triple(R.id.calc_7, "7", "7"), Triple(R.id.calc_8, "8", "8"), Triple(R.id.calc_9, "9", "9"), Triple(R.id.calc_multiply, "*", "×"),
            Triple(R.id.calc_4, "4", "4"), Triple(R.id.calc_5, "5", "5"), Triple(R.id.calc_6, "6", "6"), Triple(R.id.calc_minus, "-", "−"),
            Triple(R.id.calc_1, "1", "1"), Triple(R.id.calc_2, "2", "2"), Triple(R.id.calc_3, "3", "3"), Triple(R.id.calc_plus, "+", "+"),
            Triple(R.id.calc_sign, "sign", "±"), Triple(R.id.calc_0, "0", "0"), Triple(R.id.calc_decimal, ".", "."), Triple(R.id.calc_equals, "equals", "="),
        )
        private val CALCULATOR_TOKENS = setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "+", "-", "*", "/")
        private val TIME_VALUE = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
    }
}

private fun Double?.orOne() = this?.takeIf { it > 0 } ?: 1.0
private fun compactHome(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else SafeExpression.format(value, 4).trimEnd('0').trimEnd('.')

private const val ACTION_WIDGET_ACTION = "app.arbor.chat.action.GENERATED_WIDGET_ACTION"
private const val ACTION_CALCULATOR = "app.arbor.chat.action.CALCULATOR_WIDGET"
private const val ACTION_REFRESH = "app.arbor.chat.action.REFRESH_WIDGET"
private const val ACTION_MINI_APP = "app.arbor.chat.action.MINI_APP_WIDGET"
private const val EXTRA_ACTION_INDEX = "action_index"
private const val EXTRA_CALCULATOR_KEY = "calculator_key"
private const val EXTRA_MINI_SCREEN = "mini_screen"
private const val EXTRA_MINI_COMPONENT = "mini_component"
private const val EXTRA_MINI_KIND = "mini_kind"
private const val EXTRA_MINI_CHILD = "mini_child"
private const val EXTRA_TOKEN = "widget_token"
private const val CALCULATOR_EXPRESSION = "_calculator_expression"
private const val CALCULATOR_ERROR = "_calculator_error"
private const val MINI_SUBMISSION = "_mini_submission"
private const val MAX_HOME_ROWS = 14
private const val MAX_HOME_CHARTS = 2
private const val MAX_WIDGET_SOURCE = 48_000
