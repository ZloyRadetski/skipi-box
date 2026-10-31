// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The TCP ping connects from inside SKIPI Core, whose sockets stay off the tunnel, so a server's
 * latency is the path to it even while the VPN is on. A socket opened by the JVM would be taken
 * by the tunnel and answer with a made-up zero.
 */
class DesktopCoreTcpConnectorTest {
    private fun tester(measure: (String, Int, Int) -> Result<Long>) =
        DesktopServerLatencyTester(connector = DesktopCoreTcpConnector(measure))

    @Test
    fun theConnectGoesToCoreWithTheHostPortAndTimeout() {
        var seen: Triple<String, Int, Int>? = null
        val result = tester { host, port, timeout -> seen = Triple(host, port, timeout); Result.success(31L) }
            .measure("server.example", 443, Duration.ofMillis(2_500))

        assertEquals(Triple("server.example", 443, 2_500), seen)
        assertTrue(result is DesktopServerLatencyResult.Success)
    }

    @Test
    fun aCoreTimeoutIsATimeout() {
        for (message in listOf("connect to s:443: dial tcp 1.2.3.4:443: i/o timeout", "context deadline exceeded")) {
            val result = tester { _, _, _ -> Result.failure(IllegalStateException(message)) }
                .measure("server.example", 443, Duration.ofSeconds(1))

            assertEquals(DesktopServerLatencyResult.Timeout, result, message)
        }
    }

    @Test
    fun anyOtherCoreFailureIsAnErrorWithItsReason() {
        val result = tester { _, _, _ -> Result.failure(IllegalStateException("connect to s:443: connection refused")) }
            .measure("server.example", 443, Duration.ofSeconds(1))

        assertEquals("connect to s:443: connection refused", (result as DesktopServerLatencyResult.Error).message)
    }
}
