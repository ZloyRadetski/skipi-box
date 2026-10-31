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

/** Runs hev-socks5-tunnel on an already attached tun descriptor. */
internal interface DesktopHevTunnel {
    /** Returns once the tunnel is running, or the reason it could not start. */
    fun start(configYaml: String, tunFd: Int): Result<Unit>

    fun stop(): Result<Unit>

    fun isRunning(): Boolean
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
 */
internal class DesktopTunRuntime(
    private val device: DesktopTunDevice,
    private val tunnel: DesktopHevTunnel,
) {
    private var started = false

    /** Puts the socket binding in place before Core starts, so none of its connections escape it. */
    fun prepare(plan: TunInterfacePlan): Result<Unit> = device.prepare(plan.name)

    /** Undoes [prepare] when the session never got as far as [start]. */
    fun release(): Result<Unit> = device.close()

    @Synchronized
    fun start(request: DesktopTunStartRequest): Result<Unit> {
        if (started) return Result.failure(IllegalStateException("TUN is already running"))
        val fd = device.open(request.plan.toDevicePlanJson()).getOrElse { return Result.failure(it) }
        val result = tunnel.start(request.toHevYaml(), fd)
        if (result.isFailure) {
            device.close()
            return result
        }
        val attached = device.attach(request.plan)
        if (attached.isFailure) {
            tunnel.stop()
            device.close()
            return attached
        }
        started = true
        return Result.success(Unit)
    }

    @Synchronized
    fun stop(): Result<Unit> {
        if (!started) return Result.success(Unit)
        started = false
        val tunnelResult = tunnel.stop()
        val deviceResult = device.close()
        return tunnelResult.takeIf { it.isFailure } ?: deviceResult
    }

    fun isRunning(): Boolean = started && tunnel.isRunning()
}

@Serializable
private data class DevicePlan(
    val name: String,
    val mtu: Int,
    val addresses: List<String>,
    val routes: List<String>,
)

private val DevicePlanJson = Json

private fun TunInterfacePlan.toDevicePlanJson(): String = DevicePlanJson.encodeToString(
    DevicePlan(
        name = name,
        mtu = mtu,
        addresses = addresses.map { "${it.address}/${it.prefixLength}" },
        routes = routes.map { "${it.address}/${it.prefixLength}" },
    ),
)

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
