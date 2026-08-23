// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.content.Intent
import java.util.concurrent.ConcurrentHashMap

/**
 * A generated Xray configuration may contain every member of a large
 * balancer. Passing it through Intent extras duplicates that payload in a
 * Binder transaction, which delays (or can reject) the VPN service start.
 *
 * The service lives in the application process and is explicitly
 * START_NOT_STICKY, so a short-lived in-memory hand-off is both sufficient and
 * safer: a process restart must build a new configuration anyway.
 */
internal object VpnServiceStartConfigStore {
    private val configs = ConcurrentHashMap<Long, VpnServiceStartConfig>()

    fun put(operationId: Long, config: VpnServiceStartConfig) {
        configs[operationId] = config
    }

    fun take(operationId: Long): VpnServiceStartConfig? = configs.remove(operationId)

    fun remove(operationId: Long) {
        configs.remove(operationId)
    }
}

internal object SkipiVpnServiceIntents {
    const val ACTION_START = "app.action.START_VPN"
    const val ACTION_STOP = "app.action.STOP_VPN"

    fun startIntent(
        context: Context,
        config: VpnServiceStartConfig,
        operationId: Long,
    ): Intent {
        require(operationId > 0L)
        VpnServiceStartConfigStore.put(operationId, config)
        return Intent(context, SkipiVpnService::class.java).apply {
            action = ACTION_START
            putExtra(ExtraOperationId, operationId)
        }
    }

    fun stopIntent(
        context: Context,
        operationId: Long,
        keepForegroundNotification: Boolean,
    ): Intent {
        require(operationId > 0L)
        return Intent(context, SkipiVpnService::class.java).apply {
            action = ACTION_STOP
            putExtra(ExtraOperationId, operationId)
            putExtra(ExtraKeepForegroundNotification, keepForegroundNotification)
        }
    }
}

internal fun Intent.vpnServiceOperationId(): Long? {
    return getLongExtra(ExtraOperationId, InvalidOperationId)
        .takeIf { operationId -> operationId > 0L }
}

internal fun Intent.keepVpnForegroundNotification(): Boolean {
    return getBooleanExtra(ExtraKeepForegroundNotification, false)
}

private const val ExtraOperationId = "operation_id"
private const val ExtraKeepForegroundNotification = "keep_foreground_notification"
private const val InvalidOperationId = -1L
