// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

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
    return generateSequence(error) { current -> current.cause }
        .any { cause ->
            when (cause) {
                is SubscriptionHttpException ->
                    cause.statusCode == 408 || cause.statusCode == 429 || cause.statusCode in 500..599

                is ConnectException,
                is NoRouteToHostException,
                is SocketException,
                is SocketTimeoutException,
                is UnknownHostException,
                -> true

                else -> false
            }
        }
}
