// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import app.skipi.app.config.toTrafficConfigState
import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// These tests exercise the Desktop bridge into the shared worker execution helper. They do not
// cover WorkManager/context/input/UA/options integration, which remains part of Android's host.
class DesktopTrafficConfigAutoRefreshTest {
    @Test
    fun absentAndIneligibleProfilesCompleteWithoutCallingRefreshPorts() = runBlocking {
        val eligible = storedProfile(id = 9, name = "Eligible")
        val cases = listOf(
            GuardCase("absent", eligible, requestedId = 99),
            GuardCase("automatic refresh disabled", storedProfile(id = 9, name = "Disabled", autoUpdate = false)),
            GuardCase("locked", storedProfile(id = 9, name = "Locked", updateLocked = true)),
            GuardCase("blank URL", storedProfile(id = 9, name = "Blank URL", sourceUrl = " \t\n")),
        )

        for (case in cases) {
            val initial = libraryOf(case.profile)
            var latest = initial
            var saveCalls = 0
            var publishCalls = 0
            var fetchCalls = 0
            var reconcileCalls = 0
            var clockCalls = 0
            val repository = DesktopTrafficConfigRepository(
                initialLibrary = initial,
                readLibrary = { latest },
                saveLibrary = { saveCalls += 1; Result.success(Unit) },
                publishLibrary = { publishCalls += 1; latest = it },
            )
            val refresh = DesktopTrafficConfigAutoRefresh(
                readLibrary = { latest },
                repository = repository,
                fetch = { _, _ -> fetchCalls += 1; fetchedDocument },
                reconcile = { reconcileCalls += 1 },
                nowMillis = { clockCalls += 1; 123L },
                resourceUserAgentFallback = desktopUserAgent,
            )

            assertEquals(
                TrafficConfigAutoUpdateExecutionResult.Success,
                refresh.run(case.requestedId),
                "${case.label} should complete as a successful preflight skip",
            )
            assertEquals(0, fetchCalls, "${case.label} should not fetch")
            assertEquals(0, saveCalls, "${case.label} should not save")
            assertEquals(0, publishCalls, "${case.label} should not publish")
            assertEquals(0, reconcileCalls, "${case.label} should not reconcile")
            assertEquals(0, clockCalls, "${case.label} should not request a commit timestamp")
            assertEquals(initial, latest)
        }
    }

    @Test
    fun capturedDesktopProfileUsesFallbackCustomAndExplicitlyBlankResourceUserAgents() = runBlocking {
        val cases = listOf(
            UserAgentCase("missing setting uses Desktop fallback", null, desktopUserAgent),
            UserAgentCase("custom setting is preserved", "Profile-specific agent", "Profile-specific agent"),
            UserAgentCase("explicit blank setting stays blank", "", ""),
        )

        for ((label, storedUserAgent, expectedUserAgent) in cases) {
            val profile = storedProfile(
                id = 15,
                name = "Captured $label",
                sourceUrl = "  https://captured.example/profile.conf  ",
                resourceUserAgent = storedUserAgent,
            )
            val initial = libraryOf(profile)
            var latest = initial
            var persisted = initial
            var receivedProfile: TrafficConfigState? = null
            var receivedUrl: String? = null
            val repository = DesktopTrafficConfigRepository(
                initialLibrary = initial,
                readLibrary = { latest },
                saveLibrary = { persisted = it; Result.success(Unit) },
                publishLibrary = { latest = it },
            )
            val refresh = DesktopTrafficConfigAutoRefresh(
                readLibrary = { latest },
                repository = repository,
                fetch = { captured, url ->
                    receivedProfile = captured
                    receivedUrl = url
                    fetchedDocument
                },
                reconcile = {},
                nowMillis = { 123L },
                resourceUserAgentFallback = desktopUserAgent,
            )

            assertEquals(TrafficConfigAutoUpdateExecutionResult.Success, refresh.run(profile.id), label)
            val captured = assertNotNull(receivedProfile, "$label should pass the selected Desktop profile to fetch")
            assertEquals(profile.id, captured.id)
            assertEquals(profile.name, captured.name)
            assertEquals(profile.sourceUrl, captured.sourceUrl)
            assertEquals(expectedUserAgent, captured.resourceSettings.userAgent, label)
            assertEquals("https://captured.example/profile.conf", receivedUrl, label)
            assertEquals(latest, persisted, label)
        }
    }

    @Test
    fun fetchCommitsIntoTheLatestSameIdRowAndPreservesLibraryAndSchedulingMetadata() = runBlocking {
        val active = storedProfile(id = 2, name = "Active", sourceUrl = "https://active.example/profile")
        val captured = storedProfile(
            id = 9,
            name = "Captured name",
            sourceUrl = "  https://captured.example/subscription  ",
        )
        val sibling = storedProfile(id = 13, name = "Existing sibling", sourceUrl = "https://sibling.example")
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, captured, sibling),
            nextConfigId = 18,
        )
        var latest = initial
        var persisted = initial
        val events = mutableListOf<String>()
        val currentTarget = storedProfile(
            id = captured.id,
            name = "Latest name before commit",
            sourceUrl = "https://latest.example/replaced-during-fetch",
            updateLocked = true,
            lastUpdatedAtMillis = 44L,
            autoUpdate = false,
            updateInterval = "13",
            resourceAutoUpdate = false,
            resourceUpdateInterval = "17",
            resourceUserAgent = "Latest profile agent",
        )
        val addedDuringFetch = storedProfile(id = 30, name = "Added during fetch")
        val libraryDuringFetch = initial.copy(
            selectedConfigId = addedDuringFetch.id,
            configs = listOf(active, currentTarget, sibling, addedDuringFetch),
            nextConfigId = 41,
        )
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { events += "read"; latest },
            saveLibrary = { events += "save"; persisted = it; Result.success(Unit) },
            publishLibrary = { events += "publish"; latest = it },
        )
        val refresh = DesktopTrafficConfigAutoRefresh(
            readLibrary = { events += "read"; latest },
            repository = repository,
            fetch = { capturedProfile, url ->
                events += "fetch"
                assertEquals(captured.id, capturedProfile.id)
                assertEquals("Captured name", capturedProfile.name)
                assertEquals(captured.sourceUrl, capturedProfile.sourceUrl)
                assertEquals("https://captured.example/subscription", url)
                latest = libraryDuringFetch
                fetchedDocument
            },
            reconcile = { events += "reconcile" },
            nowMillis = { events += "timestamp"; 9_876L },
            resourceUserAgentFallback = desktopUserAgent,
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Success, refresh.run(captured.id))

        assertEquals(listOf("read", "fetch", "read", "timestamp", "save", "publish", "reconcile"), events)
        assertEquals(libraryDuringFetch.selectedConfigId, latest.selectedConfigId)
        assertEquals(libraryDuringFetch.nextConfigId, latest.nextConfigId)
        assertEquals(libraryDuringFetch.configs.map(ConfigProfile::id), latest.configs.map(ConfigProfile::id))
        assertEquals(active, latest.configs.single { it.id == active.id })
        assertEquals(sibling, latest.configs.single { it.id == sibling.id })
        assertEquals(addedDuringFetch, latest.configs.single { it.id == addedDuringFetch.id })

        val saved = latest.configs.single { it.id == captured.id }
        assertEquals("Provider response name", saved.name)
        assertEquals("https://captured.example/subscription", saved.sourceUrl)
        assertEquals(9_876L, saved.lastUpdatedAtMillis)
        assertEquals("debug", saved.content.analyzeShadowrocketConfig().general["loglevel"])
        val savedState = saved.toTrafficConfigState(desktopUserAgent)
        assertTrue(savedState.updateLocked)
        assertFalse(savedState.autoUpdate)
        assertEquals("13", savedState.updateInterval)
        assertEquals(false, savedState.resourceSettings.autoUpdate)
        assertEquals("17", savedState.resourceSettings.updateInterval)
        assertEquals("Provider response agent", savedState.resourceSettings.userAgent)
        assertEquals(latest, persisted)
    }

    @Test
    fun deletionDuringFetchIsNotUndoneAndStillRunsReconciliation() = runBlocking {
        val target = storedProfile(id = 9, name = "Removed while fetching")
        val neighbor = storedProfile(id = 13, name = "Still present")
        val initial = DesktopConfigLibrary(
            selectedConfigId = target.id,
            configs = listOf(target, neighbor),
            nextConfigId = 20,
        )
        val deleted = DesktopConfigLibraries.remove(initial, target.id)
        var latest = initial
        var persisted = initial
        val events = mutableListOf<String>()
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { events += "save"; persisted = it; Result.success(Unit) },
            publishLibrary = { events += "publish"; latest = it },
        )
        val refresh = DesktopTrafficConfigAutoRefresh(
            readLibrary = { latest },
            repository = repository,
            fetch = { _, _ -> events += "fetch"; latest = deleted; fetchedDocument },
            reconcile = { events += "reconcile" },
            nowMillis = { 9_876L },
            resourceUserAgentFallback = desktopUserAgent,
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Success, refresh.run(target.id))

        assertEquals(listOf("fetch", "save", "publish", "reconcile"), events)
        assertEquals(deleted, latest)
        assertEquals(deleted, persisted)
        assertTrue(latest.configs.none { it.id == target.id })
        assertEquals(neighbor, latest.configs.single())
    }

    @Test
    fun saveFailureReturnsRetryWithoutPublishingOrReconciling() = runBlocking {
        val target = storedProfile(id = 9, name = "Save failure")
        val initial = libraryOf(target)
        var latest = initial
        var publishCalls = 0
        val failure = IllegalStateException("library save failed")
        val events = mutableListOf<String>()
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { events += "save"; Result.failure(failure) },
            publishLibrary = { publishCalls += 1; latest = it },
        )
        val refresh = DesktopTrafficConfigAutoRefresh(
            readLibrary = { latest },
            repository = repository,
            fetch = { _, _ -> events += "fetch"; fetchedDocument },
            reconcile = { events += "reconcile" },
            nowMillis = { 9_876L },
            resourceUserAgentFallback = desktopUserAgent,
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, refresh.run(target.id))
        assertEquals(listOf("fetch", "save"), events)
        assertEquals(0, publishCalls)
        assertEquals(initial, latest)
    }

    @Test
    fun reconciliationFailureReturnsRetryAfterTheLibraryWasCommitted() = runBlocking {
        val target = storedProfile(id = 9, name = "Reconcile failure")
        val initial = libraryOf(target)
        var latest = initial
        var persisted = initial
        val events = mutableListOf<String>()
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { events += "save"; persisted = it; Result.success(Unit) },
            publishLibrary = { events += "publish"; latest = it },
        )
        val refresh = DesktopTrafficConfigAutoRefresh(
            readLibrary = { latest },
            repository = repository,
            fetch = { _, _ -> events += "fetch"; fetchedDocument },
            reconcile = { events += "reconcile"; throw IllegalStateException("reconcile failed") },
            nowMillis = { 9_876L },
            resourceUserAgentFallback = desktopUserAgent,
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, refresh.run(target.id))
        assertEquals(listOf("fetch", "save", "publish", "reconcile"), events)
        assertEquals(latest, persisted)
        assertEquals("debug", latest.configs.single().content.analyzeShadowrocketConfig().general["loglevel"])
        assertEquals(9_876L, latest.configs.single().lastUpdatedAtMillis)
    }

    @Test
    fun fetchFailuresIncludingCancellationReturnRetryWithoutDesktopSideEffects() = runBlocking {
        val failures = listOf<Throwable>(
            IllegalStateException("fetch failed"),
            CancellationException("fetch cancelled"),
        )

        for (failure in failures) {
            val target = storedProfile(id = 9, name = "Fetch failure")
            val initial = libraryOf(target)
            var latest = initial
            var saveCalls = 0
            var publishCalls = 0
            var reconcileCalls = 0
            val events = mutableListOf<String>()
            val repository = DesktopTrafficConfigRepository(
                initialLibrary = initial,
                readLibrary = { latest },
                saveLibrary = { saveCalls += 1; Result.success(Unit) },
                publishLibrary = { publishCalls += 1; latest = it },
            )
            val refresh = DesktopTrafficConfigAutoRefresh(
                readLibrary = { latest },
                repository = repository,
                fetch = { _, _ -> events += "fetch"; throw failure },
                reconcile = { reconcileCalls += 1 },
                nowMillis = { 9_876L },
                resourceUserAgentFallback = desktopUserAgent,
            )

            assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, refresh.run(target.id))
            assertEquals(listOf("fetch"), events)
            assertEquals(0, saveCalls)
            assertEquals(0, publishCalls)
            assertEquals(0, reconcileCalls)
            assertEquals(initial, latest)
        }
    }

    private fun storedProfile(
        id: Int,
        name: String,
        sourceUrl: String = "https://provider.example/profile.conf",
        updateLocked: Boolean = false,
        lastUpdatedAtMillis: Long = 0L,
        autoUpdate: Boolean = true,
        updateInterval: String = "24",
        resourceAutoUpdate: Boolean = true,
        resourceUpdateInterval: String = "24",
        resourceUserAgent: String? = null,
    ): ConfigProfile {
        val userAgentLine = resourceUserAgent?.let { "\nresource-user-agent = $it" }.orEmpty()
        val content = """[General]
loglevel = warning

[SKIPI]
profile-name = $name
profile-update-url = $sourceUrl
profile-update-locked = $updateLocked
profile-auto-update = $autoUpdate
profile-update-interval = $updateInterval
resource-auto-update = $resourceAutoUpdate
resource-update-interval = $resourceUpdateInterval$userAgentLine
"""
        return ConfigProfile(
            id = id,
            name = name,
            content = content,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
        )
    }

    private fun libraryOf(profile: ConfigProfile) = DesktopConfigLibrary(
        selectedConfigId = profile.id,
        configs = listOf(profile),
        nextConfigId = profile.id + 1,
    )

    private data class GuardCase(
        val label: String,
        val profile: ConfigProfile,
        val requestedId: Int = profile.id,
    )

    private data class UserAgentCase(
        val label: String,
        val storedUserAgent: String?,
        val expectedUserAgent: String,
    )

    private companion object {
        const val desktopUserAgent = "Skipi/9.8.7/Desktop"
        const val fetchedDocument = """[General]
loglevel = debug

[SKIPI]
profile-name = Provider response name
profile-update-url = https://provider.example/response-url
profile-update-locked = false
profile-auto-update = true
profile-update-interval = 99
resource-auto-update = true
resource-update-interval = 99
resource-user-agent = Provider response agent
"""
    }
}
