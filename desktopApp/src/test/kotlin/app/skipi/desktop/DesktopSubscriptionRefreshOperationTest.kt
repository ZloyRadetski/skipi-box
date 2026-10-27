// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyServerSelectionRuntimeAction
import app.skipi.app.home.decideProxyServerSelectionRuntimeAction
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import features.config.ConfigProfile
import features.config.analyzeShadowrocketConfig
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.usecase.ProxyServerPayloadParser
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionFetchResponse
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshOperationTest {
    @Test
    fun refreshingUnselectedSubscriptionKeepsBroadChangeFlagButAndroidParityPolicyDoesNotReconnect() = runBlocking {
        val fixture = SingleRefreshFixture()
        fixture.configLibrary = DesktopConfigLibrary()
        val selectedServerIdBefore = fixture.serverLibrary.selectedServerId
        val selectedServerBefore = fixture.serverLibrary.servers.single { it.id == fixture.manualServerId }
        val operation = fixture.operation(
            fetchResponse = { _, _, _ ->
                SubscriptionFetchResponse(
                    body = """
                        proxies:
                          - name: Refreshed inactive subscription server
                            type: socks
                            server: inactive-refresh.example.test
                            port: 1080
                    """.trimIndent(),
                )
            },
        )

        val result = operation.run(fixture.request())

        assertEquals(fixture.manualServerId, fixture.serverLibrary.selectedServerId)
        assertEquals(selectedServerBefore, fixture.serverLibrary.servers.single { it.id == fixture.manualServerId })
        assertTrue(result.serverLibraryChanged)
        assertFalse(result.profileLibraryChanged)
        // The library flag records the persisted catalog change. Runtime reconnect
        // follows Android's selection-transition policy, which ignores this inactive
        // subscription update while the selected server and active runtime target match.
        assertEquals(
            ProxyServerSelectionRuntimeAction.None,
            decideProxyServerSelectionRuntimeAction(
                previousSelectedServerId = selectedServerIdBefore,
                selectedServerId = fixture.serverLibrary.selectedServerId,
                requestedServerId = checkNotNull(fixture.serverLibrary.selectedServerId),
                serverExists = fixture.serverLibrary.servers.any { it.id == fixture.manualServerId },
                runtimeRunning = true,
                activeServerId = selectedServerIdBefore,
            ),
        )
    }

    @Test
    fun runLoadsWithTheProductionParserOffCallerAndCommitsPersistedServerMetadataAndActiveProfile() = runBlocking {
        val fixture = SingleRefreshFixture()
        val parserThread = AtomicReference<Thread?>()
        val callerThread = AtomicReference<Thread?>()
        val productionParser = DesktopMihomoPayloadImporter.subscriptionPayloadParser()
        val threadRecordingParser: ProxyServerPayloadParser = { payload, context ->
            parserThread.set(Thread.currentThread())
            productionParser(payload, context)
        }
        val seenOptions = mutableListOf<DesktopSubscriptionRefreshFetchOptions>()
        val operation = fixture.operation(
            fetchResponse = { url, _, options ->
                assertEquals(fixture.subscriptionUrl, url)
                seenOptions += options
                SubscriptionFetchResponse(
                    body = """
                        proxies:
                          - name: Refreshed provider server
                            type: socks
                            server: refreshed.example.test
                            port: 1080
                    """.trimIndent(),
                    headers = mapOf(
                        "profile-title" to "Updated provider",
                        "routing" to "[Rule]\nFINAL,PROXY",
                    ),
                )
            },
            parsers = listOf(threadRecordingParser),
        )
        val callerDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        val result = try {
            withContext(callerDispatcher) {
                callerThread.set(Thread.currentThread())
                operation.run(fixture.request())
            }
        } finally {
            callerDispatcher.close()
        }

        val expectedCaller = checkNotNull(callerThread.get())
        assertFalse(parserThread.get() === expectedCaller)
        assertTrue(fixture.commitThreads.isNotEmpty())
        assertTrue(fixture.commitThreads.all { thread -> thread === expectedCaller })
        assertEquals(listOf(fixture.fetchOptions), seenOptions)

        assertEquals(
            listOf(
                SubscriptionRefreshCommitScope.SERVERS,
                SubscriptionRefreshCommitScope.SUBSCRIPTION,
                SubscriptionRefreshCommitScope.PROFILE,
            ),
            result.refresh.commit.appliedScopes,
        )
        assertTrue(result.refresh.commit.applicable)
        assertEquals("Updated provider", result.refresh.loaded.metadata.profileTitle)
        assertTrue(result.refresh.loaded.resolvedEmbeddedConfig?.activate == true)
        assertEquals(1, result.refresh.loaded.servers.size)

        val refreshedServer = fixture.serverLibrary.servers.single { it.subscriptionId == fixture.subscriptionId }
        assertEquals(
            "Refreshed provider server",
            refreshedServer.decode().getOrThrow().getInfo().remarks,
        )
        assertEquals(900L, fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
        assertEquals(
            "Updated provider",
            fixture.subscriptionRepository.catalog.value.subscriptions.single().metadata?.profileTitle,
        )
        assertEquals("[Rule]\nFINAL,PROXY", result.refresh.loaded.resolvedEmbeddedConfig?.content)
        assertFalse(fixture.subscriptionRepository.catalog.value.subscriptions.single().enabled)
        assertEquals(setOf(fixture.manualServerId), fixture.latencyByServerId.keys)
        assertEquals(DesktopServerLatencyResult.Success(55), fixture.latencyByServerId[fixture.manualServerId])

        val activeProfileId = checkNotNull(fixture.configLibrary.selectedConfigId)
        val activeProfile = fixture.configLibrary.configs.single { profile -> profile.id == activeProfileId }
        // Shared import adds canonical [SKIPI] metadata to persisted text; assert the source
        // payload above and the parsed routing rules here instead of raw document equality.
        assertEquals(
            listOf("FINAL,PROXY"),
            activeProfile.content.analyzeShadowrocketConfig().rules.map { rule -> rule.raw.trim() },
        )
        assertEquals("Disabled provider", activeProfile.name)
        assertEquals("subscription://${fixture.subscriptionId}", activeProfile.sourceUrl)
        assertTrue(result.serverLibraryChanged)
        assertTrue(result.profileLibraryChanged)
    }

    @Test
    fun cancellationDuringBackgroundLoadPropagatesBeforeAnyPersistenceWrite() = runBlocking {
        val fixture = SingleRefreshFixture()
        val fetchStarted = CompletableDeferred<Unit>()
        val operation = fixture.operation(
            fetchResponse = { _, _, _ ->
                fetchStarted.complete(Unit)
                awaitCancellation()
            },
        )
        val job = async { operation.run(fixture.request()) }

        fetchStarted.await()
        job.cancel()
        assertFailsWith<CancellationException> { job.await() }

        assertEquals(0, fixture.writeCount)
        assertEquals(
            fixture.subscriptionId,
            fixture.serverLibrary.servers.single { it.id == fixture.oldServerId }.subscriptionId,
        )
        assertEquals(200L, fixture.subscriptionRepository.catalog.value.subscriptions.single().lastUpdatedAtMillis)
        assertEquals(20, fixture.configLibrary.selectedConfigId)
    }
}

private class SingleRefreshFixture {
    val subscriptionId = 4
    val subscriptionUrl = "https://subscription.example.test/list"
    val manualServerId = 9
    val oldServerId = 8
    val fetchOptions = DesktopSubscriptionRefreshFetchOptions(
        timeout = Duration.ofSeconds(17),
        proxy = null,
        deviceHeaders = mapOf("X-Device-OS" to "Desktop test"),
        ageSecretKey = "age secret",
        fallbackUserAgent = "Configured Desktop agent",
    )
    var subscriptionLibrary = DesktopSubscriptionLibrary(
        subscriptions = listOf(
            StoredSubscription(
                id = subscriptionId,
                url = subscriptionUrl,
                userAgent = "subscription-specific agent",
                name = "Disabled provider",
                metadata = StoredSubscriptionMetadata(lastUpdatedAtMillis = 200L),
                enabled = false,
            ),
        ),
    )
    var serverLibrary = DesktopServerLibrary(
        selectedServerId = manualServerId,
        servers = listOf(
            storedServer(oldServerId, subscriptionId, "old.example.test", "Old provider server"),
            storedServer(manualServerId, null, "manual.example.test", "Manual server"),
        ),
        nextServerId = 10,
    )
    var configLibrary = DesktopConfigLibrary(
        selectedConfigId = 20,
        configs = listOf(ConfigProfile(20, "Existing profile", "[Rule]\nFINAL,DIRECT\n")),
        nextConfigId = 100,
    )
    var latencyByServerId: Map<Int, DesktopServerLatencyResult> = mapOf(
        oldServerId to DesktopServerLatencyResult.Success(37),
        manualServerId to DesktopServerLatencyResult.Success(55),
    )
    val commitThreads = mutableListOf<Thread>()
    var writeCount = 0

    val subscriptionRepository = DesktopSubscriptionRepository(
        initialLibrary = subscriptionLibrary,
        readLibrary = { subscriptionLibrary },
        saveLibrary = { updated ->
            recordWrite()
            subscriptionLibrary = updated
            Result.success(Unit)
        },
        publishLibrary = { updated ->
            commitThreads += Thread.currentThread()
            subscriptionLibrary = updated
        },
    )
    val proxyServerRepository = DesktopProxyServerRepository(
        initialLibrary = serverLibrary,
        readLibrary = { serverLibrary },
        saveLibrary = { updated ->
            recordWrite()
            serverLibrary = updated
            Result.success(Unit)
        },
        publishLibrary = { updated ->
            commitThreads += Thread.currentThread()
            serverLibrary = updated
        },
    )

    fun request() = SubscriptionRefreshLoadRequest(
        subscription = subscriptionRepository.catalog.value.subscriptions.single(),
        fetchOptions = fetchOptions,
    )

    fun operation(
        fetchResponse: suspend (String, String, DesktopSubscriptionRefreshFetchOptions) -> SubscriptionFetchResponse,
        parsers: List<ProxyServerPayloadParser> = listOf(DesktopMihomoPayloadImporter.subscriptionPayloadParser()),
    ) = DesktopSubscriptionRefreshOperation(
        subscriptionRepository = subscriptionRepository,
        proxyServerRepository = proxyServerRepository,
        readConfigs = { configLibrary },
        saveConfigs = { updated ->
            recordWrite()
            configLibrary = updated
            Result.success(Unit)
        },
        publishConfigs = { updated ->
            commitThreads += Thread.currentThread()
            configLibrary = updated
        },
        readLatencyByServerId = { latencyByServerId },
        publishLatencyByServerId = { updated -> latencyByServerId = updated },
        fetchResponse = fetchResponse,
        parsers = parsers,
        refreshedAtMillis = { 900L },
    )

    private fun recordWrite() {
        writeCount += 1
        commitThreads += Thread.currentThread()
    }
}

private fun storedServer(id: Int, subscriptionId: Int?, host: String, name: String) =
    DesktopStoredProxyServer(
        id = id,
        serverJson = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d9eb9c6@$host:443?security=tls#$name",
        ).encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
