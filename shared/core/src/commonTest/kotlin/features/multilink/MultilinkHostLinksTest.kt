// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.multilink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultilinkHostLinksTest {
    private fun net(
        handle: Long,
        transport: HostTransport,
        iface: String,
        vararg addresses: String,
        metered: Boolean = false,
    ) = HostNetworkSnapshot(handle, transport, metered, iface, addresses.toList())

    @Test
    fun wifiAndMobileGetKindIdsAndPhoneFlag() {
        val links = buildHostLinks(
            listOf(
                net(2, HostTransport.Cellular, "rmnet0", "10.20.30.40", metered = true),
                net(1, HostTransport.WiFi, "wlan0", "192.168.1.5"),
            ),
            mobileName = "Mobile data",
        )
        assertEquals(listOf("mobile", "wifi"), links.map { it.id })
        assertEquals("Mobile data", links[0].name)
        assertEquals(MultilinkLinkKind.Mobile, links[0].kind)
        assertTrue(links[0].phone)
        assertEquals("Wi-Fi", links[1].name)
        assertFalse(links[1].phone)
        assertEquals(1L, links[1].handle)
    }

    @Test
    fun secondNetworkOfAKindGetsIdFromInterfaceIndependentOfInputOrder() {
        val a = net(10, HostTransport.WiFi, "wlan0", "192.168.1.5")
        val b = net(11, HostTransport.WiFi, "wlan1", "192.168.2.5")
        val first = buildHostLinks(listOf(a, b))
        val second = buildHostLinks(listOf(b, a))
        assertEquals(first, second)
        assertEquals(listOf("wifi", "wifi:wlan1"), first.map { it.id })
        assertEquals("Wi-Fi (wlan1)", first[1].name)
        assertEquals(10L, first[0].handle)
    }

    @Test
    fun networksWithoutUsableAddressAreSkippedAndDoNotTakeTheId() {
        val links = buildHostLinks(
            listOf(
                net(1, HostTransport.WiFi, "wlan0", "169.254.3.4", "fe80::1%wlan0"),
                net(2, HostTransport.WiFi, "wlan1", "192.168.2.5"),
            ),
        )
        assertEquals(listOf("wifi"), links.map { it.id })
        assertEquals(2L, links.single().handle)
    }

    @Test
    fun ipv6OnlyLinkKeepsGlobalAddressWithoutScope() {
        val links = buildHostLinks(listOf(net(1, HostTransport.Cellular, "rmnet1", "fe80::1", "2001:db8::7%rmnet1")))
        assertEquals("", links.single().ipv4)
        assertEquals("2001:db8::7", links.single().ipv6)
    }

    @Test
    fun ipv6Classification() {
        assertTrue(isGlobalIpv6("2001:db8::1"))
        assertTrue(isGlobalIpv6("2a00:1450:4001:81b::200e%wlan0"))
        assertFalse(isGlobalIpv6("fe80::1"))
        assertFalse(isGlobalIpv6("febf::1"))
        assertFalse(isGlobalIpv6("fd00::1"))
        assertFalse(isGlobalIpv6("fc00::1"))
        assertFalse(isGlobalIpv6("::1"))
        assertFalse(isGlobalIpv6("0:0:0:0:0:0:0:1"))
        assertFalse(isGlobalIpv6("::"))
        assertFalse(isGlobalIpv6("ff02::1"))
        assertFalse(isGlobalIpv6("fec0::1"))
        assertFalse(isGlobalIpv6("192.168.0.1"))
    }

    @Test
    fun ipv4KeepsFirstUsableAddress() {
        val links = buildHostLinks(listOf(net(1, HostTransport.Ethernet, "eth0", "127.0.0.1", "169.254.1.1", "10.0.0.2", "10.0.0.3")))
        assertEquals("10.0.0.2", links.single().ipv4)
        assertEquals(MultilinkLinkKind.Ethernet, links.single().kind)
    }

    @Test
    fun linksEncodeForCore() {
        val json = buildHostLinks(listOf(net(7, HostTransport.WiFi, "wlan0", "192.168.1.5", metered = true))).toCoreLinksJson()
        assertTrue(json.contains("\"id\":\"wifi\""))
        assertTrue(json.contains("\"kind\":\"wifi\""))
        assertTrue(json.contains("\"handle\":7"))
        assertTrue(json.contains("\"phone\":true"))
        assertTrue(json.contains("\"ipv4\":\"192.168.1.5\""))
        assertEquals("[]", emptyList<MultilinkHostLink>().toCoreLinksJson())
    }
}
