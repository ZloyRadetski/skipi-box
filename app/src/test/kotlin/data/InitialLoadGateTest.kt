// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class InitialLoadGateTest {
    @Test
    fun persistLaunchedBeforeReady_doesNotRunUntilReady() {
        val gate = InitialLoadGate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val ran = CopyOnWriteArrayList<String>()
        val done = CountDownLatch(1)
        scope.launch {
            gate.awaitReady()
            ran += "persist"
            done.countDown()
        }
        assertFalse(done.await(200, TimeUnit.MILLISECONDS))
        assertTrue(ran.isEmpty())
        assertFalse(gate.isReady.value)

        gate.markReady()

        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(listOf("persist"), ran.toList())
        assertTrue(gate.isReady.value)
        scope.cancel()
    }

    @Test
    fun awaitReadyBlocking_returnsAfterMarkReady() {
        val gate = InitialLoadGate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            Thread.sleep(100)
            gate.markReady()
        }
        assertTrue(gate.awaitReadyBlocking(timeoutMillis = 5_000))
        scope.cancel()
    }

    @Test
    fun awaitReadyBlocking_timesOutWhenNeverReady() {
        assertFalse(InitialLoadGate().awaitReadyBlocking(timeoutMillis = 50))
    }

    @Test
    fun awaitReady_afterMarkReadyReturnsImmediately() = runBlocking {
        val gate = InitialLoadGate()
        gate.markReady()
        withTimeout(1_000) { gate.awaitReady() }
    }
}
