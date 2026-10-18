// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.TrafficConfigLibraryOperations
import app.skipi.app.config.TrafficConfigProfileRefreshMode
import app.skipi.app.config.TrafficConfigProfileRefreshOperations
import features.config.ConfigProfile
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupDisplayMode
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopTrafficConfigProxyGroupReconcilerTest {
    @Test
    fun latestRefreshAndCatalogReconcilePreserveGroupIdentityOpaqueSelectionAndLibraries() = runBlocking {
        val initialProfile = ConfigProfile(
            id = 7,
            name = "Remote",
            content = groupDocument("Node A", "Node B"),
            sourceUrl = "https://captured.example/profile.conf",
        )
        val unrelatedProfile = ConfigProfile(25, "Unrelated", "[General]\nloglevel = info\n")
        val initialConfigs = DesktopConfigLibrary(
            selectedConfigId = initialProfile.id,
            configs = listOf(initialProfile),
            nextConfigId = 8,
        )
        var persistedConfigs = initialConfigs
        val configRepository = DesktopTrafficConfigRepository(
            initialLibrary = initialConfigs,
            readLibrary = { persistedConfigs },
            saveLibrary = { updated ->
                persistedConfigs = updated
                Result.success(Unit)
            },
            publishLibrary = { updated -> persistedConfigs = updated },
        )

        // A profile edit and an unrelated import complete while the fetch is in flight.
        persistedConfigs = initialConfigs.copy(
            selectedConfigId = unrelatedProfile.id,
            configs = listOf(initialProfile.copy(name = "Edited while fetching"), unrelatedProfile),
            nextConfigId = 60,
        )
        val capturedUrl = initialProfile.sourceUrl
        val refreshedAt = 900_000L
        val committedConfigs = configRepository.transformLatest { latest ->
            val current = requireNotNull(latest.configs.firstOrNull { it.id == initialProfile.id })
                .toRefreshState()
            val refreshed = TrafficConfigProfileRefreshOperations.apply(
                current = current,
                normalizedDocument = fetchedDocumentWithoutName,
                capturedUrl = capturedUrl,
                timestamp = refreshedAt,
                mode = TrafficConfigProfileRefreshMode.Manual(unlock = false),
            )
            TrafficConfigLibraryOperations.update(
                library = latest,
                id = refreshed.id,
                name = refreshed.name,
                content = refreshed.rawConfig,
                sourceUrl = refreshed.sourceUrl,
                updateLocked = refreshed.updateLocked,
                lastUpdatedAtMillis = refreshed.lastUpdatedAtMillis,
            )
        }

        assertEquals(listOf(7, 25), committedConfigs.configs.map(ConfigProfile::id))
        assertEquals(25, committedConfigs.selectedConfigId)
        assertEquals(60, committedConfigs.nextConfigId)
        assertEquals("Edited while fetching", committedConfigs.configs.first().name)
        assertEquals(capturedUrl, committedConfigs.configs.first().sourceUrl)
        assertEquals(refreshedAt, committedConfigs.configs.first().lastUpdatedAtMillis)
        assertTrue(committedConfigs.configs.first().content.contains("Stable = select, Node B, Node C"))
        assertEquals(unrelatedProfile, committedConfigs.configs.last())

        val manual = stored(5, VLESS(remarks = "Manual", id = "m", server = "manual.example", port = "443"))
        val nodeA = stored(10, VLESS(remarks = "Node A", id = "a", server = "a.example", port = "443"))
        val nodeB = stored(20, VLESS(remarks = "Node B", id = "b", server = "b.example", port = "443"))
        val generated = stored(
            30,
            StrategyGroup(
                remarks = "Stable",
                proxyServerIds = listOf(10, 20),
                selectedMemberId = 20,
                displayMode = StrategyGroupDisplayMode.ALWAYS,
                sourceTrafficConfigId = initialProfile.id,
                sourcePolicyGroupName = "Stable",
            ),
            subscriptionId = -2,
        )
        val opaque = DesktopStoredProxyServer(id = 99, serverJson = "{\"futureProxy\":{\"keep\":true}}")
        val initialServers = DesktopServerLibrary(
            selectedServerId = opaque.id,
            servers = listOf(manual, nodeA, nodeB, generated, opaque),
            nextServerId = 100,
        )
        var persistedServers = initialServers
        var publishedServers = initialServers
        val serverRepository = DesktopProxyServerRepository(
            initialLibrary = initialServers,
            readLibrary = { persistedServers },
            saveLibrary = { updated ->
                persistedServers = updated
                Result.success(Unit)
            },
            publishLibrary = { updated -> publishedServers = updated },
        )
        // A newer server snapshot appears after the adapter was constructed.
        val nodeC = stored(40, VLESS(remarks = "Node C", id = "c", server = "c.example", port = "443"))
        persistedServers = initialServers.copy(servers = initialServers.servers + nodeC)

        DesktopTrafficConfigProxyGroupReconciler(
            serverRepository = serverRepository,
            readConfigLibrary = { persistedConfigs },
            stripLeadingCountryFlag = { it },
        ).reconcile()

        val updatedServers = persistedServers
        val updatedGroup = updatedServers.servers.single { it.id == generated.id }
            .decode().getOrThrow() as StrategyGroup
        assertEquals(listOf(20, 40), updatedGroup.proxyServerIds)
        assertEquals(20, updatedGroup.selectedMemberId)
        assertEquals(initialProfile.id, updatedGroup.sourceTrafficConfigId)
        assertEquals("Stable", updatedGroup.sourcePolicyGroupName)
        assertEquals(opaque.id, updatedServers.selectedServerId)
        assertEquals(opaque, updatedServers.servers.single { it.id == opaque.id })
        assertEquals(manual, updatedServers.servers.single { it.id == manual.id })
        assertEquals(nodeA, updatedServers.servers.single { it.id == nodeA.id })
        assertEquals(nodeB, updatedServers.servers.single { it.id == nodeB.id })
        assertEquals(nodeC, updatedServers.servers.single { it.id == nodeC.id })
        assertEquals(100, updatedServers.nextServerId)
        assertEquals(updatedServers, publishedServers)
        assertEquals(committedConfigs, persistedConfigs)
    }

    @Test
    fun failedServerSaveDoesNotPublishReconciledCatalog() = runBlocking {
        val profile = ConfigProfile(
            id = 7,
            name = "Remote",
            content = groupDocument("Node B", "Node C"),
        )
        val manual = stored(5, VLESS(remarks = "Manual", id = "m", server = "manual.example", port = "443"))
        val nodeB = stored(20, VLESS(remarks = "Node B", id = "b", server = "b.example", port = "443"))
        val nodeC = stored(40, VLESS(remarks = "Node C", id = "c", server = "c.example", port = "443"))
        val generated = stored(
            30,
            StrategyGroup(
                remarks = "Stable",
                proxyServerIds = listOf(10, 20),
                selectedMemberId = 20,
                sourceTrafficConfigId = profile.id,
                sourcePolicyGroupName = "Stable",
            ),
            subscriptionId = -2,
        )
        val initial = DesktopServerLibrary(
            selectedServerId = 5,
            servers = listOf(manual, nodeB, nodeC, generated),
            nextServerId = 50,
        )
        var published = initial
        var publishCount = 0
        val repository = DesktopProxyServerRepository(
            initialLibrary = initial,
            readLibrary = { initial },
            saveLibrary = { Result.failure(IllegalStateException("save failed")) },
            publishLibrary = {
                publishCount += 1
                published = it
            },
        )

        val failure = runCatching {
            DesktopTrafficConfigProxyGroupReconciler(
                serverRepository = repository,
                readConfigLibrary = { DesktopConfigLibrary(configs = listOf(profile), nextConfigId = 8) },
                stripLeadingCountryFlag = { it },
            ).reconcile()
        }.exceptionOrNull()

        assertIs<IllegalStateException>(failure)
        assertEquals(0, publishCount)
        assertEquals(initial, published)
    }

    private fun ConfigProfile.toRefreshState(): TrafficConfigState {
        val parsed = TrafficConfigState(
            id = id,
            name = name,
            rawConfig = content,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
        ).withSkipiSettingsReadFromRawConfig()
        return parsed.copy(
            id = id,
            name = name,
            sourceUrl = sourceUrl,
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
        )
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

    private companion object {
        fun groupDocument(first: String, second: String): String = """[Proxy Group]
            Stable = select, $first, $second, skipi-display=always
        """.trimIndent()

        val fetchedDocumentWithoutName = TrafficConfigState(
            id = 99,
            name = "Provider name that should not replace the latest name",
            rawConfig = groupDocument("Node B", "Node C"),
            sourceUrl = "https://provider.example/profile.conf",
        ).withSkipiSettingsInRawConfig().rawConfig
            .replace("profile-name = Provider name that should not replace the latest name\n", "")
    }
}
