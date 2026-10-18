// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

/** Shared classifier and attempt bound for Android's existing dynamic-port startup retry. */
object LocalProxyStartRetryPolicy {
    const val MaxAttempts = 2

    fun isLocalProxyBindFailure(failure: Throwable): Boolean =
        generateSequence(failure) { cause -> cause.cause }
            .mapNotNull(Throwable::message)
            .any { message ->
                val normalized = message.lowercase()
                "address already in use" in normalized ||
                    "failed to listen" in normalized ||
                    "listen tcp" in normalized
            }

    fun shouldRetry(
        dynamicPortEnabled: Boolean,
        completedAttempts: Int,
        failure: Throwable,
    ): Boolean =
        dynamicPortEnabled &&
            completedAttempts < MaxAttempts &&
            isLocalProxyBindFailure(failure)
}
