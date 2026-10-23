// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionRefreshFailurePolicyTest {
    @Test
    fun httpStatusesMatchTheAndroidRetryPolicyAcrossTheCauseChain() {
        listOf(408, 429, 500, 599).forEach { statusCode ->
            assertTrue(
                isTransientSubscriptionRefreshFailure(TestHttpFailure(statusCode), ::httpStatusCode),
                "HTTP $statusCode should be transient",
            )
        }
        assertTrue(
            isTransientSubscriptionRefreshFailure(
                IllegalStateException("scheduled refresh", TestHttpFailure(429)),
                ::httpStatusCode,
            ),
            "a wrapped retryable HTTP failure should remain transient",
        )

        listOf(200, 400, 407, 409, 499, 600).forEach { statusCode ->
            assertFalse(
                isTransientSubscriptionRefreshFailure(TestHttpFailure(statusCode), ::httpStatusCode),
                "HTTP $statusCode should be permanent",
            )
        }
    }

    @Test
    fun onlyTheAndroidListedConnectionFailuresAreTransient() {
        val connectionFailures = listOf<Throwable>(
            UnknownHostException("dns"),
            SocketTimeoutException("timeout"),
            ConnectException("refused"),
            NoRouteToHostException("unreachable"),
            SocketException("socket"),
        )

        connectionFailures.forEach { cause ->
            assertTrue(
                isTransientSubscriptionRefreshFailure(
                    IllegalStateException("refresh failed", cause),
                    ::httpStatusCode,
                ),
                "${cause::class.simpleName} should remain transient when wrapped",
            )
        }

        assertFalse(isTransientSubscriptionRefreshFailure(IOException("unclassified I/O failure"), ::httpStatusCode))
        assertFalse(
            isTransientSubscriptionRefreshFailure(IllegalArgumentException("invalid payload"), ::httpStatusCode),
        )
    }

    private fun httpStatusCode(error: Throwable): Int? = (error as? TestHttpFailure)?.statusCode

    private class TestHttpFailure(val statusCode: Int) : IllegalStateException("HTTP $statusCode")
}
