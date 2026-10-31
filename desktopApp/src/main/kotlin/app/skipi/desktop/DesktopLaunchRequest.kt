// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.SkipiDeepLink

/** What one start of the executable asks for: a hidden start and/or skipi:// links to run. */
internal data class DesktopLaunchRequest(
    val hidden: Boolean,
    val links: List<String>,
)

private val ShortLinkOptions = mapOf(
    "--toggle" to "skipi://toggle",
    "--connect" to "skipi://connect",
    "--disconnect" to "skipi://disconnect",
)

/**
 * `SKIPI skipi://toggle` is how a browser or xdg-open hands a link over; `--toggle`, `--connect`
 * and `--disconnect` are the same control links for keyboard shortcuts.
 */
internal fun parseDesktopLaunchArgs(args: List<String>): DesktopLaunchRequest = DesktopLaunchRequest(
    hidden = "--hidden" in args,
    links = args.mapNotNull { argument ->
        ShortLinkOptions[argument] ?: argument.takeIf { it.startsWith("skipi://", ignoreCase = true) }
    },
)

internal enum class DesktopTunnelCommand { Start, Stop, None }

/** Android's skipi://connect, disconnect and toggle: connect and disconnect leave a matching state alone. */
internal fun desktopTunnelCommandFor(link: SkipiDeepLink, running: Boolean): DesktopTunnelCommand = when (link) {
    SkipiDeepLink.Connect -> if (running) DesktopTunnelCommand.None else DesktopTunnelCommand.Start
    SkipiDeepLink.Disconnect -> if (running) DesktopTunnelCommand.Stop else DesktopTunnelCommand.None
    SkipiDeepLink.Toggle -> if (running) DesktopTunnelCommand.Stop else DesktopTunnelCommand.Start
    else -> DesktopTunnelCommand.None
}

internal enum class DesktopLinkOutcome { Connected, Disconnected, ConnectFailed, DisconnectFailed }

/** What a control link's notification says: the state after the link ran, and whether it failed. */
internal fun desktopLinkOutcome(command: DesktopTunnelCommand, runningAfter: Boolean): DesktopLinkOutcome = when (command) {
    DesktopTunnelCommand.Start -> if (runningAfter) DesktopLinkOutcome.Connected else DesktopLinkOutcome.ConnectFailed
    DesktopTunnelCommand.Stop -> if (runningAfter) DesktopLinkOutcome.DisconnectFailed else DesktopLinkOutcome.Disconnected
    DesktopTunnelCommand.None -> if (runningAfter) DesktopLinkOutcome.Connected else DesktopLinkOutcome.Disconnected
}

internal data class DesktopNotificationText(val title: String, val body: String)

/** "SKIPI" as the title and the VPN status as the text, with the reason when it failed. */
internal fun desktopLinkNotificationText(status: String, failureReason: String): DesktopNotificationText =
    DesktopNotificationText(
        title = "SKIPI",
        body = if (failureReason.isBlank()) status else "$status: ${failureReason.trim()}",
    )
