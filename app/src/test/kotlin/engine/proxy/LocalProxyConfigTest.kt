// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.proxy

import app.AppState
import engine.vpn.toVpnAppendHttpProxyOptions
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProxyConfigTest {
    @Test
    fun dynamicPortAllocatesANewEphemeralPortInsteadOfKeepingConfiguredPort() {
        val resolved = AppState(
            localProxyPort = "1",
            enableDynamicLocalProxyPort = true,
        ).withResolvedDynamicLocalProxyPort()

        val port = resolved.localProxyPort.toInt()
        assertTrue(port in 1..65_535)
        assertNotEquals(1, port)
    }

    @Test
    fun dynamicPortExcludesThePortFromAPreviousFailedStart() {
        val resolved = AppState(enableDynamicLocalProxyPort = true)
            .withResolvedDynamicLocalProxyPort(excludedPorts = setOf(20_003))

        assertNotEquals("20003", resolved.localProxyPort)
    }

    @Test
    fun appendedHttpProxyDoesNotReuseTheStatsApiPort() {
        val localProxy = LocalProxyOptions(
            listenAddress = LocalProxyLoopbackAddress,
            port = 20_001,
            username = "",
            password = "",
        )

        val httpProxy = AppState(enableVpnAppendHttpProxy = true)
            .toVpnAppendHttpProxyOptions(localProxy, excludedPorts = setOf(20_002))

        assertTrue(httpProxy.enabled)
        assertNotEquals(localProxy.port, httpProxy.port)
        assertNotEquals(20_002, httpProxy.port)
    }

    @Test
    fun localPortBindingFailureIsRecognizedForRetry() {
        assertTrue(IllegalStateException("failed to listen TCP 127.0.0.1:20001").isLocalPortBindFailure())
        assertTrue(IllegalStateException("Address already in use").isLocalPortBindFailure())
    }
}
