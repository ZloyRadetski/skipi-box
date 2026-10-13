// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

// Draft intended for desktopApp/src/test/kotlin/app/skipi/desktop/DesktopVisibleServerReorderBridgeTest.kt
// Keep outside module source sets until root reviews the expected bridge API.
package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import features.proxy.server.model.HTTP
import features.proxy.server.model.encodePersistedProxyServer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Resolves a neighbor from visible Home order, then moves IDs in the latest whole library. */
class DesktopVisibleServerReorderBridgeTest {
    @Test
    fun visibleNeighborMovesLatestWholeLibraryAndPreservesOtherState() {
        val path = Files.createTempFile("skipi-visible-reorder", ".json")
        val initial = DesktopServerLibrary(
            selectedServerId = 2,
            servers = listOf(
                stored(1, "A first", subscriptionId = 10),
                stored(2, "B", subscriptionId = 20),
                stored(3, "A second", subscriptionId = 10),
            ),
            nextServerId = 80,
        )

        try {
            DesktopServerLibraries.save(path, initial).getOrThrow()
            val repository = DesktopProxyServerRepository(
                initialLibrary = initial,
                readLibrary = { DesktopServerLibraries.load(path).getOrThrow() },
                saveLibrary = { DesktopServerLibraries.save(path, it) },
                publishLibrary = {},
            )
            val effectContext = DesktopProxyHomeEffectContext(
                decodedServers = initial.servers.map { stored -> stored to stored.decode().getOrThrow() },
                orderedVisibleServerIds = listOf(1, 3),
                onMoveServer = { fromServerId, toServerId ->
                    repository.updateLibrary { current ->
                        current.copy(
                            servers = reorderServerInLibrary(
                                current.servers,
                                fromServerId = fromServerId,
                                toServerId = toServerId,
                            ),
                        )
                    }
                    Result.success(Unit)
                },
            )

            // A concurrent writer adds a different-group row and advances the high-water mark
            // after the Home-visible order was captured but before the move is committed.
            repository.updateLibrary { current ->
                current.copy(
                    servers = current.servers + stored(4, "late row", subscriptionId = 30),
                    nextServerId = 81,
                )
            }

            assertTrue(effectContext.handle(ProxyHomeEffect.MoveServer("1", 1)).isSuccess)

            val persisted = DesktopServerLibraries.load(path).getOrThrow()
            assertEquals(listOf(2, 3, 1, 4), persisted.servers.map(DesktopStoredProxyServer::id))
            assertEquals(2, persisted.selectedServerId)
            assertEquals(81, persisted.nextServerId)
            assertEquals(listOf(20, 10, 10, 30), persisted.servers.map(DesktopStoredProxyServer::subscriptionId))
            assertEquals(
                listOf("B", "A second", "A first", "late row"),
                persisted.servers.map { it.decode().getOrThrow().getInfo().remarks },
            )
        } finally {
            Files.deleteIfExists(path)
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
        }
    }

    private fun stored(id: Int, remarks: String, subscriptionId: Int) = DesktopStoredProxyServer(
        id = id,
        serverJson = HTTP(remarks = remarks, server = "$id.example").encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
}
