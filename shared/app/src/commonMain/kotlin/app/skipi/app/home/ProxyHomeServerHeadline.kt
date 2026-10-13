// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.home

import features.proxy.server.model.extractLeadingCountryFlagOrNull
import features.proxy.server.model.stripLeadingCountryFlag

/** Display title and leading flag badge for one Home server row. */
data class ProxyHomeServerHeadline(
    val title: String,
    val flag: String?,
)

/** Applies the Android Home fallback rules using the portable shared country-flag parser. */
fun proxyHomeServerHeadline(
    remarks: String,
    protocol: String,
    serverId: Int,
    isStrategyGroup: Boolean,
): ProxyHomeServerHeadline {
    val flag = remarks.extractLeadingCountryFlagOrNull()
        ?: if (isStrategyGroup) "⚡" else null
    val title = remarks.stripLeadingCountryFlag()
        .ifBlank { protocol.ifBlank { "#$serverId" } }
    return ProxyHomeServerHeadline(title = title, flag = flag)
}
