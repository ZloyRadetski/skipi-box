// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.subscription.SubscriptionHttpException
import java.io.IOException
import features.subscription.isTransientSubscriptionFailure
import java.net.http.HttpTimeoutException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopSubscriptionRefreshFailureClassifierTest {
    @Test
    fun httpStatusAndJdkTimeoutFailuresMatchTheSharedAndroidRetryPolicy() {
        listOf(408, 429, 500, 599).forEach { statusCode ->
            assertTrue(isTransientSubscriptionFailure(SubscriptionHttpException(statusCode, responseBody = "")))
            assertTrue(
                isTransientSubscriptionFailure(
                    IllegalStateException("scheduled refresh", SubscriptionHttpException(statusCode, responseBody = "")),
                ),
            )
        }

        listOf(200, 400, 407, 409, 499, 600).forEach { statusCode ->
            assertFalse(isTransientSubscriptionFailure(SubscriptionHttpException(statusCode, responseBody = "")))
        }

        assertTrue(isTransientSubscriptionFailure(HttpTimeoutException("request timed out")))
        assertTrue(
            isTransientSubscriptionFailure(
                IllegalStateException("scheduled refresh", HttpTimeoutException("request timed out")),
            ),
        )
        assertFalse(isTransientSubscriptionFailure(IOException("generic I/O failure")))
        assertFalse(isTransientSubscriptionFailure(IllegalArgumentException("invalid payload")))
    }
}
