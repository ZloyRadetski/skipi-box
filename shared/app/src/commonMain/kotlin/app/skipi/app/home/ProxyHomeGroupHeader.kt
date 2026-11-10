// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

/**
 * The header over a group's servers that is not a subscription: manual servers, the user's own proxy
 * groups and the balancers. It carries the group's ping, as a subscription's header does, and none of
 * the subscription's refresh or settings.
 */
data class ProxyHomeGroupHeader(
    val title: String,
    val serverCount: Int,
    /** One of this group's servers is being measured right now. */
    val pinging: Boolean,
    /** What the ping button sends: the group test, or the cancel of the run in progress. Null: nothing it can do now. */
    val pingAction: ProxyHomeAction?,
)

/** Null where the page shows no such header: subscriptions bring their own, and an empty group shows its empty state. */
fun ProxyHomeUiState.groupHeader(page: ProxyHomePageUiState): ProxyHomeGroupHeader? {
    val group = page.group
    if (group.kind != ProxyHomeGroupKind.Manual && group.kind != ProxyHomeGroupKind.AutoBalancer) return null
    if (page.servers.isEmpty()) return null
    val pinging = page.servers.any(ProxyServerSummary::latencyTesting)
    val pingAction = when {
        // The run is cancelled even while the host is busy with it.
        pinging -> ProxyHomeAction.CancelLatencyTests.takeIf { ProxyHomeActionId.CancelLatencyTests in availableActions }
        group.enabled && !isTestingLatency && canDispatch(ProxyHomeActionId.TestGroup) ->
            ProxyHomeAction.TestGroup(group.id)
        else -> null
    }
    return ProxyHomeGroupHeader(
        title = group.title,
        serverCount = page.servers.size,
        pinging = pinging,
        pingAction = pingAction,
    )
}

private fun ProxyHomeUiState.canDispatch(actionId: ProxyHomeActionId): Boolean =
    actionId in availableActions && actionId !in busyActions
