// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TrafficConfigEditorControllerTest {
    @Test
    fun unchangedCapturedBasicsSkipStoreUpdateButStillNavigate() {
        val initial = profile(id = 30, name = "Unchanged").withSkipiSettingsInRawConfig()
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()

        val result = controller.saveBasicsThenNavigate(
            profile = initial,
            draft = basicsDraft(initial),
            onNavigate = { events += "navigate" },
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(0, store.updateCalls)
        assertEquals(listOf("navigate"), events)
        assertEquals(initial, store.profile(initial.id))
    }

    @Test
    fun changedCapturedBasicsStillTransformsLatestRowBeforeNavigation() {
        val initial = profile(id = 31, name = "Initial").withSkipiSettingsInRawConfig()
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val latest = initial.copy(
            androidSettings = initial.androidSettings.copy(enableFakeDns = true),
            rawConfig = initial.rawConfig.replace("example.net", "latest.example.net"),
        ).withSkipiSettingsInRawConfig()
        store.replace(latest)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "commit" }

        val result = controller.saveBasicsThenNavigate(
            profile = initial,
            draft = basicsDraft(name = "Changed by editor"),
            onNavigate = {
                assertEquals("Changed by editor", store.profile(initial.id)?.name)
                events += "navigate"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(1, store.updateCalls)
        assertTrue(store.profile(initial.id)!!.androidSettings.enableFakeDns)
        assertTrue(store.profile(initial.id)!!.rawConfig.contains("latest.example.net"))
        assertEquals(listOf("commit", "navigate"), events)
    }

    @Test
    fun saveFailureWithoutHandlerPropagatesAndSkipsNavigation() {
        val initial = profile(id = 29, name = "Failure")
        val failure = IllegalStateException("storage unavailable")
        val store = FailingProfileStore(failure)
        val controller = TrafficConfigEditorController(store)
        var navigated = false

        val thrown = assertFailsWith<IllegalStateException> {
            controller.saveBasicsThenNavigate(
                profileId = initial.id,
                draft = basicsDraft(name = "Will fail"),
                onNavigate = { navigated = true },
            )
        }

        assertSame(failure, thrown)
        assertFalse(navigated)
    }

    @Test
    fun handledSaveFailureReturnsFailedReportsCauseAndSkipsNavigation() {
        val initial = profile(id = 28, name = "Handled failure")
        val failure = IllegalStateException("storage unavailable")
        val store = FailingProfileStore(failure)
        var reported: Throwable? = null
        val controller = TrafficConfigEditorController(store, onSaveFailure = { reported = it })
        var navigated = false

        val result = controller.saveBasicsThenNavigate(
            profileId = initial.id,
            draft = basicsDraft(name = "Will fail"),
            onNavigate = { navigated = true },
        )

        val failed = assertIs<TrafficConfigEditorResult.Failed>(result)
        assertSame(failure, failed.cause)
        assertSame(failure, reported)
        assertFalse(navigated)
    }

    @Test
    fun rejectedRawBackStillLeavesWhenSaveFailureHandlerIsInstalled() {
        val initial = profile(id = 27, name = "Rejected raw with handler")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store, onSaveFailure = {})
        val events = mutableListOf<String>()

        val result = controller.leaveRaw(
            profileId = initial.id,
            content = """[Rule]
FINAL,PROXY
FINAL,DIRECT
""",
            onLeave = { events += "leave" },
        )

        assertIs<TrafficConfigEditorResult.Rejected>(result)
        assertEquals(initial, store.profile(initial.id))
        assertEquals(listOf("leave"), events)
    }

    @Test
    fun basicsSaveTransformsTheLatestProfileAndKeepsOtherProfileUntouched() {
        val first = profile(id = 7, name = "Before")
        val other = profile(id = 8, name = "Other")
        val store = InMemoryProfileStore(first, other)
        val controller = TrafficConfigEditorController(store)

        // Simulate a change made elsewhere after the editor was opened. The editor must
        // transform the current row by ID rather than write back a captured snapshot.
        val latest = first.copy(
            name = "Changed elsewhere",
            androidSettings = first.androidSettings.copy(enableFakeDns = true),
            rawConfig = first.rawConfig.replace("example.net", "latest.example.net"),
        ).withSkipiSettingsInRawConfig()
        store.replace(latest)

        val result = controller.saveBasics(
            profileId = first.id,
            draft = TrafficConfigProfileBasicsDraft(
                name = "  Updated profile  ",
                sourceUrl = " https://updated.example/profile.conf ",
                updateLocked = true,
                autoUpdate = true,
                updateInterval = " 18 ",
                resourceAutoUpdate = false,
                resourceUpdateInterval = " 36 ",
            ),
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        val saved = store.profile(first.id)!!
        assertEquals("Updated profile", saved.name)
        assertEquals("https://updated.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertTrue(saved.autoUpdate)
        assertEquals("18", saved.updateInterval)
        assertFalse(saved.resourceSettings.autoUpdate)
        assertEquals("36", saved.resourceSettings.updateInterval)
        assertTrue(saved.androidSettings.enableFakeDns)
        assertTrue(saved.rawConfig.contains("latest.example.net"))
        assertEquals(other, store.profile(other.id))

        val restored = saved.withSkipiSettingsReadFromRawConfig()
        assertEquals(saved.name, restored.name)
        assertEquals(saved.sourceUrl, restored.sourceUrl)
        assertEquals(saved.updateLocked, restored.updateLocked)
        assertEquals(saved.autoUpdate, restored.autoUpdate)
        assertEquals(saved.updateInterval, restored.updateInterval)
        assertEquals(saved.resourceSettings.autoUpdate, restored.resourceSettings.autoUpdate)
        assertEquals(saved.resourceSettings.updateInterval, restored.resourceSettings.updateInterval)
    }

    @Test
    fun generalAndDnsSaveUseExistingPortableTransformsAndRetainUnrelatedDocumentData() {
        val initial = profile(id = 12, name = "Network")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)

        assertIs<TrafficConfigEditorResult.Saved>(
            controller.saveGeneral(profileId = initial.id, ipv6 = false, preferIpv6 = true),
        )
        val settings = TrafficConfigAndroidSettings(
            tunVpnDns = " 10.0.0.1 ",
            proxyDns = listOf(" https://1.1.1.1/dns-query ", "", "https://8.8.8.8/dns-query"),
            directDns = listOf(" 77.88.8.8 ", "77.88.8.1"),
            directDnsDomains = listOf(" geosite:cn ", "domain:ru", "geosite:cn"),
            dnsHosts = listOf("example.org: 1.2.3.4"),
        )
        assertIs<TrafficConfigEditorResult.Saved>(controller.saveDns(initial.id, settings))

        val saved = store.profile(initial.id)!!
        val analysis = saved.rawConfig.analyzeShadowrocketConfig()
        assertEquals("false", analysis.general["ipv6"])
        assertEquals("true", analysis.general["prefer-ipv6"])
        assertEquals("warning", analysis.general["loglevel"])
        assertTrue(analysis.sections["proxy"].orEmpty().contains("server = proxy.example:443"))
        assertEquals(listOf("77.88.8.8", "77.88.8.1"), saved.androidSettings.directDns)
        assertEquals(listOf("geosite:cn", "domain:ru"), saved.androidSettings.directDnsDomains)
        assertEquals(listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"), saved.androidSettings.proxyDns)
        assertEquals(listOf("example.org: 1.2.3.4"), saved.androidSettings.dnsHosts)
        assertTrue(saved.rawConfig.contains("vendor-option = preserve-me"))
    }

    @Test
    fun missingProfileIsReportedWithoutCreatingOrResurrectingIt() {
        val store = InMemoryProfileStore()
        val controller = TrafficConfigEditorController(store)

        val result = controller.saveGeneral(profileId = 91, ipv6 = false, preferIpv6 = false)

        assertIs<TrafficConfigEditorResult.Missing>(result)
        assertTrue(store.profiles.isEmpty())
    }

    @Test
    fun basicsCommitCompletesBeforeEnteringSectionAndBeforeBackNavigation() {
        val initial = profile(id = 31, name = "Basics")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "commit" }

        val sectionResult = controller.saveBasicsThenNavigate(
            profileId = initial.id,
            draft = basicsDraft(name = "Before section"),
            onNavigate = {
                assertEquals("Before section", store.profile(initial.id)?.name)
                events += "enter-section"
            },
        )
        val backResult = controller.saveBasicsThenNavigate(
            profileId = initial.id,
            draft = basicsDraft(name = "Before back"),
            onNavigate = {
                assertEquals("Before back", store.profile(initial.id)?.name)
                events += "back"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(sectionResult)
        assertIs<TrafficConfigEditorResult.Saved>(backResult)
        assertEquals(listOf("commit", "enter-section", "commit", "back"), events)
    }

    @Test
    fun generalBackAndSaveNavigationHappenAfterCommit() {
        val initial = profile(id = 32, name = "General")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "commit" }

        val backResult = controller.saveGeneralThenNavigate(
            profileId = initial.id,
            ipv6 = false,
            preferIpv6 = true,
            onNavigate = {
                assertEquals("false", store.profile(initial.id)!!.rawConfig.analyzeShadowrocketConfig().general["ipv6"])
                events += "back"
            },
        )
        val saveResult = controller.saveGeneralThenNavigate(
            profileId = initial.id,
            ipv6 = true,
            preferIpv6 = false,
            onNavigate = {
                val general = store.profile(initial.id)!!.rawConfig.analyzeShadowrocketConfig().general
                assertEquals("true", general["ipv6"])
                assertEquals("false", general["prefer-ipv6"])
                events += "save"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(backResult)
        assertIs<TrafficConfigEditorResult.Saved>(saveResult)
        assertEquals(listOf("commit", "back", "commit", "save"), events)
    }

    @Test
    fun dnsBackAndSaveNavigationHappenAfterCommitAndMissingStillLeaves() {
        val initial = profile(id = 33, name = "DNS")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "commit" }
        val dns = initial.androidSettings.copy(directDns = listOf(" 9.9.9.9 "))

        val backResult = controller.saveDnsThenNavigate(
            profileId = initial.id,
            settings = dns,
            onNavigate = {
                assertEquals(listOf("9.9.9.9"), store.profile(initial.id)!!.androidSettings.directDns)
                events += "back"
            },
        )
        val saveResult = controller.saveDnsThenNavigate(
            profileId = initial.id,
            settings = dns.copy(directDns = listOf(" 1.1.1.1 ")),
            onNavigate = {
                assertEquals(listOf("1.1.1.1"), store.profile(initial.id)!!.androidSettings.directDns)
                events += "save"
            },
        )
        val missingResult = controller.saveDnsThenNavigate(
            profileId = 99,
            settings = dns,
            onNavigate = { events += "missing-back" },
        )

        assertIs<TrafficConfigEditorResult.Saved>(backResult)
        assertIs<TrafficConfigEditorResult.Saved>(saveResult)
        assertIs<TrafficConfigEditorResult.Missing>(missingResult)
        assertEquals(listOf("commit", "back", "commit", "save", "missing-back"), events)
        assertEquals(listOf(initial.id), store.profiles.keys.toList())
    }

    @Test
    fun validRawBackSavesNormalizedTextBeforeLeaving() {
        val initial = profile(id = 15, name = "Raw")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "save" }

        val result = controller.leaveRaw(
            profileId = initial.id,
            content = "[General]\nipv6 = false\n\n",
            onLeave = { events += "leave" },
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals("[General]\nipv6 = false\n", store.profile(initial.id)!!.rawConfig)
        assertEquals(listOf("save", "leave"), events)
    }

    @Test
    fun rejectedRawBackLeavesWithoutChangingTheProfile() {
        val initial = profile(id = 22, name = "Invalid raw")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "save" }

        val result = controller.leaveRaw(
            profileId = initial.id,
            content = "[Rule]\nFINAL,PROXY\nFINAL,DIRECT\n",
            onLeave = { events += "leave" },
        )

        assertIs<TrafficConfigEditorResult.Rejected>(result)
        assertEquals(initial, store.profile(initial.id))
        assertEquals(listOf("leave"), events)
    }

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        sourceUrl = "https://old.example/profile.conf",
        updateLocked = false,
        autoUpdate = true,
        updateInterval = "24",
        rawConfig = """# external comment
[General]
loglevel = warning

[Proxy]
server = proxy.example:443

[Rule]
DOMAIN-SUFFIX,example.net,DIRECT

[SKIPI]
profile-name = $name
profile-update-url = https://old.example/profile.conf
profile-update-locked = false
profile-auto-update = true
profile-update-interval = 24
resource-auto-update = true
resource-update-interval = 72

[Vendor]
vendor-option = preserve-me
""".trimIndent(),
    ).withSkipiSettingsReadFromRawConfig()

    private fun basicsDraft(name: String) = TrafficConfigProfileBasicsDraft(
        name = name,
        sourceUrl = "https://source.example/$name",
        updateLocked = false,
        autoUpdate = true,
        updateInterval = "24",
        resourceAutoUpdate = true,
        resourceUpdateInterval = "72",
    )

    private fun basicsDraft(profile: TrafficConfigState) = TrafficConfigProfileBasicsDraft(
        name = profile.name,
        sourceUrl = profile.sourceUrl,
        updateLocked = profile.updateLocked,
        autoUpdate = profile.autoUpdate,
        updateInterval = profile.updateInterval,
        resourceAutoUpdate = profile.resourceSettings.autoUpdate,
        resourceUpdateInterval = profile.resourceSettings.updateInterval,
    )

    private class InMemoryProfileStore(vararg profiles: TrafficConfigState) : TrafficConfigEditorProfileStore {
        val profiles = linkedMapOf(*profiles.map { it.id to it }.toTypedArray())
        var onUpdate: (() -> Unit)? = null
        var updateCalls: Int = 0

        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean {
            updateCalls += 1
            val current = profiles[profileId] ?: return false
            val updated = transform(current)
            profiles[profileId] = updated
            onUpdate?.invoke()
            return true
        }

        fun replace(profile: TrafficConfigState) {
            profiles[profile.id] = profile
        }

        fun profile(id: Int): TrafficConfigState? = profiles[id]
    }

    private class FailingProfileStore(
        private val failure: Exception,
    ) : TrafficConfigEditorProfileStore {
        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean = throw failure
    }
}
