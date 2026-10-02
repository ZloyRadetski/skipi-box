// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.model

import kotlin.random.Random
import kotlinx.serialization.Serializable

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
    val id: String = newNetworkAutomationRuleId(),
    val type: NetworkRuleType,
    val ssid: String? = null,
    val action: NetworkRuleAction = NetworkRuleAction.SWITCH_SERVER,
    val targetServerId: Int? = null,
    val enabled: Boolean = true,
)

private fun newNetworkAutomationRuleId(): String {
    val bytes = Random.Default.nextBytes(ByteArray(16))
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = "0123456789abcdef"
    return buildString(36) {
        bytes.forEachIndexed { index, byte ->
            if (index == 4 || index == 6 || index == 8 || index == 10) append('-')
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}
