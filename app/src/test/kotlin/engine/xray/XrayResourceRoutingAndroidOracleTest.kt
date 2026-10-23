// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import features.routing.model.RouteRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Baseline against Android's current file-backed global routing validation before extraction. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class XrayResourceRoutingAndroidOracleTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sanitizerUsesNonEmptyResourceFilesAndCaseInsensitiveGeoTags() {
        val dataDir = temporaryFolder.newFolder("routing-resources")
        writeGeoDat(dataDir, "geosite.dat", "OpenAI")
        writeGeoDat(dataDir, "geoip.dat", "US")
        writeGeoDat(dataDir, "custom.dat", "RegionTag")
        File(dataDir, "empty.dat").writeBytes(byteArrayOf())
        val path = dataDir.absolutePath

        assertTrue(XrayGeoRuleSanitizer.isDomainRuleValid("GEOSITE:openai", path))
        assertTrue(XrayGeoRuleSanitizer.isDomainRuleValid("EXT:custom:regiontag", path))
        assertFalse(XrayGeoRuleSanitizer.isDomainRuleValid("geosite:missing", path))
        assertFalse(XrayGeoRuleSanitizer.isDomainRuleValid("ext:empty.dat:tag", path))
        assertFalse(XrayGeoRuleSanitizer.isDomainRuleValid("ext:missing.dat:tag", path))
        assertEquals(
            listOf("plain.example", "geosite:OPENAI", "ext:custom:RegionTag"),
            XrayGeoRuleSanitizer.filterValidDomainRules(
                listOf(
                    "plain.example",
                    "geosite:OPENAI",
                    "geosite:missing",
                    "ext:custom:RegionTag",
                    "ext:empty.dat:tag",
                ),
                path,
            ),
        )

        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("203.0.113.7", path))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("geoip:us", path))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("EXT:custom:regiontag", path))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("geoip:private", path))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("geoip:missing", path))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("not-an-ip", path))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("ext:empty.dat:tag", path))
        assertEquals(
            listOf("203.0.113.7", "geoip:US", "geoip:private", "ext:custom:RegionTag"),
            XrayGeoRuleSanitizer.filterValidIpRules(
                listOf(
                    "203.0.113.7",
                    "not-an-ip",
                    "geoip:US",
                    "geoip:missing",
                    "geoip:private",
                    "ext:empty.dat:tag",
                    "ext:custom:RegionTag",
                ),
                path,
            ),
        )
    }

    @Test
    fun missingDataDirectoryKeepsListsButStillValidatesDirectIpChecks() {
        val domains = listOf("", "geosite:missing", "ext:malformed", "plain.example")
        val ips = listOf("", "not-an-ip", "geoip:missing", "ext:malformed")

        assertTrue(XrayGeoRuleSanitizer.isDomainRuleValid("", null))
        assertTrue(XrayGeoRuleSanitizer.isDomainRuleValid("ext:malformed", null))
        assertEquals(domains, XrayGeoRuleSanitizer.filterValidDomainRules(domains, null))
        assertEquals(ips, XrayGeoRuleSanitizer.filterValidIpRules(ips, null))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("", null))
        assertFalse(XrayGeoRuleSanitizer.isIpRuleValid("not-an-ip", null))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("geoip:missing", null))
        assertTrue(XrayGeoRuleSanitizer.isIpRuleValid("ext:malformed", null))
    }

    @Test
    fun externalPreflightChecksEnabledDomainRulesAndPreservesFailureOrder() {
        val dataDir = temporaryFolder.newFolder("external-routing")
        File(dataDir, "custom.dat").writeBytes(byteArrayOf(1))
        File(dataDir, "empty.dat").writeBytes(byteArrayOf())

        appState(
            RouteRule(
                id = 1,
                outboundTag = "direct",
                domain = listOf("ext:custom.dat:tag", " ext:custom.dat:tag "),
                ip = listOf("ext:ip-only.dat:ignored"),
            ),
            RouteRule(
                id = 2,
                outboundTag = "direct",
                domain = listOf("ext:disabled.dat:ignored", "ext:malformed"),
                enabled = false,
            ),
        ).validateXrayExternalRoutingResources(dataDir.absolutePath)

        val malformedError = runCatching {
            appState(
                RouteRule(id = 3, outboundTag = "direct", domain = listOf("ext:malformed", "ext:missing.dat:tag")),
            ).validateXrayExternalRoutingResources(dataDir.absolutePath)
        }.exceptionOrNull()
        assertTrue(malformedError is IllegalStateException)
        assertEquals("Invalid external routing domain rule: ext:malformed", malformedError?.message)

        val missingError = runCatching {
            appState(
                RouteRule(
                    id = 4,
                    outboundTag = "direct",
                    domain = listOf("ext:missing.dat:first", "ext:missing.dat:second", "EXT:missing.dat:first"),
                ),
            ).validateXrayExternalRoutingResources(dataDir.absolutePath)
        }.exceptionOrNull()
        assertTrue(missingError is IllegalStateException)
        assertEquals("Missing external routing resource file: missing.dat", missingError?.message)

        // Tag lookup normalizes ext:custom to custom.dat; preflight keeps its literal filename.
        val literalNameError = runCatching {
            appState(
                RouteRule(id = 6, outboundTag = "direct", domain = listOf("ext:custom:tag")),
            ).validateXrayExternalRoutingResources(dataDir.absolutePath)
        }.exceptionOrNull()
        assertTrue(literalNameError is IllegalStateException)
        assertEquals("Missing external routing resource file: custom", literalNameError?.message)

        val emptyError = runCatching {
            appState(
                RouteRule(id = 5, outboundTag = "direct", domain = listOf("ext:empty.dat:tag")),
            ).validateXrayExternalRoutingResources(dataDir.absolutePath)
        }.exceptionOrNull()
        assertTrue(emptyError is IllegalStateException)
        assertEquals("Missing external routing resource file: empty.dat", emptyError?.message)
    }

    private fun appState(vararg rules: RouteRule) = AppState(routeRules = rules.toList())

    private fun writeGeoDat(directory: File, fileName: String, vararg tags: String) {
        val entries = tags.map { tag ->
            val tagBytes = tag.encodeToByteArray()
            val entry = byteArrayOf(0x0a, tagBytes.size.toByte()) + tagBytes
            byteArrayOf(0x0a, entry.size.toByte()) + entry
        }
        File(directory, fileName).writeBytes(entries.fold(byteArrayOf()) { bytes, entry -> bytes + entry })
    }
}
