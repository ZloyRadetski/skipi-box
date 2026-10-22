// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

// Shared execution contract only. Android WorkManager/context/input/UA/options integration
// remains outside this suite.
package app.skipi.app.config

import features.config.TrafficConfigState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TrafficConfigAutoUpdateExecutionTest {
    @Test
    fun nullAndIneligibleCapturedProfilesCompleteWithoutInvokingCallbacks() = runTest {
        val eligible = eligibleProfile()
        val cases = listOf(
            GuardCase("missing profile", null),
            GuardCase("automatic refresh disabled", eligible.copy(autoUpdate = false)),
            GuardCase("profile locked", eligible.copy(updateLocked = true)),
            GuardCase("blank source URL", eligible.copy(sourceUrl = " \t\n")),
        )

        for (case in cases) {
            val events = mutableListOf<String>()
            val result = executeTrafficConfigAutoUpdate(
                capturedProfile = case.profile,
                fetch = { _, _ ->
                    events += "fetch"
                    fetchedDocument
                },
                commit = { _, _, _ -> events += "commit" },
                reconcile = { events += "reconcile" },
            )

            assertEquals(TrafficConfigAutoUpdateExecutionResult.Success, result, "${case.label} should be skipped successfully")
            assertEquals(emptyList<String>(), events, "${case.label} should not call the execution ports")
        }
    }

    @Test
    fun validRefreshUsesCapturedProfileAndTrimmedUrlThenNormalizesCommitsAndReconcilesInOrder() = runTest {
        val captured = eligibleProfile(sourceUrl = "  https://captured.example/profile.conf \n")
        val expectedUrl = "https://captured.example/profile.conf"
        val expectedDocument = fetchedDocument.trimEnd() + "\n"
        val events = mutableListOf<String>()

        val result = executeTrafficConfigAutoUpdate(
            capturedProfile = captured,
            fetch = { fetchedProfile, trimmedUrl ->
                events += "fetch"
                assertSame(captured, fetchedProfile, "fetch should receive the captured profile object")
                assertEquals(expectedUrl, trimmedUrl)
                fetchedDocument + "\n \t"
            },
            commit = { profileId, normalizedDocument, capturedUrl ->
                events += "commit"
                assertEquals(captured.id, profileId)
                assertEquals(expectedDocument, normalizedDocument)
                assertEquals(expectedUrl, capturedUrl)
            },
            reconcile = { events += "reconcile" },
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Success, result)
        assertEquals(listOf("fetch", "commit", "reconcile"), events)
    }

    @Test
    fun documentWithAnErrorDiagnosticRetriesWithoutCommitOrReconciliation() = runTest {
        val events = mutableListOf<String>()
        val invalidDocument = """
            [Rule]
            FINAL,PROXY
            FINAL,DIRECT
        """.trimIndent()

        val result = executeTrafficConfigAutoUpdate(
            capturedProfile = eligibleProfile(),
            fetch = { _, _ ->
                events += "fetch"
                invalidDocument
            },
            commit = { _, _, _ -> events += "commit" },
            reconcile = { events += "reconcile" },
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, result)
        assertEquals(listOf("fetch"), events)
    }

    @Test
    fun reconciliationRunsAfterCommitCallbackEvenWhenLatestProfileWasAlreadyRemoved() = runTest {
        val captured = eligibleProfile()
        val events = mutableListOf<String>()

        val result = executeTrafficConfigAutoUpdate(
            capturedProfile = captured,
            fetch = { _, _ ->
                events += "fetch"
                fetchedDocument
            },
            commit = { profileId, normalizedDocument, capturedUrl ->
                // The host's latest-row update finds no matching ID and is a no-op.
                // Its Unit result gives the executor no deletion signal to reinterpret.
                assertEquals(captured.id, profileId)
                assertEquals(fetchedDocument.trimEnd() + "\n", normalizedDocument)
                assertEquals(captured.sourceUrl, capturedUrl)
                events += "commit-no-op-for-removed-profile"
            },
            reconcile = { events += "reconcile" },
        )

        assertEquals(TrafficConfigAutoUpdateExecutionResult.Success, result)
        assertEquals(listOf("fetch", "commit-no-op-for-removed-profile", "reconcile"), events)
    }

    @Test
    fun fetchErrorsIncludingCancellationMapToRetryAndStopBeforeCommit() = runTest {
        val failures = listOf<Throwable>(
            IllegalStateException("fetch failed"),
            CancellationException("fetch cancelled"),
        )

        for (failure in failures) {
            val events = mutableListOf<String>()
            val result = executeTrafficConfigAutoUpdate(
                capturedProfile = eligibleProfile(),
                fetch = { _, _ ->
                    events += "fetch"
                    throw failure
                },
                commit = { _, _, _ -> events += "commit" },
                reconcile = { events += "reconcile" },
            )

            assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, result)
            assertEquals(listOf("fetch"), events)
        }
    }

    @Test
    fun commitErrorsIncludingCancellationMapToRetryAndSkipReconciliation() = runTest {
        val failures = listOf<Throwable>(
            IllegalStateException("commit failed"),
            CancellationException("commit cancelled"),
        )

        for (failure in failures) {
            val events = mutableListOf<String>()
            val result = executeTrafficConfigAutoUpdate(
                capturedProfile = eligibleProfile(),
                fetch = { _, _ ->
                    events += "fetch"
                    fetchedDocument
                },
                commit = { _, _, _ ->
                    events += "commit"
                    throw failure
                },
                reconcile = { events += "reconcile" },
            )

            assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, result)
            assertEquals(listOf("fetch", "commit"), events)
        }
    }

    @Test
    fun reconciliationErrorsIncludingCancellationMapToRetryAfterCommit() = runTest {
        val failures = listOf<Throwable>(
            IllegalStateException("reconciliation failed"),
            CancellationException("reconciliation cancelled"),
        )

        for (failure in failures) {
            val events = mutableListOf<String>()
            val result = executeTrafficConfigAutoUpdate(
                capturedProfile = eligibleProfile(),
                fetch = { _, _ ->
                    events += "fetch"
                    fetchedDocument
                },
                commit = { _, _, _ -> events += "commit" },
                reconcile = {
                    events += "reconcile"
                    throw failure
                },
            )

            assertEquals(TrafficConfigAutoUpdateExecutionResult.Retry, result)
            assertEquals(listOf("fetch", "commit", "reconcile"), events)
        }
    }

    private fun eligibleProfile(sourceUrl: String = "https://provider.example/profile.conf") = TrafficConfigState(
        id = 29,
        name = "Automatic profile",
        rawConfig = "[General]\nloglevel = warning\n",
        sourceUrl = sourceUrl,
        updateLocked = false,
        autoUpdate = true,
    )

    private data class GuardCase(
        val label: String,
        val profile: TrafficConfigState?,
    )

    private companion object {
        // Matches a valid profile document used by the existing Android refresh baselines.
        val fetchedDocument = "[General]\nloglevel = debug\n"
    }
}
