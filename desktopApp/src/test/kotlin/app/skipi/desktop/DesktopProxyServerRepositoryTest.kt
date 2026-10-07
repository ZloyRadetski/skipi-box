// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.ProxyServerRecord
import app.skipi.app.proxy.createProxyServerRecord
import app.skipi.app.proxy.editProxyServerRecord
import app.skipi.app.proxy.importProxyServerRecordBatch
import features.proxy.server.model.HTTP
import features.proxy.server.model.encodePersistedProxyServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
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
    fun sharedCreatePrependsAndPreservesAnExistingSelection() = runBlocking {
        val initial = serverLibrary(
            selectedServerId = 9,
            records = listOf(
                serverRecord(42, "first.example", subscriptionId = 4),
                serverRecord(9, "selected.example", subscriptionId = 8),
            ),
        ).copy(nextServerId = 50)
        val fixture = RepositoryFixture(initial)

        fixture.repository.updateCatalog { current ->
            createProxyServerRecord(
                catalog = current,
                server = HTTP(remarks = "Created", server = "created.example"),
                sourceSubscriptionId = 17,
            )
        }

        assertEquals(listOf(50, 42, 9), fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::id))
        assertEquals(listOf(50, 42, 9), fixture.repository.servers.value.map(ProxyServerRecord::id))
        assertEquals(9, fixture.persistedLibrary.selectedServerId)
        assertEquals(51, fixture.persistedLibrary.nextServerId)
        assertEquals(17, fixture.persistedLibrary.servers.first().subscriptionId)
        assertEquals("created.example", fixture.repository.servers.value.first().server.let { (it as HTTP).server })
    }

    @Test
    fun sharedCreatePreservesTheSelectionOfAnOpaqueRawRecord() = runBlocking {
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "future protocol payload; retain these exact bytes",
            subscriptionId = 17,
        )
        val initial = DesktopServerLibrary(
            selectedServerId = 99,
            servers = listOf(serverRecord(42, "visible.example").toStoredServer(), opaque),
            nextServerId = 100,
        )
        val fixture = RepositoryFixture(initial)

        fixture.repository.updateCatalog { current ->
            createProxyServerRecord(
                catalog = current,
                server = HTTP(remarks = "Created", server = "created.example"),
                sourceSubscriptionId = null,
            )
        }

        assertEquals(99, fixture.persistedLibrary.selectedServerId)
        assertEquals(opaque, fixture.persistedLibrary.servers.single { it.id == 99 })
        assertEquals(listOf(100, 42), fixture.repository.servers.value.map(ProxyServerRecord::id))
        assertEquals(101, fixture.persistedLibrary.nextServerId)
    }

    @Test
    fun sharedManualBatchUsesLatestHighWaterPrependsInOrderAndKeepsExistingSelection() = runBlocking {
        val afterDeletingHighestId = DesktopServerLibraries.remove(
            library = DesktopServerLibraries.add(
                library = DesktopServerLibraries.add(
                    library = DesktopServerLibrary(),
                    server = HTTP(remarks = "Remaining", server = "remaining.example"),
                ),
                server = HTTP(remarks = "Deleted highest", server = "deleted.example"),
            ),
            serverId = 2,
        )
        val fixture = RepositoryFixture(afterDeletingHighestId)
        val imported = listOf(
            HTTP(remarks = "Imported one", server = "import-one.example"),
            HTTP(remarks = "Imported two", server = "import-two.example"),
        )

        fixture.repository.updateCatalog { current ->
            importProxyServerRecordBatch(
                catalog = current,
                importedServers = imported,
            )
        }

        val result = fixture.persistedLibrary
        assertEquals(listOf(3, 4, 1), result.servers.map(DesktopStoredProxyServer::id))
        assertEquals(listOf(3, 4, 1), fixture.repository.servers.value.map(ProxyServerRecord::id))
        assertEquals(listOf("Imported one", "Imported two"), result.servers.take(2).map { stored ->
            stored.decode().getOrThrow().getInfo().remarks
        })
        assertTrue(result.servers.take(2).all { it.subscriptionId == null })
        assertEquals(1, result.servers.last().id)
        assertEquals(1, result.selectedServerId)
        assertEquals(5, result.nextServerId)
    }

    @Test
    fun sharedEditReplacesTheServerInPlaceAndPreservesItsAssociation() = runBlocking {
        val initial = serverLibrary(
            selectedServerId = 9,
            records = listOf(
                serverRecord(42, "first.example", subscriptionId = 4),
                serverRecord(9, "old.example", subscriptionId = 8),
                serverRecord(7, "last.example", subscriptionId = 7),
            ),
        ).copy(nextServerId = 50)
        val fixture = RepositoryFixture(initial)

        fixture.repository.updateCatalog { current ->
            editProxyServerRecord(
                catalog = current,
                serverId = 9,
                server = HTTP(remarks = "Edited", server = "edited.example"),
                sourceSubscriptionId = 99,
            )
        }

        assertEquals(listOf(42, 9, 7), fixture.persistedLibrary.servers.map(DesktopStoredProxyServer::id))
        assertEquals(listOf(42, 9, 7), fixture.repository.servers.value.map(ProxyServerRecord::id))
        assertEquals(8, fixture.persistedLibrary.servers[1].subscriptionId)
        assertEquals(9, fixture.persistedLibrary.selectedServerId)
        assertEquals(50, fixture.persistedLibrary.nextServerId)
        assertEquals("edited.example", fixture.repository.servers.value[1].server.let { (it as HTTP).server })
    }

    @Test
    fun failedSharedCreateLeavesThePreviousCatalogAndPublicationHistoryIntact() = runBlocking {
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
                    if (candidate.servers.any { it.id == 4 }) {
                        Result.failure(IllegalStateException("The host cannot persist the new server"))
                    } else {
                        DesktopServerLibraries.save(path, candidate)
                    }
                },
                publishLibrary = { published -> publicationHistory += published },
            )
            val originalVisibleServers = repository.servers.value
            val failure = runCatching {
                repository.updateCatalog { current ->
                    createProxyServerRecord(
                        catalog = current,
                        server = HTTP(remarks = "New", server = "new.example"),
                        sourceSubscriptionId = null,
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
    fun concurrentSharedCreatesAllocateDistinctIdsFromSerializedCatalogSnapshots() {
        val initial = DesktopServerLibrary(
            selectedServerId = 8,
            servers = listOf(serverRecord(8, "existing.example").toStoredServer()),
            nextServerId = 10,
        )
        val persistedLibrary = AtomicReference(initial)
        val saveCount = AtomicInteger()
        val firstSaveEntered = CountDownLatch(1)
        val allowFirstSaveToFinish = CountDownLatch(1)
        val publications = CopyOnWriteArrayList<DesktopServerLibrary>()
        val repository = DesktopProxyServerRepository(
            initialLibrary = initial,
            readLibrary = { persistedLibrary.get() },
            saveLibrary = { candidate ->
                if (saveCount.incrementAndGet() == 1) {
                    firstSaveEntered.countDown()
                    check(allowFirstSaveToFinish.await(10, TimeUnit.SECONDS)) {
                        "Timed out waiting to release the first catalog save"
                    }
                }
                persistedLibrary.set(candidate)
                Result.success(Unit)
            },
            publishLibrary = { published -> publications.add(published) },
        )
        val executor = Executors.newFixedThreadPool(2)

        try {
            val firstCreate = executor.submit {
                runBlocking {
                    repository.updateCatalog { current ->
                        createProxyServerRecord(
                            catalog = current,
                            server = HTTP(server = "concurrent-first.example"),
                            sourceSubscriptionId = null,
                        )
                    }
                }
            }
            assertTrue(firstSaveEntered.await(10, TimeUnit.SECONDS))

            val secondStarted = CountDownLatch(1)
            val secondCreate = executor.submit {
                secondStarted.countDown()
                runBlocking {
                    repository.updateCatalog { current ->
                        createProxyServerRecord(
                            catalog = current,
                            server = HTTP(server = "concurrent-second.example"),
                            sourceSubscriptionId = null,
                        )
                    }
                }
            }
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
            allowFirstSaveToFinish.countDown()
            firstCreate.get(10, TimeUnit.SECONDS)
            secondCreate.get(10, TimeUnit.SECONDS)

            val finalLibrary = persistedLibrary.get()
            assertEquals(listOf(11, 10, 8), finalLibrary.servers.map(DesktopStoredProxyServer::id))
            assertEquals(listOf(11, 10, 8), repository.servers.value.map(ProxyServerRecord::id))
            assertEquals(12, finalLibrary.nextServerId)
            assertEquals(8, finalLibrary.selectedServerId)
            assertEquals(2, publications.size)
            assertEquals(listOf(10, 8), publications.first().servers.map(DesktopStoredProxyServer::id))
            assertEquals(listOf(11, 10, 8), publications.last().servers.map(DesktopStoredProxyServer::id))
            assertTrue(finalLibrary.servers.all { it.id > 0 })
        } finally {
            allowFirstSaveToFinish.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun concurrentManualBatchCreateAndSubscriptionRefreshUseLatestLibrary() {
        val opaque = DesktopStoredProxyServer(
            id = 99,
            serverJson = "future protocol payload; keep these bytes",
            subscriptionId = 70,
        )
        val initial = DesktopServerLibrary(
            selectedServerId = opaque.id,
            servers = listOf(
                serverRecord(1, "existing-manual.example").toStoredServer(),
                serverRecord(7, "old-subscription.example", subscriptionId = 10).toStoredServer(),
                opaque,
            ),
            nextServerId = 100,
        )
        val persistedLibrary = AtomicReference(initial)
        val saveCount = AtomicInteger()
        val firstSaveEntered = CountDownLatch(1)
        val allowFirstSaveToFinish = CountDownLatch(1)
        val publications = CopyOnWriteArrayList<DesktopServerLibrary>()
        val repository = DesktopProxyServerRepository(
            initialLibrary = initial,
            readLibrary = { persistedLibrary.get() },
            saveLibrary = { candidate ->
                if (saveCount.incrementAndGet() == 1) {
                    firstSaveEntered.countDown()
                    check(allowFirstSaveToFinish.await(10, TimeUnit.SECONDS)) {
                        "Timed out waiting to release the first catalog save"
                    }
                }
                persistedLibrary.set(candidate)
                Result.success(Unit)
            },
            publishLibrary = { published ->
                persistedLibrary.set(published)
                publications.add(published)
            },
        )
        val executor = Executors.newFixedThreadPool(3)

        try {
            val batchImport = executor.submit {
                runBlocking {
                    repository.updateCatalog { current ->
                        importProxyServerRecordBatch(
                            catalog = current,
                            importedServers = listOf(
                                HTTP(remarks = "manual batch first", server = "batch-first.example"),
                                HTTP(remarks = "manual batch second", server = "batch-second.example"),
                            ),
                        )
                    }
                }
            }
            assertTrue(firstSaveEntered.await(10, TimeUnit.SECONDS))

            val otherMutationsStarted = CountDownLatch(2)
            val createServer = executor.submit {
                otherMutationsStarted.countDown()
                runBlocking {
                    repository.updateCatalog { current ->
                        createProxyServerRecord(
                            catalog = current,
                            server = HTTP(remarks = "created while importing", server = "created.example"),
                            sourceSubscriptionId = null,
                        )
                    }
                }
            }
            val refreshSubscription = executor.submit {
                otherMutationsStarted.countDown()
                runBlocking {
                    repository.updateLibrary { current ->
                        DesktopServerLibraries.replaceSubscriptionServers(
                            library = current,
                            subscriptionId = 10,
                            servers = listOf(
                                HTTP(remarks = "refreshed subscription", server = "refreshed.example"),
                            ),
                        )
                    }
                }
            }
            assertTrue(otherMutationsStarted.await(10, TimeUnit.SECONDS))
            allowFirstSaveToFinish.countDown()
            batchImport.get(10, TimeUnit.SECONDS)
            createServer.get(10, TimeUnit.SECONDS)
            refreshSubscription.get(10, TimeUnit.SECONDS)

            val finalLibrary = persistedLibrary.get()
            val decodedServers = finalLibrary.servers.mapNotNull { stored ->
                stored.decode().getOrNull()?.let { server -> stored to server.getInfo().remarks }
            }
            val batchRows = decodedServers.filter { (_, remarks) -> remarks.startsWith("manual batch") }

            assertEquals(99, finalLibrary.selectedServerId)
            assertEquals(opaque, finalLibrary.servers.single { it.id == opaque.id })
            assertEquals(
                listOf("manual batch first", "manual batch second"),
                batchRows.map { (_, remarks) -> remarks },
            )
            assertEquals(listOf(null, null), batchRows.map { (stored, _) -> stored.subscriptionId })
            assertEquals(1, decodedServers.count { (_, remarks) -> remarks == "created while importing" })
            assertEquals(
                listOf("refreshed subscription"),
                decodedServers.filter { (stored, _) -> stored.subscriptionId == 10 }
                    .map { (_, remarks) -> remarks },
            )
            assertEquals(finalLibrary.servers.size, finalLibrary.servers.map(DesktopStoredProxyServer::id).distinct().size)
            assertTrue(finalLibrary.nextServerId > finalLibrary.servers.maxOf(DesktopStoredProxyServer::id))
            assertEquals(3, publications.size)
            assertEquals(finalLibrary, publications.last())
            assertEquals(
                decodedServers.map { (stored, _) -> stored.id },
                repository.servers.value.map(ProxyServerRecord::id),
            )
        } finally {
            allowFirstSaveToFinish.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun failedManualBatchSaveLeavesPersistedAndPublishedOpaqueCatalogUnchanged() = runBlocking {
        val directory = Files.createTempDirectory("skipi-proxy-server-batch-save-failure-")
        val path = directory.resolve("servers.json")
        val opaque = DesktopStoredProxyServer(
            id = 999,
            serverJson = "future protocol payload; preserve exactly",
            subscriptionId = 70,
        )
        val original = DesktopServerLibrary(
            selectedServerId = opaque.id,
            servers = listOf(
                serverRecord(42, "visible.example", subscriptionId = 4).toStoredServer(),
                opaque,
            ),
            nextServerId = 10,
        )
        val publicationHistory = mutableListOf<DesktopServerLibrary>()
        try {
            DesktopServerLibraries.save(path, original).getOrThrow()
            val persistedOriginal = DesktopServerLibraries.load(path).getOrThrow()
            val repository = createFileBackedRepository(
                path = path,
                initialLibrary = persistedOriginal,
                saveLibrary = { candidate ->
                    if (candidate.servers.any { it.id == 1000 }) {
                        Result.failure(IllegalStateException("The host cannot persist the imported batch"))
                    } else {
                        DesktopServerLibraries.save(path, candidate)
                    }
                },
                publishLibrary = { published -> publicationHistory += published },
            )
            val originalVisibleServers = repository.servers.value

            val failure = runCatching {
                repository.updateCatalog { current ->
                    importProxyServerRecordBatch(
                        catalog = current,
                        importedServers = listOf(
                            HTTP(remarks = "failed first", server = "failed-first.example"),
                            HTTP(remarks = "failed second", server = "failed-second.example"),
                        ),
                    )
                }
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertEquals(
                persistedOriginal,
                DesktopServerLibraries.load(path).getOrThrow(),
            )
            assertEquals(opaque, DesktopServerLibraries.load(path).getOrThrow().servers.single { it.id == opaque.id })
            assertEquals(originalVisibleServers, repository.servers.value)
            assertEquals(emptyList(), publicationHistory)
        } finally {
            Files.deleteIfExists(path.resolveSibling("${path.fileName}.tmp"))
            Files.deleteIfExists(path)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun libraryTransformPersistsTheExactSubscriptionReplacementWithOpaqueRows() {
        val replacedOpaque = DesktopStoredProxyServer(
            id = 7,
            serverJson = "future target protocol payload; remove with this subscription group",
            subscriptionId = 10,
        )
        val unrelatedOpaque = DesktopStoredProxyServer(
            id = 40,
            serverJson = "future unrelated protocol payload; keep these exact bytes",
            subscriptionId = 90,
        )
        val initial = DesktopServerLibrary(
            selectedServerId = 8,
            servers = listOf(
                serverRecord(2, "first-manual.example").toStoredServer(),
                replacedOpaque,
                serverRecord(8, "old-target.example", subscriptionId = 10).toStoredServer(),
                unrelatedOpaque,
                serverRecord(1, "last-manual.example").toStoredServer(),
            ),
            nextServerId = 10,
        )
        val incoming = listOf(
            HTTP(remarks = "new target first", server = "new-first.example"),
            HTTP(remarks = "new target second", server = "new-second.example"),
        )
        val expected = DesktopServerLibraries.replaceSubscriptionServers(
            library = initial,
            subscriptionId = 10,
            servers = incoming,
        )
        var currentLibrary = initial
        val publications = mutableListOf<DesktopServerLibrary>()
        val repository = DesktopProxyServerRepository(
            initialLibrary = initial,
            readLibrary = { currentLibrary },
            saveLibrary = { candidate ->
                currentLibrary = candidate
                Result.success(Unit)
            },
            publishLibrary = { published ->
                currentLibrary = published
                publications += published
            },
        )

        val actual = repository.updateLibrary { latest ->
            DesktopServerLibraries.replaceSubscriptionServers(
                library = latest,
                subscriptionId = 10,
                servers = incoming,
            )
        }

        assertEquals(expected, actual)
        assertEquals(expected, currentLibrary)
        assertEquals(listOf(unrelatedOpaque), actual.servers.filter { it.id == unrelatedOpaque.id })
        assertTrue(actual.servers.none { it.id == replacedOpaque.id })
        assertEquals(listOf(expected), publications)
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
    fun sharedCreateUsesOpaqueIdHighWaterAcrossReloadAndDeletion() {
        val directory = Files.createTempDirectory("skipi-proxy-server-catalog-")
        val path = directory.resolve("servers.json")
        val opaque = DesktopStoredProxyServer(
            id = 999,
            serverJson = "future protocol payload that this version cannot decode",
            subscriptionId = 17,
        )
        try {
            val initial = DesktopServerLibrary(
                selectedServerId = 42,
                servers = listOf(serverRecord(42, "existing.example").toStoredServer(), opaque),
                nextServerId = 10,
            )
            DesktopServerLibraries.save(path, initial).getOrThrow()
            val loaded = DesktopServerLibraries.load(path).getOrThrow()
            assertEquals(1000, loaded.nextServerId)
            assertEquals(opaque, loaded.servers.single { it.id == opaque.id })

            val repositoryAfterLoad = createFileBackedRepository(path, loaded)
            runBlocking {
                repositoryAfterLoad.updateCatalog { current ->
                    createProxyServerRecord(
                        catalog = current,
                        server = HTTP(server = "first-created.example"),
                        sourceSubscriptionId = null,
                    )
                }
            }
            val afterFirstCreate = DesktopServerLibraries.load(path).getOrThrow()
            assertEquals(1000, afterFirstCreate.servers.first { it.id != opaque.id }.id)
            assertEquals(1001, afterFirstCreate.nextServerId)
            assertEquals(opaque, afterFirstCreate.servers.single { it.id == opaque.id })

            runBlocking {
                createFileBackedRepository(path, afterFirstCreate).remove(1000)
            }
            val afterDeletion = DesktopServerLibraries.load(path).getOrThrow()
            assertEquals(1001, afterDeletion.nextServerId)
            assertEquals(opaque, afterDeletion.servers.single { it.id == opaque.id })

            runBlocking {
                createFileBackedRepository(path, afterDeletion).updateCatalog { current ->
                    createProxyServerRecord(
                        catalog = current,
                        server = HTTP(server = "after-delete.example"),
                        sourceSubscriptionId = null,
                    )
                }
            }
            val afterSecondCreate = DesktopServerLibraries.load(path).getOrThrow()
            assertEquals(1001, afterSecondCreate.servers.first { it.id != opaque.id }.id)
            assertEquals(1002, afterSecondCreate.nextServerId)
            assertEquals(opaque, afterSecondCreate.servers.single { it.id == opaque.id })
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
