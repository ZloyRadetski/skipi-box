// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.network

import engine.proxy.LocalProxyRuntime
import java.io.IOException
import java.net.URL
import kotlin.test.assertFailsWith
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

    @Test
    fun tunnel_only_connection_never_falls_back_to_direct_network() {
        LocalProxyRuntime.clear()
        try {
            assertFailsWith<IOException> {
                TunnelNetworks.openTunnelHttpConnection(
                    context = null,
                    url = URL("https://speed.cloudflare.com/"),
                )
            }
        } finally {
            LocalProxyRuntime.clear()
        }
    }
}
