// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.toProxyServerListState
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import platform.TunnelPhase
import platform.TunnelSnapshot

/**
 * Starting the VPN flips runtime fields of AppState several times. Home must not regroup and
 * re-summarise every server of every subscription for each of those emissions.
 */
class ProxyHomeCatalogCacheTest {
    private val groups = (1..20).map { index ->
        SubscriptionGroupState(
            id = index + 1,
            name = "Group $index",
            url = "https://example.test/$index",
            userAgent = "",
            updateInterval = "",
            enabled = true,
        )
    }
    private val servers = groups.flatMap { group ->
        (1..30).map { index ->
            ProxyServerState(
                id = group.id * 1000 + index,
                server = VLESS(remarks = "Node ${group.id}/$index", id = "u", server = "n$index.example", port = "443"),
                groupId = group.id,
            )
        }
    }
    private val state = AppState(
        subscriptionGroups = groups,
        proxyServers = servers,
        selectedProxyServerId = 2001,
        enableAllProxyGroup = true,
    )

    private fun cache() = ProxyHomeCatalogCache()

    private fun ProxyHomeCatalogCache.homeGroups(of: AppState) =
        homeGroups(of.toProxyServerListState(), allGroupName = "All", defaultGroupName = "Manual", autoBalancerGroupName = "Auto")

    private fun ProxyHomeCatalogCache.catalogOf(of: AppState, groupState: ProxyServerListGroups, refreshing: Set<Int> = emptySet()) =
        catalog(of, groupState, refreshing, pinging = emptySet(), formatter = null)

    @Test
    fun runtimeOnlyChangesKeepTheGroupingAndTheCatalog() {
        val cache = cache()
        val before = cache.homeGroups(state)
        val catalogBefore = cache.catalogOf(state, before)

        val running = state.copy(proxyRunning = true, localProxyPort = "21000", unappliedRoutingRules = listOf("rule"))
        val after = cache.homeGroups(running)

        assertSame(before, after)
        assertSame(catalogBefore, cache.catalogOf(running, after))
    }

    @Test
    fun changingServersSelectionOrRefreshStateRebuilds() {
        val cache = cache()
        val before = cache.homeGroups(state)
        val catalogBefore = cache.catalogOf(state, before)

        val withoutOne = state.copy(proxyServers = servers.drop(1))
        val regrouped = cache.homeGroups(withoutOne)
        assertNotSame(before, regrouped)
        assertNotSame(catalogBefore, cache.catalogOf(withoutOne, regrouped))

        val reselected = state.copy(selectedProxyServerId = 3001)
        assertNotSame(catalogBefore, cache.catalogOf(reselected, cache.homeGroups(reselected)))

        assertNotSame(catalogBefore, cache.catalogOf(state, before, refreshing = setOf(groups[0].id)))
        assertNotSame(before, cache.homeGroups(state.copy(enableAllProxyGroup = false)))
    }

    @Test
    fun theCachedCatalogProducesTheSameHomeInputAsAFreshBuild() {
        val cache = cache()
        val groupState = cache.homeGroups(state)
        val catalog = cache.catalogOf(state, groupState)
        val running = TunnelSnapshot(phase = TunnelPhase.Connected)

        val viaCache = state.toProxyHomeInput(
            groupState = groupState,
            tunnelSnapshot = running,
            tunnelBusy = false,
            isTestingLatency = false,
            catalog = catalog,
        )
        val fresh = state.toProxyHomeInput(
            groupState = groupState,
            tunnelSnapshot = running,
            tunnelBusy = false,
            isTestingLatency = false,
        )

        assertEquals(fresh, viaCache)
        assertSame(catalog.servers, viaCache.servers)
    }
}
