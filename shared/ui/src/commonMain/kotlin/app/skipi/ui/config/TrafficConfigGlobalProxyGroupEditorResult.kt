// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.server.ProxyServerEditResult
import app.skipi.ui.server.editor.editableCopy
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup

fun trafficConfigGlobalProxyGroupEditResult(
    server: ProxyServer<*>,
    serverId: Int?,
): ProxyServerEditResult? {
    val strategyGroup = server as? StrategyGroup ?: return null
    return ProxyServerEditResult(
        serverId = serverId,
        server = strategyGroup,
        groupId = DefaultAutoBalancerGroupId,
        returnGroupId = DefaultAutoBalancerGroupId,
        deleted = false,
    )
}

fun trafficConfigGlobalProxyGroupDuplicateResult(
    server: ProxyServer<*>,
    copyName: String,
): ProxyServerEditResult? {
    val strategyGroup = server as? StrategyGroup ?: return null
    val duplicate = strategyGroup.editableCopy() as StrategyGroup
    duplicate.remarks = copyName
    return ProxyServerEditResult(
        serverId = null,
        server = duplicate,
        groupId = DefaultAutoBalancerGroupId,
        returnGroupId = DefaultAutoBalancerGroupId,
        deleted = false,
    )
}
