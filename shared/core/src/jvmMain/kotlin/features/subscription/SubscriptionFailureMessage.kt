// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The short text shown when a subscription refresh fails. An HTTP failure (recognised by the host through
 * [isHttpFailure], since each host has its own exception type) keeps its own message; a network failure is
 * explained from its root cause.
 */
fun subscriptionFailureMessage(error: Throwable, isHttpFailure: (Throwable) -> Boolean): String {
    val rootCause = generateSequence(error) { it.cause }.lastOrNull() ?: error
    return when {
        isHttpFailure(error) -> error.message.orEmpty()
        isHttpFailure(rootCause) -> rootCause.message.orEmpty()
        rootCause is UnknownHostException -> "DNS error: ${rootCause.message ?: "Unknown host"}"
        rootCause is SocketTimeoutException -> "Timeout connecting to server"
        rootCause is ConnectException -> "Connection failed: ${rootCause.message ?: "Refused"}"
        rootCause is SocketException -> "Network error: ${rootCause.message ?: "Socket exception"}"
        error.message?.contains("imported no proxy servers", ignoreCase = true) == true -> "Subscription returned empty list"
        error.message?.isNotBlank() == true -> error.message.orEmpty()
        else -> rootCause.localizedMessage ?: "Unknown error"
    }
}
