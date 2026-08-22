// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import app.MainActivity
import app.AppState
import app.R
import data.AndroidAppStateStore
import engine.proxy.AndroidProxyEngine
import engine.stats.toTrafficSizeString
import features.logs.AndroidAppLogger

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
            running = running,
            processing = processing,
            serverName = selectedServerRemarks(state),
            speedSample = WidgetSpeedStore.read(appContext).takeIf { running },
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
            running = true,
            processing = false,
            serverName = selectedServerRemarks(state),
            speedSample = sample,
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
        running: Boolean,
        processing: Boolean,
        serverName: String?,
        speedSample: WidgetSpeedSample?,
    ) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val component = ComponentName(context, SkipiWidgetProvider::class.java)
        if (manager.getAppWidgetIds(component).isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.widget_skipi)
        views.setTextViewText(
            R.id.widget_status_text,
            when {
                processing -> context.getString(R.string.quick_settings_tile_processing)
                running -> context.getString(R.string.quick_settings_tile_running)
                else -> context.getString(R.string.quick_settings_tile_stopped)
            },
        )
        views.setTextViewText(
            R.id.widget_server_text,
            serverName?.takeIf(String::isNotBlank) ?: context.getString(R.string.app_name),
        )
        views.setTextViewText(R.id.widget_speed_text, formatSpeed(context, speedSample))
        views.setInt(
            R.id.widget_toggle_button,
            SetColorFilterMethod,
            if (running) ToggleActiveColor else ToggleIdleColor,
        )
        views.setOnClickPendingIntent(R.id.widget_toggle_button, togglePendingIntent(context))
        views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
        manager.updateAppWidget(component, views)
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

    private fun selectedServerRemarks(state: AppState): String? {
        return state.proxyServers
            .firstOrNull { server -> server.id == state.selectedProxyServerId }
            ?.server
            ?.getInfo()
            ?.remarks
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context) ?: return false
        val component = ComponentName(context, SkipiWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private fun togglePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SkipiWidgetProvider::class.java).setAction(SkipiWidgetProvider.ActionToggle)
        return PendingIntent.getBroadcast(
            context,
            ToggleRequestCode,
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

    private const val ToggleRequestCode = 4001
    private const val OpenAppRequestCode = 4002
    private const val SpeedFreshWindowMillis = 15_000L
    private const val SetColorFilterMethod = "setColorFilter"
    private const val LogTag = "SkipiWidgetRenderer"

    private const val ToggleActiveColor = 0xFF34A853.toInt()
    private const val ToggleIdleColor = 0xFF9AA0A6.toInt()
}

