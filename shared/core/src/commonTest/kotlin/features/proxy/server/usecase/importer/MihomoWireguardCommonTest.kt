// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** WireGuard and AmneziaWG nodes read their address and reserved bytes the same way, each naming itself in errors. */
class MihomoWireguardCommonTest {
    private fun wireguard(extra: Map<String, Any?>) = (
        mapOf("name" to "n", "server" to "s", "port" to 1, "private-key" to "k", "public-key" to "p") + extra
        ).toMihomoWireguardProxyServer()

    private fun amnezia(extra: Map<String, Any?>) = (
        mapOf("name" to "n", "server" to "s", "port" to 1, "private-key" to "k", "public-key" to "p") + extra
        ).toMihomoAmneziaWgProxyServer()

    @Test
    fun addressesGetTheirDefaultPrefixAndKeepAnExplicitOne() {
        val both = mapOf("ip" to "10.0.0.2", "ipv6" to "fd00::2/64")

        assertEquals("10.0.0.2/32,fd00::2/64", wireguard(both).address)
        assertEquals("10.0.0.2/32,fd00::2/64", amnezia(both).address)
        assertEquals("fd00::2/128", wireguard(mapOf("ipv6" to "fd00::2")).address)
        assertEquals("fd00::2/128", amnezia(mapOf("ipv6" to "fd00::2")).address)
    }

    @Test
    fun reservedBytesAreReadFromAListOrACommaString() {
        assertEquals("1,2,3", wireguard(mapOf("ip" to "10.0.0.2", "reserved" to listOf(1, 2, 3))).reserved)
        assertEquals("1,2,3", amnezia(mapOf("ip" to "10.0.0.2", "reserved" to "1,2,3")).reserved)
        assertEquals("0,0,0", wireguard(mapOf("ip" to "10.0.0.2")).reserved)
        assertEquals("0,0,0", amnezia(mapOf("ip" to "10.0.0.2")).reserved)
    }

    @Test
    fun aNodeWithoutAnAddressIsRejectedNamingItsOwnProtocol() {
        assertEquals(
            "WireGuard ip or ipv6 is required",
            assertFailsWith<UnsupportedMihomoProxyException> { wireguard(emptyMap()) }.message,
        )
        assertEquals(
            "AmneziaWG ip or ipv6 is required",
            assertFailsWith<UnsupportedMihomoProxyException> { amnezia(emptyMap()) }.message,
        )
    }

    @Test
    fun reservedBytesThatAreNotThreeNumbersAreRejectedNamingTheProtocol() {
        assertEquals(
            "WireGuard reserved must be three numeric bytes",
            assertFailsWith<UnsupportedMihomoProxyException> {
                wireguard(mapOf("ip" to "10.0.0.2", "reserved" to listOf(1, 2)))
            }.message,
        )
        assertEquals(
            "AmneziaWG reserved must be three numeric bytes",
            assertFailsWith<UnsupportedMihomoProxyException> {
                amnezia(mapOf("ip" to "10.0.0.2", "reserved" to "1,2,x"))
            }.message,
        )
    }
}
