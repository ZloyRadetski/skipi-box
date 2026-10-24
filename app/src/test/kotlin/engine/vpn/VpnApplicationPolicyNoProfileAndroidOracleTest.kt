// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import app.AppState
import app.modes.ProxyAppListModeWhitelist
import app.withActiveTrafficConfigApplied
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Captures the non-default root-state fallback when no traffic profiles exist. */
class VpnApplicationPolicyNoProfileAndroidOracleTest {

    @Test
    fun noProfilesLeaveNonDefaultRootAppPolicyAsTheRuntimeFallback() {
        val storedState = AppState(
            trafficConfigs = emptyList(),
            proxyAppListMode = ProxyAppListModeWhitelist,
            proxyAppListSelectedApps = listOf("7:com.example.legacy-profileless"),
        )

        val effectiveState = storedState.withActiveTrafficConfigApplied()

        assertSame(storedState, effectiveState)
        assertEquals(
            VpnApplicationPolicy(
                mode = ProxyAppListModeWhitelist,
                packageNames = listOf("com.example.legacy-profileless"),
            ),
            effectiveState.toVpnApplicationPolicy(currentUserId = CurrentUserId),
        )
    }

    private companion object {
        const val CurrentUserId = 7
    }
}
