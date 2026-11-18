// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Mirrors Xray v26.11.9 infra/conf/transport_security.go and transport/internet/tls/tls.go. */
class TlsFingerprintTest {
    private val id = "11111111-2222-4333-8444-555555555555"

    private fun tls(fp: String?) = VLESS(
        remarks = "x", id = id, server = "s.example.com", port = "443",
        parms = V2RayParameters(type = "raw", security = "tls", fp = fp),
    ).validateFull()

    private fun reality(fp: String?) = VLESS(
        remarks = "x", id = id, server = "s.example.com", port = "443",
        parms = V2RayParameters(type = "raw", security = "reality", fp = fp ?: "", pbk = "A".repeat(43)),
    ).validateFull()

    @Test
    fun theDefaultChromeAndTheOldPresetsStayValid() {
        listOf("", "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized")
            .forEach { assertTrue(tls(it).isEmpty(), "'$it' must be valid for TLS: ${tls(it)}") }
    }

    @Test
    fun explicitModernVersionsAreValid() {
        listOf("hellochrome_155", "hellochrome_133", "hellofirefox_148", "hellosafari_26_3", "helloios_14", "helloedge_106")
            .forEach {
                assertTrue(tls(it).isEmpty(), "'$it' must be valid for TLS: ${tls(it)}")
                assertTrue(reality(it).isEmpty(), "'$it' must be valid for REALITY: ${reality(it)}")
            }
    }

    @Test
    fun theCoreAlsoKnowsRandomizedNoAlpnAndTheLegacyHelloNames() {
        listOf("randomizednoalpn", "hellochrome_auto", "hellochrome_120_pq", "hellorandomizedalpn")
            .forEach { assertTrue(tls(it).isEmpty(), "'$it' must be valid for TLS: ${tls(it)}") }
    }

    @Test
    fun theNameIsCaseInsensitiveBecauseTheCoreLowercasesIt() {
        assertTrue(tls("Chrome").isEmpty())
        assertTrue(tls("HelloChrome_155").isEmpty())
    }

    // Checked against the core's own config builder: TLS accepts both, REALITY refuses both.
    @Test
    fun unsafeAndHellogolangAreAcceptedForTlsButRefusedForReality() {
        listOf("unsafe", "hellogolang").forEach {
            assertTrue(tls(it).isEmpty(), "TLS accepts '$it': ${tls(it)}")
            assertFalse(reality(it).isEmpty(), "REALITY refuses '$it'")
        }
    }

    @Test
    fun unknownNamesAreRefusedEverywhere() {
        listOf("chrome_999", "hellochrome", "netscape").forEach {
            assertTrue(tls(it).any { issue -> issue.error == ProxyServerValidationError.UnsupportedValue }, "'$it' for TLS")
            assertTrue(reality(it).any { issue -> issue.error == ProxyServerValidationError.UnsupportedValue }, "'$it' for REALITY")
        }
    }

    @Test
    fun theEditorOffersTheOldPresetsFirstThenTheModernVersionsAndAllOfThemAreValid() {
        val options = tlsFingerprintEditorOptions(current = null)

        assertEquals("", options.first())
        assertEquals(listOf("chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq", "random", "randomized"), options.drop(1).take(10))
        assertTrue("hellochrome_155" in options)
        options.forEach { assertTrue(tls(it).isEmpty(), "editor option '$it' must be valid: ${tls(it)}") }
    }

    @Test
    fun theEditorKeepsAnImportedValueThatIsNotInTheList() {
        val imported = "hellochrome_120_pq"

        val options = tlsFingerprintEditorOptions(current = imported)

        assertTrue(imported in options, "the stored value must be shown instead of falling back to the first entry")
        assertEquals(1, options.count { it == imported })
        assertEquals(tlsFingerprintEditorOptions(current = null), tlsFingerprintEditorOptions(current = "chrome"))
    }
}
