// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** The server (when one is selected) with a separator under it, then connect or disconnect, a separator and exit. */
internal fun desktopTrayMenuEntries(
    serverName: String?,
    toggleLabel: String,
    exitLabel: String,
    onToggleTunnel: () -> Unit,
    onExit: () -> Unit,
): List<DesktopTrayMenuEntry> = buildList {
    if (!serverName.isNullOrBlank()) {
        add(DesktopTrayMenuEntry.Info(serverName))
        add(DesktopTrayMenuEntry.Separator)
    }
    add(DesktopTrayMenuEntry.Action(toggleLabel, onToggleTunnel))
    add(DesktopTrayMenuEntry.Separator)
    add(DesktopTrayMenuEntry.Action(exitLabel, onExit))
}

/** The tooltip: the app and its state, then the active server. */
internal fun desktopTrayTooltip(status: String, serverName: String?): String =
    if (serverName.isNullOrBlank()) "SKIPI — $status" else "SKIPI — $status · $serverName"

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
 * What the panel's click does. The status notifier is started before the window exists, so its callback is
 * bound here and pointed at the live window state on every composition.
 */
internal class DesktopTrayActions {
    @Volatile var onToggleWindow: () -> Unit = {}
}

/** A StatusNotifier tray exists on Linux only; Windows and macOS use the AWT tray. */
internal fun desktopStatusNotifierOrNull(actions: DesktopTrayActions): DesktopStatusNotifier? {
    if (!System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)) return null
    return DesktopStatusNotifier(
        busAddress = null,
        onActivate = { actions.onToggleWindow() },
    ).also { notifier ->
        notifier.start(DesktopStatusNotifierState(tooltip = "SKIPI", menu = emptyList(), icons = emptyList()))
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
 *
 * The AWT icon is the platform's, but its context menu is not: the AWT popup menu is drawn by the OS toolkit
 * (Win32 on Windows), so a right click opens a Compose menu in [theme] instead. A StatusNotifier panel draws the
 * same entries itself from a dbusmenu, because it never asks the app to draw a menu.
 */
@Composable
internal fun ApplicationScope.DesktopTrayIcon(
    connected: Boolean,
    tooltip: String,
    toggleLabel: String,
    exitLabel: String,
    /** The active server's name, shown at the top of the menu; null when none is selected. */
    serverName: String?,
    statusNotifier: DesktopStatusNotifier?,
    statusNotifierShown: Boolean,
    actions: DesktopTrayActions,
    onToggleWindow: () -> Unit,
    onToggleTunnel: () -> Unit,
    onExit: () -> Unit,
    theme: @Composable (@Composable () -> Unit) -> Unit,
) {
    SideEffect { actions.onToggleWindow = onToggleWindow }
    val logo = painterResource(Res.drawable.ic_skipi_logo)
    if (statusNotifier != null) {
        val density = LocalDensity.current
        val icons = remember(logo, connected, density) { trayPixmaps(logo, connected, density) }
        // The panel keeps the actions of the entries it was last given, so they read the live ones when clicked.
        val toggleTunnel by rememberUpdatedState(onToggleTunnel)
        val exit by rememberUpdatedState(onExit)
        LaunchedEffect(statusNotifier, icons, tooltip, serverName, toggleLabel, exitLabel) {
            val menu = desktopTrayMenuEntries(serverName, toggleLabel, exitLabel, { toggleTunnel() }, { exit() })
            withContext(Dispatchers.IO) {
                statusNotifier.update(DesktopStatusNotifierState(tooltip, menu, icons))
            }
        }
    }
    if (statusNotifierShown || !isTraySupported) return
    var menuClick by remember { mutableStateOf<Point?>(null) }
    val image = remember(logo, connected) { renderTrayImage(logo, connected) }
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
        val entries = desktopTrayMenuEntries(serverName, toggleLabel, exitLabel, onToggleTunnel, onExit)
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
