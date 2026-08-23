// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelNetworksTest {
    @Test
    fun socksAuthenticatorAcceptsOnlyTheConfiguredLocalPort() {
        assertTrue(isSocksProxyAuthenticationRequest(requestingPort = 2080, proxyPort = 2080))
        assertFalse(isSocksProxyAuthenticationRequest(requestingPort = 2081, proxyPort = 2080))
        assertFalse(isSocksProxyAuthenticationRequest(requestingPort = 0, proxyPort = 0))
    }
}
