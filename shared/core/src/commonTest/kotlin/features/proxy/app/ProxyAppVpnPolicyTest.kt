// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.app

import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import kotlin.test.Test
import kotlin.test.assertEquals

/** Portable contract fixtures mirrored by VpnApplicationPolicyAndroidOracleTest. */
class ProxyAppVpnPolicyTest {

    @Test
    fun unknownModeFallsBackToGlobal() {
        assertEquals(
            ProxyAppVpnPolicy(mode = ProxyAppListModeGlobal, packageNames = emptyList()),
            projectProxyAppVpnPolicy(
                mode = UnknownMode,
                selectedAppKeys = listOf("7:com.example.selected"),
                currentUserId = CurrentUserId,
                strictFullTunnel = false,
            ),
        )
    }

    @Test
    fun globalModeIgnoresSelectedKeys() {
        assertEquals(
            ProxyAppVpnPolicy(mode = ProxyAppListModeGlobal, packageNames = emptyList()),
            projectProxyAppVpnPolicy(
                mode = ProxyAppListModeGlobal,
                selectedAppKeys = listOf("7:com.example.selected", "invalid-user:key"),
                currentUserId = CurrentUserId,
                strictFullTunnel = false,
            ),
        )
    }

    @Test
    fun strictFullTunnelProjectsGlobalWithoutMutatingTheStoredKeys() {
        val storedKeys = mutableListOf("7:com.example.bypass")
        val originalKeys = storedKeys.toList()

        val policy = projectProxyAppVpnPolicy(
            mode = ProxyAppListModeBlacklist,
            selectedAppKeys = storedKeys,
            currentUserId = CurrentUserId,
            strictFullTunnel = true,
        )

        assertEquals(
            ProxyAppVpnPolicy(mode = ProxyAppListModeGlobal, packageNames = emptyList()),
            policy,
        )
        assertEquals(originalKeys, storedKeys)
    }

    @Test
    fun selectedKeysFilterUserPrefixesThenTrimAndDeduplicateInEncounterOrder() {
        val selectedKeys = listOf(
            "7: com.example.first ",
            "8:com.example.foreign",
            "not-a-user:com.example.malformed",
            " 7:com.example.whitespace-before-user",
            "7:   ",
            " ",
            " com.example.legacy ",
            "7:com.example.first",
            "7:com.example.second",
            "7:com.example.legacy",
        )

        assertEquals(
            ProxyAppVpnPolicy(
                mode = ProxyAppListModeBlacklist,
                packageNames = listOf(
                    "com.example.first",
                    "com.example.legacy",
                    "com.example.second",
                ),
            ),
            projectProxyAppVpnPolicy(
                mode = ProxyAppListModeBlacklist,
                selectedAppKeys = selectedKeys,
                currentUserId = CurrentUserId,
                strictFullTunnel = false,
            ),
        )
    }

    @Test
    fun whitelistModeIsPreservedWithNormalizedPackages() {
        assertEquals(
            ProxyAppVpnPolicy(
                mode = ProxyAppListModeWhitelist,
                packageNames = listOf("com.example.active"),
            ),
            projectProxyAppVpnPolicy(
                mode = ProxyAppListModeWhitelist,
                selectedAppKeys = listOf("7:com.example.active"),
                currentUserId = CurrentUserId,
                strictFullTunnel = false,
            ),
        )
    }

    private companion object {
        const val CurrentUserId = 7
        const val UnknownMode = 99
    }
}
