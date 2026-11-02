// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.ic_skipi_logo
import org.jetbrains.compose.resources.painterResource

internal enum class DesktopTrayToggle { Connect, Disconnect }

/** The tray menu offers the opposite of what the tunnel is doing. */
internal fun desktopTrayToggle(tunnelRunning: Boolean): DesktopTrayToggle =
    if (tunnelRunning) DesktopTrayToggle.Disconnect else DesktopTrayToggle.Connect

internal enum class DesktopTrayWindowStep { Show, HideToBackground, Minimize }

/**
 * What a click on the tray icon does to the window: bring a hidden or minimized one back, otherwise
 * put it away the way the close button would (to the background when chosen, else minimized).
 */
internal fun desktopTrayWindowStep(
    windowShown: Boolean,
    minimized: Boolean,
    settings: DesktopAppSettings,
): DesktopTrayWindowStep = when {
    !windowShown || minimized -> DesktopTrayWindowStep.Show
    settings.closeToBackground -> DesktopTrayWindowStep.HideToBackground
    else -> DesktopTrayWindowStep.Minimize
}

/**
 * The system tray icon: the logo in colour while the tunnel runs, grey while it does not. Hosts
 * without a tray (some Linux desktops) simply get none.
 */
@Composable
internal fun ApplicationScope.DesktopTrayIcon(
    connected: Boolean,
    tooltip: String,
    toggleLabel: String,
    exitLabel: String,
    onToggleWindow: () -> Unit,
    onToggleTunnel: () -> Unit,
    onExit: () -> Unit,
) {
    if (!isTraySupported) return
    val logo = painterResource(Res.drawable.ic_skipi_logo)
    val icon = remember(logo, connected) { TrayStatePainter(logo, connected) }
    Tray(icon = icon, tooltip = tooltip, onAction = onToggleWindow) {
        Item(toggleLabel, onClick = onToggleTunnel)
        Separator()
        Item(exitLabel, onClick = onExit)
    }
}

private class TrayStatePainter(private val base: Painter, connected: Boolean) : Painter() {
    private val filter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    override val intrinsicSize get() = base.intrinsicSize

    override fun DrawScope.onDraw() {
        with(base) { draw(size, 1f, filter) }
    }
}
