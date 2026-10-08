// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import features.subscription.StoredSubscription
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSubscriptionServerRemovalAdapterTest {
    @Test
    fun selectedOpaqueSubscriptionServerStopsBeforeDeletingLatestRows() = runBlocking {
        val subscriptionId = 5
        val target = StoredSubscription(id = subscriptionId, url = "https://example.com/list", name = "Target")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(target))
        val events = mutableListOf<String>()
        var visibleServers = DesktopServerLibrary(
            selectedServerId = 99,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 99,
                    serverJson = "future protocol bytes must be deleted exactly\n",
                    subscriptionId = subscriptionId,
                ),
                DesktopStoredProxyServer(
                    id = 7,
                    serverJson = proxyServer("manual.example").encodePersistedProxyServer(),
                ),
            ),
        )
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = visibleServers,
            readLibrary = { visibleServers },
            saveLibrary = { updated ->
                events += "server-save"
                Result.success(Unit)
            },
            publishLibrary = { updated ->
                visibleServers = updated
                events += "server-publish"
            },
        )
        val removalAdapter = DesktopSubscriptionServerRemovalAdapter(
            readLibrary = { visibleServers },
            proxyServerRepository = proxyRepository,
            stopSelectedServer = {
                assertTrue(visibleServers.servers.any { it.id == 99 })
                events += "stop-proxy"
                visibleServers = visibleServers.copy(
                    nextServerId = 100,
                    servers = visibleServers.servers + DesktopStoredProxyServer(
                        id = 42,
                        serverJson = proxyServer("concurrent.example").encodePersistedProxyServer(),
                    ),
                )
            },
        )
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated ->
                events += "subscription-save"
                subscriptions = updated
                Result.success(Unit)
            },
            publishLibrary = { updated ->
                subscriptions = updated
                events += "subscription-publish"
            },
            removeLinkedServers = removalAdapter::remove,
        )

        subscriptionRepository.remove(subscriptionId)

        assertEquals(
            listOf("stop-proxy", "server-save", "server-publish", "subscription-save", "subscription-publish"),
            events,
        )
        assertTrue(visibleServers.servers.any { it.id == 7 })
        assertTrue(visibleServers.servers.any { it.id == 42 })
        assertFalse(visibleServers.servers.any { it.id == 99 })
        assertTrue(subscriptions.subscriptions.isEmpty())
    }

    @Test
    fun failedServerSaveAfterStoppingSelectedServerKeepsRowsAndSubscription() = runBlocking {
        val subscriptionId = 6
        val target = StoredSubscription(id = subscriptionId, url = "https://example.com/list", name = "Target")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(target))
        val initialServers = DesktopServerLibrary(
            selectedServerId = 99,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 99,
                    serverJson = "opaque target bytes",
                    subscriptionId = subscriptionId,
                ),
                DesktopStoredProxyServer(
                    id = 7,
                    serverJson = proxyServer("manual.example").encodePersistedProxyServer(),
                ),
            ),
        )
        var visibleServers = initialServers
        val events = mutableListOf<String>()
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = visibleServers,
            readLibrary = { visibleServers },
            saveLibrary = {
                events += "server-save"
                Result.failure(IllegalStateException("disk full"))
            },
            publishLibrary = { updated ->
                visibleServers = updated
                events += "server-publish"
            },
        )
        val removalAdapter = DesktopSubscriptionServerRemovalAdapter(
            readLibrary = { visibleServers },
            proxyServerRepository = proxyRepository,
            stopSelectedServer = { events += "stop-proxy" },
        )
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated ->
                events += "subscription-save"
                subscriptions = updated
                Result.success(Unit)
            },
            publishLibrary = { updated ->
                subscriptions = updated
                events += "subscription-publish"
            },
            removeLinkedServers = removalAdapter::remove,
        )

        val failure = runCatching { subscriptionRepository.remove(subscriptionId) }.exceptionOrNull()

        assertEquals("disk full", failure?.message)
        assertEquals(listOf("stop-proxy", "server-save"), events)
        assertEquals(initialServers, visibleServers)
        assertEquals(listOf(target), subscriptions.subscriptions)
    }

    @Test
    fun failingToStopSelectedServerLeavesServersAndSubscriptionUntouched() = runBlocking {
        val subscriptionId = 8
        val target = StoredSubscription(id = subscriptionId, url = "https://example.com/list", name = "Target")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(target))
        val initialServers = DesktopServerLibrary(
            selectedServerId = 99,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 99,
                    serverJson = "opaque target bytes",
                    subscriptionId = subscriptionId,
                ),
                DesktopStoredProxyServer(
                    id = 7,
                    serverJson = proxyServer("manual.example").encodePersistedProxyServer(),
                ),
            ),
        )
        var visibleServers = initialServers
        val events = mutableListOf<String>()
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = initialServers,
            readLibrary = { visibleServers },
            saveLibrary = {
                events += "server-save"
                Result.success(Unit)
            },
            publishLibrary = { updated ->
                visibleServers = updated
                events += "server-publish"
            },
        )
        val removalAdapter = DesktopSubscriptionServerRemovalAdapter(
            readLibrary = { visibleServers },
            proxyServerRepository = proxyRepository,
            stopSelectedServer = {
                assertTrue(visibleServers.servers.any { it.id == 99 })
                events += "stop-proxy"
                error("core stop failed")
            },
        )
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated ->
                events += "subscription-save"
                subscriptions = updated
                Result.success(Unit)
            },
            publishLibrary = { updated ->
                subscriptions = updated
                events += "subscription-publish"
            },
            removeLinkedServers = removalAdapter::remove,
        )

        val failure = runCatching { subscriptionRepository.remove(subscriptionId) }.exceptionOrNull()

        assertEquals("core stop failed", failure?.message)
        assertEquals(listOf("stop-proxy"), events)
        assertEquals(initialServers, visibleServers)
        assertEquals(listOf(target), subscriptions.subscriptions)
    }

    @Test
    fun removingUnselectedSubscriptionServersDoesNotStopProxy() = runBlocking {
        val subscriptionId = 7
        val target = StoredSubscription(id = subscriptionId, url = "https://example.com/list", name = "Target")
        var subscriptions = DesktopSubscriptionLibrary(subscriptions = listOf(target))
        var visibleServers = DesktopServerLibrary(
            selectedServerId = 11,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 99,
                    serverJson = "opaque target bytes",
                    subscriptionId = subscriptionId,
                ),
                DesktopStoredProxyServer(
                    id = 11,
                    serverJson = proxyServer("manual.example").encodePersistedProxyServer(),
                ),
            ),
        )
        var stopCount = 0
        val proxyRepository = DesktopProxyServerRepository(
            initialLibrary = visibleServers,
            readLibrary = { visibleServers },
            saveLibrary = { Result.success(Unit) },
            publishLibrary = { updated -> visibleServers = updated },
        )
        val removalAdapter = DesktopSubscriptionServerRemovalAdapter(
            readLibrary = { visibleServers },
            proxyServerRepository = proxyRepository,
            stopSelectedServer = { stopCount++ },
        )
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptions,
            readLibrary = { subscriptions },
            saveLibrary = { updated -> subscriptions = updated; Result.success(Unit) },
            publishLibrary = { subscriptions = it },
            removeLinkedServers = removalAdapter::remove,
        )

        subscriptionRepository.remove(subscriptionId)

        assertEquals(0, stopCount)
        assertEquals(11, visibleServers.selectedServerId)
        assertFalse(visibleServers.servers.any { it.id == 99 })
        assertTrue(subscriptions.subscriptions.isEmpty())
    }

    private fun proxyServer(host: String): ProxyServer<*> = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@$host:443?security=tls#Server",
    )
}
