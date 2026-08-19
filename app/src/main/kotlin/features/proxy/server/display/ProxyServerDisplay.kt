// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.display

import features.subscription.DefaultSubscriptionGroupId
import app.ProxyServerState
import app.SubscriptionGroupState
import ui.text.formatTemplate

internal fun SubscriptionGroupState.displayName(defaultGroupName: String): String {
    return if (builtIn && id == DefaultSubscriptionGroupId) {
        defaultGroupName
    } else {
        name
    }
}

internal fun List<SubscriptionGroupState>.displayNameById(defaultGroupName: String): Map<Int, String> {
    return associate { group ->
        group.id to group.displayName(defaultGroupName)
    }
}

internal fun ProxyServerState.displayNameWithGroup(
    defaultProxyServerTemplate: String,
    groupNames: Map<Int, String>,
    unknownGroupName: String,
): String {
    val proxyServerName = server.getInfo().remarks.ifBlank {
        defaultProxyServerTemplate.formatTemplate("id" to id)
    }
    if (groupId == DefaultSubscriptionGroupId) {
        return proxyServerName
    }
    val groupName = groupNames[groupId] ?: unknownGroupName
    return "$proxyServerName ($groupName)"
}

internal fun ProxyServerState.displayName(): String {
    val info = server.getInfo()
    return info.remarks.ifBlank { info.protocol.ifBlank { "#$id" } }
}
