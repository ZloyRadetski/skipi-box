// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.ResolvedEmbeddedSubscriptionConfig
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.commitSubscriptionRefresh
import app.skipi.app.subscription.commitSubscriptionRefreshBatch
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import features.config.ConfigProfile
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import features.subscription.StoredSubscription
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSubscriptionProfileGroupReconciliationTest {
    @Test
    fun singleEmbeddedProfileRefreshReconcilesTheExistingGeneratedGroupAfterCommit() = runBlocking {
        val profile = ConfigProfile(
            id = 7,
            name = "Original profile",
            content = profileDocument("Original profile", "Stable", listOf("Node A")),
            sourceUrl = "subscription://4",
        )
        val sibling = ConfigProfile(
            id = 8,
            name = "Sibling profile",
            content = profileDocument("Sibling profile", "Sibling", listOf("Node B")),
            sourceUrl = "manual://sibling",
        )
        val initialConfigs = DesktopConfigLibrary(
            selectedConfigId = sibling.id,
            configs = listOf(profile, sibling),
            nextConfigId = 40,
        )
        val initialServers = DesktopServerLibrary(
            selectedServerId = 12,
            servers = listOf(
                stored(10, vless("Node A", "a.example")),
                stored(11, vless("Node B", "b.example")),
                stored(12, vless("Node C", "c.example")),
                stored(
                    30,
                    StrategyGroup(
                        remarks = "Stable",
                        proxyServerIds = listOf(10),
                        selectedMemberId = 10,
                        sourceTrafficConfigId = profile.id,
                        sourcePolicyGroupName = "Stable",
                    ),
                    subscriptionId = -2,
                ),
                stored(
                    25,
                    StrategyGroup(
                        remarks = "Sibling",
                        proxyServerIds = listOf(11),
                        selectedMemberId = 11,
                        sourceTrafficConfigId = sibling.id,
                        sourcePolicyGroupName = "Sibling",
                    ),
                    subscriptionId = -2,
                ),
            ),
            nextServerId = 31,
        )

        withFixture(
            subscriptions = listOf(provider(4)),
            configs = initialConfigs,
            servers = initialServers,
        ) { fixture ->
            val subscription = fixture.subscriptionRepository.catalog.value.subscriptions.single()
            val adapter = fixture.singlePort(subscription.id)
            val result = commitSubscriptionRefresh(
                loaded = loaded(
                    subscription = subscription,
                    embedded = embedded(
                        sourceUrl = "subscription://4",
                        fallbackName = "Provider 4",
                        activate = true,
                        content = profileDocument("Revised profile", "Stable", listOf("Node B", "Node C")),
                    ),
                ),
                refreshedAtMillis = 12_000L,
                port = adapter,
            )

            assertNull(result.failure)
            assertFalse(adapter.profileLibraryChanged)
            assertEquals(
                listOf(10, 11, 12, 30, 25),
                fixture.readServers().servers.map(DesktopStoredProxyServer::id),
            )
            val stable = fixture.readServers().servers.single { it.id == 30 }
                .decode().getOrThrow() as StrategyGroup
            assertEquals(listOf(11, 12), stable.proxyServerIds)
            assertEquals(11, stable.selectedMemberId)
            assertEquals(7, stable.sourceTrafficConfigId)
            assertEquals("Stable", stable.sourcePolicyGroupName)
            val updatedConfigs = fixture.readConfigs()
            assertEquals(8, updatedConfigs.selectedConfigId)
            assertEquals(listOf(7, 8), updatedConfigs.configs.map(ConfigProfile::id))
            assertEquals("Revised profile", updatedConfigs.configs.first().name)
            assertEquals("subscription://4", updatedConfigs.configs.first().sourceUrl)
            assertEquals(sibling, updatedConfigs.configs.last())
            assertEquals(40, updatedConfigs.nextConfigId)
            assertEquals(12, fixture.readServers().selectedServerId)
            assertEquals(31, fixture.readServers().nextServerId)

            val siblingGroup = fixture.readServers().servers.single { it.id == 25 }
                .decode().getOrThrow() as StrategyGroup
            assertEquals(listOf(11), siblingGroup.proxyServerIds)
            assertEquals(11, siblingGroup.selectedMemberId)
        }
    }

    @Test
    fun batchEmbeddedProfilesMaterializeGroupsInSourceOrderAfterTheAggregateCommit() = runBlocking {
        val initialConfigs = DesktopConfigLibrary(
            selectedConfigId = 6,
            configs = listOf(
                ConfigProfile(5, "Base one", "[Rule]\nFINAL,DIRECT\n"),
                ConfigProfile(6, "Base two", "[Rule]\nFINAL,DIRECT\n"),
            ),
            nextConfigId = 100,
        )
        val initialServers = DesktopServerLibrary(
            selectedServerId = 12,
            servers = listOf(
                stored(10, vless("Node A", "a.example")),
                stored(11, vless("Node B", "b.example")),
                stored(12, vless("Node C", "c.example")),
            ),
            nextServerId = 50,
        )

        withFixture(
            subscriptions = listOf(provider(4), provider(5)),
            configs = initialConfigs,
            servers = initialServers,
        ) { fixture ->
            val loadedUpdates = fixture.subscriptionRepository.catalog.value.subscriptions.map { subscription ->
                val config = when (subscription.id) {
                    4 -> embedded(
                        sourceUrl = "subscription://4",
                        fallbackName = "Provider 4",
                        activate = true,
                        content = profileDocument("Provider Four", "Provider Four Group", listOf("Node A", "Node B")),
                    )
                    5 -> embedded(
                        sourceUrl = "subscription://5",
                        fallbackName = "Provider 5",
                        activate = false,
                        content = profileDocument("Provider Five", "Provider Five Group", listOf("Node B", "Node C")),
                    )
                    else -> error("Unexpected subscription ${subscription.id}")
                }
                loaded(subscription, embedded = config)
            }
            val result = commitSubscriptionRefreshBatch(
                loaded = loadedUpdates,
                refreshedAtMillis = 13_000L,
                singlePort = fixture.singlePort(4),
                batchPort = fixture.batchPort(loadedUpdates),
            )

            assertTrue(result.applicable)
            assertEquals(listOf(4, 5), result.appliedSubscriptionIds)
            val updatedConfigs = fixture.readConfigs()
            assertEquals(listOf(5, 6, 100, 101), updatedConfigs.configs.map(ConfigProfile::id))
            assertEquals(100, updatedConfigs.selectedConfigId)
            assertEquals(102, updatedConfigs.nextConfigId)
            assertEquals("Provider Four", updatedConfigs.configs[2].name)
            assertEquals("Provider Five", updatedConfigs.configs[3].name)
            val savedServers = fixture.readServers()
            val firstGroup = savedServers.servers.single { it.id == 50 }
                .decode().getOrThrow() as StrategyGroup
            val secondGroup = savedServers.servers.single { it.id == 51 }
                .decode().getOrThrow() as StrategyGroup
            assertEquals(100, firstGroup.sourceTrafficConfigId)
            assertEquals("Provider Four Group", firstGroup.sourcePolicyGroupName)
            assertEquals(listOf(10, 11), firstGroup.proxyServerIds)
            assertEquals(101, secondGroup.sourceTrafficConfigId)
            assertEquals("Provider Five Group", secondGroup.sourcePolicyGroupName)
            assertEquals(listOf(11, 12), secondGroup.proxyServerIds)
            assertEquals(52, savedServers.nextServerId)
        }
    }

    @Test
    fun invalidDuplicateFinalDocumentDoesNotWriteProfilesOrReconcileTheServerLibrary() = runBlocking {
        val profile = ConfigProfile(
            id = 7,
            name = "Current profile",
            content = profileDocument("Current profile", "Stable", listOf("Node A")),
            sourceUrl = "subscription://4",
        )
        val initialConfigs = DesktopConfigLibrary(
            selectedConfigId = 7,
            configs = listOf(profile),
            nextConfigId = 8,
        )
        val initialServers = DesktopServerLibrary(
            selectedServerId = 10,
            servers = listOf(
                stored(10, vless("Node A", "a.example")),
                stored(11, vless("Node B", "b.example")),
                // Deliberately stale relative to the current profile. An erroneous reconciliation
                // after the rejected import would rewrite this record on disk.
                stored(
                    30,
                    StrategyGroup(
                        remarks = "Stable",
                        proxyServerIds = listOf(11),
                        selectedMemberId = 11,
                        sourceTrafficConfigId = 7,
                        sourcePolicyGroupName = "Stable",
                    ),
                    subscriptionId = -2,
                ),
            ),
            nextServerId = 31,
        )

        withFixture(
            subscriptions = listOf(provider(4)),
            configs = initialConfigs,
            servers = initialServers,
        ) { fixture ->
            val subscription = fixture.subscriptionRepository.catalog.value.subscriptions.single()
            val result = commitSubscriptionRefresh(
                loaded = loaded(
                    subscription = subscription,
                    embedded = embedded(
                        sourceUrl = "subscription://4",
                        fallbackName = "Invalid replacement",
                        activate = true,
                        content = """# name: Invalid replacement
                            [Proxy Group]
                            Stable = select, Node B, skipi-display=always

                            [Rule]
                            FINAL,DIRECT
                            FINAL,PROXY
                        """.trimIndent(),
                    ),
                ),
                refreshedAtMillis = 14_000L,
                port = fixture.singlePort(subscription.id),
            )

            assertTrue(result.applicable)
            assertNull(result.failure)
            assertEquals(initialConfigs, fixture.readConfigs())
            assertEquals(initialServers, fixture.readServers())
            assertEquals(initialConfigs, fixture.publishedConfigs)
            assertEquals(0, fixture.profileWriteAttempts)
            assertEquals(0, fixture.profilePublishCount)
            assertEquals(0, fixture.serverWriteAttempts)
            assertEquals(0, fixture.generatedGroupSaveAttempts)
        }
    }

    @Test
    fun failedProfileSaveDoesNotPublishProfilesOrPersistGeneratedGroups() = runBlocking {
        val initialConfigs = DesktopConfigLibrary(nextConfigId = 100)
        val initialServers = DesktopServerLibrary(nextServerId = 10)

        withFixture(
            subscriptions = listOf(provider(4)),
            configs = initialConfigs,
            servers = initialServers,
        ) { fixture ->
            fixture.profileSaveFailure = IllegalStateException("configs.json unavailable")
            val subscription = fixture.subscriptionRepository.catalog.value.subscriptions.single()
            val adapter = fixture.singlePort(subscription.id)
            val result = commitSubscriptionRefresh(
                loaded = loaded(
                    subscription = subscription,
                    servers = listOf(vless("Node B", "b.example")),
                    embedded = embedded(
                        sourceUrl = "subscription://4",
                        fallbackName = "New provider profile",
                        activate = true,
                        content = profileDocument("New provider profile", "Stable", listOf("Node B")),
                    ),
                ),
                refreshedAtMillis = 15_000L,
                port = adapter,
            )

            assertTrue(result.applicable)
            assertEquals(SubscriptionRefreshCommitScope.PROFILE, result.failure?.scope)
            assertEquals(
                listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
                result.appliedScopes,
            )
            assertEquals(initialConfigs, fixture.readConfigs())
            assertEquals(initialConfigs, fixture.publishedConfigs)
            assertFalse(adapter.profileLibraryChanged)
            assertEquals(1, fixture.profileWriteAttempts)
            assertEquals(0, fixture.profilePublishCount)
            assertEquals(1, fixture.successfulServerWriteCount)
            assertEquals(0, fixture.generatedGroupSaveAttempts)
            assertEquals(1, fixture.readServers().servers.size)
            assertTrue(fixture.readServers().servers.none { it.isGeneratedProfileGroup() })
        }
    }

    @Test
    fun generatedGroupSaveFailureReportsProfileScopeWithoutRollingBackTheCommittedProfile() = runBlocking {
        val initialConfigs = DesktopConfigLibrary(
            selectedConfigId = 5,
            configs = listOf(ConfigProfile(5, "Current profile", "[Rule]\nFINAL,DIRECT\n")),
            nextConfigId = 100,
        )
        val initialServers = DesktopServerLibrary(nextServerId = 10)

        withFixture(
            subscriptions = listOf(provider(4)),
            configs = initialConfigs,
            servers = initialServers,
        ) { fixture ->
            fixture.failNewGeneratedGroupSave = true
            val subscription = fixture.subscriptionRepository.catalog.value.subscriptions.single()
            val adapter = fixture.singlePort(subscription.id)
            val result = commitSubscriptionRefresh(
                loaded = loaded(
                    subscription = subscription,
                    servers = listOf(vless("Node B", "b.example")),
                    embedded = embedded(
                        sourceUrl = "subscription://4",
                        fallbackName = "New provider profile",
                        activate = true,
                        content = profileDocument("New provider profile", "Stable", listOf("Node B")),
                    ),
                ),
                refreshedAtMillis = 16_000L,
                port = adapter,
            )

            assertTrue(result.applicable)
            assertEquals(SubscriptionRefreshCommitScope.PROFILE, result.failure?.scope)
            assertEquals("injected generated-group save failure", result.failure?.error?.message)
            assertEquals(
                listOf(SubscriptionRefreshCommitScope.SERVERS, SubscriptionRefreshCommitScope.SUBSCRIPTION),
                result.appliedScopes,
            )
            val persistedConfigs = fixture.readConfigs()
            assertEquals(100, persistedConfigs.selectedConfigId)
            assertEquals(listOf(5, 100), persistedConfigs.configs.map(ConfigProfile::id))
            assertEquals("New provider profile", persistedConfigs.configs.last().name)
            assertEquals(persistedConfigs, fixture.publishedConfigs)
            assertTrue(adapter.profileLibraryChanged)
            assertEquals(1, fixture.profileWriteAttempts)
            assertEquals(1, fixture.profilePublishCount)

            val persistedServers = fixture.readServers()
            assertEquals(listOf(10), persistedServers.servers.map(DesktopStoredProxyServer::id))
            assertTrue(persistedServers.servers.none { it.isGeneratedProfileGroup() })
            assertEquals(11, persistedServers.nextServerId)
            assertEquals(persistedServers, fixture.publishedServers)
            assertEquals(2, fixture.serverWriteAttempts)
            assertEquals(1, fixture.successfulServerWriteCount)
            assertEquals(1, fixture.generatedGroupSaveAttempts)
        }
    }

    private class Fixture(
        root: Path,
        subscriptions: List<StoredSubscription>,
        initialConfigs: DesktopConfigLibrary,
        initialServers: DesktopServerLibrary,
    ) {
        private val configPath = root.resolve("configs.json")
        private val serverPath = root.resolve("servers.json")
        private var subscriptionLibrary = DesktopSubscriptionLibrary(subscriptions = subscriptions)

        var publishedConfigs = initialConfigs
            private set
        var publishedServers = initialServers
            private set
        var profileSaveFailure: Throwable? = null
        var failNewGeneratedGroupSave = false
        var profileWriteAttempts = 0
            private set
        var profilePublishCount = 0
            private set
        var serverWriteAttempts = 0
            private set
        var successfulServerWriteCount = 0
            private set
        var generatedGroupSaveAttempts = 0
            private set

        init {
            DesktopConfigLibraries.save(configPath, initialConfigs).getOrThrow()
            DesktopServerLibraries.save(serverPath, initialServers).getOrThrow()
        }

        val subscriptionRepository = DesktopSubscriptionRepository(
            initialLibrary = subscriptionLibrary,
            readLibrary = { subscriptionLibrary },
            saveLibrary = { updated ->
                subscriptionLibrary = updated
                Result.success(Unit)
            },
            publishLibrary = { updated -> subscriptionLibrary = updated },
        )

        private val proxyServerRepository = DesktopProxyServerRepository(
            initialLibrary = readServers(),
            readLibrary = ::readServers,
            saveLibrary = ::saveServers,
            publishLibrary = { updated -> publishedServers = updated },
        )

        fun readConfigs(): DesktopConfigLibrary = DesktopConfigLibraries.load(configPath).getOrThrow()

        fun readServers(): DesktopServerLibrary = DesktopServerLibraries.load(serverPath).getOrThrow()

        fun singlePort(subscriptionId: Int) = DesktopSubscriptionRefreshCommitAdapter(
            subscriptionId = subscriptionId,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = ::readConfigs,
            saveConfigs = ::saveConfigs,
            publishConfigs = ::publishConfigs,
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )

        fun batchPort(loaded: List<LoadedSubscriptionRefresh>) = DesktopSubscriptionRefreshBatchCommitAdapter(
            loaded = loaded,
            subscriptionRepository = subscriptionRepository,
            proxyServerRepository = proxyServerRepository,
            readConfigs = ::readConfigs,
            saveConfigs = ::saveConfigs,
            publishConfigs = ::publishConfigs,
            readLatencyByServerId = { emptyMap() },
            publishLatencyByServerId = {},
        )

        private fun saveConfigs(updated: DesktopConfigLibrary): Result<Unit> {
            profileWriteAttempts += 1
            profileSaveFailure?.let { return Result.failure(it) }
            return DesktopConfigLibraries.save(configPath, updated)
        }

        private fun publishConfigs(updated: DesktopConfigLibrary) {
            profilePublishCount += 1
            publishedConfigs = updated
        }

        private fun saveServers(updated: DesktopServerLibrary): Result<Unit> {
            serverWriteAttempts += 1
            val currentGeneratedIds = readServers().generatedProfileGroupIds()
            val newGeneratedGroupIds = updated.generatedProfileGroupIds() - currentGeneratedIds
            if (newGeneratedGroupIds.isNotEmpty()) {
                generatedGroupSaveAttempts += 1
                if (failNewGeneratedGroupSave) {
                    return Result.failure(IllegalStateException("injected generated-group save failure"))
                }
            }
            return DesktopServerLibraries.save(serverPath, updated).also { result ->
                if (result.isSuccess) successfulServerWriteCount += 1
            }
        }
    }

    private fun stored(
        id: Int,
        server: features.proxy.server.model.ProxyServer<*>,
        subscriptionId: Int? = null,
    ) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )

    private fun vless(name: String, host: String) = VLESS(
        remarks = name,
        id = "${name.lowercase().replace(' ', '-')}-id",
        server = host,
        port = "443",
    )

    private fun provider(id: Int) = StoredSubscription(
        id = id,
        url = "https://subscriptions.example.test/$id",
        name = "Provider $id",
    )

    private fun loaded(
        subscription: SubscriptionRecord,
        servers: List<features.proxy.server.model.ProxyServer<*>> = emptyList(),
        embedded: ResolvedEmbeddedSubscriptionConfig? = null,
    ) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = servers.size,
        servers = servers,
        metadata = SubscriptionMetadata(),
        resolvedEmbeddedConfig = embedded,
    )

    private fun embedded(
        sourceUrl: String,
        fallbackName: String,
        activate: Boolean,
        content: String,
    ) = ResolvedEmbeddedSubscriptionConfig(
        content = content,
        sourceUrl = sourceUrl,
        fallbackName = fallbackName,
        activate = activate,
    )

    private inline fun withFixture(
        subscriptions: List<StoredSubscription>,
        configs: DesktopConfigLibrary,
        servers: DesktopServerLibrary,
        block: (Fixture) -> Unit,
    ) {
        val root = Files.createTempDirectory("desktop-subscription-profile-group-")
        try {
            block(Fixture(root, subscriptions, configs, servers))
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private companion object {
        fun profileDocument(profileName: String, groupName: String, members: List<String>): String =
            """# name: $profileName
                [Proxy Group]
                $groupName = select, ${members.joinToString(", ")}, skipi-display=always

                [Rule]
                FINAL,DIRECT
            """.trimIndent()
    }
}

private fun DesktopServerLibrary.generatedProfileGroupIds(): Set<Int> = servers.mapNotNull { stored ->
    val strategy = stored.decode().getOrNull() as? StrategyGroup ?: return@mapNotNull null
    stored.id.takeIf { strategy.sourceTrafficConfigId != null }
}.toSet()

private fun DesktopStoredProxyServer.isGeneratedProfileGroup(): Boolean =
    (decode().getOrNull() as? StrategyGroup)?.sourceTrafficConfigId != null
