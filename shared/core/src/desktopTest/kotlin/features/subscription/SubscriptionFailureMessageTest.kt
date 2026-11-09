// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionFailureMessageTest {
    private class HttpFailure(message: String) : RuntimeException(message)

    private fun message(error: Throwable) = subscriptionFailureMessage(error) { it is HttpFailure }

    @Test
    fun anHttpFailureKeepsItsOwnMessageEvenWhenWrapped() {
        assertEquals("HTTP 403", message(HttpFailure("HTTP 403")))
        assertEquals("HTTP 403", message(IllegalStateException("outer", HttpFailure("HTTP 403"))))
    }

    @Test
    fun networkFailuresGetAShortExplanationFromTheirRootCause() {
        assertEquals("DNS error: no.such.host", message(RuntimeException(UnknownHostException("no.such.host"))))
        assertEquals("DNS error: Unknown host", message(UnknownHostException()))
        assertEquals("Timeout connecting to server", message(SocketTimeoutException("slow")))
        assertEquals("Connection failed: refused", message(ConnectException("refused")))
        assertEquals("Connection failed: Refused", message(ConnectException()))
        assertEquals("Network error: reset", message(SocketException("reset")))
    }

    @Test
    fun anEmptySubscriptionIsNamedAsSuch() {
        assertEquals(
            "Subscription returned empty list",
            message(IllegalStateException("Subscription imported no proxy servers")),
        )
    }

    @Test
    fun anyOtherMessageIsPassedThroughAndAMissingOneBecomesUnknown() {
        assertEquals("bad payload", message(IllegalArgumentException("bad payload")))
        assertEquals("Unknown error", message(IllegalStateException()))
    }
}
