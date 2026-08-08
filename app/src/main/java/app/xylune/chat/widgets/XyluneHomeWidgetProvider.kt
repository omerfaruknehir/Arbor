package app.xylune.chat.widgets

import app.xylune.chat.settings.withStoredXyluneLanguage

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.xylune.chat.MainActivity
import app.xylune.chat.R
import java.text.DateFormat
import java.util.Date
import java.util.UUID

enum class WidgetPinResult { REQUESTED, UNSUPPORTED, INVALID }

object WidgetPinning {
    @Suppress("UnspecifiedImmutableFlag")
    fun request(context: Context, source: String, grants: WidgetCapabilityGrants): WidgetPinResult {
        val definition = XyluneProgramParser.parse(source, XyluneProgramSurface.WIDGET).getOrNull() ?: return WidgetPinResult.INVALID
        if (!grantsSatisfy(definition, grants)) return WidgetPinResult.INVALID
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return WidgetPinResult.UNSUPPORTED
        val token = UUID.randomUUID().toString()
        WidgetStorage(context).savePending(token, source, grants)
        val callbackIntent = Intent(context, WidgetPinReceiver::class.java).putExtra(EXTRA_TOKEN, token)
        val callback = PendingIntent.getBroadcast(
            context,
            token.hashCode(),
            callbackIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val preview = XyluneHomeWidgetProvider.views(context, AppWidgetManager.INVALID_APPWIDGET_ID, source, grants, preview = true)
        val extras = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview) }
        return if (manager.requestPinAppWidget(ComponentName(context, XyluneHomeWidgetProvider::class.java), extras, callback)) {
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
        val pending = storage.takePending(token) ?: return
        val definition = XyluneProgramParser.parse(pending.source, XyluneProgramSurface.WIDGET).getOrNull() ?: return
        if (!grantsSatisfy(definition, pending.grants)) return
        storage.save(id, pending.source, pending.grants)
        storage.initializeState(id, definition.state)
        WidgetRefreshScheduler.sync(context, id, definition, pending.grants)
        AppWidgetManager.getInstance(context).updateAppWidget(id, XyluneHomeWidgetProvider.views(context, id, pending.source, pending.grants))
        if (definition.dataSources.isNotEmpty()) WidgetRefreshScheduler.refreshNow(context, id)
    }
}

class XyluneHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val storage = WidgetStorage(context)
        appWidgetIds.forEach { id ->
            val source = storage.source(id)
            val grants = storage.grants(id)
            val definition = source?.let { XyluneProgramParser.parse(it, XyluneProgramSurface.WIDGET).getOrNull() }
            if (definition != null && grants != null) {
                storage.initializeState(id, definition.state)
                WidgetRefreshScheduler.sync(context, id, definition, grants)
            }
            manager.updateAppWidget(id, views(context, id, source, grants))
            if (definition?.dataSources?.isNotEmpty() == true && storage.updatedAt(id) == 0L) {
                WidgetRefreshScheduler.refreshNow(context, id)
            }
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val storage = WidgetStorage(context)
        manager.updateAppWidget(appWidgetId, views(context, appWidgetId, storage.source(appWidgetId), storage.grants(appWidgetId)))
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
        if (intent.action != ACTION_PROGRAM_ACTION && intent.action != ACTION_REFRESH) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val storage = WidgetStorage(context)
        val source = storage.source(id) ?: return
        val grants = storage.grants(id) ?: return
        val definition = XyluneProgramParser.parse(source, XyluneProgramSurface.WIDGET).getOrNull() ?: return
        if (intent.action == ACTION_REFRESH) {
            WidgetRefreshScheduler.refreshNow(context, id)
            return
        }
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID).orEmpty()
        if (actionId !in definition.actions) return
        val transition = XyluneProgramRuntime.apply(actionId, definition, storage.state(id).ifEmpty { definition.state })
        storage.setState(id, transition.state)
        transition.folderWrites.forEach { write ->
            val target = definition.dataSources.firstOrNull { it.id == write.source }
            runCatching {
                requireNotNull(target) { "Folder data source no longer exists" }
                WidgetDataRuntime.writeFolder(context, target, grants, write.content)
            }.onFailure { storage.setError(id, it.message ?: "Folder write failed") }
        }
        if (transition.refreshSources.isNotEmpty()) WidgetRefreshScheduler.refreshNow(context, id, transition.refreshSources)
        transition.openRoute?.let { route ->
            runCatching {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(EXTRA_WIDGET_ROUTE, route)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
        }
        AppWidgetManager.getInstance(context).updateAppWidget(id, views(context, id, source, grants))
    }

    companion object {
        internal fun views(
            context: Context,
            id: Int,
            source: String?,
            grants: WidgetCapabilityGrants?,
            preview: Boolean = false,
        ): RemoteViews {
            val definition = source?.let { XyluneProgramParser.parse(it, XyluneProgramSurface.WIDGET).getOrNull() }
            if (definition == null || grants == null) return emptyViews(context)
            val storage = WidgetStorage(context)
            val state = if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) definition.state else storage.state(id).ifEmpty { definition.state }
            val options = if (id == AppWidgetManager.INVALID_APPWIDGET_ID) Bundle.EMPTY else AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (preview) 320 else 300).coerceIn(180, 600)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, if (preview) 240 else 180).coerceIn(100, 500)
            val compact = heightDp < 220
            val showSubtitle = definition.description.isNotBlank() && heightDp >= 240
            val showStatus = definition.dataSources.isNotEmpty() && heightDp >= 190
            val actionLimit = when {
                widthDp < 260 -> 2
                widthDp < 360 -> 3
                else -> 4
            }
            val actions = visibleActions(definition, state).take(actionLimit)
            val showActions = actions.isNotEmpty() && heightDp >= 150
            val chromeDp = 20 + 34 + 4 +
                (if (showSubtitle) 34 else 0) +
                (if (showStatus) 20 else 0) +
                (if (showActions) 38 else 0)
            val canvasHeightDp = (heightDp - chromeDp).coerceAtLeast(56)
            val metrics = context.resources.displayMetrics
            val bitmap = WidgetCanvasRenderer.render(
                definition = definition,
                state = state,
                widthPx = (widthDp * metrics.density).toInt().coerceAtLeast(240),
                heightPx = (canvasHeightDp * metrics.density).toInt().coerceAtLeast(120),
                dark = isDark(context),
                suppressActionControls = true,
                density = metrics.density,
                scaledDensity = metrics.scaledDensity,
            )
            return RemoteViews(context.packageName, R.layout.xylune_home_widget_program).apply {
                setTextViewText(R.id.widget_title, definition.title)
                setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP, if (compact) 16f else 18f)
                setTextViewText(R.id.widget_subtitle, definition.description)
                setTextViewTextSize(R.id.widget_subtitle, TypedValue.COMPLEX_UNIT_SP, 13f)
                setViewVisibility(R.id.widget_subtitle, if (showSubtitle) View.VISIBLE else View.GONE)
                setImageViewBitmap(R.id.widget_program_image, bitmap)
                val error = if (preview) null else storage.error(id)?.takeIf(String::isNotBlank)
                val status = when {
                    preview -> "Preview · configured"
                    error != null -> "Needs attention · ${error.take(80)}"
                    storage.updatedAt(id) > 0L -> "Live · updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(storage.updatedAt(id)))}"
                    definition.dataSources.isNotEmpty() -> "Live · waiting for first refresh"
                    else -> "Ready · works offline"
                }
                setTextViewText(R.id.widget_status, status)
                setTextViewTextSize(R.id.widget_status, TypedValue.COMPLEX_UNIT_SP, 12f)
                setViewVisibility(R.id.widget_status, if (showStatus) View.VISIBLE else View.GONE)
                bindOpen(context, this, id, preview)

                val hasRefresh = definition.dataSources.isNotEmpty()
                setViewVisibility(R.id.widget_refresh, if (hasRefresh) View.VISIBLE else View.GONE)
                if (hasRefresh && !preview && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val refreshIntent = Intent(context, XyluneHomeWidgetProvider::class.java)
                        .setAction(ACTION_REFRESH)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    setOnClickPendingIntent(
                        R.id.widget_refresh,
                        PendingIntent.getBroadcast(
                            context,
                            41_000 + id,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }

                setViewVisibility(R.id.widget_actions, if (showActions) View.VISIBLE else View.GONE)
                ACTION_IDS.forEachIndexed { index, viewId ->
                    val action = actions.getOrNull(index)
                    setViewVisibility(viewId, if (action == null) View.GONE else View.VISIBLE)
                    if (action != null) {
                        setTextViewText(viewId, action.label)
                        setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, 12f)
                        if (!preview && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            val intent = Intent(context, XyluneHomeWidgetProvider::class.java)
                                .setAction(ACTION_PROGRAM_ACTION)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .putExtra(EXTRA_ACTION_ID, action.id)
                            setOnClickPendingIntent(
                                viewId,
                                PendingIntent.getBroadcast(
                                    context,
                                    31_000 + id * 17 + index,
                                    intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                                ),
                            )
                        }
                    }
                }
            }
        }

        private fun emptyViews(context: Context) = RemoteViews(context.packageName, R.layout.xylune_home_widget_program).apply {
            setTextViewText(R.id.widget_title, context.withStoredXyluneLanguage().getString(R.string.app_name))
            setTextViewText(R.id.widget_subtitle, "Add an xylune-widget/1 program from a conversation")
            setTextViewText(R.id.widget_status, "Create and add a configured widget from a conversation")
            setImageViewResource(R.id.widget_program_image, R.drawable.ic_xylune_mark)
            setViewVisibility(R.id.widget_refresh, View.GONE)
            setViewVisibility(R.id.widget_actions, View.GONE)
            ACTION_IDS.forEach { setViewVisibility(it, View.GONE) }
        }

        private fun bindOpen(context: Context, views: RemoteViews, id: Int, preview: Boolean) {
            if (preview || id == AppWidgetManager.INVALID_APPWIDGET_ID) return
            val intent = Intent(context, MainActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(
                context,
                21_000 + id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            views.setOnClickPendingIntent(R.id.widget_program_image, pending)
            views.setOnClickPendingIntent(R.id.widget_title, pending)
            views.setOnClickPendingIntent(R.id.widget_subtitle, pending)
            views.setOnClickPendingIntent(R.id.widget_status, pending)
        }

        private fun visibleActions(definition: XyluneProgramDefinition, state: Map<String, String>): List<WidgetVisibleAction> {
            val values = mutableListOf<WidgetVisibleAction>()
            fun walk(node: XyluneProgramNode) {
                if (!XyluneProgramRuntime.visible(node.visibleWhen, state)) return
                if (node.type == "button" && node.action.isNotBlank()) {
                    values += WidgetVisibleAction(XyluneProgramRuntime.render(node.label, state).take(32), node.action)
                }
                if (node.type == "input" && node.action.isNotBlank()) {
                    values += WidgetVisibleAction(XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state).take(32), node.action)
                }
                if (node.type == "toggle" && node.action.isNotBlank()) {
                    val label = XyluneProgramRuntime.render(node.label.ifBlank { node.value }, state)
                    val stateLabel = if (XyluneProgramRuntime.truthy(state[node.value])) "On" else "Off"
                    values += WidgetVisibleAction("$label · $stateLabel".take(32), node.action)
                }
                if (node.type == "choice") {
                    node.options.filter { it.action.isNotBlank() }.forEach { option ->
                        values += WidgetVisibleAction(XyluneProgramRuntime.render(option.label, state).take(32), option.action)
                    }
                }
                if (node.type == "list") {
                    node.items.filter { it.action.isNotBlank() }.forEach { item ->
                        values += WidgetVisibleAction(XyluneProgramRuntime.render(item.label, state).take(32), item.action)
                    }
                }
                node.children.forEach(::walk)
            }
            walk(definition.ui)
            return values.distinctBy { it.id }.take(ACTION_IDS.size)
        }

        private fun isDark(context: Context): Boolean =
            context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

        private val ACTION_IDS = listOf(R.id.widget_action_1, R.id.widget_action_2, R.id.widget_action_3, R.id.widget_action_4)
    }
}

private data class WidgetVisibleAction(val label: String, val id: String)

internal fun grantsSatisfy(definition: XyluneProgramDefinition, grants: WidgetCapabilityGrants): Boolean =
    definition.capabilities.all { capability ->
        when (capability.type) {
            "network" -> capability.origins.all { it in grants.networkOrigins }
            "location" -> when (capability.accuracy) {
                "precise" -> grants.location == WidgetLocationGrant.PRECISE
                else -> grants.location != WidgetLocationGrant.NONE
            }
            "folder" -> grants.folderUri != null && (capability.mode != "read_write" || grants.folderWrite)
            "background_refresh" -> grants.backgroundRefresh
            else -> false
        }
    }

private const val EXTRA_TOKEN = "xylune_widget_token_v2"
private const val EXTRA_ACTION_ID = "xylune_widget_action_id"
internal const val EXTRA_WIDGET_ROUTE = "xylune_widget_route"
private const val ACTION_PROGRAM_ACTION = "app.xylune.chat.widget.PROGRAM_ACTION_V2"
private const val ACTION_REFRESH = "app.xylune.chat.widget.REFRESH_V2"
