// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.subscription.DefaultSubscriptionUserAgent

fun androidDefaultTrafficConfigResourceSettings(): TrafficConfigResourceSettings =
    TrafficConfigResourceSettings(userAgent = DefaultSubscriptionUserAgent)

fun TrafficConfigResourceSettings.withAndroidDefaultUserAgent(): TrafficConfigResourceSettings =
    if (userAgent.isBlank()) copy(userAgent = DefaultSubscriptionUserAgent) else this

fun newAndroidTrafficConfig(
    id: Int,
    name: String,
    rawConfig: String,
    sourceUrl: String = "",
    lastUpdatedAtMillis: Long = 0L,
): TrafficConfigState = TrafficConfigState(
    id = id,
    name = name,
    rawConfig = rawConfig,
    sourceUrl = sourceUrl,
    lastUpdatedAtMillis = lastUpdatedAtMillis,
    resourceSettings = androidDefaultTrafficConfigResourceSettings(),
)
