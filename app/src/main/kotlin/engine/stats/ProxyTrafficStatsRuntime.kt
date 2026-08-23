// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import engine.xray.XrayStatsApiTag

internal data class ProxyTrafficStatsRuntime(
    val listenAddress: String,
    val port: Int,
    val serverName: String,
    val apiTag: String = XrayStatsApiTag,
    /** Effective FINAL target frozen when the current tunnel was started. */
    val finalOutboundTag: String = "proxy",
    val selectedServerId: Int = -1,
    /** Keeps the notification's connection timer stable while the app process lives. */
    val startedAtElapsedRealtime: Long = SystemClock.elapsedRealtime(),
    /** A paused runtime keeps the notification visible but has no active VPN tunnel. */
    val paused: Boolean = false,
    /** Freezes the connection duration displayed by a paused notification. */
    val pausedAtElapsedRealtime: Long = 0L,
)

internal object ProxyTrafficStatsRuntimeStore {
    fun read(context: Context): ProxyTrafficStatsRuntime? {
        val preferences = context.preferences()
        val port = preferences.getInt(KeyPort, 0).takeIf { value -> value > 0 } ?: return null
        return ProxyTrafficStatsRuntime(
            listenAddress = preferences.getString(KeyListenAddress, XrayStatsApiListenAddress)
                ?.takeIf(String::isNotBlank)
                ?: XrayStatsApiListenAddress,
            port = port,
            serverName = preferences.getString(KeyServerName, "").orEmpty(),
            apiTag = preferences.getString(KeyApiTag, XrayStatsApiTag)
                ?.takeIf(String::isNotBlank)
                ?: XrayStatsApiTag,
            finalOutboundTag = preferences.getString(KeyFinalOutboundTag, "proxy")
                ?.takeIf(String::isNotBlank)
                ?: "proxy",
            selectedServerId = preferences.getInt(KeySelectedServerId, -1),
            startedAtElapsedRealtime = preferences.getLong(KeyStartedAtElapsedRealtime, 0L)
                .takeIf { value -> value > 0L }
                ?: SystemClock.elapsedRealtime(),
            paused = preferences.getBoolean(KeyPaused, false),
            pausedAtElapsedRealtime = preferences.getLong(KeyPausedAtElapsedRealtime, 0L),
        )
    }

    fun readPort(context: Context): Int? {
        return context.preferences().getInt(KeyPort, 0).takeIf { value -> value > 0 }
    }

    fun write(
        context: Context,
        runtime: ProxyTrafficStatsRuntime,
    ) {
        context.preferences().edit {
            putString(KeyListenAddress, runtime.listenAddress)
            putInt(KeyPort, runtime.port)
            putString(KeyServerName, runtime.serverName)
            putString(KeyApiTag, runtime.apiTag)
            putString(KeyFinalOutboundTag, runtime.finalOutboundTag)
            putInt(KeySelectedServerId, runtime.selectedServerId)
            putLong(KeyStartedAtElapsedRealtime, runtime.startedAtElapsedRealtime)
            putBoolean(KeyPaused, runtime.paused)
            putLong(KeyPausedAtElapsedRealtime, runtime.pausedAtElapsedRealtime)
        }
    }

    fun clear(context: Context) {
        context.preferences().edit {
            remove(KeyListenAddress)
            remove(KeyPort)
            remove(KeyServerName)
            remove(KeyApiTag)
            remove(KeyFinalOutboundTag)
            remove(KeySelectedServerId)
            remove(KeyStartedAtElapsedRealtime)
            remove(KeyPaused)
            remove(KeyPausedAtElapsedRealtime)
        }
    }

    private fun Context.preferences() = applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
}

private const val PreferencesName = "proxy_traffic_stats"
private const val KeyListenAddress = "listen_address"
private const val KeyPort = "port"
private const val KeyServerName = "server_name"
private const val KeyApiTag = "api_tag"
private const val KeyFinalOutboundTag = "final_outbound_tag"
private const val KeySelectedServerId = "selected_server_id"
private const val KeyStartedAtElapsedRealtime = "started_at_elapsed_realtime"
private const val KeyPaused = "paused"
private const val KeyPausedAtElapsedRealtime = "paused_at_elapsed_realtime"
