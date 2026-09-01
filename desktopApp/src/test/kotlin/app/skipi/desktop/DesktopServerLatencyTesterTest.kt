// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.proxy.server.model.ProxyServer
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DesktopServerLatencyTesterTest {
    @Test
    fun measuresSuccessfulTcpConnectWithInjectedClock() {
        var receivedHost = ""
        var receivedPort = 0
        var receivedTimeoutMillis = 0
        val result = DesktopServerLatencyTester(
            connector = DesktopTcpConnector { host, port, timeoutMillis ->
                receivedHost = host
                receivedPort = port
                receivedTimeoutMillis = timeoutMillis
            },
            clock = sequenceClock(1_000_000L, 38_500_000L),
        ).measure("  edge.example  ", 443, Duration.ofSeconds(2))

        assertEquals(DesktopServerLatencyResult.Success(37), result)
        assertEquals("edge.example", receivedHost)
        assertEquals(443, receivedPort)
        assertEquals(2_000, receivedTimeoutMillis)
    }

    @Test
    fun reportsSocketTimeoutWithoutTreatingItAsGenericFailure() {
        val result = DesktopServerLatencyTester(
            connector = DesktopTcpConnector { _, _, _ -> throw SocketTimeoutException("timed out") },
            clock = sequenceClock(0L),
        ).measure("edge.example", 443, Duration.ofMillis(250))

        assertEquals(DesktopServerLatencyResult.Timeout, result)
    }

    @Test
    fun exposesConnectionFailureForServerCardFeedback() {
        val result = DesktopServerLatencyTester(
            connector = DesktopTcpConnector { _, _, _ -> throw IOException("Connection refused") },
            clock = sequenceClock(0L),
        ).measure("edge.example", 443)

        val error = assertIs<DesktopServerLatencyResult.Error>(result)
        assertEquals("Connection refused", error.message)
        assertIs<IOException>(error.cause)
    }

    @Test
    fun rejectsInvalidEndpointBeforeOpeningSocket() {
        var connectorCalled = false
        val tester = DesktopServerLatencyTester(
            connector = DesktopTcpConnector { _, _, _ -> connectorCalled = true },
            clock = sequenceClock(0L),
        )

        val result = tester.measure(" ", 0, Duration.ZERO)

        assertIs<DesktopServerLatencyResult.Error>(result)
        assertEquals(false, connectorCalled)
    }

    @Test
    fun roundsSubMillisecondTimeoutUpForSocketApi() {
        var timeoutMillis = 0
        val tester = DesktopServerLatencyTester(
            connector = DesktopTcpConnector { _, _, receivedTimeout -> timeoutMillis = receivedTimeout },
            clock = sequenceClock(0L, 0L),
        )

        assertEquals(
            DesktopServerLatencyResult.Success(0),
            tester.measure("127.0.0.1", 1080, Duration.ofNanos(1)),
        )
        assertEquals(1, timeoutMillis)
    }

    @Test
    fun extractsEndpointFromPortableSkipiProxyModel() {
        val server = ProxyServer.parse(
            "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@edge.example:443#Latency",
        )

        assertEquals(DesktopServerTcpEndpoint("edge.example", 443), server.desktopTcpEndpointOrNull())
    }
}

private fun sequenceClock(vararg readings: Long): DesktopMonotonicClock {
    var index = 0
    return DesktopMonotonicClock {
        readings.getOrElse(index++) { readings.lastOrNull() ?: 0L }
    }
}
