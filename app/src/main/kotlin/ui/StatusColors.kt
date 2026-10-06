// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package ui

import androidx.compose.ui.graphics.Color

/** Android source compatibility facade over portable status colors. */
object StatusColorDefaults {
    val StatusRunningLight: Color get() = app.skipi.ui.theme.StatusColorDefaults.StatusRunningLight
    val StatusRunningDark: Color get() = app.skipi.ui.theme.StatusColorDefaults.StatusRunningDark
    val PingFastLight: Color get() = app.skipi.ui.theme.StatusColorDefaults.PingFastLight
    val PingFastDark: Color get() = app.skipi.ui.theme.StatusColorDefaults.PingFastDark
    val PingMediumLight: Color get() = app.skipi.ui.theme.StatusColorDefaults.PingMediumLight
    val PingMediumDark: Color get() = app.skipi.ui.theme.StatusColorDefaults.PingMediumDark
    val PingSlowLight: Color get() = app.skipi.ui.theme.StatusColorDefaults.PingSlowLight
    val PingSlowDark: Color get() = app.skipi.ui.theme.StatusColorDefaults.PingSlowDark

    fun statusRunning(isDark: Boolean): Color = app.skipi.ui.theme.StatusColorDefaults.statusRunning(isDark)
    fun pingFast(isDark: Boolean): Color = app.skipi.ui.theme.StatusColorDefaults.pingFast(isDark)
    fun pingMedium(isDark: Boolean): Color = app.skipi.ui.theme.StatusColorDefaults.pingMedium(isDark)
    fun pingSlow(isDark: Boolean): Color = app.skipi.ui.theme.StatusColorDefaults.pingSlow(isDark)
}
