// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** JVM exception translation matching Android's subscription refresh message adapter. */
internal fun desktopSubscriptionRefreshFailureMessage(error: Throwable): String {
    val unwrapped = generateSequence(error) { it.cause }.lastOrNull() ?: error
    return when {
        error is DesktopSubscriptionHttpException -> error.message.orEmpty()
        unwrapped is DesktopSubscriptionHttpException -> unwrapped.message.orEmpty()
        unwrapped is UnknownHostException -> "DNS error: ${unwrapped.message ?: "Unknown host"}"
        unwrapped is SocketTimeoutException -> "Timeout connecting to server"
        unwrapped is ConnectException -> "Connection failed: ${unwrapped.message ?: "Refused"}"
        unwrapped is SocketException -> "Network error: ${unwrapped.message ?: "Socket exception"}"
        error.message?.contains("imported no proxy servers", ignoreCase = true) == true ->
            "Subscription returned empty list"
        !error.message.isNullOrBlank() -> error.message!!
        else -> unwrapped.localizedMessage ?: "Unknown error"
    }
}
