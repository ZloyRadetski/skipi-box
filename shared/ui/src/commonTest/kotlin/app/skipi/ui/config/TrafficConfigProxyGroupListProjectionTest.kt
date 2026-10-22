// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.proxy.ProxyServerRecord
import features.config.TrafficConfigState
import features.proxy.server.model.OutboundObject
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerInfo
import features.proxy.server.model.ProxyServerValidationIssue
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficConfigProxyGroupListProjectionTest {
    @Test
    fun sourcedRowsPreserveProfileAndParserOrderAndUseProfileLineKeys() {
        val inactiveFirst = profile(
            id = 41,
            name = "First profile",
            rawConfig = """# header
[Proxy Group]
Repeated = select, DIRECT, REJECT
Latency = url-test, DIRECT
""",
        )
        val activeSecond = profile(
            id = 3,
            name = "Second profile",
            rawConfig = """[Proxy Group]
Repeated = load-balance, DIRECT
""",
        )

        assertEquals(
            listOf(
                TrafficConfigProxyGroupItem(
                    key = "source:41:3",
                    name = "Repeated",
                    subtitle = "First profile",
                    badge = "select",
                ),
                TrafficConfigProxyGroupItem(
                    key = "source:41:4",
                    name = "Latency",
                    subtitle = "First profile",
                    badge = "url-test",
                ),
                TrafficConfigProxyGroupItem(
                    key = "source:3:2",
                    name = "Repeated",
                    subtitle = "Second profile",
                    badge = "load-balance",
                ),
            ),
            projectTrafficConfigSourcedProxyGroups(listOf(inactiveFirst, activeSecond)),
        )
    }

    @Test
    fun globalRowsPreserveCatalogOrderAndSafeCastEligibility() {
        assertEquals(
            listOf(
                TrafficConfigProxyGroupItem(
                    key = "global:70",
                    name = "Global group",
                    subtitle = "select, all",
                    badge = "StrategyGroup",
                ),
                TrafficConfigProxyGroupItem(
                    key = "global:71",
                    name = "Non-strategy auto-row",
                    subtitle = "node.example:443",
                    badge = "StrategyGroup",
                ),
                TrafficConfigProxyGroupItem(
                    key = "global:74",
                    name = "Second global group",
                    subtitle = "select, all",
                    badge = "StrategyGroup",
                ),
            ),
            projectTrafficConfigGlobalProxyGroups(
                listOf(
                    ProxyServerRecord(
                        id = 70,
                        groupId = DefaultAutoBalancerGroupId,
                        server = StrategyGroup(remarks = "Global group"),
                    ),
                    // The Android safe cast compares null to null for a non-StrategyGroup server.
                    ProxyServerRecord(
                        id = 71,
                        groupId = DefaultAutoBalancerGroupId,
                        server = VLESS(
                            remarks = "Non-strategy auto-row",
                            id = "node-71",
                            server = "node.example",
                            port = "443",
                        ),
                    ),
                    ProxyServerRecord(
                        id = 72,
                        groupId = DefaultAutoBalancerGroupId,
                        server = StrategyGroup(remarks = "Profile-owned", sourceTrafficConfigId = 41),
                    ),
                    ProxyServerRecord(
                        id = 73,
                        groupId = 5,
                        server = StrategyGroup(remarks = "Other group"),
                    ),
                    ProxyServerRecord(
                        id = 74,
                        groupId = DefaultAutoBalancerGroupId,
                        server = StrategyGroup(remarks = "Second global group"),
                    ),
                ),
            ),
        )
    }

    @Test
    fun globalRowsFallBackToProtocolAndAutoBalancerSubtitleForBlankInfoFields() {
        assertEquals(
            listOf(
                TrafficConfigProxyGroupItem(
                    key = "global:80",
                    name = "Synthetic proxy",
                    subtitle = "Auto-Balancer",
                    badge = "StrategyGroup",
                ),
            ),
            projectTrafficConfigGlobalProxyGroups(
                listOf(
                    ProxyServerRecord(
                        id = 80,
                        groupId = DefaultAutoBalancerGroupId,
                        server = ProxyServerInfoStub(
                            ProxyServerInfo(
                                remarks = "",
                                address = "",
                                protocol = "Synthetic proxy",
                            ),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun emptyCatalogsProduceNoGlobalOrSourcedRows() {
        assertEquals(emptyList(), projectTrafficConfigGlobalProxyGroups(emptyList()))
        assertEquals(emptyList(), projectTrafficConfigSourcedProxyGroups(emptyList()))
    }

    private fun profile(id: Int, name: String, rawConfig: String) = TrafficConfigState(
        id = id,
        name = name,
        rawConfig = rawConfig,
    )
}

/** Test-only server with blank information fields to exercise the projection fallbacks. */
private class ProxyServerInfoStub(
    private val info: ProxyServerInfo,
) : ProxyServer<ProxyServerInfoStub> {
    override fun getInfo(): ProxyServerInfo = info

    override fun toXrayOutbound(tag: String): OutboundObject = error("not used by the list projection")

    override fun update(other: ProxyServer<*>) = error("not used by the list projection")

    override fun validateBasic(): List<ProxyServerValidationIssue> = emptyList()

    override fun validateFull(): List<ProxyServerValidationIssue> = emptyList()

    override fun connectionFingerprint(): String = "test"
}
