// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProxyHomeLatencyEffectWiringTest {
    @Test
    fun homeServerAndGroupLatencyEffectsUseTheSharedCoordinatorCallbacks() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@server.example:443?security=tls#Server",
        )
        val stored = DesktopStoredProxyServer(
            id = 31,
            serverJson = server.encodePersistedProxyServer(),
            subscriptionId = 7,
        )
        val subscription = DesktopStoredSubscription(id = 7, url = "https://example.com/sub")
        val groups = DesktopProxyGroups.create(
            serverLibrary = DesktopServerLibrary(servers = listOf(stored)),
            subscriptionLibrary = DesktopSubscriptionLibrary(listOf(subscription)),
        )
        val visibleIds = mutableListOf<List<String>>()
        val groupIds = mutableListOf<String>()
        var directMeasurementCalls = 0
        val context = DesktopProxyHomeEffectContext(
            decodedServers = listOf(stored to server),
            groupCatalog = groups,
            subscriptions = listOf(subscription),
            onMeasureServers = { _ -> directMeasurementCalls++ },
            onTestVisibleServers = { ids -> visibleIds += ids; Result.success(Unit) },
            onTestGroup = { id -> groupIds += id; Result.success(Unit) },
        )

        assertTrue(context.handle(ProxyHomeEffect.TestServer("31")).isSuccess)
        assertTrue(context.handle(ProxyHomeEffect.TestVisibleServers(listOf("31"))).isSuccess)
        assertTrue(context.handle(ProxyHomeEffect.TestGroup(DesktopProxyGroupIds.subscription(7))).isSuccess)

        assertEquals(listOf(listOf("31"), listOf("31")), visibleIds)
        assertEquals(listOf(DesktopProxyGroupIds.subscription(7)), groupIds)
        assertEquals(0, directMeasurementCalls)
    }

    @Test
    fun coordinatorRejectionIsReturnedWithoutStartingTheOldDirectMeasurementPath() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@server.example:443?security=tls#Server",
        )
        val stored = DesktopStoredProxyServer(id = 31, serverJson = server.encodePersistedProxyServer())
        var directMeasurementCalls = 0
        val rejection = IllegalStateException("latency busy")
        val context = DesktopProxyHomeEffectContext(
            decodedServers = listOf(stored to server),
            onMeasureServers = { _ -> directMeasurementCalls++ },
            onTestVisibleServers = { Result.failure(rejection) },
        )

        val result = context.handle(ProxyHomeEffect.TestServer("31"))

        assertTrue(result.isFailure)
        assertEquals(rejection, result.exceptionOrNull())
        assertEquals(0, directMeasurementCalls)
    }
}
