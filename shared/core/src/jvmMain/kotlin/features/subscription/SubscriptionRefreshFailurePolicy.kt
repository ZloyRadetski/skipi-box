// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Applies the Android scheduled-refresh retry rules using the host's HTTP status extractor. */
fun isTransientSubscriptionRefreshFailure(
    error: Throwable,
    httpStatusCode: (Throwable) -> Int?,
): Boolean = generateSequence(error) { current -> current.cause }
    .any { cause ->
        val statusCode = httpStatusCode(cause)
        statusCode == 408 || statusCode == 429 ||
            (statusCode != null && statusCode in 500..599) ||
            cause is ConnectException ||
            cause is NoRouteToHostException ||
            cause is SocketException ||
            cause is SocketTimeoutException ||
            cause is UnknownHostException
    }
