// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NaiveProxyTest {

    private fun full() = NaiveProxy(
        remarks = "Naive Edge",
        server = "naive.example.com",
        port = "8443",
        user = "alice",
        pass = "s3cret",
        quic = true,
        padding = true,
        sni = "front.example.com",
        headers = """{"X-Token":"abc","User-Agent":"skipi"}""",
        concurrency = "4",
    )

    private fun minimal() = NaiveProxy(remarks = "Min", server = "naive.example.com")

    // --- constants / info / transport ---

    @Test
    fun schemeConstants() {
        assertEquals("naive+https", ProxyServerConstants.PROTOCOL_NAIVE_HTTPS)
        assertEquals("naive+quic", ProxyServerConstants.PROTOCOL_NAIVE_QUIC)
    }

    @Test
    fun infoShowsNaiveAndEndpoint() {
        val info = full().getInfo()

        assertEquals("Naive Edge", info.remarks)
        assertEquals("naive.example.com:8443", info.address)
        assertEquals("Naive", info.protocol)
    }

    @Test
    fun transportDisplayFollowsQuic() {
        assertEquals("HTTP/2", full().copy(quic = false).getTransportDisplay())
        assertEquals("HTTP/3", full().copy(quic = true).getTransportDisplay())
    }

    // --- outbound ---

    @Test
    fun outboundCarriesNaiveProtocolAndFullSettings() {
        val outbound = full().toXrayOutbound("proxy")
        val s = outbound.settings

        assertEquals("proxy", outbound.tag)
        assertEquals("naive", outbound.protocol)
        assertEquals("naive.example.com", s["address"]?.jsonPrimitive?.content)
        assertEquals(8443, s["port"]?.jsonPrimitive?.content?.toInt())
        assertFalse(s["port"]!!.jsonPrimitive.isString, "port is a JSON number")
        assertEquals("alice", s["user"]?.jsonPrimitive?.content)
        assertEquals("s3cret", s["pass"]?.jsonPrimitive?.content)
        assertEquals("true", s["quic"]?.jsonPrimitive?.content)
        assertEquals("front.example.com", s["serverName"]?.jsonPrimitive?.content)
        assertEquals("abc", s["headers"]?.jsonObject?.get("X-Token")?.jsonPrimitive?.content)
        assertEquals("skipi", s["headers"]?.jsonObject?.get("User-Agent")?.jsonPrimitive?.content)
        assertEquals(4, s["insecureConcurrency"]?.jsonPrimitive?.content?.toInt())
        assertFalse(s["insecureConcurrency"]!!.jsonPrimitive.isString, "insecureConcurrency is a JSON number")
    }

    @Test
    fun outboundOmitsUnsetOptionalSettingsAndHasNoStreamSettings() {
        val outbound = minimal().toXrayOutbound("proxy")
        val s = outbound.settings

        assertEquals("naive", outbound.protocol)
        assertEquals(443, s["port"]?.jsonPrimitive?.content?.toInt())
        listOf("user", "pass", "quic", "serverName", "headers", "insecureConcurrency").forEach {
            assertNull(s[it], "$it must be omitted")
        }
        assertNull(outbound.streamSettings)
        assertNull(full().toXrayOutbound("proxy").streamSettings)
    }

    @Test
    fun quicIsWrittenOnlyWhenEnabled() {
        assertNull(full().copy(quic = false).toXrayOutbound("p").settings["quic"])
        assertEquals("true", full().copy(quic = true).toXrayOutbound("p").settings["quic"]?.jsonPrimitive?.content)
    }

    @Test
    fun paddingNeverReachesTheOutbound() {
        val withPadding = full().copy(padding = true).toXrayOutbound("p")
        val without = full().copy(padding = false).toXrayOutbound("p")

        assertEquals(without.settings, withPadding.settings)
    }

    // --- link parsing ---

    @Test
    fun parsesAnHttpsLink() {
        val server = assertIs<NaiveProxy>(
            ProxyServer.parse(
                "naive+https://alice:s3cret@naive.example.com:8443?padding=true&sni=front.example.com" +
                    "&insecure-concurrency=4&extra-headers=X-Token%3A%20abc%0D%0AUser-Agent%3A%20skipi#Naive%20Edge",
            ),
        )

        assertEquals("Naive Edge", server.remarks)
        assertEquals("naive.example.com", server.server)
        assertEquals("8443", server.port)
        assertEquals("alice", server.user)
        assertEquals("s3cret", server.pass)
        assertFalse(server.quic)
        assertTrue(server.padding)
        assertEquals("front.example.com", server.sni)
        assertEquals("4", server.concurrency)
        val headers = Json.parseToJsonElement(server.headers).jsonObject
        assertEquals("abc", headers["X-Token"]?.jsonPrimitive?.content)
        assertEquals("skipi", headers["User-Agent"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesAQuicLink() {
        val server = assertIs<NaiveProxy>(ProxyServer.parse("naive+quic://alice:pw@naive.example.com#Q"))

        assertTrue(server.quic)
        assertEquals("443", server.port)
        assertFalse(server.padding)
        assertEquals("", server.sni)
        assertEquals("", server.headers)
        assertEquals("", server.concurrency)
    }

    @Test
    fun userinfoIsPercentDecoded() {
        val server = assertIs<NaiveProxy>(ProxyServer.parse("naive+https://al%40ice:p%3Aw%20x@naive.example.com#N"))

        assertEquals("al@ice", server.user)
        assertEquals("p:w x", server.pass)
    }

    @Test
    fun linkWithoutUserinfoHasNoCredentials() {
        val server = assertIs<NaiveProxy>(ProxyServer.parse("naive+https://naive.example.com#N"))

        assertEquals("", server.user)
        assertEquals("", server.pass)
    }

    @Test
    fun paddingFalseAndUnknownParamsAreIgnored() {
        val server = assertIs<NaiveProxy>(ProxyServer.parse("naive+https://a:b@naive.example.com?padding=false&foo=bar#N"))

        assertFalse(server.padding)
    }

    @Test
    fun malformedExtraHeaderPairsAreSkipped() {
        val server = assertIs<NaiveProxy>(
            ProxyServer.parse(
                "naive+https://a:b@naive.example.com?extra-headers=NoColonHere%0D%0AX-Ok%3A%20%20v1%20%0D%0A%3A%20novalue#N",
            ),
        )
        val headers = Json.parseToJsonElement(server.headers).jsonObject

        assertEquals(setOf("X-Ok"), headers.keys)
        assertEquals("v1", headers["X-Ok"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesAnIpv6Host() {
        val server = assertIs<NaiveProxy>(ProxyServer.parse("naive+https://a:b@[2001:db8::1]:8443#V6"))

        assertEquals("2001:db8::1", server.server)
        assertEquals("8443", server.port)
    }

    // --- link building ---

    @Test
    fun urlUsesTheSchemeOfTheTransport() {
        assertTrue(full().copy(quic = false).getUrl().startsWith("naive+https://"))
        assertTrue(full().copy(quic = true).getUrl().startsWith("naive+quic://"))
    }

    @Test
    fun minimalUrlOmitsOptionalParts() {
        val url = minimal().getUrl()

        assertTrue(url.startsWith("naive+https://naive.example.com:443"), url)
        assertFalse(url.contains("@"), url)
        assertFalse(url.contains("padding"), url)
        assertFalse(url.contains("sni="), url)
        assertFalse(url.contains("insecure-concurrency"), url)
        assertFalse(url.contains("extra-headers"), url)
    }

    @Test
    fun fullUrlCarriesTheOptionalParts() {
        val url = full().getUrl()

        assertTrue(url.contains("alice:s3cret@"), url)
        assertTrue(url.contains("padding=true"), url)
        assertTrue(url.contains("sni=front.example.com"), url)
        assertTrue(url.contains("insecure-concurrency=4"), url)
        assertTrue(url.contains("extra-headers="), url)
        assertTrue(url.contains("%0D%0A"), "headers are CRLF separated: $url")
    }

    @Test
    fun urlRoundTripKeepsEveryField() {
        val original = full()

        assertEquals(original, ProxyServer.parse(original.getUrl()))
        assertEquals(original.copy(quic = false), ProxyServer.parse(original.copy(quic = false).getUrl()))
    }

    @Test
    fun minimalUrlRoundTrip() {
        val original = minimal()

        assertEquals(original, ProxyServer.parse(original.getUrl()))
    }

    @Test
    fun ipv6UrlRoundTrip() {
        val original = full().copy(server = "2001:db8::1")

        assertEquals(original, ProxyServer.parse(original.getUrl()))
    }

    // --- persistence ---

    @Test
    fun persistedProtocolNameIsNaiveAndRoundTrips() {
        val original = full()

        val restored = assertIs<NaiveProxy>(original.encodePersistedProxyServer().decodePersistedProxyServer())

        assertEquals("naive", original.toPersistedProxyServer().protocol)
        assertEquals(original, restored)
    }

    // --- endpoint ---

    @Test
    fun endpointAndServerHostBehaveLikeMasque() {
        assertEquals(ProxyServerEndpoint("naive.example.com", 8443), full().connectionEndpointOrNull())
        assertNull(NaiveProxy(server = "", port = "443").connectionEndpointOrNull())
        assertNull(NaiveProxy(server = "naive.example.com", port = "0").connectionEndpointOrNull())
        assertNull(NaiveProxy(server = "naive.example.com", port = "65536").connectionEndpointOrNull())
        assertEquals("naive.example.com", full().serverHost())
    }

    // --- update / fingerprint ---

    @Test
    fun updateCopiesEveryField() {
        val target = NaiveProxy()

        target.update(full())

        assertEquals(full(), target)
    }

    @Test
    fun fingerprintIsEqualForEqualServersAndIgnoresRemarksAndPadding() {
        val base = full()

        assertEquals(base.connectionFingerprint(), base.copy().connectionFingerprint())
        assertEquals(base.connectionFingerprint(), base.copy(remarks = "Other").connectionFingerprint())
        assertEquals(base.connectionFingerprint(), base.copy(padding = !base.padding).connectionFingerprint())
    }

    @Test
    fun fingerprintChangesWithEveryConnectionField() {
        val base = full()
        listOf(
            base.copy(server = "other.example.com"),
            base.copy(port = "444"),
            base.copy(user = "bob"),
            base.copy(pass = "other"),
            base.copy(quic = !base.quic),
            base.copy(sni = "other.example.com"),
            base.copy(headers = """{"X-Token":"zzz"}"""),
            base.copy(concurrency = "8"),
        ).forEach { changed ->
            assertNotEquals(base.connectionFingerprint(), changed.connectionFingerprint(), "$changed")
        }
    }

    // --- validation ---

    @Test
    fun validServersHaveNoIssues() {
        assertTrue(full().validateFull().isEmpty())
        assertTrue(minimal().validateFull().isEmpty())
    }

    @Test
    fun basicValidationChecksTheCommonFields() {
        assertTrue(NaiveProxy(server = "", port = "").validateBasic().isNotEmpty())
        assertTrue(minimal().validateBasic().isEmpty())
    }

    @Test
    fun userAndPasswordComeAsAPair() {
        assertTrue(minimal().copy(user = "alice").validateFull().any { it.error == ProxyServerValidationError.PasswordRequiredForUsername })
        assertTrue(minimal().copy(pass = "pw").validateFull().any { it.error == ProxyServerValidationError.UsernameRequiredForPassword })
        assertTrue(minimal().copy(user = "alice", pass = "pw").validateFull().isEmpty())
    }

    @Test
    fun userCannotContainAColon() {
        assertTrue(full().copy(user = "al:ice").validateFull().any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun headersMustBeAJsonObjectWhenSet() {
        listOf("[1]", "not json").forEach { headers ->
            assertTrue(
                full().copy(headers = headers).validateFull().any { it.error == ProxyServerValidationError.JsonObjectRequired },
                "headers '$headers' must be rejected",
            )
        }
        assertTrue(full().copy(headers = "").validateFull().isEmpty())
        assertTrue(full().copy(headers = """{"X-Token":"abc"}""").validateFull().isEmpty())
    }

    @Test
    fun concurrencyMustBeAnIntegerFromOneToSixtyFour() {
        listOf("0", "65", "-1", "abc", "1.5").forEach { value ->
            assertTrue(
                full().copy(concurrency = value).validateFull().any { it.error == ProxyServerValidationError.UnsupportedValue },
                "'$value' must be rejected",
            )
        }
        listOf("", "1", "4", "64").forEach { value ->
            assertTrue(full().copy(concurrency = value).validateFull().isEmpty(), "'$value' must be accepted")
        }
    }
}
