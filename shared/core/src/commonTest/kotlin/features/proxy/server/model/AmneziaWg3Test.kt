// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import features.proxy.server.usecase.importer.WireguardConfParseResult
import features.proxy.server.usecase.importer.parseWireguardConf
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AmneziaWG 3.0 (amneziawg-tools 3.0, commit d09ecc3) adds to `[Interface]`:
 * HeaderProtectionKey, ContentPaddingAddition, RekeyAfterTime, RekeyTimeout, RejectAfterTime,
 * KeepaliveTimeout and MaxHandshakeAttempts, and lets `[Peer] PersistentKeepalive` be a range.
 * Every range is `N` or `N-M`, 0..65535, lower <= upper.
 */
class AmneziaWg3Test {
    private val key = "aGVsbG8gd29ybGQgdGhpcyBpcyBhIHZhbGlkIGtleSE="
    private val peerKey = "YW5vdGhlciB2YWxpZCBrZXkgZm9yIHRlc3Rpbmcgb2s="
    private val headerKey = "c29tZSBoZWFkZXIgcHJvdGVjdGlvbiBrZXkgb2shISE="

    private fun server(configure: AmneziaWg.() -> Unit = {}) = AmneziaWg(
        remarks = "v3",
        server = "203.0.113.7",
        port = "51820",
        secretKey = key,
        publicKey = peerKey,
    ).apply(configure)

    private fun issues(configure: AmneziaWg.() -> Unit) = server(configure).validateFull()

    private fun settings(configure: AmneziaWg.() -> Unit): JsonObject =
        server(configure).toXrayOutbound("t").settings

    private fun JsonObject.peer(): JsonObject = (this["peers"] as JsonArray).single().jsonObject

    // ---- defaults --------------------------------------------------------------------------

    @Test
    fun anAwg2ProfileHasNoVersion3Fields() {
        val s = server()
        assertEquals("", s.headerProtectionKey)
        assertEquals("", s.contentPaddingAddition)
        assertEquals("", s.rekeyAfterTime)
        assertEquals("", s.rekeyTimeout)
        assertEquals("", s.rejectAfterTime)
        assertEquals("", s.keepaliveTimeout)
        assertEquals("", s.maxHandshakeAttempts)
        assertEquals("", s.persistentKeepalive)
        val out = s.toXrayOutbound("t").settings
        listOf(
            "headerProtectionKey", "contentPaddingAddition", "rekeyAfterTime", "rekeyTimeout",
            "rejectAfterTime", "keepaliveTimeout", "maxHandshakeAttempts",
        ).forEach { assertNull(out[it], it) }
        assertNull(out.peer()["keepAlive"])
        assertTrue(s.validateFull().isEmpty())
    }

    // ---- outbound --------------------------------------------------------------------------

    @Test
    fun aPlainNumberIsAJsonNumberAndARangeIsAString() {
        val out = settings {
            headerProtectionKey = headerKey
            contentPaddingAddition = "16-64"
            rekeyAfterTime = "120"
            rekeyTimeout = "5-10"
            rejectAfterTime = "180"
            keepaliveTimeout = "10"
            maxHandshakeAttempts = "20-90"
            persistentKeepalive = "20-30"
        }
        assertEquals(headerKey, out["headerProtectionKey"]!!.jsonPrimitive.content)
        assertEquals("16-64", out["contentPaddingAddition"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(120), out["rekeyAfterTime"])
        assertEquals("5-10", out["rekeyTimeout"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(180), out["rejectAfterTime"])
        assertEquals(JsonPrimitive(10), out["keepaliveTimeout"])
        assertEquals("20-90", out["maxHandshakeAttempts"]!!.jsonPrimitive.content)
        assertEquals("20-30", out.peer()["keepAlive"]!!.jsonPrimitive.content)
    }

    @Test
    fun aPlainKeepaliveIsANumberAndZeroOrOffIsOmitted() {
        assertEquals(JsonPrimitive(25), settings { persistentKeepalive = "25" }.peer()["keepAlive"])
        assertNull(settings { persistentKeepalive = "0" }.peer()["keepAlive"])
        assertNull(settings { persistentKeepalive = "off" }.peer()["keepAlive"])
        assertNull(settings { contentPaddingAddition = "0" }["contentPaddingAddition"])
    }

    // ---- link ------------------------------------------------------------------------------

    @Test
    fun theLinkCarriesEveryVersion3FieldAndRebuildsIt() {
        val original = server {
            headerProtectionKey = headerKey
            contentPaddingAddition = "16-64"
            rekeyAfterTime = "120"
            rekeyTimeout = "5-10"
            rejectAfterTime = "180"
            keepaliveTimeout = "10"
            maxHandshakeAttempts = "20-90"
            persistentKeepalive = "20-30"
        }
        val parsed = assertIs<AmneziaWg>(ProxyServer.parse(original.getUrl()))
        assertEquals(headerKey, parsed.headerProtectionKey)
        assertEquals("16-64", parsed.contentPaddingAddition)
        assertEquals("120", parsed.rekeyAfterTime)
        assertEquals("5-10", parsed.rekeyTimeout)
        assertEquals("180", parsed.rejectAfterTime)
        assertEquals("10", parsed.keepaliveTimeout)
        assertEquals("20-90", parsed.maxHandshakeAttempts)
        assertEquals("20-30", parsed.persistentKeepalive)
    }

    @Test
    fun aRawPlusInTheHeaderProtectionKeyIsNotLost() {
        val plusKey = "a+b/".repeat(10) + "c+8="
        val link = "awg://${key.replace("=", "%3D")}@203.0.113.7:51820?publickey=${peerKey.replace("=", "%3D")}&headerprotectionkey=$plusKey#n"
        assertEquals(plusKey, assertIs<AmneziaWg>(ProxyServer.parse(link)).headerProtectionKey)
    }

    @Test
    fun anUnsetVersion3FieldIsNotWrittenToTheLink() {
        val url = server().getUrl()
        listOf("headerprotectionkey", "contentpaddingaddition", "rekey", "rejectaftertime", "keepalivetimeout", "maxhandshake")
            .forEach { assertFalse(it in url, it) }
    }

    // ---- update / fingerprint / persistence --------------------------------------------------

    @Test
    fun updateCopiesTheVersion3Fields() {
        val source = server {
            headerProtectionKey = headerKey
            contentPaddingAddition = "8"
            rekeyAfterTime = "1"
            rekeyTimeout = "2"
            rejectAfterTime = "3"
            keepaliveTimeout = "4"
            maxHandshakeAttempts = "5"
            persistentKeepalive = "6"
        }
        val target = server()
        target.update(source)
        assertEquals(source.copy(), target)
    }

    @Test
    fun eachVersion3FieldChangesTheRuntimeFingerprint() {
        val base = server().connectionFingerprint()
        val changed = listOf<AmneziaWg.() -> Unit>(
            { headerProtectionKey = headerKey },
            { contentPaddingAddition = "8" },
            { rekeyAfterTime = "1" },
            { rekeyTimeout = "2" },
            { rejectAfterTime = "3" },
            { keepaliveTimeout = "4" },
            { maxHandshakeAttempts = "5" },
            { persistentKeepalive = "6" },
        ).map { server(it).connectionFingerprint() }
        assertEquals(changed.size, changed.toSet().size)
        assertFalse(base in changed)
    }

    @Test
    fun persistenceRoundTripKeepsTheVersion3Fields() {
        val original = server {
            headerProtectionKey = headerKey
            contentPaddingAddition = "16-64"
            persistentKeepalive = "20-30"
            maxHandshakeAttempts = "9"
        }
        val restored = assertIs<AmneziaWg>(original.encodePersistedProxyServer().decodePersistedProxyServer())
        assertEquals(original, restored)
    }

    // ---- validation ------------------------------------------------------------------------

    @Test
    fun validVersion3ValuesAreAccepted() {
        val result = issues {
            headerProtectionKey = headerKey
            s1 = "12"; s2 = "12"; s3 = "12"; s4 = "12"
            contentPaddingAddition = "0-65535"
            rekeyAfterTime = "120"
            rekeyTimeout = "5-10"
            rejectAfterTime = "180"
            keepaliveTimeout = "10"
            maxHandshakeAttempts = "20-90"
            persistentKeepalive = "off"
        }
        assertEquals(emptyList(), result)
        assertEquals(emptyList(), issues { persistentKeepalive = "15-25" })
    }

    @Test
    fun aBadRangeIsReportedWithTheNameOfTheField() {
        val fields = mapOf<String, (AmneziaWg, String) -> Unit>(
            "ContentPaddingAddition" to { s, v -> s.contentPaddingAddition = v },
            "RekeyAfterTime" to { s, v -> s.rekeyAfterTime = v },
            "RekeyTimeout" to { s, v -> s.rekeyTimeout = v },
            "RejectAfterTime" to { s, v -> s.rejectAfterTime = v },
            "KeepaliveTimeout" to { s, v -> s.keepaliveTimeout = v },
            "MaxHandshakeAttempts" to { s, v -> s.maxHandshakeAttempts = v },
            "PersistentKeepalive" to { s, v -> s.persistentKeepalive = v },
        )
        fields.forEach { (name, set) ->
            listOf("abc", "-5", "10-", "5-3", "65536", "1-65536", "1-2-3", " 5", "5 ", "1.5").forEach { bad ->
                val found = issues { set(this, bad) }.filter { it.error == ProxyServerValidationError.AmneziaWgRangeInvalid }
                assertEquals(1, found.size, "$name='$bad' must be rejected")
                assertEquals(name, found.single().values.first())
            }
        }
    }

    @Test
    fun aHeaderProtectionKeyNeedsEveryPaddingOfAtLeast12() {
        // The native runner refuses the key unless S1..S4 each leave room for its 12-byte nonce.
        val ok = issues { headerProtectionKey = headerKey; s1 = "12"; s2 = "12"; s3 = "12"; s4 = "12" }
        assertEquals(emptyList(), ok)
        listOf("S1", "S2", "S3", "S4").forEach { name ->
            val found = issues {
                headerProtectionKey = headerKey
                s1 = "20"; s2 = "20"; s3 = "20"; s4 = "20"
                when (name) { "S1" -> s1 = "11"; "S2" -> s2 = ""; "S3" -> s3 = "0"; else -> s4 = "5" }
            }.filter { it.error == ProxyServerValidationError.AmneziaWgHeaderProtectionPaddingTooSmall }
            assertEquals(1, found.size, name)
            assertEquals(name, found.single().values.first())
        }
        // Without the key small or empty paddings stay valid.
        assertEquals(emptyList(), issues { s1 = "1"; s3 = ""; s4 = "" })
    }

    @Test
    fun aMalformedHeaderProtectionKeyIsRejected() {
        val found = issues { headerProtectionKey = "not-a-key" }
        assertTrue(found.isNotEmpty())
    }

    // ---- .conf import ----------------------------------------------------------------------

    private fun importConf(text: String): AmneziaWg {
        val result = parseWireguardConf(text.trimIndent())
        return assertIs<AmneziaWg>(assertIs<WireguardConfParseResult.Imported>(result).server)
    }

    @Test
    fun aConfWithOnlyVersion3KeysIsAnAmneziaProfile() {
        val parsed = importConf(
            """
            [Interface]
            PrivateKey = $key
            Address = 10.8.0.2/32
            S1 = 15
            S2 = 30
            S3 = 20
            S4 = 25
            HeaderProtectionKey = $headerKey
            ContentPaddingAddition = 16-64
            RekeyAfterTime = 120
            RekeyTimeout = 5-10
            RejectAfterTime = 180
            KeepaliveTimeout = 10
            MaxHandshakeAttempts = 20-90

            [Peer]
            PublicKey = $peerKey
            Endpoint = 203.0.113.7:51820
            PersistentKeepalive = 20-30
            """,
        )
        assertEquals(headerKey, parsed.headerProtectionKey)
        assertEquals("16-64", parsed.contentPaddingAddition)
        assertEquals("120", parsed.rekeyAfterTime)
        assertEquals("5-10", parsed.rekeyTimeout)
        assertEquals("180", parsed.rejectAfterTime)
        assertEquals("10", parsed.keepaliveTimeout)
        assertEquals("20-90", parsed.maxHandshakeAttempts)
        assertEquals("20-30", parsed.persistentKeepalive)
    }

    @Test
    fun keepaliveOffAndNumbersInAnAwgConfAreRead() {
        val base = """
            [Interface]
            PrivateKey = $key
            Address = 10.8.0.2/32
            Jc = 4

            [Peer]
            PublicKey = $peerKey
            Endpoint = 203.0.113.7:51820
        """
        assertEquals("25", importConf("$base\nPersistentKeepalive = 25").persistentKeepalive)
        assertEquals("", importConf("$base\nPersistentKeepalive = off").persistentKeepalive)
    }

    @Test
    fun aPlainWireguardConfWithKeepaliveStaysPlainWireguard() {
        val result = parseWireguardConf(
            """
            [Interface]
            PrivateKey = $key
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = $peerKey
            Endpoint = 203.0.113.7:51820
            PersistentKeepalive = 25
            """.trimIndent(),
        )
        assertIs<Wireguard>(assertIs<WireguardConfParseResult.Imported>(result).server)
    }

    @Test
    fun anInvalidVersion3ValueFailsTheImport() {
        val result = parseWireguardConf(
            """
            [Interface]
            PrivateKey = $key
            Address = 10.8.0.2/32
            RekeyTimeout = 9-3

            [Peer]
            PublicKey = $peerKey
            Endpoint = 203.0.113.7:51820
            """.trimIndent(),
        )
        assertIs<WireguardConfParseResult.Invalid>(result)
    }
}
