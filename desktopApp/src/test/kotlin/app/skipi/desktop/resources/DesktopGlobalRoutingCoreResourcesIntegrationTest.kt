// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.ConfigProfileLibrary
import features.proxy.server.model.Socks
import features.proxy.server.model.encodePersistedProxyServer
import features.routing.model.RouteRule
import features.routing.usecase.GeoDatParser
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Opt-in real-Core proof that a missing global geo tag cannot blackhole traffic. */
class DesktopGlobalRoutingCoreResourcesIntegrationTest {
    @Test
    fun missingGlobalGeoSiteCriterionIsFilteredAndLoopbackRequestUsesDefaultProxy() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(runtimeDirectory).getOrThrow()
        val missingGeoTag = "skipi_missing_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        assertTrue(
            missingGeoTag !in GeoDatParser.parseTags(runtime.geoSite.toFile()),
            "The generated fixture tag unexpectedly exists in the integration geosite.dat",
        )

        val xrayRoot = Files.createTempDirectory("skipi-core-empty-xray-")
        try {
            LoopbackHttpMarkerServer().use { marker ->
                LoopbackSocksRelay().use { defaultRelay ->
                    val options = platform.LocalProxyXrayConfigOptions(
                        socksPort = freeLoopbackPort(),
                        listenAddress = "127.0.0.1",
                        logLevel = "none",
                    )
                    val settings = DesktopAppSettings(
                        localProxyPort = options.socksPort,
                        localProxyListenAddress = "127.0.0.1",
                        useSystemProxy = false,
                        useWindowsSystemProxy = false,
                        subscriptionPingUrl = marker.url("/probe"),
                        defaultRouteOutboundTag = "proxy",
                        routeRules = listOf(
                            RouteRule(
                                id = 4,
                                remarks = "missing geosite fixture must be removed",
                                outboundTag = "block",
                                domain = listOf("geosite:$missingGeoTag"),
                            ),
                        ),
                    )
                    val plan = prepareDesktopTunnelStartPlan(
                        settings = settings,
                        serverLibrary = DesktopServerLibrary(
                            selectedServerId = 91,
                            servers = listOf(
                                DesktopStoredProxyServer(
                                    id = 91,
                                    serverJson = Socks(
                                        remarks = "Default loopback relay",
                                        server = "127.0.0.1",
                                        port = defaultRelay.port.toString(),
                                    ).encodePersistedProxyServer(),
                                ),
                            ),
                        ),
                        configLibrary = ConfigProfileLibrary(),
                        latencyByServerId = emptyMap(),
                        localProxyRuntime = DesktopLocalProxyRuntime(),
                        resourceFiles = DesktopProfileResourceFiles(xrayRootDir = xrayRoot.toFile()),
                        globalAssetsDirectory = runtime.directory,
                        allocatePort = { _, _ -> options.socksPort },
                    )

                    assertEquals(runtime.directory, plan.assetsDirectory)
                    val actualRules = Json.parseToJsonElement(plan.configuration).jsonObject
                        .getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
                    assertTrue(actualRules.none { rule -> rule["domain"]?.jsonArray?.any { value ->
                        value.jsonPrimitive.content == "geosite:$missingGeoTag"
                    } == true })

                    DesktopCoreController(runtimeProvider = { Result.success(runtime) }).use { controller ->
                        assertTrue(controller.start(plan.configuration, plan.assetsDirectory).getOrThrow().isRunning)
                        try {
                            val response = requestThroughSocks(options.socksPort, marker.port, "/strategy")
                            assertTrue(response.contains("HTTP/1.1 200 OK"), response)
                            assertTrue(response.endsWith("strategy-marker"), response)
                            assertTrue(
                                defaultRelay.targets.any { target -> target.port == marker.port },
                                "The default proxy did not receive the loopback request: ${defaultRelay.targets}",
                            )
                        } finally {
                            assertTrue(!controller.stop().getOrThrow().isRunning)
                        }
                    }
                }
            }
        } finally {
            Files.walk(xrayRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun freeLoopbackPort(): Int = ServerSocket(0, 0, Loopback).use(ServerSocket::getLocalPort)

    private fun requestThroughSocks(socksPort: Int, destinationPort: Int, path: String): String =
        Socket(Proxy(Proxy.Type.SOCKS, InetSocketAddress(Loopback, socksPort))).use { socket ->
            socket.soTimeout = SocketTimeoutMillis
            socket.connect(InetSocketAddress(Loopback, destinationPort), SocketTimeoutMillis)
            socket.getOutputStream().write(
                "GET $path HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n".toByteArray(UTF_8),
            )
            socket.getOutputStream().flush()
            socket.getInputStream().readBytes().toString(UTF_8)
        }

    private companion object {
        const val IntegrationRuntimeDirectoryEnv = "SKIPI_CORE_INTEGRATION_DIR"
        const val SocketTimeoutMillis = 5_000
        val Loopback = InetAddress.getByName("127.0.0.1")
    }
}
