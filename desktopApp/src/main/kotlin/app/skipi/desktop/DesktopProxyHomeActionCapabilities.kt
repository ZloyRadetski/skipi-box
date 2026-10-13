// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeServerTool

/** Capabilities backed by concrete Desktop callbacks and current subscription rows. */
internal data class DesktopProxyHomeActionCapabilities(
    val actions: Set<ProxyHomeActionId>,
    val serverTools: Set<ProxyHomeServerTool>,
)

internal fun desktopProxyHomeActionCapabilities(
    subscriptions: List<DesktopStoredSubscription>,
    onRefreshAllSubscriptions: (() -> Unit)?,
    onDeleteDuplicateServers: ((Set<Int>) -> Unit)?,
    onDeleteInvalidServers: ((Set<Int>) -> Unit)?,
    onRestartService: (() -> Unit)? = null,
    onDeleteAllServers: ((Set<Int>) -> Unit)? = null,
): DesktopProxyHomeActionCapabilities {
    val serverTools = buildSet {
        if (onRefreshAllSubscriptions != null && subscriptions.any { it.url.isNotBlank() }) {
            add(ProxyHomeServerTool.UpdateSubscriptions)
        }
        if (onDeleteDuplicateServers != null) add(ProxyHomeServerTool.DeleteDuplicateServers)
        if (onDeleteInvalidServers != null) add(ProxyHomeServerTool.DeleteInvalidServers)
        if (onRestartService != null) add(ProxyHomeServerTool.RestartService)
        if (onDeleteAllServers != null) add(ProxyHomeServerTool.DeleteAllServers)
    }
    val actions = buildSet {
        if (ProxyHomeServerTool.UpdateSubscriptions in serverTools) {
            add(ProxyHomeActionId.RefreshAllSubscriptions)
        }
        if (serverTools.isNotEmpty()) add(ProxyHomeActionId.RunServerTool)
    }
    return DesktopProxyHomeActionCapabilities(actions = actions, serverTools = serverTools)
}
