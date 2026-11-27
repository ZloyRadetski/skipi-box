// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MultilinkLinkDraftTest {
    private val settings = MultilinkSettings(
        enabled = true,
        weights = mapOf("{eth}" to 2.0),
        limits = mapOf("{eth}" to 7.5),
        phones = listOf("{eth}"),
        names = mapOf("{eth}" to "Home line"),
    )

    @Test
    fun aDraftShowsWhatIsSet() {
        val draft = MultilinkLinkDraft.of(settings, "{eth}")
        assertEquals(MultilinkLinkDraft(name = "Home line", limitMbps = "7.5", weight = "2", phone = true), draft)
        assertEquals(MultilinkLinkDraft(), MultilinkLinkDraft.of(settings, "{wifi}"))
    }

    @Test
    fun savingADraftChangesOnlyThatLink() {
        val saved = settings.withDraft("{wifi}", MultilinkLinkDraft(name = "Wi-Fi 6", limitMbps = "50", weight = "", phone = false))
            .getOrThrow()
        assertEquals(50.0, saved.limits["{wifi}"])
        assertEquals("Wi-Fi 6", saved.names["{wifi}"])
        assertEquals(settings.limits["{eth}"], saved.limits["{eth}"])
        assertEquals(settings.weights, saved.weights, "an empty weight keeps the link measured")
    }

    @Test
    fun clearingFieldsRemovesTheirSettings() {
        val saved = settings.withDraft("{eth}", MultilinkLinkDraft()).getOrThrow()
        assertEquals(emptyMap(), saved.limits)
        assertEquals(emptyMap(), saved.weights)
        assertEquals(emptyList(), saved.phones)
        assertEquals(emptyMap(), saved.names)
    }

    @Test
    fun aCommaIsADecimalPoint() {
        val saved = settings.withDraft("{eth}", MultilinkLinkDraft(limitMbps = "2,5")).getOrThrow()
        assertEquals(2.5, saved.limits["{eth}"])
    }

    @Test
    fun nonsenseIsRefused() {
        for (draft in listOf(
            MultilinkLinkDraft(limitMbps = "fast"),
            MultilinkLinkDraft(limitMbps = "-1"),
            MultilinkLinkDraft(limitMbps = "2000000"),
            MultilinkLinkDraft(weight = "0"),
            MultilinkLinkDraft(weight = "1001"),
            MultilinkLinkDraft(weight = "NaN"),
        )) {
            assertTrue(settings.withDraft("{eth}", draft).isFailure, "accepted $draft")
        }
    }

    @Test
    fun probeTargetsAreIpAndPort() {
        assertEquals(listOf("77.88.8.8:443", "[2a02:6b8::feed:ff]:443"), parseProbeTargets(" 77.88.8.8:443,\n[2a02:6b8::feed:ff]:443 ").getOrThrow())
        assertEquals(emptyList(), parseProbeTargets("  ").getOrThrow())
        for (bad in listOf("example.com:443", "1.1.1.1", "1.1.1.1:0", "1.1.1.1:70000", "300.1.1.1:443", "2a02::1:443")) {
            assertTrue(parseProbeTargets(bad).isFailure, "accepted $bad")
        }
    }

    @Test
    fun probeTargetsReadBackAsText() {
        assertEquals("77.88.8.8:443, 8.8.8.8:443", MultilinkSettings(probeTargets = listOf("77.88.8.8:443", "8.8.8.8:443")).probeTargetsText())
    }
}

class MultilinkFormatTest {
    @Test
    fun speedsHaveOneDecimalAtMost() {
        assertEquals("12.3", formatMbps(12.34))
        assertEquals("20", formatMbps(20.0))
        assertEquals("0", formatMbps(0.04))
        assertEquals("0.1", formatMbps(0.05))
        assertEquals("1000.5", formatMbps(1000.49))
    }
}
