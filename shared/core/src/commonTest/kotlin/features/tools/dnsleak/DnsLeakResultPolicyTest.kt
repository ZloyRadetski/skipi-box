// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.dnsleak

import kotlin.test.Test
import kotlin.test.assertEquals

class DnsLeakResultPolicyTest {
    @Test
    fun comparesOnlyAvailableResolverCountriesToTheExit() {
        assertEquals(DnsLeakVerdict.Unknown, DnsLeakResultPolicy.verdict(emptyList(), "US"))
        assertEquals(DnsLeakVerdict.Unknown, DnsLeakResultPolicy.verdict(listOf(""), "US"))
        assertEquals(DnsLeakVerdict.NoLeak, DnsLeakResultPolicy.verdict(listOf("us", "US"), "US"))
        assertEquals(DnsLeakVerdict.SuspectedLeak, DnsLeakResultPolicy.verdict(listOf("US", "DE"), "US"))
        assertEquals(DnsLeakFailureKind.NoInternet, DnsLeakResultPolicy.failureKind(false))
        assertEquals(DnsLeakFailureKind.TunnelNotPassing, DnsLeakResultPolicy.failureKind(true))
    }
}
