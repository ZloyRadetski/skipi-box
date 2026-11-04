// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import platform.LocalProxyAuthMode
import platform.LocalProxyCredentials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppStateLocalProxyTest {
    @Test
    fun theHttpInboundIsOffByDefaultOnAnItsOwnPortWithoutAuthentication() {
        val state = AppState()

        assertFalse(state.enableLocalHttpProxy)
        assertEquals("10809", state.localHttpProxyPort)
        assertEquals(LocalProxyAuthMode.Off, state.localProxyHttpAuthMode())
    }

    @Test
    fun socksKeepsItsExistingDefaultOfAGeneratedLogin() {
        assertEquals(LocalProxyAuthMode.Auto, AppState().localProxySocksAuthMode())
    }

    @Test
    fun theHttpLoginIsGeneratedSeparatelyFromTheSocksLogin() {
        val state = AppState()

        assertTrue(state.localHttpProxyUsername.isNotBlank())
        assertTrue(state.localHttpProxyPassword.isNotBlank())
        assertNotEquals(state.localProxyUsername, state.localHttpProxyUsername)
    }

    @Test
    fun theLegacyAuthSwitchIsTheSocksOffMode() {
        val off = AppState(enableLocalProxyAuth = false, localProxyAuthMode = LocalProxyAuthMode.Manual)

        assertEquals(LocalProxyAuthMode.Off, off.localProxySocksAuthMode())
        assertEquals(LocalProxyAuthMode.Manual, off.copy(enableLocalProxyAuth = true).localProxySocksAuthMode())
    }

    @Test
    fun choosingAnAuthModeSetsTheSwitchAndRemembersTheModeWhileOff() {
        val manual = AppState().withLocalProxySocksAuthMode(LocalProxyAuthMode.Manual)
        assertTrue(manual.enableLocalProxyAuth)
        assertEquals(LocalProxyAuthMode.Manual, manual.localProxySocksAuthMode())

        val off = manual.withLocalProxySocksAuthMode(LocalProxyAuthMode.Off)
        assertFalse(off.enableLocalProxyAuth)
        assertEquals(LocalProxyAuthMode.Off, off.localProxySocksAuthMode())
        assertEquals(LocalProxyAuthMode.Manual, off.localProxyAuthMode, "the last real mode is kept for switching back")
    }

    @Test
    fun httpAuthModesWorkTheSameWayAndNeverTouchSocks() {
        val state = AppState().withLocalProxyHttpAuthMode(LocalProxyAuthMode.FromJson)

        assertEquals(LocalProxyAuthMode.FromJson, state.localProxyHttpAuthMode())
        assertEquals(LocalProxyAuthMode.Auto, state.localProxySocksAuthMode())
        assertEquals(LocalProxyAuthMode.Off, state.withLocalProxyHttpAuthMode(LocalProxyAuthMode.Off).localProxyHttpAuthMode())
    }

    @Test
    fun theStoredCredentialsOfEachInboundAreSeparate() {
        val state = AppState(
            localProxyUsername = "s-user",
            localProxyPassword = "s-pass",
            localHttpProxyUsername = "h-user",
            localHttpProxyPassword = "h-pass",
        )

        assertEquals(LocalProxyCredentials("s-user", "s-pass"), state.localProxySocksCredentials())
        assertEquals(LocalProxyCredentials("h-user", "h-pass"), state.localProxyHttpCredentials())
    }

    @Test
    fun theHttpInboundRunsWhenEnabledOrWhenTheVpnProxyNeedsIt() {
        assertFalse(AppState().httpProxyInboundEnabled)
        assertTrue(AppState(enableLocalHttpProxy = true).httpProxyInboundEnabled)
        assertTrue(AppState(enableVpnAppendHttpProxy = true).httpProxyInboundEnabled)
    }
}
