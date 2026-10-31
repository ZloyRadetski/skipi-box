// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.encodePersistedProxyServer
import features.routing.model.RouteRule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Comparator
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Optional integration coverage for generated selected-server configs in the real desktop Core. */
class DesktopSelectedServerCoreFfmIntegrationTest {
    @Test
    fun generatedChainCarriesLoopbackTrafficThroughItsConfiguredHopOrder() = withIntegrationRuntime { runtime ->
        LoopbackHttpMarkerServer().use { marker ->
            LoopbackSocksRelay().use { hopOne ->
                LoopbackSocksRelay().use { hopTwo ->
                    DesktopCoreController(runtimeProvider = { Result.success(runtime) }).use { controller ->
                        val options = loopbackOptions()
                        val config = selectedConfig(
                            library(
                                selectedId = 99,
                                stored(10, socks("Hop one", hopOne.port)),
                                stored(20, socks("Hop two", hopTwo.port)),
                                stored(99, ChainProxy(remarks = "Two hops", proxyServerIds = listOf(10, 20))),
                            ),
                            options,
                            marker.url("/subscription-ping"),
                        )

                        assertTrue(controller.start(config).getOrThrow().isRunning)
                        val response = requestThroughSocks(options.socksPort, marker.port, "/chain")

                        assertTrue(response.contains("HTTP/1.1 200 OK"), response)
                        assertTrue(response.endsWith("chain-marker"), response)
                        assertTrue(hopOne.targets.any { it.port == hopTwo.port }, "hop1 did not dial hop2: ${hopOne.targets}")
                        assertTrue(hopTwo.targets.any { it.port == marker.port }, "hop2 did not dial marker: ${hopTwo.targets}")
                        assertTrue(!controller.stop().getOrThrow().isRunning)
                    }
                }
            }
        }
    }

    @Test
    fun generatedAutomaticGroupStartsAndRoutesLoopbackTrafficThroughAMember() = withIntegrationRuntime { runtime ->
        LoopbackHttpMarkerServer().use { marker ->
            LoopbackSocksRelay().use { first ->
                LoopbackSocksRelay().use { second ->
                    DesktopCoreController(runtimeProvider = { Result.success(runtime) }).use { controller ->
                        val options = loopbackOptions()
                        val probeUrl = marker.url("/probe")
                        val config = selectedConfig(
                            library(
                                selectedId = 90,
                                stored(10, socks("Member one", first.port)),
                                stored(20, socks("Member two", second.port)),
                                stored(
                                    90,
                                    StrategyGroup(
                                        remarks = "Loopback least-ping group",
                                        strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                                        proxyServerIds = listOf(10, 20),
                                        probeUrl = probeUrl,
                                        probeInterval = "1s",
                                    ),
                                ),
                            ),
                            options,
                            marker.url("/subscription-ping"),
                        )
                        val root = Json.parseToJsonElement(config).jsonObject
                        val routing = root.getValue("routing").jsonObject
                        assertTrue(routing.getValue("balancers").jsonArray.isNotEmpty())
                        assertTrue(routing.getValue("rules").jsonArray.any { rule ->
                            rule.jsonObject["balancerTag"]?.jsonPrimitive?.content == "proxy"
                        })
                        assertEquals(probeUrl, root.getValue("observatory").jsonObject
                            .getValue("probeURL").jsonPrimitive.content)

                        assertTrue(controller.start(config).getOrThrow().isRunning)
                        val response = requestThroughSocks(options.socksPort, marker.port, "/strategy")

                        assertTrue(response.contains("HTTP/1.1 200 OK"), response)
                        assertTrue(response.endsWith("strategy-marker"), response)
                        assertTrue(listOf(first, second).any { relay -> relay.targets.any { it.port == marker.port } })
                        assertTrue(!controller.stop().getOrThrow().isRunning)
                    }
                }
            }
        }
    }

    @Test
    fun generatedNoProfileConfigRoutesLoopbackTrafficThroughSavedGlobalRulesAndDefault() =
        withIntegrationRuntime { runtime ->
            LoopbackHttpMarkerServer().use { ruleMarker ->
                LoopbackHttpMarkerServer().use { defaultMarker ->
                    LoopbackSocksRelay().use { selectedRelay ->
                        LoopbackSocksRelay().use { ruleRelay ->
                            LoopbackSocksRelay().use { defaultRelay ->
                                val resourceRoot = Files.createTempDirectory("skipi-global-routing-resources-")
                                try {
                                    val plan = prepareDesktopTunnelStartPlan(
                                        settings = DesktopAppSettings(
                                            localProxyPort = loopbackOptions().socksPort,
                                            localProxyListenAddress = "127.0.0.1",
                                            useSystemProxy = false,
                                            subscriptionPingUrl = ruleMarker.url("/subscription-ping"),
                                            routeDomainStrategy = 2,
                                            defaultRouteOutboundTag = "3",
                                            routeRules = listOf(
                                                RouteRule(
                                                    id = 5,
                                                    remarks = "route marker port through R",
                                                    outboundTag = "2",
                                                    ip = listOf("127.0.0.1"),
                                                    port = ruleMarker.port.toString(),
                                                ),
                                            ),
                                        ),
                                        serverLibrary = library(
                                            selectedId = 1,
                                            stored(1, socks("Selected S", selectedRelay.port)),
                                            stored(2, socks("Rule R", ruleRelay.port)),
                                            stored(3, socks("Default D", defaultRelay.port)),
                                        ),
                                        configLibrary = DesktopConfigLibrary(),
                                        latencyByServerId = emptyMap(),
                                        localProxyRuntime = DesktopLocalProxyRuntime(),
                                        resourceFiles = DesktopProfileResourceFiles(xrayRootDir = resourceRoot.toFile()),
                                    )

                                    DesktopCoreController(runtimeProvider = { Result.success(runtime) }).use { controller ->
                                        assertTrue(controller.start(plan.configuration, plan.assetsDirectory).getOrThrow().isRunning)
                                        val ruleResponse = requestThroughSocks(plan.localProxyPort, ruleMarker.port, "/rule")
                                        val defaultResponse = requestThroughSocks(plan.localProxyPort, defaultMarker.port, "/default")

                                        assertTrue(ruleResponse.contains("HTTP/1.1 200 OK"), ruleResponse)
                                        assertTrue(ruleResponse.endsWith("strategy-marker"), ruleResponse)
                                        assertTrue(defaultResponse.contains("HTTP/1.1 200 OK"), defaultResponse)
                                        assertTrue(defaultResponse.endsWith("strategy-marker"), defaultResponse)
                                        assertTrue(
                                            SocksTarget("127.0.0.1", ruleMarker.port) in ruleRelay.targets,
                                            "Rule relay R did not receive the matching marker: ${ruleRelay.targets}",
                                        )
                                        assertTrue(
                                            SocksTarget("127.0.0.1", defaultMarker.port) in defaultRelay.targets,
                                            "Default relay D did not receive the unmatched marker: ${defaultRelay.targets}",
                                        )
                                        assertTrue(
                                            ruleRelay.targets.none { it.port == defaultMarker.port },
                                            "Unmatched marker reached rule relay R: ${ruleRelay.targets}",
                                        )
                                        assertTrue(
                                            defaultRelay.targets.none { it.port == ruleMarker.port },
                                            "Matching marker reached default relay D: ${defaultRelay.targets}",
                                        )
                                        assertTrue(
                                            listOf(ruleMarker.port, defaultMarker.port).none { targetPort ->
                                                selectedRelay.targets.any { target -> target.port == targetPort }
                                            },
                                            "Selected relay S received an application marker: ${selectedRelay.targets}",
                                        )
                                        assertTrue(!controller.stop().getOrThrow().isRunning)
                                    }
                                } finally {
                                    Files.walk(resourceRoot).use { paths ->
                                        paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun selectedConfig(
        library: DesktopServerLibrary,
        options: LocalProxyXrayConfigOptions,
        subscriptionPingUrl: String,
    ): String = assertNotNull(
        DesktopSelectedServerXrayConfigFactory.build(
            serverLibrary = library,
            routing = RoutingConfigRecord(),
            trafficConfigContentById = emptyMap(),
            options = options,
            subscriptionPingUrl = subscriptionPingUrl,
            latencyByServerId = emptyMap(),
        ),
    ).getOrThrow()

    private fun loopbackOptions() = LocalProxyXrayConfigOptions(
        socksPort = ServerSocket(0, 0, Loopback).use(ServerSocket::getLocalPort),
        listenAddress = "127.0.0.1",
        logLevel = "none",
    )

    private fun library(selectedId: Int, vararg servers: DesktopStoredProxyServer) =
        DesktopServerLibrary(selectedServerId = selectedId, servers = servers.toList())

    private fun stored(id: Int, server: ProxyServer<*>) = DesktopStoredProxyServer(id, server.encodePersistedProxyServer())

    private fun socks(name: String, port: Int) = Socks(remarks = name, server = "127.0.0.1", port = port.toString())

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

    private fun withIntegrationRuntime(block: (DesktopCoreRuntime) -> Unit) {
        val directory = System.getenv(IntegrationRuntimeDirectoryEnv)?.takeIf(String::isNotBlank) ?: return
        val source = DesktopCoreRuntimes.fromDirectory(Path.of(directory)).getOrThrow()
        val temp = Files.createTempDirectory("skipi-selected-server-ffm-")
        try {
            val geoIp = Files.copy(source.geoIp, temp.resolve("geoip.dat"), REPLACE_EXISTING)
            val geoSite = Files.copy(source.geoSite, temp.resolve("geosite.dat"), REPLACE_EXISTING)
            block(DesktopCoreRuntime(temp, source.library, geoIp, geoSite))
        } finally {
            Files.walk(temp).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    private companion object {
        const val IntegrationRuntimeDirectoryEnv = "SKIPI_CORE_INTEGRATION_DIR"
        const val SocketTimeoutMillis = 5_000
        val Loopback = InetAddress.getByName("127.0.0.1")
    }
}

internal data class SocksTarget(val host: String, val port: Int)

/** Local HTTP fixture returns 204 to Core probes and a marker body to tunnel requests. */
internal class LoopbackHttpMarkerServer : AutoCloseable {
    private val listener = LoopbackSocketListener(::serve)
    val port: Int get() = listener.port
    fun url(path: String) = "http://127.0.0.1:$port$path"

    private fun serve(socket: Socket) {
        socket.soTimeout = 8_000
        val input = BufferedReader(InputStreamReader(socket.getInputStream(), UTF_8))
        val requestLine = input.readLine().orEmpty()
        while (input.readLine()?.isNotEmpty() == true) Unit
        val probe = requestLine.contains("/probe")
        val body = if (probe) "" else if (requestLine.contains("/chain")) "chain-marker" else "strategy-marker"
        val status = if (probe) "204 No Content" else "200 OK"
        socket.getOutputStream().write(
            "HTTP/1.1 $status\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n$body".toByteArray(UTF_8),
        )
    }

    override fun close() = listener.close()
}

/** Minimal unauthenticated SOCKS5 CONNECT relay used only between loopback endpoints. */
internal class LoopbackSocksRelay : AutoCloseable {
    private val targetLog = ConcurrentLinkedQueue<SocksTarget>()
    private val upstreamSockets = ConcurrentHashMap.newKeySet<Socket>()
    private val listener = LoopbackSocketListener(::relay)
    val port: Int get() = listener.port
    val targets: List<SocksTarget> get() = targetLog.toList()

    private fun relay(client: Socket) {
        client.soTimeout = 8_000
        val input = DataInputStream(client.getInputStream())
        val output = DataOutputStream(client.getOutputStream())
        require(input.readUnsignedByte() == 5)
        val methods = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        require(methods.any { it.toInt() == 0 })
        output.write(byteArrayOf(5, 0)); output.flush()
        require(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 1)
        input.readUnsignedByte()
        val host = when (input.readUnsignedByte()) {
            1 -> InetAddress.getByAddress(ByteArray(4).also { input.readFully(it) }).hostAddress
            3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.toString(UTF_8)
            4 -> InetAddress.getByAddress(ByteArray(16).also { input.readFully(it) }).hostAddress
            else -> error("Unknown SOCKS5 address type")
        }
        val target = SocksTarget(host, input.readUnsignedShort())
        targetLog += target
        val upstream = Socket().also(upstreamSockets::add)
        upstream.connect(InetSocketAddress(target.host, target.port), 8_000)
        client.soTimeout = 0; upstream.soTimeout = 0
        output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0)); output.flush()
        val clientToTarget = listener.submit { copy(client.getInputStream(), upstream.getOutputStream()); upstream.shutdownOutput() }
        val targetToClient = listener.submit { copy(upstream.getInputStream(), client.getOutputStream()); client.shutdownOutput() }
        try {
            clientToTarget.get(10, TimeUnit.SECONDS)
            targetToClient.get(10, TimeUnit.SECONDS)
        } finally {
            runCatching { client.close() }; runCatching { upstream.close() }
            upstreamSockets -= upstream
            clientToTarget.cancel(true); targetToClient.cancel(true)
        }
    }

    private fun copy(input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return
            output.write(buffer, 0, count); output.flush()
        }
    }

    override fun close() {
        upstreamSockets.forEach { runCatching { it.close() } }
        listener.close()
    }
}

/** Owns local sockets and bounded workers; cleanup closes sockets before waiting for threads. */
private class LoopbackSocketListener(private val handler: (Socket) -> Unit) : AutoCloseable {
    private val server = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    private val workers = Executors.newCachedThreadPool { Thread(it, "skipi-ffm-loopback").apply { isDaemon = true } }
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val acceptThread = Thread(::accept, "skipi-ffm-accept").apply { isDaemon = true; start() }
    val port: Int get() = server.localPort
    fun submit(block: () -> Unit) = workers.submit(block)

    private fun accept() {
        while (!server.isClosed) {
            val socket = try { server.accept() } catch (_: SocketException) { return }
            sockets += socket
            workers.submit { try { handler(socket) } finally { sockets -= socket; runCatching { socket.close() } } }
        }
    }

    override fun close() {
        runCatching { server.close() }; sockets.forEach { runCatching { it.close() } }
        acceptThread.join(1_000); workers.shutdownNow(); workers.awaitTermination(1, TimeUnit.SECONDS)
    }
}
