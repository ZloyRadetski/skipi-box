// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import features.proxy.server.usecase.importer.WireguardConfParseResult
import features.proxy.server.usecase.importer.parseWireguardConf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AmneziaWG 3.1 (amneziawg-tools 3.1, commit ee0f0a9) adds two boolean `[Interface]` keys:
 * RandomTrailers and DisableCookies. The tools accept `on`/`off` (any letter case) or a number.
 * RandomTrailers lets a peer accept packets longer than the expected size, so a 3.1 server that sends
 * trailers cannot be reached by a client that leaves it off.
 */
class AmneziaWg31Test {
    private val key = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE="
    private val peerKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s="

    private fun server(configure: AmneziaWg.() -> Unit = {}) = AmneziaWg(
        remarks = "v31",
        server = "203.0.113.7",
        port = "51820",
        secretKey = key,
        publicKey = peerKey,
    ).apply(configure)

    private fun settings(configure: AmneziaWg.() -> Unit): JsonObject = server(configure).toXrayOutbound("t").settings

    private fun invalid(configure: AmneziaWg.() -> Unit) = server(configure).validateFull()
        .filter { it.error == ProxyServerValidationError.AmneziaWgBooleanInvalid }

    // ---- defaults --------------------------------------------------------------------------

    @Test
    fun anOlderProfileLeavesBothFlagsUnset() {
        val s = server()
        assertEquals("", s.randomTrailers)
        assertEquals("", s.disableCookies)
        val out = s.toXrayOutbound("t").settings
        assertNull(out["randomTrailers"])
        assertNull(out["disableCookies"])
        assertTrue(s.validateFull().isEmpty())
        assertFalse("randomtrailers" in s.getUrl())
        assertFalse("disablecookies" in s.getUrl())
    }

    // ---- outbound --------------------------------------------------------------------------

    @Test
    fun theFlagsAreJsonBooleansNoMatterHowTheyAreSpelled() {
        listOf("on", "ON", "On", "true", "TRUE", "1").forEach { text ->
            assertEquals(JsonPrimitive(true), settings { randomTrailers = text }["randomTrailers"], text)
            assertEquals(JsonPrimitive(true), settings { disableCookies = text }["disableCookies"], text)
        }
        listOf("off", "OFF", "false", "False", "0").forEach { text ->
            assertEquals(JsonPrimitive(false), settings { randomTrailers = text }["randomTrailers"], text)
            assertEquals(JsonPrimitive(false), settings { disableCookies = text }["disableCookies"], text)
        }
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(JsonPrimitive(true), settings { randomTrailers = " on " }["randomTrailers"])
    }

    // ---- link ------------------------------------------------------------------------------

    @Test
    fun theLinkCarriesBothFlagsAndRebuildsThem() {
        val original = server { randomTrailers = "on"; disableCookies = "off" }
        val parsed = assertIs<AmneziaWg>(ProxyServer.parse(original.getUrl()))
        assertEquals("on", parsed.randomTrailers)
        assertEquals("off", parsed.disableCookies)
    }

    // ---- update / fingerprint / persistence --------------------------------------------------

    @Test
    fun updateFingerprintAndPersistenceKeepBothFlags() {
        val source = server { randomTrailers = "on"; disableCookies = "on" }
        val target = server()
        target.update(source)
        assertEquals(source.copy(), target)

        val base = server().connectionFingerprint()
        val withTrailers = server { randomTrailers = "on" }.connectionFingerprint()
        val withCookies = server { disableCookies = "on" }.connectionFingerprint()
        assertEquals(3, setOf(base, withTrailers, withCookies).size)

        val restored = assertIs<AmneziaWg>(source.encodePersistedProxyServer().decodePersistedProxyServer())
        assertEquals(source, restored)
    }

    // ---- validation ------------------------------------------------------------------------

    @Test
    fun validBooleanSpellingsAreAccepted() {
        listOf("", "on", "off", "ON", "true", "false", "1", "0").forEach { text ->
            assertEquals(emptyList(), invalid { randomTrailers = text; disableCookies = text }, "'$text'")
        }
    }

    @Test
    fun anythingElseIsReportedWithTheNameOfTheField() {
        listOf("yes", "maybe", "2", "-1", "onn", "tru", "1.0").forEach { text ->
            val trailers = invalid { randomTrailers = text }
            assertEquals(listOf("RandomTrailers"), trailers.map { it.values.first() }, "'$text'")
            val cookies = invalid { disableCookies = text }
            assertEquals(listOf("DisableCookies"), cookies.map { it.values.first() }, "'$text'")
        }
    }

    // ---- .conf import ----------------------------------------------------------------------

    private fun importConf(interfaceLines: String): WireguardConfParseResult = parseWireguardConf(
        """
        [Interface]
        PrivateKey = $key
        Address = 10.8.0.2/32
        $interfaceLines

        [Peer]
        PublicKey = $peerKey
        Endpoint = 203.0.113.7:51820
        """.trimIndent(),
    )

    @Test
    fun aConfWithOnlyVersion31KeysIsAnAmneziaProfile() {
        val result = importConf("RandomTrailers = on\nDisableCookies = off")
        val parsed = assertIs<AmneziaWg>(assertIs<WireguardConfParseResult.Imported>(result).server)
        assertEquals("on", parsed.randomTrailers)
        assertEquals("off", parsed.disableCookies)
    }

    @Test
    fun aMalformedBooleanFailsTheImport() {
        assertIs<WireguardConfParseResult.Invalid>(importConf("RandomTrailers = sometimes"))
    }
}
