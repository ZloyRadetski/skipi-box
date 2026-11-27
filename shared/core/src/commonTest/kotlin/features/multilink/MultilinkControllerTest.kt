// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MultilinkControllerTest {
    private val on = MultilinkSettings(enabled = true)

    private class Core {
        val applied = mutableListOf<String>()
        var fail = false
        fun apply(json: String): Result<Unit> =
            if (fail) Result.failure(IllegalStateException("no")) else Result.success(Unit).also { applied += json }
    }

    private fun TestScope.controller(
        core: Core,
        settings: MutableStateFlow<MultilinkSettings>,
        running: MutableStateFlow<Boolean>,
        results: MutableList<Result<Unit>> = mutableListOf(),
    ) = MultilinkController(
        scope = backgroundScope,
        sync = MultilinkSync(apply = core::apply, readStatus = { Result.success("{}") }),
        settings = settings,
        tunnelRunning = running,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        onResult = { _, _, result -> results += result },
    )

    @Test
    fun appliesAtOnceWhileTheTunnelRuns() = runTest {
        val core = Core()
        controller(core, MutableStateFlow(on), MutableStateFlow(true)).start()
        runCurrent()
        assertEquals(1, core.applied.size)
        assertTrue(""""enabled":true""" in core.applied[0])
    }

    @Test
    fun stoppingWaitsTheGracePeriodAndARestartCancelsIt() = runTest {
        val core = Core()
        val running = MutableStateFlow(true)
        controller(core, MutableStateFlow(on), running).start()
        runCurrent()
        running.value = false
        advanceTimeBy(MultilinkOffDelayMillis - 1)
        runCurrent()
        assertEquals(1, core.applied.size, "still within the grace period")
        running.value = true
        advanceTimeBy(MultilinkOffDelayMillis * 2)
        runCurrent()
        assertEquals(1, core.applied.size, "the tunnel came back: nothing changed for Core")
    }

    @Test
    fun aStoppedTunnelSwitchesMergingOffAfterTheGracePeriod() = runTest {
        val core = Core()
        val running = MutableStateFlow(true)
        controller(core, MutableStateFlow(on), running).start()
        runCurrent()
        running.value = false
        advanceTimeBy(MultilinkOffDelayMillis + 1)
        runCurrent()
        assertEquals(2, core.applied.size)
        assertTrue(""""enabled":false""" in core.applied[1])
    }

    @Test
    fun stopSwitchesCoreOffAndNothingIsAppliedAfterIt() = runTest {
        val core = Core()
        val settings = MutableStateFlow(on)
        val controller = controller(core, settings, MutableStateFlow(true))
        controller.start()
        runCurrent()
        assertTrue(controller.stop().isSuccess)
        assertTrue(""""enabled":false""" in core.applied.last())
        val count = core.applied.size
        settings.value = on.withLimit("wifi", 5.0)
        runCurrent()
        assertEquals(count, core.applied.size)
        assertEquals(null, controller.applyOnce(on, true))
    }

    @Test
    fun failuresReachTheListener() = runTest {
        val core = Core().also { it.fail = true }
        val results = mutableListOf<Result<Unit>>()
        controller(core, MutableStateFlow(on), MutableStateFlow(true), results).start()
        runCurrent()
        assertEquals(1, results.size)
        assertTrue(results.single().isFailure)
    }
}
