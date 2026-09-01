// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import com.sun.net.httpserver.HttpServer
import features.subscription.SubscriptionEmbeddedConfig
import features.subscription.SubscriptionMetadata
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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

            val update = DesktopSubscriptionFetcher().fetchAndImport("http://127.0.0.1:${server.address.port}/sub")

            assertEquals(DefaultDesktopSubscriptionUserAgent, receivedAgent)
            assertEquals("Desktop Provider", update.response.headers["profile-title"])
            assertEquals("Desktop Provider", update.metadata.profileTitle)
            assertEquals(1, update.importResult.servers.size)
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

    @Test
    fun resolvesInlineRoutingConfigFromSubscriptionMetadata() {
        withSubscriptionServer { server ->
            server.createContext("/sub") { exchange ->
                exchange.responseHeaders.add("routing", "skipi://conf/onadd/W1J1bGVdCkZJTkFMLFBST1hZ")
                val body = "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=tls#One"
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }

            val fetcher = DesktopSubscriptionFetcher()
            val update = fetcher.fetchAndImport("http://127.0.0.1:${server.address.port}/sub")
            val config = fetcher.resolveEmbeddedConfig(update.metadata)

            assertEquals("[Rule]\nFINAL,PROXY", config?.content)
            assertEquals(true, config?.activate)
            assertEquals("", config?.sourceUrl)
        }
    }

    @Test
    fun refuses_loopback_mihomo_provider_without_touching_the_local_endpoint() {
        withSubscriptionServer { server ->
            val providerUrl = "http://127.0.0.1:${server.address.port}/provider"
            var providerRequests = 0
            server.createContext("/sub") { exchange ->
                val body = """
                    proxy-providers:
                      remote:
                        type: http
                        url: $providerUrl
                """.trimIndent()
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }
            server.createContext("/provider") { exchange ->
                providerRequests += 1
                val body = """
                    proxies:
                      - name: Provider VLESS
                        type: vless
                        server: provider.example.com
                        port: 443
                        uuid: 8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6
                        tls: true
                """.trimIndent()
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }

            val update = DesktopSubscriptionFetcher().fetchAndImport(
                "http://127.0.0.1:${server.address.port}/sub",
            )

            assertEquals(0, update.importResult.servers.size)
            assertEquals(0, providerRequests)
            assertTrue(update.importDiagnostics.any { diagnostic -> diagnostic.contains("Провайдер 'remote'") })
        }
    }

    @Test
    fun rejects_private_loopback_and_metadata_automatic_resource_urls() {
        listOf(
            "http://127.0.0.1/provider",
            "http://10.0.0.1/provider",
            "http://169.254.169.254/latest/meta-data",
            "http://metadata.google.internal/computeMetadata/v1",
            "http://[::1]/provider",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException> {
                requireSafeAutomaticSubscriptionResourceUrl(value)
            }
        }
    }

    @Test
    fun rejects_hostname_when_dns_resolution_includes_a_private_address() {
        val privateAddress = InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1))

        assertFailsWith<IllegalArgumentException> {
            requireSafeAutomaticSubscriptionResourceUrl(
                "https://provider.example.com/list.yaml",
                resolveAddresses = { arrayOf(privateAddress) },
            )
        }
    }

    @Test
    fun accepts_public_automatic_resource_url() {
        val uri = requireSafeAutomaticSubscriptionResourceUrl("https://8.8.8.8/list.yaml")

        assertEquals("8.8.8.8", uri.host)
    }

    @Test
    fun remote_embedded_config_rejects_a_loopback_url_before_fetching() {
        val metadata = SubscriptionMetadata(
            embeddedConfig = SubscriptionEmbeddedConfig(
                payload = "http://127.0.0.1/private.conf",
                activate = true,
                isUrl = true,
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            DesktopSubscriptionFetcher().resolveEmbeddedConfig(metadata)
        }
    }

    @Test
    fun limits_subscription_response_body_before_importing_it() {
        withSubscriptionServer { server ->
            server.createContext("/large") { exchange ->
                val body = "x".repeat(33)
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }

            assertFailsWith<DesktopSubscriptionResponseTooLargeException> {
                DesktopSubscriptionFetcher(maxResponseBytes = 32)
                    .fetch("http://127.0.0.1:${server.address.port}/large")
            }
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
