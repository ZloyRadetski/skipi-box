// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.ComponentCallbacks2

/**
 * The core is only told to free memory while the tunnel runs and the system is really short of
 * it. UI_HIDDEN and the background/moderate levels fire on ordinary use (leaving the app) and
 * would make the core drop caches it needs again a moment later.
 */
internal fun shouldForceFreeMemory(level: Int, vpnRunning: Boolean): Boolean =
    vpnRunning && (
        level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        )
