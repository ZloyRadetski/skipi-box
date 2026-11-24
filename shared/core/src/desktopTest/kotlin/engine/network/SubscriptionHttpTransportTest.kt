// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import features.subscription.SubscriptionHttpException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionHttpTransportTest {
    private class Reply(
        val status: Int = 200,
        val body: ByteArray = ByteArray(0),
        val headers: Map<String, String> = emptyMap(),
        val declaredLength: Long = body.size.toLong(),
    )

    private class FakeConnection(url: URL, private val reply: Reply) : HttpURLConnection(url) {
        val sent = linkedMapOf<String, String>()
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun setRequestProperty(key: String?, value: String?) {
            if (key != null && value != null) sent[key] = value
        }
        override fun getResponseCode() = reply.status
        override fun getHeaderField(name: String?): String? =
            reply.headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        override fun getHeaderFields(): Map<String?, List<String>> =
            reply.headers.mapValues { listOf(it.value) } + (null to listOf("HTTP/1.1 ${reply.status}"))
        override fun getContentLengthLong() = reply.declaredLength
        override fun getInputStream(): InputStream = ByteArrayInputStream(reply.body)
        override fun getErrorStream(): InputStream = ByteArrayInputStream(reply.body)
    }

    private class Recorder(private val replies: List<Reply>) {
        val connections = mutableListOf<FakeConnection>()
        val transport = SubscriptionHttpTransport { url, _ ->
            FakeConnection(url, replies[connections.size]).also(connections::add)
        }
    }

    private fun request(url: String, block: SubscriptionHttpRequestBuilder.() -> Unit = {}): SubscriptionHttpRequest =
        SubscriptionHttpRequestBuilder(url).apply(block).build()

    private class SubscriptionHttpRequestBuilder(val url: String) {
        var maxBodyBytes = 1024
        var maxErrorBodyBytes = 16
        var maxRedirects = 2
        var urlGuard: ((URI) -> URI)? = null
        fun build() = SubscriptionHttpRequest(
            url = url,
            userAgent = "agent",
            timeoutMillis = 5_000,
            identityHeaders = mapOf("x-hwid" to "secret-hwid", "User-Agent" to "ignored", "x-blank" to " "),
            agePublicKey = "age1key",
            maxBodyBytes = maxBodyBytes,
            maxErrorBodyBytes = maxErrorBodyBytes,
            maxRedirects = maxRedirects,
            urlGuard = urlGuard,
        )
    }

    private fun redirect(location: String) = Reply(status = 302, headers = mapOf("Location" to location))

    @Test
    fun aSameSiteRedirectKeepsTheIdentityAndACrossSiteOneDropsIt() {
        val recorder = Recorder(
            listOf(
                redirect("/next"),
                redirect("https://other.example/final"),
                Reply(body = "ok".toByteArray()),
            ),
        )

        val response = recorder.transport.fetch(request("https://user:pw@sub.example/start#frag"))

        assertEquals("ok", response.body)
        val (first, second, third) = recorder.connections
        assertEquals("secret-hwid", first.sent["x-hwid"])
        assertEquals("age1key", first.sent["X-Age-Public-Key"])
        assertTrue("Authorization" in first.sent)
        assertEquals("agent", first.sent["User-Agent"])
        assertFalse("x-blank" in first.sent)
        assertEquals("/start", first.url.path)
        assertEquals("secret-hwid", second.sent["x-hwid"])
        assertFalse("x-hwid" in third.sent)
        assertFalse("X-Age-Public-Key" in third.sent)
        assertFalse("Authorization" in third.sent)
    }

    @Test
    fun moreRedirectsThanTheLimitFail() {
        val recorder = Recorder(List(4) { redirect("/again") })

        val error = assertFailsWith<IllegalStateException> { recorder.transport.fetch(request("https://sub.example/a")) }

        assertTrue("2 redirects" in error.message.orEmpty())
        assertEquals(3, recorder.connections.size)
    }

    @Test
    fun aDowngradeToHttpIsRefused() {
        val recorder = Recorder(listOf(redirect("http://sub.example/plain")))

        assertFailsWith<IllegalStateException> { recorder.transport.fetch(request("https://sub.example/a")) }
    }

    @Test
    fun aBodyOverTheLimitIsRefusedByItsDeclaredLengthOrWhileReading() {
        val declared = Recorder(listOf(Reply(body = ByteArray(10), declaredLength = 5_000)))
        assertFailsWith<IllegalStateException> { declared.transport.fetch(request("https://sub.example/a")) }

        val streamed = Recorder(listOf(Reply(body = ByteArray(2_000), declaredLength = -1)))
        assertFailsWith<IllegalStateException> { streamed.transport.fetch(request("https://sub.example/a")) }
    }

    @Test
    fun anErrorBodyIsCappedSmallerThanAnOrdinaryBody() {
        val small = Recorder(listOf(Reply(status = 403, body = """{"message":"Device limit"}""".toByteArray().copyOf(15))))
        val smallError = assertFailsWith<SubscriptionHttpException> { small.transport.fetch(request("https://sub.example/a")) }
        assertEquals(403, smallError.statusCode)

        val large = Recorder(listOf(Reply(status = 500, body = ByteArray(100) { 'x'.code.toByte() })))
        val largeError = assertFailsWith<SubscriptionHttpException> { large.transport.fetch(request("https://sub.example/a")) }
        assertEquals(500, largeError.statusCode)
        assertNull(largeError.responseBody)
    }

    @Test
    fun headerNamesAreLoweredAndRawUtf8ValuesDecoded() {
        val title = "Подписка"
        val recorder = Recorder(
            listOf(
                Reply(
                    body = "x".toByteArray(),
                    headers = mapOf("Profile-Title" to String(title.toByteArray(), Charsets.ISO_8859_1)),
                ),
            ),
        )

        val response = recorder.transport.fetch(request("https://sub.example/a"))

        assertEquals(title, response.headers["profile-title"])
    }

    @Test
    fun theGuardChecksEveryHopAndStopsARedirectToAPrivateAddress() {
        val recorder = Recorder(listOf(redirect("https://internal.example/x"), Reply(body = "no".toByteArray())))
        val seen = mutableListOf<String>()
        val guard: (URI) -> URI = { uri ->
            seen += uri.host
            requireSafeAutomaticSubscriptionResourceUrl(uri.toString(), resolveAddresses = { host ->
                arrayOf(InetAddress.getByName(if (host == "provider.example") "8.8.8.8" else "10.0.0.5"))
            })
        }

        assertFailsWith<IllegalArgumentException> {
            recorder.transport.fetch(request("https://provider.example/list") { urlGuard = guard })
        }

        assertEquals(listOf("provider.example", "internal.example"), seen)
        assertEquals(1, recorder.connections.size)
    }

    @Test
    fun anInvalidUrlIsRefusedBeforeConnecting() {
        val recorder = Recorder(emptyList())

        assertFailsWith<IllegalArgumentException> { recorder.transport.fetch(request("file:///etc/passwd")) }

        assertEquals(0, recorder.connections.size)
    }

    @Test
    fun theSafetyGuardRefusesLocalAndMetadataHosts() {
        listOf("http://127.0.0.1/p", "http://localhost/p", "http://169.254.169.254/x", "http://[::1]/p", "http://nas.local/p")
            .forEach { url -> assertFailsWith<IllegalArgumentException>(url) { requireSafeAutomaticSubscriptionResourceUrl(url) } }
        assertEquals("8.8.8.8", requireSafeAutomaticSubscriptionResourceUrl("https://8.8.8.8/list").host)
    }
}
