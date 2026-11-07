// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.isTraySupported
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.ic_skipi_logo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * What the panel's click and menu do. The status notifier is started before the window exists, so its
 * callbacks are bound here and pointed at the live window and tunnel state on every composition.
 */
internal class DesktopTrayActions {
    @Volatile var onToggleWindow: () -> Unit = {}
    @Volatile var onToggleTunnel: () -> Unit = {}
    @Volatile var onExit: () -> Unit = {}
}

/** A StatusNotifier tray exists on Linux only; Windows and macOS use the AWT tray. */
internal fun desktopStatusNotifierOrNull(actions: DesktopTrayActions): DesktopStatusNotifier? {
    if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return null
    return DesktopStatusNotifier(
        busAddress = null,
        onActivate = { actions.onToggleWindow() },
        onToggleTunnel = { actions.onToggleTunnel() },
        onExit = { actions.onExit() },
    ).also { notifier ->
        notifier.start(DesktopStatusNotifierState(tooltip = "SKIPI", toggleLabel = "", exitLabel = "", icons = emptyList()))
    }
}

private val TrayPixmapSizes = listOf(22, 32, 48, 64)

/** The logo as ARGB pixels at the sizes a panel may ask for, grey while disconnected. */
internal fun trayPixmaps(
    logo: Painter,
    connected: Boolean,
    density: Density,
    sizes: List<Int> = TrayPixmapSizes,
): List<DesktopTrayPixmap> {
    val painter = TrayStatePainter(logo, connected)
    return sizes.map { side ->
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            with(painter) { draw(size) }
        }
        val argb = IntArray(side * side)
        bitmap.toAwtImage().getRGB(0, 0, side, side, argb, 0, side)
        DesktopTrayPixmap(side, side, argb)
    }
}

/**
 * The tray icon: the logo in colour while the tunnel runs, grey while it does not. A StatusNotifier panel
 * (KDE, waybar, quickshell, GNOME with AppIndicator) gets it from [statusNotifier]; AWT's tray is only used
 * where there is none and the desktop still has an XEmbed one, and hosts with neither get no icon.
 */
@Composable
internal fun ApplicationScope.DesktopTrayIcon(
    connected: Boolean,
    tooltip: String,
    toggleLabel: String,
    exitLabel: String,
    statusNotifier: DesktopStatusNotifier?,
    statusNotifierShown: Boolean,
    actions: DesktopTrayActions,
    onToggleWindow: () -> Unit,
    onToggleTunnel: () -> Unit,
    onExit: () -> Unit,
) {
    SideEffect {
        actions.onToggleWindow = onToggleWindow
        actions.onToggleTunnel = onToggleTunnel
        actions.onExit = onExit
    }
    val logo = painterResource(Res.drawable.ic_skipi_logo)
    if (statusNotifier != null) {
        val density = LocalDensity.current
        val icons = remember(logo, connected, density) { trayPixmaps(logo, connected, density) }
        LaunchedEffect(statusNotifier, icons, tooltip, toggleLabel, exitLabel) {
            withContext(Dispatchers.IO) {
                statusNotifier.update(DesktopStatusNotifierState(tooltip, toggleLabel, exitLabel, icons))
            }
        }
    }
    if (statusNotifierShown || !isTraySupported) return
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
