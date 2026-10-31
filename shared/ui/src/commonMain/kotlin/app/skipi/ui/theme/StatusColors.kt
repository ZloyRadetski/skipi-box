// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Default fallback colors for status and ping indicators. They are deliberately vivid: on a dark
 * surface a pastel tone (the tonal "error" pink, a pale green) reads as washed out.
 */
object StatusColorDefaults {
    val StatusRunningLight = Color(0xFF0A9B3A)
    val StatusRunningDark = Color(0xFF2EDB6A)

    /** Stopped, failed and timed-out: plain red in both themes. */
    val StatusStoppedLight = Color(0xFFE0201C)
    val StatusStoppedDark = Color(0xFFFF4040)

    val PingFastLight = StatusRunningLight
    val PingFastDark = StatusRunningDark

    val PingMediumLight = Color(0xFFE59A00)
    val PingMediumDark = Color(0xFFFFB800)

    val PingSlowLight = Color(0xFFF05A00)
    val PingSlowDark = Color(0xFFFF7A29)

    fun statusRunning(isDark: Boolean): Color = if (isDark) StatusRunningDark else StatusRunningLight

    fun statusStopped(isDark: Boolean): Color = if (isDark) StatusStoppedDark else StatusStoppedLight

    fun pingFast(isDark: Boolean): Color = if (isDark) PingFastDark else PingFastLight

    fun pingMedium(isDark: Boolean): Color = if (isDark) PingMediumDark else PingMediumLight

    fun pingSlow(isDark: Boolean): Color = if (isDark) PingSlowDark else PingSlowLight
}
