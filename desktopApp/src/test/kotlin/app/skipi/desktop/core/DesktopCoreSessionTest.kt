// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Core owns HEV TUN: the tunnel starts with one session call and stops with one bounded call. */
class DesktopCoreSessionTest {
    private val bridge = DesktopAmneziaWgBridge(configJson = """{"awg":true}""", socksPort = 10_809)
    private val hev = DesktopCoreHevPlan(configYaml = "tunnel:\n  mtu: 1400\n", tunFd = 41, executable = null)

    private fun controller(native: SessionNative) = DesktopCoreController(
        runtimeProvider = {
            Result.success(
                DesktopCoreRuntime(
                    directory = Path.of("assets"),
                    library = Path.of("runtime", "libskipicore.so"),
                    geoIp = Path.of("assets", "geoip.dat"),
                    geoSite = Path.of("assets", "geosite.dat"),
                ),
            )
        },
        nativeLoader = { native },
    )

    private fun plan(native: SessionNative): JsonObject = Json.parseToJsonElement(checkNotNull(native.planJson)).jsonObject

    @Test
    fun aSessionWithHevStartsThroughOneCoreCallAndNothingElse() {
        val native = SessionNative()
        val controller = controller(native)

        controller.start("{\"xray\":1}", amneziaWg = bridge, hev = hev).getOrThrow()

        assertEquals(listOf("assets", "create", "session-start"), native.events)
        assertTrue(native.events.none { it == "core-start" || it.startsWith("awg") })
        val plan = plan(native)
        assertEquals("{\"xray\":1}", plan.getValue("xrayConfig").jsonPrimitive.content)
        assertEquals(10_809, plan.getValue("amneziaWg").jsonObject.getValue("socksPort").jsonPrimitive.int)
        assertEquals("""{"awg":true}""", plan.getValue("amneziaWg").jsonObject.getValue("config").jsonPrimitive.content)
        val hevBlock = plan.getValue("hev").jsonObject
        assertEquals("tunnel:\n  mtu: 1400\n", hevBlock.getValue("configYaml").jsonPrimitive.content)
        assertEquals(41, hevBlock.getValue("tunFd").jsonPrimitive.int)
        assertFalse("executable" in hevBlock)
        assertFalse(plan.getValue("connectionLog").jsonPrimitive.boolean)
    }

    @Test
    fun theWindowsHevExecutableTravelsInThePlan() {
        val native = SessionNative()

        controller(native).start("{}", hev = hev.copy(tunFd = -1, executable = "C:\\Skipi\\hev\\hev-socks5-tunnel.exe"))
            .getOrThrow()

        val hevBlock = plan(native).getValue("hev").jsonObject
        assertEquals("C:\\Skipi\\hev\\hev-socks5-tunnel.exe", hevBlock.getValue("executable").jsonPrimitive.content)
        assertTrue("amneziaWg" !in plan(native))
    }

    @Test
    fun theConnectionLogSwitchIsPartOfThePlan() {
        val native = SessionNative()
        val controller = controller(native)
        controller.setConnectionLogEnabled(true)

        controller.start("{}", hev = hev).getOrThrow()

        assertTrue(plan(native).getValue("connectionLog").jsonPrimitive.boolean)
    }

    @Test
    fun aStartWithoutHevKeepsTheSeparateAwgAndCoreCalls() {
        val native = SessionNative()
        val controller = controller(native)

        controller.start("{}", amneziaWg = bridge).getOrThrow()
        controller.stop().getOrThrow()

        assertTrue(native.events.none { it.startsWith("session") })
        assertTrue("core-stop" in native.events)
    }

    @Test
    fun aFailedSessionStartReleasesTheHandleAndReportsTheError() {
        val native = SessionNative(failSessionStart = true)
        val controller = controller(native)

        val result = controller.start("{}", amneziaWg = bridge, hev = hev)

        assertEquals("session refused", result.exceptionOrNull()?.message)
        assertEquals(listOf("assets", "create", "session-start", "destroy"), native.events)
        assertFalse(controller.state().isRunning)
    }

    @Test
    fun stopRunsOneBoundedCoreStopThenReleasesTheHandle() {
        val native = SessionNative()
        val controller = controller(native)
        controller.start("{}", hev = hev).getOrThrow()
        native.events.clear()

        assertFalse(controller.stop().getOrThrow().isRunning)

        assertEquals(listOf("stop-timeout:${DesktopCoreSessionStopTimeoutMillis}", "destroy"), native.events)
    }

    @Test
    fun aStopThatTimedOutStillEndsTheSession() {
        val native = SessionNative(failStop = true)
        val controller = controller(native)
        controller.start("{}", hev = hev).getOrThrow()

        val result = controller.stop()

        assertTrue(result.isFailure)
        assertFalse(controller.state().isRunning)
        assertFalse(controller.sessionRunning())
    }

    @Test
    fun sessionRunningReadsCoreSessionState() {
        val native = SessionNative()
        val controller = controller(native)
        assertFalse(controller.sessionRunning())
        controller.start("{}", hev = hev).getOrThrow()

        assertTrue(controller.sessionRunning())
        native.sessionState = 3 // stopping
        assertFalse(controller.sessionRunning())
        native.sessionState = 0 // idle
        assertFalse(controller.sessionRunning())
    }

    @Test
    fun aFailingStateReadCountsAsNotRunning() {
        val native = SessionNative()
        val controller = controller(native)
        controller.start("{}", hev = hev).getOrThrow()
        native.failStateRead = true

        assertFalse(controller.sessionRunning())
    }

    @Test
    fun forceStopEndsTheSessionWithoutTheBoundedStop() {
        val native = SessionNative()
        val controller = controller(native)
        controller.start("{}", hev = hev).getOrThrow()
        native.events.clear()

        assertFalse(controller.forceStop().getOrThrow().isRunning)

        assertEquals(listOf("force-stop", "destroy"), native.events)
        assertFalse(controller.state().isRunning)
    }

    @Test
    fun forceStopWithoutASessionDoesNothing() {
        val native = SessionNative()

        assertFalse(controller(native).forceStop().getOrThrow().isRunning)
        assertTrue(native.events.isEmpty())
    }

    @Test
    fun forceStopDoesNotWaitForAHungStart() {
        val native = SessionNative(blockSessionStart = true)
        val controller = controller(native)
        val starter = Thread { controller.start("{}", hev = hev) }.apply { isDaemon = true; start() }
        assertTrue(native.startEntered.await(5, java.util.concurrent.TimeUnit.SECONDS))

        val result = controller.forceStop()

        assertTrue(result.isSuccess)
        assertTrue("force-stop" in native.events)
        native.releaseStart.countDown()
        starter.join(5_000)
    }

    @Test
    fun abandonedInstancesAreReadFromCore() {
        val native = SessionNative()
        native.abandoned = 2

        assertEquals(2L, controller(native).abandonedInstances().getOrThrow())
    }

    @Test
    fun tunnelStatsAreReadFromTheRunningSession() {
        val native = SessionNative()
        val controller = controller(native)
        assertTrue(controller.queryTunnelStats().isFailure)
        controller.start("{}", hev = hev).getOrThrow()

        assertEquals("""{"txPackets":1}""", controller.queryTunnelStats().getOrThrow())
    }

    @Test
    fun theStartingSessionIsNotReportedAsAnActiveHandle() {
        val native = SessionNative(failSessionStart = true)
        val controller = controller(native)
        controller.start("{}", hev = hev)

        assertNull(controller.state().sessionId)
    }

    private class SessionNative(
        private val failSessionStart: Boolean = false,
        private val failStop: Boolean = false,
        private val blockSessionStart: Boolean = false,
    ) : DesktopCoreNative {
        override val apiVersion: String = DesktopCoreApiVersion
        override val coreVersion: String = "session-test"
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        var planJson: String? = null
        @Volatile var sessionState = 0
        var failStateRead = false
        var abandoned = 0L
        val startEntered = java.util.concurrent.CountDownLatch(1)
        val releaseStart = java.util.concurrent.CountDownLatch(1)

        override fun initializeAssets(directory: String) { events += "assets" }
        override fun createController(): Long { events += "create"; return 7L }
        override fun destroyController(handle: Long) { events += "destroy"; sessionState = 0 }
        override fun start(handle: Long, configJson: String, tunFd: Long) { events += "core-start"; sessionState = 2 }
        override fun stop(handle: Long) { events += "core-stop"; sessionState = 0 }
        override fun isRunning(handle: Long): Boolean = sessionState == 2
        override fun queryTrafficStats(handle: Long): String = "{}"
        override fun measureDelay(handle: Long, targetUrl: String): Long = 0
        override fun readMemoryStats(): String = "{}"
        override fun forceFreeMemory() = Unit
        override fun startAmneziaWg(configJson: String, socksPort: Int) { events += "awg-start" }
        override fun stopAmneziaWg() { events += "awg-stop" }

        override fun startSession(handle: Long, planJson: String, tunFd: Long) {
            events += "session-start"
            this.planJson = planJson
            if (blockSessionStart) {
                startEntered.countDown()
                releaseStart.await()
            }
            if (failSessionStart) error("session refused")
            sessionState = 2
        }

        override fun sessionState(handle: Long): Int {
            if (failStateRead) error("state unreadable")
            return sessionState
        }

        override fun stopWithTimeout(handle: Long, timeoutMillis: Long) {
            events += "stop-timeout:$timeoutMillis"
            sessionState = 0
            if (failStop) error("stop timed out")
        }

        override fun forceStop(handle: Long) { events += "force-stop"; sessionState = 0 }
        override fun abandonedInstances(): Long = abandoned
        override fun queryTunnelStats(handle: Long): String = """{"txPackets":1}"""
        override fun close() = Unit
    }
}
