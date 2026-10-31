// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HostAddressLookupTest {
    @Test
    fun aNameResolvesToItsIpv4AddressForDisplay() = runBlocking {
        assertEquals("127.0.0.1", lookupHostAddress("localhost"))
    }

    @Test
    fun aNameThatDoesNotResolveGivesNothingNotAnException() = runBlocking {
        assertNull(lookupHostAddress("no-such-host.invalid"))
    }
}
