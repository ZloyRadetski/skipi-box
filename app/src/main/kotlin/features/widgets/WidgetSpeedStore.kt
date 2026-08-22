// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.widgets

import android.content.Context
import androidx.core.content.edit

/** Last traffic sample pushed into the home screen widgets by [ProxyWidgetRuntime]. */
internal data class WidgetSpeedSample(
    val uplinkBytesPerSecond: Long = 0L,
    val downlinkBytesPerSecond: Long = 0L,
    val totalUplinkBytes: Long = 0L,
    val totalDownlinkBytes: Long = 0L,
    /** [android.os.SystemClock.elapsedRealtime] timestamp used to drop stale samples. */
    val updatedAtElapsedRealtime: Long = 0L,
)

/**
 * Tiny SharedPreferences-backed bridge between the traffic poller living in the
 * application process and short-lived widget renders that happen after process death.
 */
internal object WidgetSpeedStore {
    fun read(context: Context): WidgetSpeedSample? {
        val preferences = preferences(context)
        val timestamp = preferences.getLong(KeyUpdatedAt, 0L).takeIf { value -> value > 0L } ?: return null
        return WidgetSpeedSample(
            uplinkBytesPerSecond = preferences.getLong(KeyUplinkSpeed, 0L),
            downlinkBytesPerSecond = preferences.getLong(KeyDownlinkSpeed, 0L),
            totalUplinkBytes = preferences.getLong(KeyTotalUplink, 0L),
            totalDownlinkBytes = preferences.getLong(KeyTotalDownlink, 0L),
            updatedAtElapsedRealtime = timestamp,
        )
    }

    fun write(
        context: Context,
        sample: WidgetSpeedSample,
    ) {
        preferences(context).edit {
            putLong(KeyUplinkSpeed, sample.uplinkBytesPerSecond)
            putLong(KeyDownlinkSpeed, sample.downlinkBytesPerSecond)
            putLong(KeyTotalUplink, sample.totalUplinkBytes)
            putLong(KeyTotalDownlink, sample.totalDownlinkBytes)
            putLong(KeyUpdatedAt, sample.updatedAtElapsedRealtime)
        }
    }

    fun clear(context: Context) {
        preferences(context).edit {
            remove(KeyUplinkSpeed)
            remove(KeyDownlinkSpeed)
            remove(KeyTotalUplink)
            remove(KeyTotalDownlink)
            remove(KeyUpdatedAt)
        }
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
}

private const val PreferencesName = "proxy_widget_speed"
private const val KeyUplinkSpeed = "uplink_bytes_per_second"
private const val KeyDownlinkSpeed = "downlink_bytes_per_second"
private const val KeyTotalUplink = "total_uplink_bytes"
private const val KeyTotalDownlink = "total_downlink_bytes"
private const val KeyUpdatedAt = "updated_at_elapsed_realtime"
