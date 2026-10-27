// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeCopyFormat
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationLabels
import features.proxy.server.presentation.ProxyServerPresentationNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProxyHomeProjectionTest {
    @Test
    fun projectionAdvertisesQrOnlyForUrlProxyServers() {
        val serverLibrary = DesktopServerLibrary(
            servers = listOf(
                storedServer(30, "QR node"),
                storedStrategy(31, StrategyGroup(remarks = "Strategy")),
            ),
        )
        val subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = emptyList())
        val groupCatalog = DesktopProxyGroups.create(
            serverLibrary = serverLibrary,
            subscriptionLibrary = subscriptionLibrary,
            options = DesktopProxyGroupOptions(enableAllProxyGroup = true),
        )
        val decodedServers = serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
        val presentationNodes = decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                ProxyServerPresentationNode(
                    id = stored.id,
                    groupId = stored.subscriptionId,
                    server = current,
                )
            }
        }
        val projection = projectDesktopProxyHomeProjection(
            groupCatalog = groupCatalog,
            decodedServers = decodedServers,
            subscriptionLibrary = subscriptionLibrary,
            selectedServerId = 30,
            latencyByServerId = emptyMap(),
            testingServerIds = emptySet(),
            presentationFormatter = ProxyServerPresentationFormatter(
                groupNames = emptyMap(),
                labels = projectionTestPresentationLabels(),
            ),
            presentationNodes = presentationNodes,
            refreshingSubscriptionIds = emptySet(),
            pingingSubscriptionIds = emptySet(),
        )
        val formatsById = projection.servers.associate { it.id to it.availableCopyFormats }

        assertTrue(ProxyHomeCopyFormat.QrCode in formatsById.getValue("30"))
        assertTrue(ProxyHomeCopyFormat.QrCode !in formatsById.getValue("31"))
    }

    @Test
    fun projectionMatchesAndroidAllMembershipAndServerSearchAndSortFields() {
        val workServer = storedServer(
            id = 11,
            remarks = "🇺🇸 Amsterdam",
            subscriptionId = 2,
        )
        val userBalancer = storedStrategy(20, StrategyGroup(remarks = "User strategy"), subscriptionId = -2)
        val configBalancer = storedStrategy(
            21,
            StrategyGroup(
                remarks = "Config strategy",
                sourceTrafficConfigId = 7,
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            ),
            subscriptionId = -2,
        )
        val manualServer = storedServer(10, "Manual node")
        val serverLibrary = DesktopServerLibrary(
            servers = listOf(manualServer, workServer, userBalancer, configBalancer),
        )
        val subscriptionLibrary = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                DesktopStoredSubscription(id = 2, url = "https://work.example/sub", name = "Work"),
            ),
        )
        val groupCatalog = DesktopProxyGroups.create(
            serverLibrary = serverLibrary,
            subscriptionLibrary = subscriptionLibrary,
            options = DesktopProxyGroupOptions(activeTrafficConfigId = 7, enableAllProxyGroup = true),
        )
        val decodedServers = serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
        val presentationNodes = decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                ProxyServerPresentationNode(
                    id = stored.id,
                    groupId = stored.subscriptionId,
                    server = current,
                )
            }
        }
        val formatter = ProxyServerPresentationFormatter(
            groupNames = mapOf(2 to "Work"),
            labels = ProxyServerPresentationLabels(
                unknownGroupName = "Unknown",
                allGroupsName = "All groups",
                selectName = "Select",
                leastPingName = "Least ping",
                leastLoadName = "Least load",
                randomName = "Random",
                roundRobinName = "Round robin",
                strategyGroupSummaryTemplate = "{count}",
                strategyGroupSummaryWithFilterTemplate = "{filter} {count}",
                chainProxySummaryTemplate = "{count}",
            ),
        )

        val projection = projectDesktopProxyHomeProjection(
            groupCatalog = groupCatalog,
            decodedServers = decodedServers,
            subscriptionLibrary = subscriptionLibrary,
            selectedServerId = 11,
            latencyByServerId = emptyMap(),
            testingServerIds = emptySet(),
            presentationFormatter = formatter,
            presentationNodes = presentationNodes,
            refreshingSubscriptionIds = emptySet(),
            pingingSubscriptionIds = emptySet(),
        )

        val all = projection.groups.first { it.kind == ProxyHomeGroupKind.All }
        assertEquals(3, all.serverCount)
        assertEquals(setOf("10", "11", "20"), all.serverIds)
        assertEquals(listOf("10", "11", "20", "21"), projection.servers.map { it.id })
        assertTrue("21" !in all.serverIds.orEmpty())

        val projectedWorkServer = projection.servers.first { it.id == "11" }
        assertEquals("subscription:2", projectedWorkServer.groupId)
        assertEquals("🇺🇸 Amsterdam", projectedWorkServer.searchText)
        assertEquals("🇺🇸 Amsterdam", projectedWorkServer.sortKey)
        assertEquals("Amsterdam", projectedWorkServer.title)
        assertTrue(projectedWorkServer.selected)
    }

    @Test
    fun projectionMatchesAndroidHeadlineFallbackAndCountryFlagRules() {
        val englandFlag = "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
        val serverLibrary = DesktopServerLibrary(
            servers = listOf(
                storedServer(30, ""),
                storedStrategy(31, StrategyGroup(remarks = ""), subscriptionId = -2),
                storedServer(32, "  😀 Plain label  "),
                storedServer(33, "🇫🇮"),
                storedServer(34, "  ${englandFlag} England"),
            ),
        )
        val subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = emptyList())
        val groupCatalog = DesktopProxyGroups.create(
            serverLibrary = serverLibrary,
            subscriptionLibrary = subscriptionLibrary,
            options = DesktopProxyGroupOptions(enableAllProxyGroup = true),
        )
        val decodedServers = serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
        val presentationNodes = decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                ProxyServerPresentationNode(
                    id = stored.id,
                    groupId = stored.subscriptionId,
                    server = current,
                )
            }
        }
        val formatter = ProxyServerPresentationFormatter(
            groupNames = emptyMap(),
            labels = ProxyServerPresentationLabels(
                unknownGroupName = "Unknown",
                allGroupsName = "All groups",
                selectName = "Select",
                leastPingName = "Least ping",
                leastLoadName = "Least load",
                randomName = "Random",
                roundRobinName = "Round robin",
                strategyGroupSummaryTemplate = "{count}",
                strategyGroupSummaryWithFilterTemplate = "{filter} {count}",
                chainProxySummaryTemplate = "{count}",
            ),
        )

        val projection = projectDesktopProxyHomeProjection(
            groupCatalog = groupCatalog,
            decodedServers = decodedServers,
            subscriptionLibrary = subscriptionLibrary,
            selectedServerId = null,
            latencyByServerId = emptyMap(),
            testingServerIds = emptySet(),
            presentationFormatter = formatter,
            presentationNodes = presentationNodes,
            refreshingSubscriptionIds = emptySet(),
            pingingSubscriptionIds = emptySet(),
        )
        val summaries = projection.servers.associateBy { it.id }

        assertEquals("VLESS", summaries.getValue("30").title)
        assertEquals(null, summaries.getValue("30").flag)
        assertEquals("Strategy", summaries.getValue("31").title)
        assertEquals("⚡", summaries.getValue("31").flag)
        assertEquals("  😀 Plain label  ", summaries.getValue("32").title)
        assertEquals(null, summaries.getValue("32").flag)
        assertEquals("🇫🇮", summaries.getValue("33").title)
        assertEquals("🇫🇮", summaries.getValue("33").flag)
        assertEquals("England", summaries.getValue("34").title)
        assertEquals(englandFlag, summaries.getValue("34").flag)
    }

    @Test
    fun retainedBatchProgressMarksEverySubscriptionRowById() {
        val serverLibrary = DesktopServerLibrary(
            servers = listOf(
                storedServer(11, "First", subscriptionId = 2),
                storedServer(12, "Second", subscriptionId = 3),
            ),
        )
        val subscriptionLibrary = DesktopSubscriptionLibrary(
            subscriptions = listOf(
                DesktopStoredSubscription(id = 2, url = "https://first.example/sub", name = "First"),
                DesktopStoredSubscription(id = 3, url = "https://second.example/sub", name = "Second"),
                DesktopStoredSubscription(id = 4, url = "https://third.example/sub", name = "Third"),
            ),
        )
        val groupCatalog = DesktopProxyGroups.create(serverLibrary, subscriptionLibrary)
        val decodedServers = serverLibrary.servers.map { stored -> stored to stored.decode().getOrNull() }
        val nodes = decodedServers.mapNotNull { (stored, server) ->
            server?.let { current ->
                ProxyServerPresentationNode(id = stored.id, groupId = stored.subscriptionId, server = current)
            }
        }
        val formatter = ProxyServerPresentationFormatter(
            groupNames = mapOf(2 to "First", 3 to "Second", 4 to "Third"),
            labels = projectionTestPresentationLabels(),
        )

        val projection = projectDesktopProxyHomeProjection(
            groupCatalog = groupCatalog,
            decodedServers = decodedServers,
            subscriptionLibrary = subscriptionLibrary,
            selectedServerId = 11,
            latencyByServerId = emptyMap(),
            testingServerIds = emptySet(),
            presentationFormatter = formatter,
            presentationNodes = nodes,
            refreshingSubscriptionIds = setOf(2, 3),
            pingingSubscriptionIds = emptySet(),
        )

        assertEquals(
            mapOf("2" to true, "3" to true, "4" to false),
            projection.groups
                .mapNotNull { group -> group.subscription?.let { it.id to it.refreshing } }
                .toMap(),
        )
    }

    private fun storedServer(
        id: Int,
        remarks: String,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        subscriptionId = subscriptionId,
        serverJson = VLESS(
            remarks = remarks,
            id = "uuid-$id",
            server = "node-$id.example",
            port = "443",
        ).encodePersistedProxyServer(),
    )

    private fun storedStrategy(
        id: Int,
        strategy: StrategyGroup,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        subscriptionId = subscriptionId,
        serverJson = strategy.encodePersistedProxyServer(),
    )

    private fun projectionTestPresentationLabels() = ProxyServerPresentationLabels(
        unknownGroupName = "Unknown",
        allGroupsName = "All groups",
        selectName = "Select",
        leastPingName = "Least ping",
        leastLoadName = "Least load",
        randomName = "Random",
        roundRobinName = "Round robin",
        strategyGroupSummaryTemplate = "{count}",
        strategyGroupSummaryWithFilterTemplate = "{filter} {count}",
        chainProxySummaryTemplate = "{count}",
    )
}
