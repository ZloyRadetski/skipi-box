// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberDialogState
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.ic_skipi_logo
import org.jetbrains.compose.resources.painterResource
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

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
 *
 * The icon itself is the platform's, but its context menu is not: the AWT popup menu is drawn by
 * the OS toolkit (Win32 on Windows), so a right click opens a Compose menu in [theme] instead and
 * it looks the same everywhere.
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
    theme: @Composable (@Composable () -> Unit) -> Unit,
) {
    if (!isTraySupported) return
    val logo = painterResource(Res.drawable.ic_skipi_logo)
    val image = remember(logo, connected) { renderTrayImage(logo, connected) }
    var menuClick by remember { mutableStateOf<Point?>(null) }
    val toggleWindow by rememberUpdatedState(onToggleWindow)
    val trayIcon = remember { TrayIcon(image).apply { isImageAutoSize = true } }
    SideEffect {
        trayIcon.image = image
        trayIcon.toolTip = tooltip
    }
    DisposableEffect(trayIcon) {
        val action = ActionListener { toggleWindow() }
        // The popup trigger is a press on some platforms and a release on others.
        val mouse = object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = openMenuOn(event)
            override fun mouseReleased(event: MouseEvent) = openMenuOn(event)
            private fun openMenuOn(event: MouseEvent) {
                if (event.isPopupTrigger) menuClick = Point(event.xOnScreen, event.yOnScreen)
            }
        }
        trayIcon.addActionListener(action)
        trayIcon.addMouseListener(mouse)
        val added = runCatching { SystemTray.getSystemTray().add(trayIcon) }.isSuccess
        onDispose {
            trayIcon.removeActionListener(action)
            trayIcon.removeMouseListener(mouse)
            if (added) SystemTray.getSystemTray().remove(trayIcon)
        }
    }
    menuClick?.let { click ->
        val entries = listOf(
            DesktopTrayMenuEntry.Action(toggleLabel, onToggleTunnel),
            DesktopTrayMenuEntry.Separator,
            DesktopTrayMenuEntry.Action(exitLabel, onExit),
        )
        key(click) {
            DesktopTrayMenuWindow(click, entries, theme, onDismiss = { menuClick = null })
        }
    }
}

/** The menu's own window: undecorated and transparent, shown at the click and closed as soon as it loses focus. */
@Composable
private fun DesktopTrayMenuWindow(
    click: Point,
    entries: List<DesktopTrayMenuEntry>,
    theme: @Composable (@Composable () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val windowSize = desktopTrayMenuWindowSize(entries)
    val origin = remember(click) {
        val areas = awtScreenAreas()
        val screen = desktopTrayScreenAt(areas, click.x, click.y) ?: areas.first()
        desktopTrayMenuOrigin(click.x, click.y, windowSize, screen)
    }
    val state = rememberDialogState(
        position = WindowPosition(origin.x.dp, origin.y.dp),
        size = DpSize(windowSize.width.dp, windowSize.height.dp),
    )
    DialogWindow(
        onCloseRequest = onDismiss,
        state = state,
        title = "SKIPI",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        onKeyEvent = { event ->
            (event.key == Key.Escape && event.type == KeyEventType.KeyUp).also { if (it) onDismiss() }
        },
    ) {
        DisposableEffect(window) {
            val focus = object : WindowAdapter() {
                override fun windowLostFocus(event: WindowEvent) = onDismiss()
            }
            window.addWindowFocusListener(focus)
            window.toFront()
            window.requestFocus()
            onDispose { window.removeWindowFocusListener(focus) }
        }
        theme { DesktopTrayMenuContent(entries, onDismiss) }
    }
}

/** Usable area (without the taskbar) of every screen, in the units AWT positions windows in. */
private fun awtScreenAreas(): List<DesktopTrayScreenArea> {
    val toolkit = Toolkit.getDefaultToolkit()
    val areas = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        val configuration = device.defaultConfiguration
        val bounds = configuration.bounds
        val insets = toolkit.getScreenInsets(configuration)
        DesktopTrayScreenArea(
            x = bounds.x + insets.left,
            y = bounds.y + insets.top,
            width = bounds.width - insets.left - insets.right,
            height = bounds.height - insets.top - insets.bottom,
        )
    }
    return areas.ifEmpty { listOf(DesktopTrayScreenArea(0, 0, 1920, 1080)) }
}

private fun renderTrayImage(logo: Painter, connected: Boolean): java.awt.Image {
    // Drawn large and scaled down by the tray (isImageAutoSize), which keeps it sharp on high-DPI trays.
    val side = TrayImageSide
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
        with(TrayStatePainter(logo, connected)) { draw(size) }
    }
    return bitmap.toAwtImage()
}

private const val TrayImageSide = 64

private class TrayStatePainter(private val base: Painter, connected: Boolean) : Painter() {
    private val filter = if (connected) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    override val intrinsicSize get() = base.intrinsicSize

    override fun DrawScope.onDraw() {
        with(base) { draw(size, 1f, filter) }
    }
}
