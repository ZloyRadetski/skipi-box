// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.effects

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.AppState
import app.MainActivity
import app.modes.AppIconCyber
import app.modes.AppIconDark
import app.modes.AppIconDefault
import app.modes.AppIconEmerald
import app.modes.AppIconLight
import app.modes.AppIconMonet
import app.modes.AppIconNordic
import app.modes.AppIconStealth
import app.modes.AppIconSunset
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeSystem
import data.AndroidAppStateStore
import features.logs.AndroidAppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
internal fun LauncherIconSynchronizer(
    context: Context,
    stateStore: AndroidAppStateStore,
) {
    val appContext = context.applicationContext
    LaunchedEffect(appContext, stateStore) {
        stateStore.state
            .map { state -> resolvedLauncherActivityName(state) }
            .distinctUntilChanged()
            .collect { targetActivityName ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        appContext.setLauncherIcon(targetActivityName)
                    }
                }.onFailure { error ->
                    AndroidAppLogger.warn(
                        LogTag,
                        "Failed to synchronize launcher icon to $targetActivityName",
                        error,
                    )
                }
            }
    }
}

private fun resolvedLauncherActivityName(state: AppState): String = when (state.appIcon) {
    AppIconDark -> "DarkLauncherActivity"
    AppIconLight -> "LightLauncherActivity"
    AppIconMonet -> "MonetLauncherActivity"
    AppIconCyber -> "CyberLauncherActivity"
    AppIconSunset -> "SunsetLauncherActivity"
    AppIconNordic -> "NordicLauncherActivity"
    AppIconEmerald -> "EmeraldLauncherActivity"
    AppIconStealth -> "StealthLauncherActivity"
    AppIconDefault -> if (state.colorMode in ColorModeThemeSystem..ColorModeThemeDark) "MonetLauncherActivity" else "DefaultLauncherActivity"
    else -> "DefaultLauncherActivity"
}

private val AllLauncherActivities = listOf(
    "DefaultLauncherActivity",
    "DarkLauncherActivity",
    "LightLauncherActivity",
    "MonetLauncherActivity",
    "CyberLauncherActivity",
    "SunsetLauncherActivity",
    "NordicLauncherActivity",
    "EmeraldLauncherActivity",
    "StealthLauncherActivity",
)

private fun Context.setLauncherIcon(targetActivityName: String) {
    val pm = packageManager
    val launcherPackageName = MainActivity::class.java.name.substringBeforeLast('.')
    for (activityName in AllLauncherActivities) {
        val component = ComponentName(packageName, "$launcherPackageName.$activityName")
        val desiredState = if (activityName == targetActivityName) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (pm.getComponentEnabledSetting(component) != desiredState) {
            pm.setComponentEnabledSetting(
                component,
                desiredState,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

private const val LogTag = "LauncherIconSync"
