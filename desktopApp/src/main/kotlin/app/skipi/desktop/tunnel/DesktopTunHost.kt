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
    private val isWindows: Boolean = false,
    private val isElevated: () -> Boolean = ::isWindowsElevated,
) {
    private var tunRuntime: DesktopTunRuntime? = null

    /** Whether this operating system has a TUN implementation at all. */
    val hostSupported: Boolean get() = isLinux || isWindows

    /**
     * Windows creates the TUN adapter and its routes only for an administrator; the settings page
     * offers a UAC relaunch instead of a dead switch.
     */
    fun needsElevation(): Boolean = isWindows && !isElevated()

    /** Null when TUN mode can be started on this machine right now. */
    fun unavailableReason(): String? {
        if (!hostSupported) return "TUN mode is available only on Linux and Windows so far"
        val files = runtime().getOrElse { return it.message ?: "SKIPI Core runtime is unavailable" }
        if (isWindows) {
            if (files.hevExecutable == null) return "hev-socks5-tunnel.exe is missing from the application"
        } else if (files.hevLibrary == null) {
            return "libhev-socks5-tunnel.so is missing from the application"
        }
        return coreProbe()
    }

    @Synchronized
    fun tunRuntime(): DesktopTunRuntime {
        tunRuntime?.let { return it }
        val files = runtime().getOrThrow()
        val hevPath = if (isWindows) {
            files.hevExecutable ?: error("hev-socks5-tunnel.exe is missing from the application")
        } else {
            files.hevLibrary ?: error("libhev-socks5-tunnel.so is missing from the application")
        }
        return DesktopTunRuntime(device(), hev(hevPath)).also { tunRuntime = it }
    }

    companion object {
        fun forCore(
            coreController: DesktopCoreController,
            runtime: () -> Result<DesktopCoreRuntime> = DesktopCoreRuntimes::discover,
        ): DesktopTunHost {
            val osName = System.getProperty("os.name").orEmpty()
            val windows = osName.contains("windows", ignoreCase = true)
            return forCore(coreController, runtime, linux = osName.contains("linux", ignoreCase = true), windows = windows)
        }

        internal fun forCore(
            coreController: DesktopCoreController,
            runtime: () -> Result<DesktopCoreRuntime>,
            linux: Boolean,
            windows: Boolean,
        ): DesktopTunHost = DesktopTunHost(
            isLinux = linux,
            isWindows = windows,
            runtime = runtime,
            coreProbe = {
                coreController.nativeForTun().fold(
                    onSuccess = { native -> native.tunUnavailableReason() },
                    onFailure = { error -> error.message ?: "SKIPI Core library cannot be loaded" },
                )
            },
            device = {
                val core = coreController.nativeForTun().getOrThrow()
                if (windows) DesktopWindowsTunDevice(core) else DesktopCoreTunDevice(core)
            },
            hev = { path -> if (windows) DesktopHevProcessTunnel(path) else DesktopHevFfmTunnel(path) },
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
