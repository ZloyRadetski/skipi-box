// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.home.ProxyHomeEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProxyHomeLatencyJobOwnerTest {
    @Test
    fun cancelLatencyEffectCancelsTheRetainedHostJobAndRunsItsCleanup() = runBlocking {
        val jobStarted = CompletableDeferred<Unit>()
        val jobCleanedUp = CompletableDeferred<Unit>()
        val owner = DesktopProxyHomeLatencyJobOwner()
        val latencyJob = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            jobStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                jobCleanedUp.complete(Unit)
            }
        }
        owner.track(latencyJob)
        jobStarted.await()

        val effects = DesktopProxyHomeEffectContext(onCancelLatencyTests = owner::cancel)
        assertTrue(effects.handle(ProxyHomeEffect.CancelLatencyTests).isSuccess)

        latencyJob.join()
        jobCleanedUp.await()
        assertFalse(owner.hasActiveJobs)
    }
}
