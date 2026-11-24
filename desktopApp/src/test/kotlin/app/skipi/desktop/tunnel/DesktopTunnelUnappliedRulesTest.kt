// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.runBlocking
import platform.TunnelConnectRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rules a built configuration does not apply reach the UI from connect until the tunnel is gone. */
class DesktopTunnelUnappliedRulesTest {
    private class Harness(plans: List<List<String>>, failFirstStart: Boolean = false) {
        var running = false
        val published = mutableListOf<List<String>>()
        private var startAttempts = 0
        private val remaining = plans.toMutableList()

        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
            startPlanForProfile = {
                val unapplied = remaining.removeFirst()
                Result.success(
                    DesktopTunnelStartPlan(
                        configuration = "config",
                        dynamicPortEnabled = false,
                        localProxyPort = 20_000,
                        rebuild = { error("no retry expected") },
                        unappliedRules = unapplied,
                    ),
                )
            },
            startCore = {
                startAttempts += 1
                if (failFirstStart && startAttempts == 1) {
                    Result.failure(IllegalStateException("Core startup failed"))
                } else {
                    running = true
                    Result.success(DesktopCoreState(isRunning = true, sessionId = 1L))
                }
            },
            stopCore = { running = false; Result.success(DesktopCoreState(isRunning = false)) },
            coreState = { DesktopCoreState(isRunning = running) },
            onUnappliedRulesChanged = { published += it },
        )
    }

    @Test
    fun aSuccessfulConnectPublishesTheListAndDisconnectClearsIt() = runBlocking {
        val harness = Harness(listOf(listOf("geoip:as13335")))

        assertTrue(harness.controller.connect(TunnelConnectRequest("p")).isSuccess)
        assertEquals(listOf(listOf("geoip:as13335")), harness.published)

        assertTrue(harness.controller.disconnect().isSuccess)
        assertEquals(listOf(listOf("geoip:as13335"), emptyList()), harness.published)
    }

    @Test
    fun aFailedConnectPublishesNothingButAnEmptyList() = runBlocking {
        val harness = Harness(listOf(listOf("geoip:as13335")), failFirstStart = true)

        assertTrue(harness.controller.connect(TunnelConnectRequest("p")).isFailure)

        assertEquals(listOf(emptyList()), harness.published)
    }

    @Test
    fun theNextConnectReplacesTheListOfTheLastOne() = runBlocking {
        val harness = Harness(listOf(listOf("a"), listOf("b")))

        harness.controller.connect(TunnelConnectRequest("p"))
        harness.controller.disconnect()
        harness.controller.connect(TunnelConnectRequest("p"))

        assertEquals(listOf(listOf("a"), emptyList(), listOf("b")), harness.published)
    }

    @Test
    fun coreDyingUnderAConnectedTunnelClearsTheList() = runBlocking {
        val harness = Harness(listOf(listOf("a")))
        harness.controller.connect(TunnelConnectRequest("p"))

        harness.running = false
        harness.controller.snapshot()

        assertEquals(listOf(listOf("a"), emptyList()), harness.published)
    }
}
