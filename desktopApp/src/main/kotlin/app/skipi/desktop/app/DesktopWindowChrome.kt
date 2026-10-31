// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.window.FrameWindowScope
import java.awt.Dimension
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

/** The window opens large but cannot be squeezed below about a phone, where the layout still reads. */
internal const val DesktopInitialWindowWidth = 1180
internal const val DesktopInitialWindowHeight = 860
internal const val DesktopMinimumWindowWidth = 360
internal const val DesktopMinimumWindowHeight = 640

/** Colors are `0x00BBGGRR` on Windows; alpha is dropped. */
internal fun windowsColorRef(color: Color): Int {
    val red = (color.red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val green = (color.green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val blue = (color.blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (blue shl 16) or (green shl 8) or red
}

/** Title text that stays readable on [background]. */
internal fun readableTitleTextColor(background: Color): Color =
    if (background.luminance() < 0.5f) Color.White else Color.Black

/** Applies the minimum window size and, on Windows, paints the title bar in the interface color. */
@Composable
internal fun FrameWindowScope.DesktopWindowChrome(background: Color, isDark: Boolean) {
    LaunchedEffect(window) {
        window.minimumSize = Dimension(DesktopMinimumWindowWidth, DesktopMinimumWindowHeight)
    }
    LaunchedEffect(window, background, isDark) {
        if (isWindowsHost()) {
            applyWindowsTitleBar(
                handle = window.windowHandle,
                background = background,
                text = readableTitleTextColor(background),
                dark = isDark,
            )
        }
    }
}

private fun isWindowsHost(): Boolean =
    System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)

private const val DwmaUseImmersiveDarkMode = 20
private const val DwmaBorderColor = 34
private const val DwmaCaptionColor = 35
private const val DwmaTextColor = 36

/**
 * Colors the caption, border and title text through DWM. Windows 10 knows only the dark-mode
 * attribute; the color attributes need Windows 11 and are ignored elsewhere, so every call is
 * best effort and never fails the application.
 */
internal fun applyWindowsTitleBar(handle: Long, background: Color, text: Color, dark: Boolean) {
    if (handle == 0L) return
    runCatching {
        val linker = Linker.nativeLinker()
        val dwmapi = SymbolLookup.libraryLookup("dwmapi", Arena.global())
        val setAttribute = linker.downcallHandle(
            dwmapi.findOrThrow("DwmSetWindowAttribute"),
            FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.JAVA_LONG,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
        )
        Arena.ofConfined().use { arena ->
            fun set(attribute: Int, value: Int) {
                val buffer = arena.allocate(ValueLayout.JAVA_INT)
                buffer.set(ValueLayout.JAVA_INT, 0, value)
                setAttribute.invokeWithArguments(handle, attribute, buffer, Int.SIZE_BYTES)
            }
            set(DwmaUseImmersiveDarkMode, if (dark) 1 else 0)
            set(DwmaCaptionColor, windowsColorRef(background))
            set(DwmaBorderColor, windowsColorRef(background))
            set(DwmaTextColor, windowsColorRef(text))
        }
    }
}
