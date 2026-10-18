// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigNetworkTransportCellular
import features.config.TrafficConfigNetworkTransportWifi
import features.config.TrafficConfigState
import features.config.withSkipiSettingsReadFromRawConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TrafficConfigAdditionalSectionsControllerTest {
    @Test
    fun tunnelSaveUsesLatestRowAndTheFullCapturedSettingsSnapshot() {
        val opened = profile(41, "Opened")
        val draftSettings = opened.androidSettings.copy(
            enableSniffing = false,
            enableSniffingRouteOnly = false,
            enableMux = true,
            enableFragment = true,
        )
        val latest = opened.copy(
            name = "Changed while editor is open",
            androidSettings = opened.androidSettings.copy(
                enableFakeDns = true,
                tunVpnDns = "9.9.9.9",
                directDns = listOf("1.1.1.1"),
            ),
            rawConfig = opened.rawConfig.replace("example.net", "latest.example.net"),
            networkActivation = TrafficConfigNetworkActivation(
                enabled = true,
                transport = TrafficConfigNetworkTransportCellular,
            ),
        )
        val sibling = profile(42, "Sibling")
        val store = InMemoryProfileStore(latest, sibling)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "commit" }

        val result = controller.saveTunnelThenNavigate(
            profileId = opened.id,
            settings = draftSettings,
            muxConcurrency = " 19 ",
            onNavigate = {
                val saved = store.profile(opened.id)!!
                assertEquals(draftSettings.copy(muxConcurrency = "19"), saved.androidSettings)
                assertEquals("Changed while editor is open", saved.name)
                assertTrue(saved.rawConfig.contains("latest.example.net"))
                assertEquals(latest.networkActivation, saved.networkActivation)
                events += "navigate"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(result)
        assertEquals(1, store.updateCalls)
        assertEquals(sibling, store.profile(sibling.id))
        assertEquals(listOf("commit", "navigate"), events)
    }

    @Test
    fun networkActivationSaveClampsBothBoundsAndNavigatesAfterTheUpdate() {
        val initial = profile(43, "Network")
        val store = InMemoryProfileStore(initial)
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()
        store.onUpdate = { events += "commit" }

        val upperBoundResult = controller.saveNetworkActivationThenNavigate(
            profileId = initial.id,
            enabled = true,
            transport = Int.MAX_VALUE,
            onNavigate = {
                assertEquals(
                    TrafficConfigNetworkActivation(true, TrafficConfigNetworkTransportCellular),
                    store.profile(initial.id)?.networkActivation,
                )
                events += "navigate"
            },
        )

        assertIs<TrafficConfigEditorResult.Saved>(upperBoundResult)
        assertEquals(listOf("commit", "navigate"), events)

        val lowerBoundResult = controller.saveNetworkActivation(
            profileId = initial.id,
            enabled = false,
            transport = Int.MIN_VALUE,
        )

        assertIs<TrafficConfigEditorResult.Saved>(lowerBoundResult)
        assertEquals(
            TrafficConfigNetworkActivation(false, TrafficConfigNetworkTransportWifi),
            store.profile(initial.id)?.networkActivation,
        )
        assertEquals(listOf("commit", "navigate", "commit"), events)
    }

    @Test
    fun tunnelAndNetworkNavigationStillRunWhenTheProfileIsMissing() {
        val store = InMemoryProfileStore()
        val controller = TrafficConfigEditorController(store)
        val events = mutableListOf<String>()

        val tunnelResult = controller.saveTunnelThenNavigate(
            profileId = 99,
            settings = TrafficConfigAndroidSettings(),
            muxConcurrency = "8",
            onNavigate = { events += "tunnel" },
        )
        val networkResult = controller.saveNetworkActivationThenNavigate(
            profileId = 99,
            enabled = true,
            transport = TrafficConfigNetworkTransportCellular,
            onNavigate = { events += "network" },
        )

        assertIs<TrafficConfigEditorResult.Missing>(tunnelResult)
        assertIs<TrafficConfigEditorResult.Missing>(networkResult)
        assertEquals(2, store.updateCalls)
        assertEquals(listOf("tunnel", "network"), events)
        assertEquals(emptyList(), store.profileIds())
    }

    private fun profile(id: Int, name: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = """[General]
loglevel = warning

[Proxy]
server = proxy.example:443

[Rule]
DOMAIN-SUFFIX,example.net,DIRECT

[SKIPI]
profile-name = $name
""",
    ).withSkipiSettingsReadFromRawConfig()

    private class InMemoryProfileStore(vararg initialProfiles: TrafficConfigState) : TrafficConfigEditorProfileStore {
        private val profiles = linkedMapOf(*initialProfiles.map { it.id to it }.toTypedArray())
        var updateCalls: Int = 0
        var onUpdate: (() -> Unit)? = null

        override fun update(
            profileId: Int,
            transform: (TrafficConfigState) -> TrafficConfigState,
        ): Boolean {
            updateCalls += 1
            val current = profiles[profileId] ?: return false
            profiles[profileId] = transform(current)
            onUpdate?.invoke()
            return true
        }

        fun profile(profileId: Int): TrafficConfigState? = profiles[profileId]
        fun profileIds(): List<Int> = profiles.keys.toList()
    }
}
