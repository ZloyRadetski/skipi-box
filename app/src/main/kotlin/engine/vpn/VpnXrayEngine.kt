// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import app.modes.RunModeVpnService
import app.R
import engine.proxy.mode.AndroidModeProxyEngine
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import features.logs.AndroidAppLogger

internal class VpnXrayEngine(
    private val context: Context,
    private val requestVpnPermission: suspend (Intent) -> Boolean,
    private val runtimeRunning: () -> Boolean = SkipiVpnService::isRunning,
) : AndroidModeProxyEngine {
    override val runMode: Int = RunModeVpnService

    override suspend fun start(request: ProxyEngineStartRequest): ProxyEngineStatus {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null && !requestVpnPermission(prepareIntent)) {
            error(context.getString(R.string.error_vpn_permission_denied))
        }
        val configStartedAt = SystemClock.elapsedRealtime()
        val config = VpnXrayConfigFactory.create(context, request)
        val configReadyAt = SystemClock.elapsedRealtime()
        AndroidAppLogger.info(
            LogTag,
            "Prepared VPN tunnel config for server '${request.selectedServer.server.getInfo().remarks}' (id=${request.selectedServer.id}). Xray config:\n${config.xrayConfigJson}",
        )
        SkipiVpnService.start(
            context = context,
            config = config,
        )
        AndroidAppLogger.info(
            LogTag,
            "VPN startup timing: config=${configReadyAt - configStartedAt}ms, service=${SystemClock.elapsedRealtime() - configReadyAt}ms",
        )
        return status()
    }

    override suspend fun stop(): ProxyEngineStatus {
        SkipiVpnService.stop(context)
        return status()
    }

    override suspend fun status(): ProxyEngineStatus {
        return ProxyEngineStatus(
            running = runtimeRunning(),
            runMode = runMode,
        )
    }

    private companion object {
        private const val LogTag = "VpnXrayEngine"
    }
}
