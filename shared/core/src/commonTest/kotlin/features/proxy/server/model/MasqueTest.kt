// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MasqueTest {

    private fun userPassServer() = Masque(
        remarks = "Edge",
        server = "masque.example.com",
        port = "443",
        user = "alice",
        pass = "s3cret",
        sni = "masque.example.com",
        path = "/.well-known/masque/ip/{target}/{ipproto}/",
        remoteDns = "1.1.1.1, 8.8.8.8",
    )

    private fun warpServer() = Masque(
        remarks = "Warp",
        server = "engage.cloudflareclient.com",
        port = "443",
        warpPrivateKey = "cHJpdmF0ZQ==",
        warpPublicKey = "cHVibGlj",
        warpAddress = "172.16.0.2/32, 2606:4700:110::1/128",
    )

    @Test
    fun outboundCarriesTheMasqueProtocolWithServerAndRemoteDns() {
        val outbound = userPassServer().toXrayOutbound("proxy")

        assertEquals("proxy", outbound.tag)
        assertEquals("masque", outbound.protocol)
        assertEquals("masque.example.com", outbound.settings["address"]?.jsonPrimitive?.content)
        assertEquals(443, outbound.settings["port"]?.jsonPrimitive?.content?.toInt())
        val dns = outbound.settings["remoteDNS"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), dns)
    }

    @Test
    fun outboundOmitsRemoteDnsWhenNoneIsSet() {
        val outbound = userPassServer().copy(remoteDns = "").toXrayOutbound("proxy")

        assertNull(outbound.settings["remoteDNS"])
    }

    @Test
    fun streamSettingsUseTheMasqueNetworkOverTls() {
        val stream = userPassServer().toXrayOutbound("proxy").streamSettings!!

        assertEquals("masque", stream["network"]?.jsonPrimitive?.content)
        assertEquals("tls", stream["security"]?.jsonPrimitive?.content)
        assertEquals("masque.example.com", stream["tlsSettings"]?.jsonObject?.get("serverName")?.jsonPrimitive?.content)
    }

    @Test
    fun userAndPasswordGoIntoMasqueSettings() {
        val masque = userPassServer().toXrayOutbound("proxy").streamSettings!!["masqueSettings"]!!.jsonObject

        assertEquals("alice", masque["user"]?.jsonPrimitive?.content)
        assertEquals("s3cret", masque["pass"]?.jsonPrimitive?.content)
        assertEquals("/.well-known/masque/ip/{target}/{ipproto}/", masque["path"]?.jsonPrimitive?.content)
        assertNull(masque["warp"])
    }

    @Test
    fun blankOptionalFieldsAreNotWrittenToMasqueSettings() {
        val masque = Masque(server = "masque.example.com", user = "alice", pass = "pw")
            .toXrayOutbound("proxy").streamSettings!!["masqueSettings"]!!.jsonObject

        assertNull(masque["host"])
        assertNull(masque["path"])
        assertNull(masque["warp"])
    }

    @Test
    fun warpKeysAndAddressesGoIntoTheWarpObjectWithoutUserOrPassword() {
        val masque = warpServer().toXrayOutbound("proxy").streamSettings!!["masqueSettings"]!!.jsonObject
        val warp = masque["warp"]!!.jsonObject

        assertEquals("cHJpdmF0ZQ==", warp["privateKey"]?.jsonPrimitive?.content)
        assertEquals("cHVibGlj", warp["publicKey"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("172.16.0.2/32", "2606:4700:110::1/128"),
            (warp["address"] as JsonArray).map { it.jsonPrimitive.content },
        )
        assertNull(masque["user"])
        assertNull(masque["pass"])
    }

    @Test
    fun pinnedCertificateFingerprintIsPassedToTls() {
        val pin = "a".repeat(64)
        val tls = userPassServer().copy(pinSHA256 = pin)
            .toXrayOutbound("proxy").streamSettings!!["tlsSettings"]!!.jsonObject

        assertEquals(pin, tls["pinnedPeerCertSha256"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesAMasqueUrl() {
        val server = assertIs<Masque>(
            ProxyServer.parse(
                "masque://alice:s3cret@masque.example.com:8443?sni=front.example.com&host=edge.example.com" +
                    "&path=%2Fmasque%2F%7Btarget%7D%2F%7Bipproto%7D%2F&dns=1.1.1.1,8.8.8.8#My%20Edge",
            ),
        )

        assertEquals("My Edge", server.remarks)
        assertEquals("masque.example.com", server.server)
        assertEquals("8443", server.port)
        assertEquals("alice", server.user)
        assertEquals("s3cret", server.pass)
        assertEquals("front.example.com", server.sni)
        assertEquals("edge.example.com", server.host)
        assertEquals("/masque/{target}/{ipproto}/", server.path)
        assertEquals("1.1.1.1,8.8.8.8", server.remoteDns)
    }

    @Test
    fun parsesAWarpMasqueUrl() {
        val server = assertIs<Masque>(
            ProxyServer.parse(
                "masque://engage.cloudflareclient.com:443?warp-private-key=cHJpdmF0ZQ%3D%3D" +
                    "&warp-public-key=cHVibGlj&warp-address=172.16.0.2%2F32#Warp",
            ),
        )

        assertEquals("", server.user)
        assertEquals("cHJpdmF0ZQ==", server.warpPrivateKey)
        assertEquals("cHVibGlj", server.warpPublicKey)
        assertEquals("172.16.0.2/32", server.warpAddress)
    }

    @Test
    fun urlRoundTripKeepsEveryField() {
        val original = userPassServer().copy(host = "edge.example.com", pinSHA256 = "b".repeat(64))

        val restored = assertIs<Masque>(ProxyServer.parse(original.getUrl()))

        assertEquals(original.copy(remoteDns = "1.1.1.1,8.8.8.8"), restored)
    }

    @Test
    fun warpUrlRoundTripKeepsKeys() {
        val original = warpServer().copy(warpAddress = "172.16.0.2/32,2606:4700:110::1/128")

        val restored = assertIs<Masque>(ProxyServer.parse(original.getUrl()))

        assertEquals(original, restored)
    }

    @Test
    fun jsonPersistenceRoundTrip() {
        val original = userPassServer()

        val text = ProxyServer.json.encodeToString(original)
        val restored = ProxyServer.json.decodeFromString<Masque>(text)

        assertEquals(original, restored)
    }

    @Test
    fun infoShowsTheMasqueProtocolAndEndpoint() {
        val info = userPassServer().getInfo()

        assertEquals("Edge", info.remarks)
        assertEquals("masque.example.com:443", info.address)
        assertEquals("MASQUE", info.protocol)
    }

    @Test
    fun updateCopiesEveryField() {
        val target = Masque()
        val source = warpServer().copy(host = "h.example.com", path = "/p/", pinSHA256 = "c".repeat(64), remoteDns = "9.9.9.9")

        target.update(source)

        assertEquals(source, target)
    }

    @Test
    fun validUserPasswordServerHasNoIssues() {
        assertTrue(userPassServer().validateFull().isEmpty())
    }

    @Test
    fun validWarpServerHasNoIssues() {
        assertTrue(warpServer().validateFull().isEmpty())
    }

    @Test
    fun serverAndPortAreRequired() {
        val issues = Masque(server = "", port = "").validateBasic()

        assertTrue(issues.isNotEmpty())
    }

    @Test
    fun userCannotContainAColon() {
        val issues = userPassServer().copy(user = "al:ice").validateFull()

        assertTrue(issues.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun warpCannotBeCombinedWithUserOrPassword() {
        val issues = warpServer().copy(user = "alice", pass = "pw").validateFull()

        assertTrue(issues.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun warpNeedsBothKeysAndAnAddress() {
        val missingPublic = warpServer().copy(warpPublicKey = "").validateFull()
        val missingAddress = warpServer().copy(warpAddress = "").validateFull()

        assertFalse(missingPublic.isEmpty())
        assertFalse(missingAddress.isEmpty())
    }

    @Test
    fun warpKeysAreEcdsaDerNotWireguardKeysSoOnlyBase64IsChecked() {
        // A real WARP key is a long DER blob; the 44-character WireGuard key shape must not be required.
        val realisticLength = "A".repeat(160)
        val valid = warpServer().copy(warpPrivateKey = realisticLength, warpPublicKey = realisticLength).validateFull()
        val notBase64 = warpServer().copy(warpPrivateKey = "not base64 !!").validateFull()

        assertTrue(valid.isEmpty())
        assertTrue(notBase64.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun warpKeysFollowTheCoreEncodingStandardPaddedBase64OrPem() {
        val pem = "-----BEGIN PRIVATE KEY-----\nMIGHAgEAMBMG\n-----END PRIVATE KEY-----\n"
        val pemPublic = "-----BEGIN PUBLIC KEY-----\nMFkwEwYH\n-----END PUBLIC KEY-----\n"

        assertTrue(warpServer().copy(warpPrivateKey = pem, warpPublicKey = pemPublic).validateFull().isEmpty())
        // the core decodes with base64.StdEncoding: no missing padding, no URL-safe alphabet
        listOf("AAAAAAAAAAAAAAAAAAAAAAA", "AAAA-AAA_AAA", "AAAAAAAAAA=A").forEach { badKey ->
            val found = warpServer().copy(warpPrivateKey = badKey).validateFull()
            assertTrue(
                found.any { it.error == ProxyServerValidationError.UnsupportedValue },
                "'$badKey' must be rejected",
            )
        }
        assertTrue(warpServer().copy(warpPrivateKey = "AAAAAAAAAAAAAAAAAAAAAA==").validateFull().isEmpty())
    }

    @Test
    fun warpAddressAcceptsPlainIpsAndCidrsWithAtMostOnePerFamily() {
        val plain = warpServer().copy(warpAddress = "172.16.0.2, 2606:4700:110::1").validateFull()
        val twoIpv4 = warpServer().copy(warpAddress = "172.16.0.2/32, 10.0.0.2/32").validateFull()
        val garbage = warpServer().copy(warpAddress = "not-an-ip").validateFull()

        assertTrue(plain.isEmpty())
        assertTrue(twoIpv4.any { it.error == ProxyServerValidationError.UnsupportedValue })
        assertTrue(garbage.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun pathMustStartWithASlashAndOnlyUseKnownVariables() {
        val noSlash = userPassServer().copy(path = "masque/").validateFull()
        val unknownVariable = userPassServer().copy(path = "/masque/{foo}/").validateFull()

        assertTrue(noSlash.any { it.error == ProxyServerValidationError.PathMustStartWithSlash })
        assertTrue(unknownVariable.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun remoteDnsEntriesMustBeIpAddresses() {
        val issues = userPassServer().copy(remoteDns = "1.1.1.1, dns.example").validateFull()

        assertTrue(issues.any { it.error == ProxyServerValidationError.UnsupportedValue })
    }

    @Test
    fun fingerprintChangesWithTheCredentialsAndTheEndpoint() {
        val base = userPassServer()

        assertEquals(base.connectionFingerprint(), base.copy(remarks = "Other").connectionFingerprint())
        assertFalse(base.connectionFingerprint() == base.copy(pass = "other").connectionFingerprint())
        assertFalse(base.connectionFingerprint() == base.copy(port = "444").connectionFingerprint())
    }
}
