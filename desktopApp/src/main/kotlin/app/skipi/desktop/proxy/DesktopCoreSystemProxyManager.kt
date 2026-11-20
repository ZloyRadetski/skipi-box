// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.ui.resources.Res
import app.skipi.ui.resources.desktop_system_proxy_activated
import app.skipi.ui.resources.desktop_system_proxy_already_owned
import app.skipi.ui.resources.desktop_system_proxy_no_active_lease
import app.skipi.ui.resources.desktop_system_proxy_no_interrupted_lease
import app.skipi.ui.resources.desktop_system_proxy_no_lease_stored
import app.skipi.ui.resources.desktop_system_proxy_no_saved_lease
import app.skipi.ui.resources.desktop_system_proxy_recovered
import app.skipi.ui.resources.desktop_system_proxy_restored
import app.skipi.ui.resources.desktop_system_proxy_windows_changed_outside
import app.skipi.ui.resources.desktop_system_proxy_windows_points
import app.skipi.ui.resources.desktop_system_proxy_windows_recovered
import app.skipi.ui.resources.desktop_system_proxy_windows_restored
import app.skipi.ui.resources.desktop_system_proxy_windows_stale_lease
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** One call into SKIPI Core's system proxy (`SkipiCoreDesktopSystemProxy`): an operation and its JSON arguments. */
internal fun interface DesktopSystemProxyCore {
    fun call(operation: String, arguments: String?): String
}

@Serializable
private data class CoreProxyDirectory(val directory: String)

@Serializable
private data class CoreProxyAction(val action: String)

@Serializable
private data class CoreProxySupported(val supported: Boolean)

@Serializable
private data class CoreProxyRepair(val changed: Boolean)

/**
 * The operating system's proxy settings, which SKIPI Core owns on Windows, Linux and macOS alike: Core
 * snapshots the user's values, points the system at the local endpoints, gives the user's values back, and
 * restores them after a crash. This class only asks, and words what Core reports for the status line.
 */
internal class DesktopCoreSystemProxyManager(
    private val core: () -> Result<DesktopSystemProxyCore>,
    private val directory: Path = DesktopSystemProxyDirectory.default(),
    private val osName: String = System.getProperty("os.name"),
) : DesktopSystemProxyManager {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val windows = osName.startsWith("Windows", ignoreCase = true)
    private val platform = when {
        windows -> "Windows"
        osName.startsWith("Mac", ignoreCase = true) -> "macOS"
        else -> "Linux"
    }
    private var configuredCore: DesktopSystemProxyCore? = null
    private var supported: Boolean? = null

    /** Windows needs nothing installed, so it answers without loading Core; the others ask what Core finds. */
    @Synchronized
    override fun isSupportedHost(): Boolean {
        if (windows) return true
        supported?.let { return it }
        val answer = runCatching {
            json.decodeFromString<CoreProxySupported>(call("supported", null)).supported
        }.getOrDefault(false)
        supported = answer
        return answer
    }

    override fun acquire(endpoints: DesktopSystemProxyEndpoints): Result<DesktopSystemProxyLeaseResult> = runCatching {
        val action = action(call("acquire", json.encodeToString(endpoints)))
        DesktopSystemProxyLeaseResult(action, message = acquireMessage(action, endpoints)).also {
            if (action == DesktopSystemProxyLeaseAction.Acquired) {
                DesktopLogger.info("SystemProxy", "Acquired $platform system proxy (HTTP ${endpoints.httpPort}, SOCKS ${endpoints.socksPort})")
            }
        }
    }

    override fun release(): Result<DesktopSystemProxyLeaseResult> = runCatching {
        val action = action(call("release", null))
        DesktopSystemProxyLeaseResult(action, message = releaseMessage(action))
    }

    override fun recover(): Result<DesktopSystemProxyLeaseResult> = runCatching {
        val action = action(call("recover", null))
        DesktopSystemProxyLeaseResult(action, message = recoverMessage(action))
    }

    override fun forceClear(): Result<Unit> = runCatching { call("forceClear", null); Unit }

    override fun repairStale(): Result<Boolean> = runCatching {
        json.decodeFromString<CoreProxyRepair>(call("repairStale", null)).changed
    }

    /** Runs one Core operation, telling Core first where it keeps its lease. */
    @Synchronized
    private fun call(operation: String, arguments: String?): String {
        val bridge = configuredCore ?: core().getOrThrow().also { loaded ->
            loaded.call("configure", json.encodeToString(CoreProxyDirectory(directory.toString())))
            configuredCore = loaded
        }
        return bridge.call(operation, arguments)
    }

    private fun action(answer: String): DesktopSystemProxyLeaseAction = when (val wire = json.decodeFromString<CoreProxyAction>(answer).action) {
        "acquired" -> DesktopSystemProxyLeaseAction.Acquired
        "alreadyAcquired" -> DesktopSystemProxyLeaseAction.AlreadyAcquired
        "released" -> DesktopSystemProxyLeaseAction.Released
        "nothingToRelease" -> DesktopSystemProxyLeaseAction.NothingToRelease
        "recovered" -> DesktopSystemProxyLeaseAction.Recovered
        "skippedNotOwner" -> DesktopSystemProxyLeaseAction.SkippedNotOwner
        else -> error("SKIPI Core reported an unknown system proxy action: $wire")
    }

    private fun acquireMessage(action: DesktopSystemProxyLeaseAction, endpoints: DesktopSystemProxyEndpoints): String = when {
        action == DesktopSystemProxyLeaseAction.AlreadyAcquired -> desktopText(Res.string.desktop_system_proxy_already_owned, platform)
        windows -> desktopText(Res.string.desktop_system_proxy_windows_points, endpoints.host, endpoints.httpPort)
        else -> desktopText(Res.string.desktop_system_proxy_activated, platform)
    }

    private fun releaseMessage(action: DesktopSystemProxyLeaseAction): String = when (action) {
        DesktopSystemProxyLeaseAction.SkippedNotOwner -> desktopText(Res.string.desktop_system_proxy_windows_changed_outside)
        DesktopSystemProxyLeaseAction.NothingToRelease ->
            if (windows) desktopText(Res.string.desktop_system_proxy_no_lease_stored)
            else desktopText(Res.string.desktop_system_proxy_no_active_lease, platform)
        else ->
            if (windows) desktopText(Res.string.desktop_system_proxy_windows_restored)
            else desktopText(Res.string.desktop_system_proxy_restored, platform)
    }

    private fun recoverMessage(action: DesktopSystemProxyLeaseAction): String = when (action) {
        DesktopSystemProxyLeaseAction.SkippedNotOwner -> desktopText(Res.string.desktop_system_proxy_windows_stale_lease)
        DesktopSystemProxyLeaseAction.NothingToRelease ->
            if (windows) desktopText(Res.string.desktop_system_proxy_no_interrupted_lease)
            else desktopText(Res.string.desktop_system_proxy_no_saved_lease)
        else ->
            if (windows) desktopText(Res.string.desktop_system_proxy_windows_recovered)
            else desktopText(Res.string.desktop_system_proxy_recovered, platform)
    }
}

/** Where Core keeps the lease of an interrupted session: next to the app's other per-user files. */
internal object DesktopSystemProxyDirectory {
    fun default(): Path {
        val appData = System.getenv("APPDATA")?.takeIf(String::isNotBlank)
        val base = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true) && appData != null) appData
        else System.getProperty("user.home")
        return Path.of(base, "SKIPI")
    }
}
