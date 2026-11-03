// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayConfigLogRedactionTest {
    private val config = """
        {
          "inbounds": [{"tag": "socks", "port": 10808, "settings": {"auth": "password", "accounts": [{"user": "skipi_user", "pass": "skipi_pass"}]}}],
          "outbounds": [
            {"tag": "proxy", "protocol": "vless", "settings": {"vnext": [{"address": "secret.example.test", "port": 443,
              "users": [{"id": "11111111-2222-3333-4444-555555555555", "flow": "xtls-rprx-vision"}]}]},
             "streamSettings": {"security": "reality", "realitySettings": {"serverName": "front.example.test", "publicKey": "PUBKEY", "shortId": "abcd", "fingerprint": "chrome"}}},
            {"tag": "t", "protocol": "trojan", "settings": {"servers": [{"address": "other.example.test", "port": 443, "password": "hunter2"}]}}
          ]
        }
    """.trimIndent()

    @Test
    fun credentialsAndServerAddressesAreMasked() {
        val redacted = redactXrayConfigForLog(config)

        listOf(
            "11111111-2222-3333-4444-555555555555",
            "hunter2",
            "skipi_user",
            "skipi_pass",
            "PUBKEY",
            "abcd",
            "secret.example.test",
            "other.example.test",
            "front.example.test",
        ).forEach { secret ->
            assertFalse(redacted.contains(secret), "$secret leaked into the log")
        }
    }

    @Test
    fun structureNeededForDebuggingSurvives() {
        val redacted = redactXrayConfigForLog(config)

        listOf("\"protocol\"", "vless", "trojan", "reality", "10808", "xtls-rprx-vision", "chrome").forEach { kept ->
            assertTrue(redacted.contains(kept), "$kept should still be visible")
        }
    }

    @Test
    fun unparseableInputIsNeverEchoed() {
        val redacted = redactXrayConfigForLog("{not json password=hunter2")

        assertFalse(redacted.contains("hunter2"))
    }
}
