// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.routing.model.RouteRule
import features.routing.model.DefaultRouteOutboundTag as SharedDefaultRouteOutboundTag
import features.subscription.DefaultSubscriptionGroupId
import features.subscription.DefaultSubscriptionUserAgent

const val DefaultRouteOutboundTag = SharedDefaultRouteOutboundTag
const val MinTrafficStatsNotificationRefreshIntervalSeconds = 1
const val MaxTrafficStatsNotificationRefreshIntervalSeconds = 10
const val DefaultTrafficStatsNotificationRefreshIntervalSeconds = 2

fun generateRandomProxyCredential(prefix: String = "skipi_"): String {
    val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    val randomPart = (1..6).map { chars.random() }.joinToString("")
    return "$prefix$randomPart"
}

val DefaultSubscriptionGroups = listOf(
    SubscriptionGroupState(
        id = DefaultSubscriptionGroupId,
        name = "Default",
        url = "",
        userAgent = DefaultSubscriptionUserAgent,
        updateInterval = "",
        enabled = true,
        builtIn = true,
    ),
)

val DefaultRouteRules: List<RouteRule> = emptyList()
