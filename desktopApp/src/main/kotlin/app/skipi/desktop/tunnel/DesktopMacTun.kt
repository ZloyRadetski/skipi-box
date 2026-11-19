// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path

/*
 * TUN on macOS.
 *
 * hev-socks5-tunnel creates the utun interface itself, and both that and the routes need root. SKIPI Core owns
 * that work: it shows the administrator prompt, runs a root supervisor that starts the shipped hev program,
 * installs the routes, and removes them again on stop or when hev dies. The app only hands Core the interface plan,
 * the hev executable and the run directory through the session plan (see DesktopCoreHevPlan), and keeps Core's own
 * sockets off the tunnel by IP_BOUND_IF (see skipi-core's netbind).
 */

/** The first free utunN from 9 up, clear of the system's own low numbers and of other VPNs. */
internal fun pickMacTunInterfaceName(existing: Collection<String>, first: Int = 9): String {
    val taken = existing.toSet()
    var number = first
    while ("utun$number" in taken) number += 1
    return "utun$number"
}

internal fun parseMacInterfaceList(output: String): List<String> = output.trim().split(Regex("\\s+")).filter(String::isNotEmpty)

/** The names of all network interfaces, from `ifconfig -l`. */
internal fun macInterfaceNames(): List<String> =
    runProcessWithTimeout(listOf("/sbin/ifconfig", "-l"), kotlin.time.Duration.parse("5s"))
        .map(::parseMacInterfaceList)
        .getOrDefault(emptyList())

/** The TUN device on macOS: Core binds its own sockets; the interface and routes belong to Core's supervisor. */
internal class DesktopMacTunDevice(
    private val core: DesktopCoreFfmNative,
) : DesktopTunDevice {
    override fun prepare(interfaceName: String): Result<Unit> = runCatching { core.prepareTun(interfaceName) }

    /** macOS hands over no descriptor: hev opens the utun interface itself, under Core's supervisor. */
    override fun open(planJson: String): Result<Int> = Result.success(NoDescriptor)

    override fun close(): Result<Unit> = runCatching { core.closeTun() }

    private companion object {
        const val NoDescriptor = -1
    }
}

/** Where Core's supervisor keeps its markers and the generated hev configuration. */
internal fun macTunRunDirectory(): Path = Path.of(System.getProperty("user.home"), "SKIPI", "run")
