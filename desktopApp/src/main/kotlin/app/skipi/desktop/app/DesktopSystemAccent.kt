// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The accent colour the operating system shows its own controls in; null when this system has none to read. */
internal object DesktopSystemAccent {
    fun read(): Color? = runCatching {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            "windows" in os -> run(
                "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\DWM", "/v", "AccentColor",
            )?.let(::parseWindowsAccentColor)
            "mac" in os -> macAccentColor(
                run("defaults", "read", "-g", "AppleAccentColor")?.trim()?.toIntOrNull(),
            )
            else -> run("gsettings", "get", "org.gnome.desktop.interface", "accent-color")?.let(::gnomeAccentColor)
        }
    }.getOrNull()

    private fun run(vararg command: String): String? =
        runProcessWithTimeout(command.toList(), kotlin.time.Duration.parse("2s")).getOrNull()
}

/** The system accent while [enabled]; read off the UI thread, so it is null until the first read returns. */
@Composable
internal fun rememberDesktopSystemAccent(enabled: Boolean): Color? {
    var accent by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(enabled) {
        if (enabled) accent = withContext(Dispatchers.IO) { DesktopSystemAccent.read() }
    }
    return accent.takeIf { enabled }
}

private val WindowsAccentValue = Regex("""AccentColor\s+REG_DWORD\s+0x([0-9a-fA-F]{1,8})""")

/** `reg query` prints the accent as 0xAABBGGRR. */
internal fun parseWindowsAccentColor(output: String): Color? {
    val value = WindowsAccentValue.find(output)?.groupValues?.get(1)?.toLongOrNull(16) ?: return null
    val red = (value and 0xFF).toInt()
    val green = ((value shr 8) and 0xFF).toInt()
    val blue = ((value shr 16) and 0xFF).toInt()
    return Color(red = red, green = green, blue = blue)
}

/** macOS stores the accent as an index; no value means the default blue. */
internal fun macAccentColor(index: Int?): Color? = when (index) {
    null -> Color(0xFF007AFF)
    -1 -> Color(0xFF989898)
    0 -> Color(0xFFFF5257)
    1 -> Color(0xFFF7821B)
    2 -> Color(0xFFFFC600)
    3 -> Color(0xFF62BA46)
    4 -> Color(0xFF007AFF)
    5 -> Color(0xFF953D96)
    6 -> Color(0xFFF74F9E)
    else -> null
}

/** GNOME 47+ names its accent; `gsettings get` prints it quoted. */
internal fun gnomeAccentColor(output: String): Color? = when (output.trim().trim('\'')) {
    "blue" -> Color(0xFF3584E4)
    "teal" -> Color(0xFF2190A4)
    "green" -> Color(0xFF3A944A)
    "yellow" -> Color(0xFFC88800)
    "orange" -> Color(0xFFED5B00)
    "red" -> Color(0xFFE62D42)
    "pink" -> Color(0xFFD56199)
    "purple" -> Color(0xFF9141AC)
    "slate" -> Color(0xFF6F8396)
    else -> null
}
