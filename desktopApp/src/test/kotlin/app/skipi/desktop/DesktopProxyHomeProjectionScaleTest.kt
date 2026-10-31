// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationLabels
import features.proxy.server.presentation.ProxyServerPresentationNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * Home is projected again for every latency result and tunnel change. With many subscriptions the
 * cost has to stay linear in the number of servers, not square.
 */
class DesktopProxyHomeProjectionScaleTest {
    private val subscriptionCount = 60
    private val serversPerSubscription = 500

    private class Fixture(
        val groupCatalog: DesktopProxyGroupCatalog,
        val decoded: List<Pair<DesktopStoredProxyServer, features.proxy.server.model.ProxyServer<*>?>>,
        val subscriptions: DesktopSubscriptionLibrary,
        val nodes: List<ProxyServerPresentationNode>,
    )

    private fun fixture(): Fixture {
        val stored = (1..subscriptionCount).flatMap { sub ->
            (1..serversPerSubscription).map { index ->
                val id = sub * 10_000 + index
                DesktopStoredProxyServer(
                    id = id,
                    subscriptionId = sub,
                    serverJson = VLESS(
                        remarks = "Node $sub/$index",
                        id = "uuid-$id",
                        server = "node-$id.example",
                        port = "443",
                    ).encodePersistedProxyServer(),
                )
            }
        }
        val serverLibrary = DesktopServerLibrary(servers = stored)
        val subscriptions = DesktopSubscriptionLibrary(
            subscriptions = (1..subscriptionCount).map { sub ->
                DesktopStoredSubscription(id = sub, url = "https://sub$sub.example/list", name = "Sub $sub")
            },
        )
        val catalog = DesktopProxyGroups.create(
            serverLibrary = serverLibrary,
            subscriptionLibrary = subscriptions,
            options = DesktopProxyGroupOptions(enableAllProxyGroup = true),
        )
        val decoded = serverLibrary.servers.map { it to it.decode().getOrNull() }
        val nodes = decoded.mapNotNull { (s, server) ->
            server?.let { ProxyServerPresentationNode(id = s.id, groupId = s.subscriptionId, server = it) }
        }
        return Fixture(catalog, decoded, subscriptions, nodes)
    }

    private fun project(f: Fixture, latency: Map<Int, DesktopServerLatencyResult> = emptyMap()) =
        projectDesktopProxyHomeProjection(
            groupCatalog = f.groupCatalog,
            decodedServers = f.decoded,
            subscriptionLibrary = f.subscriptions,
            selectedServerId = 10_001,
            latencyByServerId = latency,
            testingServerIds = emptySet(),
            presentationFormatter = ProxyServerPresentationFormatter(
                groupNames = emptyMap(),
                labels = ProxyServerPresentationLabels(
                    unknownGroupName = "Unknown",
                    allGroupsName = "All groups",
                    selectName = "Select",
                    leastPingName = "Least ping",
                    leastLoadName = "Least load",
                    randomName = "Random",
                    roundRobinName = "Round robin",
                    strategyGroupSummaryTemplate = "{count}",
                    strategyGroupSummaryWithFilterTemplate = "{filter} {count}",
                    chainProxySummaryTemplate = "{count}",
                ),
            ),
            presentationNodes = f.nodes,
            refreshingSubscriptionIds = emptySet(),
            pingingSubscriptionIds = emptySet(),
        )

    @Test
    fun everyServerKeepsItsSubscriptionGroupAndAddress() {
        val projection = project(fixture())

        assertEquals(subscriptionCount * serversPerSubscription, projection.servers.size)
        val sample = projection.servers.first { it.id == (7 * 10_000 + 123).toString() }
        assertEquals("subscription:7", sample.groupId)
        assertEquals("node-70123.example", sample.address.substringBefore(':'))
    }

    @Test
    fun projectingManySubscriptionsStaysFast() {
        val f = fixture()
        project(f) // warm up
        val elapsed = measureTime { repeat(3) { project(f) } } / 3

        println("PROJECTION_MS> ${elapsed.inWholeMilliseconds}")
        assertTrue(elapsed.inWholeMilliseconds < 500, "projection took $elapsed for ${f.decoded.size} servers")
    }
}
