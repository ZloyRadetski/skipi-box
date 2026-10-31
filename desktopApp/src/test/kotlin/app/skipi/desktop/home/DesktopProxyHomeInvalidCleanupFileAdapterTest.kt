// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.proxy.ProxyServerStopOutcome
import app.skipi.app.store.SharedApplicationActionOutcome
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Exercises invalid cleanup through the Desktop repository's real file-backed adapter. */
class DesktopProxyHomeInvalidCleanupFileAdapterTest {
    @Test
    fun selectedInvalidCleanupStopsThenDeletesCapturedIdsFromLatestFileCatalog() = runBlocking {
        val libraryPath = Files.createTempFile("skipi-home-invalid-delete", ".json")
        val initial = DesktopServerLibrary(
            selectedServerId = 2,
            servers = listOf(
                stored(2, HTTP(port = ""), subscriptionId = 12),
                stored(3, HTTP(remarks = "Valid", server = "valid.example", port = "443"), 13),
                stored(4, HTTP(port = ""), 14),
            ),
            nextServerId = 20,
        )
        val events = mutableListOf<String>()

        try {
            DesktopServerLibraries.save(libraryPath, initial).getOrThrow()
            val repository = repository(libraryPath, initial)
            runDesktopProxyHomeDeleteInvalidServers(
                currentGroupServerIds = setOf(2, 3),
                servers = repository.servers.value,
                selectedServerId = initial.selectedServerId,
                tunnelRunning = true,
                serviceOperationInProgress = false,
                stopService = {
                    events += "stop"
                    // A later catalog write lands while stopping. The delete must retain it,
                    // its selected row, and its ID high-water mark while pruning references.
                    repository.updateCatalog { latest ->
                        latest.copy(
                            servers = latest.servers + listOf(
                                ProxyServerRecord(
                                    id = 30,
                                    server = ChainProxy(proxyServerIds = listOf(2, 3)),
                                    sourceSubscriptionId = 30,
                                ),
                                ProxyServerRecord(
                                    id = 40,
                                    server = ProxyServer.parse(
                                        "vless://late@late.example:443#Late",
                                    ),
                                    sourceSubscriptionId = 40,
                                ),
                            ),
                            selectedServerId = 40,
                            nextServerId = 100,
                        )
                    }
                    ProxyServerStopOutcome.Stopped
                },
                applyStoppedState = { events += "stop-state" },
                dispatchDelete = { ids ->
                    events += "delete:${ids.sorted().joinToString(",")}"
                    repository.updateCatalog { latest ->
                        app.skipi.app.proxy.deleteProxyServerRecords(latest, ids)
                    }
                    SharedApplicationActionOutcome.Completed
                },
                onDeleted = { count -> events += "deleted:$count" },
                onNoInvalidServers = { events += "empty" },
                onDeleteError = { events += "delete-error" },
                onStopFailed = { events += "stop-error" },
            )

            val persisted = DesktopServerLibraries.load(libraryPath).getOrThrow()
            assertEquals(listOf("stop", "stop-state", "delete:2", "deleted:1"), events)
            assertEquals(listOf(3, 4, 30, 40), persisted.servers.map { it.id })
            assertEquals(40, persisted.selectedServerId)
            assertEquals(100, persisted.nextServerId)
            assertEquals(
                listOf(3),
                (persisted.servers.single { it.id == 30 }.decode().getOrThrow() as ChainProxy).proxyServerIds,
            )
            assertEquals(listOf(13, 14, 30, 40), persisted.servers.map { it.subscriptionId })
        } finally {
            Files.deleteIfExists(libraryPath)
            Files.deleteIfExists(libraryPath.resolveSibling("${libraryPath.fileName}.tmp"))
        }
    }

    private fun repository(path: java.nio.file.Path, initial: DesktopServerLibrary) = DesktopProxyServerRepository(
        initialLibrary = initial,
        readLibrary = { DesktopServerLibraries.load(path).getOrThrow() },
        saveLibrary = { DesktopServerLibraries.save(path, it) },
        publishLibrary = {},
    )

    private fun stored(id: Int, server: ProxyServer<*>, subscriptionId: Int) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = subscriptionId,
    )
}
