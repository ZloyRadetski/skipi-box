// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription.runtime

import app.AppState
import app.SubscriptionGroupState
import features.proxy.server.usecase.ProxyServerListSubscriptionFailure
import features.proxy.server.usecase.ProxyServerListSubscriptionUpdateResult
import features.subscription.SubscriptionHttpException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionWorkerRunnerTest {
    @Test
    fun missingOrDisabledGroupCompletesWithoutFetching() = runBlocking {
        var fetchCount = 0
        val runner = SubscriptionWorkerRunner(
            stateProvider = {
                AppState(
                    subscriptionGroups = listOf(
                        SubscriptionGroupState(
                            id = 123,
                            name = "Disabled",
                            url = "https://example.test/sub",
                            userAgent = "",
                            updateInterval = "24",
                            enabled = false,
                        ),
                    ),
                )
            },
            update = {
                fetchCount++
                emptyResult()
            },
        )

        assertEquals(SubscriptionWorkerResult.SUCCESS, runner.run(123))
        assertEquals(0, fetchCount)
    }

    @Test
    fun transientFailureRequestsWorkManagerRetry() = runBlocking {
        val runner = runnerWithFailure(SubscriptionHttpException(503))

        assertEquals(SubscriptionWorkerResult.RETRY, runner.run(1))
    }

    @Test
    fun permanentFailureFailsWorkAndSuccessfulUpdateCompletes() = runBlocking {
        assertEquals(SubscriptionWorkerResult.FAILURE, runnerWithFailure(IllegalStateException("invalid" )).run(1))

        var updatesApplied = false
        val successRunner = SubscriptionWorkerRunner(
            stateProvider = { stateWithEnabledGroup() },
            update = {
                updatesApplied = true
                emptyResult()
            },
        )
        assertEquals(SubscriptionWorkerResult.SUCCESS, successRunner.run(1))
        assertTrue(updatesApplied)
    }

    private fun runnerWithFailure(error: Throwable) = SubscriptionWorkerRunner(
        stateProvider = { stateWithEnabledGroup() },
        update = { groupId ->
            ProxyServerListSubscriptionUpdateResult(
                updates = emptyList(),
                failures = listOf(ProxyServerListSubscriptionFailure(groupId, error)),
                updatedAtMillis = 0L,
            )
        },
    )

    private fun stateWithEnabledGroup() = AppState(
        subscriptionGroups = listOf(
            SubscriptionGroupState(
                id = 1,
                name = "Test",
                url = "https://example.test/sub",
                userAgent = "",
                updateInterval = "",
                enabled = true,
            ),
        ),
    )

    private fun emptyResult() = ProxyServerListSubscriptionUpdateResult(
        updates = emptyList(),
        failures = emptyList(),
        updatedAtMillis = 0L,
    )
}
