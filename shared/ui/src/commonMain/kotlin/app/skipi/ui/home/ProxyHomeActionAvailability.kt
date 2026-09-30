// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeUiState

internal fun ProxyHomeAction.requiredActionId(): ProxyHomeActionId? = when (this) {
    is ProxyHomeAction.SelectGroup,
    is ProxyHomeAction.SetSearchQuery,
    is ProxyHomeAction.SetSearchVisible,
    is ProxyHomeAction.ToggleSubscriptionExpanded,
    ProxyHomeAction.DismissMessage -> null
    ProxyHomeAction.ToggleTunnel -> ProxyHomeActionId.ToggleTunnel
    is ProxyHomeAction.SelectServer -> ProxyHomeActionId.SelectServer
    is ProxyHomeAction.TestServer -> ProxyHomeActionId.TestServer
    is ProxyHomeAction.TestVisibleServers,
    ProxyHomeAction.TestAllVisibleServers -> ProxyHomeActionId.TestVisibleServers
    is ProxyHomeAction.TestGroup -> ProxyHomeActionId.TestGroup
    ProxyHomeAction.CancelLatencyTests -> ProxyHomeActionId.CancelLatencyTests
    is ProxyHomeAction.RefreshSubscription -> ProxyHomeActionId.RefreshSubscription
    ProxyHomeAction.RefreshAllSubscriptions -> ProxyHomeActionId.RefreshAllSubscriptions
    is ProxyHomeAction.PingSubscription -> ProxyHomeActionId.PingSubscription
    is ProxyHomeAction.ToggleSubscriptionEnabled -> ProxyHomeActionId.ToggleSubscriptionEnabled
    is ProxyHomeAction.AddServer -> ProxyHomeActionId.AddServer
    ProxyHomeAction.AddSubscription -> ProxyHomeActionId.AddSubscription
    is ProxyHomeAction.ImportServers -> ProxyHomeActionId.ImportServers
    is ProxyHomeAction.EditServer -> ProxyHomeActionId.EditServer
    is ProxyHomeAction.DeleteServer -> ProxyHomeActionId.DeleteServer
    is ProxyHomeAction.ShowServerQr -> ProxyHomeActionId.ShowServerQr
    is ProxyHomeAction.CopyServer -> ProxyHomeActionId.CopyServer
    is ProxyHomeAction.EditSubscription -> ProxyHomeActionId.EditSubscription
    is ProxyHomeAction.EditGroup -> ProxyHomeActionId.EditGroup
    is ProxyHomeAction.DeleteGroup -> ProxyHomeActionId.DeleteGroup
    is ProxyHomeAction.MoveGroup -> ProxyHomeActionId.MoveGroup
    is ProxyHomeAction.MoveServer -> ProxyHomeActionId.MoveServer
    is ProxyHomeAction.OpenStrategyMemberPicker -> ProxyHomeActionId.OpenStrategyMemberPicker
    is ProxyHomeAction.SelectStrategyMember -> ProxyHomeActionId.SelectStrategyMember
    is ProxyHomeAction.SetSort -> ProxyHomeActionId.SetSort
    is ProxyHomeAction.RunServerTool -> ProxyHomeActionId.RunServerTool
    is ProxyHomeAction.OpenExternalLink -> ProxyHomeActionId.OpenExternalLink
}

internal fun canDispatchHomeAction(
    actionId: ProxyHomeActionId,
    availableActions: Set<ProxyHomeActionId>,
    busyActions: Set<ProxyHomeActionId>,
    allowBusy: Boolean = false,
): Boolean = actionId in availableActions && (allowBusy || actionId !in busyActions)

internal fun canTestHomeGroup(
    groupEnabled: Boolean,
    serverCount: Int,
    availableActions: Set<ProxyHomeActionId>,
    busyActions: Set<ProxyHomeActionId>,
    isTestingLatency: Boolean = false,
): Boolean {
    return groupEnabled && serverCount > 0 && !isTestingLatency && canDispatchHomeAction(
        actionId = ProxyHomeActionId.TestGroup,
        availableActions = availableActions,
        busyActions = busyActions,
        allowBusy = false,
    )
}

internal fun canToggleHomePower(
    canToggleTunnel: Boolean,
    tunnelBusy: Boolean,
    isConnecting: Boolean,
    availableActions: Set<ProxyHomeActionId>,
    busyActions: Set<ProxyHomeActionId>,
): Boolean {
    val isBusy = tunnelBusy || isConnecting || ProxyHomeActionId.ToggleTunnel in busyActions
    return canToggleTunnel && !isBusy && canDispatchHomeAction(
        actionId = ProxyHomeActionId.ToggleTunnel,
        availableActions = availableActions,
        busyActions = busyActions,
        allowBusy = false,
    )
}

internal fun dispatchHomeAction(
    state: ProxyHomeUiState,
    action: ProxyHomeAction,
    onAction: (ProxyHomeAction) -> Unit,
) {
    val actionId = action.requiredActionId()
    if (actionId == null || canDispatchHomeAction(actionId, state.availableActions, state.busyActions)) {
        onAction(action)
    }
}
