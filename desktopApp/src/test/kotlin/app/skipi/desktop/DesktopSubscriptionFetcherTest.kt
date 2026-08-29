// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import com.sun.net.httpserver.HttpServer
import features.subscription.importSubscriptionServers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.net.InetSocketAddress

class DesktopSubscriptionFetcherTest {
    @Test
    fun fetches_subscription_body_headers_and_user_agent() {
        withSubscriptionServer { server ->
            var receivedAgent = ""
            server.createContext("/sub") { exchange ->
                receivedAgent = exchange.requestHeaders.getFirst("User-Agent").orEmpty()
                exchange.responseHeaders.add("profile-title", "Desktop Provider")
                val body = "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=tls#One"
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }

            val response = DesktopSubscriptionFetcher().fetch("http://127.0.0.1:${server.address.port}/sub")

            assertEquals(DefaultDesktopSubscriptionUserAgent, receivedAgent)
            assertEquals("Desktop Provider", response.headers["profile-title"])
            assertEquals(1, response.body.importSubscriptionServers().servers.size)
        }
    }

    @Test
    fun exposes_non_success_http_statuses() {
        withSubscriptionServer { server ->
            server.createContext("/denied") { exchange ->
                exchange.sendResponseHeaders(403, -1)
                exchange.close()
            }

            val error = assertFailsWith<DesktopSubscriptionHttpException> {
                DesktopSubscriptionFetcher().fetch("http://127.0.0.1:${server.address.port}/denied")
            }
            assertEquals(403, error.statusCode)
        }
    }
}

private fun withSubscriptionServer(block: (HttpServer) -> Unit) {
    val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.start()
    try {
        block(server)
    } finally {
        server.stop(0)
    }
}
