// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedNativeStepsTest {
    private val health = NativeRuntimeHealth()
    private val warnings = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val steps = BoundedNativeSteps(health) { message, _ -> warnings += message }

    @Test
    fun blockedStep_returnsAfterDeadline_marksDirty_andNextStepStillRuns() {
        val forever = CountDownLatch(1)
        val startedAt = System.nanoTime()
        val first = steps.run("hev", deadlineMillis = 100) { forever.await() }
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertEquals(StepOutcome.TimedOut, first)
        assertTrue("returned after $elapsedMillis ms", elapsedMillis in 90..2_000)
        assertTrue(health.isDirty)
        assertTrue(warnings.any { "hev" in it })

        val ran = AtomicInteger()
        val second = steps.run("core", deadlineMillis = 1_000) { ran.incrementAndGet() }
        assertEquals(StepOutcome.Completed, second)
        assertEquals(1, ran.get())
        forever.countDown()
    }

    @Test
    fun quickStep_doesNotMarkDirty() {
        assertEquals(StepOutcome.Completed, steps.run("hev", deadlineMillis = 1_000) { })
        assertFalse(health.isDirty)
    }

    @Test
    fun failingStep_isLoggedButNotDirty() {
        val outcome = steps.run("core", deadlineMillis = 1_000) { error("boom") }
        assertTrue(outcome is StepOutcome.Failed)
        assertFalse(health.isDirty)
        assertTrue(warnings.any { "core" in it })
    }

    @Test
    fun runsOnAThreadOtherThanTheCaller() {
        var stepThread: Thread? = null
        steps.run("hev", deadlineMillis = 1_000) { stepThread = Thread.currentThread() }
        assertTrue(stepThread != null && stepThread !== Thread.currentThread())
    }
}

class VpnStartGuardTest {
    @Test
    fun cleanRuntime_allowsStart() {
        assertEquals(VpnStartDecision.Allowed, decideVpnStart(NativeRuntimeHealth()))
    }

    @Test
    fun dirtyRuntime_refusesStart() {
        val health = NativeRuntimeHealth().apply { markDirty("hev stop") }
        assertEquals(VpnStartDecision.RefusedNativeRuntimeDirty, decideVpnStart(health))
    }
}

class EmergencyStopCoordinatorTest {
    private class FakeKiller : ProcessKiller {
        val kills = AtomicInteger()
        override fun kill() {
            kills.incrementAndGet()
        }
    }

    private class FakeActions(private val hang: Boolean) : EmergencyStopActions {
        val forever = CountDownLatch(1)
        val log = java.util.Collections.synchronizedList(mutableListOf<String>())
        private fun maybeHang() {
            if (hang) forever.await()
        }
        override fun markStoppedAndPersistIntent() { log += "persist" }
        override fun stopHev() { log += "hev"; maybeHang() }
        override fun stopCore() { log += "core"; maybeHang() }
        override fun stopTailers() { log += "tailers"; maybeHang() }
        override fun closeTun() { log += "tun" }
        override fun stopService() { log += "service" }
        override fun notifyStateChanged() { log += "notify" }
        override fun prepareRelaunch() { log += "relaunch" }
    }

    private fun coordinator(health: NativeRuntimeHealth, actions: EmergencyStopActions, killer: ProcessKiller) =
        EmergencyStopCoordinator(
            health = health,
            steps = BoundedNativeSteps(health) { _, _ -> },
            actions = actions,
            killer = killer,
            stepDeadlineMillis = 100,
        )

    @Test
    fun hungStops_finishWithinDeadlines_closeTun_andKillExactlyOnce() {
        val actions = FakeActions(hang = true)
        val killer = FakeKiller()
        // The operation mutex is held by a hung stop; the coordinator never takes it.
        val startedAt = System.nanoTime()
        coordinator(NativeRuntimeHealth(), actions, killer).run()
        val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)

        assertTrue("took $elapsedMillis ms", elapsedMillis < 3_000)
        assertEquals(
            listOf("persist", "hev", "core", "tailers", "tun", "service", "notify", "relaunch"),
            actions.log.toList(),
        )
        assertEquals(1, killer.kills.get())
        actions.forever.countDown()
    }

    @Test
    fun cleanStops_doNotKill() {
        val actions = FakeActions(hang = false)
        val killer = FakeKiller()
        coordinator(NativeRuntimeHealth(), actions, killer).run()

        assertEquals(0, killer.kills.get())
        assertFalse("relaunch" in actions.log)
        assertTrue("service" in actions.log)
    }

    @Test
    fun alreadyDirty_killsEvenWhenStopsReturn() {
        val killer = FakeKiller()
        val health = NativeRuntimeHealth().apply { markDirty("earlier") }
        coordinator(health, FakeActions(hang = false), killer).run()
        assertEquals(1, killer.kills.get())
    }

    @Test
    fun alreadyDirty_neverCallsIntoTheStuckNativeRuntimesAgain() {
        // A second native stop racing the one still stuck in JNI can crash instead of dying cleanly.
        val actions = FakeActions(hang = false)
        val health = NativeRuntimeHealth().apply { markDirty("earlier") }
        coordinator(health, actions, FakeKiller()).run()
        assertFalse("hev" in actions.log)
        assertFalse("core" in actions.log)
        assertTrue("tun" in actions.log)
        assertTrue("service" in actions.log)
    }
}

class StopWatchdogTest {
    @Test
    fun hungStop_firesWatchdogAfterTimeout() {
        val fired = CountDownLatch(1)
        val watchdog = StopWatchdog(timeoutMillis = 50, onHung = { fired.countDown() })
        val release = CountDownLatch(1)
        val worker = Thread { watchdog.guard { release.await() } }.apply { start() }
        assertTrue(fired.await(2, TimeUnit.SECONDS))
        release.countDown()
        worker.join(2_000)
    }

    @Test
    fun quickStop_neverFires() {
        val fires = AtomicInteger()
        val cleared = AtomicInteger()
        val watchdog = StopWatchdog(
            timeoutMillis = 100,
            onHung = { fires.incrementAndGet() },
            onCleared = { cleared.incrementAndGet() },
        )
        watchdog.guard { }
        Thread.sleep(300)
        assertEquals(0, fires.get())
        assertEquals(0, cleared.get())
    }

    @Test
    fun firedWatchdog_isClearedWhenStopCompletes() {
        val fired = CountDownLatch(1)
        val cleared = AtomicInteger()
        val watchdog = StopWatchdog(
            timeoutMillis = 30,
            onHung = { fired.countDown() },
            onCleared = { cleared.incrementAndGet() },
        )
        watchdog.guard { assertTrue(fired.await(2, TimeUnit.SECONDS)) }
        assertEquals(1, cleared.get())
    }
}

class EmergencyStopMarkerTest {
    private class MemoryStore : EmergencyStopMarkerStore {
        var value = 0L
        override fun read() = value
        override fun write(epochMillis: Long) {
            value = epochMillis
        }
    }

    @Test
    fun appOpenAutoConnect_isSuppressedOnce() {
        val marker = EmergencyStopMarker(MemoryStore()) { 1_000L }
        marker.record()
        assertTrue(marker.consumeSuppressAutoConnect())
        assertFalse(marker.consumeSuppressAutoConnect())
    }

    @Test
    fun systemManagedStart_isSuppressedOnlyShortlyAfter() {
        var now = 1_000L
        val marker = EmergencyStopMarker(MemoryStore()) { now }
        marker.record()
        assertTrue(marker.suppressSystemManagedStart())
        now += EmergencyStopMarker.SystemStartWindowMillis + 1
        assertFalse(marker.suppressSystemManagedStart())
    }

    @Test
    fun withoutRecord_nothingIsSuppressed() {
        val marker = EmergencyStopMarker(MemoryStore()) { 1_000L }
        assertFalse(marker.consumeSuppressAutoConnect())
        assertFalse(marker.suppressSystemManagedStart())
    }
}
