// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.modes.ProxyAppListModeBlacklist
import app.modes.ProxyAppListModeGlobal
import app.modes.ProxyAppListModeWhitelist
import app.withActiveTrafficConfigApplied
import features.config.TrafficConfigState
import features.config.defaultShadowrocketConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android-source baseline for the app-list policy projected at VPN start. */
class VpnApplicationPolicyAndroidOracleTest {

    @Test
    fun unknownModeFallsBackToGlobalAndGlobalModeIgnoresSelectedKeys() {
        val selectedKeys = listOf("7:com.example.selected")

        assertEquals(
            VpnApplicationPolicy(mode = ProxyAppListModeGlobal),
            AppState(
                proxyAppListMode = 99,
                proxyAppListSelectedApps = selectedKeys,
            ).toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
        assertEquals(
            VpnApplicationPolicy(mode = ProxyAppListModeGlobal),
            AppState(
                proxyAppListMode = ProxyAppListModeGlobal,
                proxyAppListSelectedApps = selectedKeys,
            ).toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    @Test
    fun selectedKeysKeepCurrentUserPackagesAndLegacyIdsInTrimmedEncounterOrder() {
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
            VpnApplicationPolicy(
                mode = ProxyAppListModeBlacklist,
                packageNames = listOf(
                    "com.example.first",
                    "com.example.legacy",
                    "com.example.second",
                ),
            ),
            AppState(
                proxyAppListMode = ProxyAppListModeBlacklist,
                proxyAppListSelectedApps = selectedKeys,
            ).toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    @Test
    fun activeProfileOverridesLegacyAppStatePolicyAtRuntime() {
        val inactiveProfile = profile(
            id = 10,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("7:com.example.inactive"),
        )
        val activeProfile = profile(
            id = 20,
            mode = ProxyAppListModeWhitelist,
            selectedApps = listOf("7:com.example.active"),
        )
        val storedState = AppState(
            trafficConfigs = listOf(inactiveProfile, activeProfile),
            activeTrafficConfigId = activeProfile.id,
            proxyAppListMode = ProxyAppListModeGlobal,
            proxyAppListSelectedApps = listOf("7:com.example.legacy-state"),
        )

        val effectiveState = storedState.withActiveTrafficConfigApplied()

        assertEquals(ProxyAppListModeWhitelist, effectiveState.proxyAppListMode)
        assertEquals(
            VpnApplicationPolicy(
                mode = ProxyAppListModeWhitelist,
                packageNames = listOf("com.example.active"),
            ),
            effectiveState.toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    @Test
    fun missingActiveProfileFallsBackToFirstProfile() {
        val firstProfile = profile(
            id = 10,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("7:com.example.first-profile"),
        )
        val secondProfile = profile(
            id = 20,
            mode = ProxyAppListModeWhitelist,
            selectedApps = listOf("7:com.example.second-profile"),
        )
        val storedState = AppState(
            trafficConfigs = listOf(firstProfile, secondProfile),
            activeTrafficConfigId = 999,
        )

        val effectiveState = storedState.withActiveTrafficConfigApplied()

        assertEquals(
            VpnApplicationPolicy(
                mode = ProxyAppListModeBlacklist,
                packageNames = listOf("com.example.first-profile"),
            ),
            effectiveState.toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    @Test
    fun strictFullTunnelUsesGlobalPolicyWithoutRewritingTheStoredProfile() {
        val profile = profile(
            id = 10,
            mode = ProxyAppListModeBlacklist,
            selectedApps = listOf("7:com.example.bypass"),
        )
        val storedState = AppState(
            enableStrictFullTunnel = true,
            trafficConfigs = listOf(profile),
            activeTrafficConfigId = profile.id,
        )

        val effectiveState = storedState
            .withActiveTrafficConfigApplied()
            .withStrictFullTunnelApplied()

        assertEquals(profile, storedState.trafficConfigs.single())
        assertEquals(ProxyAppListModeBlacklist, profile.proxyAppListMode)
        assertEquals(listOf("7:com.example.bypass"), profile.proxyAppListSelectedApps)
        assertEquals(ProxyAppListModeGlobal, effectiveState.proxyAppListMode)
        assertTrue(effectiveState.proxyAppListSelectedApps.isEmpty())
        assertEquals(
            VpnApplicationPolicy(mode = ProxyAppListModeGlobal),
            effectiveState.toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    @Test
    fun noProfileUsesTheGlobalAppStateFallback() {
        val storedState = AppState(trafficConfigs = emptyList())

        val effectiveState = storedState.withActiveTrafficConfigApplied()

        assertSame(storedState, effectiveState)
        assertEquals(
            VpnApplicationPolicy(mode = ProxyAppListModeGlobal),
            effectiveState.toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    private fun profile(
        id: Int,
        mode: Int,
        selectedApps: List<String>,
    ): TrafficConfigState = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = defaultShadowrocketConfig(),
        proxyAppListMode = mode,
        proxyAppListSelectedApps = selectedApps,
    )

    private companion object {
        const val CurrentUserId = 7
    }
}
