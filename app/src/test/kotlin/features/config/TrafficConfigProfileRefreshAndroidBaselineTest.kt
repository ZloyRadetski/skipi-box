// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-derived reference oracle for the typed commit blocks currently inline in
 * TrafficConfigPage and TrafficConfigAutoUpdateWorker at HEAD d8b3e0a. The test invokes
 * the real profile collection updater and document parser/writer, but the policy lambdas
 * below are copied references, not an integration test of the composable or worker wiring.
 */
class TrafficConfigProfileRefreshAndroidBaselineTest {
    @Test
    fun diagnosticCustomResourceFileSurvivesAndroidWriterSectionParserAndTypedReader() {
        val expectedRawValue = "6,Extra,https%3A%2F%2Fgeo.example%2Fextra.dat"
        val writerLines = fetchedDocument.lineSequence()
            .filter { it.contains("resource-custom-file", ignoreCase = true) }
            .toList()
        val analyzedSectionLines = fetchedDocument.analyzeShadowrocketConfig()
            .sections["skipi"].orEmpty()
            .filter { it.contains("resource-custom-file", ignoreCase = true) }
        val parsedSectionValues = fetchedDocument.skipiSectionValues()[SkipiResourceCustomFile].orEmpty()
        val typedResourceSettings = TrafficConfigState(
            id = 99,
            name = "Fetched name",
            rawConfig = fetchedDocument,
            resourceSettings = TrafficConfigResourceSettings(),
        ).withSkipiSettingsReadFromRawConfig().resourceSettings

        // Synthetic diagnostic: keep all stages in one assertion so a failure exposes
        // the precise Android writer/section/parser boundary that drops this value.
        assertEquals(
            listOf(
                listOf("resource-custom-file = $expectedRawValue"),
                listOf("resource-custom-file = $expectedRawValue"),
                listOf(expectedRawValue),
                listOf(TrafficConfigCustomResourceFile(6, "Extra", "https://geo.example/extra.dat")),
                7,
            ),
            listOf(
                writerLines,
                analyzedSectionLines,
                parsedSectionValues,
                typedResourceSettings.customFiles,
                typedResourceSettings.nextCustomFileId,
            ),
        )
    }

    @Test
    fun manualPageCommitAcceptsFetchedPolicyAndUnlockOverridesTheFetchedLock() {
        val initial = profile(
            id = 17,
            name = "Before",
            locked = false,
            autoUpdate = false,
            updateInterval = "48",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "72",
        )
        val url = "https://captured.example/profile.conf"
        val timestamp = 321_000L

        val lockedResult = AppState(
            trafficConfigs = listOf(initial),
            activeTrafficConfigId = initial.id,
            nextTrafficConfigId = initial.id + 1,
        ).withUpdatedTrafficConfigProfile(initial.id) { current ->
            current.androidManualPageCommit(fetchedDocument, url, timestamp, unlock = false)
        }.trafficConfigs.single()

        assertEquals(initial.id, lockedResult.id)
        assertEquals("Fetched name", lockedResult.name)
        assertEquals(url, lockedResult.sourceUrl)
        assertEquals(timestamp, lockedResult.lastUpdatedAtMillis)
        assertTrue(lockedResult.updateLocked)
        assertTrue(lockedResult.autoUpdate)
        assertEquals("6", lockedResult.updateInterval)
        assertTrue(lockedResult.resourceSettings.autoUpdate)
        assertEquals("3", lockedResult.resourceSettings.updateInterval)
        assertEquals("profile-agent", lockedResult.resourceSettings.userAgent)
        assertEquals(7, lockedResult.resourceSettings.nextCustomFileId)
        assertPortableFieldsMatchDocument(lockedResult)

        val unlockedResult = initial.androidManualPageCommit(
            fetchedDocument,
            url,
            timestamp,
            unlock = true,
        )
        assertFalse(unlockedResult.updateLocked)
        assertTrue(unlockedResult.rawConfig.contains("profile-update-locked = false"))
        assertPortableFieldsMatchDocument(unlockedResult)
    }

    @Test
    fun automaticWorkerCommitPreservesCurrentSchedulingButImportsOtherFetchedSettings() {
        val current = profile(
            id = 29,
            name = "Current name",
            locked = true,
            autoUpdate = false,
            updateInterval = "96",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "120",
        )
        val url = "https://captured.example/automatic.conf"
        val timestamp = 654_000L

        val updated = current.androidAutomaticWorkerCommit(fetchedDocument, url, timestamp)

        assertEquals(current.id, updated.id)
        assertEquals("Fetched name", updated.name)
        assertEquals(url, updated.sourceUrl)
        assertEquals(timestamp, updated.lastUpdatedAtMillis)
        assertTrue(updated.updateLocked)
        assertFalse(updated.autoUpdate)
        assertEquals("96", updated.updateInterval)
        assertEquals(23, updated.resourceSettings.source)
        assertEquals("https://geo.example/ip.dat", updated.resourceSettings.customGeoIpUrl)
        assertEquals("https://geo.example/site.dat", updated.resourceSettings.customGeoSiteUrl)
        assertEquals("https://geo.example/direct-v4.dat", updated.resourceSettings.customDirectCidrIpv4Url)
        assertEquals("profile-agent", updated.resourceSettings.userAgent)
        assertEquals(listOf(TrafficConfigCustomResourceFile(6, "Extra", "https://geo.example/extra.dat")), updated.resourceSettings.customFiles)
        assertEquals(7, updated.resourceSettings.nextCustomFileId)
        assertFalse(updated.resourceSettings.autoUpdate)
        assertEquals("120", updated.resourceSettings.updateInterval)
        assertPortableFieldsMatchDocument(updated)
    }

    @Test
    fun lateAndroidCommitTransformsTheCurrentProfileByIdAndKeepsUnrelatedRows() {
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
            androidSettings = TrafficConfigAndroidSettings(enableMux = true),
        ).withSkipiSettingsInRawConfig()
        val latest = clicked.copy(
            name = "Edited while fetching",
            sourceUrl = "https://latest.example/source.conf",
            updateInterval = "36",
            androidSettings = clicked.androidSettings.copy(enableMux = false),
        ).withSkipiSettingsInRawConfig()
        val unrelated = profile(
            id = 38,
            name = "Unrelated",
            locked = false,
            autoUpdate = false,
            updateInterval = "",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "",
        )
        val beforeResponse = AppState(
            trafficConfigs = listOf(latest, unrelated),
            activeTrafficConfigId = unrelated.id,
            nextTrafficConfigId = 50,
        )

        val afterResponse = beforeResponse.withUpdatedTrafficConfigProfile(clicked.id) { current ->
            current.androidManualPageCommit(
                fetchedDocument.replace("profile-name = Fetched name\n", ""),
                capturedUrl = clicked.sourceUrl,
                timestamp = 987_000L,
                unlock = false,
            )
        }

        assertEquals(listOf(clicked.id, unrelated.id), afterResponse.trafficConfigs.map(TrafficConfigState::id))
        assertEquals(beforeResponse.activeTrafficConfigId, afterResponse.activeTrafficConfigId)
        assertEquals(beforeResponse.nextTrafficConfigId, afterResponse.nextTrafficConfigId)
        assertEquals("Edited while fetching", afterResponse.trafficConfigs.first().name)
        assertEquals("https://captured.example/source.conf", afterResponse.trafficConfigs.first().sourceUrl)
        assertFalse(afterResponse.trafficConfigs.first().androidSettings.enableMux)
        assertEquals(unrelated, afterResponse.trafficConfigs.last())
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

    private fun TrafficConfigState.androidManualPageCommit(
        normalizedDocument: String,
        capturedUrl: String,
        timestamp: Long,
        unlock: Boolean,
    ): TrafficConfigState = copy(
        rawConfig = normalizedDocument,
        sourceUrl = capturedUrl,
        updateLocked = if (unlock) false else updateLocked,
        lastUpdatedAtMillis = timestamp,
    ).withSkipiSettingsReadFromRawConfig().let { parsed ->
        parsed.copy(
            sourceUrl = capturedUrl.ifBlank { parsed.sourceUrl },
            updateLocked = if (unlock) false else parsed.updateLocked,
        ).withSkipiSettingsInRawConfig()
    }

    private fun TrafficConfigState.androidAutomaticWorkerCommit(
        normalizedDocument: String,
        capturedUrl: String,
        timestamp: Long,
    ): TrafficConfigState = copy(
        rawConfig = normalizedDocument,
        sourceUrl = capturedUrl,
        lastUpdatedAtMillis = timestamp,
    ).withSkipiSettingsReadFromRawConfig().let { parsed ->
        parsed.copy(
            sourceUrl = capturedUrl.ifBlank { parsed.sourceUrl },
            updateLocked = updateLocked,
            autoUpdate = autoUpdate,
            updateInterval = updateInterval,
            resourceSettings = parsed.resourceSettings.copy(
                autoUpdate = resourceSettings.autoUpdate,
                updateInterval = resourceSettings.updateInterval,
            ),
        ).withSkipiSettingsInRawConfig()
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
            customDirectCidrIpv4Url = "https://old.example/direct-v4.dat",
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
