// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import app.ProxyServerState
import app.skipi.ui.config.TrafficConfigProxyGroupItem
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.OutboundObject
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerInfo
import features.proxy.server.model.ProxyServerValidationIssue
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import org.junit.Test
import kotlin.test.assertEquals

/** Source-derived oracle for the inline global and profile-sourced rows in TrafficConfigPage. */
class TrafficConfigProxyGroupListAndroidOracleTest {
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

        val rows = projectTrafficConfigSourcedProxyGroups(listOf(inactiveFirst, activeSecond))

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
            rows,
        )
    }

    @Test
    fun globalRowsPreserveCatalogOrderAndUseAndroidSafeCastEligibility() {
        val rows = projectTrafficConfigGlobalProxyGroups(
            listOf(
                ProxyServerState(
                    id = 70,
                    groupId = AutoBalancerGroupId,
                    server = StrategyGroup(remarks = "Global group"),
                ),
                // Android's `(server as? StrategyGroup)?.sourceTrafficConfigId == null`
                // also includes a non-strategy value placed in the auto-balancer group.
                ProxyServerState(
                    id = 71,
                    groupId = AutoBalancerGroupId,
                    server = VLESS(
                        remarks = "Non-strategy auto-row",
                        id = "node-71",
                        server = "node.example",
                        port = "443",
                    ),
                ),
                ProxyServerState(
                    id = 72,
                    groupId = AutoBalancerGroupId,
                    server = StrategyGroup(remarks = "Profile-owned", sourceTrafficConfigId = 41),
                ),
                ProxyServerState(
                    id = 73,
                    groupId = 5,
                    server = StrategyGroup(remarks = "Other group"),
                ),
                ProxyServerState(
                    id = 74,
                    groupId = AutoBalancerGroupId,
                    server = StrategyGroup(remarks = "Second global group"),
                ),
            ),
        )

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
            rows,
        )
    }

    @Test
    fun globalRowsFallBackToProtocolAndAutoBalancerSubtitleForBlankInfoFields() {
        val rows = projectTrafficConfigGlobalProxyGroups(
            listOf(
                ProxyServerState(
                    id = 80,
                    groupId = AutoBalancerGroupId,
                    server = ProxyServerInfoStub(
                        ProxyServerInfo(
                            remarks = "",
                            address = "",
                            protocol = "Synthetic proxy",
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                TrafficConfigProxyGroupItem(
                    key = "global:80",
                    name = "Synthetic proxy",
                    subtitle = "Auto-Balancer",
                    badge = "StrategyGroup",
                ),
            ),
            rows,
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

/** Test-only server with blank information fields to exercise the Android fallbacks directly. */
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
