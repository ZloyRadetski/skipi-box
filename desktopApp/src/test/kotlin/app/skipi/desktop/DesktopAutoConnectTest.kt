// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAutoConnectTest {
    private val enabled = DesktopAppSettings(autoConnectOnAppOpen = true, onboardingCompleted = true)

    @Test
    fun connectsOnOpenWhenEnabledWithASelectedServerAndNothingRunning() {
        assertTrue(shouldAutoConnectOnOpen(enabled, hasSelection = true, tunnelRunning = false))
    }

    @Test
    fun staysOffWhenTheSettingIsOff() {
        assertFalse(shouldAutoConnectOnOpen(enabled.copy(autoConnectOnAppOpen = false), true, false))
    }

    @Test
    fun staysOffWithoutASelectedServerOrProfile() {
        assertFalse(shouldAutoConnectOnOpen(enabled, hasSelection = false, tunnelRunning = false))
    }

    @Test
    fun staysOffWhenAlreadyRunning() {
        assertFalse(shouldAutoConnectOnOpen(enabled, hasSelection = true, tunnelRunning = true))
    }

    @Test
    fun waitsForOnboardingToFinish() {
        assertFalse(shouldAutoConnectOnOpen(enabled.copy(onboardingCompleted = false), true, false))
    }
}

class DesktopAutoConnectRetryTest {
    private val delays = listOf(1_000L, 2_000L, 4_000L)
    private var running = false
    private var intent = 0L
    private var connects = 0
    private val waited = mutableListOf<Long>()

    private val failures = mutableListOf<Pair<Int, Long>>()

    private suspend fun run(
        succeedOnAttempt: Int?,
        onDelay: (Long) -> Unit = {},
        stop: () -> Boolean = { false },
    ) = autoConnectWithRetry(
        onAttemptFailed = { attempt, waitMillis -> failures += attempt to waitMillis },
        connect = {
            connects += 1
            running = connects == succeedOnAttempt
        },
        isRunning = { running },
        intentVersion = { intent },
        shouldStop = stop,
        retryDelaysMillis = delays,
        pause = { millis ->
            waited += millis
            onDelay(millis)
        },
    )

    @Test
    fun aSuccessfulFirstAttemptIsNotRetried() = kotlinx.coroutines.runBlocking {
        run(succeedOnAttempt = 1)

        assertEquals(1, connects)
        assertTrue(waited.isEmpty())
    }

    @Test
    fun aFailedAttemptIsRetriedWithGrowingPausesUntilItSucceeds() = kotlinx.coroutines.runBlocking {
        run(succeedOnAttempt = 3)

        assertEquals(3, connects)
        assertEquals(listOf(1_000L, 2_000L), waited)
    }

    @Test
    fun everyFailedAttemptIsReportedWithItsNumberAndThePauseThatFollows() = kotlinx.coroutines.runBlocking {
        run(succeedOnAttempt = 3)

        assertEquals(listOf(1 to 1_000L, 2 to 2_000L), failures)
    }

    @Test
    fun theLastFailureIsReportedWithoutAPause() = kotlinx.coroutines.runBlocking {
        run(succeedOnAttempt = null)

        assertEquals(listOf(1 to 1_000L, 2 to 2_000L, 3 to 4_000L, 4 to 0L), failures)
    }

    @Test
    fun itGivesUpAfterTheLastPause() = kotlinx.coroutines.runBlocking {
        run(succeedOnAttempt = null)

        assertEquals(4, connects)
        assertEquals(delays, waited)
    }

    @Test
    fun aUserActionDuringThePauseEndsTheRetries() = kotlinx.coroutines.runBlocking {
        run(succeedOnAttempt = null, onDelay = { intent += 1 })

        assertEquals(1, connects)
    }

    @Test
    fun closingTheApplicationEndsTheRetries() = kotlinx.coroutines.runBlocking {
        var closing = false
        run(succeedOnAttempt = null, onDelay = { closing = true }, stop = { closing })

        assertEquals(1, connects)
    }
}
