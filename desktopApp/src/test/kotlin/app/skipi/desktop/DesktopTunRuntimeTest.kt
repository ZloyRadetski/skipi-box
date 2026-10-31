// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.network.NetworkCidrAddress
import engine.vpn.TunInterfacePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTunRuntimeTest {
    private val events = mutableListOf<String>()
    private var openedPlan: String? = null
    private var startedYaml: String? = null
    private var startedFd: Int? = null
    private var tunnelAlive = false
    private var deviceOpenResult: Result<Int> = Result.success(41)
    private var tunnelStartResult: Result<Unit> = Result.success(Unit)
    private var tunnelStopResult: Result<Unit> = Result.success(Unit)
    private var deviceCloseResult: Result<Unit> = Result.success(Unit)

    private var prepareResult: Result<Unit> = Result.success(Unit)
    private var preparedName: String? = null
    private val device = object : DesktopTunDevice {
        override fun prepare(interfaceName: String): Result<Unit> {
            events += "prepare"
            preparedName = interfaceName
            return prepareResult
        }

        override fun open(planJson: String): Result<Int> {
            events += "open"
            openedPlan = planJson
            return deviceOpenResult
        }

        override fun close(): Result<Unit> {
            events += "close"
            return deviceCloseResult
        }
    }
    private val tunnel = object : DesktopHevTunnel {
        override fun start(configYaml: String, tunFd: Int): Result<Unit> {
            events += "start"
            startedYaml = configYaml
            startedFd = tunFd
            tunnelAlive = tunnelStartResult.isSuccess
            return tunnelStartResult
        }

        override fun stop(): Result<Unit> {
            events += "stop"
            tunnelAlive = false
            return tunnelStopResult
        }

        override fun isRunning(): Boolean = tunnelAlive
    }
    private val request = DesktopTunStartRequest(
        plan = TunInterfacePlan(
            name = "skipi0",
            mtu = 1400,
            addresses = listOf(NetworkCidrAddress("172.19.0.1", 30)),
            routes = listOf(NetworkCidrAddress("0.0.0.0", 1), NetworkCidrAddress("128.0.0.0", 1)),
        ),
        socksAddress = "127.0.0.1",
        socksPort = 10808,
        socksUsername = "",
        socksPassword = "",
        logPath = "/tmp/hev.log",
    )

    private fun runtime() = DesktopTunRuntime(device, tunnel)

    @Test
    fun prepareBindsCoreSocketsToThePlannedInterfaceBeforeAnythingElse() {
        val runtime = runtime()

        assertTrue(runtime.prepare(request.plan).isSuccess)

        assertEquals(listOf("prepare"), events)
        assertEquals("skipi0", preparedName)
    }

    @Test
    fun aFailedPrepareIsReported() {
        prepareResult = Result.failure(IllegalStateException("cannot bind"))

        assertEquals("cannot bind", runtime().prepare(request.plan).exceptionOrNull()?.message)
    }

    @Test
    fun releaseUndoesAPrepareThatNeverGotToStart() {
        val runtime = runtime()
        runtime.prepare(request.plan)
        events.clear()

        assertTrue(runtime.release().isSuccess)

        assertEquals(listOf("close"), events)
    }

    @Test
    fun opensTheDeviceThenRunsHevOnItsDescriptor() {
        val result = runtime().start(request)

        assertTrue(result.isSuccess)
        assertEquals(listOf("open", "start"), events)
        assertEquals(41, startedFd)
    }

    @Test
    fun sendsThePlanToTheDeviceAsJson() {
        runtime().start(request)

        assertEquals(
            """{"name":"skipi0","mtu":1400,"addresses":["172.19.0.1/30"],"routes":["0.0.0.0/1","128.0.0.0/1"]}""",
            openedPlan,
        )
    }

    @Test
    fun hevConfigurationPointsAtTheLocalSocksInboundLikeAndroid() {
        runtime().start(request.copy(socksUsername = "u", socksPassword = "p"))

        val yaml = checkNotNull(startedYaml)
        assertTrue("  ipv4: '172.19.0.1'" in yaml)
        assertTrue("  mtu: 1400" in yaml)
        assertTrue("  port: 10808" in yaml)
        assertTrue("  address: '127.0.0.1'" in yaml)
        assertTrue("  username: 'u'" in yaml)
        assertTrue("  tcp-fastopen: true" in yaml)
        assertTrue("  log-file: '/tmp/hev.log'" in yaml)
        assertFalse("ipv6" in yaml)
    }

    @Test
    fun anIpv6AddressInThePlanIsPassedToHev() {
        val withIpv6 = request.copy(
            plan = request.plan.copy(
                addresses = request.plan.addresses + NetworkCidrAddress("fdfe:dcba:9876::1", 126),
            ),
        )

        runtime().start(withIpv6)

        assertTrue("  ipv6: 'fdfe:dcba:9876::1'" in checkNotNull(startedYaml))
    }

    @Test
    fun failedDeviceOpenNeverStartsHev() {
        deviceOpenResult = Result.failure(IllegalStateException("NetworkManager refused"))

        val result = runtime().start(request)

        assertEquals("NetworkManager refused", result.exceptionOrNull()?.message)
        assertEquals(listOf("open"), events)
    }

    @Test
    fun failedHevStartClosesTheDevice() {
        tunnelStartResult = Result.failure(IllegalStateException("hev exited"))

        val result = runtime().start(request)

        assertEquals("hev exited", result.exceptionOrNull()?.message)
        assertEquals(listOf("open", "start", "close"), events)
    }

    @Test
    fun stopsHevBeforeClosingTheDevice() {
        val runtime = runtime()
        runtime.start(request)
        events.clear()

        assertTrue(runtime.stop().isSuccess)

        assertEquals(listOf("stop", "close"), events)
        assertFalse(runtime.isRunning())
    }

    @Test
    fun stopStillClosesTheDeviceWhenHevFailsToStopAndReportsTheFirstError() {
        val runtime = runtime()
        runtime.start(request)
        events.clear()
        tunnelStopResult = Result.failure(IllegalStateException("hev stuck"))
        deviceCloseResult = Result.failure(IllegalStateException("close failed"))

        val result = runtime.stop()

        assertEquals(listOf("stop", "close"), events)
        assertEquals("hev stuck", result.exceptionOrNull()?.message)
    }

    @Test
    fun secondStartWhileRunningIsRejected() {
        val runtime = runtime()
        runtime.start(request)
        events.clear()

        val result = runtime.start(request)

        assertTrue(result.isFailure)
        assertTrue(events.isEmpty())
    }

    @Test
    fun stopWithoutStartDoesNothing() {
        assertTrue(runtime().stop().isSuccess)
        assertTrue(events.isEmpty())
    }

    @Test
    fun isRunningFollowsHev() {
        val runtime = runtime()
        assertFalse(runtime.isRunning())
        runtime.start(request)
        assertTrue(runtime.isRunning())
        tunnelAlive = false
        assertFalse(runtime.isRunning())
    }
}
