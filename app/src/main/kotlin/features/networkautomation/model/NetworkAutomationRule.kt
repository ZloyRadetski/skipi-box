// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class NetworkRuleType {
    CELLULAR,
    ANY_WIFI,
    SPECIFIC_WIFI,
}

@Serializable
enum class NetworkRuleAction {
    SWITCH_SERVER,
    SWITCH_IF_CONNECTED,
    DISCONNECT_VPN,
}

@Serializable
data class NetworkAutomationRule(
    val id: String = UUID.randomUUID().toString(),
    val type: NetworkRuleType,
    val ssid: String? = null,
    val action: NetworkRuleAction = NetworkRuleAction.SWITCH_SERVER,
    val targetServerId: Int? = null,
    val enabled: Boolean = true,
)
