// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import android.os.Build
import app.ProjectInfo
import engine.proxy.LocalProxyOptions
import engine.proxy.LocalProxyRuntime
import features.subscription.DefaultSubscriptionUserAgent
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.test.assertFailsWith

class AndroidSubscriptionFetcherTest {
    @Test
    fun disabledUpdateViaProxyUsesNoLocalSocksProxy() {
        LocalProxyRuntime.update(
            LocalProxyOptions(
                listenAddress = "127.0.0.1",
                port = 10808,
                username = "user",
                password = "password",
            ),
        )
        try {
            assertNull(AndroidSubscriptionFetchOptions(useRunningProxy = false).toProxy())

            val enabledProxy = checkNotNull(AndroidSubscriptionFetchOptions(useRunningProxy = true).toProxy())
            assertEquals(10808, enabledProxy.port)
            assertEquals("user", enabledProxy.username)
        } finally {
            LocalProxyRuntime.clear()
        }
    }

    @Test
    fun sends_android_request_headers_and_decrypts_age_response() {
        withSubscriptionServer { server ->
            val receivedHeaders = mutableMapOf<String, String>()
            val encryptedBody = " \n-----BEGIN AGE ENCRYPTED FILE-----\nfixture"
            server.createContext("/sub") { exchange ->
                exchange.requestHeaders.forEach { (name, values) ->
                    receivedHeaders[name.lowercase()] = values.firstOrNull().orEmpty()
                }
                exchange.responseHeaders.add("profile-title", "Age provider")
                exchange.sendResponseHeaders(200, encryptedBody.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(encryptedBody.encodeToByteArray()) }
            }
            val crypto = RecordingAgeCrypto("decrypted body")
            val fetcher = AndroidSubscriptionFetcher(
                installationHwid = "installation-hwid",
                ageCrypto = crypto,
            )

            val response = runBlocking {
                fetcher.fetchResponse(
                    url = "http://127.0.0.1:${server.address.port}/sub",
                    userAgent = "SKIPI/0.0/Android",
                    options = AndroidSubscriptionFetchOptions(ageSecretKey = " secret "),
                )
            }

            assertEquals(DefaultSubscriptionUserAgent, receivedHeaders["user-agent"])
            assertEquals("close", receivedHeaders["connection"]?.lowercase())
            assertEquals("SKIPI", receivedHeaders["x-client"])
            assertEquals(ProjectInfo.VERSION_NAME, receivedHeaders["x-app-version"])
            assertEquals("Android", receivedHeaders["x-device-os"])
            assertEquals(Build.VERSION.RELEASE.orEmpty().ifBlank { "unknown" }, receivedHeaders["x-ver-os"])
            assertEquals(Build.MODEL.orEmpty().ifBlank { "Android" }, receivedHeaders["x-device-model"])
            assertEquals("installation-hwid", receivedHeaders["x-hwid"])
            assertEquals("installation-hwid", receivedHeaders["x-device-id"])
            assertEquals("age1test-recipient", receivedHeaders["x-age-public-key"])
            assertEquals("secret", crypto.publicKeySecret)
            assertEquals("secret", crypto.decryptSecret)
            assertEquals(encryptedBody, crypto.decryptedText)
            assertEquals("decrypted body", response.body)
            assertEquals("Age provider", response.headers["profile-title"])
        }
    }

    @Test
    fun follows_any_three_hundred_redirect_and_allows_two_hops() {
        withSubscriptionServer { server ->
            server.createContext("/start") { exchange ->
                exchange.responseHeaders.add("Location", "/one")
                exchange.sendResponseHeaders(300, -1)
                exchange.close()
            }
            server.createContext("/one") { exchange ->
                exchange.responseHeaders.add("Location", "/final")
                exchange.sendResponseHeaders(303, -1)
                exchange.close()
            }
            server.createContext("/final") { exchange ->
                val body = "redirected body"
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }

            val body = runBlocking {
                AndroidSubscriptionFetcher("hwid", RecordingAgeCrypto("unused"))
                    .fetch(
                        url = "http://127.0.0.1:${server.address.port}/start",
                        userAgent = "custom-agent",
                        options = AndroidSubscriptionFetchOptions(),
                    )
            }

            assertEquals("redirected body", body)
        }
    }

    @Test
    fun fails_after_two_redirect_hops() {
        withSubscriptionServer { server ->
            server.createContext("/start") { exchange ->
                exchange.responseHeaders.add("Location", "/one")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/one") { exchange ->
                exchange.responseHeaders.add("Location", "/two")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/two") { exchange ->
                exchange.responseHeaders.add("Location", "/final")
                exchange.sendResponseHeaders(302, -1)
                exchange.close()
            }
            server.createContext("/final") { exchange ->
                exchange.sendResponseHeaders(200, 0)
                exchange.close()
            }

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    AndroidSubscriptionFetcher("hwid", RecordingAgeCrypto("unused"))
                        .fetch(
                            url = "http://127.0.0.1:${server.address.port}/start",
                            userAgent = "custom-agent",
                            options = AndroidSubscriptionFetchOptions(),
                        )
                }
            }
        }
    }

    @Test
    fun reads_a_response_larger_than_the_old_desktop_body_cap() {
        withSubscriptionServer { server ->
            val body = "x".repeat(8 * 1024 * 1024 + 1)
            server.createContext("/large") { exchange ->
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }

            val response = runBlocking {
                AndroidSubscriptionFetcher("hwid", RecordingAgeCrypto("unused"))
                    .fetchResponse(
                        url = "http://127.0.0.1:${server.address.port}/large",
                        userAgent = "custom-agent",
                        options = AndroidSubscriptionFetchOptions(),
                    )
            }

            assertEquals(body, response.body)
        }
    }
}

private class RecordingAgeCrypto(
    private val decryptedBody: String,
) : SubscriptionAgeCrypto {
    var publicKeySecret: String? = null
    var decryptSecret: String? = null
    var decryptedText: String? = null

    override fun publicKey(secretKey: String): String {
        publicKeySecret = secretKey
        return "age1test-recipient"
    }

    override fun decryptArmored(text: String, secretKey: String): String {
        decryptedText = text
        decryptSecret = secretKey
        return decryptedBody
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
