// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficConfigDuplicateSnapshotTest {
    @Test
    fun duplicateUsesClickedProfileWhenRepositoryNowHasNewerRecordWithSameId() {
        val clicked = profile(
            name = "Clicked profile",
            revision = "clicked",
            sourceUrl = "https://clicked.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 10L,
        )
        val newerStored = profile(
            name = "Newer stored profile",
            revision = "newer",
            sourceUrl = "https://newer.example/profile",
            updateLocked = false,
            lastUpdatedAtMillis = 20L,
        )
        val appState = AppState(
            activeTrafficConfigId = clicked.id,
            nextTrafficConfigId = 24,
            trafficConfigs = listOf(newerStored),
        )

        val creation = appState.withDuplicatedTrafficConfigProfile(clicked, "Clicked profile copy")
        val copy = creation.state.trafficConfigs.last()

        assertEquals(listOf(23, 24), creation.state.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(23, creation.state.activeTrafficConfigId)
        assertEquals(25, creation.state.nextTrafficConfigId)
        assertEquals(24, creation.profileId)
        assertSame(newerStored, creation.state.trafficConfigs.first())
        assertEquals("Clicked profile copy", copy.name)
        assertEquals("https://clicked.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals(false, copy.autoUpdate)
        assertEquals("clicked-agent", copy.resourceSettings.userAgent)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.rawConfig.contains("revision = clicked"))
        assertTrue(copy.rawConfig.contains("profile-update-url = https://clicked.example/profile"))
    }

    @Test
    fun duplicateStillUsesClickedProfileAfterRepositoryRowWasRemoved() {
        val clicked = profile(
            name = "Clicked profile",
            revision = "clicked",
            sourceUrl = "https://clicked.example/profile",
            updateLocked = true,
            lastUpdatedAtMillis = 10L,
        )
        val remaining = TrafficConfigState(7, "Remaining", "[General]\n")
        val appState = AppState(
            activeTrafficConfigId = remaining.id,
            nextTrafficConfigId = 24,
            trafficConfigs = listOf(remaining),
        )

        val creation = appState.withDuplicatedTrafficConfigProfile(clicked, "Clicked profile copy")
        val copy = creation.state.trafficConfigs.last()

        assertEquals(listOf(7, 24), creation.state.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(7, creation.state.activeTrafficConfigId)
        assertEquals(25, creation.state.nextTrafficConfigId)
        assertEquals("https://clicked.example/profile", copy.sourceUrl)
        assertEquals(true, copy.updateLocked)
        assertEquals("clicked-agent", copy.resourceSettings.userAgent)
        assertEquals(0L, copy.lastUpdatedAtMillis)
        assertTrue(copy.rawConfig.contains("revision = clicked"))
    }

    private fun profile(
        name: String,
        revision: String,
        sourceUrl: String,
        updateLocked: Boolean,
        lastUpdatedAtMillis: Long,
    ): TrafficConfigState = TrafficConfigState(
        id = 23,
        name = name,
        rawConfig = "[General]\nloglevel = warning\n\n[Vendor]\nrevision = $revision\n",
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
        autoUpdate = false,
        updateInterval = "48",
        resourceSettings = TrafficConfigResourceSettings(
            userAgent = "clicked-agent",
            autoUpdate = false,
            updateInterval = "72",
        ),
    ).withSkipiSettingsInRawConfig()
}
