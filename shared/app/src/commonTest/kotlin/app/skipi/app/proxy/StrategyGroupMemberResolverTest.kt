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

        assertEquals(listOf(2, 1), members.map(ProxyServerRecord::id))
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

        assertEquals(listOf(1, 2), members.map(ProxyServerRecord::id))
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

        assertEquals(listOf(1), members.map(ProxyServerRecord::id))
    }

    private fun record(id: Int, server: features.proxy.server.model.ProxyServer<*>) =
        ProxyServerRecord(id = id, groupId = 1, server = server)
}
