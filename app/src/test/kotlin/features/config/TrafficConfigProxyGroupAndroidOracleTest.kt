// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.AppState
import app.ProxyServerState
import app.skipi.app.server.ProxyServerEditResult
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * Source-derived oracle for Android's profile Proxy Groups page and editor-result adapter.
 * These cases exercise document/AppState behavior; they do not claim Compose navigation coverage.
 */
class TrafficConfigProxyGroupAndroidOracleTest {
    @Test
    fun deletingGroupUsesCapturedDocumentAndUpdatesLatestSameIdProfileEnvelope() {
        val opened = profile(
            id = 41,
            name = "Opened profile",
            rawConfig = """# opened document
[General]
loglevel = warning

[Proxy]
server = opened.example:443

[Proxy Group]
Keep = select, DIRECT, skipi-display=always
Remove = url-test, Node A, skipi-display=never

[Vendor]
vendor-option = opened
""",
        )
        val removedGroup = opened.rawConfig.analyzeShadowrocketConfig().proxyGroups.single { it.name == "Remove" }
        val latest = opened.copy(
            name = "Latest profile metadata",
            sourceUrl = "https://latest.example/profile.conf",
            updateLocked = true,
            lastUpdatedAtMillis = 567L,
            androidSettings = opened.androidSettings.copy(enableFakeDns = true),
            rawConfig = opened.rawConfig
                .replace("server = opened.example:443", "server = latest.example:8443")
                .replace("vendor-option = opened", "vendor-option = latest"),
        )
        val sibling = profile(42, "Sibling profile", "[General]\nloglevel = info\n")
        val globalGroup = ProxyServerState(
            id = 70,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(remarks = "Global group", sourceTrafficConfigId = null),
        )
        val before = AppState(
            trafficConfigs = listOf(latest, sibling),
            activeTrafficConfigId = sibling.id,
            nextTrafficConfigId = 99,
            proxyServers = listOf(globalGroup),
            nextProxyServerId = 80,
            selectedProxyServerId = globalGroup.id,
        )

        // TrafficConfigProxyGroupsPage.onDeleteGroup closes over config.rawConfig and updates by ID.
        val capturedDocument = opened.rawConfig.withoutShadowrocketProxyGroupLine(removedGroup.lineNumber)
        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = capturedDocument)
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        assertEquals("Latest profile metadata", saved.name)
        assertEquals("https://latest.example/profile.conf", saved.sourceUrl)
        assertTrue(saved.updateLocked)
        assertEquals(567L, saved.lastUpdatedAtMillis)
        assertTrue(saved.androidSettings.enableFakeDns)
        assertEquals(listOf("Keep"), saved.rawConfig.analyzeShadowrocketConfig().proxyGroups.map { it.name })
        assertTrue(saved.rawConfig.contains("server = opened.example:443"))
        assertFalse(saved.rawConfig.contains("server = latest.example:8443"))
        assertTrue(saved.rawConfig.contains("vendor-option = opened"))
        assertFalse(saved.rawConfig.contains("vendor-option = latest"))
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
        assertEquals(before.activeTrafficConfigId, after.activeTrafficConfigId)
        assertEquals(before.nextTrafficConfigId, after.nextTrafficConfigId)
        assertEquals(before.proxyServers, after.proxyServers)
        assertEquals(before.nextProxyServerId, after.nextProxyServerId)
        assertEquals(before.selectedProxyServerId, after.selectedProxyServerId)
    }

    @Test
    fun editingGroupUsesItsProfileLineNumberEvenWhenItMatchesAGlobalServerId() {
        val opened = profile(
            id = 51,
            name = "Group profile",
            rawConfig = """[General]
loglevel = warning

[Proxy Group]
Original = select, DIRECT, skipi-display=always

[Vendor]
vendor-option = keep
""",
        )
        val group = opened.rawConfig.analyzeShadowrocketConfig().proxyGroups.single()
        val collidingGlobalServer = ProxyServerState(
            id = group.lineNumber,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "Catalog server", id = "catalog-user", server = "catalog.example", port = "443"),
        )
        val sibling = profile(52, "Sibling", "[General]\nloglevel = debug\n")
        val before = AppState(
            trafficConfigs = listOf(opened, sibling),
            activeTrafficConfigId = sibling.id,
            nextTrafficConfigId = 90,
            proxyServers = listOf(collidingGlobalServer),
            nextProxyServerId = 30,
            selectedProxyServerId = collidingGlobalServer.id,
        )
        val serverChoices = trafficConfigProxyGroupServerChoices(
            listOf(TrafficConfigProxyGroupServerInput(collidingGlobalServer.id, collidingGlobalServer.server)),
        )
        val editorGroup = group.toEditableStrategyGroup(
            trafficConfigId = opened.id,
            serverChoices = serverChoices,
        ).copy(remarks = "Edited profile group")
        val result = ProxyServerEditResult(
            serverId = group.lineNumber,
            server = editorGroup,
        )
        val replacement = (result.server as StrategyGroup).toShadowrocketLine(serverChoices)

        // openEditGroup passes lineNumber as the result ID; this page interprets that value as a
        // raw-document line and updates the profile, even if the integer also names a catalog row.
        val editedDocument = opened.rawConfig.withShadowrocketProxyGroupLine(
            result.serverId!!,
            replacement,
        )
        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = editedDocument)
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        val savedGroup = saved.rawConfig.analyzeShadowrocketConfig().proxyGroups.single()
        assertEquals("Edited profile group", savedGroup.name)
        assertEquals(group.lineNumber, savedGroup.lineNumber)
        assertEquals("[General]\nloglevel = warning\n\n[Proxy Group]\n", saved.rawConfig.substringBefore("Edited profile group"))
        assertTrue(saved.rawConfig.contains("[Vendor]\nvendor-option = keep"))
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
        assertEquals(listOf(collidingGlobalServer), after.proxyServers)
        assertEquals(before.activeTrafficConfigId, after.activeTrafficConfigId)
        assertEquals(before.nextTrafficConfigId, after.nextTrafficConfigId)
        assertEquals(before.nextProxyServerId, after.nextProxyServerId)
        assertEquals(before.selectedProxyServerId, after.selectedProxyServerId)
    }

    @Test
    fun newProfileGroupIsActiveConfigOwnedAndIsSavedOnlyToTheProfileDocument() {
        val opened = profile(
            id = 61,
            name = "New group profile",
            rawConfig = """[General]
loglevel = warning

[Vendor]
vendor-option = preserve
""",
        )
        val sibling = profile(62, "Sibling", "[General]\nloglevel = info\n")
        val globalServer = ProxyServerState(
            id = 18,
            groupId = DefaultSubscriptionGroupId,
            server = VLESS(remarks = "Node A", id = "node-a", server = "node-a.example", port = "443"),
        )
        val globalGroup = ProxyServerState(
            id = 19,
            groupId = AutoBalancerGroupId,
            server = StrategyGroup(remarks = "Manual group", sourceTrafficConfigId = null),
        )
        val before = AppState(
            trafficConfigs = listOf(opened, sibling),
            activeTrafficConfigId = sibling.id,
            nextTrafficConfigId = 90,
            proxyServers = listOf(globalServer, globalGroup),
            nextProxyServerId = 25,
            selectedProxyServerId = globalServer.id,
        )
        val serverChoices = trafficConfigProxyGroupServerChoices(
            before.proxyServers.map { server -> TrafficConfigProxyGroupServerInput(server.id, server.server) },
        )
        val newStrategy = newTrafficConfigProxyGroupStrategy(opened.id).copy(
            remarks = "Profile-only group",
            proxyServerIds = listOf(globalServer.id),
        )
        assertEquals(opened.id, newStrategy.sourceTrafficConfigId)
        assertEquals(StrategyGroupDisplayMode.ACTIVE_CONFIG, newStrategy.displayMode)

        val result = ProxyServerEditResult(serverId = -1, server = newStrategy)
        val newLine = (result.server as StrategyGroup).toShadowrocketLine(serverChoices)
        val updatedDocument = opened.rawConfig.withShadowrocketProxyGroupAdded(newLine)
        val after = before.withUpdatedTrafficConfigProfile(opened.id) { current ->
            current.copy(rawConfig = updatedDocument)
        }

        val saved = after.trafficConfigs.single { it.id == opened.id }
        val savedGroup = saved.rawConfig.analyzeShadowrocketConfig().proxyGroups.single()
        assertEquals("Profile-only group", savedGroup.name)
        assertEquals(StrategyGroupDisplayMode.ACTIVE_CONFIG, savedGroup.displayMode)
        assertTrue(saved.rawConfig.contains("skipi-display=active_config"))
        assertTrue(saved.rawConfig.contains("[Vendor]\nvendor-option = preserve"))
        assertEquals(sibling, after.trafficConfigs.single { it.id == sibling.id })
        assertEquals(before.activeTrafficConfigId, after.activeTrafficConfigId)
        assertEquals(before.nextTrafficConfigId, after.nextTrafficConfigId)
        assertEquals(before.proxyServers, after.proxyServers)
        assertEquals(before.nextProxyServerId, after.nextProxyServerId)
        assertEquals(before.selectedProxyServerId, after.selectedProxyServerId)
    }

    private fun profile(id: Int, name: String, rawConfig: String): TrafficConfigState = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = rawConfig,
        sourceUrl = "https://opened.example/$id.conf",
        updateLocked = false,
        lastUpdatedAtMillis = 123L,
    )
}
