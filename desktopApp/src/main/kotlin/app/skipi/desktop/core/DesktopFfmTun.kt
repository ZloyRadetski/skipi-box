// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop


/** TUN device owned by SKIPI Core: NetworkManager creates it, Core attaches and keeps it. */
internal class DesktopCoreTunDevice(private val core: DesktopCoreFfmNative) : DesktopTunDevice {
    override fun prepare(interfaceName: String): Result<Unit> = runCatching { core.prepareTun(interfaceName) }

    override fun open(planJson: String): Result<Int> = runCatching { core.openTun(planJson) }

    override fun close(): Result<Unit> = runCatching { core.closeTun() }
}
