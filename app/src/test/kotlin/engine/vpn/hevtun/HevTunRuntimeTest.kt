// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HevTunRuntimeTest {
    @Test
    fun readiness_waits_until_the_native_tunnel_accepts_traffic() = runBlocking {
        var readinessChecks = 0

        val ready = awaitHevTunReadiness(
            isRunning = { true },
            isReady = { ++readinessChecks >= 2 },
            timeoutMillis = 100L,
            pollIntervalMillis = 1L,
        )

        assertTrue(ready)
    }

    @Test
    fun readiness_fails_when_native_thread_exits_before_initialization() = runBlocking {
        val ready = awaitHevTunReadiness(
            isRunning = { false },
            isReady = { false },
            timeoutMillis = 100L,
            pollIntervalMillis = 1L,
        )

        assertFalse(ready)
    }
}
