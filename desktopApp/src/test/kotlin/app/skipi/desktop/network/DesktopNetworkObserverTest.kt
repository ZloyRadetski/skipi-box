// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.networkautomation.policy.ObservedNetworkTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DesktopNetworkObserverTest {
    private fun nic(name: String, display: String = name, up: Boolean = true, loopback: Boolean = false, hasAddress: Boolean = true) =
        DesktopNetworkInterface(name, display, up, loopback, hasAddress)

    @Test
    fun wifiAdapterIsReportedWithTheCurrentSsid() {
        val observed = observeDesktopNetwork(listOf(nic("wlan0")), ssidProvider = { "Home" })
        assertEquals(setOf(ObservedNetworkTransport.WIFI), observed?.transports)
        assertEquals("Home", observed?.wifiSsid)
    }

    @Test
    fun windowsWifiIsRecognisedByItsDescription() {
        val observed = observeDesktopNetwork(
            listOf(nic("net4", "Intel(R) Wi-Fi 6 AX201 160MHz")),
            ssidProvider = { null },
        )
        assertEquals(setOf(ObservedNetworkTransport.WIFI), observed?.transports)
    }

    @Test
    fun wiredAndMobileBroadbandAreClassified() {
        assertEquals(setOf(ObservedNetworkTransport.ETHERNET), observeDesktopNetwork(listOf(nic("eth0")), { null })?.transports)
        assertEquals(setOf(ObservedNetworkTransport.ETHERNET), observeDesktopNetwork(listOf(nic("enp3s0")), { null })?.transports)
        assertEquals(setOf(ObservedNetworkTransport.CELLULAR), observeDesktopNetwork(listOf(nic("wwan0")), { null })?.transports)
    }

    @Test
    fun virtualTunnelAndContainerAdaptersAreIgnored() {
        val ignored = listOf(
            nic("tun0"), nic("wintun", "Wintun Userspace Tunnel"), nic("docker0"), nic("veth1a2b"),
            nic("virbr0"), nic("vEthernet (WSL)", "Hyper-V Virtual Ethernet Adapter"), nic("tap0"),
            nic("lo", loopback = true), nic("eth1", up = false), nic("eth2", hasAddress = false),
        )
        assertNull(observeDesktopNetwork(ignored, { "Home" }))
    }

    @Test
    fun wifiWinsOverEthernetLikeOnAndroid() {
        val observed = observeDesktopNetwork(listOf(nic("eth0"), nic("wlan0")), { "Office" })
        assertEquals(setOf(ObservedNetworkTransport.WIFI, ObservedNetworkTransport.ETHERNET), observed?.transports)
        assertEquals("Office", observed?.wifiSsid)
    }

    @Test
    fun ssidIsOnlyReadWhenWifiIsPresent() {
        var reads = 0
        observeDesktopNetwork(listOf(nic("eth0")), { reads++; "x" })
        assertEquals(0, reads)
    }

    @Test
    fun netshOutputYieldsTheConnectedSsid() {
        val output = """
            There is 1 interface on the system:

                Name                   : Wi-Fi
                State                  : connected
                SSID                   : My Home Net
                BSSID                  : aa:bb:cc:dd:ee:ff
        """.trimIndent()
        assertEquals("My Home Net", parseNetshWlanSsid(output))
        assertNull(parseNetshWlanSsid("    State                  : disconnected\n    SSID                   : Old"))
        assertNull(parseNetshWlanSsid(""))
    }

    @Test
    fun nmcliOutputYieldsTheActiveSsid() {
        assertEquals("Cafe:Guest", parseNmcliActiveSsid("no:Other\nyes:Cafe\\:Guest\n"))
        assertNull(parseNmcliActiveSsid("no:Other\n"))
    }
}
