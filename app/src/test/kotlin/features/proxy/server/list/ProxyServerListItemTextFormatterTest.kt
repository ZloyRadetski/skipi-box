// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.VLESS
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationLabels
import features.subscription.DefaultSubscriptionGroupId
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Home maps every visible server through this formatter. Looking at the whole list for each
 * plain server makes the projection quadratic, so only composite servers may read the list.
 */
class ProxyServerListItemTextFormatterTest {
    private val formatter = ProxyServerListItemTextFormatter(
        ProxyServerPresentationFormatter(
            groupNames = emptyMap(),
            labels = ProxyServerPresentationLabels(
                unknownGroupName = "Unknown",
                allGroupsName = "All",
                selectName = "Select",
                leastPingName = "Ping",
                leastLoadName = "Load",
                randomName = "Random",
                roundRobinName = "RR",
                strategyGroupSummaryTemplate = "{strategy} · {group}",
                strategyGroupSummaryWithFilterTemplate = "{strategy} · {group} · {filter}",
                chainProxySummaryTemplate = "{count} hops",
            ),
        ),
    )

    private fun vless(id: Int) = ProxyServerState(
        id = id,
        server = VLESS(remarks = "Node $id", id = "id-$id", server = "$id.example", port = "443"),
        groupId = DefaultSubscriptionGroupId,
    )

    /** A list that records how many elements are read. */
    private class CountingList<T>(private val items: List<T>) : AbstractList<T>() {
        var reads = 0
        override val size: Int get() = items.size
        override fun get(index: Int): T = items[index].also { reads++ }
    }

    @Test
    fun plainServerNeverReadsTheServerList() {
        val servers = CountingList((1..500).map(::vless))

        val text = formatter.displayOf(servers[0], servers)

        assertEquals("1.example:443", text.summary)
        assertEquals(1, servers.reads) // only the element the test itself fetched
    }

    @Test
    fun chainSummaryStillResolvesMemberNames() {
        val members = listOf(vless(1), vless(2))
        val chain = ProxyServerState(
            id = 9,
            server = ChainProxy(remarks = "Chain", proxyServerIds = listOf(1, 2)),
            groupId = DefaultSubscriptionGroupId,
        )

        assertEquals("Node 1 -> Node 2", formatter.displayOf(chain, members + chain).summary)
    }
}
