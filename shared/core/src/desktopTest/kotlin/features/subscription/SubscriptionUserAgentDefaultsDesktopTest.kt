// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.ProjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Desktop uses the same User-Agent contract as Android; only the platform label differs. */
class SubscriptionUserAgentDefaultsDesktopTest {
    private val desktopAgent = "SKIPI/${ProjectInfo.VERSION_NAME}/Desktop"

    @Test
    fun defaultAgentCarriesTheVersionAndTheDesktopLabel() {
        assertEquals(desktopAgent, DefaultSubscriptionUserAgent)
    }

    @Test
    fun presetListMatchesAndroidWithTheDesktopLabelFirst() {
        assertEquals(
            listOf(
                desktopAgent,
                "clash.meta",
                "FlClash X/v0.4.2 Platform/android",
                "Mihomo/1.19.0",
                "ClashForAndroid/2.5.12",
                "FlClash/0.8.73",
                "Shadowrocket/2.2.48",
                "Surfboard/2.23.0",
                "NekoBoxForAndroid/1.3.9",
            ),
            DefaultSubscriptionUserAgents,
        )
    }

    @Test
    fun anySkipiAgentOfAnotherPlatformOrVersionNormalizesToTheCurrentDesktopAgent() {
        listOf("SKIPI/0.1.0/Android", "SKIPI/9.9.9/Desktop", "Skipi/0.4.0/Desktop", "  ", "").forEach { stored ->
            assertEquals(desktopAgent, normalizeSkipiUserAgent(stored), "stored=<$stored>")
        }
    }

    @Test
    fun foreignAgentsAreKeptVerbatim() {
        assertEquals("clash.meta", normalizeSkipiUserAgent("clash.meta"))
        assertEquals("SKIPI Desktop", normalizeSkipiUserAgent("SKIPI Desktop"))
        assertFalse(isSkipiUserAgent("Mihomo/1.19.0"))
        assertTrue(isSkipiUserAgent("SKIPI/1.0/Android"))
    }

    @Test
    fun normalizedListDeduplicatesAndKeepsTheCurrentAgentFirstWhenMissing() {
        assertEquals(
            listOf(desktopAgent, "Custom"),
            normalizeSkipiUserAgents(listOf("SKIPI/0.1/Android", "SKIPI/0.2/Desktop", "Custom")),
        )
        assertEquals(listOf(desktopAgent, "Custom"), normalizeSkipiUserAgents(listOf("Custom")))
    }
}
