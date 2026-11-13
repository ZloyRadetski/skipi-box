// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy.latency

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PingCacheCoreTest {
    private class Fixture(scope: CoroutineScope, initial: String? = null) {
        val saved = mutableListOf<String>()
        var now = 1_000L
        val core = PingCacheCore(
            scope = scope,
            debounceMillis = 2_000,
            nowMillis = { now },
            load = { initial },
            save = { saved += it },
        )
    }

    @Test
    fun manyRecords_persistOnceAfterDebounce_andMemoIsImmediate() = runTest {
        val f = Fixture(backgroundScope)
        f.core.record(1, 10)
        f.core.record(2, 20)
        f.core.record(3, 30)

        assertEquals(mapOf(1 to 10L, 2 to 20L, 3 to 30L), f.core.freshest(listOf(1, 2, 3)))
        assertEquals(0, f.saved.size)

        advanceTimeBy(2_001)
        runCurrent()

        assertEquals(1, f.saved.size)
    }

    @Test
    fun flush_persistsImmediatelyAndOnlyOnce() = runTest {
        val f = Fixture(backgroundScope)
        f.core.record(1, 10)
        f.core.flush()
        assertEquals(1, f.saved.size)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, f.saved.size)

        f.core.flush()
        assertEquals(1, f.saved.size)
    }

    @Test
    fun flushedPayload_isReloadedByNewCore() = runTest {
        val f = Fixture(backgroundScope)
        f.core.record(7, 70)
        f.core.flush()

        val reloaded = Fixture(backgroundScope, initial = f.saved.single())
        assertEquals(mapOf(7 to 70L), reloaded.core.freshest(listOf(7)))
    }

    @Test
    fun invalidate_dropsEntryAndSchedulesPersist() = runTest {
        val f = Fixture(backgroundScope)
        f.core.record(1, 10)
        f.core.invalidate(1)
        assertEquals(emptyMap<Int, Long>(), f.core.freshest(listOf(1)))
        f.core.flush()
        assertEquals(1, f.saved.size)
    }

    @Test
    fun staleEntries_areNotFresh() = runTest {
        val f = Fixture(backgroundScope)
        f.core.record(1, 10)
        f.now += 25L * 60 * 60 * 1000
        assertEquals(emptyMap<Int, Long>(), f.core.freshest(listOf(1)))
    }
}
