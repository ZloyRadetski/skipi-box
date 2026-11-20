// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.network.NetworkCidrAddress
import engine.vpn.TunInterfacePlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * On Windows SKIPI Core sets up the network around the adapter hev creates (routes, DNS, the multicast block, the
 * clean-up of adapters an earlier session left); the device only hands it the plan at the right moments.
 */
class DesktopWindowsTunDeviceTest {
    private val plan = TunInterfacePlan(
        name = "skipi0",
        mtu = 1400,
        addresses = listOf(NetworkCidrAddress("172.19.0.1", 30)),
        routes = listOf(NetworkCidrAddress("0.0.0.0", 1), NetworkCidrAddress("128.0.0.0", 1)),
        dnsServers = listOf("1.1.1.1"),
    )

    private class RecordingCore : DesktopTunCore {
        val events = mutableListOf<String>()
        var attachFailure: String? = null

        override fun prepareTun(interfaceName: String) { events += "prepare:$interfaceName" }
        override fun attachTun(planJson: String) {
            events += "attach:$planJson"
            attachFailure?.let { throw IllegalStateException(it) }
        }
        override fun closeTun() { events += "close" }
    }

    @Test
    fun prepareBindsCoreAndOpenHandsOverNoDescriptor() {
        val core = RecordingCore()
        val device = DesktopWindowsTunDevice(core)

        device.prepare("skipi0").getOrThrow()
        val descriptor = device.open("""{"name":"skipi0"}""").getOrThrow()

        assertEquals(-1, descriptor, "hev creates the adapter itself, so there is no descriptor")
        assertEquals(listOf("prepare:skipi0"), core.events)
    }

    @Test
    fun attachHandsCoreThePlanItWasOpenedWith() {
        val core = RecordingCore()
        val device = DesktopWindowsTunDevice(core)
        val planJson = """{"name":"skipi0","mtu":1400,"addresses":["172.19.0.1/30"],"routes":["0.0.0.0/1","128.0.0.0/1"],"dns":["1.1.1.1"]}"""

        device.open(planJson).getOrThrow()
        device.attach(plan).getOrThrow()

        assertEquals("attach:$planJson", core.events.single())
    }

    @Test
    fun aFailedAttachIsReportedSoTheTunnelIsNotCalledUp() {
        val core = RecordingCore().apply { attachFailure = "The TUN adapter skipi0 did not appear" }
        val device = DesktopWindowsTunDevice(core)
        device.open("{}").getOrThrow()

        val result = device.attach(plan)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("did not appear"))
    }

    @Test
    fun closeAsksCoreToRemoveTheRoutesAndCleanUpBehindTheDisconnect() {
        val core = RecordingCore()
        val device = DesktopWindowsTunDevice(core)

        device.close().getOrThrow()

        assertEquals(listOf("close"), core.events)
    }
}
