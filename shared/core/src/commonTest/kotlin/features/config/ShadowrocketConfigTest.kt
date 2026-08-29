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
}
