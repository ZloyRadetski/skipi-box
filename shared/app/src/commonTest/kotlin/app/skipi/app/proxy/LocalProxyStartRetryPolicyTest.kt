// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalProxyStartRetryPolicyTest {
    @Test
    fun bindFailureClassifierMatchesAndroidMarkersThroughCauseChainCaseInsensitively() {
        val failures = listOf(
            IllegalStateException("Address already in use"),
            IllegalStateException("failed to listen TCP 127.0.0.1:20001"),
            IllegalStateException("start failed", IllegalArgumentException("LISTEN TCP 127.0.0.1:20001")),
        )

        failures.forEach { failure ->
            assertTrue(LocalProxyStartRetryPolicy.isLocalProxyBindFailure(failure))
        }
        assertFalse(LocalProxyStartRetryPolicy.isLocalProxyBindFailure(IllegalStateException("connection refused")))
        assertFalse(LocalProxyStartRetryPolicy.isLocalProxyBindFailure(IllegalStateException("proxy port is occupied")))
    }

    @Test
    fun onlyDynamicBindFailureBeforeTheFinalAttemptCanRetry() {
        val bindFailure = IllegalStateException("Address already in use")
        val unrelatedFailure = IllegalStateException("invalid Xray configuration")

        assertEquals(2, LocalProxyStartRetryPolicy.MaxAttempts)
        assertTrue(
            LocalProxyStartRetryPolicy.shouldRetry(
                dynamicPortEnabled = true,
                completedAttempts = 1,
                failure = bindFailure,
            ),
        )
        assertFalse(
            LocalProxyStartRetryPolicy.shouldRetry(
                dynamicPortEnabled = false,
                completedAttempts = 1,
                failure = bindFailure,
            ),
        )
        assertFalse(
            LocalProxyStartRetryPolicy.shouldRetry(
                dynamicPortEnabled = true,
                completedAttempts = 2,
                failure = bindFailure,
            ),
        )
        assertFalse(
            LocalProxyStartRetryPolicy.shouldRetry(
                dynamicPortEnabled = true,
                completedAttempts = 1,
                failure = unrelatedFailure,
            ),
        )
    }
}
