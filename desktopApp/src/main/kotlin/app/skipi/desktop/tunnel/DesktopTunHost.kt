// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Path

/**
 * What the Desktop host needs to run TUN mode: the bundled hev library, the Core library that
 * brings the interface up, and an operating system that lets the user do it. The check explains
 * why not, so the settings page can say so instead of hiding the mode.
 */
internal class DesktopTunHost(
    private val isLinux: Boolean,
    private val runtime: () -> Result<DesktopCoreRuntime>,
    private val coreProbe: () -> String?,
    private val device: () -> DesktopTunDevice,
    private val hev: (Path) -> DesktopHevTunnel,
) {
    private var tunRuntime: DesktopTunRuntime? = null

    /** Whether this operating system has a TUN implementation at all. */
    val hostSupported: Boolean get() = isLinux

    /** Null when TUN mode can be started on this machine right now. */
    fun unavailableReason(): String? {
        if (!isLinux) return "TUN mode is available only on Linux so far"
        val files = runtime().getOrElse { return it.message ?: "SKIPI Core runtime is unavailable" }
        if (files.hevLibrary == null) return "libhev-socks5-tunnel.so is missing from the application"
        return coreProbe()
    }

    @Synchronized
    fun tunRuntime(): DesktopTunRuntime {
        tunRuntime?.let { return it }
        val hevLibrary = runtime().getOrThrow().hevLibrary
            ?: error("libhev-socks5-tunnel.so is missing from the application")
        return DesktopTunRuntime(device(), hev(hevLibrary)).also { tunRuntime = it }
    }

    companion object {
        fun forCore(
            coreController: DesktopCoreController,
            runtime: () -> Result<DesktopCoreRuntime> = DesktopCoreRuntimes::discover,
        ): DesktopTunHost = DesktopTunHost(
            isLinux = System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true),
            runtime = runtime,
            coreProbe = {
                coreController.nativeForTun().fold(
                    onSuccess = { native -> native.tunUnavailableReason() },
                    onFailure = { error -> error.message ?: "SKIPI Core library cannot be loaded" },
                )
            },
            device = {
                DesktopCoreTunDevice(coreController.nativeForTun().getOrThrow())
            },
            hev = { library -> DesktopHevFfmTunnel(library) },
        )
    }
}

/**
 * The reason the settings page shows next to the TUN switch. Null means TUN can be started, so a
 * missing host must be told apart from an available one explicitly rather than by null alone.
 */
internal fun tunUnavailableReasonFor(host: DesktopTunHost?): String? {
    if (host == null) return "TUN mode is not available"
    return host.unavailableReason()
}
