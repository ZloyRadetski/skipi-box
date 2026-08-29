// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

/** Platform-independent result of downloading a subscription. */
data class SubscriptionFetchResponse(
    val body: String,
    val headers: Map<String, String> = emptyMap(),
)
