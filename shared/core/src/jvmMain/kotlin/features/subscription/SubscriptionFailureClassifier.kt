// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.io.IOException

class SubscriptionHttpException(
    val statusCode: Int,
    val serverMessage: String? = null,
    val responseBody: String? = null,
) : IOException(formatHttpExceptionMessage(statusCode, serverMessage))

fun formatHttpExceptionMessage(statusCode: Int, serverMessage: String?): String {
    return if (!serverMessage.isNullOrBlank()) {
        "HTTP $statusCode: $serverMessage"
    } else {
        "HTTP $statusCode"
    }
}

/**
 * Whether a subscription refresh failure is worth retrying later. A JDK `HttpClient` timeout counts as one; it is
 * matched by name because `java.net.http` does not exist on Android.
 */
fun isTransientSubscriptionFailure(error: Throwable): Boolean {
    return isTransientSubscriptionRefreshFailure(
        error = error,
        httpStatusCode = { cause -> (cause as? SubscriptionHttpException)?.statusCode },
        isHostConnectionFailure = { cause -> cause.javaClass.name == "java.net.http.HttpTimeoutException" },
    )
}
