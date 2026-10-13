// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.subscription

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.proxy.ProxyServerRecord
import features.proxy.server.model.ChainProxy
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SubscriptionRefreshBatchTest {
    @Test
    fun aggregateReconciliationMatchesAndroidIdsOrderReferencesSelectionAndProfiles() {
        val (snapshot, first, second) = androidBatchSnapshot()
        val result = reconcileSubscriptionRefreshBatch(
            snapshot = snapshot,
            loaded = listOf(
                loaded(first, "First refreshed", "fresh-first", "fresh-first.example.test", "Updated first profile"),
                loaded(second, "Second refreshed", "fresh-second", "fresh-second.example.test", "Updated second profile"),
            ),
            refreshedAtMillis = 1_100L,
        )

        assertTrue(result.applicable)
        assertEquals(listOf(first.id, second.id), result.appliedSubscriptionIds)
        assertEquals(
            listOf(
                Triple(102, first.id, "First refreshed"),
                Triple(8, second.id, "Second refreshed"),
                Triple(33, 90, "Manual"),
                Triple(100, -2, "Balancer"),
                Triple(101, -2, "Chain"),
            ),
            result.snapshot.servers.map { row ->
                Triple(row.id, row.groupId, row.server.getInfo().remarks)
            },
        )
        assertEquals(103, result.snapshot.nextServerId)
        assertEquals(33, result.snapshot.selectedServerId)

        val strategy = result.snapshot.servers.single { it.id == 100 }.server as StrategyGroup
        val chain = result.snapshot.servers.single { it.id == 101 }.server as ChainProxy
        assertEquals(listOf(102, 8), strategy.proxyServerIds)
        assertEquals(8, strategy.selectedMemberId)
        assertEquals(listOf(102, 8), chain.proxyServerIds)

        assertEquals(
            listOf("Updated first profile", "Updated second profile"),
            result.snapshot.subscriptions.map(SubscriptionRecord::title),
        )
        assertEquals(listOf(first.id, second.id), result.snapshot.subscriptions.map { it.id })
        assertEquals(listOf(1_100L, 1_100L), result.snapshot.subscriptions.map { it.lastUpdatedAtMillis })
        assertEquals(listOf("profile://4", "profile://5"), result.resolvedEmbeddedConfigs.map { it.sourceUrl })
    }

    @Test
    fun aggregateReconciliationFiltersStaleAndDeletedSourcesAgainstLatestState() {
        val (snapshot, requestedFirst, requestedSecond) = androidBatchSnapshot()
        val latestFirst = requestedFirst.copy(
            url = "https://first-edited.example.test/list",
            title = "Renamed while fetching",
            metadata = SubscriptionMetadata(profileDescription = "Latest first description"),
        )
        val latestSecond = requestedSecond.copy(
            title = "Latest second title",
            autoOverrideRules = false,
            metadata = SubscriptionMetadata(
                profileDescription = "Keep latest description",
                announce = "Keep latest announcement",
                trafficUploadBytes = 80L,
                trafficDownloadBytes = 120L,
                trafficTotalBytes = 500L,
                trafficExpireAtSeconds = 1_800L,
            ),
            lastUpdatedAtMillis = 700L,
        )
        val deleted = subscription(id = 6, url = "https://deleted.example.test/list")
        val latestManual = ProxyServerRecord(
            id = 70,
            groupId = 90,
            server = VLESS(remarks = "Latest manual", id = "manual-latest", server = "manual.example.test", port = "443"),
            latency = "9 ms",
        )
        val latest = snapshot.copy(
            subscriptions = listOf(latestFirst, latestSecond, subscription(id = 9)),
            servers = listOf(
                ProxyServerRecord(7, requestedFirst.id, HTTP(remarks = "Old first", server = "old-first.example.test", port = "443")),
                ProxyServerRecord(8, requestedSecond.id, vless("old-second.example.test", "Old second"), latency = "30 ms"),
                latestManual,
            ),
            nextServerId = 100,
            selectedServerId = 70,
        )

        val result = reconcileSubscriptionRefreshBatch(
            snapshot = latest,
            loaded = listOf(
                loaded(requestedFirst, "Stale first", "stale-first", "stale-first.example.test", "Stale first title"),
                loaded(requestedSecond, "Current second", "fresh-second", "old-second.example.test", "Updated second title"),
                loaded(deleted, "Deleted", "deleted", "deleted.example.test", "Deleted title"),
            ),
            refreshedAtMillis = 1_200L,
        )

        assertTrue(result.applicable)
        assertEquals(listOf(requestedSecond.id), result.appliedSubscriptionIds)
        assertEquals(listOf("profile://5"), result.resolvedEmbeddedConfigs.map { it.sourceUrl })
        assertTrue(result.resolvedEmbeddedConfigs.none { it.sourceUrl == "profile://4" || it.sourceUrl == "profile://6" })
        assertEquals("Renamed while fetching", result.snapshot.subscriptions.first { it.id == 4 }.title)
        assertEquals("Latest first description", result.snapshot.subscriptions.first { it.id == 4 }.metadata?.profileDescription)
        assertEquals("Updated second title", result.snapshot.subscriptions.first { it.id == 5 }.title)
        assertEquals("Keep latest description", result.snapshot.subscriptions.first { it.id == 5 }.metadata?.profileDescription)
        assertEquals("Keep latest announcement", result.snapshot.subscriptions.first { it.id == 5 }.metadata?.announce)
        assertEquals(80L, result.snapshot.subscriptions.first { it.id == 5 }.metadata?.trafficUploadBytes)
        assertEquals(1_200L, result.snapshot.subscriptions.first { it.id == 5 }.lastUpdatedAtMillis)
        assertEquals("Latest manual", result.snapshot.servers.first { it.id == 70 }.server.getInfo().remarks)
        assertEquals(listOf(70), result.snapshot.servers.filter { it.groupId == 90 }.map { it.id })
        assertEquals("Old first", result.snapshot.servers.first { it.id == 7 }.server.getInfo().remarks)
        assertEquals("Current second", result.snapshot.servers.first { it.groupId == 5 }.server.getInfo().remarks)
        assertEquals("https://first-edited.example.test/list", result.snapshot.subscriptions.first { it.id == 4 }.url)
        assertEquals("Latest first description", result.snapshot.subscriptions.first { it.id == 4 }.metadata?.profileDescription)
        assertEquals(listOf(4, 5, 9), result.snapshot.subscriptions.map { it.id })
    }

    @Test
    fun batchCommitUsesOneAggregateCatalogThenOneMetadataAndOrderedProfileBoundary() = runTest {
        val (snapshot, first, second) = androidBatchSnapshot()
        val port = RecordingBatchCommitPort(snapshot)

        val result = commitSubscriptionRefreshBatch(
            loaded = listOf(
                loaded(first, "First refreshed", "fresh-first", "fresh-first.example.test", "Updated first profile"),
                loaded(second, "Second refreshed", "fresh-second", "fresh-second.example.test", "Updated second profile"),
            ),
            refreshedAtMillis = 1_100L,
            singlePort = unusedSingleCommitPort(),
            batchPort = port,
        )

        assertTrue(result.applicable)
        assertEquals(listOf(first.id, second.id), result.appliedSubscriptionIds)
        assertEquals(listOf("servers", "metadata-and-profiles"), port.events)
        assertEquals(1, port.serverWrites)
        assertEquals(1, port.metadataWrites)
        assertEquals(listOf("profile://4", "profile://5"), port.appliedProfileSources)
        assertEquals(103, port.latest.nextServerId)
        assertEquals(33, port.latest.selectedServerId)
        assertEquals(listOf("Updated first profile", "Updated second profile"), port.latest.subscriptions.map { it.title })
    }

    @Test
    fun aggregateCommitFailureAfterCatalogWritePropagatesAndLeavesCatalogApplied() = runTest {
        val (snapshot, first, second) = androidBatchSnapshot()
        val port = RecordingBatchCommitPort(snapshot).apply {
            failMetadataWrite = true
        }

        val failure = assertFailsWith<IllegalStateException> {
            commitSubscriptionRefreshBatch(
                loaded = listOf(
                    loaded(first, "First catalog write", "fresh-first", "fresh-first.example.test", "First metadata"),
                    loaded(second, "Second catalog write", "fresh-second", "fresh-second.example.test", "Second metadata"),
                ),
                refreshedAtMillis = 1_300L,
                singlePort = unusedSingleCommitPort(),
                batchPort = port,
            )
        }

        assertEquals("injected aggregate metadata/profile write failure", failure.message)
        assertEquals(listOf("servers", "metadata-and-profiles"), port.events)
        assertEquals(listOf("First catalog write", "Second catalog write"), port.latest.servers.take(2).map { it.server.getInfo().remarks })
        assertEquals(listOf("Stored first", "Stored second"), port.latest.subscriptions.map { it.title })
        assertEquals(emptyList(), port.appliedProfileSources)
    }

    @Test
    fun aggregateCatalogWriteFailureStopsBeforeMetadataAndProfiles() = runTest {
        val (snapshot, first, second) = androidBatchSnapshot()
        val port = RecordingBatchCommitPort(snapshot).apply {
            failServerWrite = true
        }

        val failure = assertFailsWith<IllegalStateException> {
            commitSubscriptionRefreshBatch(
                loaded = listOf(
                    loaded(first, "First refreshed", "fresh-first", "fresh-first.example.test", "Updated first profile"),
                    loaded(second, "Second refreshed", "fresh-second", "fresh-second.example.test", "Updated second profile"),
                ),
                refreshedAtMillis = 1_100L,
                singlePort = unusedSingleCommitPort(),
                batchPort = port,
            )
        }

        assertEquals("injected aggregate catalog write failure", failure.message)
        assertEquals(listOf("servers"), port.events)
        assertEquals(1, port.serverWrites)
        assertEquals(0, port.metadataWrites)
        assertEquals(listOf("Old first", "Old second", "Manual", "Balancer", "Chain"), port.latest.servers.map { it.server.getInfo().remarks })
        assertEquals(listOf("Stored first", "Stored second"), port.latest.subscriptions.map { it.title })
        assertEquals(emptyList(), port.appliedProfileSources)
    }

    @Test
    fun batchOperationLoadsConcurrentlyKeepsAllRefreshingIdsThroughAggregateCommitAndIsolatesFetchFailure() = runTest {
        val requests = listOf(1, 2, 3)
        val started = mutableListOf<Int>()
        val releases = requests.associateWith { CompletableDeferred<Unit>() }
        val commitStarted = CompletableDeferred<List<String>>()
        val finishCommit = CompletableDeferred<Unit>()
        val commits = mutableListOf<String>()
        val operation = batchOperation(
            requests = { requests },
            loader = { id ->
                started += id
                releases.getValue(id).await()
                if (id == 2) error("fetch failed for $id")
                "loaded-$id"
            },
            commitSingle = { update, _ ->
                commits += "single:$update"
                "single"
            },
            commitBatch = { updates, _ ->
                commits += "batch:${updates.joinToString()}"
                commitStarted.complete(updates)
                finishCommit.await()
                "batch"
            },
        )

        val running = async { operation.run() }
        runCurrent()
        assertEquals(setOf(1, 2, 3), started.toSet())
        assertEquals(setOf(1, 2, 3), operation.progress.value.refreshingSubscriptionIds)
        releases.values.forEach { it.complete(Unit) }
        runCurrent()

        assertEquals(listOf("loaded-1", "loaded-3"), commitStarted.await())
        assertEquals(listOf("batch:loaded-1, loaded-3"), commits)
        assertEquals(setOf(1, 2, 3), operation.progress.value.refreshingSubscriptionIds)
        finishCommit.complete(Unit)
        val result = running.await()

        assertEquals(listOf("loaded-1", "loaded-3"), result.refresh.updates)
        assertEquals(listOf(2), result.refresh.failures.map { it.request.subscription.id })
        assertEquals("fetch failed for 2", result.refresh.failures.single().error.message)
        assertEquals("batch", result.commitResult)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
    }

    @Test
    fun oneSuccessfulLoadUsesSingleCommitPathWhileMultipleUseAggregatePath() = runTest {
        val singleCalls = mutableListOf<String>()
        val single = batchOperation(
            requests = { listOf(1, 2) },
            loader = { id -> if (id == 2) error("one request failed") else "loaded-$id" },
            commitSingle = { update, _ -> singleCalls += update; "single:$update" },
            commitBatch = { _, _ -> error("batch path must not run for one success") },
        ).run()

        val batchCalls = mutableListOf<List<String>>()
        val multiple = batchOperation(
            requests = { listOf(3, 4) },
            loader = { id -> "loaded-$id" },
            commitSingle = { _, _ -> error("single path must not run for two successes") },
            commitBatch = { updates, _ -> batchCalls += updates; "batch" },
        ).run()

        assertEquals(listOf("loaded-1"), singleCalls)
        assertEquals("single:loaded-1", single.commitResult)
        assertEquals(listOf("loaded-1"), single.refresh.updates)
        assertEquals(listOf(2), single.refresh.failures.map { it.request.subscription.id })
        assertEquals(listOf(listOf("loaded-3", "loaded-4")), batchCalls)
        assertEquals("batch", multiple.commitResult)
    }

    @Test
    fun batchEligibilityUsesTheCapturedEnabledNonblankSubscriptionsInCatalogOrder() = runTest {
        val enabled = subscription(id = 4, url = "https://enabled.example.test/list")
        val disabled = subscription(id = 5, url = "https://disabled.example.test/list").copy(enabled = false)
        val blankUrl = subscription(id = 6, url = "  ")
        val visited = mutableListOf<Int>()
        val operation = SubscriptionRefreshBatchOperation(
            captureRequests = {
                listOf(enabled, disabled, blankUrl).map(::refreshRequest)
            },
            loader = SubscriptionRefreshLoader { record ->
                visited += record.subscription.id
                record.subscription.id
            },
            refreshedAtMillis = { 1_234L },
            commitSingle = { update, _ -> update },
            commitBatch = { updates, _ -> updates },
        )

        val result = operation.run()

        assertEquals(listOf(enabled.id), visited)
        assertEquals(listOf(enabled.id), result.refresh.updates)
        assertEquals(enabled.id, result.commitResult)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
    }

    @Test
    fun disabledOnlyUrlSetHasNoEligibleFetchOrCommit() = runTest {
        val disabled = subscription(id = 5, url = "https://disabled.example.test/list").copy(enabled = false)
        val visited = mutableListOf<Int>()
        var singleCommits = 0
        var batchCommits = 0
        val operation = SubscriptionRefreshBatchOperation(
            captureRequests = { listOf(refreshRequest(disabled)) },
            loader = SubscriptionRefreshLoader { request ->
                visited += request.subscription.id
                request.subscription.id
            },
            refreshedAtMillis = { 1_234L },
            commitSingle = { _, _ -> singleCommits += 1 },
            commitBatch = { _, _ -> batchCommits += 1 },
        )

        val result = operation.run()

        assertEquals(emptyList(), visited)
        assertEquals(emptyList(), result.refresh.updates)
        assertEquals(emptyList(), result.refresh.failures)
        assertEquals(null, result.commitResult)
        assertEquals(0, singleCommits)
        assertEquals(0, batchCommits)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
    }

    @Test
    fun batchReentryWaitsForFirstRunThenCapturesFreshEligibilityInsideTheMutex() = runTest {
        var currentRequests = listOf(1)
        var captures = 0
        val firstLoadStarted = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()
        val loads = mutableListOf<Int>()
        val operation = batchOperation(
            requests = {
                captures += 1
                currentRequests
            },
            loader = { id ->
                loads += id
                if (id == 1) {
                    firstLoadStarted.complete(Unit)
                    releaseFirstLoad.await()
                }
                "loaded-$id"
            },
            commitSingle = { _, _ -> Unit },
            commitBatch = { _, _ -> Unit },
        )

        val first = async { operation.run() }
        runCurrent()
        firstLoadStarted.await()
        currentRequests = listOf(2)
        val second = async { operation.run() }
        runCurrent()

        assertEquals(1, captures)
        assertEquals(listOf(1), loads)
        assertEquals(setOf(1), operation.progress.value.refreshingSubscriptionIds)

        releaseFirstLoad.complete(Unit)
        first.await()
        runCurrent()
        second.await()

        assertEquals(2, captures)
        assertEquals(listOf(1, 2), loads)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
    }

    @Test
    fun cancellingBatchClearsProgressWithoutCommitting() = runTest {
        var committed = false
        val operation = batchOperation(
            requests = { listOf(1, 2) },
            loader = {
                awaitCancellation()
            },
            commitSingle = { _, _ -> committed = true; Unit },
            commitBatch = { _, _ -> committed = true; Unit },
        )

        val running = async { operation.run() }
        runCurrent()
        assertEquals(setOf(1, 2), operation.progress.value.refreshingSubscriptionIds)
        running.cancelAndJoin()

        assertEquals(false, committed)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
    }

    @Test
    fun aggregatePersistenceFailurePropagatesOutsideFetchFailureListAndClearsProgress() = runTest {
        val operation = batchOperation(
            requests = { listOf(1, 2) },
            loader = { id -> "loaded-$id" },
            commitSingle = { _, _ -> error("unexpected single commit") },
            commitBatch = { _, _ -> error("catalog persistence failed") },
        )

        val failure = assertFailsWith<IllegalStateException> { operation.run() }

        assertEquals("catalog persistence failed", failure.message)
        assertEquals(emptySet(), operation.progress.value.refreshingSubscriptionIds)
    }

    private fun <CommitResult> batchOperation(
        requests: suspend () -> List<Int>,
        loader: suspend (Int) -> String,
        commitSingle: suspend (String, Long) -> CommitResult,
        commitBatch: suspend (List<String>, Long) -> CommitResult,
    ) = SubscriptionRefreshBatchOperation(
        captureRequests = { requests().map { id -> refreshRequest(subscription(id = id)) } },
        loader = SubscriptionRefreshLoader { request -> loader(request.subscription.id) },
        refreshedAtMillis = { 1_234L },
        commitSingle = commitSingle,
        commitBatch = commitBatch,
    )

    private fun refreshRequest(subscription: SubscriptionRecord) = SubscriptionRefreshLoadRequest(
        subscription = subscription,
        fetchOptions = "options-${subscription.id}",
    )

    private fun androidBatchSnapshot(): Triple<SubscriptionRefreshSnapshot, SubscriptionRecord, SubscriptionRecord> {
        val first = subscription(id = 4, url = "https://subscription.example.test/first")
        val second = subscription(id = 5, url = "https://subscription.example.test/second")
        return Triple(
            SubscriptionRefreshSnapshot(
                subscriptions = listOf(first, second),
                servers = listOf(
                    ProxyServerRecord(7, first.id, HTTP(remarks = "Old first", server = "old-first.example.test", port = "443")),
                    ProxyServerRecord(8, second.id, vless("old-second.example.test", "Old second"), latency = "30 ms"),
                    ProxyServerRecord(33, 90, vless("manual.example.test", "Manual"), latency = "9 ms"),
                    ProxyServerRecord(100, -2, StrategyGroup(remarks = "Balancer", proxyServerIds = listOf(7, 8), selectedMemberId = 8)),
                    ProxyServerRecord(101, -2, ChainProxy(remarks = "Chain", proxyServerIds = listOf(7, 8))),
                ),
                nextServerId = 102,
                selectedServerId = 7,
            ),
            first,
            second,
        )
    }

    private fun loaded(
        subscription: SubscriptionRecord,
        remarks: String,
        serverId: String,
        host: String,
        profileTitle: String,
    ) = LoadedSubscriptionRefresh(
        sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
        urlCount = 1,
        servers = listOf(VLESS(remarks = remarks, id = serverId, server = host, port = "443")),
        metadata = SubscriptionMetadata(profileTitle = profileTitle),
        resolvedEmbeddedConfig = ResolvedEmbeddedSubscriptionConfig(
            content = "[General]\ndns-server = 1.1.1.1",
            sourceUrl = "profile://${subscription.id}",
            fallbackName = profileTitle,
            activate = true,
        ),
    )

    private fun subscription(id: Int, url: String = "https://subscription.example.test/$id") = SubscriptionRecord(
        id = id,
        title = "Stored ${if (id == 4) "first" else if (id == 5) "second" else id}",
        url = url,
        userAgent = "Skipi/1 Android",
        updateInterval = "6",
        hwid = "device-$id",
        ageSecretKey = "",
        updateViaProxy = false,
        autoOverrideRules = false,
        enabled = true,
        builtIn = false,
        metadata = SubscriptionMetadata(profileDescription = "Stored description $id"),
        lastUpdatedAtMillis = 100L,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )

    private fun vless(host: String, remarks: String) = VLESS(
        remarks = remarks,
        id = "uuid-$remarks",
        server = host,
        port = "443",
    )

    private fun unusedSingleCommitPort() = object : SubscriptionRefreshCommitPort {
        override suspend fun updateServers(
            reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshReconciliationResult,
        ): SubscriptionRefreshReconciliationResult = error("single commit path must not run")

        override suspend fun updateSubscription(
            subscriptionId: Int,
            transform: (SubscriptionRecord?) -> SubscriptionRecord?,
        ): SubscriptionRecord? = error("single commit path must not run")

        override suspend fun applyEmbeddedConfig(config: ResolvedEmbeddedSubscriptionConfig): Boolean =
            error("single commit path must not run")
    }

    private class RecordingBatchCommitPort(
        var latest: SubscriptionRefreshSnapshot,
    ) : SubscriptionRefreshBatchCommitPort {
        val events = mutableListOf<String>()
        val appliedProfileSources = mutableListOf<String>()
        var serverWrites = 0
        var metadataWrites = 0
        var failServerWrite = false
        var failMetadataWrite = false

        override suspend fun updateServers(
            reconcileLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
        ): SubscriptionRefreshBatchReconciliationResult {
            events += "servers"
            serverWrites += 1
            if (failServerWrite) error("injected aggregate catalog write failure")
            val result = reconcileLatest(latest)
            if (result.applicable) {
                latest = latest.copy(
                    servers = result.snapshot.servers,
                    nextServerId = result.snapshot.nextServerId,
                    selectedServerId = result.snapshot.selectedServerId,
                )
            }
            return result
        }

        override suspend fun updateMetadataAndProfiles(
            applyLatest: (SubscriptionRefreshSnapshot) -> SubscriptionRefreshBatchReconciliationResult,
        ): SubscriptionRefreshBatchReconciliationResult {
            events += "metadata-and-profiles"
            metadataWrites += 1
            if (failMetadataWrite) error("injected aggregate metadata/profile write failure")
            val latestWithConcurrentMetadata = latest.copy(
                subscriptions = latest.subscriptions.map { subscription ->
                    if (subscription.id == 4) subscription.copy(
                        title = "Renamed during catalog write",
                        metadata = subscription.metadata?.copy(profileDescription = "Latest description"),
                    ) else subscription
                },
            )
            val result = applyLatest(latestWithConcurrentMetadata)
            if (result.applicable) {
                latest = latestWithConcurrentMetadata.copy(
                    subscriptions = result.snapshot.subscriptions,
                )
                appliedProfileSources += result.resolvedEmbeddedConfigs.map { it.sourceUrl }
            }
            return result
        }
    }
}
