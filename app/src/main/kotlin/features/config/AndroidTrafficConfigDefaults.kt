// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.subscription.DefaultSubscriptionUserAgent

internal fun androidDefaultTrafficConfigResourceSettings(): TrafficConfigResourceSettings =
    TrafficConfigResourceSettings(userAgent = DefaultSubscriptionUserAgent)

internal fun TrafficConfigResourceSettings.withAndroidDefaultUserAgent(): TrafficConfigResourceSettings =
    if (userAgent.isBlank()) copy(userAgent = DefaultSubscriptionUserAgent) else this

internal fun newAndroidTrafficConfig(
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
