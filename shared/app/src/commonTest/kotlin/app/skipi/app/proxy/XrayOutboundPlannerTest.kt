// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import engine.xray.XrayRouteTargetKind
import engine.xray.buildXrayObservatory
import features.config.ShadowrocketPolicyGroup
import features.config.ShadowrocketPolicyGroupTagPrefix
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.Custom
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.routing.model.RouteRule
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shared behavior fixtures copied from observable Android planner results. */
class XrayOutboundPlannerTest {

    @Test
    fun selectedAndReferencedOutboundsKeepAndroidOrderAndFixedTargets() {
        val selected = node(10, "Selected", "selected.example")
        val defaultTarget = node(30, "Default", "default.example")
        val enabledTarget = node(20, "Enabled", "enabled.example")
        val disabledTarget = node(40, "Disabled", "disabled.example")

        val plan = XrayOutboundPlanner.plan(
            input(
                selected = selected,
                servers = listOf(selected, defaultTarget, enabledTarget, disabledTarget),
                routeRules = listOf(
                    RouteRule(outboundTag = "20"),
                    RouteRule(outboundTag = "40", enabled = false),
                ),
                defaultRouteOutboundTag = "30",
                enableLocalDns = true,
                enableFragment = true,
            ),
        )

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

        val withoutOptionalTargets = XrayOutboundPlanner.plan(
            input(
                selected = selected,
                servers = listOf(selected),
                defaultRouteOutboundTag = "proxy",
                enableLocalDns = false,
                enableFragment = false,
            ),
        )
        assertEquals(listOf("proxy", "direct", "block"), withoutOptionalTargets.routeTargets.keys.toList())
    }

    @Test
    fun chainReversesUsableMembersAndAssignsDialerTagsWithoutFragment() {
        val hop1 = node(1, "Hop 1", "hop1.example")
        val nestedChain = record(2, ChainProxy(remarks = "Nested", proxyServerIds = listOf(1, 5)))
        val custom = record(3, Custom(remarks = "Custom", configJson = "{\"outbounds\":[]}"))
        val hop4 = node(4, "Hop 4", "hop4.example")
        val hop5 = node(5, "Hop 5", "hop5.example")
        val selected = record(99, ChainProxy(remarks = "Three hop", proxyServerIds = listOf(1, 2, 3, 4, 5)))

        val plan = XrayOutboundPlanner.plan(
            input(selected, listOf(selected, hop1, nestedChain, custom, hop4, hop5), enableFragment = true),
        )

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
        val selected = record(99, ChainProxy(remarks = "Incomplete", proxyServerIds = listOf(1, 3, 404)))
        val custom = record(3, Custom(remarks = "Filtered custom", configJson = "{\"outbounds\":[]}"))

        val error = assertFailsWith<IllegalStateException> {
            XrayOutboundPlanner.plan(input(selected, listOf(selected, onlyUsable, custom)))
        }

        assertTrue(error.message.orEmpty().contains("at least two available proxy servers"))
    }

    @Test
    fun strategyGroupWithoutAvailableMembersFailsClearly() {
        val selected = record(
            100,
            StrategyGroup(
                remarks = "Empty",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                proxyServerIds = emptyList(),
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            XrayOutboundPlanner.plan(input(selected, listOf(selected)))
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

        val plan = XrayOutboundPlanner.plan(
            input(
                selected = selected,
                servers = listOf(selected),
                routeRules = listOf(RouteRule(outboundTag = group.outboundTag, enabled = false)),
                groups = listOf(group),
            ),
        )

        assertEquals(listOf("proxy"), plan.proxyOutbounds.map { it.tag })
        assertFalse(plan.routeTargets.containsKey(group.outboundTag))
        assertFalse(plan.balancers.any { it.tag == group.outboundTag })
    }

    @Test
    fun customOutboundSelectsProxyAndContributesHostsOnlyWhenOverrideIsEnabled() {
        val selected = record(
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

        val plan = XrayOutboundPlanner.plan(input(selected, listOf(selected)))
        val outbound = plan.proxyOutbounds.single()

        assertEquals("proxy", outbound.tag)
        assertNull(outbound.server)
        assertEquals("vless", outbound.customOutbound?.get("protocol")?.jsonPrimitive?.content)
        assertEquals(listOf("backup.example", "primary.example"), plan.dnsHostServers)

        val withoutOverride = selected.copy(
            server = (selected.server as Custom).copy(overrideInboundAndDns = false),
        )
        val noOverridePlan = XrayOutboundPlanner.plan(input(withoutOverride, listOf(withoutOverride)))
        assertTrue(noOverridePlan.dnsHostServers.isEmpty())
    }

    @Test
    fun customOutboundWithoutAProxyCandidateFailsClearly() {
        val selected = record(
            71,
            Custom(
                remarks = "Direct only",
                configJson = """{"outbounds":[{"tag":"direct","protocol":"freedom"}]}""",
            ),
        )

        val error = assertFailsWith<IllegalStateException> {
            XrayOutboundPlanner.plan(input(selected, listOf(selected)))
        }

        assertTrue(error.message.orEmpty().contains("has no usable proxy outbound"))
    }

    @Test
    fun selectGroupUsesSelectedMemberAndFallsBackToFirstConfiguredMember() {
        val first = node(10, "First", "first.example")
        val second = node(20, "Second", "second.example")
        val selectedGroup = record(
            100,
            StrategyGroup(
                remarks = "Select",
                strategy = StrategyGroupConstants.TYPE_SELECT,
                proxyServerIds = listOf(10, 20),
                selectedMemberId = 20,
            ),
        )
        val withSelectedMember = XrayOutboundPlanner.plan(
            input(selectedGroup, listOf(first, second, selectedGroup)),
        )
        assertEquals(listOf("proxy"), withSelectedMember.proxyOutbounds.map { it.tag })
        assertEquals("Second", withSelectedMember.proxyOutbounds.single().server?.getInfo()?.remarks)
        assertTrue(withSelectedMember.balancers.isEmpty())

        val missingSelection = selectedGroup.copy(
            server = (selectedGroup.server as StrategyGroup).copy(selectedMemberId = 999),
        )
        val firstFallback = XrayOutboundPlanner.plan(
            input(missingSelection, listOf(first, second, missingSelection)),
        )
        assertEquals("First", firstFallback.proxyOutbounds.single().server?.getInfo()?.remarks)
    }

    @Test
    fun filteredStrategyGroupRebindsDuplicateIdToFirstSourceRecord() {
        val selected = node(1, "Selected", "selected.example")
        val firstSourceRecord = node(10, "Earlier source row", "earlier.example")
        val laterMatchingRecord = node(10, "Filter candidate", "later.example", latency = "20ms").copy(groupId = 2)
        val selectedGroup = record(
            100,
            StrategyGroup(
                remarks = "Filtered",
                strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                subscriptionGroupId = 2,
                filter = "Filter candidate",
            ),
        )

        val plan = XrayOutboundPlanner.plan(
            input(
                selected = selected,
                servers = listOf(selected, firstSourceRecord, laterMatchingRecord, selectedGroup),
                routeRules = listOf(RouteRule(outboundTag = "100")),
            ),
        )

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
        val fallbackGroup = record(
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
        val latencyGroup = record(
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

        val plan = XrayOutboundPlanner.plan(
            input(
                selected = selected,
                servers = listOf(selected, fallbackGroup, latencyGroup, slowerSelected, faster, failed),
                routeRules = listOf(RouteRule(outboundTag = "100"), RouteRule(outboundTag = "200")),
            ),
        )

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
        assertEquals(
            listOf("100-policy-", "200-policy-"),
            rendered["subjectSelector"]?.let { value ->
                (value as kotlinx.serialization.json.JsonArray).map { it.jsonPrimitive.content }
            },
        )
    }

    @Test
    fun disabledShadowrocketRouteStillPlansItsMatchingStrategyAlias() {
        val selected = node(1, "Selected", "selected.example")
        val first = node(10, "Node 10", "node10.example")
        val second = node(20, "Node 20", "node20.example")
        val strategy = record(
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

        val plan = XrayOutboundPlanner.plan(
            input(
                selected = selected,
                servers = listOf(selected, first, second, strategy),
                routeRules = listOf(RouteRule(outboundTag = group.outboundTag, enabled = false)),
                groups = listOf(group),
                trafficConfigContentById = mapOf(7 to "[Proxy Group]\nAuto = url-test, Node 10"),
                subscriptionPingUrl = "https://subscription.example/check",
            ),
        )

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

    private fun input(
        selected: ProxyServerRecord,
        servers: List<ProxyServerRecord>,
        routeRules: List<RouteRule> = emptyList(),
        defaultRouteOutboundTag: String = "proxy",
        groups: List<ShadowrocketPolicyGroup> = emptyList(),
        trafficConfigContentById: Map<Int, String> = emptyMap(),
        enableLocalDns: Boolean = true,
        enableFragment: Boolean = false,
        subscriptionPingUrl: String = "https://subscription.example/check",
    ) = XrayOutboundPlanningInput(
        selectedServer = selected,
        servers = servers,
        routeRules = routeRules,
        defaultRouteOutboundTag = defaultRouteOutboundTag,
        shadowrocketPolicyGroups = groups,
        trafficConfigContentById = trafficConfigContentById,
        enableLocalDns = enableLocalDns,
        enableFragment = enableFragment,
        subscriptionPingUrl = subscriptionPingUrl,
        stripLeadingFlag = { value -> value },
    )

    private fun node(
        id: Int,
        remarks: String,
        host: String,
        latency: String = "",
    ) = ProxyServerRecord(
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

    private fun record(id: Int, server: ProxyServer<*>) = ProxyServerRecord(
        id = id,
        groupId = 1,
        server = server,
    )
}
