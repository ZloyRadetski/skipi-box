// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrafficConfigProfileRefreshOperationsTest {
    @Test
    fun manualRefreshDecisionRequestsConfirmationOnlyForLockedProfile() {
        val unlocked = profile(
            id = 11,
            name = "Unlocked",
            locked = false,
            autoUpdate = false,
            updateInterval = "",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "",
        )
        val locked = unlocked.copy(id = 12, name = "Locked", updateLocked = true)

        assertEquals(
            TrafficConfigProfileRefreshRequest.Refresh(
                snapshot = unlocked,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
            ),
            TrafficConfigProfileRefreshOperations.requestManualRefresh(unlocked),
        )
        assertEquals(
            TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh(locked),
            TrafficConfigProfileRefreshOperations.requestManualRefresh(locked),
        )
    }

    @Test
    fun acknowledgingLockedRefreshUsesCapturedProfileAndExplicitUnlockMode() {
        val clicked = profile(
            id = 13,
            name = "Clicked profile",
            locked = true,
            autoUpdate = false,
            updateInterval = "",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "",
        )
        val confirmation = assertIs<TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh>(
            TrafficConfigProfileRefreshOperations.requestManualRefresh(clicked),
        )
        val changedAfterClick = clicked.copy(name = "Edited after click", updateLocked = false)

        assertEquals(
            TrafficConfigProfileRefreshRequest.Refresh(
                snapshot = clicked,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = true),
            ),
            TrafficConfigProfileRefreshOperations.confirmUnlockAndRefresh(confirmation),
        )
        assertEquals(
            TrafficConfigProfileRefreshRequest.Refresh(
                snapshot = changedAfterClick,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
            ),
            TrafficConfigProfileRefreshOperations.requestManualRefresh(changedAfterClick),
        )
    }

    @Test
    fun dismissingLockedRefreshConfirmationProducesNoRefreshRequest() {
        val locked = profile(
            id = 14,
            name = "Locked",
            locked = true,
            autoUpdate = false,
            updateInterval = "",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "",
        )
        val confirmation = assertIs<TrafficConfigProfileRefreshRequest.ConfirmUnlockAndRefresh>(
            TrafficConfigProfileRefreshOperations.requestManualRefresh(locked),
        )

        assertEquals(
            null,
            TrafficConfigProfileRefreshOperations.dismissUnlockAndRefresh(confirmation),
        )
    }

    @Test
    fun manualRefreshUsesFetchedPortableSettingsAndUnlockOverridesFetchedLock() {
        val current = profile(
            id = 17,
            name = "Before",
            locked = false,
            autoUpdate = false,
            updateInterval = "48",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "72",
        )
        val capturedUrl = "https://captured.example/profile.conf"
        val updatedAt = 321_000L

        val locked = TrafficConfigProfileRefreshOperations.apply(
            current = current,
            normalizedDocument = fetchedDocument,
            capturedUrl = capturedUrl,
            timestamp = updatedAt,
            mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
        )
        val unlocked = TrafficConfigProfileRefreshOperations.apply(
            current = current,
            normalizedDocument = fetchedDocument,
            capturedUrl = capturedUrl,
            timestamp = updatedAt,
            mode = TrafficConfigProfileRefreshMode.Manual(unlock = true),
        )

        assertEquals(current.id, locked.id)
        assertEquals("Fetched name", locked.name)
        assertEquals(capturedUrl, locked.sourceUrl)
        assertEquals(updatedAt, locked.lastUpdatedAtMillis)
        assertTrue(locked.updateLocked)
        assertTrue(locked.autoUpdate)
        assertEquals("6", locked.updateInterval)
        assertEquals(23, locked.resourceSettings.source)
        assertEquals("https://geo.example/ip.dat", locked.resourceSettings.customGeoIpUrl)
        assertEquals("profile-agent", locked.resourceSettings.userAgent)
        assertEquals(
            listOf(TrafficConfigCustomResourceFile(6, "Extra", "https://geo.example/extra.dat")),
            locked.resourceSettings.customFiles,
        )
        assertEquals(7, locked.resourceSettings.nextCustomFileId)
        assertTrue(locked.resourceSettings.autoUpdate)
        assertEquals("3", locked.resourceSettings.updateInterval)
        assertPortableFieldsMatchDocument(locked)

        assertFalse(unlocked.updateLocked)
        assertTrue(unlocked.rawConfig.contains("profile-update-locked = false"))
        assertPortableFieldsMatchDocument(unlocked)
    }

    @Test
    fun automaticRefreshImportsFetchedSettingsButKeepsCurrentSchedulingAndLockPolicy() {
        val current = profile(
            id = 29,
            name = "Current name",
            locked = true,
            autoUpdate = false,
            updateInterval = "96",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "120",
        )
        val capturedUrl = "https://captured.example/automatic.conf"
        val updatedAt = 654_000L

        val updated = TrafficConfigProfileRefreshOperations.apply(
            current = current,
            normalizedDocument = fetchedDocument,
            capturedUrl = capturedUrl,
            timestamp = updatedAt,
            mode = TrafficConfigProfileRefreshMode.Automatic,
        )

        assertEquals(current.id, updated.id)
        assertEquals("Fetched name", updated.name)
        assertEquals(capturedUrl, updated.sourceUrl)
        assertEquals(updatedAt, updated.lastUpdatedAtMillis)
        assertTrue(updated.updateLocked)
        assertFalse(updated.autoUpdate)
        assertEquals("96", updated.updateInterval)
        assertEquals(23, updated.resourceSettings.source)
        assertEquals("https://geo.example/ip.dat", updated.resourceSettings.customGeoIpUrl)
        assertEquals("https://geo.example/site.dat", updated.resourceSettings.customGeoSiteUrl)
        assertEquals("profile-agent", updated.resourceSettings.userAgent)
        assertEquals(
            listOf(TrafficConfigCustomResourceFile(6, "Extra", "https://geo.example/extra.dat")),
            updated.resourceSettings.customFiles,
        )
        assertEquals(7, updated.resourceSettings.nextCustomFileId)
        assertFalse(updated.resourceSettings.autoUpdate)
        assertEquals("120", updated.resourceSettings.updateInterval)
        assertPortableFieldsMatchDocument(updated)
    }

    @Test
    fun lateManualResponseTransformsLatestSameIdAndKeepsMissingNameAndUnrelatedRows() {
        val clicked = profile(
            id = 37,
            name = "Clicked",
            locked = false,
            autoUpdate = true,
            updateInterval = "24",
            resourceAutoUpdate = true,
            resourceUpdateInterval = "24",
        ).copy(
            sourceUrl = "https://captured.example/source.conf",
        )
        val latest = clicked.copy(
            name = "Edited while fetching",
            sourceUrl = "https://latest.example/source.conf",
            updateInterval = "36",
            androidSettings = clicked.androidSettings.copy(enableMux = false),
        ).withSkipiSettingsInRawConfig()
        val sibling = profile(
            id = 38,
            name = "Unrelated",
            locked = false,
            autoUpdate = false,
            updateInterval = "",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "",
        )
        val profiles = listOf(latest, sibling)

        val afterResponse = updateTrafficConfigProfile(profiles, clicked.id) { current ->
            TrafficConfigProfileRefreshOperations.apply(
                current = current,
                normalizedDocument = fetchedDocument.replace("profile-name = Fetched name\n", ""),
                capturedUrl = clicked.sourceUrl,
                timestamp = 987_000L,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
            )
        }

        assertEquals(listOf(clicked.id, sibling.id), afterResponse.map(TrafficConfigState::id))
        assertEquals("Edited while fetching", afterResponse.first().name)
        assertEquals("https://captured.example/source.conf", afterResponse.first().sourceUrl)
        assertEquals(987_000L, afterResponse.first().lastUpdatedAtMillis)
        assertFalse(afterResponse.first().androidSettings.enableMux)
        assertEquals(sibling, afterResponse.last())
        assertEquals(
            "Edited while fetching",
            afterResponse.first().withSkipiSettingsReadFromRawConfig().name,
        )
    }

    @Test
    fun blankCapturedUrlFallsBackToFetchedDocumentUrl() {
        val result = TrafficConfigProfileRefreshOperations.apply(
            current = profile(
                id = 41,
                name = "Current",
                locked = false,
                autoUpdate = false,
                updateInterval = "",
                resourceAutoUpdate = true,
                resourceUpdateInterval = "24",
            ),
            normalizedDocument = fetchedDocument,
            capturedUrl = " ",
            timestamp = 50L,
            mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
        )

        assertEquals("https://remote.example/original.conf", result.sourceUrl)
    }

    private fun assertPortableFieldsMatchDocument(profile: TrafficConfigState) {
        val restored = profile.withSkipiSettingsReadFromRawConfig()
        assertEquals(profile.name, restored.name)
        assertEquals(profile.sourceUrl, restored.sourceUrl)
        assertEquals(profile.updateLocked, restored.updateLocked)
        assertEquals(profile.autoUpdate, restored.autoUpdate)
        assertEquals(profile.updateInterval, restored.updateInterval)
        assertEquals(profile.resourceSettings, restored.resourceSettings)
    }

    private fun profile(
        id: Int,
        name: String,
        locked: Boolean,
        autoUpdate: Boolean,
        updateInterval: String,
        resourceAutoUpdate: Boolean,
        resourceUpdateInterval: String,
    ): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = "[General]\nloglevel = warning\n",
        sourceUrl = "https://old.example/profile.conf",
        updateLocked = locked,
        lastUpdatedAtMillis = 10L,
        autoUpdate = autoUpdate,
        updateInterval = updateInterval,
        resourceSettings = TrafficConfigResourceSettings(
            source = 2,
            customGeoIpUrl = "https://old.example/ip.dat",
            customGeoSiteUrl = "https://old.example/site.dat",
            userAgent = "old-agent",
            autoUpdate = resourceAutoUpdate,
            updateInterval = resourceUpdateInterval,
        ),
    ).withSkipiSettingsInRawConfig()

    private companion object {
        val fetchedDocument = TrafficConfigState(
            id = 99,
            name = "Fetched name",
            rawConfig = "[General]\nloglevel = debug\n",
            sourceUrl = "https://remote.example/original.conf",
            updateLocked = true,
            autoUpdate = true,
            updateInterval = "6",
            resourceSettings = TrafficConfigResourceSettings(
                source = 23,
                customGeoIpUrl = "https://geo.example/ip.dat",
                customGeoSiteUrl = "https://geo.example/site.dat",
                customDirectCidrIpv4Url = "https://geo.example/direct-v4.dat",
                customFiles = listOf(
                    TrafficConfigCustomResourceFile(6, "Extra", "https://geo.example/extra.dat"),
                ),
                userAgent = "profile-agent",
                autoUpdate = true,
                updateInterval = "3",
            ),
        ).withSkipiSettingsInRawConfig().rawConfig
    }
}
