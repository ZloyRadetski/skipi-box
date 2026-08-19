// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.R
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** The VPN core runs in SKIPI's process, so Android's PSS is the available tunnel-memory metric. */
@Composable
internal fun SettingsTunnelMemorySection(
    showOnHome: Boolean,
    onShowOnHomeChange: (Boolean) -> Unit,
) {
    SmallTitle(text = stringResource(R.string.settings_tunnel_memory))
    SettingsSectionCard {
        SwitchPreference(
            title = stringResource(R.string.settings_tunnel_memory_show_on_home),
            summary = stringResource(R.string.settings_tunnel_memory_show_on_home_summary),
            checked = showOnHome,
            onCheckedChange = onShowOnHomeChange,
        )
    }
}

internal fun Context.currentTunnelMemoryPssKb(): Long {
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
