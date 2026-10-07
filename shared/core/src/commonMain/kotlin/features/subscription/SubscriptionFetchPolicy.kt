// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** Request behavior shared by Android and Desktop subscription transports. */
object SubscriptionFetchPolicy {
    const val defaultTimeoutSeconds: Int = 10
    const val maxRedirects: Int = 2

    fun timeoutSeconds(configuredSeconds: Int): Int = configuredSeconds.coerceIn(3, 600)
}
