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

fun isTransientSubscriptionFailure(error: Throwable): Boolean {
    return isTransientSubscriptionRefreshFailure(
        error = error,
        httpStatusCode = { cause -> (cause as? SubscriptionHttpException)?.statusCode },
    )
}
