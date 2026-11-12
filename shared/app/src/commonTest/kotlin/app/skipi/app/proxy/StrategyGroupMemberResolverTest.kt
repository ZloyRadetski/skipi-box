// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class StrategyGroupMemberResolverTest {
    @Test
    fun explicitMemberOrderIsPreservedAndNestedCyclesAreIgnored() {
        val servers = listOf(
            record(1, HTTP(remarks = "First")),
            record(2, HTTP(remarks = "Second")),
            record(3, StrategyGroup(remarks = "Nested", proxyServerIds = listOf(2, 1, 3))),
            record(4, StrategyGroup(remarks = "Root", proxyServerIds = listOf(3, 1))),
        )

        val members = resolveStrategyGroupMembers(servers[3].server as StrategyGroup, servers)

        assertEquals(listOf(2, 1), members.map(ProxyServerRow::id))
    }

    @Test
    fun configOwnedGroupResolvesTheDeclaredPolicyMembers() {
        val servers = listOf(
            record(1, HTTP(remarks = "🇩🇪 Alpha")),
            record(2, HTTP(remarks = "Beta")),
            record(3, StrategyGroup(remarks = "From profile", sourceTrafficConfigId = 8, sourcePolicyGroupName = "Policy")),
        )
        val config = """[Proxy Group]
Policy = select, Alpha, Beta
"""

        val members = resolveStrategyGroupMembers(
            strategyGroup = servers[2].server as StrategyGroup,
            servers = servers,
            trafficConfigContentById = mapOf(8 to config),
            stripLeadingFlag = { value -> value.removePrefix("🇩🇪 ") },
        )

        assertEquals(listOf(1, 2), members.map(ProxyServerRow::id))
    }

    @Test
    fun invalidRegexFallbackRemainsCaseSensitive() {
        val servers = listOf(
            record(1, HTTP(remarks = "[A server")),
            record(2, HTTP(remarks = "[a server")),
        )

        val members = resolveStrategyGroupMembers(
            strategyGroup = StrategyGroup(filter = "[A"),
            servers = servers,
        )

        assertEquals(listOf(1), members.map(ProxyServerRow::id))
    }

    @Test
    fun aWholeSubscriptionCoversItsServersIncludingOnesAddedByALaterRefresh() {
        val group = StrategyGroup(wholeGroupIds = listOf(5))
        val before = listOf(record(1, HTTP(remarks = "A"), groupId = 5), record(2, HTTP(remarks = "B"), groupId = 6))
        val after = before + record(3, HTTP(remarks = "C"), groupId = 5)

        assertEquals(listOf(1), resolveStrategyGroupMembers(group, before).map(ProxyServerRow::id))
        assertEquals(listOf(1, 3), resolveStrategyGroupMembers(group, after).map(ProxyServerRow::id))
    }

    @Test
    fun theRegexOnlyLooksAtTheChosenGroupsAndPicksUpNewMatches() {
        val group = StrategyGroup(filter = "^DE", filterGroupIds = listOf(5))
        val servers = listOf(
            record(1, HTTP(remarks = "DE 1"), groupId = 5),
            record(2, HTTP(remarks = "DE 2"), groupId = 6),
            record(3, HTTP(remarks = "US 1"), groupId = 5),
            record(4, HTTP(remarks = "DE 3"), groupId = 5),
        )

        assertEquals(listOf(1, 4), resolveStrategyGroupMembers(group, servers).map(ProxyServerRow::id))
    }

    @Test
    fun aRegexWithoutChosenGroupsLooksAtEveryGroup() {
        val group = StrategyGroup(filter = "^DE")
        val servers = listOf(record(1, HTTP(remarks = "DE 1"), groupId = 5), record(2, HTTP(remarks = "DE 2"), groupId = 6))

        assertEquals(listOf(1, 2), resolveStrategyGroupMembers(group, servers).map(ProxyServerRow::id))
    }

    @Test
    fun pickedServersWholeSubscriptionsAndRegexMatchesAddUpWithoutRepeats() {
        val group = StrategyGroup(
            proxyServerIds = listOf(9, 1),
            wholeGroupIds = listOf(5),
            filter = "^DE",
            filterGroupIds = listOf(6),
        )
        val servers = listOf(
            record(1, HTTP(remarks = "A"), groupId = 5),
            record(2, HTTP(remarks = "DE 2"), groupId = 6),
            record(3, HTTP(remarks = "US 3"), groupId = 6),
            record(9, HTTP(remarks = "Picked"), groupId = 7),
        )

        assertEquals(listOf(9, 1, 2), resolveStrategyGroupMembers(group, servers).map(ProxyServerRow::id))
    }

    @Test
    fun theOldSingleSourceGroupStillMeansTheSameThing() {
        val servers = listOf(
            record(1, HTTP(remarks = "DE 1"), groupId = 5),
            record(2, HTTP(remarks = "US 2"), groupId = 5),
            record(3, HTTP(remarks = "DE 3"), groupId = 6),
        )

        assertEquals(listOf(1, 2), resolveStrategyGroupMembers(StrategyGroup(subscriptionGroupId = 5), servers).map(ProxyServerRow::id))
        assertEquals(listOf(1), resolveStrategyGroupMembers(StrategyGroup(subscriptionGroupId = 5, filter = "^DE"), servers).map(ProxyServerRow::id))
    }

    private fun record(id: Int, server: features.proxy.server.model.ProxyServer<*>, groupId: Int = 1) =
        ProxyServerRow(id = id, groupId = groupId, server = server)
}
