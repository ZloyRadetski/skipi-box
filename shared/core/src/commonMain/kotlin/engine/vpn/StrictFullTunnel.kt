// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.DefaultRouteOutboundTag
import app.modes.ProxyAppListModeGlobal
import engine.xray.XrayTags
import features.routing.model.RouteRule

/**
 * Produces the VPN-start state for an opt-in strict tunnel. The stored routing
 * profile is intentionally left intact: turning this setting off restores the
 * user's per-app and Direct rules without an irreversible migration.
 *
 * This governs managed application traffic. The core still needs a protected
 * underlying connection to reach the selected proxy endpoint, and may need a
 * bootstrap DNS lookup before that connection exists.
 */
fun AppState.withStrictFullTunnelApplied(): AppState {
    if (!enableStrictFullTunnel) return this

    val routing = strictFullTunnelRouting(defaultRouteOutboundTag, routeRules)
    return copy(
        proxyAppListMode = ProxyAppListModeGlobal,
        proxyAppListSelectedApps = emptyList(),
        defaultRouteOutboundTag = routing.defaultOutboundTag,
        routeRules = routing.rules,
    )
}

/** Global routing as it is while a strict tunnel runs. */
data class StrictFullTunnelRouting(
    val defaultOutboundTag: String,
    val rules: List<RouteRule>,
)

/**
 * The routing half of the strict tunnel for any host that stores global routing: Direct rules are
 * switched off and a Direct default route returns to the standard default. The input is never
 * modified, so turning the setting off restores the user's routing exactly.
 */
fun strictFullTunnelRouting(defaultOutboundTag: String, rules: List<RouteRule>): StrictFullTunnelRouting =
    StrictFullTunnelRouting(
        defaultOutboundTag = defaultOutboundTag
            .trim()
            .takeUnless { tag -> tag.equals(XrayTags.DIRECT, ignoreCase = true) }
            ?: DefaultRouteOutboundTag,
        rules = rules.map { rule ->
            if (rule.enabled && rule.outboundTag.trim().equals(XrayTags.DIRECT, ignoreCase = true)) {
                rule.copy(enabled = false)
            } else {
                rule
            }
        },
    )
