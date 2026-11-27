// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectSpeedTestConnectionTest {
    @Test
    fun withoutATunnelTheRequestGoesStraightToTheServer() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val connection = directSpeedTestConnectionFactory().connect("http://127.0.0.1:${server.address.port}/") { it.connectTimeout = 3_000 }
            assertEquals("ok", connection.inputStream.use { String(it.readAllBytes()) })
        } finally {
            server.stop(0)
        }
    }
}
