// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfileLibrary
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import platform.TunnelConnectRequest

/** Opt-in: the Desktop connect path with the dynamic local proxy port and the system proxy, against the real Core. */
class DesktopDynamicPortRealCoreTest {
    @Test
    fun dynamicPortConnectOpensTheListenerThatTheRuntimePublishes() = runBlocking {
        val runtimeDirectory = System.getenv("SKIPI_CORE_INTEGRATION_DIR")?.takeIf(String::isNotBlank) ?: return@runBlocking
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val coreController = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        val localProxyRuntime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            useSystemProxy = true,
            useWindowsSystemProxy = true,
            enableDynamicLocalProxyPort = true,
        )
        val xrayRoot = Files.createTempDirectory("skipi-dyn-port")
        val acquired = mutableListOf<DesktopSystemProxyEndpoints>()
        Files.createDirectories(Path.of(DesktopCoreLogPaths.default().errorLogPath).parent)
        try {
            val controller = DesktopTunnelController(
                configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
                startPlanForProfile = {
                    runCatching {
                        prepareDesktopTunnelStartPlan(
                            settings = settings,
                            serverLibrary = DesktopServerLibrary(
                                selectedServerId = 91,
                                servers = listOf(
                                    DesktopStoredProxyServer(
                                        id = 91,
                                        serverJson = Socks("Selected", "selected.example", "1080").encodePersistedProxyServer(),
                                    ),
                                ),
                            ),
                            configLibrary = ConfigProfileLibrary(),
                            latencyByServerId = emptyMap(),
                            localProxyRuntime = localProxyRuntime,
                            resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot.toFile()),
                            globalAssetsDirectory = runtime.directory,
                        )
                    }
                },
                startCore = { request ->
                    localProxyRuntime.onCoreStarted(
                        coreController.start(request.configuration, request.assetsDirectory, request.amneziaWg),
                    )
                },
                stopCore = { localProxyRuntime.onCoreStopped(coreController.stop()) },
                coreState = coreController::state,
                awaitSystemProxyEndpoint = {
                    val options = localProxyRuntime.activeOptions(coreController.state().isRunning)!!
                    DesktopLocalProxyReadiness().awaitLoopbackHttpEndpoint(options.httpProxyPort, coreState = coreController::state)
                },
                acquireSystemProxy = {
                    val options = localProxyRuntime.activeOptions(coreController.state().isRunning)!!
                    acquired += DesktopSystemProxyEndpoints(httpPort = options.httpProxyPort, socksPort = options.socksPort)
                    Result.success(Unit)
                },
                systemProxySupported = { true },
            )

            controller.connect(TunnelConnectRequest("91")).getOrThrow()

            val active = assertNotNull(localProxyRuntime.activeOptions(coreController.state().isRunning))
            assertTrue(active.socksPort != 10_808, "dynamic port must differ from the configured one")
            Socket("127.0.0.1", active.socksPort).use { socket ->
                socket.soTimeout = 2_000
                DataOutputStream(socket.getOutputStream()).apply { write(byteArrayOf(5, 1, 0)); flush() }
                val input = DataInputStream(socket.getInputStream())
                assertEquals(5, input.readUnsignedByte())
            }
            assertEquals(1, acquired.size)
            controller.disconnect().getOrThrow()
        } finally {
            coreController.close()
            xrayRoot.toFile().deleteRecursively()
        }
    }
}
