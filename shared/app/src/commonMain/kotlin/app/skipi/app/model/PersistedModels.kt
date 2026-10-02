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
    /** Stable shared key for a named theme; retained across host-specific theme enums. */
    val themeVariant: String? = null,
    val seedIndex: Int = 0,
    val customMaterialYouSeed: Long? = null,
    val customColorsEnabled: Boolean = false,
    val customAccentColor: Long? = null,
    val customBackgroundColor: Long? = null,
    val customSurfaceColor: Long? = null,
    val customSurfaceVariantColor: Long? = null,
    val customTextColor: Long? = null,
    val customTextSecondaryColor: Long? = null,
    val customStatusRunningColor: Long? = null,
    val customStatusStoppedColor: Long? = null,
    val customPingFastColor: Long? = null,
    val customPingMediumColor: Long? = null,
    val customPingSlowColor: Long? = null,
    val customCategoryIconColor: Long? = null,
    val customProtocolVlessColor: Long? = null,
    val customProtocolVmessColor: Long? = null,
    val customProtocolHysteria2Color: Long? = null,
    val customProtocolTrojanColor: Long? = null,
    val customProtocolShadowsocksColor: Long? = null,
    val customProtocolWireguardColor: Long? = null,
    val customProtocolSocksColor: Long? = null,
    val customProtocolHttpColor: Long? = null,
    val customProtocolStrategyColor: Long? = null,
    val customProtocolChainColor: Long? = null,
    val customProtocolJsonColor: Long? = null,
    val fontFamilyMode: Int? = null,
    val fontSizeMode: Int? = null,
    val fontWeightMode: Int? = null,
    val backgroundStyle: Int? = null,
    val backgroundPhotoDimPercent: Int? = null,
    val bottomBarSize: Int? = null,
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
    val home: HomeDisplaySettings = HomeDisplaySettings(),
    val proxy: ProxySelectionSettings = ProxySelectionSettings(),
    val traffic: TrafficSelectionSettings = TrafficSelectionSettings(),
    val application: ApplicationPreferences = ApplicationPreferences(),
)

/** User-controlled Home presentation choices shared by the Android and Desktop UIs. */
data class HomeDisplaySettings(
    /** Existing app values: 0 = compact, 1 = classic. Null preserves a host default. */
    val connectionDisplayMode: Int? = null,
    val pinConnectionPanel: Boolean = false,
    val subscriptionSwipeEnabled: Boolean = true,
    val showFloatingPowerButton: Boolean = false,
    val confirmDeletion: Boolean = true,
    val showServerSearch: Boolean = false,
)

/** Small set of portable preferences; platform-owned settings stay in host extensions. */
data class ApplicationPreferences(
    val languageTag: String? = null,
    val hapticsEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
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

/** Persisted routing configuration shared by platform adapters. */
data class RoutingConfigRecord(
    val domainStrategy: Int = 0,
    val defaultOutboundTag: String = features.routing.model.DefaultRouteOutboundTag,
    val rules: List<features.routing.model.RouteRule> = emptyList(),
)

/** Resource definitions only. File presence, update progress, and paths belong to runtime/adapters. */
data class ResourceCatalogRecord(
    val sourceId: String = "default",
    val userAgent: String? = null,
    val customResources: List<ResourceDefinition> = emptyList(),
)

data class ResourceDefinition(
    val id: Int,
    val name: String,
    val url: String,
)
