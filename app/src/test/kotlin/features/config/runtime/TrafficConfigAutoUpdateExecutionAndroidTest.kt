// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

// Android source-derived baseline retained against the shared worker execution helper. These tests
// call that helper directly, not WorkManager/context/input/UA/options integration.
package features.config.runtime

import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import app.skipi.app.config.executeTrafficConfigAutoUpdate
import features.config.TrafficConfigState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TrafficConfigAutoUpdateExecutionAndroidTest {
    @Test
    fun nullAndIneligibleCapturedProfilesCompleteWithoutInvokingCallbacks() = runBlocking {
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

            assertEquals("${case.label} should be skipped successfully", TrafficConfigAutoUpdateExecutionResult.Success, result)
            assertEquals("${case.label} should not call the execution ports", emptyList<String>(), events)
        }
    }

    @Test
    fun validRefreshUsesCapturedProfileAndTrimmedUrlThenNormalizesCommitsAndReconcilesInOrder() = runBlocking {
        val captured = eligibleProfile(sourceUrl = "  https://captured.example/profile.conf \n")
        val expectedUrl = "https://captured.example/profile.conf"
        val expectedDocument = fetchedDocument.trimEnd() + "\n"
        val events = mutableListOf<String>()

        val result = executeTrafficConfigAutoUpdate(
            capturedProfile = captured,
            fetch = { fetchedProfile, trimmedUrl ->
                events += "fetch"
                assertSame("fetch should receive the captured profile object", captured, fetchedProfile)
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
    fun documentWithAnErrorDiagnosticRetriesWithoutCommitOrReconciliation() = runBlocking {
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
    fun reconciliationRunsAfterCommitCallbackEvenWhenLatestProfileWasAlreadyRemoved() = runBlocking {
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
    fun fetchErrorsIncludingCancellationMapToRetryAndStopBeforeCommit() = runBlocking {
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
    fun commitErrorsIncludingCancellationMapToRetryAndSkipReconciliation() = runBlocking {
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
    fun reconciliationErrorsIncludingCancellationMapToRetryAfterCommit() = runBlocking {
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
