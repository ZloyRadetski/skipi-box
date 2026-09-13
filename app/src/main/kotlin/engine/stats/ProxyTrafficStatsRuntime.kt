// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.stats

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit

internal data class ProxyTrafficStatsRuntime(
    val serverName: String,
    /** Effective FINAL target frozen when the current tunnel was started. */
    val finalOutboundTag: String = "proxy",
    val selectedServerId: Int = -1,
    /** Member picked during balancer startup, before Xray reports traffic for it. */
    val startupStrategyMemberId: Int? = null,
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
        val startedAt = preferences.getLong(KeyStartedAtElapsedRealtime, 0L)
            .takeIf { value -> value > 0L }
            ?: return null
        return ProxyTrafficStatsRuntime(
            serverName = preferences.getString(KeyServerName, "").orEmpty(),
            finalOutboundTag = preferences.getString(KeyFinalOutboundTag, "proxy")
                ?.takeIf(String::isNotBlank)
                ?: "proxy",
            selectedServerId = preferences.getInt(KeySelectedServerId, -1),
            startupStrategyMemberId = preferences.getInt(KeyStartupStrategyMemberId, -1)
                .takeIf { value -> value >= 0 },
            startedAtElapsedRealtime = startedAt,
            paused = preferences.getBoolean(KeyPaused, false),
            pausedAtElapsedRealtime = preferences.getLong(KeyPausedAtElapsedRealtime, 0L),
        )
    }

    fun write(
        context: Context,
        runtime: ProxyTrafficStatsRuntime,
    ) {
        context.preferences().edit {
            // Clear the old gRPC endpoint metadata as part of the migration;
            // it is not consulted by this runtime anymore.
            remove(LegacyKeyListenAddress)
            remove(LegacyKeyPort)
            remove(LegacyKeyApiTag)
            putString(KeyServerName, runtime.serverName)
            putString(KeyFinalOutboundTag, runtime.finalOutboundTag)
            putInt(KeySelectedServerId, runtime.selectedServerId)
            runtime.startupStrategyMemberId?.let { memberId ->
                putInt(KeyStartupStrategyMemberId, memberId)
            } ?: remove(KeyStartupStrategyMemberId)
            putLong(KeyStartedAtElapsedRealtime, runtime.startedAtElapsedRealtime)
            putBoolean(KeyPaused, runtime.paused)
            putLong(KeyPausedAtElapsedRealtime, runtime.pausedAtElapsedRealtime)
        }
        CoreTrafficStatsSampler.reconcile(context, runtime)
    }

    fun clear(context: Context) {
        context.preferences().edit {
            remove(LegacyKeyListenAddress)
            remove(LegacyKeyPort)
            remove(LegacyKeyApiTag)
            remove(KeyServerName)
            remove(KeyFinalOutboundTag)
            remove(KeySelectedServerId)
            remove(KeyStartupStrategyMemberId)
            remove(KeyStartedAtElapsedRealtime)
            remove(KeyPaused)
            remove(KeyPausedAtElapsedRealtime)
        }
        CoreTrafficStatsSampler.reconcile(context, null)
    }

    private fun Context.preferences() = applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
}

private const val PreferencesName = "proxy_traffic_stats"
private const val LegacyKeyListenAddress = "listen_address"
private const val LegacyKeyPort = "port"
private const val LegacyKeyApiTag = "api_tag"
private const val KeyServerName = "server_name"
private const val KeyFinalOutboundTag = "final_outbound_tag"
private const val KeySelectedServerId = "selected_server_id"
private const val KeyStartupStrategyMemberId = "startup_strategy_member_id"
private const val KeyStartedAtElapsedRealtime = "started_at_elapsed_realtime"
private const val KeyPaused = "paused"
private const val KeyPausedAtElapsedRealtime = "paused_at_elapsed_realtime"
