// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.networkautomation.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

class NetworkAutomationRuleSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testSerializeAndDeserializeSwitchServerRule() {
        val rule = NetworkAutomationRule(
            id = "test-rule-1",
            type = NetworkRuleType.CELLULAR,
            ssid = null,
            action = NetworkRuleAction.SWITCH_SERVER,
            targetServerId = 42,
            enabled = true,
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<NetworkAutomationRule>(encoded)

        assertEquals(rule, decoded)
        assertEquals(NetworkRuleAction.SWITCH_SERVER, decoded.action)
    }

    @Test
    fun testSerializeAndDeserializeSwitchIfConnectedRule() {
        val rule = NetworkAutomationRule(
            id = "test-rule-2",
            type = NetworkRuleType.SPECIFIC_WIFI,
            ssid = "MyOffice5G",
            action = NetworkRuleAction.SWITCH_IF_CONNECTED,
            targetServerId = 101,
            enabled = true,
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<NetworkAutomationRule>(encoded)

        assertEquals(rule, decoded)
        assertEquals(NetworkRuleAction.SWITCH_IF_CONNECTED, decoded.action)
        assertEquals("MyOffice5G", decoded.ssid)
    }

    @Test
    fun testSerializeAndDeserializeDisconnectRule() {
        val rule = NetworkAutomationRule(
            id = "test-rule-3",
            type = NetworkRuleType.ANY_WIFI,
            ssid = null,
            action = NetworkRuleAction.DISCONNECT_VPN,
            targetServerId = null,
            enabled = false,
        )

        val encoded = json.encodeToString(rule)
        val decoded = json.decodeFromString<NetworkAutomationRule>(encoded)

        assertEquals(rule, decoded)
        assertEquals(NetworkRuleAction.DISCONNECT_VPN, decoded.action)
    }
}
