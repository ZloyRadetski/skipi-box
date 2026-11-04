// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import platform.LocalProxyAuthMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalProxyAuthModeOptionsTest {
    @Test
    fun theSelectorListsAutoManualFromJsonThenOff() {
        assertEquals(
            listOf(LocalProxyAuthMode.Auto, LocalProxyAuthMode.Manual, LocalProxyAuthMode.FromJson, LocalProxyAuthMode.Off),
            LocalProxyAuthModeOptions,
        )
    }

    @Test
    fun everyModeIsOffered() {
        assertEquals(LocalProxyAuthMode.entries.toSet(), LocalProxyAuthModeOptions.toSet())
    }

    @Test
    fun onlyAutoAndManualShowALogin() {
        assertTrue(LocalProxyAuthMode.Auto.showsCredentials())
        assertTrue(LocalProxyAuthMode.Manual.showsCredentials())
        assertFalse(LocalProxyAuthMode.FromJson.showsCredentials())
        assertFalse(LocalProxyAuthMode.Off.showsCredentials())
    }
}
