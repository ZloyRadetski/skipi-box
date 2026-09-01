// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.proxy.server.model.StrategyGroupDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShadowrocketConfigTest {
    @Test
    fun generalEditPreservesUnsupportedSectionsAndComments() {
        val original = """
            # user-owned comment
            [General]
            dns-server = 1.1.1.1

            [MITM]
            hostname = example.com
        """.trimIndent() + "\n"

        val updated = original.withShadowrocketGeneralValue("dns-server", "9.9.9.9")

        assertTrue(updated.contains("# user-owned comment"))
        assertTrue(updated.contains("[MITM]\nhostname = example.com"))
        assertTrue(updated.contains("dns-server = 9.9.9.9"))
    }

    @Test
    fun portableSectionValueEditKeepsAndroidSpecificMetadataAndComments() {
        val original = """
            [SKIPI]
            # kept for Android
            profile-name = Phone profile
            enable-fake-dns = true
            profile-update-url = https://old.example/config
            profile-update-url = https://effective.example/config

            [MITM]
            hostname = example.com
        """.trimIndent() + "\n"

        val updated = original
            .withShadowrocketSectionValue("SKIPI", "profile-name", "Desktop profile")
            .withShadowrocketSectionValue("SKIPI", "profile-update-locked", "true")

        assertEquals("Desktop profile", updated.shadowrocketSectionValue("skipi", "profile-name"))
        assertEquals("https://effective.example/config", updated.shadowrocketSectionValue("SKIPI", "profile-update-url"))
        assertEquals("true", updated.shadowrocketSectionValue("SKIPI", "profile-update-locked"))
        assertTrue(updated.contains("# kept for Android"))
        assertTrue(updated.contains("enable-fake-dns = true"))
        assertTrue(updated.contains("[MITM]\nhostname = example.com"))
    }

    @Test
    fun parserReadsPortableProfileGroupExtensions() {
        val analysis = """
            [Proxy Group]
            Fast = url-test, One, Two, url=https://example.test/ping, interval=120, skipi-display=active_config, skipi-burst-probe=false, skipi-tolerance=75ms, timeout=8
        """.trimIndent().analyzeShadowrocketConfig()

        val group = analysis.proxyGroups.single()
        assertEquals("Fast", group.name)
        assertEquals(listOf("One", "Two"), group.members)
        assertEquals(StrategyGroupDisplayMode.ACTIVE_CONFIG, group.displayMode)
        assertFalse(group.enableBurstProbe)
        assertEquals("75ms", group.tolerance)
        assertEquals(8, group.timeoutSeconds)
    }

    @Test
    fun parserReportsMoreThanOneFinalRuleWithoutDiscardingThem() {
        val analysis = """
            [Rule]
            FINAL,PROXY
            FINAL,DIRECT
        """.trimIndent().analyzeShadowrocketConfig()

        assertEquals(2, analysis.rules.size)
        assertTrue(analysis.diagnostics.any { it.severity == ShadowrocketConfigDiagnosticSeverity.Error })
    }

    @Test
    fun decodesPortableInlineSkipiConfigPayload() {
        assertEquals(
            "[Rule]\nFINAL,PROXY",
            "W1J1bGVdCkZJTkFMLFBST1hZ".decodeSkipiConfigPayloadOrNull(),
        )
        assertEquals(null, "not a config payload".decodeSkipiConfigPayloadOrNull())
    }
}
