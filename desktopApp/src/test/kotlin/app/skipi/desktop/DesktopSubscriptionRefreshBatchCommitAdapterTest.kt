// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshLoader
import app.skipi.app.subscription.commitSubscriptionRefreshBatch
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import features.config.ConfigProfile
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import features.subscription.StoredSubscription
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshBatchCommitAdapterTest {
    @Test
    fun retainedSharedOperationCommitsTwoLoadsAsOneDesktopBatchAcrossTwoRuns() = runBlocking {
        val fixture = Fixture()
        var captureGeneration = 0
        var loadedCount = 0
        val operation = DesktopSubscriptionRefreshBatchRunner(
            captureRequests = {
                captureGeneration += 1
                fixture.subscriptionRepository.catalog.value.subscriptions.map { subscription ->
                    SubscriptionRefreshLoadRequest(
                        subscription = subscription,
                        fetchOptions = captureGeneration,
                    )
                }
            },
            loader = SubscriptionRefreshLoader { request ->
                loadedCount += 1
                loaded(request.subscription, request.fetchOptions)
            },
            refreshedAtMillis = { 2_000L + captureGeneration },
            subscriptionRepository = fixture.subscriptionRepository,
            proxyServerRepository = fixture.proxyServerRepository,
            readConfigs = { fixture.configLibrary },
            saveConfigs = { updated ->
                fixture.configWriteCount += 1
                fixture.configSaveFailure?.let { Result.failure(it) }
                    ?: run { fixture.configLibrary = updated; Result.success(Unit) }
            },
            publishConfigs = { fixture.configLibrary = it },
            readLatencyByServerId = { fixture.latencyByServerId },
            publishLatencyByServerId = { fixture.latencyByServerId = it },
        )

        val first = operation.run()
        val second = operation.run()

        assertEquals(listOf(4, 5), first.commitResult?.appliedSubscriptionIds)
        assertEquals(listOf(4, 5), second.commitResult?.appliedSubscriptionIds)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
        assertEquals(2, captureGeneration)
        assertEquals(4, loadedCount)
        assertEquals(2, fixture.serverWriteCount)
        assertEquals(2, fixture.subscriptionWriteCount)
        assertEquals(2, fixture.configWriteCount)
        // The Android-original same-protocol characterization keeps IDs 11/12, selection 11,
        // and the high-water mark 100 for this exact VLESS/VLESS fixture.
        assertEquals(
            listOf(
                Triple(11, 4, "Refresh 2 group 4"),
                Triple(12, 5, "Refresh 2 group 5"),
                Triple(33, null, "Manual"),
            ),
            fixture.serverLibrary.servers.map { stored ->
                Triple(stored.id, stored.subscriptionId, stored.decode().getOrThrow().getInfo().remarks)
            },
        )
        assertEquals(11, fixture.serverLibrary.selectedServerId)
        assertEquals(100, fixture.serverLibrary.effectiveNextServerId)
        assertEquals(
            listOf("Refresh 2 group 4", "Refresh 2 group 5"),
            fixture.subscriptionRepository.catalog.value.subscriptions.map(SubscriptionRecord::title),
        )
        assertEquals(
            listOf("https://profiles.example.test/2/4", "https://profiles.example.test/2/5"),
            fixture.configLibrary.configs.takeLast(2).map(ConfigProfile::sourceUrl),
        )
        assertEquals(listOf(100, 101), fixture.configLibrary.configs.map(ConfigProfile::id))
        assertEquals(102, fixture.configLibrary.nextConfigId)
    }

    @Test
    fun profileSaveFailurePropagatesAfterTheAggregateServerAndMetadataWrites() = runBlocking {
        val fixture = Fixture().apply {
            configSaveFailure = IllegalStateException("injected profile save failure")
        }
        val first = fixture.subscriptionRepository.catalog.value.subscriptions.first()
        val second = fixture.subscriptionRepository.catalog.value.subscriptions.last()
        val loadedUpdates = listOf(loaded(first, 1), loaded(second, 1))
        val failure = try {
            commitSubscriptionRefreshBatch(
                loaded = loadedUpdates,
                refreshedAtMillis = 2_100L,
                singlePort = fixture.singlePort(first.id),
                batchPort = fixture.batchPort(loadedUpdates),
            )
            error("Expected the aggregate profile save to fail")
        } catch (error: IllegalStateException) {
            error
        }

        assertEquals("injected profile save failure", failure.message)
        assertEquals(1, fixture.serverWriteCount)
        assertEquals(1, fixture.subscriptionWriteCount)
        assertEquals(1, fixture.configWriteCount)
        assertEquals(
            listOf("Refresh 1 group 4", "Refresh 1 group 5"),
            fixture.serverLibrary.servers
                .filter { it.subscriptionId != null }
                .map { stored -> stored.decode().getOrThrow().getInfo().remarks },
        )
        assertEquals(
            listOf("Refresh 1 group 4", "Refresh 1 group 5"),
            fixture.subscriptionRepository.catalog.value.subscriptions.map(SubscriptionRecord::title),
        )
        assertTrue(fixture.configLibrary.configs.isEmpty())
    }

    private fun loaded(subscription: SubscriptionRecord, generation: Int) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = 1,
        servers = listOf(proxy("fresh-$generation-${subscription.id}.example.test", "Refresh $generation group ${subscription.id}")),
        metadata = SubscriptionMetadata(profileTitle = "Refresh $generation group ${subscription.id}"),
        resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
            content = "[Rule]\nFINAL,DIRECT\n",
            sourceUrl = "https://profiles.example.test/$generation/${subscription.id}",
            fallbackName = "Profile ${subscription.id}",
            activate = false,
        ),
    )

    private fun proxy(host: String, name: String): ProxyServer<*> = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@$host:443?security=tls#$name",
    )

    private inner class Fixture {
        private val storedSubscriptions = listOf(
            StoredSubscription(id = 4, url = "https://subscriptions.example.test/4", name = "Stored first"),
            StoredSubscription(id = 5, url = "https://subscriptions.example.test/5", name = "Stored second"),
        )
        var subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = storedSubscriptions)
        var serverLibrary = DesktopServerLibrary(
            selectedServerId = 11,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 11,
                    serverJson = proxy("old-4.example.test", "Old first").encodePersistedProxyServer(),
                    subscriptionId = 4,
                ),
                DesktopStoredProxyServer(
                    id = 12,
                    serverJson = proxy("old-5.example.test", "Old second").encodePersistedProxyServer(),
                    subscriptionId = 5,
                ),
                DesktopStoredProxyServer(
                    id = 33,
                    serverJson = proxy("manual.example.test", "Manual").encodePersistedProxyServer(),
                ),
            ),
            nextServerId = 100,
        )
        var configLibrary = DesktopConfigLibrary(nextConfigId = 100)
        var serverSaveFailure: Throwable? = null
        var subscriptionSaveFailure: Throwable? = null
        var configSaveFailure: Throwable? = null
        var serverWriteCount = 0
        var subscriptionWriteCount = 0
        var configWriteCount = 0
        var latencyByServerId: Map<Int, DesktopServerLatencyResult> = emptyMap()

        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptionLibrary,
            readLibrary = { subscriptionLibrary },
            saveLibrary = { updated ->
                subscriptionWriteCount += 1
                subscriptionSaveFailure?.let { Result.failure(it) }
                    ?: run { subscriptionLibrary = updated; Result.success(Unit) }
            },
            publishLibrary = { subscriptionLibrary = it },
        )
        val proxyServerRepository = DesktopProxyServerRepository(
            initialLibrary = serverLibrary,
            readLibrary = { serverLibrary },
            saveLibrary = { updated ->
                serverWriteCount += 1
                serverSaveFailure?.let { Result.failure(it) }
                    ?: run { serverLibrary = updated; Result.success(Unit) }
            },
            publishLibrary = { serverLibrary = it },
        )

        fun batchPort(loaded: List<LoadedSubscriptionRefresh>) = DesktopSubscriptionRefreshBatchCommitAdapter(
            loaded = loaded,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = { configLibrary },
            saveConfigs = { updated ->
                configWriteCount += 1
                configSaveFailure?.let { Result.failure(it) }
                    ?: run { configLibrary = updated; Result.success(Unit) }
            },
            publishConfigs = { configLibrary = it },
            readLatencyByServerId = { latencyByServerId },
            publishLatencyByServerId = { latencyByServerId = it },
        )

        fun singlePort(subscriptionId: Int) = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = subscriptionId,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = { configLibrary },
            saveConfigs = { updated ->
                configWriteCount += 1
                configSaveFailure?.let { Result.failure(it) }
                    ?: run { configLibrary = updated; Result.success(Unit) }
            },
            publishConfigs = { configLibrary = it },
            readLatencyByServerId = { latencyByServerId },
            publishLatencyByServerId = { latencyByServerId = it },
        )
    }
}
