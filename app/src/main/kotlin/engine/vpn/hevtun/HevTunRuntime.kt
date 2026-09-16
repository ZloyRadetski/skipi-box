// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import engine.hevtun.HevSocks5TunnelConfig
import engine.hevtun.writeConfigFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

internal class HevTunRuntime(
    private val nativeGateway: HevTunNativeGateway = HevTunNative,
    private val readinessTimeoutMillis: Long = HevTunReadinessTimeoutMillis,
    private val readinessPollIntervalMillis: Long = HevTunReadinessPollIntervalMillis,
) {
    private var nativeStartRequested = false

    init {
        require(readinessTimeoutMillis > 0L)
        require(readinessPollIntervalMillis > 0L)
    }

    suspend fun start(config: HevSocks5TunnelConfig, tunFd: Int) {
        stop()
        config.writeConfigFile()
        check(nativeGateway.startService(config.configPath, tunFd)) {
            "Failed to start Hev TUN native service"
        }
        nativeStartRequested = true
        try {
            check(
                awaitHevTunReadiness(
                    isRunning = nativeGateway::isRunning,
                    isReady = nativeGateway::isReady,
                    timeoutMillis = readinessTimeoutMillis,
                    pollIntervalMillis = readinessPollIntervalMillis,
                ),
            ) {
                "Hev TUN native service did not become ready"
            }
        } catch (error: Throwable) {
            runCatching { stop() }
            throw error
        }
    }

    fun stop() {
        if (!nativeStartRequested) return
        try {
            check(nativeGateway.stopService()) {
                "Failed to stop Hev TUN native service"
            }
        } finally {
            nativeStartRequested = false
        }
    }
}

internal const val HevTunReadinessTimeoutMillis = 5_000L
private const val HevTunReadinessPollIntervalMillis = 25L

internal suspend fun awaitHevTunReadiness(
    isRunning: () -> Boolean,
    isReady: () -> Boolean,
    timeoutMillis: Long = HevTunReadinessTimeoutMillis,
    pollIntervalMillis: Long = HevTunReadinessPollIntervalMillis,
): Boolean {
    require(timeoutMillis > 0L)
    require(pollIntervalMillis > 0L)
    return withTimeoutOrNull(timeoutMillis) {
        while (isRunning()) {
            currentCoroutineContext().ensureActive()
            if (isReady()) return@withTimeoutOrNull true
            delay(pollIntervalMillis)
        }
        false
    } ?: false
}
