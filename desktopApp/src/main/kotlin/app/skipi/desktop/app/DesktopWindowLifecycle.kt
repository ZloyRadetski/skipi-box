// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

internal enum class DesktopWindowCloseAction {
    ExitApplication,

    /** The window and everything it draws go away; the tunnel and background work stay. */
    HideToBackground,
}

internal fun desktopWindowCloseAction(settings: DesktopAppSettings): DesktopWindowCloseAction =
    if (settings.closeToBackground) DesktopWindowCloseAction.HideToBackground else DesktopWindowCloseAction.ExitApplication

/** A hidden start without the background choice keeps its minimized window, as before the choice existed. */
internal fun desktopWindowShownAtStart(hidden: Boolean, closeToBackground: Boolean): Boolean =
    !(hidden && closeToBackground)
