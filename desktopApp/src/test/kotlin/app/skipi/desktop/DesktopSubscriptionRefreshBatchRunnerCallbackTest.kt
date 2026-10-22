// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0
//
// Expected constructor addition:
//   onCommittedLibrariesChanged: () -> Unit = {},
// The callback runs once from commit's existing finally block iff either library-change flag is true.

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshLoadRequest
import app.skipi.app.subscription.SubscriptionRefreshLoader
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import features.config.ConfigProfile
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import features.subscription.StoredSubscription
import features.subscription.StoredSubscriptionMetadata
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshBatchRunnerCallbackTest {
    @Test
    fun changedBatchCommitNotifiesOnceAfterTheRunnerCapturesItsFlags() = runBlocking {
        withFixture { fixture ->
            var callbackCount = 0
            lateinit var runner: DesktopSubscriptionRefreshBatchRunner<Int>
            var flagsSeenByCallback: Pair<Boolean, Boolean>? = null
            runner = fixture.runner(
                loader = SubscriptionRefreshLoader { request ->
                    loaded(
                        subscription = request.subscription,
                        servers = listOf(
                            vless(
                                name = "Refreshed ${request.subscription.id}",
                                host = "refreshed-${request.subscription.id}.example.test",
                            ),
                        ),
                    )
                },
                onCommittedLibrariesChanged = {
                    callbackCount += 1
                    flagsSeenByCallback = runner.serverLibraryChanged to runner.profileLibraryChanged
                },
            )

            val result = runner.run()

            assertEquals(listOf(4, 5), result.commitResult?.appliedSubscriptionIds)
            assertEquals(1, callbackCount)
            assertEquals(true to false, flagsSeenByCallback)
            assertTrue(runner.serverLibraryChanged)
            assertFalse(runner.profileLibraryChanged)
            assertEquals(1, fixture.successfulServerWriteCount)
            assertEquals(1, fixture.successfulSubscriptionWriteCount)
        }
    }

    @Test
    fun persistedActiveProfileStillNotifiesWhenGeneratedGroupSaveFails() = runBlocking {
        val initialConfigs = DesktopConfigLibrary(
            selectedConfigId = 6,
            configs = listOf(
                ConfigProfile(5, "Base one", "[Rule]\nFINAL,DIRECT\n"),
                ConfigProfile(6, "Base two", "[Rule]\nFINAL,DIRECT\n"),
            ),
            nextConfigId = 100,
        )
        val initialServers = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(stored(10, vless("Node A", "a.example.test"))),
            nextServerId = 50,
        )

        withFixture(configs = initialConfigs, servers = initialServers) { fixture ->
            fixture.failNewGeneratedGroupSave = true
            var callbackCount = 0
            lateinit var runner: DesktopSubscriptionRefreshBatchRunner<Int>
            var flagsSeenByCallback: Pair<Boolean, Boolean>? = null
            runner = fixture.runner(
                loader = SubscriptionRefreshLoader { request ->
                    val sourceId = request.subscription.id
                    loaded(
                        subscription = request.subscription,
                        // Keep the server library unchanged so the profile flag is the only
                        // reason the commit-finally callback should run.
                        servers = emptyList(),
                        embedded = if (sourceId == 4) {
                            embeddedProfile(
                                sourceUrl = "subscription://4",
                                fallbackName = "Provider Four",
                                activate = true,
                                profileName = "Provider Four",
                                groupName = "Stable group",
                                members = listOf("Node A"),
                            )
                        } else {
                            null
                        },
                    )
                },
                onCommittedLibrariesChanged = {
                    callbackCount += 1
                    flagsSeenByCallback = runner.serverLibraryChanged to runner.profileLibraryChanged
                },
            )

            val failure = assertFailsWith<IllegalStateException> { runner.run() }

            assertEquals("injected generated-group save failure", failure.message)
            assertEquals(1, callbackCount)
            assertEquals(false to true, flagsSeenByCallback)
            assertFalse(runner.serverLibraryChanged)
            assertTrue(runner.profileLibraryChanged)
            val savedConfigs = fixture.readConfigs()
            assertEquals(100, savedConfigs.selectedConfigId)
            assertEquals(listOf(5, 6, 100), savedConfigs.configs.map(ConfigProfile::id))
            assertEquals("subscription://4", savedConfigs.configs.last().sourceUrl)
            assertEquals(savedConfigs, fixture.publishedConfigs)
            assertTrue(fixture.readServers().servers.none { it.isGeneratedProfileGroup() })
            assertEquals(0, fixture.successfulServerWriteCount)
            assertEquals(1, fixture.generatedGroupSaveAttempts)
            assertEquals(1, fixture.profilePublishCount)
        }
    }

    @Test
    fun allFetchFailuresDoNotInvokeTheCommitCallback() = runBlocking {
        withFixture { fixture ->
            var callbackCount = 0
            val runner = fixture.runner(
                loader = SubscriptionRefreshLoader { request ->
                    throw IllegalStateException("fetch failed for ${request.subscription.id}")
                },
                onCommittedLibrariesChanged = { callbackCount += 1 },
            )

            val result = runner.run()

            assertEquals(2, result.refresh.failures.size)
            assertNull(result.commitResult)
            assertEquals(0, callbackCount)
            assertEquals(0, fixture.serverWriteAttempts)
            assertEquals(0, fixture.subscriptionWriteAttempts)
            assertEquals(0, fixture.profileWriteAttempts)
        }
    }

    @Test
    fun unchangedServerAndProfileLibrariesDoNotInvokeTheCommitCallback() = runBlocking {
        val sameRefreshTime = 17_000L
        val initialSubscriptions = listOf(provider(4, sameRefreshTime), provider(5, sameRefreshTime))
        val initialServers = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(
                stored(10, vless("Stable Four", "four.example.test"), subscriptionId = 4),
                stored(11, vless("Stable Five", "five.example.test"), subscriptionId = 5),
            ),
            nextServerId = 12,
        )

        withFixture(subscriptions = initialSubscriptions, servers = initialServers) { fixture ->
            var callbackCount = 0
            val runner = fixture.runner(
                loader = SubscriptionRefreshLoader { request ->
                    val sameServers = fixture.readServers().servers
                        .filter { it.subscriptionId == request.subscription.id }
                        .map { it.decode().getOrThrow() }
                    loaded(
                        subscription = request.subscription,
                        servers = sameServers,
                        metadata = request.subscription.metadata ?: SubscriptionMetadata(),
                    )
                },
                onCommittedLibrariesChanged = { callbackCount += 1 },
                refreshedAtMillis = sameRefreshTime,
            )

            val result = runner.run()

            assertEquals(listOf(4, 5), result.commitResult?.appliedSubscriptionIds)
            assertEquals(0, callbackCount)
            assertFalse(runner.serverLibraryChanged)
            assertFalse(runner.profileLibraryChanged)
            assertEquals(0, fixture.successfulServerWriteCount)
            assertEquals(0, fixture.subscriptionWriteAttempts)
            assertEquals(0, fixture.profileWriteAttempts)
        }
    }

    @Test
    fun cancellationAfterTheServerFileWriteStillNotifiesWithCapturedFlags() = runBlocking {
        withFixture { fixture ->
            var callbackCount = 0
            lateinit var runner: DesktopSubscriptionRefreshBatchRunner<Int>
            var flagsSeenByCallback: Pair<Boolean, Boolean>? = null
            runner = fixture.runner(
                loader = SubscriptionRefreshLoader { request ->
                    loaded(
                        subscription = request.subscription,
                        servers = listOf(
                            vless(
                                name = "Refreshed ${request.subscription.id}",
                                host = "refreshed-${request.subscription.id}.example.test",
                            ),
                        ),
                    )
                },
                onCommittedLibrariesChanged = {
                    callbackCount += 1
                    flagsSeenByCallback = runner.serverLibraryChanged to runner.profileLibraryChanged
                },
            )

            var commitJob: Job? = null
            fixture.afterSuccessfulServerSave = { savedLibrary ->
                // Prove cancellation happens after the actual Desktop JSON file is durable.
                assertEquals(savedLibrary, fixture.readServers())
                checkNotNull(commitJob).cancel(
                    CancellationException("cancelled after successful server-library save"),
                )
            }
            val operation = async(start = CoroutineStart.LAZY) {
                commitJob = coroutineContext[Job]
                runner.run()
            }

            operation.start()
            assertFailsWith<CancellationException> { operation.await() }

            assertEquals(1, callbackCount)
            assertEquals(true to false, flagsSeenByCallback)
            assertTrue(runner.serverLibraryChanged)
            assertFalse(runner.profileLibraryChanged)
            assertEquals(1, fixture.afterSuccessfulServerSaveCount)
            assertEquals(1, fixture.successfulServerWriteCount)
        }
    }

    private class Fixture(
        subscriptions: List<StoredSubscription>,
        initialConfigs: DesktopConfigLibrary,
        initialServers: DesktopServerLibrary,
        private val refreshedAtMillis: Long,
    ) : AutoCloseable {
        private val root = Files.createTempDirectory("desktop-batch-runner-callback-")
        private val subscriptionsPath = root.resolve("subscriptions.json")
        private val configsPath = root.resolve("configs.json")
        private val serversPath = root.resolve("servers.json")

        var publishedConfigs = initialConfigs
            private set
        var publishedServers = initialServers
            private set
        var failNewGeneratedGroupSave = false
        var serverWriteAttempts = 0
            private set
        var successfulServerWriteCount = 0
            private set
        var generatedGroupSaveAttempts = 0
            private set
        var subscriptionWriteAttempts = 0
            private set
        var successfulSubscriptionWriteCount = 0
            private set
        var profileWriteAttempts = 0
            private set
        var profilePublishCount = 0
            private set
        var afterSuccessfulServerSave: ((DesktopServerLibrary) -> Unit)? = null
        var afterSuccessfulServerSaveCount = 0
            private set

        init {
            DesktopSubscriptionLibraries.save(
                subscriptionsPath,
                DesktopSubscriptionLibrary(subscriptions = subscriptions),
            ).getOrThrow()
            DesktopConfigLibraries.save(configsPath, initialConfigs).getOrThrow()
            DesktopServerLibraries.save(serversPath, initialServers).getOrThrow()
        }

        private var subscriptionsPublished: DesktopSubscriptionLibrary = readSubscriptions()

        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = readSubscriptions(),
            readLibrary = ::readSubscriptions,
            saveLibrary = ::saveSubscriptions,
            publishLibrary = { updated -> subscriptionsPublished = updated },
        )

        private val proxyServerRepository = DesktopProxyServerRepository(
            initialLibrary = readServers(),
            readLibrary = ::readServers,
            saveLibrary = ::saveServers,
            publishLibrary = { updated -> publishedServers = updated },
        )

        fun runner(
            loader: SubscriptionRefreshLoader<SubscriptionRefreshLoadRequest<Int>, LoadedSubscriptionRefresh>,
            onCommittedLibrariesChanged: () -> Unit,
            refreshedAtMillis: Long = this.refreshedAtMillis,
        ): DesktopSubscriptionRefreshBatchRunner<Int> = DesktopSubscriptionRefreshBatchRunner(
            captureRequests = {
                subscriptionRepository.catalog.value.subscriptions.map { subscription ->
                    SubscriptionRefreshLoadRequest(subscription = subscription, fetchOptions = subscription.id)
                }
            },
            loader = loader,
            refreshedAtMillis = { refreshedAtMillis },
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = ::readConfigs,
            saveConfigs = ::saveConfigs,
            publishConfigs = ::publishConfigs,
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
            onCommittedLibrariesChanged = onCommittedLibrariesChanged,
        )

        fun readSubscriptions(): DesktopSubscriptionLibrary =
            DesktopSubscriptionLibraries.load(subscriptionsPath).getOrThrow()

        fun readConfigs(): DesktopConfigLibrary = DesktopConfigLibraries.load(configsPath).getOrThrow()

        fun readServers(): DesktopServerLibrary = DesktopServerLibraries.load(serversPath).getOrThrow()

        private fun saveSubscriptions(updated: DesktopSubscriptionLibrary): Result<Unit> {
            subscriptionWriteAttempts += 1
            return DesktopSubscriptionLibraries.save(subscriptionsPath, updated).also { result ->
                if (result.isSuccess) successfulSubscriptionWriteCount += 1
            }
        }

        private fun saveConfigs(updated: DesktopConfigLibrary): Result<Unit> {
            profileWriteAttempts += 1
            return DesktopConfigLibraries.save(configsPath, updated)
        }

        private fun publishConfigs(updated: DesktopConfigLibrary) {
            profilePublishCount += 1
            publishedConfigs = updated
        }

        private fun saveServers(updated: DesktopServerLibrary): Result<Unit> {
            serverWriteAttempts += 1
            val currentGeneratedIds = readServers().generatedGroupIds()
            if ((updated.generatedGroupIds() - currentGeneratedIds).isNotEmpty()) {
                generatedGroupSaveAttempts += 1
                if (failNewGeneratedGroupSave) {
                    return Result.failure(IllegalStateException("injected generated-group save failure"))
                }
            }

            return DesktopServerLibraries.save(serversPath, updated).also { result ->
                if (result.isSuccess) {
                    successfulServerWriteCount += 1
                    val hook = afterSuccessfulServerSave
                    afterSuccessfulServerSave = null
                    if (hook != null) {
                        afterSuccessfulServerSaveCount += 1
                        hook(updated)
                    }
                }
            }
        }

        override fun close() {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private suspend fun withFixture(
        subscriptions: List<StoredSubscription> = listOf(provider(4), provider(5)),
        configs: DesktopConfigLibrary = DesktopConfigLibrary(nextConfigId = 100),
        servers: DesktopServerLibrary = DesktopServerLibrary(nextServerId = 10),
        refreshedAtMillis: Long = 17_000L,
        block: suspend (Fixture) -> Unit,
    ) {
        val fixture = Fixture(subscriptions, configs, servers, refreshedAtMillis)
        try {
            block(fixture)
        } finally {
            fixture.close()
        }
    }

    private fun loaded(
        subscription: SubscriptionRecord,
        servers: List<ProxyServer<*>>,
        embedded: ResolvedEmbeddedSubscriptionConfig? = null,
        metadata: SubscriptionMetadata = SubscriptionMetadata(profileTitle = "Refreshed ${subscription.id}"),
    ) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = servers.size,
        servers = servers,
        metadata = metadata,
        resolvedEmbeddedConfig = embedded,
    )

    private fun embeddedProfile(
        sourceUrl: String,
        fallbackName: String,
        activate: Boolean,
        profileName: String,
        groupName: String,
        members: List<String>,
    ) = ResolvedEmbeddedSubscriptionConfig(
        sourceUrl = sourceUrl,
        fallbackName = fallbackName,
        activate = activate,
        content = """# name: $profileName
            [Proxy Group]
            $groupName = select, ${members.joinToString(", ")}, skipi-display=always

            [Rule]
            FINAL,DIRECT
        """.trimIndent(),
    )

    private fun provider(id: Int, lastUpdatedAtMillis: Long = 0L) = StoredSubscription(
        id = id,
        url = "https://subscriptions.example.test/$id",
        name = "Provider $id",
        metadata = StoredSubscriptionMetadata(lastUpdatedAtMillis = lastUpdatedAtMillis),
    )

    private fun vless(name: String, host: String) = VLESS(
        remarks = name,
        id = "${name.lowercase().replace(' ', '-')}-id",
        server = host,
        port = "443",
    )

    private fun stored(
        id: Int,
        server: ProxyServer<*>,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
}

private fun DesktopServerLibrary.generatedGroupIds(): Set<Int> = servers.mapNotNull { stored ->
    val strategy = stored.decode().getOrNull() as? StrategyGroup ?: return@mapNotNull null
    stored.id.takeIf { strategy.sourceTrafficConfigId != null }
}.toSet()

private fun DesktopStoredProxyServer.isGeneratedProfileGroup(): Boolean =
    (decode().getOrNull() as? StrategyGroup)?.sourceTrafficConfigId != null
