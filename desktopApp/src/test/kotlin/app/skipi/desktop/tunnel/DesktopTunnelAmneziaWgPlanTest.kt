// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.runBlocking
import platform.TunnelConnectRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The AmneziaWG runner a start plan needs travels with the configuration, including across a retry. */
class DesktopTunnelAmneziaWgPlanTest {
    private fun bridge(port: Int) = DesktopAmneziaWgBridge(configJson = "{\"port\":$port}", socksPort = port)

    @Test
    fun theStartRequestCarriesTheBridgeOfTheCurrentPlanAndARebuiltPlanBringsItsOwn() = runBlocking {
        val requests = mutableListOf<DesktopCoreStartRequest>()
        var running = false

        fun plan(port: Int, excluded: Set<Int>): DesktopTunnelStartPlan = DesktopTunnelStartPlan(
            configuration = "config-$port",
            dynamicPortEnabled = true,
            localProxyPort = 20_000 + port,
            rebuild = { failed -> plan(port + 1, excluded + failed) },
            amneziaWg = bridge(port),
        )

        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
            startPlanForProfile = { Result.success(plan(10_809, emptySet())) },
            startCore = { request ->
                requests += request
                if (requests.size == 1) {
                    Result.failure(IllegalStateException("Core startup failed", IllegalArgumentException("Address already in use")))
                } else {
                    running = true
                    Result.success(DesktopCoreState(isRunning = true, sessionId = 1L))
                }
            },
            stopCore = {
                running = false
                Result.success(DesktopCoreState(isRunning = false))
            },
            coreState = { DesktopCoreState(isRunning = running) },
        )

        val result = controller.connect(TunnelConnectRequest("profile-1"))

        assertTrue(result.isSuccess)
        assertEquals(listOf(10_809, 10_810), requests.map { it.amneziaWg?.socksPort })
        assertEquals(listOf("config-10809", "config-10810"), requests.map { it.configuration })
    }

    @Test
    fun aPlanWithoutAnAmneziaWgServerStartsWithoutABridge() = runBlocking {
        val requests = mutableListOf<DesktopCoreStartRequest>()
        var running = false
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("the start plan should be used")) },
            startPlanForProfile = {
                Result.success(
                    DesktopTunnelStartPlan(
                        configuration = "plain",
                        dynamicPortEnabled = false,
                        localProxyPort = 20_000,
                        rebuild = { error("no retry expected") },
                    ),
                )
            },
            startCore = { request ->
                requests += request
                running = true
                Result.success(DesktopCoreState(isRunning = true, sessionId = 1L))
            },
            stopCore = { running = false; Result.success(DesktopCoreState(isRunning = false)) },
            coreState = { DesktopCoreState(isRunning = running) },
        )

        assertTrue(controller.connect(TunnelConnectRequest("profile-1")).isSuccess)

        assertEquals(listOf(null), requests.map { it.amneziaWg })
    }
}
