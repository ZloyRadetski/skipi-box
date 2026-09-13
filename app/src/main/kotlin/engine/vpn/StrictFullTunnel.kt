// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.DefaultRouteOutboundTag
import app.modes.ProxyAppListModeGlobal
import engine.xray.XrayTags

/**
 * Produces the VPN-start state for an opt-in strict tunnel. The stored routing
 * profile is intentionally left intact: turning this setting off restores the
 * user's per-app and Direct rules without an irreversible migration.
 *
 * This governs managed application traffic. The core still needs a protected
 * underlying connection to reach the selected proxy endpoint, and may need a
 * bootstrap DNS lookup before that connection exists.
 */
internal fun AppState.withStrictFullTunnelApplied(): AppState {
    if (!enableStrictFullTunnel) return this

    return copy(
        proxyAppListMode = ProxyAppListModeGlobal,
        proxyAppListSelectedApps = emptyList(),
        defaultRouteOutboundTag = defaultRouteOutboundTag
            .trim()
            .takeUnless { tag -> tag.equals(XrayTags.DIRECT, ignoreCase = true) }
            ?: DefaultRouteOutboundTag,
        routeRules = routeRules.map { rule ->
            if (rule.enabled && rule.outboundTag.trim().equals(XrayTags.DIRECT, ignoreCase = true)) {
                rule.copy(enabled = false)
            } else {
                rule
            }
        },
    )
}
