// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.automation

import app.AppState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoConnectSettingsTest {

    @Test
    fun appState_autoConnect_defaults_to_false() {
        val state = AppState()
        assertFalse("autoConnectOnBoot should default to false", state.autoConnectOnBoot)
        assertFalse("autoConnectOnAppOpen should default to false", state.autoConnectOnAppOpen)
    }

    @Test
    fun appState_autoConnect_can_be_enabled() {
        val state = AppState().copy(
            autoConnectOnBoot = true,
            autoConnectOnAppOpen = true,
        )
        assertTrue("autoConnectOnBoot should be enabled", state.autoConnectOnBoot)
        assertTrue("autoConnectOnAppOpen should be enabled", state.autoConnectOnAppOpen)
    }
}
