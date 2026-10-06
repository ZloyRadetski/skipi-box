// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord
import features.proxy.server.model.HTTP
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopProxyServerRepositoryTest {
    @Test
    fun selectingServerPreservesPersistedIdPayloadAndSubscriptionAssociation() {
        val payload = HTTP(remarks = "Stable ID", server = "proxy.example").encodePersistedProxyServer()
        var currentLibrary = DesktopServerLibrary(
            selectedServerId = null,
            servers = listOf(
                DesktopStoredProxyServer(
                    id = 42,
                    serverJson = payload,
                    subscriptionId = 9,
                ),
            ),
        )
        val repository = DesktopProxyServerRepository(
            initialLibrary = currentLibrary,
            readLibrary = { currentLibrary },
            saveLibrary = { library ->
                currentLibrary = library
                Result.success(Unit)
            },
            publishLibrary = { library -> currentLibrary = library },
        )

        repository.selectAndCommit(42)

        assertEquals(42, currentLibrary.selectedServerId)
        assertEquals(42, currentLibrary.servers.single().id)
        assertEquals(payload, currentLibrary.servers.single().serverJson)
        assertEquals(9, currentLibrary.servers.single().subscriptionId)
    }

    @Test
    fun identityCatalogUpdatePreservesExistingAndNullSelections() = runBlocking {
        val nonFirstLibrary = serverLibrary(
            selectedServerId = 9,
            records = listOf(
                serverRecord(42, "first.example"),
                serverRecord(9, "selected.example"),
            ),
        )
        val nonFirst = RepositoryFixture(nonFirstLibrary)
        nonFirst.repository.updateCatalog { it }

        assertEquals(9, nonFirst.persistedLibrary.selectedServerId)
        assertEquals(listOf(42, 9), nonFirst.repository.servers.value.map(ProxyServerRecord::id))

        val nullSelection = RepositoryFixture(
            serverLibrary(
                selectedServerId = null,
                records = listOf(serverRecord(42, "available.example")),
            ),
        )
        nullSelection.repository.updateCatalog { it }

        assertEquals(null, nullSelection.persistedLibrary.selectedServerId)
        assertEquals(listOf(42), nullSelection.repository.servers.value.map(ProxyServerRecord::id))
    }

    @Test
    fun updateCollectionPersistsAndPublishesReorderedServers() = runBlocking {
        val initial = serverLibrary(
            selectedServerId = 9,
            records = listOf(
                serverRecord(42, "first.example", subscriptionId = 4),
                serverRecord(9, "second.example", subscriptionId = 8),
            ),
        )
        val fixture = RepositoryFixture(initial)
        val reordered = fixture.repository.servers.value.reversed()

        fixture.repository.updateCollection { reordered }

        assertEquals(listOf(9, 42), fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::id))
        assertEquals(listOf(9, 42), fixture.repository.servers.value.map(ProxyServerRecord::id))
        assertEquals(listOf(8, 4), fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::subscriptionId))
    }

    @Test
    fun catalogReplacementPublishesOnlyTheFinalOrderedSnapshotAndFallsBackToItsFirstServer() = runBlocking {
        val initial = serverLibrary(
            selectedServerId = 9,
            records = listOf(
                serverRecord(42, "old-a.example", subscriptionId = 4),
                serverRecord(9, "selected-old.example", subscriptionId = 9),
                serverRecord(7, "old-c.example", subscriptionId = 7),
            ),
        )
        val fixture = RepositoryFixture(initial)
        val replacement = listOf(
            serverRecord(70, "new-first.example", subscriptionId = 70),
            serverRecord(42, "updated-second.example", subscriptionId = 41),
        )
        val expectedFinalLibrary = DesktopServerLibrary(
            selectedServerId = 70,
            servers = replacement.map { it.toStoredServer() },
        )

        fixture.repository.updateCatalog { current ->
            current.copy(
                servers = replacement,
                selectedServerId = 9,
            )
        }

        assertEquals(expectedFinalLibrary.observableContent(), fixture.persistedLibrary.observableContent())
        assertEquals(replacement, fixture.repository.servers.value)
        assertEquals(
            listOf(expectedFinalLibrary.observableContent()),
            fixture.publicationHistory.map { it.observableContent() },
        )
    }

    @Test
    fun catalogTransformUsesTheLatestHostLibraryAfterAnExternalAddition() = runBlocking {
        val original = serverLibrary(
            selectedServerId = 1,
            records = listOf(serverRecord(1, "first.example")),
        )
        val fixture = RepositoryFixture(original)
        fixture.replacePersistedExternally(
            serverLibrary(
                selectedServerId = 1,
                records = listOf(
                    serverRecord(1, "first.example"),
                    serverRecord(2, "external.example"),
                ),
            ),
        )
        var addedByTransform: ProxyServerRecord? = null

        fixture.repository.updateCatalog { current ->
            val newServer = serverRecord(current.nextServerId, "repository.example")
            addedByTransform = newServer
            current.copy(
                servers = current.servers + newServer,
                nextServerId = newServer.id + 1,
            )
        }

        assertEquals(listOf(1, 2, 3), fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::id))
        assertEquals(listOf(1, 2, 3), fixture.repository.servers.value.map(ProxyServerRecord::id))
        assertEquals(3, addedByTransform?.id)
        assertEquals(serverRecord(2, "external.example"), fixture.repository.servers.value[1])
    }

    @Test
    fun persistenceFailureLeavesThePreviousCatalogAndPublicationHistoryIntact() = runBlocking {
        val directory = Files.createTempDirectory("skipi-proxy-server-save-failure-")
        val path = directory.resolve("servers.json")
        val original = serverLibrary(
            selectedServerId = 2,
            records = listOf(
                serverRecord(1, "a.example"),
                serverRecord(2, "b.example"),
                serverRecord(3, "c.example"),
            ),
        )
        val publicationHistory = mutableListOf<DesktopServerLibrary>()
        try {
            DesktopServerLibraries.save(path, original).getOrThrow()
            val persistedOriginal = DesktopServerLibraries.load(path).getOrThrow()
            val repository = createFileBackedRepository(
                path = path,
                initialLibrary = persistedOriginal,
                saveLibrary = { candidate ->
                    if (candidate.servers.none { it.id == 3 }) {
                        Result.failure(IllegalStateException("The host cannot persist a catalog after C is removed"))
                    } else {
                        DesktopServerLibraries.save(path, candidate)
                    }
                },
                publishLibrary = { published -> publicationHistory += published },
            )
            val originalVisibleServers = repository.servers.value
            val failure = runCatching {
                repository.updateCatalog { current ->
                    current.copy(
                        servers = listOf(
                            current.servers.single { it.id == 1 },
                            serverRecord(4, "d.example"),
                        ),
                        selectedServerId = 4,
                    )
                }
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals(
                persistedOriginal.observableContent(),
                DesktopServerLibraries.load(path).getOrThrow().observableContent(),
            )
            assertEquals(originalVisibleServers, repository.servers.value)
            assertEquals(emptyList(), publicationHistory)
        } finally {
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun transformErrorsInvalidServerIdsAndUnknownSelectionAreRejectedBeforePublication() = runBlocking {
        val ordinary = serverLibrary(
            selectedServerId = 42,
            records = listOf(serverRecord(42, "visible.example")),
        )
        val cases = listOf(
            RejectedMutation("transform exception", ordinary) { repository ->
                repository.updateCatalog { throw IllegalStateException("transform failed") }
            },
            RejectedMutation("duplicate server IDs", ordinary) { repository ->
                repository.updateCatalog { current ->
                    val visible = current.servers.single()
                    current.copy(servers = listOf(visible, visible))
                }
            },
            RejectedMutation("nonpositive server ID", ordinary) { repository ->
                repository.updateCatalog { current ->
                    current.copy(servers = listOf(current.servers.single().copy(id = 0)))
                }
            },
            RejectedMutation("unknown selection", ordinary) { repository ->
                repository.select(404)
            },
        )

        cases.forEach { case -> assertRejectedWithoutPublication(case) }
    }

    @Test
    fun nonpositiveNextServerIdIsRejectedBeforePublication() = runBlocking {
        val initial = serverLibrary(
            selectedServerId = 42,
            records = listOf(serverRecord(42, "visible.example")),
        )

        assertRejectedWithoutPublication(
            RejectedMutation("nonpositive next server ID", initial) { repository ->
                repository.updateCatalog { current -> current.copy(nextServerId = 0) }
            },
        )
    }

    @Test
    fun disabledServerIsRejectedBeforePublication() = runBlocking {
        val initial = serverLibrary(
            selectedServerId = 42,
            records = listOf(serverRecord(42, "visible.example")),
        )

        assertRejectedWithoutPublication(
            RejectedMutation("disabled server", initial) { repository ->
                repository.updateCatalog { current ->
                    current.copy(servers = current.servers + serverRecord(55, "disabled.example", enabled = false))
                }
            },
        )
    }

    @Test
    fun transformedRecordCannotReplaceAnOpaqueRecordWithTheSameId() = runBlocking {
        val visibleServer = serverRecord(42, "visible.example")
        val initial = serverLibrary(
            selectedServerId = 42,
            records = listOf(visibleServer),
        ).copy(servers = listOf(
            visibleServer.toStoredServer(),
            DesktopStoredProxyServer(
                id = 99,
                serverJson = "opaque payload that this version cannot decode",
                subscriptionId = 17,
            ),
        ))

        assertRejectedWithoutPublication(
            RejectedMutation("opaque record ID collision", initial) { repository ->
                repository.updateCatalog { current ->
                    current.copy(servers = current.servers + serverRecord(99, "replacement.example"))
                }
            },
        )
    }

    @Test
    fun identityCatalogUpdateKeepsOpaquePayloadAndVisibleDecodedServers() = runBlocking {
        val valid = serverRecord(42, "visible.example", subscriptionId = 4)
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "opaque payload that this version cannot decode",
            subscriptionId = 17,
        )
        val fixture = RepositoryFixture(
            DesktopServerLibrary(
                selectedServerId = 42,
                servers = listOf(valid.toStoredServer(), opaque),
            ),
        )

        fixture.repository.updateCatalog { it }

        assertEquals(opaque, fixture.persistedLibrary.servers.single { it.id == 99 })
        assertEquals(listOf(valid), fixture.repository.servers.value)
    }

    @Test
    fun identityCatalogUpdatePreservesSelectionOfOpaqueRawRecord() = runBlocking {
        val visible = serverRecord(42, "visible.example")
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "opaque payload that this version cannot decode",
            subscriptionId = 17,
        )
        val fixture = RepositoryFixture(
            DesktopServerLibrary(
                selectedServerId = 99,
                servers = listOf(visible.toStoredServer(), opaque),
            ),
        )

        fixture.repository.updateCatalog { it }

        assertEquals(99, fixture.persistedLibrary.selectedServerId)
        assertEquals(opaque, fixture.persistedLibrary.servers.single { it.id == 99 })
        assertEquals(listOf(visible), fixture.repository.servers.value)
    }

    @Test
    fun upsertingVisibleServerPreservesSelectionOfOpaqueRawRecord() = runBlocking {
        val visible = serverRecord(42, "visible.example")
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "opaque payload that this version cannot decode",
            subscriptionId = 17,
        )
        val fixture = RepositoryFixture(
            DesktopServerLibrary(
                selectedServerId = 99,
                servers = listOf(visible.toStoredServer(), opaque),
            ),
        )

        fixture.repository.upsert(serverRecord(42, "updated.example"))

        assertEquals(99, fixture.persistedLibrary.selectedServerId)
        assertEquals(opaque, fixture.persistedLibrary.servers.single { it.id == 99 })
        assertEquals("updated.example", fixture.repository.servers.value.single().server.let { (it as HTTP).server })
    }

    @Test
    fun replacingVisibleCatalogPreservesInterleavedOpaquePayloadAndVisibleOrder() = runBlocking {
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "opaque payload that this version cannot decode",
            subscriptionId = 17,
        )
        val initial = DesktopServerLibrary(
            selectedServerId = 42,
            servers = listOf(
                serverRecord(42, "old-first.example").toStoredServer(),
                opaque,
                serverRecord(9, "old-second.example").toStoredServer(),
            ),
        )
        val fixture = RepositoryFixture(initial)
        val replacement = listOf(
            serverRecord(9, "updated-second.example"),
            serverRecord(42, "updated-first.example"),
        )

        fixture.repository.updateCatalog { current -> current.copy(servers = replacement) }

        assertEquals(opaque, fixture.persistedLibrary.servers.single { it.id == 99 })
        assertEquals(listOf(9, 42), fixture.repository.servers.value.map(ProxyServerRecord::id))
        val persistedIds = fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::id)
        assertEquals(listOf(9, 42), persistedIds.filterNot { it == opaque.id })
        assertEquals(3, persistedIds.size)
        assertEquals(3, persistedIds.distinct().size)
        assertEquals(setOf(9, 42, 99), persistedIds.toSet())
    }

    @Test
    fun catalogIdAllocatorSurvivesReloadAndDoesNotRewindAfterDeletion() {
        val directory = Files.createTempDirectory("skipi-proxy-server-catalog-")
        val path = directory.resolve("servers.json")
        try {
            val initial = serverLibrary(
                selectedServerId = 42,
                records = listOf(serverRecord(42, "existing.example")),
            )
            DesktopServerLibraries.save(path, initial).getOrThrow()
            createFileBackedRepository(path, DesktopServerLibraries.load(path).getOrThrow()).let { repository ->
                runBlocking {
                    repository.updateCatalog { current -> current.copy(nextServerId = 100) }
                }
            }

            val afterHighWaterUpdate = DesktopServerLibraries.load(path).getOrThrow()
            val reloadedRepository = createFileBackedRepository(path, afterHighWaterUpdate)
            var nextIdSeenAfterReload: Int? = null
            runBlocking {
                reloadedRepository.updateCatalog { current ->
                    nextIdSeenAfterReload = current.nextServerId
                    current
                }
            }

            val firstAddition = DesktopServerLibraries.add(afterHighWaterUpdate, HTTP(server = "allocated.example"))
            DesktopServerLibraries.save(path, firstAddition).getOrThrow()
            val allocatedId = firstAddition.servers.last().id
            runBlocking {
                createFileBackedRepository(path, DesktopServerLibraries.load(path).getOrThrow())
                    .remove(allocatedId)
            }
            val afterDeletion = DesktopServerLibraries.load(path).getOrThrow()
            val nextAddition = DesktopServerLibraries.add(afterDeletion, HTTP(server = "after-delete.example"))
            val nextIdAfterDeletion = nextAddition.servers.last().id

            assertEquals(listOf(100, 100, 101), listOf(nextIdSeenAfterReload, allocatedId, nextIdAfterDeletion))
        } finally {
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun removingSelectedServerFallsBackToFirstRemainingAndEmptyCatalogStaysUnselected() = runBlocking {
        val fixture = RepositoryFixture(
            serverLibrary(
                selectedServerId = 9,
                records = listOf(
                    serverRecord(42, "first.example"),
                    serverRecord(9, "selected.example"),
                ),
            ),
        )

        fixture.repository.remove(9)

        assertEquals(42, fixture.persistedLibrary.selectedServerId)
        assertEquals(listOf(42), fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::id))

        fixture.repository.remove(42)

        assertEquals(null, fixture.persistedLibrary.selectedServerId)
        assertEquals(emptyList(), fixture.persistedLibrary.servers)
    }

    private class RepositoryFixture(
        initialLibrary: DesktopServerLibrary,
        private val rejectSave: (DesktopServerLibrary) -> Throwable? = { null },
    ) {
        var persistedLibrary: DesktopServerLibrary = initialLibrary
            private set
        val publicationHistory = mutableListOf<DesktopServerLibrary>()
        val repository = DesktopProxyServerRepository(
            initialLibrary = initialLibrary,
            readLibrary = { persistedLibrary },
            saveLibrary = { candidate ->
                val failure = rejectSave(candidate)
                if (failure == null) {
                    persistedLibrary = candidate
                    Result.success(Unit)
                } else {
                    Result.failure(failure)
                }
            },
            publishLibrary = { published ->
                persistedLibrary = published
                publicationHistory += published
            },
        )

        fun replacePersistedExternally(library: DesktopServerLibrary) {
            persistedLibrary = library
        }
    }

    private data class RejectedMutation(
        val name: String,
        val initialLibrary: DesktopServerLibrary,
        val mutate: suspend (DesktopProxyServerRepository) -> Unit,
    )

    private suspend fun assertRejectedWithoutPublication(case: RejectedMutation) {
        val fixture = RepositoryFixture(case.initialLibrary)
        val originalVisibleServers = fixture.repository.servers.value
        val failure = runCatching { case.mutate(fixture.repository) }.exceptionOrNull()

        assertNotNull(failure, "${case.name} should fail")
        assertEquals(case.initialLibrary, fixture.persistedLibrary, case.name)
        assertEquals(originalVisibleServers, fixture.repository.servers.value, case.name)
        assertEquals(emptyList(), fixture.publicationHistory, case.name)
    }

    private fun createFileBackedRepository(
        path: java.nio.file.Path,
        initialLibrary: DesktopServerLibrary,
        saveLibrary: (DesktopServerLibrary) -> Result<Unit> = { library ->
            DesktopServerLibraries.save(path, library)
        },
        publishLibrary: (DesktopServerLibrary) -> Unit = {},
    ): DesktopProxyServerRepository = DesktopProxyServerRepository(
        initialLibrary = initialLibrary,
        readLibrary = { DesktopServerLibraries.load(path).getOrThrow() },
        saveLibrary = saveLibrary,
        publishLibrary = publishLibrary,
    )

    private fun serverLibrary(
        selectedServerId: Int?,
        records: List<ProxyServerRecord>,
    ): DesktopServerLibrary = DesktopServerLibrary(
        selectedServerId = selectedServerId,
        servers = records.map { it.toStoredServer() },
    )

    private fun serverRecord(
        id: Int,
        server: String,
        subscriptionId: Int? = null,
        enabled: Boolean = true,
    ): ProxyServerRecord = ProxyServerRecord(
        id = id,
        server = HTTP(remarks = "Server $id", server = server),
        sourceSubscriptionId = subscriptionId,
        enabled = enabled,
    )

    private fun ProxyServerRecord.toStoredServer(): DesktopStoredProxyServer = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
        subscriptionId = sourceSubscriptionId,
    )

    private fun DesktopServerLibrary.observableContent(): Pair<Int?, List<DesktopStoredProxyServer>> =
        selectedServerId to servers
}
