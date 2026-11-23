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
    private val isWindows: Boolean = false,
    private val isElevated: () -> Boolean = ::isWindowsElevated,
    private val isMac: Boolean = false,
    /** Where Core's macOS supervisor keeps its marker files and the generated hev configuration. */
    private val macRunDirectory: () -> Path = ::macTunRunDirectory,
    /** Whether Core's session, which carries hev, is running. */
    private val coreSessionRunning: () -> Boolean = { false },
) {
    private var tunRuntime: DesktopTunRuntime? = null

    /** Whether this operating system has a TUN implementation at all. */
    val hostSupported: Boolean get() = isLinux || isWindows || isMac

    /**
     * Windows creates the TUN adapter and its routes only for an administrator; the settings page
     * offers a UAC relaunch instead of a dead switch.
     */
    fun needsElevation(): Boolean = isWindows && !isElevated()

    /** Null when TUN mode can be started on this machine right now. */
    fun unavailableReason(): String? {
        if (!hostSupported) return "TUN mode is available only on Linux, Windows and macOS so far"
        runtime().getOrElse { return it.message ?: "SKIPI Core runtime is unavailable" }
        // hev is part of the Core library on every platform; Core reports at the start if a build lacks it.
        return coreProbe()
    }

    @Synchronized
    fun tunRuntime(): DesktopTunRuntime {
        tunRuntime?.let { return it }
        runtime().getOrThrow()
        val created = when {
            // Core's privileged supervisor runs the hev Core carries and shows the administrator prompt.
            isMac -> DesktopTunRuntime(
                device(),
                macRunDirectory = macRunDirectory(),
                coreSessionRunning = coreSessionRunning,
            )
            // Windows: Core supervises the hev it carries, which makes the interface itself. Linux: hev is
            // linked into Core and runs on the descriptor Core's device opens.
            else -> DesktopTunRuntime(device(), coreSessionRunning = coreSessionRunning)
        }
        return created.also { tunRuntime = it }
    }

    /** Closes the TUN device if this run created one; never creates the runtime just to stop it. */
    @Synchronized
    fun stopPreparedTun() {
        tunRuntime?.stop()
    }

    companion object {
        fun forCore(
            coreController: DesktopCoreController,
            runtime: () -> Result<DesktopCoreRuntime> = DesktopCoreRuntimes::discover,
        ): DesktopTunHost {
            val osName = System.getProperty("os.name").orEmpty()
            val windows = osName.contains("windows", ignoreCase = true)
            return forCore(
                coreController,
                runtime,
                linux = osName.contains("linux", ignoreCase = true),
                windows = windows,
                mac = osName.contains("mac", ignoreCase = true),
            )
        }

        internal fun forCore(
            coreController: DesktopCoreController,
            runtime: () -> Result<DesktopCoreRuntime>,
            linux: Boolean,
            windows: Boolean,
            mac: Boolean = false,
        ): DesktopTunHost = DesktopTunHost(
            isLinux = linux,
            isWindows = windows,
            isMac = mac,
            runtime = runtime,
            coreProbe = {
                coreController.nativeForTun().fold(
                    onSuccess = { native -> native.tunUnavailableReason() },
                    onFailure = { error -> error.message ?: "SKIPI Core library cannot be loaded" },
                )
            },
            device = {
                val core = coreController.nativeForTun().getOrThrow()
                when {
                    windows -> DesktopWindowsTunDevice(core)
                    mac -> DesktopMacTunDevice(core)
                    else -> DesktopCoreTunDevice(core)
                }
            },
            coreSessionRunning = coreController::sessionRunning,
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
