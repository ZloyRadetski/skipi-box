// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.encodePersistedProxyServer
import platform.LocalProxyXrayConfigOptions
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opt-in end-to-end test of the AmneziaWG runner inside the real shared Core library
 * (`SKIPI_CORE_INTEGRATION_DIR`). The peer address is unreachable on purpose: starting the
 * runner and Core, and releasing both, does not need a handshake.
 */
class DesktopCoreAmneziaWgIntegrationTest {
    private fun freePort(): Int = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).use(ServerSocket::getLocalPort)

    private fun listens(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }
        true
    } catch (_: Exception) {
        false
    }

    private fun socksGreeting(port: Int): List<Int> = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 2_000
        socket.getOutputStream().apply { write(byteArrayOf(0x05, 0x01, 0x00)); flush() }
        val reply = ByteArray(2)
        check(socket.getInputStream().readNBytes(reply, 0, 2) == 2) { "No SOCKS reply" }
        reply.map { it.toInt() and 0xFF }
    }

    @Test
    fun anAmneziaWgServerRunsBehindALoopbackSocksBridgeAndIsReleasedWithCore() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val server = AmneziaWg(
            remarks = "Integration AWG",
            server = "127.0.0.1",
            port = "59999",
            secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
            publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
        )
        val library = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(DesktopStoredProxyServer(id = 10, serverJson = server.encodePersistedProxyServer())),
        )
        val generated = assertNotNull(
            buildDesktopSelectedServerConfigWithBridge(
                serverLibrary = library,
                configLibrary = DesktopConfigLibrary(),
                routing = RoutingConfigRecord(),
                options = LocalProxyXrayConfigOptions(socksPort = freePort()),
                trafficConfigContentById = emptyMap(),
                subscriptionPingUrl = "",
                latencyByServerId = emptyMap(),
            ),
        ).getOrThrow()
        val bridge = assertNotNull(generated.amneziaWg)
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        try {
            val started = controller.start(generated.json, amneziaWg = bridge).getOrThrow()

            assertTrue(started.isRunning)
            assertEquals(listOf(5, 0), socksGreeting(bridge.socksPort), "The runner must answer SOCKS5 on its bridge port")

            assertFalse(controller.stop().getOrThrow().isRunning)
            assertFalse(listens(bridge.socksPort), "The runner must be released together with Core")
        } finally {
            controller.close()
        }
    }

    @Test
    fun aFailedCoreStartDoesNotLeaveTheRunnerListening() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val port = freePort()
        val bridge = DesktopAmneziaWgBridge(
            configJson = """
                {"secretKey":"aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=","address":["10.8.0.2/32"],
                 "dnsServers":["1.1.1.1"],
                 "peers":[{"publicKey":"YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
                           "endpoint":"127.0.0.1:59999","allowedIPs":["0.0.0.0/0"]}]}
            """.trimIndent(),
            socksPort = port,
        )
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        try {
            val result = controller.start("{ this is not a valid core configuration", amneziaWg = bridge)

            assertTrue(result.isFailure)
            assertFalse(listens(port), "A failed start must stop the runner it started")
        } finally {
            controller.close()
        }
    }

    @Test
    fun anOutboundIsMeasuredInAnIsolatedCoreInstanceWithoutASession() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val http = com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> exchange.sendResponseHeaders(204, -1); exchange.close() }
            start()
        }
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        try {
            val delay = controller.measureOutboundDelay(
                configJson = """{"log":{"loglevel":"none"},"outbounds":[{"tag":"probe","protocol":"freedom"}]}""",
                targetUrl = "http://127.0.0.1:${http.address.port}/",
            ).getOrThrow()

            assertTrue(delay >= 0)
            assertFalse(controller.state().isRunning, "a probe must not leave a session behind")
        } finally {
            http.stop(0)
            controller.close()
        }
    }

    @Test
    fun anAmneziaWgProbeStartsAndReleasesTheRunnerAroundTheMeasurement() {
        val runtimeDirectory = System.getenv(IntegrationRuntimeDirectoryEnv)
            ?.takeIf(String::isNotBlank)
            ?: return
        val runtime = DesktopCoreRuntimes.fromDirectory(Path.of(runtimeDirectory)).getOrThrow()
        val server = AmneziaWg(
            remarks = "Probe AWG",
            server = "127.0.0.1",
            port = "59999",
            secretKey = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE=",
            publicKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s=",
        )
        val probe = buildDesktopLatencyProbe(server)
        val bridge = assertNotNull(probe.amneziaWg)
        val controller = DesktopCoreController(runtimeProvider = { Result.success(runtime) })
        try {
            // The peer never answers, so there is no delay to report; the runner must still be released.
            val result = controller.measureOutboundDelay(probe.configJson, "http://127.0.0.1:9/", bridge)

            assertTrue(result.isFailure)
            assertFalse(listens(bridge.socksPort), "The probe must release the runner")
        } finally {
            controller.close()
        }
    }

    private companion object {
        const val IntegrationRuntimeDirectoryEnv = "SKIPI_CORE_INTEGRATION_DIR"
    }
}
