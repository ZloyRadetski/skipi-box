// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionCatalog
import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.subscription.LoadedSubscriptionRefresh
import app.skipi.app.subscription.SubscriptionRefreshCommitFailure
import app.skipi.app.subscription.SubscriptionRefreshCommitResult
import app.skipi.app.subscription.SubscriptionRefreshCommitScope
import app.skipi.app.subscription.SubscriptionRefreshResult
import app.skipi.app.subscription.SubscriptionWorkerOutcome
import app.skipi.app.subscription.subscriptionRefreshRequestIdentity
import features.subscription.SubscriptionMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshCoordinatorTest {
    @Test
    fun queued_batch_and_scheduled_refresh_share_admission_and_use_the_latest_saved_record() = runTest {
        val original = subscription(id = 4, title = "Original")
        val repository = InMemorySubscriptionRepository(SubscriptionCatalog(listOf(original), 5))
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val releaseFirstRefresh = CompletableDeferred<Unit>()
        val batchStarted = CompletableDeferred<Unit>()
        val releaseBatch = CompletableDeferred<Unit>()
        val observedRecords = mutableListOf<SubscriptionRecord>()
        val observedFetchOptions = mutableListOf<TestFetchOptions>()
        var activeOperations = 0
        var maximumConcurrentOperations = 0

        fun enterOperation() {
            activeOperations += 1
            maximumConcurrentOperations = maxOf(maximumConcurrentOperations, activeOperations)
        }

        fun leaveOperation() {
            activeOperations -= 1
        }

        val coordinator = DesktopSubscriptionRefreshCoordinator(
            subscriptionRepository = repository,
            refresh = { saved ->
                enterOperation()
                try {
                    observedRecords += saved
                    // The Main callback builds fetch options from the record it receives.
                    observedFetchOptions += TestFetchOptions(
                        userAgent = saved.userAgent,
                        ageSecretKey = saved.ageSecretKey,
                        updateViaProxy = saved.updateViaProxy,
                    )
                    if (observedRecords.size == 1) {
                        firstRefreshStarted.complete(Unit)
                        releaseFirstRefresh.await()
                    }
                    successfulOperation(saved)
                } finally {
                    leaveOperation()
                }
            },
        )

        val single = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refresh(original.id) }
        firstRefreshStarted.await()

        val latest = original.copy(
            title = "Latest saved title",
            userAgent = "Latest user agent",
            ageSecretKey = "latest age key",
            updateViaProxy = true,
        )
        repository.upsert(latest)

        val batch = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.runBatch {
                enterOperation()
                try {
                    batchStarted.complete(Unit)
                    releaseBatch.await()
                    "batch finished"
                } finally {
                    leaveOperation()
                }
            }
        }
        val scheduled = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshScheduled(original.id)
        }
        runCurrent()

        assertEquals(1, observedRecords.size, "both newer requests wait while the first refresh owns admission")
        assertFalse(batchStarted.isCompleted)
        assertEquals(1, activeOperations)

        releaseFirstRefresh.complete(Unit)
        runCurrent()
        batchStarted.await()
        assertEquals(1, observedRecords.size, "the scheduled callback waits behind the queued batch")
        assertEquals(1, activeOperations)

        releaseBatch.complete(Unit)
        assertTrue(single.await().isSuccess)
        assertEquals("batch finished", batch.await())
        assertEquals(SubscriptionWorkerOutcome.SUCCESS, scheduled.await())

        assertEquals(2, observedRecords.size)
        assertEquals(latest, observedRecords.last())
        assertEquals(
            TestFetchOptions(
                userAgent = "Latest user agent",
                ageSecretKey = "latest age key",
                updateViaProxy = true,
            ),
            observedFetchOptions.last(),
            "request-specific options are derived from the latest row after admission",
        )
        assertEquals(1, maximumConcurrentOperations)
        assertEquals(0, activeOperations)
    }

    @Test
    fun scheduled_refresh_rechecks_disabled_group_after_wait_and_skips_without_fetch() = runTest {
        val original = subscription(id = 7, title = "Still enabled")
        val repository = InMemorySubscriptionRepository(SubscriptionCatalog(listOf(original), 8))
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val releaseFirstRefresh = CompletableDeferred<Unit>()
        var refreshCalls = 0
        val coordinator = DesktopSubscriptionRefreshCoordinator(
            subscriptionRepository = repository,
            refresh = { saved ->
                refreshCalls += 1
                if (refreshCalls == 1) {
                    firstRefreshStarted.complete(Unit)
                    releaseFirstRefresh.await()
                }
                successfulOperation(saved)
            },
        )

        val single = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refresh(original.id) }
        firstRefreshStarted.await()
        val scheduled = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshScheduled(original.id)
        }
        runCurrent()

        repository.upsert(original.copy(enabled = false))
        releaseFirstRefresh.complete(Unit)

        assertTrue(single.await().isSuccess)
        assertEquals(SubscriptionWorkerOutcome.SUCCESS, scheduled.await())
        assertEquals(1, refreshCalls, "a group disabled while waiting is skipped after admission")
    }

    @Test
    fun install_waits_for_admission_refreshes_saved_disabled_duplicate_and_returns_failure_with_saved_title() = runTest {
        val saved = subscription(id = 11, title = "Saved title").copy(
            userAgent = "Saved agent",
            updateInterval = "12",
            ageSecretKey = "saved age key",
            updateViaProxy = true,
            enabled = false,
        )
        val repository = InMemorySubscriptionRepository(SubscriptionCatalog(listOf(saved), 12))
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val releaseFirstRefresh = CompletableDeferred<Unit>()
        val installRefreshStarted = CompletableDeferred<Unit>()
        val refreshFailure = IOException("initial fetch failed")
        var callbackCalls = 0
        val coordinator = DesktopSubscriptionRefreshCoordinator(
            subscriptionRepository = repository,
            refresh = { record ->
                callbackCalls += 1
                if (callbackCalls == 1) {
                    firstRefreshStarted.complete(Unit)
                    releaseFirstRefresh.await()
                    successfulOperation(record)
                } else {
                    installRefreshStarted.complete(Unit)
                    assertEquals(saved, repository.catalog.value.subscriptions.single())
                    assertEquals(saved, record, "the duplicate keeps every saved field despite being disabled")
                    throw refreshFailure
                }
            },
        )

        val existingRefresh = async(start = CoroutineStart.UNDISPATCHED) { coordinator.refresh(saved.id) }
        firstRefreshStarted.await()
        val install = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.install(
                DesktopSubscriptionInstallUri(
                    name = "Incoming title",
                    url = saved.url,
                    userAgent = "Incoming agent",
                    source = DesktopSubscriptionInstallSource.RawHttp,
                ),
            )
        }
        runCurrent()

        assertFalse(installRefreshStarted.isCompleted, "install also waits for the shared admission")
        releaseFirstRefresh.complete(Unit)
        assertTrue(existingRefresh.await().isSuccess)
        val result = install.await()

        assertEquals(2, callbackCalls)
        assertEquals(saved, result.preparation.subscription)
        assertEquals("Saved title", result.preparation.existingTitle)
        assertEquals(refreshFailure, result.refreshResult.exceptionOrNull())
        assertEquals(listOf(saved), repository.catalog.value.subscriptions)
    }

    @Test
    fun scheduled_refresh_maps_load_and_commit_outcomes_and_propagates_cancellation() = runTest {
        val saved = subscription(id = 18, title = "Provider")
        val repository = InMemorySubscriptionRepository(SubscriptionCatalog(listOf(saved), 19))
        var thrownFailure: Throwable? = null
        var result = successfulOperation(saved)
        val coordinator = DesktopSubscriptionRefreshCoordinator(
            subscriptionRepository = repository,
            refresh = { record ->
                thrownFailure?.let { throw it }
                result.copy(
                    refresh = result.refresh.copy(
                        loaded = result.refresh.loaded.copy(
                            sourceIdentity = subscriptionRefreshRequestIdentity(record),
                        ),
                    ),
                )
            },
        )

        assertEquals(SubscriptionWorkerOutcome.SUCCESS, coordinator.refreshScheduled(saved.id))

        result = result.withCommitFailure(IllegalStateException("disk write failed"))
        assertEquals(
            SubscriptionWorkerOutcome.FAILURE,
            coordinator.refreshScheduled(saved.id),
            "commit/storage errors are permanent worker failures, not network retries",
        )

        result = result.withStaleCommit()
        assertEquals(
            SubscriptionWorkerOutcome.SUCCESS,
            coordinator.refreshScheduled(saved.id),
            "an inapplicable receipt without a commit error matches Android's skipped apply",
        )

        thrownFailure = DesktopSubscriptionHttpException(503, "temporarily unavailable")
        assertEquals(SubscriptionWorkerOutcome.RETRY, coordinator.refreshScheduled(saved.id))

        thrownFailure = IllegalArgumentException("invalid payload")
        assertEquals(SubscriptionWorkerOutcome.FAILURE, coordinator.refreshScheduled(saved.id))

        val cancellation = CancellationException("owner cancelled")
        thrownFailure = cancellation
        val propagated = try {
            coordinator.refreshScheduled(saved.id)
            null
        } catch (error: CancellationException) {
            error
        }
        assertSame(cancellation, propagated)
    }

    @Test
    fun cancelling_a_suspended_refresh_propagates_and_releases_admission_for_the_next_request() = runTest {
        val saved = subscription(id = 23, title = "Provider")
        val repository = InMemorySubscriptionRepository(SubscriptionCatalog(listOf(saved), 24))
        val firstRefreshStarted = CompletableDeferred<Unit>()
        val firstRefreshCancelled = CompletableDeferred<Unit>()
        var refreshCalls = 0
        val coordinator = DesktopSubscriptionRefreshCoordinator(
            subscriptionRepository = repository,
            refresh = { record ->
                refreshCalls += 1
                if (refreshCalls == 1) {
                    firstRefreshStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } catch (cancelled: CancellationException) {
                        firstRefreshCancelled.complete(Unit)
                        throw cancelled
                    }
                }
                successfulOperation(record)
            },
        )

        val cancelledRefresh = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refreshScheduled(saved.id)
        }
        firstRefreshStarted.await()
        val nextRefresh = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.refresh(saved.id)
        }
        runCurrent()

        cancelledRefresh.cancel()
        cancelledRefresh.join()
        firstRefreshCancelled.await()

        assertTrue(nextRefresh.await().isSuccess, "cancellation releases the shared admission")
        assertEquals(2, refreshCalls)
    }
}

private data class TestFetchOptions(
    val userAgent: String,
    val ageSecretKey: String,
    val updateViaProxy: Boolean,
)

private fun successfulOperation(
    subscription: SubscriptionRecord,
) = DesktopSubscriptionRefreshOperationResult(
    refresh = SubscriptionRefreshResult(
        loaded = LoadedSubscriptionRefresh(
            sourceIdentity = subscriptionRefreshRequestIdentity(subscription),
            urlCount = 1,
            servers = emptyList(),
            metadata = SubscriptionMetadata(),
            resolvedEmbeddedConfig = null,
        ),
        commit = SubscriptionRefreshCommitResult(
            applicable = true,
            appliedScopes = listOf(
                SubscriptionRefreshCommitScope.SERVERS,
                SubscriptionRefreshCommitScope.SUBSCRIPTION,
            ),
        ),
    ),
    serverLibraryChanged = false,
    profileLibraryChanged = false,
)

private fun DesktopSubscriptionRefreshOperationResult.withCommitFailure(error: Throwable) = copy(
    refresh = refresh.copy(
        commit = SubscriptionRefreshCommitResult(
            applicable = true,
            appliedScopes = listOf(SubscriptionRefreshCommitScope.SERVERS),
            failure = SubscriptionRefreshCommitFailure(
                scope = SubscriptionRefreshCommitScope.SUBSCRIPTION,
                error = error,
            ),
        ),
    ),
)

private fun DesktopSubscriptionRefreshOperationResult.withStaleCommit() = copy(
    refresh = refresh.copy(
        commit = SubscriptionRefreshCommitResult(
            applicable = false,
            appliedScopes = emptyList(),
        ),
    ),
)

private fun subscription(id: Int, title: String) = SubscriptionRecord(
    id = id,
    title = title,
    url = "https://provider.example.test/$id",
    userAgent = "Default agent",
    updateInterval = "12",
    hwid = "",
    ageSecretKey = "old age key",
    updateViaProxy = false,
    autoOverrideRules = true,
    enabled = true,
    builtIn = false,
    metadata = null,
    lastUpdatedAtMillis = null,
    notifyOnExpiry = true,
    customExpiryReminders = null,
)

private class InMemorySubscriptionRepository(initial: SubscriptionCatalog) : SubscriptionRepository {
    private val mutableCatalog = MutableStateFlow(initial)
    private val mutableSubscriptions = MutableStateFlow(initial.subscriptions)
    override val catalog: StateFlow<SubscriptionCatalog> = mutableCatalog
    override val subscriptions: StateFlow<List<SubscriptionRecord>> = mutableSubscriptions

    override suspend fun updateCatalog(transform: (SubscriptionCatalog) -> SubscriptionCatalog) {
        val updated = transform(mutableCatalog.value)
        mutableCatalog.value = updated
        mutableSubscriptions.value = updated.subscriptions
    }

    override suspend fun upsert(subscription: SubscriptionRecord) {
        updateCatalog { catalog ->
            val existingIndex = catalog.subscriptions.indexOfFirst { it.id == subscription.id }
            val subscriptions = if (existingIndex < 0) {
                catalog.subscriptions + subscription
            } else {
                catalog.subscriptions.map { existing ->
                    if (existing.id == subscription.id) subscription else existing
                }
            }
            catalog.copy(subscriptions = subscriptions)
        }
    }

    override suspend fun remove(subscriptionId: Int) {
        updateCatalog { catalog ->
            catalog.copy(subscriptions = catalog.subscriptions.filterNot { it.id == subscriptionId })
        }
    }

    override suspend fun refresh(subscriptionId: Int): Result<SubscriptionRecord> =
        Result.failure(UnsupportedOperationException("The test refresh callback owns this operation"))
}
