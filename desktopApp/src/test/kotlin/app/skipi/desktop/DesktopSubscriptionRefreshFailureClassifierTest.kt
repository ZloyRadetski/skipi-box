// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.IOException
import java.net.http.HttpTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshFailureClassifierTest {
    @Test
    fun httpStatusAndJdkTimeoutFailuresMatchTheSharedAndroidRetryPolicy() {
        listOf(408, 429, 500, 599).forEach { statusCode ->
            assertTrue(isTransientDesktopSubscriptionRefreshFailure(DesktopSubscriptionHttpException(statusCode, "")))
            assertTrue(
                isTransientDesktopSubscriptionRefreshFailure(
                    IllegalStateException("scheduled refresh", DesktopSubscriptionHttpException(statusCode, "")),
                ),
            )
        }

        listOf(200, 400, 407, 409, 499, 600).forEach { statusCode ->
            assertFalse(isTransientDesktopSubscriptionRefreshFailure(DesktopSubscriptionHttpException(statusCode, "")))
        }

        assertTrue(isTransientDesktopSubscriptionRefreshFailure(HttpTimeoutException("request timed out")))
        assertTrue(
            isTransientDesktopSubscriptionRefreshFailure(
                IllegalStateException("scheduled refresh", HttpTimeoutException("request timed out")),
            ),
        )
        assertFalse(isTransientDesktopSubscriptionRefreshFailure(IOException("generic I/O failure")))
        assertFalse(isTransientDesktopSubscriptionRefreshFailure(IllegalArgumentException("invalid payload")))
    }
}
