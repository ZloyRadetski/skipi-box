// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.ComponentCallbacks2
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimMemoryPolicyTest {
    @Test
    fun runningVpn_freesOnlyOnCriticalOrComplete() {
        assertTrue(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, vpnRunning = true))
        assertTrue(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, vpnRunning = true))
    }

    @Test
    fun uiHiddenAndModerate_doNotFree() {
        assertFalse(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, vpnRunning = true))
        assertFalse(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_MODERATE, vpnRunning = true))
        assertFalse(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, vpnRunning = true))
        assertFalse(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, vpnRunning = true))
    }

    @Test
    fun stoppedVpn_neverFrees() {
        assertFalse(shouldForceFreeMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, vpnRunning = false))
    }
}
