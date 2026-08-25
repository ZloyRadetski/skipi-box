package ui

import androidx.compose.ui.graphics.Color

/**
 * Default fallback colors for status and ping indicators.
 * Applied when the user has not overridden these colors in appearance settings.
 */
object StatusColorDefaults {
    val StatusRunningLight = Color(0xFF128A3C)
    val StatusRunningDark = Color(0xFF6BD58A)

    val PingFastLight = Color(0xFF128A3C)
    val PingFastDark = Color(0xFF6BD58A)

    val PingMediumLight = Color(0xFFD18A00)
    val PingMediumDark = Color(0xFFFFC857)

    val PingSlowLight = Color(0xFFE06400)
    val PingSlowDark = Color(0xFFFF9B63)

    fun statusRunning(isDark: Boolean): Color = if (isDark) StatusRunningDark else StatusRunningLight

    fun pingFast(isDark: Boolean): Color = if (isDark) PingFastDark else PingFastLight

    fun pingMedium(isDark: Boolean): Color = if (isDark) PingMediumDark else PingMediumLight

    fun pingSlow(isDark: Boolean): Color = if (isDark) PingSlowDark else PingSlowLight
}
