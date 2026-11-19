// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.strategyGroupLatency
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.hasMemberSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    // Every real probe, whether of a lone server or of a group's member, takes a place here. Groups measured at the
    // same time each probe their members [concurrency] at a time, so without one shared limit the probes (each holding
    // a thread until it answers or times out) multiplied to concurrency squared.
    private val probePlaces = Semaphore(concurrency.coerceAtLeast(1))

    // A run's answers by server: groups that share members, and a member also pinged on its own, reuse one probe
    // (each boots a Core instance and makes a network round trip). A tester lives for one ping run.
    private val answers = ConcurrentHashMap<ProxyServer<*>, CompletableDeferred<DesktopServerLatencyResult>>()

    private suspend fun limited(server: ProxyServer<*>): DesktopServerLatencyResult {
        val mine = CompletableDeferred<DesktopServerLatencyResult>()
        val earlier = answers.putIfAbsent(server, mine)
        if (earlier != null) return earlier.await()
        try {
            return probePlaces.withPermit { single(server) }.also(mine::complete)
        } catch (error: Throwable) {
            // A probe that did not finish is not an answer: the next asker measures again.
            answers.remove(server, mine)
            mine.completeExceptionally(error)
            throw error
        }
    }

    override suspend fun test(serverId: Int, server: ProxyServer<*>): DesktopServerLatencyResult {
        if (server !is StrategyGroup) return limited(server)
        val testable = members(server).filter { member -> member !is StrategyGroup && member.desktopTcpEndpointOrNull() != null }
        val best = strategyGroupLatency(testable, concurrency) { member ->
            (limited(member) as? DesktopServerLatencyResult.Success)?.milliseconds
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
