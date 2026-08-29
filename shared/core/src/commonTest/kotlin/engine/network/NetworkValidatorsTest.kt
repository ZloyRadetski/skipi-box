// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetworkValidatorsTest {
    @Test
    fun parsesValidIpv4AndIpv6Cidrs() {
        assertEquals(NetworkCidrAddress("10.0.0.1", 24), parseCidrAddressOrNull("10.0.0.1/24"))
        assertEquals(NetworkCidrAddress("2001:db8::1", 64), parseCidrAddressOrNull("2001:db8::1/64"))
    }

    @Test
    fun rejectsInvalidPortableNetworkSettings() {
        assertNull(parseCidrAddressOrNull("10.0.0.1/33"))
        assertNull(parseCidrAddressOrNull("2001:db8::1/129"))
        assertFalse(isIpOrCidrAddress("not an address"))
        assertFalse(isPortList("443-80"))
    }

    @Test
    fun acceptsValidPortFormsWithoutPlatformApis() {
        assertEquals(65_535, "65535".toPortOrNull())
        assertNull("65536".toPortOrNull())
        assertTrue(isPortList("80,443,10-20"))
    }
}
