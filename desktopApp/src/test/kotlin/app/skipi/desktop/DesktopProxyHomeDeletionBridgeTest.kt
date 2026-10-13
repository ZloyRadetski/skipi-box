// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.proxy.deleteProxyServerRecords
import features.proxy.server.model.HTTP
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.encodePersistedProxyServer
import features.proxy.server.usecase.ProxyServerGroupCleanup
import features.proxy.server.usecase.ProxyServerGroupCleanupRecord
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Exercises Home effects against Desktop's actual file-backed proxy repository. */
class DesktopProxyHomeDeletionBridgeTest {
    @Test
    fun selectedAndUnselectedDeleteEffectsPersistFallbackAndHighWaterMark() {
        val libraryPath = Files.createTempFile("skipi-home-delete", ".json")
        val initial = DesktopServerLibrary(
            selectedServerId = 9,
            servers = listOf(
                stored(42, "first.example", subscriptionId = 4),
                stored(9, "selected.example", subscriptionId = 9),
                stored(7, "unselected.example", subscriptionId = 7),
            ),
            nextServerId = 80,
        )

        try {
            DesktopServerLibraries.save(libraryPath, initial).getOrThrow()
            val repository = repository(libraryPath, initial)
            val effectContext = DesktopProxyHomeEffectContext(
                decodedServers = initial.servers.map { stored -> stored to stored.decode().getOrThrow() },
                onDeleteServer = { serverId -> runBlocking { repository.remove(serverId) } },
            )

            assertTrue(effectContext.handle(ProxyHomeEffect.DeleteServer("9")).isSuccess)
            var persisted = DesktopServerLibraries.load(libraryPath).getOrThrow()
            assertEquals(42, persisted.selectedServerId)
            assertEquals(listOf(42, 7), persisted.servers.map { it.id })
            assertEquals(80, persisted.nextServerId)

            assertTrue(effectContext.handle(ProxyHomeEffect.DeleteServer("7")).isSuccess)
            persisted = DesktopServerLibraries.load(libraryPath).getOrThrow()
            assertEquals(42, persisted.selectedServerId)
            assertEquals(listOf(42), persisted.servers.map { it.id })
            assertEquals(80, persisted.nextServerId)
        } finally {
            deleteLibraryFiles(libraryPath)
        }
    }

    @Test
    fun duplicateCleanupReadsLatestCatalogAndPersistsReferencePruning() {
        val duplicateUrl = "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@node.example:443#Node"
        val libraryPath = Files.createTempFile("skipi-home-duplicate-cleanup", ".json")
        val initial = DesktopServerLibrary(
            selectedServerId = 8,
            servers = listOf(
                stored(30, StrategyGroup(proxyServerIds = listOf(7, 8, 9), selectedMemberId = 7), 70),
                stored(40, ChainProxy(proxyServerIds = listOf(7, 8, 9)), 71),
                stored(7, ProxyServer.parse(duplicateUrl), 72),
                stored(8, ProxyServer.parse(duplicateUrl), 73),
                stored(9, ProxyServer.parse("vless://other@other.example:443#Other"), 99),
            ),
            nextServerId = 80,
        )
        val currentGroupServerIds = setOf(7, 8, 11)

        try {
            DesktopServerLibraries.save(libraryPath, initial).getOrThrow()
            val repository = repository(libraryPath, initial)
            val callbackCalls = mutableListOf<String>()
            var duplicateCallbackGroupIds: Set<Int>? = null
            val effectContext = DesktopProxyHomeEffectContext(
                currentGroupServerIds = currentGroupServerIds,
                decodedServers = initial.servers.map { stored -> stored to stored.decode().getOrThrow() },
                onDeleteDuplicateServers = { groupIds ->
                    callbackCalls += "duplicates"
                    duplicateCallbackGroupIds = groupIds
                    runBlocking {
                        repository.updateCatalog { latest ->
                            val duplicates = ProxyServerGroupCleanup.duplicateServerIds(
                                servers = latest.servers.map { ProxyServerGroupCleanupRecord(it.id, it.server) },
                                currentGroupServerIds = groupIds,
                                selectedServerId = latest.selectedServerId,
                            )
                            deleteProxyServerRecords(latest, duplicates)
                        }
                    }
                },
                onDeleteInvalidServers = { _ -> callbackCalls += "invalid" },
            )

            // Add/select a later duplicate after the effect context was created. Cleanup must use
            // the repository's latest catalog; Android keeps the selected duplicate and removes
            // earlier matching rows within the current group.
            runBlocking {
                repository.updateCatalog { latest ->
                    latest.copy(
                        servers = latest.servers + ProxyServerRecord(
                            id = 11,
                            server = ProxyServer.parse(duplicateUrl),
                            sourceSubscriptionId = 74,
                        ),
                        selectedServerId = 11,
                    )
                }
            }

            assertTrue(effectContext.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.DeleteDuplicateServers)).isSuccess)
            val persisted = DesktopServerLibraries.load(libraryPath).getOrThrow()
            assertEquals(listOf("duplicates"), callbackCalls)
            assertEquals(currentGroupServerIds, duplicateCallbackGroupIds)
            assertEquals(listOf(30, 40, 9, 11), persisted.servers.map { it.id })
            assertEquals(11, persisted.selectedServerId)
            assertEquals(80, persisted.nextServerId)
            assertEquals(listOf(70, 71, 99, 74), persisted.servers.map { it.subscriptionId })
            assertEquals(listOf(9), (persisted.servers.single { it.id == 30 }.decode().getOrThrow() as StrategyGroup).proxyServerIds)
            assertEquals(listOf(9), (persisted.servers.single { it.id == 40 }.decode().getOrThrow() as ChainProxy).proxyServerIds)
        } finally {
            deleteLibraryFiles(libraryPath)
        }
    }

    @Test
    fun invalidCleanupCallbackRemovesOnlyInvalidCurrentGroupRows() {
        val libraryPath = Files.createTempFile("skipi-home-invalid-cleanup", ".json")
        val initial = DesktopServerLibrary(
            selectedServerId = 2,
            servers = listOf(
                stored(1, HTTP(port = ""), 10),
                stored(2, HTTP(remarks = "Valid", server = "valid.example", port = "443"), 11),
                stored(3, HTTP(port = ""), 12),
            ),
            nextServerId = 20,
        )

        try {
            DesktopServerLibraries.save(libraryPath, initial).getOrThrow()
            val repository = repository(libraryPath, initial)
            var callbackCount = 0
            val currentGroupServerIds = setOf(1, 2)
            var invalidCallbackGroupIds: Set<Int>? = null
            val effectContext = DesktopProxyHomeEffectContext(
                currentGroupServerIds = currentGroupServerIds,
                onDeleteInvalidServers = { groupIds ->
                    callbackCount += 1
                    invalidCallbackGroupIds = groupIds
                    runBlocking {
                        repository.updateCatalog { latest ->
                            val invalid = ProxyServerGroupCleanup.invalidServerIds(
                                servers = latest.servers.map { ProxyServerGroupCleanupRecord(it.id, it.server) },
                                currentGroupServerIds = groupIds,
                            )
                            deleteProxyServerRecords(latest, invalid)
                        }
                    }
                },
            )

            assertTrue(effectContext.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.DeleteInvalidServers)).isSuccess)
            val persisted = DesktopServerLibraries.load(libraryPath).getOrThrow()
            assertEquals(1, callbackCount)
            assertEquals(currentGroupServerIds, invalidCallbackGroupIds)
            assertEquals(listOf(2, 3), persisted.servers.map { it.id })
            assertEquals(2, persisted.selectedServerId)
            assertEquals(20, persisted.nextServerId)
        } finally {
            deleteLibraryFiles(libraryPath)
        }
    }

    @Test
    fun missingCleanupCallbackReportsUnsupportedAndDoesNotChangeLibrary() {
        val libraryPath = Files.createTempFile("skipi-home-cleanup-unwired", ".json")
        val initial = DesktopServerLibrary(
            selectedServerId = 9,
            servers = listOf(stored(9, "selected.example", 9)),
            nextServerId = 80,
        )

        try {
            DesktopServerLibraries.save(libraryPath, initial).getOrThrow()
            val before = DesktopServerLibraries.load(libraryPath).getOrThrow()
            val effectContext = DesktopProxyHomeEffectContext()

            val result = effectContext.handle(ProxyHomeEffect.RunServerTool(ProxyHomeServerTool.DeleteDuplicateServers))

            assertIs<IllegalStateException>(result.exceptionOrNull())
            assertEquals(before, DesktopServerLibraries.load(libraryPath).getOrThrow())
        } finally {
            deleteLibraryFiles(libraryPath)
        }
    }

    private fun repository(path: java.nio.file.Path, initial: DesktopServerLibrary) = DesktopProxyServerRepository(
        initialLibrary = initial,
        readLibrary = { DesktopServerLibraries.load(path).getOrThrow() },
        saveLibrary = { DesktopServerLibraries.save(path, it) },
        publishLibrary = {},
    )

    private fun stored(id: Int, host: String, subscriptionId: Int) =
        stored(id, HTTP(remarks = host, server = host), subscriptionId)

    private fun stored(id: Int, server: ProxyServer<*>, subscriptionId: Int) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )

    private fun deleteLibraryFiles(path: java.nio.file.Path) {
        Files.deleteIfExists(path)
        Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
    }
}
