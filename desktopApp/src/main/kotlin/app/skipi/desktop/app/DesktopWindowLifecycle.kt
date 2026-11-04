// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

internal enum class DesktopWindowCloseAction {
    ExitApplication,

    /** The window and everything it draws go away; the tunnel and background work stay. */
    HideToBackground,
}

/** Without a tray icon a hidden window could not be brought back, so the close button then ends the application. */
internal fun desktopWindowCloseAction(settings: DesktopAppSettings, traySupported: Boolean = true): DesktopWindowCloseAction =
    if (settings.closeToBackground && traySupported) DesktopWindowCloseAction.HideToBackground else DesktopWindowCloseAction.ExitApplication

/** A hidden start without the background choice keeps its minimized window, as before the choice existed. */
internal fun desktopWindowShownAtStart(hidden: Boolean, closeToBackground: Boolean): Boolean =
    !(hidden && closeToBackground)
