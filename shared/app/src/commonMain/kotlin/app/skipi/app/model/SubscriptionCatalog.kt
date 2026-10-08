// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

/** Ordered subscriptions and the persisted high-water ID used by both hosts. */
data class SubscriptionCatalog(
    val subscriptions: List<SubscriptionRecord> = emptyList(),
    val nextSubscriptionId: Int = 1,
)
