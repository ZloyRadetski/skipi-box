// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BackgroundLogWriterTest {
    @Test
    fun keepsOrderAndNeverBlocksTheCaller() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val gate = CountDownLatch(1)
        val written = CopyOnWriteArrayList<String>()
        val allWritten = CountDownLatch(50)
        val writer = BackgroundLogWriter(scope, maxPendingDebug = 1_000) { _, message, _ ->
            gate.await()
            written += message
            allWritten.countDown()
        }

        val caller = Thread { repeat(50) { writer.post("info", "m$it") } }
        caller.start()
        caller.join(2_000)
        assertTrue("post must not block on the slow writer", !caller.isAlive)

        gate.countDown()
        assertTrue(allWritten.await(5, TimeUnit.SECONDS))
        assertEquals((0 until 50).map { "m$it" }, written.toList())
        scope.cancel()
    }

    @Test
    fun dropsDebugWhenBacklogIsFull_butKeepsOtherLevels() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val gate = CountDownLatch(1)
        val started = CountDownLatch(1)
        val written = CopyOnWriteArrayList<String>()
        val finalWritten = CountDownLatch(1)
        val writer = BackgroundLogWriter(scope, maxPendingDebug = 10) { _, message, _ ->
            started.countDown()
            gate.await()
            written += message
            if (message == "last-error") finalWritten.countDown()
        }

        writer.post("info", "first")
        assertTrue(started.await(2, TimeUnit.SECONDS))
        repeat(100) { writer.post("debug", "d$it") }
        writer.post("error", "last-error")

        gate.countDown()
        assertTrue(finalWritten.await(5, TimeUnit.SECONDS))

        assertEquals(9, written.count { it.startsWith("d") })
        assertEquals("first", written.first())
        assertEquals("last-error", written.last())
        assertEquals((0 until 9).map { "d$it" }, written.filter { it.startsWith("d") })
        scope.cancel()
    }
}
