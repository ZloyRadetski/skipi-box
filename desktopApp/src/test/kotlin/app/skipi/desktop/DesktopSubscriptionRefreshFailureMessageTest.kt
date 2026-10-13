// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSubscriptionRefreshFailureMessageTest {
    @Test
    fun unknownHostUsesAndroidDnsMessage() {
        assertEquals(
            "DNS error: provider.example",
            desktopSubscriptionRefreshFailureMessage(UnknownHostException("provider.example")),
        )
    }

    @Test
    fun timeoutUsesAndroidTimeoutMessageEvenWhenWrapped() {
        assertEquals(
            "Timeout connecting to server",
            desktopSubscriptionRefreshFailureMessage(IllegalStateException("request failed", SocketTimeoutException("slow"))),
        )
    }

    @Test
    fun httpFailureKeepsTheExistingDesktopExceptionMessage() {
        val failure = DesktopSubscriptionHttpException(503, "maintenance")

        assertEquals(failure.message, desktopSubscriptionRefreshFailureMessage(failure))
    }
}
