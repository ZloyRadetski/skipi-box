// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.presentation

import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyServerPresentationTest {
    private val labels = ProxyServerPresentationLabels(
        unknownGroupName = "Unknown",
        allGroupsName = "All groups",
        selectName = "Select",
        leastPingName = "Least ping",
        leastLoadName = "Least load",
        randomName = "Random",
        roundRobinName = "Round robin",
        strategyGroupSummaryTemplate = "{strategy} · {group}",
        strategyGroupSummaryWithFilterTemplate = "{strategy} · {group} · Filter: {filter}",
        chainProxySummaryTemplate = "{count} hops",
    )

    @Test
    fun selectStrategyUsesTheActiveMemberAndExplicitMemberCount() {
        val member = ProxyServerPresentationNode(
            id = 7,
            groupId = 5,
            server = Custom(remarks = "🇫🇮 Helsinki"),
        )
        val strategy = ProxyServerPresentationNode(
            id = 8,
            groupId = 5,
            server = StrategyGroup(
                remarks = "Fast route",
                strategy = StrategyGroupConstants.TYPE_SELECT,
                proxyServerIds = listOf(7),
                selectedMemberId = 7,
            ),
        )

        val text = ProxyServerPresentationFormatter(emptyMap(), labels)
            .displayOf(strategy, listOf(member, strategy))

        assertEquals("Fast route", text.title)
        assertEquals("Select: 🇫🇮 Helsinki (1)", text.summary)
        assertEquals("Strategy", text.protocol)
    }

    @Test
    fun chainFallsBackToTheLocalizedCountWhenMembersAreUnavailable() {
        val chain = ProxyServerPresentationNode(
            id = 1,
            groupId = 1,
            server = ChainProxy(remarks = "Fallback chain", proxyServerIds = listOf(90, 91)),
        )

        val text = ProxyServerPresentationFormatter(emptyMap(), labels).displayOf(chain, listOf(chain))

        assertEquals("2 hops", text.summary)
    }

    @Test
    fun presentationTitleSeparatesFlagsWithoutDestroyingFlagOnlyNames() {
        assertEquals(
            ProxyServerPresentationTitle(flag = "🇳🇱", title = "Amsterdam"),
            "  🇳🇱 Amsterdam".toProxyServerPresentationTitle(),
        )
        assertEquals(
            ProxyServerPresentationTitle(flag = "🇫🇮", title = "🇫🇮"),
            "🇫🇮".toProxyServerPresentationTitle(),
        )
        assertEquals(
            ProxyServerPresentationTitle(flag = "⚡", title = "Fast route"),
            "⚡ Fast route".toProxyServerPresentationTitle(),
        )
        assertEquals(
            ProxyServerPresentationTitle(flag = null, title = "Untitled"),
            "  ".toProxyServerPresentationTitle(emptyTitle = "Untitled"),
        )
    }
}
