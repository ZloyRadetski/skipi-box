// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.platform

/**
 * A capability is an operation that may require a platform adapter.
 *
 * The shared application layer can use this set to hide actions which cannot
 * be fulfilled by the current client, without importing Android or Desktop
 * APIs into common code.
 */
enum class PlatformCapability {
    Tunnel,
    SystemProxy,
    SplitTunneling,
    KillSwitch,
    BackgroundExecution,
    Clipboard,
    FilePicker,
    Notifications,
    QrCode,
}

/** Immutable description of the capabilities exposed by a platform adapter. */
data class PlatformCapabilities(
    val platformName: String,
    val supported: Set<PlatformCapability> = emptySet(),
) {
    fun supports(capability: PlatformCapability): Boolean = capability in supported
}

/** Optional observable source for capabilities which can change at runtime. */
interface PlatformCapabilitiesProvider {
    val capabilities: kotlinx.coroutines.flow.StateFlow<PlatformCapabilities>
}
