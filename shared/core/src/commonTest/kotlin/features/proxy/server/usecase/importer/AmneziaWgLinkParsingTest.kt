// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.AmneziaWg
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Wireguard
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioural contract for AmneziaWG links and `.conf` documents: what the user pastes
 * or scans must become the exact profile the native runner is later given.
 */
class AmneziaWgLinkParsingTest {
    // 32-byte keys whose Base64 form contains '+', '/' and '=' - the characters a URL can break.
    private val privateKey = "a+b/".repeat(10) + "c+8="
    private val publicKey = "d+e/".repeat(10) + "f+8="
    private val presharedKey = "g+h/".repeat(10) + "i+8="

    private fun enc(value: String) = value
        .replace("+", "%2B").replace("/", "%2F").replace("=", "%3D")
        .replace("<", "%3C").replace(">", "%3E").replace(" ", "%20").replace(",", "%2C")

    private fun parse(link: String): AmneziaWg = assertIs<AmneziaWg>(ProxyServer.parse(link))

    private fun importAll(text: String): List<ProxyServer<*>> = importProxyServersFromUrls(text).servers

    // ---- schemes ---------------------------------------------------------------------------

    @Test
    fun bothSchemesAndAnyLetterCaseAreAccepted() {
        listOf("awg", "amneziawg", "AWG", "AmneziaWG").forEach { scheme ->
            val server = parse("$scheme://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}#n")
            assertEquals("203.0.113.7", server.server, scheme)
            assertEquals(privateKey, server.secretKey, scheme)
        }
    }

    // ---- keys ------------------------------------------------------------------------------

    @Test
    fun percentEncodedKeysKeepTheirPlusSlashAndEquals() {
        val server = parse(
            "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&presharedkey=${enc(presharedKey)}#n",
        )
        assertEquals(privateKey, server.secretKey)
        assertEquals(publicKey, server.publicKey)
        assertEquals(presharedKey, server.preSharedKey)
    }

    @Test
    fun aRawPlusInTheQueryIsNotTurnedIntoASpace() {
        // Many generators leave '+' and '=' unescaped in the query; a Base64 key must survive.
        val server = parse("awg://${enc(privateKey)}@203.0.113.7:51820?publickey=$publicKey&presharedkey=$presharedKey#n")
        assertEquals(publicKey, server.publicKey)
        assertEquals(presharedKey, server.preSharedKey)
    }

    @Test
    fun aKeyWithUnescapedPlusInTheUserInfoSurvives() {
        val key = "a+b".repeat(14) + "c+8="
        val server = parse("awg://$key@203.0.113.7:51820?publickey=${enc(publicKey)}#n")
        assertEquals(key, server.secretKey)
    }

    // ---- hosts and ports -------------------------------------------------------------------

    @Test
    fun ipv6HostIsStoredWithoutBracketsAndRebuiltWithThem() {
        val server = parse("awg://${enc(privateKey)}@[2001:db8::1]:4500?publickey=${enc(publicKey)}#v6")
        assertEquals("2001:db8::1", server.server)
        assertEquals("4500", server.port)
        val endpoint = server.toXrayOutbound("t").settings["peers"].toString()
        assertTrue("[2001:db8::1]:4500" in endpoint, endpoint)
    }

    @Test
    fun domainHostAndNonDefaultPortAreKept() {
        val server = parse("awg://${enc(privateKey)}@vpn.example.org:443?publickey=${enc(publicKey)}#d")
        assertEquals("vpn.example.org", server.server)
        assertEquals("443", server.port)
    }

    @Test
    fun aLinkWithoutPortFallsBackToTheWireguardDefaultPort() {
        val server = parse("awg://${enc(privateKey)}@203.0.113.7?publickey=${enc(publicKey)}#np")
        assertEquals("51820", server.port)
    }

    // ---- remarks ---------------------------------------------------------------------------

    @Test
    fun remarksAreDecodedAndKeepPlusSigns() {
        val server = parse("awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}#%D0%A1%D0%B5%D1%80%D0%B2%D0%B5%D1%80%20A+B")
        assertEquals("Сервер A+B", server.remarks)
    }

    @Test
    fun aMissingFragmentGivesTheStandardPlaceholderRemarks() {
        val server = parse("awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}")
        assertEquals("none", server.remarks)
    }

    // ---- tunnel parameters -----------------------------------------------------------------

    @Test
    fun addressMtuAndReservedCanBeListsAndEscaped() {
        val server = parse(
            "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}" +
                "&address=${enc("10.8.0.2/32,fd00::2/128")}&mtu=1280&reserved=1,2,3#n",
        )
        assertEquals("10.8.0.2/32,fd00::2/128", server.address)
        assertEquals("1280", server.mtu)
        assertEquals("1,2,3", server.reserved)
        val settings = server.toXrayOutbound("t").settings
        assertEquals(listOf("10.8.0.2/32", "fd00::2/128"), settings["address"].toString().filter { it != '[' && it != ']' && it != '"' }.split(','))
    }

    @Test
    fun everyObfuscationFieldOfAwg2IsReadFromTheLink() {
        val server = parse(
            "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}" +
                "&jc=6&jmin=10&jmax=50&s1=11&s2=22&s3=33&s4=44" +
                "&h1=1000-2000&h2=3000-4000&h3=5000-6000&h4=7000-8000" +
                "&i1=${enc("<b 0xc0ffee><r 16><t>")}&i2=${enc("<r 8>")}&i3=${enc("<rc 4>")}&i4=${enc("<rd 4>")}&i5=${enc("<dz 2>")}#awg2",
        )
        assertEquals(listOf("6", "10", "50", "11", "22", "33", "44"), listOf(server.jc, server.jmin, server.jmax, server.s1, server.s2, server.s3, server.s4))
        assertEquals(listOf("1000-2000", "3000-4000", "5000-6000", "7000-8000"), listOf(server.h1, server.h2, server.h3, server.h4))
        assertEquals(listOf("<b 0xc0ffee><r 16><t>", "<r 8>", "<rc 4>", "<rd 4>", "<dz 2>"), listOf(server.i1, server.i2, server.i3, server.i4, server.i5))
        assertTrue(server.validateFull().isEmpty(), server.validateFull().toString())
    }

    @Test
    fun headersGoToTheRunnerAsNumbersOrRangeStrings() {
        val server = parse(
            "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&h1=4294967295&h2=5-9&h3=123&h4=0#h",
        )
        val settings = server.toXrayOutbound("t").settings
        assertEquals(JsonPrimitive(4294967295L), settings["h1"])
        assertEquals("5-9", settings["h2"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(123L), settings["h3"])
        assertNull(settings["h4"], "h4=0 means 'standard header' and is not sent")
    }

    @Test
    fun zeroObfuscationValuesAreNotSentToTheRunner() {
        val server = parse(
            "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&jc=0&jmin=0&jmax=0&s1=0&s2=0#plain",
        )
        val settings = server.toXrayOutbound("t").settings
        listOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4").forEach { key -> assertNull(settings[key], key) }
    }

    @Test
    fun finalMaskIsNotPartOfALink() {
        val server = parse("awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&finalmask=%7B%7D#f")
        assertEquals("", server.finalMask)
        assertNull(server.toXrayOutbound("t").streamSettings)
    }

    // ---- round trip ------------------------------------------------------------------------

    @Test
    fun getUrlProducesALinkThatParsesBackToTheSameProfile() {
        val original = AmneziaWg(
            remarks = "Тест A+B #1",
            server = "2001:db8::5",
            port = "4500",
            secretKey = privateKey,
            publicKey = publicKey,
            preSharedKey = presharedKey,
            address = "10.8.0.2/32,fd00::2/128",
            mtu = "1380",
            jc = "7", jmin = "20", jmax = "90", s1 = "13", s2 = "27", s3 = "5", s4 = "6",
            h1 = "100-200", h2 = "300", h3 = "400-500", h4 = "600",
            i1 = "<b 0xdeadbeef><r 16><t>", i2 = "<r 4>", i3 = "<rc 4>", i4 = "<rd 4>", i5 = "<dz 4>",
        )
        val restored = parse(original.getUrl())
        assertEquals(original.connectionFingerprint(), restored.connectionFingerprint())
        assertEquals(original.remarks, restored.remarks)
    }

    // ---- validation at import --------------------------------------------------------------

    @Test
    fun aLinkWithoutPublicKeyOrPrivateKeyIsRejected() {
        assertTrue(importAll("awg://${enc(privateKey)}@203.0.113.7:51820#nopub").isEmpty())
        assertTrue(importAll("awg://203.0.113.7:51820?publickey=${enc(publicKey)}#nopriv").isEmpty())
    }

    @Test
    fun linksThatTheNativeRunnerCannotSurviveAreRejectedAtImport() {
        val base = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}"
        listOf(
            "&jc=129", "&jc=-1", "&jmin=100&jmax=50", "&jmax=1281", "&s1=1281", "&s3=1281",
            "&h1=abc", "&h2=5-3", "&h3=4294967296", "&i1=abc", "&i2=${enc("<b 0xabc>")}", "&i3=${enc("<x>")}",
            "&jc=abc", "&mtu=99999", "&address=not-an-ip",
        ).forEach { tail ->
            assertTrue(importAll("$base$tail#bad").isEmpty(), "must reject $tail")
        }
    }

    @Test
    fun invalidKeysAreRejected() {
        val bad = "awg://${enc("short")}@203.0.113.7:51820?publickey=${enc(publicKey)}#k"
        assertTrue(importAll(bad).isEmpty())
    }

    // ---- extraction from pasted text -------------------------------------------------------

    @Test
    fun linksAreExtractedFromProseDuplicatesAndBom() {
        val link = "awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}&jc=5#One"
        val other = "amneziawg://${enc(privateKey)}@203.0.113.8:51821?publickey=${enc(publicKey)}#Two"
        val text = "﻿Here is my config: $link, and also\r\n$other\r\nsecond copy: $link;\r\n"
        val servers = importAll(text).map { assertIs<AmneziaWg>(it) }
        assertEquals(listOf("One", "Two"), servers.map { it.remarks })
        assertEquals(listOf("51820", "51821"), servers.map { it.port })
    }

    @Test
    fun awgAndWireguardLinksInOneBatchKeepTheirTypes() {
        val text = """
            awg://${enc(privateKey)}@203.0.113.7:51820?publickey=${enc(publicKey)}#A
            wireguard://${enc(privateKey)}@203.0.113.9:51820?publickey=${enc(publicKey)}#W
        """.trimIndent()
        val servers = importAll(text)
        assertIs<AmneziaWg>(servers[0])
        assertIs<Wireguard>(servers[1])
    }

    // ---- .conf documents -------------------------------------------------------------------

    private fun conf(interfaceExtra: String = "", peerExtra: String = "", endpoint: String = "203.0.113.7:51820", head: String = "") = """
        $head
        [Interface]
        PrivateKey = $privateKey
        Address = 10.8.0.2/32
        $interfaceExtra

        [Peer]
        PublicKey = $publicKey
        Endpoint = $endpoint
        AllowedIPs = 0.0.0.0/0, ::/0
        $peerExtra
    """.trimIndent()

    private fun importedConf(text: String): ProxyServer<*> =
        assertIs<WireguardConfParseResult.Imported>(parseWireguardConf(text)).server

    @Test
    fun anAwg2ConfKeepsEveryField() {
        val server = assertIs<AmneziaWg>(
            importedConf(
                conf(
                    interfaceExtra = """
                        Jc = 6
                        Jmin = 10
                        Jmax = 50
                        S1 = 11
                        S2 = 22
                        S3 = 33
                        S4 = 44
                        H1 = 1000-2000
                        H2 = 3000-4000
                        H3 = 5000-6000
                        H4 = 7000-8000
                        I1 = <b 0xc0ffee><r 16><t>
                        I2 = <r 8>
                        I3 = <rc 4>
                        I4 = <rd 4>
                        I5 = <dz 2>
                    """.trimIndent(),
                    peerExtra = "PresharedKey = $presharedKey",
                ),
            ),
        )
        assertEquals(privateKey, server.secretKey)
        assertEquals(publicKey, server.publicKey)
        assertEquals(presharedKey, server.preSharedKey)
        assertEquals(listOf("6", "10", "50", "11", "22", "33", "44"), listOf(server.jc, server.jmin, server.jmax, server.s1, server.s2, server.s3, server.s4))
        assertEquals(listOf("1000-2000", "3000-4000", "5000-6000", "7000-8000"), listOf(server.h1, server.h2, server.h3, server.h4))
        assertEquals("<b 0xc0ffee><r 16><t>", server.i1)
        assertEquals("<dz 2>", server.i5)
    }

    @Test
    fun anAwg1ConfWithFourHeadersAndNoChainsIsAmnezia() {
        val server = assertIs<AmneziaWg>(
            importedConf(conf(interfaceExtra = "Jc = 4\nJmin = 40\nJmax = 70\nS1 = 0\nS2 = 0\nH1 = 1\nH2 = 2\nH3 = 3\nH4 = 4")),
        )
        assertEquals(listOf("1", "2", "3", "4"), listOf(server.h1, server.h2, server.h3, server.h4))
        assertEquals("", server.i1)
    }

    @Test
    fun windowsLineEndingsBomKeyCaseAndIndentationDoNotMatter() {
        val text = "﻿" + conf(interfaceExtra = "   JC=5\r\n\tjMin = 20\r\nJMAX   =   60").replace("\n", "\r\n")
        val server = assertIs<AmneziaWg>(importedConf(text))
        assertEquals(listOf("5", "20", "60"), listOf(server.jc, server.jmin, server.jmax))
    }

    @Test
    fun sectionNamesAreCaseInsensitive() {
        val text = conf(interfaceExtra = "Jc = 5").replace("[Interface]", "[interface]").replace("[Peer]", "[PEER]")
        assertIs<AmneziaWg>(importedConf(text))
    }

    @Test
    fun theFirstCommentBecomesTheName() {
        val server = assertIs<AmneziaWg>(importedConf(conf(interfaceExtra = "Jc = 5", head = "# My Amnezia #1\n# second")))
        assertEquals("My Amnezia #1", server.remarks)
    }

    @Test
    fun withoutACommentTheNameIsTheEndpoint() {
        val server = assertIs<AmneziaWg>(importedConf(conf(interfaceExtra = "Jc = 5")))
        assertEquals("Amnezia WG (203.0.113.7:51820)", server.remarks)
    }

    @Test
    fun endpointFormsAreSplitCorrectly() {
        val v6 = assertIs<AmneziaWg>(importedConf(conf("Jc = 5", endpoint = "[2001:db8::1]:51999")))
        assertEquals("2001:db8::1", v6.server)
        assertEquals("51999", v6.port)

        val domain = assertIs<AmneziaWg>(importedConf(conf("Jc = 5", endpoint = "vpn.example.org:443")))
        assertEquals("vpn.example.org", domain.server)
        assertEquals("443", domain.port)

        val noPort = assertIs<AmneziaWg>(importedConf(conf("Jc = 5", endpoint = "vpn.example.org")))
        assertEquals("51820", noPort.port)
    }

    @Test
    fun valuesMayContainEqualsSignsAndKeepThem() {
        val server = assertIs<AmneziaWg>(importedConf(conf("Jc = 5")))
        assertTrue(server.secretKey.endsWith("="))
    }

    @Test
    fun trailingCommentsAfterAValueAreIgnoredLikeWgQuickDoes() {
        val server = assertIs<AmneziaWg>(importedConf(conf("Jc = 5 # junk packets\nJmin = 40 #lower")))
        assertEquals("5", server.jc)
        assertEquals("40", server.jmin)
    }

    @Test
    fun aListOfAddressesIsKept() {
        val text = conf("Jc = 5").replace("Address = 10.8.0.2/32", "Address = 10.8.0.2/32, fd00::2/128")
        val server = assertIs<AmneziaWg>(importedConf(text))
        assertEquals(listOf("10.8.0.2/32", "fd00::2/128"), server.address.split(',').map(String::trim))
    }

    @Test
    fun aPlainWireguardConfStaysWireguard() {
        assertIs<Wireguard>(importedConf(conf()))
    }

    @Test
    fun aConfWithoutAnEndpointOrKeysIsInvalid() {
        assertIs<WireguardConfParseResult.Invalid>(parseWireguardConf(conf("Jc = 5", endpoint = "")))
        assertIs<WireguardConfParseResult.Invalid>(parseWireguardConf(conf("Jc = 5").replace(privateKey, "")))
        assertIs<WireguardConfParseResult.Invalid>(parseWireguardConf(conf("Jc = 5").replace(publicKey, "")))
    }

    @Test
    fun confValuesTheRunnerCannotSurviveAreInvalid() {
        listOf(
            "Jc = 129", "Jc = abc", "Jmin = 100\nJmax = 50", "S1 = 1281", "H1 = 5-3", "H2 = nope", "I1 = <b 0xabc>", "I2 = text",
        ).forEach { extra ->
            assertIs<WireguardConfParseResult.Invalid>(parseWireguardConf(conf(extra)), extra)
        }
    }

    @Test
    fun aConfExportedAsALinkParsesBackIdentically() {
        val fromConf = assertIs<AmneziaWg>(
            importedConf(conf("Jc = 6\nJmin = 10\nJmax = 50\nS1 = 11\nS2 = 22\nH1 = 10-20\nI1 = <b 0xaa><r 4>")),
        )
        val fromLink = parse(fromConf.getUrl())
        assertEquals(fromConf.connectionFingerprint(), fromLink.connectionFingerprint())
    }
}
