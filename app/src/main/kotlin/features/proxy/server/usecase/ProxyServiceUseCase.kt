// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import app.AppState
import app.ProxyServerState
import engine.proxy.AndroidProxyEngine
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import kotlin.coroutines.cancellation.CancellationException

/** Start and stop the single VPN runtime used by SKIPI. */
internal class ProxyServiceUseCase(
    private val proxyEngine: AndroidProxyEngine,
) {
    suspend fun toggle(state: AppState, selectedServer: ProxyServerState?): ProxyServiceResult {
        val running = runCatching { proxyEngine.status(appState = state).running }
            .getOrElse { return ProxyServiceResult.Failed(it) }
        return if (running) stop(state.runMode) else start(state, selectedServer)
    }

    suspend fun restart(state: AppState, selectedServer: ProxyServerState?): ProxyServiceResult {
        val server = selectedServer ?: return ProxyServiceResult.MissingServer
        return runCatching { proxyEngine.restart(ProxyEngineStartRequest(state, server)) }.toResult()
    }

    private suspend fun start(state: AppState, selectedServer: ProxyServerState?): ProxyServiceResult {
        val server = selectedServer ?: return ProxyServiceResult.MissingServer
        return runCatching { proxyEngine.start(ProxyEngineStartRequest(state, server)) }.toResult()
    }

    suspend fun stop(runMode: Int): ProxyServiceResult = runCatching { proxyEngine.stop(runMode) }.toResult()

    suspend fun shutdown(runMode: Int): ProxyServiceResult = stop(runMode)

    private fun Result<ProxyEngineStatus>.toResult(): ProxyServiceResult = fold(
        onSuccess = { status -> ProxyServiceResult.Success(status.running, status.appState) },
        onFailure = { error ->
            if (error is CancellationException) throw error
            ProxyServiceResult.Failed(error)
        },
    )
}

internal sealed interface ProxyServiceResult {
    data class Success(val proxyRunning: Boolean, val appState: AppState? = null) : ProxyServiceResult
    data object MissingServer : ProxyServiceResult
    data class Failed(val error: Throwable) : ProxyServiceResult
}
