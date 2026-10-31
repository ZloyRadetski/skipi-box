// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshLoader
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import features.config.ConfigProfile
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import features.subscription.StoredSubscription
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshActiveProfileRuntimeTest {
    @Test
    fun batchActivatingEmbeddedProfileDoesNotReconnectWhenSelectedProxyServerStaysTheSame() = runBlocking {
        val selectedProxyId = 10
        val initialSubscriptions = DesktopSubscriptionLibrary(
            subscriptions = listOf(provider(4), provider(5)),
        )
        var storedSubscriptions = initialSubscriptions
        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = initialSubscriptions,
            readLibrary = { storedSubscriptions },
            saveLibrary = { updated ->
                storedSubscriptions = updated
                Result.success(Unit)
            },
            publishLibrary = { updated -> storedSubscriptions = updated },
        )

        val initialServers = DesktopServerLibrary(
            selectedServerId = selectedProxyId,
            servers = listOf(
                storedServer(
                    id = selectedProxyId,
                    subscriptionId = null,
                    server = vless("Selected manual", "manual.example.test", "manual-id"),
                ),
                storedServer(
                    id = 11,
                    subscriptionId = 4,
                    server = vless("Old subscription server", "old.example.test", "subscription-id"),
                ),
            ),
            nextServerId = 12,
        )
        var storedServers = initialServers
        val proxyServerRepository = DesktopProxyServerRepository(
            initialLibrary = initialServers,
            readLibrary = { storedServers },
            saveLibrary = { updated ->
                storedServers = updated
                Result.success(Unit)
            },
            publishLibrary = { updated -> storedServers = updated },
        )

        val originalActiveProfile = ConfigProfile(
            id = 6,
            name = "Manual active profile",
            content = "[Rule]\nFINAL,DIRECT",
        )
        var storedConfigs = DesktopConfigLibrary(
            selectedConfigId = originalActiveProfile.id,
            configs = listOf(originalActiveProfile),
            nextConfigId = 100,
        )
        var publishedConfigs = storedConfigs
        val coreIsRunning = true
        val reconnectRequests = mutableListOf<String>()
        lateinit var runner: DesktopSubscriptionRefreshBatchRunner<Int>
        runner = DesktopSubscriptionRefreshBatchRunner(
            captureRequests = {
                subscriptionRepository.catalog.value.subscriptions.map { subscription ->
                    SubscriptionRefreshLoadRequest(subscription = subscription, fetchOptions = subscription.id)
                }
            },
            loader = SubscriptionRefreshLoader { request ->
                LoadedSubscriptionRefresh(
                    sourceIdentity = subscriptionRefreshRequestIdentity(request.subscription),
                    urlCount = if (request.subscription.id == 4) 1 else 0,
                    servers = if (request.subscription.id == 4) {
                        listOf(vless("Refreshed subscription server", "new.example.test", "subscription-id"))
                    } else {
                        emptyList()
                    },
                    metadata = SubscriptionMetadata(),
                    resolvedEmbeddedConfig = if (request.subscription.id == 4) {
                        ResolvedEmbeddedSubscriptionConfig(
                            content = "[Rule]\nFINAL,PROXY",
                            sourceUrl = "subscription://4",
                            fallbackName = "Imported active profile",
                            activate = true,
                        )
                    } else {
                        null
                    },
                )
            },
            refreshedAtMillis = { 1_400L },
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = { storedConfigs },
            saveConfigs = { updated ->
                storedConfigs = updated
                Result.success(Unit)
            },
            publishConfigs = { updated -> publishedConfigs = updated },
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
            onCommittedLibrariesChanged = {
                val selectionChanged = shouldReconnectAfterSubscriptionRefresh(
                    coreIsRunning = coreIsRunning,
                    previousSelectedServerId = runner.previousSelectedServerId,
                    currentSelectedServerId = runner.currentSelectedServerId,
                )
                // Mirrors Main's current callback wiring. Android Home's source observer only
                // restarts after selectedProxyServerId changes, even when an embedded profile activates.
                if (selectionChanged || (coreIsRunning && runner.profileLibraryChanged)) {
                    reconnectRequests += "Подписки обновлены."
                }
            },
        )

        val result = runner.run()

        assertEquals(listOf(4, 5), result.commitResult?.appliedSubscriptionIds)
        assertEquals(selectedProxyId, storedServers.selectedServerId)
        assertTrue(runner.serverLibraryChanged)
        assertEquals(selectedProxyId, runner.previousSelectedServerId)
        assertEquals(selectedProxyId, runner.currentSelectedServerId)
        assertTrue(runner.profileLibraryChanged)
        assertEquals(100, storedConfigs.selectedConfigId)
        assertTrue(storedConfigs.configs.any { it.content.contains("FINAL,PROXY") })
        assertEquals(storedConfigs, publishedConfigs)
        assertFalse(shouldReconnectAfterSubscriptionRefresh(true, selectedProxyId, selectedProxyId))
        assertEquals(emptyList(), reconnectRequests)
    }

    private fun provider(id: Int) = StoredSubscription(
        id = id,
        url = "https://subscription.example.test/$id",
        name = "Provider $id",
    )

    private fun vless(name: String, host: String, uuid: String) = VLESS(
        remarks = name,
        id = uuid,
        server = host,
        port = "443",
    )

    private fun storedServer(
        id: Int,
        subscriptionId: Int?,
        server: VLESS,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
}
