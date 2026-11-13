// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.PasswordAuthentication
import java.net.URI
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class SubscriptionHttpSupportTest {
    @Test
    fun aUnicodeHostIsConvertedToItsAsciiForm() {
        assertEquals("http://xn--e1afmkfd.xn--p1ai/path?x=1", "http://пример.рф/path?x=1".toIdnUrl())
    }

    @Test
    fun anAsciiUrlIsLeftExactlyAsItIs() {
        assertEquals("https://example.com:8443/a?b=c", "https://example.com:8443/a?b=c".toIdnUrl())
    }

    /** What the Authorization header of a request made after [configure] was, as the server saw it (java.net hides it from getRequestProperty). */
    private fun authorizationSeenBy(configure: HttpURLConnection.(serverUrl: String) -> Unit): String? {
        var seen: String? = null
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange ->
            seen = exchange.requestHeaders.getFirst("Authorization")
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/sub"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.configure(url)
            connection.responseCode
        } finally {
            server.stop(0)
        }
        return seen
    }

    @Test
    fun credentialsInAUrlBecomeABasicAuthorizationHeader() {
        val seen = authorizationSeenBy { _ -> setEmbeddedBasicAuth("https://user:p%40ss@example.com/sub") }

        assertEquals("Basic dXNlcjpwQHNz", seen)
    }

    @Test
    fun percentEncodedCredentialsAreDecodedButAPlusStaysAPlus() {
        assertEquals(
            "Basic " + java.util.Base64.getEncoder().encodeToString("u s:p@a+b:c".toByteArray()),
            URI("https://u%20s:p%40a+b%3Ac@example.com/").toBasicAuthHeaderOrNull(),
        )
    }

    @Test
    fun onlyTheHostIsReplacedWhenAUnicodeHostAppearsElsewhereInTheUrl() {
        assertEquals(
            "http://xn--e1afmkfd.xn--p1ai/пример.рф?next=http://пример.рф/",
            "http://пример.рф/пример.рф?next=http://пример.рф/".toIdnUrl(),
        )
        assertEquals(
            "http://пример.рф:pw@xn--e1afmkfd.xn--p1ai/x",
            "http://пример.рф:pw@пример.рф/x".toIdnUrl(),
        )
    }

    @Test
    fun aUrlWithoutCredentialsSetsNoHeader() {
        assertNull(authorizationSeenBy { _ -> setEmbeddedBasicAuth("https://example.com/sub") })
    }

    @Test
    fun aUrlWithOnlyAUserSendsAnEmptyPassword() {
        assertEquals("Basic dXNlcjo=", authorizationSeenBy { _ -> setEmbeddedBasicAuth("https://user@example.com/sub") })
    }

    @Test
    fun theHeaderIsBuiltFromTheRawUserInfoOfTheUri() {
        assertEquals("Basic dXNlcjpwYXNz", URI("https://user:pass@example.com/").toBasicAuthHeaderOrNull())
        assertNull(URI("https://example.com/").toBasicAuthHeaderOrNull())
    }

    @Test
    fun aBodyWithinTheLimitIsReadAsUtf8() {
        assertEquals("привет", "привет".byteInputStream().readUtf8Limited(maxBytes = 12) { error("too large") })
    }

    @Test
    fun aBodyOverTheLimitFailsWithTheCallersErrorBeforeItIsAllRead() {
        val failure = IllegalArgumentException("too large")

        val thrown = assertFailsWith<IllegalArgumentException> {
            ByteArray(100_000).inputStream().readUtf8Limited(maxBytes = 10) { failure }
        }

        assertSame(failure, thrown)
    }

    @Test
    fun credentialsAreOfferedOnlyToTheProxyPortWhileTheBlockRuns() {
        withSocksProxyAuthenticator(port = 10808, username = "u", password = "p") {
            val own = Authenticator.requestPasswordAuthentication("127.0.0.1", InetAddress.getLoopbackAddress(), 10808, "SOCKS5", "", "")
            val other = Authenticator.requestPasswordAuthentication("127.0.0.1", InetAddress.getLoopbackAddress(), 10809, "SOCKS5", "", "")
            assertEquals("u", own?.userName)
            assertEquals("p", String(own?.password ?: CharArray(0)))
            assertNull(other)
        }
        assertNull(Authenticator.requestPasswordAuthentication("127.0.0.1", InetAddress.getLoopbackAddress(), 10808, "SOCKS5", "", ""))
    }

    @Test
    fun aBlankUserLeavesTheAuthenticatorAloneAndThePreviousOneComesBack() {
        val previous = object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication("prev", "x".toCharArray())
        }
        Authenticator.setDefault(previous)
        try {
            withSocksProxyAuthenticator(port = 10808, username = "", password = "") {
                assertEquals("prev", Authenticator.requestPasswordAuthentication("h", null, 1, "p", "", "")?.userName)
            }
            withSocksProxyAuthenticator(port = 10808, username = "u", password = "p") { }
            assertEquals("prev", Authenticator.requestPasswordAuthentication("h", null, 1, "p", "", "")?.userName)
        } finally {
            Authenticator.setDefault(null)
        }
    }

    @Test
    fun aSocksAuthenticationRequestIsRecognisedByItsPortAlone() {
        assertEquals(true, isSocksProxyAuthenticationRequest(requestingPort = 2080, proxyPort = 2080))
        assertEquals(false, isSocksProxyAuthenticationRequest(requestingPort = 2081, proxyPort = 2080))
        assertEquals(false, isSocksProxyAuthenticationRequest(requestingPort = 0, proxyPort = 0))
    }
}

class SocksProxyEndpointTest {
    @Test
    fun theJavaProxyIsASocksOneAtTheEndpointWithIpv6BracketsRemoved() {
        val proxy = SocksProxyEndpoint(host = "[::1]", port = 10808).toJavaProxy()

        assertEquals(java.net.Proxy.Type.SOCKS, proxy.type())
        val address = proxy.address() as java.net.InetSocketAddress
        assertEquals(10808, address.port)
        assertEquals("0:0:0:0:0:0:0:1", address.address.hostAddress)
    }

    @Test
    fun aMissingProxyRunsTheBlockAsItIs() {
        val none: SocksProxyEndpoint? = null

        assertEquals(7, none.withAuthenticator { 7 })
    }

    @Test
    fun aProxyWithCredentialsOffersThemWhileTheBlockRuns() {
        val proxy = SocksProxyEndpoint("127.0.0.1", 10808, "u", "p")

        proxy.withAuthenticator {
            val auth = Authenticator.requestPasswordAuthentication("127.0.0.1", InetAddress.getLoopbackAddress(), 10808, "SOCKS5", "", "")
            assertEquals("u", auth?.userName)
        }
    }
}
