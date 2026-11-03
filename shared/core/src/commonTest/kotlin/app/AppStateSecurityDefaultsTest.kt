// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.backup.AppBackupSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppStateSecurityDefaultsTest {
    @Test
    fun externalBroadcastControlIsOffUntilTheUserEnablesIt() {
        // An exported receiver with this on lets any installed app stop the VPN.
        assertFalse(AppState().enableBroadcastControl)
        assertFalse(AppBackupSettings().enableBroadcastControl)
    }

    @Test
    fun localProxyAuthenticationIsOnByDefault() {
        assertEquals(true, AppState().enableLocalProxyAuth)
    }
}
