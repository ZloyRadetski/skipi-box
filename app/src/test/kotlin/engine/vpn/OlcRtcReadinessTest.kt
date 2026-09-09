// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OlcRtcReadinessTest {

    @Test
    fun readinessPoll_stopsImmediatelyWhenTheListenerAppears() = runTest {
        var attempts = 0

        val ready = awaitOlcRtcReadiness(
            timeoutMs = 1_000L,
            pollIntervalMs = 100L,
        ) {
            ++attempts == 3
        }

        assertTrue(ready)
        assertEquals(3, attempts)
        assertEquals(200L, currentTime)
    }

    @Test
    fun readinessPoll_usesOneBoundedDeadlineWhenTheListenerNeverAppears() = runTest {
        var attempts = 0

        val ready = awaitOlcRtcReadiness(
            timeoutMs = 250L,
            pollIntervalMs = 100L,
        ) {
            attempts++
            false
        }

        assertFalse(ready)
        assertEquals(3, attempts)
        assertEquals(250L, currentTime)
    }

    @Test
    fun readinessPoll_isCancelledWithoutWaitingForItsDeadline() = runTest {
        val polling = async {
            awaitOlcRtcReadiness(
                timeoutMs = 60_000L,
                pollIntervalMs = 1_000L,
            ) { false }
        }

        runCurrent()
        polling.cancelAndJoin()

        assertTrue(polling.isCancelled)
    }
}
