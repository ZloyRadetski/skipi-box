// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import app.AppState
import app.MainActivity
import app.R
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.stats.toTrafficSizeString
import features.logs.AndroidAppLogger
import features.proxy.server.display.displayName

/** Builds and pushes RemoteViews for every placed SKIPI home screen widget. */
internal object SkipiWidgetRenderer {
    /**
     * Re-renders all widgets from the persisted app state. Safe to call after
     * process death: the running flag is re-synced against the live proxy engine.
     */
    suspend fun renderAll(
        context: Context,
        processing: Boolean = false,
    ) {
        val appContext = context.applicationContext
        val state = AndroidAppStateStore.get(appContext).state.value
        val running =
            if (processing) {
                state.proxyRunning
            } else {
                resolveRunningState(appContext, state)
            }
        pushWidgets(
            context = appContext,
            content = widgetContent(
                state = state,
                running = running,
                processing = processing,
                speedSample = WidgetSpeedStore.read(appContext).takeIf { running },
            ),
        )
    }

    /** Lightweight update used by the traffic poller; rebuilds all views to keep click targets intact. */
    fun updateTraffic(
        context: Context,
        sample: WidgetSpeedSample,
    ) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) return
        val state = AndroidAppStateStore.get(appContext).state.value
        if (!state.proxyRunning) return
        pushWidgets(
            context = appContext,
            content = widgetContent(
                state = state,
                running = true,
                processing = false,
                speedSample = sample,
            ),
        )
    }

    private fun widgetContent(
        state: AppState,
        running: Boolean,
        processing: Boolean,
        speedSample: WidgetSpeedSample?,
    ): WidgetContent {
        return WidgetContent(
            running = running,
            processing = processing,
            serverName = selectedServerName(state),
            configName = selectedConfigName(state),
            speedSample = speedSample,
            configCount = state.widgetConfigIds().size,
            serverCount = state.widgetServerIds().size,
        )
    }

    private suspend fun resolveRunningState(
        appContext: Context,
        state: AppState,
    ): Boolean {
        return runCatching {
            AndroidProxyEngine(appContext, requestVpnPermission = { false })
                .status(appState = state)
                .running
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to read proxy status for widget render", error)
        }.getOrElse { state.proxyRunning }
    }

    private fun pushWidgets(
        context: Context,
        content: WidgetContent,
    ) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        WidgetProviders.forEach { provider ->
            val component = ComponentName(context, provider.providerClass)
            val appWidgetIds = manager.getAppWidgetIds(component)
            if (appWidgetIds.isEmpty()) return@forEach
            val views =
                if (provider.supportsSelection) {
                    createControlPanelViews(context, provider, content)
                } else {
                    createCompactViews(context, provider, content)
                }
            manager.updateAppWidget(appWidgetIds, views)
        }
    }

    private fun createCompactViews(
        context: Context,
        provider: WidgetProviderSpec,
        content: WidgetContent,
    ): RemoteViews {
        return RemoteViews(context.packageName, provider.layoutResId).apply {
            applyStatus(
                context = context,
                content = content,
                statusTextViewId = R.id.widget_status_text,
                statusDotViewId = R.id.widget_status_dot,
                toggleViewId = R.id.widget_toggle_button,
            )
            setTextViewText(
                R.id.widget_server_text,
                content.serverName ?: context.getString(R.string.app_name),
            )
            setTextViewText(R.id.widget_speed_text, formatSpeed(context, content.speedSample))
            setOnClickPendingIntent(
                R.id.widget_toggle_button,
                togglePendingIntent(context, provider.providerClass),
            )
            setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        }
    }

    private fun createControlPanelViews(
        context: Context,
        provider: WidgetProviderSpec,
        content: WidgetContent,
    ): RemoteViews {
        return RemoteViews(context.packageName, provider.layoutResId).apply {
            applyStatus(
                context = context,
                content = content,
                statusTextViewId = R.id.widget_control_status_text,
                statusDotViewId = R.id.widget_control_status_dot,
                toggleViewId = R.id.widget_control_toggle_button,
            )
            setTextViewText(
                R.id.widget_control_speed_text,
                formatSpeed(context, content.speedSample).ifBlank {
                    context.getString(R.string.widget_speed_idle)
                },
            )
            setTextViewText(
                R.id.widget_control_config_text,
                content.configName ?: context.getString(R.string.widget_no_config),
            )
            setTextViewText(
                R.id.widget_control_server_text,
                content.serverName ?: context.getString(R.string.proxy_server_list_select_first),
            )
            setOnClickPendingIntent(
                R.id.widget_control_toggle_button,
                togglePendingIntent(context, provider.providerClass),
            )
            setCycleControls(
                views = this,
                context = context,
                providerClass = provider.providerClass,
                optionCount = content.configCount,
                previousButtonId = R.id.widget_previous_config_button,
                nextButtonId = R.id.widget_next_config_button,
                previousAction = SkipiWidgetProvider.ActionPreviousConfig,
                nextAction = SkipiWidgetProvider.ActionNextConfig,
                previousRequestCode = PreviousConfigRequestCode,
                nextRequestCode = NextConfigRequestCode,
            )
            setCycleControls(
                views = this,
                context = context,
                providerClass = provider.providerClass,
                optionCount = content.serverCount,
                previousButtonId = R.id.widget_previous_server_button,
                nextButtonId = R.id.widget_next_server_button,
                previousAction = SkipiWidgetProvider.ActionPreviousServer,
                nextAction = SkipiWidgetProvider.ActionNextServer,
                previousRequestCode = PreviousServerRequestCode,
                nextRequestCode = NextServerRequestCode,
            )
            setOnClickPendingIntent(R.id.widget_control_root, openAppPendingIntent(context))
        }
    }

    private fun RemoteViews.applyStatus(
        context: Context,
        content: WidgetContent,
        statusTextViewId: Int,
        statusDotViewId: Int,
        toggleViewId: Int,
    ) {
        val color = statusColor(content)
        setTextViewText(statusTextViewId, statusText(context, content))
        setTextColor(statusTextViewId, color)
        setInt(statusDotViewId, SetColorFilterMethod, color)
        setInt(toggleViewId, SetColorFilterMethod, color)
    }

    private fun setCycleControls(
        views: RemoteViews,
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        optionCount: Int,
        previousButtonId: Int,
        nextButtonId: Int,
        previousAction: String,
        nextAction: String,
        previousRequestCode: Int,
        nextRequestCode: Int,
    ) {
        val enabled = optionCount > 1
        val visibility = if (enabled) View.VISIBLE else View.GONE
        views.setViewVisibility(previousButtonId, visibility)
        views.setViewVisibility(nextButtonId, visibility)
        if (!enabled) return
        views.setInt(previousButtonId, SetColorFilterMethod, ControlIconColor)
        views.setInt(nextButtonId, SetColorFilterMethod, ControlIconColor)
        views.setOnClickPendingIntent(
            previousButtonId,
            actionPendingIntent(context, providerClass, previousAction, previousRequestCode),
        )
        views.setOnClickPendingIntent(
            nextButtonId,
            actionPendingIntent(context, providerClass, nextAction, nextRequestCode),
        )
    }

    private fun statusText(
        context: Context,
        content: WidgetContent,
    ): String {
        return when {
            content.processing -> context.getString(R.string.quick_settings_tile_processing)
            content.running -> context.getString(R.string.quick_settings_tile_running)
            else -> context.getString(R.string.quick_settings_tile_stopped)
        }
    }

    private fun statusColor(content: WidgetContent): Int {
        return when {
            content.processing -> ToggleProcessingColor
            content.running -> ToggleActiveColor
            else -> ToggleIdleColor
        }
    }

    private fun formatSpeed(
        context: Context,
        sample: WidgetSpeedSample?,
    ): String {
        val isFresh =
            sample != null &&
                SystemClock.elapsedRealtime() - sample.updatedAtElapsedRealtime <= SpeedFreshWindowMillis
        if (!isFresh) return ""
        return context.getString(
            R.string.widget_speed_format,
            sample.uplinkBytesPerSecond.toTrafficSizeString(),
            sample.downlinkBytesPerSecond.toTrafficSizeString(),
        )
    }

    private fun selectedServerName(state: AppState): String? {
        return state.proxyServers
            .firstOrNull { server -> server.id == state.selectedProxyServerId }
            ?.displayName()
    }

    private fun selectedConfigName(state: AppState): String? {
        return state.trafficConfigs
            .firstOrNull { config -> config.id == state.activeTrafficConfigId }
            ?.name
            ?.takeIf(String::isNotBlank)
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        return WidgetProviders.any { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider.providerClass)).isNotEmpty()
        }
    }

    private fun togglePendingIntent(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
    ): PendingIntent {
        return actionPendingIntent(
            context = context,
            providerClass = providerClass,
            action = SkipiWidgetProvider.ActionToggle,
            requestCode = ToggleRequestCode,
        )
    }

    private fun actionPendingIntent(
        context: Context,
        providerClass: Class<out AppWidgetProvider>,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, providerClass).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            OpenAppRequestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private data class WidgetContent(
        val running: Boolean,
        val processing: Boolean,
        val serverName: String?,
        val configName: String?,
        val speedSample: WidgetSpeedSample?,
        val configCount: Int,
        val serverCount: Int,
    )

    private data class WidgetProviderSpec(
        val providerClass: Class<out AppWidgetProvider>,
        val layoutResId: Int,
        val supportsSelection: Boolean,
    )

    private val WidgetProviders = listOf(
        WidgetProviderSpec(
            providerClass = SkipiWidgetProvider::class.java,
            layoutResId = R.layout.widget_skipi,
            supportsSelection = false,
        ),
        WidgetProviderSpec(
            providerClass = SkipiControlWidgetProvider::class.java,
            layoutResId = R.layout.widget_skipi_control,
            supportsSelection = true,
        ),
    )

    private const val ToggleRequestCode = 4001
    private const val OpenAppRequestCode = 4002
    private const val PreviousConfigRequestCode = 4003
    private const val NextConfigRequestCode = 4004
    private const val PreviousServerRequestCode = 4005
    private const val NextServerRequestCode = 4006
    private const val SpeedFreshWindowMillis = 15_000L
    private const val SetColorFilterMethod = "setColorFilter"
    private const val LogTag = "SkipiWidgetRenderer"

    private const val ToggleActiveColor = 0xFF65E28E.toInt()
    private const val ToggleIdleColor = 0xFFC7CCD6.toInt()
    private const val ToggleProcessingColor = 0xFF8AB4F8.toInt()
    private const val ControlIconColor = 0xFFE7EBF3.toInt()
}
