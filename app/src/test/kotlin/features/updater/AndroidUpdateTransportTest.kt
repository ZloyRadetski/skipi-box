// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import engine.proxy.LocalProxyOptions
import engine.proxy.LocalProxyRuntime
import org.junit.After
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** The "update through the proxy" switch decides which way update requests leave the app while the tunnel runs. */
class AndroidUpdateTransportTest {
    private val proxyConnections = AtomicInteger()

    /** Stands in for the local SOCKS inbound: counts who connects and hangs up. */
    private val proxy = ServerSocket(0).also { server ->
        thread(isDaemon = true) {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                proxyConnections.incrementAndGet()
                socket.close()
            }
        }
    }

    private val release = ServerSocket(0).also { server ->
        thread(isDaemon = true) {
            while (true) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                socket.use {
                    val request = it.getInputStream().bufferedReader()
                    while (!request.readLine().isNullOrEmpty()) Unit
                    it.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                }
            }
        }
    }

    private val url get() = "http://127.0.0.1:${release.localPort}/update.apk"

    private fun startTunnel() = LocalProxyRuntime.update(
        LocalProxyOptions(listenAddress = "127.0.0.1", port = proxy.localPort, username = "", password = ""),
    )

    private fun transport(viaProxy: Boolean) =
        AndroidUpdateTransport(context = null, connectTimeoutMs = 2_000, readTimeoutMs = 2_000, viaProxy = viaProxy)

    @After
    fun cleanup() {
        LocalProxyRuntime.clear()
        proxy.close()
        release.close()
    }

    @Test
    fun withTheSwitchOffARunningTunnelIsBypassed() {
        startTunnel()

        val body = transport(viaProxy = false).open(url).use { it.body.bufferedReader().readText() }

        assertEquals("ok", body)
        assertEquals(0, proxyConnections.get())
    }

    @Test
    fun withTheSwitchOnTheRequestGoesToTheLocalProxy() {
        startTunnel()

        assertFails { transport(viaProxy = true).open(url) }

        assertEquals(true, proxyConnections.get() > 0)
    }

    @Test
    fun withTheSwitchOnAndNoTunnelTheRequestStillGoesOutDirectly() {
        val status = transport(viaProxy = true).open(url).use { it.status }

        assertEquals(200, status)
    }
}
