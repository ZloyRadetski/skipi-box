// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.RoutingConfigRecord
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.app.server.toProxyServerCopyTarget
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.Socks
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.LocalProxyXrayConfigOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DesktopManualGroupNamespaceTest {
    @Test
    fun copyTargetKeepsSubscriptionOneDistinctFromTheManualHomeGroup() {
        val realSubscriptionGroupId = 1
        val manualGroupId = assertNotNull(
            desktopProxyServerGroupIdForHomeGroup(DesktopProxyGroupIds.Manual),
        )
        assertNotEquals(realSubscriptionGroupId, manualGroupId)

        val subscriptionTarget = ProxyServerEditResult(
            serverId = 10,
            server = Socks(remarks = "Provider node", server = "provider.example", port = "1080"),
            groupId = realSubscriptionGroupId,
        ).toProxyServerCopyTarget(defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId)
        val manualTarget = ProxyServerEditResult(
            serverId = 11,
            server = Socks(remarks = "Manual node", server = "manual.example", port = "1080"),
            groupId = manualGroupId,
        ).toProxyServerCopyTarget(defaultGroupId = DesktopProxyGroupIds.DefaultManualSubscriptionId)

        assertEquals(realSubscriptionGroupId, subscriptionTarget.sourceSubscriptionId)
        assertNull(manualTarget.sourceSubscriptionId)
    }

    @Test
    fun savedGroupOneReturnsToItsSubscriptionWhileManualUsesItsSeparateNumericId() {
        val manualGroupId = assertNotNull(
            desktopProxyServerGroupIdForHomeGroup(DesktopProxyGroupIds.Manual),
        )

        val subscriptionHomeGroup = savedGroup(1).toDesktopProxyHomeGroupId()
        val manualHomeGroup = savedGroup(manualGroupId).toDesktopProxyHomeGroupId()

        assertEquals(DesktopProxyGroupIds.subscription(1), subscriptionHomeGroup)
        assertEquals(DesktopProxyGroupIds.Manual, manualHomeGroup)
        assertNotEquals(subscriptionHomeGroup, manualHomeGroup)
    }

    @Test
    fun manualStrategyGroupResolvesOnlyManualServersBeforeSubscriptionOne() {
        val manualGroupId = assertNotNull(
            desktopProxyServerGroupIdForHomeGroup(DesktopProxyGroupIds.Manual),
        )
        val config = buildSelectedGroupConfig(
            groupId = manualGroupId,
            servers = listOf(
                stored(10, Socks(remarks = "Manual node", server = "manual.example", port = "1080")),
                stored(
                    20,
                    Socks(remarks = "Provider node", server = "provider.example", port = "1080"),
                    subscriptionId = 1,
                ),
            ),
        )

        assertEquals(listOf("manual.example"), outboundAddresses(config))
    }

    @Test
    fun subscriptionOneStrategyGroupResolvesOnlyItsServersBeforeManualServers() {
        val manualGroupId = assertNotNull(
            desktopProxyServerGroupIdForHomeGroup(DesktopProxyGroupIds.Manual),
        )
        val config = buildSelectedGroupConfig(
            groupId = 1,
            servers = listOf(
                stored(
                    20,
                    Socks(remarks = "Provider node", server = "provider.example", port = "1080"),
                    subscriptionId = 1,
                ),
                stored(10, Socks(remarks = "Manual node", server = "manual.example", port = "1080")),
            ),
        )

        assertNotEquals(1, manualGroupId)
        assertEquals(listOf("provider.example"), outboundAddresses(config))
    }

    private fun savedGroup(groupId: Int) = ProxyServerEditApplyOutcome.Saved(
        serverId = 27,
        wasExistingAtCommit = false,
        selectedGroupId = groupId,
    )

    private fun buildSelectedGroupConfig(
        groupId: Int,
        servers: List<DesktopStoredProxyServer>,
    ): String {
        val strategyId = 90
        val library = DesktopServerLibrary(
            selectedServerId = strategyId,
            servers = servers + stored(
                strategyId,
                StrategyGroup(
                    remarks = "Namespace verification group",
                    strategy = StrategyGroupConstants.TYPE_LEAST_PING,
                    subscriptionGroupId = groupId,
                ),
            ),
        )
        return assertNotNull(
            DesktopSelectedServerXrayConfigFactory.build(
                serverLibrary = library,
                routing = RoutingConfigRecord(),
                trafficConfigContentById = emptyMap(),
                options = LocalProxyXrayConfigOptions(socksPort = 19_080),
                subscriptionPingUrl = "http://127.0.0.1:18082/subscription-ping",
                latencyByServerId = emptyMap(),
            ),
        ).getOrThrow()
    }

    private fun outboundAddresses(config: String): List<String> =
        Json.parseToJsonElement(config).jsonObject.getValue("outbounds").jsonArray.mapNotNull { outbound ->
            outbound.jsonObject["settings"]
                ?.jsonObject
                ?.get("address")
                ?.jsonPrimitive
                ?.content
        }

    private fun stored(
        id: Int,
        server: ProxyServer<*>,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
}
