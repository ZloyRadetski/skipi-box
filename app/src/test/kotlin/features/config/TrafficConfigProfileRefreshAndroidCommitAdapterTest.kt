// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.ProxyServerState
import app.skipi.app.config.TrafficConfigProfileRefreshMode
import features.proxy.server.model.VLESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class TrafficConfigProfileRefreshAndroidCommitAdapterTest {
    @Test
    fun commitAppliesRefreshToLatestProfileByIdAndPreservesAppStateCatalogMetadata() {
        val clicked = profile(41, "Clicked", "https://captured.example/profile.conf")
        val latest = clicked.copy(
            name = "Edited while fetching",
            sourceUrl = "https://latest.example/profile.conf",
            androidSettings = clicked.androidSettings.copy(enableMux = false),
        ).withSkipiSettingsInRawConfig()
        val unrelated = profile(42, "Unrelated", "https://other.example/profile.conf")
        val proxy = ProxyServerState(
            id = 18,
            groupId = 0,
            server = VLESS(remarks = "Keep", id = "keep", server = "keep.example", port = "443"),
        )
        val state = AppState(
            trafficConfigs = listOf(latest, unrelated),
            activeTrafficConfigId = unrelated.id,
            nextTrafficConfigId = 70,
            proxyServers = listOf(proxy),
            nextProxyServerId = 80,
            selectedProxyServerId = proxy.id,
        )

        val updated = state.withCommittedTrafficConfigRefresh(
            configId = clicked.id,
            normalizedDocument = fetchedDocument,
            capturedUrl = clicked.sourceUrl,
            timestamp = 987_000L,
            mode = TrafficConfigProfileRefreshMode.Manual(unlock = true),
        )

        assertEquals(listOf(clicked.id, unrelated.id), updated.trafficConfigs.map(TrafficConfigState::id))
        assertEquals("Edited while fetching", updated.trafficConfigs.first().name)
        assertEquals(clicked.sourceUrl, updated.trafficConfigs.first().sourceUrl)
        assertEquals(987_000L, updated.trafficConfigs.first().lastUpdatedAtMillis)
        assertFalse(updated.trafficConfigs.first().updateLocked)
        assertFalse(updated.trafficConfigs.first().androidSettings.enableMux)
        assertEquals(unrelated, updated.trafficConfigs.last())
        assertEquals(state.activeTrafficConfigId, updated.activeTrafficConfigId)
        assertEquals(state.nextTrafficConfigId, updated.nextTrafficConfigId)
        assertSame(state.proxyServers, updated.proxyServers)
        assertEquals(state.nextProxyServerId, updated.nextProxyServerId)
        assertEquals(state.selectedProxyServerId, updated.selectedProxyServerId)
    }

    private fun profile(id: Int, name: String, url: String) = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = "[General]\nloglevel = warning\n",
        sourceUrl = url,
    ).withSkipiSettingsInRawConfig()

    private companion object {
        val fetchedDocument = TrafficConfigState(
            id = 99,
            name = "Remote name",
            rawConfig = """[General]
                loglevel = debug

                [Proxy Group]
                Stable = select, Node B, Node C, skipi-display=always
            """.trimIndent(),
            sourceUrl = "https://provider.example/original.conf",
            updateLocked = true,
        ).withSkipiSettingsInRawConfig().rawConfig
            .replace("profile-name = Remote name\n", "")
    }
}
