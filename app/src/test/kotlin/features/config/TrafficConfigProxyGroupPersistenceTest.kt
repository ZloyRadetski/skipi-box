// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.ProxyServerState
import app.skipi.app.model.ProxyServerCatalog
import app.skipi.app.model.ProxyServerRecord
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrafficConfigProxyGroupPersistenceTest {
    @Test
    fun editorReconciliationUsesSharedCatalogAndPreservesIdsOrderAndSelection() {
        val profile = TrafficConfigState(
            id = 4,
            name = "Shared groups",
            rawConfig = """
                [Proxy Group]
                Stable = select, Node A, skipi-display=always
            """.trimIndent(),
        )
        val manual = ProxyServerRecord(id = 8, server = VLESS(remarks = "Manual", id = "m", server = "m.example", port = "443"))
        val node = ProxyServerRecord(id = 10, server = VLESS(remarks = "Node A", id = "a", server = "a.example", port = "443"))
        val state = AppState(
            activeTrafficConfigId = 4,
            nextProxyServerId = 50,
            selectedProxyServerId = 8,
            proxyServers = listOf(
                ProxyServerState(8, manual.server, groupId = 0),
                ProxyServerState(10, node.server, groupId = 0),
            ),
            trafficConfigs = listOf(profile),
        )
        val catalog = ProxyServerCatalog(
            servers = listOf(manual, node),
            nextServerId = 50,
            selectedServerId = 8,
        )

        val reconciled = state.withConfigProxyGroupsReflected(catalog)

        assertEquals(listOf(8, 10, 50), reconciled.servers.map(ProxyServerRecord::id))
        assertEquals(51, reconciled.nextServerId)
        assertEquals(8, reconciled.selectedServerId)
        assertEquals(listOf(8, 10), catalog.servers.map(ProxyServerRecord::id))
    }

    @Test
    fun profileOnlyEditorOperationsDoNotChangeProxyCatalog() {
        val proxy = ProxyServerState(3, VLESS(remarks = "Keep", id = "keep", server = "keep.example", port = "443"), groupId = 0)
        val state = AppState(
            activeTrafficConfigId = 1,
            nextProxyServerId = 20,
            selectedProxyServerId = 3,
            proxyServers = listOf(proxy),
            trafficConfigs = listOf(
                TrafficConfigState(id = 1, name = "One", rawConfig = ""),
                TrafficConfigState(id = 2, name = "Two", rawConfig = ""),
            ),
        )

        val duplicated = state.withDuplicatedTrafficConfig(state.trafficConfigs.first(), "Copy").state
        val selected = state.withActiveTrafficConfig(999)
        val deleted = state.withDeletedTrafficConfig(1)
        val updated = state.withUpdatedTrafficConfig(1) { config ->
            config.copy(rawConfig = "[Proxy Group]\nFast = select, A, skipi-display=always")
        }

        listOf(duplicated, selected, deleted, updated).forEach { result ->
            assertEquals(state.proxyServers, result.proxyServers)
            assertEquals(state.nextProxyServerId, result.nextProxyServerId)
            assertEquals(state.selectedProxyServerId, result.selectedProxyServerId)
        }
        assertEquals(3, duplicated.trafficConfigs.size)
        assertEquals(1, selected.activeTrafficConfigId)
        assertEquals(listOf(2), deleted.trafficConfigs.map { it.id })
        assertTrue(updated.trafficConfigs.first().rawConfig.contains("Fast = select"))
    }

    @Test
    fun configProxyGroup_roundTripsBalancerHealthSettings() {
        val edited = StrategyGroup(
            remarks = "Fastest",
            strategy = StrategyGroupConstants.TYPE_FALLBACK,
            proxyServerIds = listOf(10, 20),
            probeUrl = "https://probe.example/204",
            probeInterval = "30s",
            enableBurstProbe = true,
            tolerance = "150ms",
            displayMode = StrategyGroupDisplayMode.ALWAYS,
        )
        val rawConfig = """
            [Proxy Group]
            ${edited.toShadowrocketLine(
                listOf(
                    ProxyGroupServerChoice(10, "Node A"),
                    ProxyGroupServerChoice(20, "Node B"),
                ),
            )}
        """.trimIndent()

        val group = rawConfig.analyzeShadowrocketConfig().proxyGroups.single()
        assertTrue(group.enableBurstProbe)
        assertEquals("150ms", group.tolerance)

        val state = AppState(
            activeTrafficConfigId = 1,
            nextProxyServerId = 30,
            proxyServers = listOf(
                ProxyServerState(10, VLESS(remarks = "Node A", id = "a", server = "a.example", port = "443"), groupId = 0),
                ProxyServerState(20, VLESS(remarks = "Node B", id = "b", server = "b.example", port = "443"), groupId = 0),
            ),
            trafficConfigs = listOf(TrafficConfigState(id = 1, name = "Test", rawConfig = rawConfig)),
        ).withConfigProxyGroupsReflected()

        val reflected = state.proxyServers.single { server -> server.groupId == AutoBalancerGroupId }.server as StrategyGroup
        assertTrue(reflected.enableBurstProbe)
        assertEquals("150ms", reflected.tolerance)
    }

    @Test
    fun configProxyGroup_preservesDisabledBurstProbe() {
        val group = StrategyGroup(
            remarks = "Battery saver",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            enableBurstProbe = false,
        )
        val raw = "[Proxy Group]\n${group.toShadowrocketLine(emptyList())}"

        val parsed = raw.analyzeShadowrocketConfig().proxyGroups.single()

        assertTrue(raw.contains("skipi-burst-probe=false"))
        assertEquals(false, parsed.enableBurstProbe)
    }

    @Test
    fun editingExistingConfigGroup_keepsAllBalancerSettings() {
        val raw = """
            [Proxy Group]
            Reliable = fallback, Node A, Node B, url=https://probe.example/204, interval=30, skipi-display=always, skipi-burst-probe=false, skipi-tolerance=150ms
        """.trimIndent()
        val parsed = raw.analyzeShadowrocketConfig().proxyGroups.single()

        val editorModel = parsed.toEditableStrategyGroup(
            trafficConfigId = 7,
            serverChoices = listOf(
                ProxyGroupServerChoice(10, "Node A"),
                ProxyGroupServerChoice(20, "Node B"),
            ),
        )

        assertEquals(StrategyGroupConstants.TYPE_FALLBACK, editorModel.strategy)
        assertEquals(listOf(10, 20), editorModel.proxyServerIds)
        assertEquals("https://probe.example/204", editorModel.probeUrl)
        assertEquals("30s", editorModel.probeInterval)
        assertEquals(false, editorModel.enableBurstProbe)
        assertEquals("150ms", editorModel.tolerance)
        assertEquals(StrategyGroupDisplayMode.ALWAYS, editorModel.displayMode)
    }
}
