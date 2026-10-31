// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import app.skipi.app.repository.SubscriptionRepository
import app.skipi.app.subscription.SubscriptionInstallAndRefreshResult
import app.skipi.app.subscription.SubscriptionInstallOperations
import app.skipi.app.subscription.SubscriptionWorkerOutcome
import app.skipi.app.subscription.shouldFetchScheduledSubscription
import app.skipi.app.subscription.subscriptionWorkerOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes Desktop's single, scheduled, install, and batch subscription refresh paths. */
internal class DesktopSubscriptionRefreshCoordinator(
    private val subscriptionRepository: SubscriptionRepository,
    private val isNetworkConnected: () -> Boolean = { true },
    private val refresh: suspend (SubscriptionRecord) -> DesktopSubscriptionRefreshOperationResult,
) {
    private val admission = Mutex()

    /** Refreshes the latest persisted row after acquiring the shared refresh admission. */
    suspend fun refresh(subscriptionId: Int): Result<DesktopSubscriptionRefreshOperationResult> = admission.withLock {
        val subscription = latestSubscription(subscriptionId)
            ?: return@withLock Result.failure(
                DesktopSubscriptionRefreshPreconditionException("Подписка больше не существует."),
            )
        if (subscription.url.isBlank()) {
            return@withLock Result.failure(
                DesktopSubscriptionRefreshPreconditionException("У группы нет ссылки для обновления."),
            )
        }
        refreshResult(subscription)
    }

    /** Rechecks worker eligibility and connectivity after admission; null defers without an attempt. */
    suspend fun refreshScheduled(subscriptionId: Int): SubscriptionWorkerOutcome? = admission.withLock {
        val subscription = latestSubscription(subscriptionId)
        if (!shouldFetchScheduledSubscription(
                groupFound = subscription != null,
                enabled = subscription?.enabled ?: false,
                url = subscription?.url.orEmpty(),
            )
        ) {
            return@withLock SubscriptionWorkerOutcome.SUCCESS
        }
        if (!isNetworkConnected()) return@withLock null

        try {
            val result = refresh(checkNotNull(subscription))
            if (result.refresh.commit.failure == null) {
                SubscriptionWorkerOutcome.SUCCESS
            } else {
                SubscriptionWorkerOutcome.FAILURE
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            subscriptionWorkerOutcome(
                transientFailureFlags = listOf(isTransientDesktopSubscriptionRefreshFailure(error)),
            )
        }
    }

    /** Persists an install and awaits its first refresh while holding this same admission. */
    suspend fun install(
        uri: DesktopSubscriptionInstallUri,
    ): SubscriptionInstallAndRefreshResult<Result<DesktopSubscriptionRefreshOperationResult>> = admission.withLock {
        SubscriptionInstallOperations.installAndRefresh(
            repository = subscriptionRepository,
            title = uri.name,
            url = uri.url,
            userAgent = uri.userAgent,
        ) { subscription ->
            refreshResult(subscription)
        }
    }

    /** Runs the existing retained batch operation under the same serial admission as single refreshes. */
    suspend fun <T> runBatch(block: suspend () -> T): T = admission.withLock {
        block()
    }

    private fun latestSubscription(subscriptionId: Int): SubscriptionRecord? =
        subscriptionRepository.catalog.value.subscriptions.firstOrNull { it.id == subscriptionId }

    private suspend fun refreshResult(
        subscription: SubscriptionRecord,
    ): Result<DesktopSubscriptionRefreshOperationResult> = try {
        Result.success(refresh(subscription))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}

internal class DesktopSubscriptionRefreshPreconditionException(message: String) : IllegalStateException(message)
