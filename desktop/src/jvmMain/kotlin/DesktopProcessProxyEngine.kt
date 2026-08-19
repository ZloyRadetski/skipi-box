// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package desktop

import app.AppState
import app.ProxyServerState
import engine.proxy.ProxyEngineStartRequest
import engine.proxy.ProxyEngineStatus
import engine.proxy.toLocalProxyOptions
import engine.proxy.xrayStatsApiConfig
import engine.xray.XrayConfigRequest
import engine.xray.XrayConfigFactory
import engine.xray.XrayCoreLogPaths
import features.logs.AppLogger
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class DesktopProcessProxyEngine {
    private val processRef = AtomicReference<Process?>()
    private val currentStatus = AtomicReference(ProxyEngineStatus(running = false))

    fun start(request: ProxyEngineStartRequest, xrayBinaryFile: File? = null): ProxyEngineStatus {
        stop()

        val tempDir = File(System.getProperty("java.io.tmpdir"), "skipi-desktop").apply { mkdirs() }
        val configFile = File(tempDir, "config.json")
        val logPaths = XrayCoreLogPaths(
            accessLogPath = File(tempDir, "access.log").absolutePath,
            errorLogPath = File(tempDir, "error.log").absolutePath,
        )

        val socksInbound = engine.proxy.buildLocalSocksInbound(
            appState = request.appState,
            tag = engine.xray.XrayTags.LOCAL_SOCKS_INBOUND,
            options = request.appState.toLocalProxyOptions(),
        )

        val configJson = XrayConfigFactory.buildXrayConfig(
            XrayConfigRequest(
                appState = request.appState,
                selectedServer = request.selectedServer,
                inbounds = listOf(socksInbound),
                coreLogPaths = logPaths,
                statsApiConfig = request.xrayStatsApiConfig(),
            )
        )
        configFile.writeText(configJson)

        val binaryPath = xrayBinaryFile?.takeIf(File::exists)?.absolutePath ?: "xray"
        AppLogger.info(LogTag, "Starting Xray process with binary=$binaryPath config=${configFile.absolutePath}")

        return runCatching {
            val process = ProcessBuilder(binaryPath, "run", "-c", configFile.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            processRef.set(process)
            val port = request.appState.localProxyPort.toIntOrNull() ?: 10808
            DesktopSystemProxy.enable(port = port)

            val status = ProxyEngineStatus(running = true, appState = request.appState)
            currentStatus.set(status)
            status
        }.getOrElse { error ->
            AppLogger.error(LogTag, "Failed to start Xray process", error)
            ProxyEngineStatus(running = false)
        }
    }

    fun stop(): ProxyEngineStatus {
        val process = processRef.getAndSet(null)
        if (process != null) {
            runCatching { process.destroy() }
            AppLogger.info(LogTag, "Xray process stopped")
        }
        DesktopSystemProxy.disable()
        val status = ProxyEngineStatus(running = false)
        currentStatus.set(status)
        return status
    }

    fun status(): ProxyEngineStatus {
        val process = processRef.get()
        val isAlive = process?.isAlive == true
        if (!isAlive && process != null) {
            processRef.set(null)
            DesktopSystemProxy.disable()
            currentStatus.set(ProxyEngineStatus(running = false))
        }
        return currentStatus.get()
    }

    companion object {
        private const val LogTag = "DesktopProcessProxyEngine"
    }
}
