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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Serializable
data class CoreMemoryStats(
    val allocBytes: Long = 0L,
    val allocMb: String = "",
    val sysBytes: Long = 0L,
    val sysMb: String = "",
    val heapInuse: Long = 0L,
    val heapIdle: Long = 0L,
    val heapReleased: Long = 0L,
    val numGoroutines: Int = 0,
    val numGc: Long = 0L,
)

private val memoryJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun currentCoreMemoryStats(): CoreMemoryStats? {
    val raw = SkipiCoreRuntime.readMemoryStats()
    if (raw.isBlank()) return null
    return runCatching {
        memoryJson.decodeFromString<CoreMemoryStats>(raw)
    }.getOrNull()
}

/** The VPN core runs in SKIPI's process; reads real Go/Xray memory from core or falls back to native PSS. */
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
    val coreStats = currentCoreMemoryStats()
    if (coreStats != null && coreStats.allocBytes > 0L) {
        return coreStats.allocBytes / 1_024L
    }
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
