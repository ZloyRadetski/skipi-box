// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyServerLatencyTrackerTest {

    @Test
    fun `cancelAll cancels registered active jobs`() = runBlocking {
        val job1 = launch(Dispatchers.Default) {
            CompletableDeferred<Unit>().await()
        }
        val job2 = launch(Dispatchers.Default) {
            CompletableDeferred<Unit>().await()
        }

        ProxyServerLatencyTracker.register(job1)
        ProxyServerLatencyTracker.register(job2)

        assertTrue(job1.isActive)
        assertTrue(job2.isActive)

        ProxyServerLatencyTracker.cancelAll()

        assertTrue(job1.isCancelled)
        assertTrue(job2.isCancelled)
    }

    @Test
    fun `completed job automatically unregisters from tracker`() = runBlocking {
        val deferred = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            deferred.await()
        }

        ProxyServerLatencyTracker.register(job)
        assertTrue(job.isActive)

        deferred.complete(Unit)
        job.join()

        assertFalse(job.isActive)
        assertFalse(job.isCancelled)
    }
}
