// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFailureClassifierTest {
    @Test
    fun httpStatusesMatchTheAndroidRetryPolicyAcrossTheCauseChain() {
        listOf(408, 429, 500, 599).forEach { statusCode ->
            assertTrue(
                "HTTP $statusCode should be transient",
                isTransientSubscriptionFailure(SubscriptionHttpException(statusCode)),
            )
        }
        assertTrue(
            "a wrapped retryable HTTP failure should remain transient",
            isTransientSubscriptionFailure(
                IllegalStateException("scheduled refresh", SubscriptionHttpException(429)),
            ),
        )

        listOf(200, 400, 407, 409, 499, 600).forEach { statusCode ->
            assertFalse(
                "HTTP $statusCode should be permanent",
                isTransientSubscriptionFailure(SubscriptionHttpException(statusCode)),
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
                "${cause::class.simpleName} should remain transient when wrapped",
                isTransientSubscriptionFailure(IllegalStateException("refresh failed", cause)),
            )
        }

        assertFalse(isTransientSubscriptionFailure(IOException("unclassified I/O failure")))
        assertFalse(isTransientSubscriptionFailure(IllegalArgumentException("invalid payload")))
    }
}
