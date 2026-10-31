// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.strategyGroupLatency
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.hasMemberSource

/** A server with its own endpoint, or a strategy group measured through its members as on Android. */
internal fun ProxyServer<*>.canBePingedOnDesktop(): Boolean =
    this is StrategyGroup || desktopTcpEndpointOrNull() != null

/**
 * Pings a strategy group through its members, the best answer becoming the group's latency, and any
 * other server with [single] as before.
 */
internal class DesktopStrategyGroupAwareTester(
    private val members: (StrategyGroup) -> List<ProxyServer<*>>,
    private val concurrency: Int,
    private val single: suspend (ProxyServer<*>) -> DesktopServerLatencyResult,
) : DesktopProxyHomeTcpTester {
    override suspend fun test(serverId: Int, server: ProxyServer<*>): DesktopServerLatencyResult {
        if (server !is StrategyGroup) return single(server)
        val testable = members(server).filter { member -> member !is StrategyGroup && member.desktopTcpEndpointOrNull() != null }
        val best = strategyGroupLatency(testable, concurrency) { member ->
            (single(member) as? DesktopServerLatencyResult.Success)?.milliseconds
        }
        return best?.let(DesktopServerLatencyResult::Success)
            ?: DesktopServerLatencyResult.Error("No member of the group answered")
    }
}

/** The failure text of a card: a strategy group without any source of members says so instead of "timeout". */
internal fun desktopLatencyErrorText(
    server: ProxyServer<*>,
    result: DesktopServerLatencyResult?,
    noMembersText: String,
): String? = noMembersText.takeIf {
    result is DesktopServerLatencyResult.Error && server is StrategyGroup && !server.hasMemberSource()
}
