// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSubscriptionHeaderDecodingTest {
    private fun asJavaDecodesHeader(value: String): String = String(value.encodeToByteArray(), Charsets.ISO_8859_1)

    @Test
    fun raw_utf8_header_bytes_read_as_latin1_are_restored() {
        val title = "🇧🇪 TORVALDS VPN"
        assertEquals(title, decodeRawUtf8HeaderValue(asJavaDecodesHeader(title)))
    }

    @Test
    fun cyrillic_utf8_header_is_restored() {
        assertEquals("Подписка", decodeRawUtf8HeaderValue(asJavaDecodesHeader("Подписка")))
    }

    @Test
    fun ascii_and_already_decoded_values_are_unchanged() {
        assertEquals("Desktop Provider", decodeRawUtf8HeaderValue("Desktop Provider"))
        assertEquals("Подписка 🇧🇪", decodeRawUtf8HeaderValue("Подписка 🇧🇪"))
        assertEquals("base64:0J/QvtC00L/QuNGB0LrQsA==", decodeRawUtf8HeaderValue("base64:0J/QvtC00L/QuNGB0LrQsA=="))
    }

    @Test
    fun genuine_latin1_text_that_is_not_utf8_is_unchanged() {
        assertEquals("café", decodeRawUtf8HeaderValue("café"))
    }

    @Test
    fun fetcher_returns_emoji_profile_title_sent_as_raw_utf8() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/sub") { exchange ->
                exchange.responseHeaders.add("profile-title", asJavaDecodesHeader("🇧🇪 TORVALDS VPN"))
                val body = "vless://123e4567-e89b-42d3-a456-426614174000@example.com:443?security=tls#One"
                exchange.sendResponseHeaders(200, body.encodeToByteArray().size.toLong())
                exchange.responseBody.use { it.write(body.encodeToByteArray()) }
            }
            server.start()
            val update = DesktopSubscriptionFetcher().fetchAndImport("http://127.0.0.1:${server.address.port}/sub")
            assertEquals("🇧🇪 TORVALDS VPN", update.response.headers["profile-title"])
        } finally {
            server.stop(0)
        }
    }
}
