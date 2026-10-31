// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.R
import engine.vpn.SkipiCoreRuntime

/** The VPN core runs in SKIPI's process; reads real Go/Xray memory from core or falls back to native PSS. */
internal fun Context.currentTunnelMemoryPssKb(): Long {
    coreMemoryKilobytes(SkipiCoreRuntime.readMemoryStats())?.let { return it }
    val manager = getSystemService(ActivityManager::class.java) ?: return 0L
    return manager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
        .firstOrNull()
        ?.nativePss
        ?.toLong()
        ?.coerceAtLeast(0L)
        ?: 0L
}

@Composable
internal fun formatTunnelMemory(kilobytes: Long): String {
    return if (kilobytes >= 1_024L) {
        stringResource(R.string.settings_tunnel_memory_megabytes, kilobytes / 1_024.0)
    } else {
        stringResource(R.string.settings_tunnel_memory_kilobytes, kilobytes)
    }
}
