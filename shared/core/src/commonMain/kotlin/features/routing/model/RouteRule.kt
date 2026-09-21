// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.model

import kotlinx.serialization.Serializable

const val DefaultRouteOutboundTag = "proxy"

@Serializable
data class RouteRule(
    val id: Int = 0,
    val remarks: String = "",
    val outboundTag: String = DefaultRouteOutboundTag,
    val domain: List<String> = emptyList(),
    val ip: List<String> = emptyList(),
    val process: List<String> = emptyList(),
    val port: String = "",
    val protocol: String = "",
    val network: String = "",
    val enabled: Boolean = true,
)
