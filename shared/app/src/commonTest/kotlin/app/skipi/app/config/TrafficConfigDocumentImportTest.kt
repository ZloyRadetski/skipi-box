// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficConfigDocumentImportTest {
    @Test
    fun firstMatchingSourceUrlRowUpdatesInPlaceBeforeLaterNameMatch() {
        val first = profile(10, "First")
        val urlMatch = profile(20, "URL target", "https://example.test/feed")
        val nameMatch = profile(30, "Incoming")
        val profiles = listOf(first, urlMatch, nameMatch)

        val result = importTrafficConfigDocument(
            trafficConfigs = profiles,
            nextTrafficConfigId = 41,
            activeTrafficConfigId = nameMatch.id,
            content = document(name = "Incoming", logLevel = "debug"),
            activate = true,
            fallbackName = "Imported",
            sourceUrl = " https://EXAMPLE.test/feed ",
        )

        assertEquals(listOf(10, 20, 30), result.trafficConfigs.map(TrafficConfigState::id))
        assertSame(first, result.trafficConfigs[0])
        assertEquals("Incoming", result.trafficConfigs[1].name)
        assertEquals("https://EXAMPLE.test/feed", result.trafficConfigs[1].sourceUrl)
        assertTrue(result.trafficConfigs[1].rawConfig.contains("loglevel = debug"))
        assertSame(nameMatch, result.trafficConfigs[2])
        assertEquals(20, result.importedConfigId)
        assertFalse(result.created)
        assertEquals(41, result.nextTrafficConfigId)
        assertEquals(nameMatch.id, result.activeTrafficConfigId)
        assertEquals("URL target", urlMatch.name)
    }

    @Test
    fun earlierProfileNameMatchWinsOverLaterSourceUrlMatch() {
        val first = profile(10, "First")
        val nameMatch = profile(20, "Incoming")
        val urlMatch = profile(30, "URL target", "https://example.test/feed")

        val result = importTrafficConfigDocument(
            trafficConfigs = listOf(first, nameMatch, urlMatch),
            nextTrafficConfigId = 41,
            activeTrafficConfigId = urlMatch.id,
            content = document(name = "Incoming", logLevel = "debug"),
            activate = true,
            fallbackName = "Imported",
            sourceUrl = " https://EXAMPLE.test/feed ",
        )

        assertEquals(listOf(10, 20, 30), result.trafficConfigs.map(TrafficConfigState::id))
        assertSame(first, result.trafficConfigs[0])
        assertEquals("Incoming", result.trafficConfigs[1].name)
        assertEquals("https://EXAMPLE.test/feed", result.trafficConfigs[1].sourceUrl)
        assertTrue(result.trafficConfigs[1].rawConfig.contains("loglevel = debug"))
        assertSame(urlMatch, result.trafficConfigs[2])
        assertEquals("URL target", result.trafficConfigs[2].name)
        assertEquals(20, result.importedConfigId)
        assertFalse(result.created)
        assertEquals(41, result.nextTrafficConfigId)
        assertEquals(urlMatch.id, result.activeTrafficConfigId)
    }

    @Test
    fun profileNameMatchUpdatesInPlaceAndDoesNotReactivateExistingProfile() {
        val first = profile(8, "First")
        val existing = profile(19, "Work")
        val last = profile(24, "Last")

        val result = importTrafficConfigDocument(
            trafficConfigs = listOf(first, existing, last),
            nextTrafficConfigId = 37,
            activeTrafficConfigId = first.id,
            content = document(name = " work ", logLevel = "info"),
            activate = true,
            fallbackName = "Imported",
        )

        assertEquals(listOf(8, 19, 24), result.trafficConfigs.map(TrafficConfigState::id))
        assertSame(first, result.trafficConfigs[0])
        assertEquals("work", result.trafficConfigs[1].name)
        assertTrue(result.trafficConfigs[1].rawConfig.contains("loglevel = info"))
        assertSame(last, result.trafficConfigs[2])
        assertEquals(existing.id, result.importedConfigId)
        assertFalse(result.created)
        assertEquals(37, result.nextTrafficConfigId)
        assertEquals(first.id, result.activeTrafficConfigId)
    }

    @Test
    fun unmatchedDocumentAppendsAtNextIdAndActivatesOnlyWhenRequested() {
        val existing = profile(7, "Current")
        val profiles = listOf(existing)
        val content = document(name = "New profile", logLevel = "warning")

        val importedWithoutActivation = importTrafficConfigDocument(
            trafficConfigs = profiles,
            nextTrafficConfigId = 43,
            activeTrafficConfigId = existing.id,
            content = content,
            activate = false,
            fallbackName = "Imported",
            sourceUrl = " https://example.test/new ",
        )
        val importedWithActivation = importTrafficConfigDocument(
            trafficConfigs = profiles,
            nextTrafficConfigId = 43,
            activeTrafficConfigId = existing.id,
            content = content,
            activate = true,
            fallbackName = "Imported",
            sourceUrl = "https://example.test/new",
        )

        assertEquals(listOf(7, 43), importedWithoutActivation.trafficConfigs.map(TrafficConfigState::id))
        assertSame(existing, importedWithoutActivation.trafficConfigs[0])
        assertEquals("New profile", importedWithoutActivation.trafficConfigs[1].name)
        assertEquals("https://example.test/new", importedWithoutActivation.trafficConfigs[1].sourceUrl)
        assertEquals(44, importedWithoutActivation.nextTrafficConfigId)
        assertEquals(existing.id, importedWithoutActivation.activeTrafficConfigId)
        assertEquals(43, importedWithoutActivation.importedConfigId)
        assertTrue(importedWithoutActivation.created)

        assertEquals(listOf(7, 43), importedWithActivation.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(44, importedWithActivation.nextTrafficConfigId)
        assertEquals(43, importedWithActivation.activeTrafficConfigId)
        assertTrue(importedWithActivation.created)
        assertEquals(listOf(existing), profiles)
    }

    private fun profile(id: Int, name: String, sourceUrl: String = "") = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = "[General]\nloglevel = warning\n",
        sourceUrl = sourceUrl,
    )

    private fun document(name: String, logLevel: String): String = """# name: $name
[General]
loglevel = $logLevel
"""
}
