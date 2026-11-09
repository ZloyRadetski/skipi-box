// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package utils

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class RunCatchingCancellableTest {
    /** Runs [block] to completion on the spot; nothing here really suspends. */
    private fun <T> runNow(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
        return checkNotNull(outcome) { "the block suspended" }.getOrThrow()
    }

    @Test
    fun aValueComesBackAsASuccess() {
        assertEquals(7, runNow { runCatchingCancellable { 7 } }.getOrNull())
    }

    @Test
    fun anOrdinaryFailureIsCapturedLikeRunCatching() {
        val failure = IllegalStateException("boom")

        val result = runNow { runCatchingCancellable<Int> { throw failure } }

        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun cancellationIsNeverCapturedSoTheCoroutineCanStop() {
        assertFailsWith<CancellationException> {
            runNow { runCatchingCancellable<Int> { throw CancellationException("cancelled") } }
        }
    }

    @Test
    fun aSubclassOfCancellationStillPropagates() {
        class Timeout : CancellationException("timeout")

        assertFailsWith<Timeout> { runNow { runCatchingCancellable<Int> { throw Timeout() } } }
    }
}
