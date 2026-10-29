// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.ProxyServerState
import app.skipi.app.home.rawInitialProxyHomeGroupId
import features.subscription.DefaultSubscriptionGroupId

fun initialAndroidProxyHomeGroupId(
    selectedProxyServerId: Int?,
    proxyServers: List<ProxyServerState>,
): Int = rawInitialProxyHomeGroupId(
    selectedServerId = selectedProxyServerId,
    decodedServerGroupIds = proxyServers.map { server -> server.id to server.groupId },
    defaultGroupId = DefaultSubscriptionGroupId,
)
