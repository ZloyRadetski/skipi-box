// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import kotlin.test.assertEquals
import org.junit.Test

class AndroidProxyHomeInitialGroupTest {
    @Test
    fun selectedPersistedServerSeedsItsRawNumericGroupId() {
        val servers = listOf(
            server(id = 7, groupId = 7),
            server(id = 8, groupId = 8),
            server(id = 9, groupId = AutoBalancerGroupId),
            server(id = 10, groupId = DefaultSubscriptionGroupId),
        )

        assertEquals(8, initialAndroidProxyHomeGroupId(selectedProxyServerId = 8, proxyServers = servers))
        assertEquals(AutoBalancerGroupId, initialAndroidProxyHomeGroupId(selectedProxyServerId = 9, proxyServers = servers))
        assertEquals(DefaultSubscriptionGroupId, initialAndroidProxyHomeGroupId(selectedProxyServerId = 10, proxyServers = servers))
    }

    @Test
    fun absentPersistedServerUsesTheAndroidManualGroupDefault() {
        assertEquals(
            DefaultSubscriptionGroupId,
            initialAndroidProxyHomeGroupId(selectedProxyServerId = 999, proxyServers = listOf(server(7, 7))),
        )
    }

    private fun server(id: Int, groupId: Int) = ProxyServerState(
        id = id,
        groupId = groupId,
        server = VLESS(
            remarks = "Node $id",
            id = "4219d973-8792-462f-8747-df766f70f137",
            server = "node-$id.example",
            port = "443",
        ),
    )
}
