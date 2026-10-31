// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.SubscriptionAgeCrypto
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.time.Duration
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshTransportBindingTest {
    @Test
    fun refreshBindingUsesConfiguredGlobalAgentOnlyWhenStoredAgentIsBlankAndPreservesOptions() = runBlocking {
        val connections = mutableListOf<RecordingConnection>()
        val proxies = mutableListOf<Proxy?>()
        val ageCrypto = RefreshBindingRecordingAgeCrypto()
        val fetcher = DesktopSubscriptionFetcher(
            urlConnectionFactory = { url, proxy ->
                proxies += proxy
                RecordingConnection(url).also(connections::add)
            },
            ageCrypto = ageCrypto,
        )
        val proxy = DesktopSubscriptionSocksProxy(host = "127.0.0.1", port = 10_810)
        val options = DesktopSubscriptionRefreshFetchOptions(
            timeout = Duration.ofSeconds(11),
            proxy = proxy,
            deviceHeaders = mapOf("X-Device-OS" to "Desktop test"),
            ageSecretKey = " secret ",
            fallbackUserAgent = "Configured global agent",
        )

        fetcher.fetchSubscriptionRefreshResponse(
            url = "https://subscription.example/blank-agent",
            storedUserAgent = "  ",
            options = options,
        )
        fetcher.fetchSubscriptionRefreshResponse(
            url = "https://subscription.example/explicit-agent",
            storedUserAgent = "  Subscription-specific agent  ",
            options = options,
        )

        assertEquals(2, connections.size)
        assertEquals(listOf("Configured global agent", "Subscription-specific agent"),
            connections.map { it.requestHeaders.getValue("User-Agent") })
        assertEquals(listOf<java.net.Proxy?>(proxy.toJavaProxy(), proxy.toJavaProxy()), proxies)
        assertTrue(connections.all { it.connectTimeout == 11_000 && it.readTimeout == 11_000 })
        assertTrue(connections.all { it.requestHeaders["X-Device-OS"] == "Desktop test" })
        assertTrue(connections.all { it.requestHeaders["X-Age-Public-Key"] == "age1test-recipient" })
        assertEquals(listOf("secret", "secret"), ageCrypto.publicKeySecrets)
    }
}

private class RecordingConnection(url: URL) : HttpURLConnection(url) {
    private val responseBody = "fixture body".encodeToByteArray()
    val requestHeaders = linkedMapOf<String, String>()

    override fun connect() = Unit
    override fun disconnect() = Unit
    override fun usingProxy(): Boolean = true
    override fun getResponseCode(): Int = 200
    override fun getInputStream(): InputStream = ByteArrayInputStream(responseBody)
    override fun getContentLengthLong(): Long = responseBody.size.toLong()
    override fun setRequestProperty(key: String?, value: String?) {
        if (key != null && value != null) requestHeaders[key] = value
    }
}

private class RefreshBindingRecordingAgeCrypto : SubscriptionAgeCrypto {
    val publicKeySecrets = mutableListOf<String>()

    override fun publicKey(secretKey: String): String {
        publicKeySecrets += secretKey
        return "age1test-recipient"
    }

    override fun decryptArmored(text: String, secretKey: String): String = text
}
