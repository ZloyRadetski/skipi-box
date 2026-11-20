// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import engine.hevtun.HevSocks5TunnelYamlSpec
import engine.hevtun.buildHevSocks5TunnelConfigYaml
import engine.vpn.TunInterfacePlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Brings the operating system's tun interface up and hands its descriptor to the application. */
internal interface DesktopTunDevice {
    /** Binds Core's own sockets to the physical interface; must precede Core's start. */
    fun prepare(interfaceName: String): Result<Unit>

    /** Returns the attached tun descriptor, or -1 when the tunnel process creates the interface itself. */
    fun open(planJson: String): Result<Int>

    /** Finishes the setup once the tunnel runs on the interface (routes on Windows); a no-op elsewhere. */
    fun attach(plan: TunInterfacePlan): Result<Unit> = Result.success(Unit)

    fun close(): Result<Unit>
}

/** The interface to bring up and the local SOCKS inbound hev forwards its traffic to. */
internal data class DesktopTunStartRequest(
    val plan: TunInterfacePlan,
    val socksAddress: String,
    val socksPort: Int,
    val socksUsername: String,
    val socksPassword: String,
    val logPath: String,
)

/**
 * Desktop TUN session: the interface exists only while hev runs on it. Mirrors Android, where the
 * VpnService interface is handed to hev and torn down with it.
 *
 * SKIPI Core runs HEV TUN inside its session on every platform: the runtime opens the device before Core starts,
 * describes hev for the session plan ([coreHevPlan]), and attaches the routes once the session runs ([attachForCore]).
 * Core stops hev with the session, so [stop] only closes the device.
 */
internal class DesktopTunRuntime(
    private val device: DesktopTunDevice,
    /** Set on macOS: Core's privileged supervisor runs hev there and keeps its marker files in this directory. */
    private val macRunDirectory: java.nio.file.Path? = null,
    /** Whether Core's session, which carries hev, is running. */
    private val coreSessionRunning: () -> Boolean = { false },
) {
    private var started = false

    /** Brings the device up and returns the descriptor for Core's session plan (-1 where hev creates the interface). */
    @Synchronized
    fun openForCore(plan: TunInterfacePlan): Result<Int> = device.open(plan.toDevicePlan().encode())

    /**
     * The hev block of Core's session plan: its configuration plus the descriptor (Linux, -1 on Windows) or the run
     * directory and interface plan for Core's macOS supervisor. Core carries the hev program itself.
     */
    fun coreHevPlan(request: DesktopTunStartRequest, tunFd: Int): DesktopCoreHevPlan {
        val runDirectory = macRunDirectory
        return DesktopCoreHevPlan(
            configYaml = request.toHevYaml(),
            tunFd = if (runDirectory == null) tunFd else null,
            runDirectory = runDirectory?.toString(),
            macInterface = if (runDirectory == null) null else request.plan.toDevicePlan().toCoreMacInterface(),
        )
    }

    /** Finishes the setup once Core's session runs on the interface (routes on Windows). */
    @Synchronized
    fun attachForCore(plan: TunInterfacePlan): Result<Unit> {
        val attached = device.attach(plan)
        if (attached.isSuccess) started = true
        return attached
    }

    /** Puts the socket binding in place before Core starts, so none of its connections escape it. */
    fun prepare(plan: TunInterfacePlan): Result<Unit> = device.prepare(plan.name)

    /** Undoes [prepare] when the session never got as far as [attachForCore]. */
    fun release(): Result<Unit> = device.close()

    /** Core already stopped hev with the session; only the device is left to close. */
    @Synchronized
    fun stop(): Result<Unit> {
        if (!started) return Result.success(Unit)
        started = false
        return device.close()
    }

    fun isRunning(): Boolean = started && coreSessionRunning()
}

@Serializable
private data class DevicePlan(
    val name: String,
    val mtu: Int,
    val addresses: List<String>,
    val routes: List<String>,
    /** Left out when empty, so a plan without DNS reads as before. */
    val dns: List<String> = emptyList(),
)

private val DevicePlanJson = Json

private fun TunInterfacePlan.toDevicePlan(): DevicePlan = DevicePlan(
    name = name,
    mtu = mtu,
    addresses = addresses.map { "${it.address}/${it.prefixLength}" },
    routes = routes.map { "${it.address}/${it.prefixLength}" },
    dns = dnsServers,
)

private fun DevicePlan.encode(): String = DevicePlanJson.encodeToString(this)

private fun DevicePlan.toCoreMacInterface() = DesktopCoreMacInterface(name, mtu, addresses, routes, dns)

private fun DesktopTunStartRequest.toHevYaml(): String {
    val ipv4 = plan.addresses.first { ':' !in it.address }
    val ipv6 = plan.addresses.firstOrNull { ':' in it.address }
    return buildHevSocks5TunnelConfigYaml(
        HevSocks5TunnelYamlSpec(
            mtu = plan.mtu,
            ipv4Address = ipv4.address,
            ipv6Address = ipv6?.address,
            socksAddress = socksAddress,
            socksPort = socksPort,
            logPath = logPath,
            tunnelName = plan.name,
            socksUsername = socksUsername,
            socksPassword = socksPassword,
            enableTcpFastOpen = true,
        ),
    )
}
