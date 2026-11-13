// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmDiagnosticsBindingFailureTest {
    @Test
    fun httpClientReportsAFailedDirectBindInsteadOfUsingTheDefaultRoute() = runBlocking {
        var hits = 0
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            hits++
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.write("ok".toByteArray())
            exchange.close()
        }
        server.start()
        try {
            val failures = mutableListOf<Throwable>()
            val client = JvmDiagnosticsHttpClient(
                route = DiagnosticsRoute.Direct,
                binding = object : DiagnosticsNetworkBinding {
                    override fun openConnection(url: URL): HttpURLConnection? = error("bind failed")
                },
                onFailure = { _, error -> failures += error },
            )

            val body = client.getText("http://127.0.0.1:${server.address.port}/", "text/plain")

            assertNull(body)
            assertEquals(1, failures.size)
            assertEquals(0, hits, "a failed bind must not fall back to the default route")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun dnsResolverReportsAFailedDirectBindInsteadOfUsingTheDefaultRoute() = runBlocking {
        // The resolver always talks to port 53; where it cannot be bound (unprivileged Unix) there is
        // nothing to observe, so the check is skipped.
        val dnsServer = runCatching { DatagramSocket(53, InetAddress.getLoopbackAddress()) }.getOrNull()
            ?: return@runBlocking
        dnsServer.use {
            dnsServer.soTimeout = 1_000
            val resolver = JvmDnsTxtResolver(
                route = DiagnosticsRoute.Direct,
                binding = object : DiagnosticsNetworkBinding {
                    override fun bind(socket: DatagramSocket) = error("bind failed")
                },
                timeoutMillis = 500,
            )

            val answer = resolver.queryTxt("127.0.0.1", "example.test")

            assertNull(answer)
            val received = runCatching { dnsServer.receive(DatagramPacket(ByteArray(512), 512)) }
            assertTrue(received.isFailure, "no query may leave through the default route")
        }
    }
}
