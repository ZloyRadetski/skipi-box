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
    private var attachResult: Result<Unit> = Result.success(Unit)
    private var attachedPlan: TunInterfacePlan? = null

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

        override fun attach(plan: TunInterfacePlan): Result<Unit> {
            events += "attach"
            attachedPlan = plan
            return attachResult
        }

        override fun close(): Result<Unit> {
            events += "close"
            return deviceCloseResult
        }
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

    private var coreSessionAlive = true

    private fun runtime() = DesktopTunRuntime(device, coreSessionRunning = { coreSessionAlive })

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

    // Core runs HEV TUN, so the runtime only opens the device, describes hev and attaches.
    private fun coreRuntime() = DesktopTunRuntime(device, coreSessionRunning = { coreSessionAlive })

    @Test
    fun aRuntimeOpensTheDeviceAndSendsItThePlanAsJson() {
        val runtime = coreRuntime()

        assertEquals(41, runtime.openForCore(request.plan).getOrThrow())

        assertEquals(listOf("open"), events)
        assertEquals(
            """{"name":"skipi0","mtu":1400,"addresses":["172.19.0.1/30"],"routes":["0.0.0.0/1","128.0.0.0/1"]}""",
            openedPlan,
        )
    }

    @Test
    fun aFailedOpenForCoreIsReported() {
        deviceOpenResult = Result.failure(IllegalStateException("NetworkManager refused"))

        assertEquals("NetworkManager refused", coreRuntime().openForCore(request.plan).exceptionOrNull()?.message)
    }

    @Test
    fun theCoreHevPlanCarriesTheYamlAndTheDescriptor() {
        val plan = coreRuntime().coreHevPlan(request.copy(socksUsername = "u", socksPassword = "p"), tunFd = 41)

        assertEquals(41, plan.tunFd)
        assertTrue("  port: 10808" in plan.configYaml)
        assertTrue("  address: '127.0.0.1'" in plan.configYaml)
        assertTrue("  username: 'u'" in plan.configYaml)
        assertTrue("  ipv4: '172.19.0.1'" in plan.configYaml)
        assertTrue("  tcp-fastopen: true" in plan.configYaml)
        assertFalse("ipv6" in plan.configYaml)
    }

    @Test
    fun theCoreHevPlanCarriesTheIpv6AddressWhenThePlanHasOne() {
        val withIpv6 = request.copy(
            plan = request.plan.copy(addresses = request.plan.addresses + NetworkCidrAddress("fdfe:dcba:9876::1", 126)),
        )

        val plan = coreRuntime().coreHevPlan(withIpv6, tunFd = 41)

        assertTrue("  ipv6: 'fdfe:dcba:9876::1'" in plan.configYaml)
    }

    @Test
    fun theCoreHevPlanPointsAtTheMtuAndLogOfTheRequest() {
        val plan = coreRuntime().coreHevPlan(request, tunFd = 41)

        assertTrue("  mtu: 1400" in plan.configYaml)
        assertTrue("  log-file: '/tmp/hev.log'" in plan.configYaml)
    }

    @Test
    fun theCoreHevPlanOnWindowsCarriesNoDescriptorOfItsOwn() {
        val plan = coreRuntime().coreHevPlan(request, tunFd = -1)

        assertEquals(-1, plan.tunFd)
    }

    @Test
    fun theMacCoreHevPlanCarriesTheInterfacePlanTheRunDirectoryAndNoDescriptor() {
        val runDirectory = java.nio.file.Path.of("home", "SKIPI", "run")
        val runtime = DesktopTunRuntime(device, macRunDirectory = runDirectory)

        val plan = runtime.coreHevPlan(request.copy(plan = request.plan.copy(dnsServers = listOf("1.1.1.1"))), tunFd = -1)

        assertEquals(null, plan.tunFd)
        assertEquals(runDirectory.toString(), plan.runDirectory)
        assertEquals(
            DesktopCoreMacInterface(
                name = "skipi0",
                mtu = 1400,
                addresses = listOf("172.19.0.1/30"),
                routes = listOf("0.0.0.0/1", "128.0.0.0/1"),
                dns = listOf("1.1.1.1"),
            ),
            plan.macInterface,
        )
        assertTrue("  port: 10808" in plan.configYaml)
    }

    @Test
    fun theLinuxAndWindowsCoreHevPlansHaveNoMacFields() {
        val plan = coreRuntime().coreHevPlan(request, tunFd = -1)

        assertEquals(null, plan.runDirectory)
        assertEquals(null, plan.macInterface)
        assertEquals(-1, plan.tunFd)
    }

    @Test
    fun attachForCoreRunsAfterTheSessionAndReportsTheRuntimeStarted() {
        val runtime = coreRuntime()
        runtime.openForCore(request.plan)
        events.clear()

        runtime.attachForCore(request.plan).getOrThrow()

        assertEquals(listOf("attach"), events)
        assertEquals(request.plan, attachedPlan)
        assertTrue(runtime.isRunning())
    }

    @Test
    fun aFailedAttachForCoreLeavesTheRuntimeNotRunningAndTheDeviceForRelease() {
        attachResult = Result.failure(IllegalStateException("routes failed"))
        val runtime = coreRuntime()
        runtime.openForCore(request.plan)
        events.clear()

        assertEquals("routes failed", runtime.attachForCore(request.plan).exceptionOrNull()?.message)
        assertFalse(runtime.isRunning())
        runtime.release()

        assertEquals(listOf("attach", "close"), events)
    }

    @Test
    fun aCoreHostedRuntimeFollowsTheCoreSessionNotAnInAppHev() {
        val runtime = coreRuntime()
        runtime.openForCore(request.plan)
        runtime.attachForCore(request.plan)

        assertTrue(runtime.isRunning())
        coreSessionAlive = false
        assertFalse(runtime.isRunning())
    }

    @Test
    fun stoppingACoreHostedRuntimeOnlyClosesTheDevice() {
        val runtime = coreRuntime()
        runtime.openForCore(request.plan)
        runtime.attachForCore(request.plan)
        events.clear()

        assertTrue(runtime.stop().isSuccess)

        assertEquals(listOf("close"), events)
        assertFalse(runtime.isRunning())
    }

    @Test
    fun stoppingACoreHostedRuntimeThatNeverAttachedDoesNothing() {
        val runtime = coreRuntime()
        runtime.openForCore(request.plan)
        events.clear()

        assertTrue(runtime.stop().isSuccess)
        assertTrue(events.isEmpty())
    }
}
