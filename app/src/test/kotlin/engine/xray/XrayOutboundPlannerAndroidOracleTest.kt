// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import app.ProxyServerState
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonArray

/** Literal Android behavior fixtures that the common planner must preserve. */
class XrayOutboundPlannerAndroidOracleTest {

    @Test
    fun selectedAndReferencedOutboundsKeepAndroidOrderAndFixedTargets() {
        val selected = node(10, "Selected", "selected.example")
        val defaultTarget = node(30, "Default", "default.example")
        val enabledTarget = node(20, "Enabled", "enabled.example")
        val disabledTarget = node(40, "Disabled", "disabled.example")

        val plan = appState(
            servers = listOf(selected, defaultTarget, enabledTarget, disabledTarget),
            routeRules = listOf(
                RouteRule(outboundTag = "20"),
                RouteRule(outboundTag = "40", enabled = false),
            ),
            defaultRouteOutboundTag = "30",
            enableLocalDns = true,
            enableFragment = true,
        ).buildXrayOutboundPlan(selected)

        assertEquals(listOf("proxy", "30", "20"), plan.proxyOutbounds.map { it.tag })
        assertEquals(
            listOf("selected.example", "default.example", "enabled.example"),
            plan.dnsHostServers,
        )
        assertEquals(
            listOf("proxy", "30", "20", "direct", "block", "dns-out", "fragment"),
            plan.routeTargets.keys.toList(),
        )
        assertFalse(plan.proxyOutbounds.any { it.tag == "40" })

        val withoutOptionalTargets = appState(
            servers = listOf(selected),
            defaultRouteOutboundTag = "proxy",
            enableLocalDns = false,
            enableFragment = false,
        ).buildXrayOutboundPlan(selected)
        assertEquals(listOf("proxy", "direct", "block"), withoutOptionalTargets.routeTargets.keys.toList())
    }

    @Test
    fun chainReversesUsableMembersAndAssignsDialerTagsWithoutFragment() {
        val hop1 = node(1, "Hop 1", "hop1.example")
        val nestedChain = row(2, ChainProxy(remarks = "Nested", proxyServerIds = listOf(1, 5)))
        val custom = row(3, Custom(remarks = "Custom", configJson = "{\"outbounds\":[]}"))
        val hop4 = node(4, "Hop 4", "hop4.example")
        val hop5 = node(5, "Hop 5", "hop5.example")
        val selected = row(99, ChainProxy(remarks = "Three hop", proxyServerIds = listOf(1, 2, 3, 4, 5)))

        val plan = appState(
            servers = listOf(selected, hop1, nestedChain, custom, hop4, hop5),
            enableFragment = true,
        ).buildXrayOutboundPlan(selected)

        assertEquals(listOf("proxy", "proxy-chain-1", "proxy-chain-2"), plan.proxyOutbounds.map { it.tag })
        assertEquals(listOf("Hop 5", "Hop 4", "Hop 1"), plan.proxyOutbounds.map { it.server?.getInfo()?.remarks })
        assertEquals(
            listOf("proxy-chain-1", "proxy-chain-2", null),
            plan.proxyOutbounds.map { it.dialerProxyTag },
        )
        assertTrue(plan.proxyOutbounds.all { !it.allowFragment })
        assertEquals(listOf("hop5.example", "hop4.example", "hop1.example"), plan.dnsHostServers)
        assertEquals(XrayRouteTargetKind.Outbound, plan.routeTargets.getValue("proxy").kind)
    }

    @Test
    fun chainRejectsFewerThanTwoUsableMembers() {
        val onlyUsable = node(1, "Only usable", "only.example")
        val selected = row(99, ChainProxy(remarks = "Incomplete", proxyServerIds = listOf(1, 3, 404)))
        val custom = row(3, Custom(remarks = "Filtered custom", configJson = "{\"outbounds\":[]}"))

        val error = assertFailsWith<IllegalStateException> {
            appState(servers = listOf(selected, onlyUsable, custom)).buildXrayOutboundPlan(selected)
        }

        assertTrue(error.message.orEmpty().contains("at least two available proxy servers"))
    }

    @Test
    fun strategyGroupWithoutAvailableMembersFailsClearly() {
        val selected = row(
            100,
            StrategyGroup(
                remarks = "Empty",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = emptyList(),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            appState(servers = listOf(selected)).buildXrayOutboundPlan(selected)
        }

        assertTrue(error.message.orEmpty().contains("has no available proxy servers"))
    }

    @Test
    fun shadowrocketAliasWithoutAvailableMembersIsSkipped() {
        val selected = node(1, "Selected", "selected.example")
        val group = ShadowrocketPolicyGroup(
            lineNumber = 1,
            name = "Missing",
            type = "url-test",
            members = listOf("No matching server"),
        )

        val plan = appState(
            servers = listOf(selected),
            routeRules = listOf(RouteRule(outboundTag = group.outboundTag, enabled = false)),
            groups = listOf(group),
        ).buildXrayOutboundPlan(selected)

        assertEquals(listOf("proxy"), plan.proxyOutbounds.map { it.tag })
        assertFalse(plan.routeTargets.containsKey(group.outboundTag))
        assertFalse(plan.balancers.any { it.tag == group.outboundTag })
    }

    @Test
    fun customOutboundSelectsProxyAndContributesHostsOnlyWhenOverrideIsEnabled() {
        val custom = row(
            70,
            Custom(
                remarks = "Raw",
                overrideInboundAndDns = true,
                configJson = """
                    {"outbounds":[
                      {"tag":"direct","protocol":"freedom","settings":{"address":"direct.example","port":443}},
                      {"tag":"backup","protocol":"vmess","settings":{"vnext":[{"address":"backup.example","port":443}]}},
                      {"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"primary.example","port":443}]}}
                    ]}
                """.trimIndent(),
            ),
        )

        val plan = appState(servers = listOf(custom)).buildXrayOutboundPlan(custom)
        val outbound = plan.proxyOutbounds.single()

        assertEquals("proxy", outbound.tag)
        assertNull(outbound.server)
        assertEquals("vless", outbound.customOutbound?.get("protocol")?.jsonPrimitive?.content)
        assertEquals(listOf("backup.example", "primary.example"), plan.dnsHostServers)

        val withoutOverride = custom.copy(server = (custom.server as Custom).copy(overrideInboundAndDns = false))
        val noOverridePlan = appState(servers = listOf(withoutOverride)).buildXrayOutboundPlan(withoutOverride)
        assertTrue(noOverridePlan.dnsHostServers.isEmpty())
    }

    @Test
    fun customOutboundWithoutAProxyCandidateFailsClearly() {
        val selected = row(
            71,
            Custom(
                remarks = "Direct only",
                configJson = """{"outbounds":[{"tag":"direct","protocol":"freedom"}]}""",
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            appState(servers = listOf(selected)).buildXrayOutboundPlan(selected)
        }

        assertTrue(error.message.orEmpty().contains("has no usable proxy outbound"))
    }

    @Test
    fun selectGroupUsesSelectedMemberAndFallsBackToFirstConfiguredMember() {
        val first = node(10, "First", "first.example")
        val second = node(20, "Second", "second.example")
        val selectedGroup = row(
            100,
            StrategyGroup(
                remarks = "Select",
                strategy = StrategyGroupConstants.TYPE_SELECT,
                proxyServerIds = listOf(10, 20),
                selectedMemberId = 20,
            ),
        )
        val withSelectedMember = appState(servers = listOf(first, second, selectedGroup))
            .buildXrayOutboundPlan(selectedGroup)
        assertEquals(listOf("proxy"), withSelectedMember.proxyOutbounds.map { it.tag })
        assertEquals("Second", withSelectedMember.proxyOutbounds.single().server?.getInfo()?.remarks)
        assertTrue(withSelectedMember.balancers.isEmpty())

        val missingSelection = selectedGroup.copy(
            server = (selectedGroup.server as StrategyGroup).copy(selectedMemberId = 999),
        )
        val firstFallback = appState(servers = listOf(first, second, missingSelection))
            .buildXrayOutboundPlan(missingSelection)
        assertEquals("First", firstFallback.proxyOutbounds.single().server?.getInfo()?.remarks)
    }

    @Test
    fun strategyGroupMembershipAdapterRebindsDuplicateIdToFirstSourceRecord() {
        val selected = node(1, "Selected", "selected.example")
        val firstSourceRecord = node(10, "Earlier source row", "earlier.example")
        val strategy = StrategyGroup(
            remarks = "Filtered",
            strategy = StrategyGroupConstants.TYPE_LEAST_PING,
            subscriptionGroupId = 2,
            filter = "Filter candidate",
        )
        val selectedGroup = row(100, strategy)
        val state = appState(
            servers = listOf(
                selected,
                firstSourceRecord,
                node(10, "Filter candidate", "later.example").copy(groupId = 2),
                selectedGroup,
            ),
        )

        assertEquals(
            listOf("Earlier source row"),
            state.strategyGroupMembers(strategy).map { it.server.getInfo().remarks },
        )
    }

    @Test
    fun filteredStrategyGroupPlannerRebindsDuplicateIdToFirstSourceRecord() {
        val selected = node(1, "Selected", "selected.example")
        val firstSourceRecord = node(10, "Earlier source row", "earlier.example")
        val laterMatchingRecord = node(10, "Filter candidate", "later.example", latency = "20ms").copy(groupId = 2)
        val selectedGroup = row(
            100,
            StrategyGroup(
                remarks = "Filtered",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                subscriptionGroupId = 2,
                filter = "Filter candidate",
            ),
        )
        val state = appState(
            servers = listOf(selected, firstSourceRecord, laterMatchingRecord, selectedGroup),
            routeRules = listOf(RouteRule(outboundTag = "100")),
        )
        val plan = state.buildXrayOutboundPlan(selected)
        assertEquals(
            "Earlier source row",
            plan.proxyOutbounds.single { it.tag == "100-policy-10" }.server?.getInfo()?.remarks,
        )
        assertEquals(listOf("selected.example", "earlier.example"), plan.dnsHostServers)
    }

    @Test
    fun automaticGroupsKeepSelectedFallbackThenChooseLatencyAndFirstProbeSettings() {
        val selected = node(1, "Selected", "selected.example")
        val slowerSelected = node(10, "Slower selected", "slow.example", latency = "120ms")
        val faster = node(20, "Faster", "fast.example", latency = "20ms")
        val failed = node(30, "Failed", "failed.example", latency = "Timeout")
        val fallbackGroup = row(
            100,
            StrategyGroup(
                remarks = "Fallback",
                strategy = StrategyGroupConstants.TYPE_FALLBACK,
                proxyServerIds = listOf(10, 20),
                selectedMemberId = 10,
                probeUrl = "https://first.example/check",
                probeInterval = "15s",
                probeTimeout = "3s",
            ),
        )
        val latencyGroup = row(
            200,
            StrategyGroup(
                remarks = "Least ping",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = listOf(30, 20),
                probeUrl = "https://later.example/check",
                probeInterval = "40s",
                probeTimeout = "8s",
            ),
        )

        val plan = appState(
            servers = listOf(selected, fallbackGroup, latencyGroup, slowerSelected, faster, failed),
            routeRules = listOf(RouteRule(outboundTag = "100"), RouteRule(outboundTag = "200")),
        ).buildXrayOutboundPlan(selected)

        assertEquals(
            listOf("proxy", "100-policy-10", "100-policy-20", "200-policy-30", "200-policy-20"),
            plan.proxyOutbounds.map { it.tag },
        )
        assertEquals(listOf("100-policy-10", "200-policy-20"), plan.balancers.map { it.fallbackTag })
        assertEquals(listOf("leastPing", "leastPing"), plan.balancers.map { it.strategy })
        assertEquals(listOf("100-policy-", "200-policy-"), plan.observatorySelectors)
        assertEquals("https://first.example/check", plan.observatoryProbeUrl)
        assertEquals("15s", plan.observatoryProbeInterval)
        assertEquals("3s", plan.observatoryProbeTimeout)

        val rendered = assertNotNull(
            buildXrayObservatory(
                selectors = plan.observatorySelectors,
                probeUrl = plan.observatoryProbeUrl,
                probeInterval = plan.observatoryProbeInterval,
                probeTimeout = plan.observatoryProbeTimeout,
            ),
        )
        assertEquals("true", rendered["enableConcurrency"]?.jsonPrimitive?.content)
        assertEquals(listOf("100-policy-", "200-policy-"), rendered["subjectSelector"]?.let { value ->
            value.jsonArray.map { it.jsonPrimitive.content }
        })
    }

    @Test
    fun disabledShadowrocketRouteStillPlansItsMatchingStrategyAlias() {
        val selected = node(1, "Selected", "selected.example")
        val first = node(10, "Node 10", "node10.example")
        val second = node(20, "Node 20", "node20.example")
        val strategy = row(
            100,
            StrategyGroup(
                remarks = "Auto",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = listOf(20, 10),
                selectedMemberId = 10,
                sourceTrafficConfigId = 7,
                sourcePolicyGroupName = "aUtO",
                probeUrl = "https://strategy.example/check",
                probeInterval = "33s",
                probeTimeout = "7s",
            ),
        )
        val group = ShadowrocketPolicyGroup(
            lineNumber = 1,
            name = "Auto",
            type = "url-test",
            members = listOf("Node 10"),
            url = "https://profile.example/check",
            intervalSeconds = 5,
            timeoutSeconds = 9,
        )

        val plan = appState(
            servers = listOf(selected, first, second, strategy),
            routeRules = listOf(RouteRule(outboundTag = group.outboundTag, enabled = false)),
            groups = listOf(group),
            trafficConfigs = emptyList(),
            subscriptionPingUrl = "https://subscription.example/check",
        ).buildXrayOutboundPlan(selected)

        val groupTag = ShadowrocketPolicyGroupTagPrefix + "Auto"
        assertEquals(listOf("proxy", "$groupTag-policy-20", "$groupTag-policy-10"), plan.proxyOutbounds.map { it.tag })
        assertEquals(groupTag, plan.balancers.single().tag)
        assertEquals("$groupTag-policy-10", plan.balancers.single().fallbackTag)
        assertEquals(XrayRouteTargetKind.Balancer, plan.routeTargets.getValue(groupTag).kind)
        assertEquals("https://strategy.example/check", plan.observatoryProbeUrl)
        assertEquals("33s", plan.observatoryProbeInterval)
        assertEquals("7s", plan.observatoryProbeTimeout)
        assertTrue(plan.observatorySelectors.contains("$groupTag-policy-"))
    }

    private fun appState(
        servers: List<ProxyServerState>,
        routeRules: List<RouteRule> = emptyList(),
        defaultRouteOutboundTag: String = "proxy",
        groups: List<ShadowrocketPolicyGroup> = emptyList(),
        trafficConfigs: List<features.config.TrafficConfigState> = emptyList(),
        enableLocalDns: Boolean = true,
        enableFragment: Boolean = false,
        subscriptionPingUrl: String = "https://subscription.example/check",
    ) = AppState(
        proxyServers = servers,
        routeRules = routeRules,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        shadowrocketPolicyGroups = groups,
        trafficConfigs = trafficConfigs,
        enableVpnLocalDns = enableLocalDns,
        enableFragment = enableFragment,
        subscriptionPingUrl = subscriptionPingUrl,
    )

    private fun node(
        id: Int,
        remarks: String,
        host: String,
        latency: String = "",
    ) = ProxyServerState(
        id = id,
        groupId = 1,
        server = VLESS(
            remarks = remarks,
            id = "00000000-0000-0000-0000-${id.toString().padStart(12, '0')}",
            server = host,
            port = "443",
        ),
        latency = latency,
    )

    private fun row(id: Int, server: features.proxy.server.model.ProxyServer<*>) = ProxyServerState(
        id = id,
        groupId = 1,
        server = server,
    )
}
