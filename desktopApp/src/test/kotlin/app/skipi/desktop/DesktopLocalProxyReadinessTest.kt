// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.IOException
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopLocalProxyReadinessTest {
    @Test
    fun succeeds_after_a_retry_when_xray_is_alive() {
        var now = 0L
        var attempts = 0
        val probe = DesktopLocalProxyReadiness(
            tcpConnect = { _, _, _ ->
                attempts++
                if (attempts < 2) throw IOException("not bound yet")
            },
            monotonicMillis = { now },
            sleep = { duration -> now += duration },
        )

        assertTrue(
            probe.awaitLoopbackHttpEndpoint(
                port = 10809,
                timeout = Duration.ofSeconds(1),
                processState = { DesktopXrayProcessState(isRunning = true) },
            ).isSuccess,
        )
        assertEquals(2, attempts)
    }

    @Test
    fun fails_without_touching_the_os_proxy_when_xray_exits() {
        val probe = DesktopLocalProxyReadiness(
            tcpConnect = { _, _, _ -> throw IOException("refused") },
            sleep = { },
        )

        val result = probe.awaitLoopbackHttpEndpoint(
            port = 10809,
            timeout = Duration.ofSeconds(1),
            processState = { DesktopXrayProcessState(isRunning = false) },
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("stopped"))
    }

    @Test
    fun rechecks_that_xray_is_alive_after_the_port_accepts_connections() {
        var checks = 0
        val probe = DesktopLocalProxyReadiness(
            tcpConnect = { _, _, _ -> },
            sleep = { },
        )

        val result = probe.awaitLoopbackHttpEndpoint(
            port = 10809,
            timeout = Duration.ofSeconds(1),
            processState = {
                checks++
                DesktopXrayProcessState(isRunning = checks == 1)
            },
        )

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("while"))
    }
}
