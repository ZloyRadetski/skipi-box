// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.model

import features.config.SkipiPerAppSettings
import features.config.parseSkipiPerAppSettings
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.ProxyServerInfo
import features.subscription.SubscriptionMetadata

/**
 * Persisted appearance choices.  A tunnel lifecycle flag deliberately does
 * not belong here; it is supplied by [app.skipi.app.runtime.AppRuntimeState].
 */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColors: Boolean = true,
    val amoledBlack: Boolean = false,
)

enum class ThemeMode {
    System,
    Light,
    Dark,
    Named,
}

/** User choices which survive process recreation and are safe to serialize. */
data class ProxySelectionSettings(
    val selectedServerId: Int? = null,
    val selectedGroupId: Int? = null,
)

data class TrafficSelectionSettings(
    val activeConfigId: Int? = null,
)

/** Root persisted state shared by Android and Desktop storage adapters. */
data class PersistedSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val proxy: ProxySelectionSettings = ProxySelectionSettings(),
    val traffic: TrafficSelectionSettings = TrafficSelectionSettings(),
)

/**
 * A stable application identity around the mutable core proxy model.
 *
 * Existing core proxy implementations are intentionally reused instead of
 * being duplicated in the application module.  Repository implementations
 * should replace a record with a new value when editing a server; they must
 * not mutate a record while it is being emitted from a StateFlow.
 */
data class ProxyServerRecord(
    val id: Int,
    val server: ProxyServer<*>,
    /** Null means the host's default/manual group; adapters map it to their existing format. */
    val sourceSubscriptionId: Int? = null,
    val enabled: Boolean = true,
) {
    val info: ProxyServerInfo get() = server.getInfo()
}

/** Subscription data that is independent of Room, JSON, or platform I/O. */
data class SubscriptionRecord(
    val id: Int,
    val title: String,
    val url: String,
    val enabled: Boolean = true,
    val metadata: SubscriptionMetadata? = null,
    val lastUpdatedAtMillis: Long? = null,
)

/**
 * Portable traffic configuration envelope.  The raw document remains intact
 * so platform adapters can preserve fields unknown to this layer.
 */
data class TrafficConfigRecord(
    val id: Int,
    val name: String,
    val rawDocument: String,
    val sourceUrl: String? = null,
    val enabled: Boolean = true,
    val locked: Boolean = false,
) {
    val perAppSettings: SkipiPerAppSettings
        get() = rawDocument.parseSkipiPerAppSettings()
}
