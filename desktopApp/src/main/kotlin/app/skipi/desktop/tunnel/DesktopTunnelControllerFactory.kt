// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_select_server_before_connect
import app.skipi.ui.resources.desktop_system_proxy_unsupported_platform
import platform.LocalProxyXrayConfigOptions

/** Wires the Desktop tunnel controller to Core, the system proxy and the latest user data. */
internal fun createDesktopTunnelController(
    coreController: DesktopCoreController,
    systemProxyManager: DesktopSystemProxyManager,
    localProxyRuntime: DesktopLocalProxyRuntime,
    localProxyReadiness: DesktopLocalProxyReadiness,
    resourceFiles: DesktopProfileResourceFiles,
    coreLogs: DesktopCoreLogs,
    readSettings: () -> DesktopAppSettings,
    readServers: () -> DesktopServerLibrary,
    readConfigs: () -> DesktopConfigLibrary,
    readLatency: () -> Map<Int, DesktopServerLatencyResult>,
    tunHost: DesktopTunHost,
): DesktopTunnelController {
    // One consistent view of the user's data per connect: the Core configuration and the TUN interface are built
    // at different moments, and a settings edit between them must not make the two disagree. The controller
    // reads `tunMode` first at every connect, which is where the view is taken.
    var pinned: DesktopConnectInputs? = null
    fun settings() = pinned?.settings ?: readSettings()
    fun servers() = pinned?.servers ?: readServers()
    fun configs() = pinned?.configs ?: readConfigs()
    fun latency() = pinned?.latency ?: readLatency()

    fun runningHttpProxyOptions(): Result<LocalProxyXrayConfigOptions> {
        val options = localProxyRuntime.activeOptions(coreRunning = coreController.state().isRunning)
        return if (options?.enableHttpProxy == true) {
            Result.success(options)
        } else {
            Result.failure(IllegalStateException("The running Core has no configured system-proxy HTTP inbound"))
        }
    }

    return DesktopTunnelController(
        configForProfile = {
            val settingsSnapshot = settings()
            val routingSnapshot = settingsSnapshot.toRoutingConfigRecord()
            val serverLibrarySnapshot = servers()
            val configLibrarySnapshot = configs()
            val trafficConfigContentSnapshot = configLibrarySnapshot.configs
                .associate { config -> config.id to config.content }
            val latencySnapshot = latency().toMap()
            runCatching {
                val globalAssetsDirectory = DesktopCoreRuntimes.discover().getOrThrow().directory
                val options = localProxyRuntime.prepareStart(
                    settingsSnapshot,
                    jsonAuth = serverLibrarySnapshot.selectedServerJsonAuth(),
                )
                buildDesktopSelectedServerConfig(
                    serverLibrary = serverLibrarySnapshot,
                    configLibrary = configLibrarySnapshot,
                    routing = routingSnapshot,
                    options = options,
                    trafficConfigContentById = trafficConfigContentSnapshot,
                    subscriptionPingUrl = settingsSnapshot.subscriptionPingUrl,
                    latencyByServerId = latencySnapshot,
                    routingDataDirectory = globalAssetsDirectory,
                    strictFullTunnel = settingsSnapshot.enableStrictFullTunnel,
                    answerTunDns = desktopAnswersTunDns(settingsSnapshot, configLibrarySnapshot),
                )?.getOrThrow()
                    ?: error(desktopText(Res.string.desktop_select_server_before_connect))
            }
        },
        startPlanForProfile = { _ ->
            runCatching {
                val settingsSnapshot = settings()
                val serverLibrarySnapshot = servers()
                val configLibrarySnapshot = configs()
                val latencySnapshot = latency().toMap()
                prepareDesktopTunnelStartPlan(
                    settings = settingsSnapshot,
                    serverLibrary = serverLibrarySnapshot,
                    configLibrary = configLibrarySnapshot,
                    latencyByServerId = latencySnapshot,
                    localProxyRuntime = localProxyRuntime,
                    resourceFiles = resourceFiles,
                    globalAssetsDirectory = DesktopCoreRuntimes.discover().getOrThrow().directory,
                )
            }
        },
        startCore = { request ->
            // Like Android: truncate the previous run's files, then follow the new ones.
            coreLogs.clear()
            coreLogs.startTailers(settings())
            localProxyRuntime.onCoreStarted(
                coreController.start(request.configuration, request.assetsDirectory, request.amneziaWg),
            )
        },
        stopCore = {
            localProxyRuntime.onCoreStopped(coreController.stop()).also {
                coreLogs.stopTailers()
            }
        },
        coreState = coreController::state,
        readCoreTraffic = coreController::readTunnelTraffic,
        awaitSystemProxyEndpoint = {
            if (!settings().useSystemProxy || !systemProxyManager.isSupportedHost()) {
                Result.success(Unit)
            } else {
                runningHttpProxyOptions().fold(
                    onSuccess = { options ->
                        localProxyReadiness.awaitLoopbackHttpEndpoint(
                            port = options.httpProxyPort,
                            coreState = coreController::state,
                        )
                    },
                    onFailure = { error -> Result.failure(error) },
                )
            }
        },
        acquireSystemProxy = {
            when {
                !settings().useSystemProxy -> Result.success(Unit)
                !systemProxyManager.isSupportedHost() -> Result.failure(
                    IllegalStateException(desktopText(Res.string.desktop_system_proxy_unsupported_platform)),
                )

                else -> runningHttpProxyOptions().mapCatching { options ->
                    systemProxyManager.acquire(
                        DesktopSystemProxyEndpoints(
                            httpPort = options.httpProxyPort,
                            socksPort = options.socksPort,
                        ),
                    ).getOrThrow()
                    Unit
                }
            }
        },
        releaseSystemProxy = {
            if (systemProxyManager.isSupportedHost()) {
                systemProxyManager.release().map { }
            } else {
                Result.success(Unit)
            }
        },
        systemProxySupported = systemProxyManager::isSupportedHost,
        reconcileSystemProxy = {
            if (!systemProxyManager.isSupportedHost()) {
                Result.success(Unit)
            } else {
                // Hand back a proxy of an earlier session, then switch off one left pointing at a dead port.
                systemProxyManager.release().map { }.onSuccess {
                    systemProxyManager.repairStale().onSuccess { repaired ->
                        if (repaired) DesktopLogger.info("Tunnel", "Switched off a system proxy that pointed at a dead local port")
                    }
                }
            }
        },
        // No silent fallback: with TUN mode on, a machine that cannot run it fails the connect and says why.
        tunMode = {
            val inputs = DesktopConnectInputs(readSettings(), readServers(), readConfigs(), readLatency().toMap())
            pinned = inputs
            inputs.settings.enableTunMode
        },
        verifyTunEndpoint = {
            runCatching {
                val options = localProxyRuntime.activeOptions(coreRunning = coreController.state().isRunning)
                    ?: error("SKIPI Core is not running")
                // The address and login hev will use: the running Core's own, not the saved settings'.
                val target = desktopTunStartRequest(
                    plan = desktopTunInterfacePlan(settings(), configs()),
                    options = options,
                    logPath = "",
                )
                localProxyReadiness.awaitSocksEndpoint(
                    host = target.socksAddress,
                    port = target.socksPort,
                    username = target.socksUsername,
                    password = target.socksPassword,
                    coreState = coreController::state,
                ).getOrThrow()
            }
        },
        prepareTun = {
            timedTunStage("prepare") {
                runCatching {
                    tunHost.unavailableReason()?.let { reason -> error(reason) }
                    tunHost.tunRuntime().prepare(desktopTunInterfacePlan(settings(), configs())).getOrThrow()
                }
            }
        },
        releaseTun = { runCatching { tunHost.tunRuntime().release().getOrThrow() } },
        startTun = {
            timedTunStage("start") {
                runCatching {
                    tunHost.unavailableReason()?.let { reason -> error(reason) }
                    val options = localProxyRuntime.activeOptions(coreRunning = coreController.state().isRunning)
                        ?: error("SKIPI Core is not running")
                    val settings = settings()
                    val configs = configs()
                    // The system resolves through the tunnel only if the running config answers it.
                    val coreAnswersDns = desktopAnswersTunDns(settings, configs) && servers().coreAnswersTunDns()
                    val request = desktopTunStartRequest(
                        plan = desktopTunInterfacePlan(settings, configs, coreAnswersDns),
                        options = options,
                        logPath = DesktopLogger.logDirectory().resolve("tun2socks.log").toString(),
                    )
                    tunHost.tunRuntime().start(request).getOrThrow()
                }
            }
        },
        stopTun = { runCatching { tunHost.tunRuntime().stop().getOrThrow() } },
        tunRunning = { tunHost.tunRuntime().isRunning() },
        tunSupported = { tunHost.hostSupported },
    )

}

/** Logs how long a TUN stage took, so a slow platform shows where the time goes. */
private inline fun <T> timedTunStage(stage: String, block: () -> Result<T>): Result<T> {
    val started = System.nanoTime()
    val result = block()
    val millis = (System.nanoTime() - started) / 1_000_000
    DesktopLogger.info("Tun", "TUN $stage took $millis ms" + if (result.isFailure) " (failed)" else "")
    return result
}

/** What one connect reads: the settings, servers, profiles and latencies as they were when it began. */
private class DesktopConnectInputs(
    val settings: DesktopAppSettings,
    val servers: DesktopServerLibrary,
    val configs: DesktopConfigLibrary,
    val latency: Map<Int, DesktopServerLatencyResult>,
)
