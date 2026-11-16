// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLocalProxyReadinessTest {
    @Test
    fun succeeds_after_a_retry_when_xray_is_alive() {
        var now = 0L
        var attempts = 0
        val probe = DesktopLocalProxyReadiness(
            tcpConnect = { _, _, _ ->
                attempts++
                if (attempts < 2) throw IOException("not bound yet")
            },
            monotonicMillis = { now },
            sleep = { duration -> now += duration },
        )

        assertTrue(
            probe.awaitLoopbackHttpEndpoint(
                port = 10809,
                timeout = Duration.ofSeconds(1),
                coreState = { DesktopCoreState(isRunning = true) },
            ).isSuccess,
        )
        assertEquals(2, attempts)
    }

    @Test
    fun fails_without_touching_the_os_proxy_when_xray_exits() {
        val probe = DesktopLocalProxyReadiness(
            tcpConnect = { _, _, _ -> throw IOException("refused") },
            sleep = { },
        )

        val result = probe.awaitLoopbackHttpEndpoint(
            port = 10809,
            timeout = Duration.ofSeconds(1),
            coreState = { DesktopCoreState(isRunning = false) },
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("stopped"))
    }

    @Test
    fun rechecks_that_xray_is_alive_after_the_port_accepts_connections() {
        var checks = 0
        val probe = DesktopLocalProxyReadiness(
            tcpConnect = { _, _, _ -> },
            sleep = { },
        )

        val result = probe.awaitLoopbackHttpEndpoint(
            port = 10809,
            timeout = Duration.ofSeconds(1),
            coreState = {
                checks++
                DesktopCoreState(isRunning = checks == 1)
            },
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("while"))
    }

    // The TUN hands every connection to hev, which talks to the SOCKS inbound Core was started with. A dead
    // tunnel looks exactly like a working one from outside, so the endpoint is checked with a real SOCKS5
    // handshake (the port, and the login when there is one) before hev is started.
    private fun fakeSocks(reply: (java.net.Socket) -> Unit): Pair<ServerSocket, Thread> {
        val server = ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        val worker = thread(isDaemon = true) {
            runCatching { server.accept().use(reply) }
        }
        return server to worker
    }

    private fun socksProbe() = DesktopLocalProxyReadiness()

    @Test
    fun socksEndpointWithoutAuthAnswersTheHandshake() {
        val (server, _) = fakeSocks { socket ->
            socket.getInputStream().readNBytes(3)
            socket.getOutputStream().apply { write(byteArrayOf(5, 0)); flush() }
        }
        server.use {
            val result = socksProbe().awaitSocksEndpoint(
                port = server.localPort, username = "", password = "",
                timeout = Duration.ofSeconds(2), coreState = { DesktopCoreState(isRunning = true) },
            )
            assertTrue(result.isSuccess, result.exceptionOrNull()?.toString().orEmpty())
        }
    }

    @Test
    fun socksEndpointWithAuthAcceptsTheStoredLogin() {
        val (server, _) = fakeSocks { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            input.readNBytes(4)
            output.write(byteArrayOf(5, 2)); output.flush()
            val head = input.readNBytes(2)
            val user = input.readNBytes(head[1].toInt())
            val passLen = input.read()
            val pass = input.readNBytes(passLen)
            val ok = String(user) == "alice" && String(pass) == "secret"
            output.write(byteArrayOf(1, if (ok) 0 else 1)); output.flush()
        }
        server.use {
            val result = socksProbe().awaitSocksEndpoint(
                port = server.localPort, username = "alice", password = "secret",
                timeout = Duration.ofSeconds(2), coreState = { DesktopCoreState(isRunning = true) },
            )
            assertTrue(result.isSuccess, result.exceptionOrNull()?.toString().orEmpty())
        }
    }

    @Test
    fun socksEndpointRejectingTheLoginFailsAndNamesTheLogin() {
        val (server, _) = fakeSocks { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            input.readNBytes(4)
            output.write(byteArrayOf(5, 2)); output.flush()
            val head = input.readNBytes(2)
            input.readNBytes(head[1].toInt())
            input.readNBytes(input.read())
            output.write(byteArrayOf(1, 1)); output.flush()
        }
        server.use {
            val result = socksProbe().awaitSocksEndpoint(
                port = server.localPort, username = "alice", password = "wrong",
                timeout = Duration.ofSeconds(2), coreState = { DesktopCoreState(isRunning = true) },
            )
            assertTrue(result.isFailure)
            assertTrue("login" in result.exceptionOrNull()?.message.orEmpty(), result.exceptionOrNull()?.message.orEmpty())
        }
    }

    @Test
    fun aPortThatIsNotSocksFails() {
        val (server, _) = fakeSocks { socket ->
            socket.getInputStream().read()
            socket.getOutputStream().apply { write("HTTP/1.1 400 Bad Request\r\n\r\n".toByteArray()); flush() }
        }
        server.use {
            val result = socksProbe().awaitSocksEndpoint(
                port = server.localPort, username = "", password = "",
                timeout = Duration.ofSeconds(2), coreState = { DesktopCoreState(isRunning = true) },
            )
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun aPortNobodyListensOnFailsAndNamesIt() {
        val free = ServerSocket(0).use { it.localPort }
        val result = socksProbe().awaitSocksEndpoint(
            port = free, username = "", password = "",
            timeout = Duration.ofMillis(400), coreState = { DesktopCoreState(isRunning = true) },
        )

        assertTrue(result.isFailure)
        assertTrue(free.toString() in result.exceptionOrNull()?.message.orEmpty(), result.exceptionOrNull()?.message.orEmpty())
    }

    @Test
    fun aStoppedCoreFailsTheSocksCheckAtOnce() {
        val result = socksProbe().awaitSocksEndpoint(
            port = 1, username = "", password = "",
            timeout = Duration.ofSeconds(1), coreState = { DesktopCoreState(isRunning = false) },
        )

        assertTrue(result.isFailure)
        assertTrue("stopped" in result.exceptionOrNull()?.message.orEmpty())
    }
}
