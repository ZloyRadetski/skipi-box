// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import app.skipi.app.server.ProxyServerEditApplyOutcome
import app.skipi.app.server.withProxyServerCopyTarget
import features.proxy.server.model.encodePersistedProxyServer

/**
 * Adds a draft to a temporary Desktop library for export. Raw rows are carried through unchanged,
 * so opaque payloads stay owned by the persistence adapter and the real allocation counter does
 * not move.
 */
internal fun DesktopServerLibrary.withProxyServerCopyTarget(target: ProxyServerRecord): DesktopServerLibrary {
    val storedTarget = DesktopStoredProxyServer(
        id = target.id,
        serverJson = target.server.encodePersistedProxyServer(),
        subscriptionId = target.sourceSubscriptionId,
    )
    return copy(
        selectedServerId = target.id,
        servers = servers.withProxyServerCopyTarget(storedTarget, DesktopStoredProxyServer::id),
    )
}

/** Converts a visible Home group ID into the captured numeric return group used by the editor. */
internal fun desktopProxyServerGroupIdForHomeGroup(groupId: String?): Int? = when (groupId) {
    DesktopProxyGroupIds.Manual -> DesktopProxyGroupIds.DefaultManualSubscriptionId
    DesktopProxyGroupIds.AutoBalancers -> DefaultAutoBalancerGroupId
    DesktopProxyGroupIds.All -> 0
    else -> groupId
        ?.takeIf { value -> value.startsWith("subscription:") }
        ?.removePrefix("subscription:")
        ?.toIntOrNull()
        ?.takeIf { id -> id > 0 }
}

/** Saved results alone may request a Home group change; failure and deletion have no group effect. */
internal fun ProxyServerEditApplyOutcome.toDesktopProxyHomeGroupId(): String? =
    (this as? ProxyServerEditApplyOutcome.Saved)
        ?.selectedGroupId
        ?.let { selectedGroupId -> desktopGroupIdForServerGroup(selectedGroupId) }

internal data class DesktopProxyHomeGroupSelectionResolution(
    val groupToSelect: String?,
    val pendingGroupId: String?,
)

/** Retains a saved group request until the updated Home input publishes that group. */
internal fun resolvePendingDesktopProxyHomeGroupSelection(
    pendingGroupId: String?,
    availableGroupIds: Set<String>,
): DesktopProxyHomeGroupSelectionResolution = when {
    pendingGroupId == null -> DesktopProxyHomeGroupSelectionResolution(
        groupToSelect = null,
        pendingGroupId = null,
    )
    pendingGroupId in availableGroupIds -> DesktopProxyHomeGroupSelectionResolution(
        groupToSelect = pendingGroupId,
        pendingGroupId = null,
    )
    else -> DesktopProxyHomeGroupSelectionResolution(
        groupToSelect = null,
        pendingGroupId = pendingGroupId,
    )
}

private fun desktopGroupIdForServerGroup(groupId: Int): String? = when (groupId) {
    DesktopProxyGroupIds.DefaultManualSubscriptionId -> DesktopProxyGroupIds.Manual
    DefaultAutoBalancerGroupId -> DesktopProxyGroupIds.AutoBalancers
    0 -> DesktopProxyGroupIds.All
    else -> groupId.takeIf { it > 0 }?.let(DesktopProxyGroupIds::subscription)
}
