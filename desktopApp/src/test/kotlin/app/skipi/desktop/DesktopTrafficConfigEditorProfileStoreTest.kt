// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.config.withGeneralOptions
import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.skipiSectionValues
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopTrafficConfigEditorProfileStoreTest {
    @Test
    fun failedLibraryCommitIsReportedWithoutPublishingMutationOrLeavingEditor() {
        val profile = ConfigProfile(9, "Before", "[General]\nloglevel = warning\n")
        val initial = DesktopConfigLibrary(
            selectedConfigId = profile.id,
            configs = listOf(profile),
            nextConfigId = 10,
        )
        var latest = initial
        var publishCalls = 0
        val failure = IllegalStateException("disk write failed")
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { Result.failure(failure) },
            publishLibrary = {
                publishCalls += 1
                latest = it
            },
        )
        val store = DesktopTrafficConfigEditorProfileStore(
            repository = repository,
            resourceUserAgentFallback = "Skipi/9.8.7/Desktop",
        )
        var reported: Throwable? = null
        val controller = TrafficConfigEditorController(store, onSaveFailure = { reported = it })
        var navigated = false

        val result = controller.saveBasicsThenNavigate(
            profileId = profile.id,
            draft = app.skipi.app.config.TrafficConfigProfileBasicsDraft(
                name = "Edited",
                sourceUrl = "https://example.test/profile.conf",
                updateLocked = true,
                autoUpdate = true,
                updateInterval = "24",
                resourceAutoUpdate = true,
                resourceUpdateInterval = "72",
            ),
            onNavigate = { navigated = true },
        )

        val failed = assertIs<TrafficConfigEditorResult.Failed>(result)
        assertSame(failure, failed.cause)
        assertSame(failure, reported)
        assertFalse(navigated)
        assertEquals(0, publishCalls)
        assertEquals(initial, latest)
    }

    @Test
    fun controllerRawSaveUsesAndroidSkipiNormalizationAndUpdatesLatestDesktopEnvelope() {
        val active = ConfigProfile(7, "Active", "[General]\nloglevel = warning\n")
        val target = ConfigProfile(
            id = 9,
            name = "Envelope before",
            content = """[General]
loglevel = warning

[SKIPI]
profile-name = Envelope before
profile-update-url = https://old.example/profile.conf
profile-update-locked = false
profile-auto-update = true
profile-update-interval = 24
resource-auto-update = true
resource-update-interval = 72
resource-user-agent = Existing profile agent

[Vendor]
vendor-option = keep-on-raw-save
""",
            sourceUrl = "https://old.example/profile.conf",
            updateLocked = false,
            lastUpdatedAtMillis = 456L,
        )
        val sibling = ConfigProfile(13, "Sibling", "[General]\nloglevel = info\n")
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, target, sibling),
            nextConfigId = 18,
        )
        var latest = initial
        var persisted = initial
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { persisted = it; Result.success(Unit) },
            publishLibrary = { latest = it },
        )
        val store = DesktopTrafficConfigEditorProfileStore(
            repository = repository,
            resourceUserAgentFallback = "Skipi/9.8.7/Desktop",
        )
        val controller = TrafficConfigEditorController(store)
        val rawDraft = """# edited in Android raw editor
[General]
loglevel = debug

[Proxy]
server = raw.example:443

[SKIPI]
profile-name = Raw editor name
profile-update-url = https://raw.example/profile.conf
profile-update-locked = true
profile-auto-update = false
profile-update-interval = 48
resource-auto-update = false
resource-update-interval = 96
resource-user-agent = Raw editor agent

[Vendor]
vendor-option = keep-on-raw-save"""

        assertIs<TrafficConfigEditorResult.Saved>(controller.saveRaw(target.id, rawDraft))

        val saved = latest.configs.single { it.id == target.id }
        val typed = saved.toTrafficConfigState("Skipi/9.8.7/Desktop")
        val skipi = saved.content.skipiSectionValues()
        assertEquals(active.id, latest.selectedConfigId)
        assertEquals(initial.nextConfigId, latest.nextConfigId)
        assertEquals(listOf(active, saved, sibling), latest.configs)
        assertEquals("Raw editor name", saved.name)
        assertEquals("https://raw.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertEquals(target.lastUpdatedAtMillis, saved.lastUpdatedAtMillis)
        assertEquals("Raw editor name", typed.name)
        assertEquals("https://raw.example/profile.conf", typed.sourceUrl)
        assertTrue(typed.updateLocked)
        assertFalse(typed.autoUpdate)
        assertEquals("48", typed.updateInterval)
        assertFalse(typed.resourceSettings.autoUpdate)
        assertEquals("96", typed.resourceSettings.updateInterval)
        assertEquals("Raw editor agent", typed.resourceSettings.userAgent)
        assertEquals("Raw editor name", skipi["profile-name"]?.last())
        assertEquals("https://raw.example/profile.conf", skipi["profile-update-url"]?.last())
        assertEquals("false", skipi["profile-auto-update"]?.last())
        assertEquals("false", skipi["resource-auto-update"]?.last())
        assertTrue(saved.content.contains("vendor-option = keep-on-raw-save"))
        assertTrue(saved.content.endsWith("\n"))
        assertEquals(latest, persisted)
    }

    @Test
    fun updateUsesInjectedLibraryTransformBeforeHostSideEffects() {
        val active = ConfigProfile(3, "Active", "[General]\nloglevel = warning\n")
        val edited = ConfigProfile(5, "Edited", "[General]\nloglevel = warning\n")
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, edited),
            nextConfigId = 6,
        )
        val latestEdited = edited.copy(content = "[General]\nloglevel = debug\n")
        var latest = initial.copy(
            configs = listOf(active, latestEdited),
            nextConfigId = 23,
        )
        var transformCallbackCalls = 0
        var rowTransformCalls = 0
        val events = mutableListOf<String>()
        val store = DesktopTrafficConfigEditorProfileStore(
            resourceUserAgentFallback = "Skipi/9.8.7/Desktop",
            onTransformConfigLibrary = { transform ->
                transformCallbackCalls += 1
                events += "host-transform-callback"
                val updated = transform(latest)
                events += "library-transform-returned"
                latest = updated
                events += "host-side-effects"
                updated
            },
        )

        assertTrue(
            store.update(edited.id) { state ->
                rowTransformCalls += 1
                assertEquals("debug", state.rawConfig.analyzeShadowrocketConfig().general["loglevel"])
                events += "profile-transform"
                state.withGeneralOptions(ipv6 = false, preferIpv6 = true)
            },
        )

        assertEquals(1, transformCallbackCalls)
        assertEquals(1, rowTransformCalls)
        assertEquals(
            listOf(
                "host-transform-callback",
                "profile-transform",
                "library-transform-returned",
                "host-side-effects",
            ),
            events,
        )
        assertEquals(active, latest.configs.single { it.id == active.id })
        assertEquals(23, latest.nextConfigId)
        assertEquals("false", latest.configs.single { it.id == edited.id }.content.analyzeShadowrocketConfig().general["ipv6"])
    }

    @Test
    fun updateTransformsLatestProfileByIdAndPreservesLibraryEnvelopeAndResourceUaSemantics() {
        val active = ConfigProfile(7, "Active", "[General]\nloglevel = warning\n")
        val target = ConfigProfile(
            id = 9,
            name = "Remote",
            content = "[General]\nloglevel = warning\n",
            sourceUrl = "https://example.test/remote",
            updateLocked = true,
            lastUpdatedAtMillis = 100L,
        )
        val explicitEmptyUa = ConfigProfile(
            id = 11,
            name = "Empty UA",
            content = "[General]\nloglevel = warning\n\n[SKIPI]\nresource-user-agent = \n",
        )
        val initial = DesktopConfigLibrary(
            selectedConfigId = active.id,
            configs = listOf(active, target, explicitEmptyUa),
            nextConfigId = 12,
        )
        var latest = initial
        var persisted = initial
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { persisted = it; Result.success(Unit) },
            publishLibrary = { latest = it },
        )
        val fallback = "Skipi/9.8.7/Desktop"
        val store = DesktopTrafficConfigEditorProfileStore(
            repository = repository,
            resourceUserAgentFallback = fallback,
        )

        // A local operation completes after the editor was opened. Updating by ID must apply
        // to this latest row and keep the current selection, counter, and sibling rows.
        val latestTarget = target.copy(
            content = """[General]
loglevel = debug

[Proxy]
server = latest.example:443
""",
        )
        val addedNeighbor = ConfigProfile(30, "Added while editing", "[General]\nloglevel = info\n")
        val current = initial.copy(
            selectedConfigId = addedNeighbor.id,
            configs = listOf(active, latestTarget, explicitEmptyUa, addedNeighbor),
            nextConfigId = 41,
        )
        latest = current
        persisted = current

        assertTrue(
            store.update(target.id) { state ->
                assertEquals(fallback, state.resourceSettings.userAgent)
                state.withGeneralOptions(ipv6 = false, preferIpv6 = true)
            },
        )
        assertTrue(
            store.update(explicitEmptyUa.id) { state ->
                assertEquals("", state.resourceSettings.userAgent)
                state.withGeneralOptions(ipv6 = true, preferIpv6 = false)
            },
        )

        assertEquals(current.selectedConfigId, latest.selectedConfigId)
        assertEquals(current.nextConfigId, latest.nextConfigId)
        assertEquals(current.configs.map(ConfigProfile::id), latest.configs.map(ConfigProfile::id))
        assertEquals(active, latest.configs.single { it.id == active.id })
        assertEquals(addedNeighbor, latest.configs.single { it.id == addedNeighbor.id })
        val savedTarget = latest.configs.single { it.id == target.id }
        assertEquals(target.name, savedTarget.name)
        assertEquals(target.sourceUrl, savedTarget.sourceUrl)
        assertEquals(target.updateLocked, savedTarget.updateLocked)
        assertEquals(target.lastUpdatedAtMillis, savedTarget.lastUpdatedAtMillis)
        val targetGeneral = savedTarget.content.analyzeShadowrocketConfig().general
        assertEquals("false", targetGeneral["ipv6"])
        assertEquals("true", targetGeneral["prefer-ipv6"])
        assertTrue(savedTarget.content.contains("server = latest.example:443"))
        val savedEmptyUa = latest.configs.single { it.id == explicitEmptyUa.id }
        assertEquals("", savedEmptyUa.toTrafficConfigState("Skipi/9.8.7/Desktop").resourceSettings.userAgent)
        assertTrue(savedEmptyUa.content.contains("resource-user-agent ="))
        assertEquals(latest, persisted)
    }

    @Test
    fun missingProfileReturnsFalseAndDoesNotAddAConfig() {
        val existing = ConfigProfile(7, "Existing", "[General]\nloglevel = warning\n")
        val initial = DesktopConfigLibrary(
            selectedConfigId = existing.id,
            configs = listOf(existing),
            nextConfigId = 8,
        )
        var latest = initial
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { latest },
            saveLibrary = { Result.success(Unit) },
            publishLibrary = { latest = it },
        )
        val store = DesktopTrafficConfigEditorProfileStore(
            repository = repository,
            resourceUserAgentFallback = "Skipi/9.8.7/Desktop",
        )

        assertFalse(store.update(99) { state: TrafficConfigState -> state.withGeneralOptions(false, false) })
        assertEquals(initial, latest)
    }
}
