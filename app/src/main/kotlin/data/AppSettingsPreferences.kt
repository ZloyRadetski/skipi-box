// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import app.AppState
import features.config.TrafficConfigState
import features.networkautomation.model.NetworkAutomationRule
import app.CustomResourceFileState
import app.ServiceControlSchedule
import app.ServiceControlSettings
import app.ServiceControlWifi
import app.ServiceControlWifiRule
import app.modes.ColorModeThemeDark
import app.modes.ColorModeThemeLight
import app.modes.ColorModeThemeSystem
import app.modes.normalizeColorMode
import app.modes.normalizeLanguageMode
import app.modes.normalizeAppIcon
import features.settings.servicecontrol.normalizeServiceControlSettings
import features.subscription.normalizeSkipiUserAgent
import features.subscription.normalizeSkipiUserAgents
import java.util.UUID
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal class AppSettingsPreferences(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    /**
     * A stable, app-scoped subscription identifier. The UUID is encrypted with
     * an AES key in AndroidKeyStore; the transmitted value is an HMAC rather
     * than the raw Android ID or local UUID.
     */
    fun getOrCreateSubscriptionHwid(): String {
        synchronized(SubscriptionHwidLock) {
            val installationUuid = preferences.getString(KeySubscriptionInstallationUuid, null)
                ?.let(::decryptSubscriptionInstallationUuid)
                ?: UUID.randomUUID().toString().also { generated ->
                    val encrypted = encryptSubscriptionInstallationUuid(generated)
                    check(preferences.edit().putString(KeySubscriptionInstallationUuid, encrypted).commit()) {
                        "Failed to persist subscription installation UUID"
                    }
                }
            return appContext.subscriptionHwid(installationUuid)
        }
    }

    fun load(): AppState {
        val defaults = AppState()
        val trafficConfigs = preferences.getTrafficConfigList(
            KeyTrafficConfigs,
            defaults.trafficConfigs,
        ).ifEmpty { defaults.trafficConfigs }.map { config ->
            config.copy(
                resourceSettings = config.resourceSettings.copy(
                    userAgent = normalizeSkipiUserAgent(config.resourceSettings.userAgent),
                ),
            )
        }
        val nextTrafficConfigId = maxOf(
            preferences.getInt(KeyNextTrafficConfigId, defaults.nextTrafficConfigId),
            (trafficConfigs.maxOfOrNull { config -> config.id } ?: 0) + 1,
        )
        val activeTrafficConfigId = preferences.getInt(
            KeyActiveTrafficConfigId,
            defaults.activeTrafficConfigId,
        ).takeIf { id -> trafficConfigs.any { config -> config.id == id } }
            ?: trafficConfigs.first().id
        val customResourceFiles = preferences.getCustomResourceFileList(
            KeyCustomResourceFiles,
            defaults.customResourceFiles,
        )
        val nextCustomResourceFileId = maxOf(
            preferences.getInt(KeyNextCustomResourceFileId, defaults.nextCustomResourceFileId),
            (customResourceFiles.maxOfOrNull { file -> file.id } ?: 0) + 1,
        )
        return defaults.copy(
            appIcon = preferences.getInt(KeyAppIcon, defaults.appIcon).let(::normalizeAppIcon),
            colorMode = preferences.getInt(KeyColorMode, defaults.colorMode).let { storedMode ->
                when (storedMode) {
                    ColorModeThemeSystem,
                    ColorModeThemeLight,
                    ColorModeThemeDark -> storedMode
                    else -> normalizeColorMode(storedMode)
                }
            },
            languageMode = preferences.getInt(KeyLanguageMode, defaults.languageMode).let(::normalizeLanguageMode),
            enableMaterialYou = preferences.getBoolean(KeyEnableMaterialYou, defaults.enableMaterialYou),
            seedIndex = preferences.getInt(KeySeedIndex, defaults.seedIndex),
            customMaterialYouSeed = if (preferences.contains(KeyCustomMaterialYouSeed)) preferences.getLong(KeyCustomMaterialYouSeed, 0L) else defaults.customMaterialYouSeed,
            enableCustomColors = preferences.getBoolean(KeyEnableCustomColors, defaults.enableCustomColors),
            customAccentColor = if (preferences.contains(KeyCustomAccentColor)) preferences.getLong(KeyCustomAccentColor, 0L) else defaults.customAccentColor,
            customBackgroundColor = if (preferences.contains(KeyCustomBackgroundColor)) preferences.getLong(KeyCustomBackgroundColor, 0L) else defaults.customBackgroundColor,
            customSurfaceColor = if (preferences.contains(KeyCustomSurfaceColor)) preferences.getLong(KeyCustomSurfaceColor, 0L) else defaults.customSurfaceColor,
            customSurfaceVariantColor = if (preferences.contains(KeyCustomSurfaceVariantColor)) preferences.getLong(KeyCustomSurfaceVariantColor, 0L) else defaults.customSurfaceVariantColor,
            customTextColor = if (preferences.contains(KeyCustomTextColor)) preferences.getLong(KeyCustomTextColor, 0L) else defaults.customTextColor,
            customTextSecondaryColor = if (preferences.contains(KeyCustomTextSecondaryColor)) preferences.getLong(KeyCustomTextSecondaryColor, 0L) else defaults.customTextSecondaryColor,
            customStatusRunningColor = if (preferences.contains(KeyCustomStatusRunningColor)) preferences.getLong(KeyCustomStatusRunningColor, 0L) else defaults.customStatusRunningColor,
            customStatusStoppedColor = if (preferences.contains(KeyCustomStatusStoppedColor)) preferences.getLong(KeyCustomStatusStoppedColor, 0L) else defaults.customStatusStoppedColor,
            customPingFastColor = if (preferences.contains(KeyCustomPingFastColor)) preferences.getLong(KeyCustomPingFastColor, 0L) else defaults.customPingFastColor,
            customPingMediumColor = if (preferences.contains(KeyCustomPingMediumColor)) preferences.getLong(KeyCustomPingMediumColor, 0L) else defaults.customPingMediumColor,
            customPingSlowColor = if (preferences.contains(KeyCustomPingSlowColor)) preferences.getLong(KeyCustomPingSlowColor, 0L) else defaults.customPingSlowColor,
            customCategoryAppearanceColor = if (preferences.contains(KeyCustomCategoryAppearanceColor)) preferences.getLong(KeyCustomCategoryAppearanceColor, 0L) else defaults.customCategoryAppearanceColor,
            customCategoryVpnColor = if (preferences.contains(KeyCustomCategoryVpnColor)) preferences.getLong(KeyCustomCategoryVpnColor, 0L) else defaults.customCategoryVpnColor,
            customCategoryProxyColor = if (preferences.contains(KeyCustomCategoryProxyColor)) preferences.getLong(KeyCustomCategoryProxyColor, 0L) else defaults.customCategoryProxyColor,
            customCategorySubscriptionsColor = if (preferences.contains(KeyCustomCategorySubscriptionsColor)) preferences.getLong(KeyCustomCategorySubscriptionsColor, 0L) else defaults.customCategorySubscriptionsColor,
            customCategoryIntegrationColor = if (preferences.contains(KeyCustomCategoryIntegrationColor)) preferences.getLong(KeyCustomCategoryIntegrationColor, 0L) else defaults.customCategoryIntegrationColor,
            customCategoryLogsColor = if (preferences.contains(KeyCustomCategoryLogsColor)) preferences.getLong(KeyCustomCategoryLogsColor, 0L) else defaults.customCategoryLogsColor,
            customCategoryBackupColor = if (preferences.contains(KeyCustomCategoryBackupColor)) preferences.getLong(KeyCustomCategoryBackupColor, 0L) else defaults.customCategoryBackupColor,
            customCategoryAboutColor = if (preferences.contains(KeyCustomCategoryAboutColor)) preferences.getLong(KeyCustomCategoryAboutColor, 0L) else defaults.customCategoryAboutColor,
            customProtocolVlessColor = if (preferences.contains(KeyCustomProtocolVlessColor)) preferences.getLong(KeyCustomProtocolVlessColor, 0L) else defaults.customProtocolVlessColor,
            customProtocolVmessColor = if (preferences.contains(KeyCustomProtocolVmessColor)) preferences.getLong(KeyCustomProtocolVmessColor, 0L) else defaults.customProtocolVmessColor,
            customProtocolHysteria2Color = if (preferences.contains(KeyCustomProtocolHysteria2Color)) preferences.getLong(KeyCustomProtocolHysteria2Color, 0L) else defaults.customProtocolHysteria2Color,
            customProtocolTrojanColor = if (preferences.contains(KeyCustomProtocolTrojanColor)) preferences.getLong(KeyCustomProtocolTrojanColor, 0L) else defaults.customProtocolTrojanColor,
            customProtocolShadowsocksColor = if (preferences.contains(KeyCustomProtocolShadowsocksColor)) preferences.getLong(KeyCustomProtocolShadowsocksColor, 0L) else defaults.customProtocolShadowsocksColor,
            customProtocolWireguardColor = if (preferences.contains(KeyCustomProtocolWireguardColor)) preferences.getLong(KeyCustomProtocolWireguardColor, 0L) else defaults.customProtocolWireguardColor,
            customProtocolSocksColor = if (preferences.contains(KeyCustomProtocolSocksColor)) preferences.getLong(KeyCustomProtocolSocksColor, 0L) else defaults.customProtocolSocksColor,
            customProtocolHttpColor = if (preferences.contains(KeyCustomProtocolHttpColor)) preferences.getLong(KeyCustomProtocolHttpColor, 0L) else defaults.customProtocolHttpColor,
            customProtocolStrategyColor = if (preferences.contains(KeyCustomProtocolStrategyColor)) preferences.getLong(KeyCustomProtocolStrategyColor, 0L) else defaults.customProtocolStrategyColor,
            customProtocolChainColor = if (preferences.contains(KeyCustomProtocolChainColor)) preferences.getLong(KeyCustomProtocolChainColor, 0L) else defaults.customProtocolChainColor,
            customProtocolJsonColor = if (preferences.contains(KeyCustomProtocolJsonColor)) preferences.getLong(KeyCustomProtocolJsonColor, 0L) else defaults.customProtocolJsonColor,
            nextSubscriptionGroupId = preferences.getInt(
                KeyNextSubscriptionGroupId,
                defaults.nextSubscriptionGroupId,
            ),
            enableAllProxyGroup = preferences.getBoolean(KeyEnableAllProxyGroup, defaults.enableAllProxyGroup),
            enableDeletionConfirmation = preferences.getBoolean(
                KeyEnableDeletionConfirmation,
                defaults.enableDeletionConfirmation,
            ),
            runMode = defaults.runMode,
            enableResolveProxyServerDomain = preferences.getBoolean(
                KeyEnableResolveProxyServerDomain,
                defaults.enableResolveProxyServerDomain,
            ),
            enableVpnLocalDns = preferences.getBoolean(KeyEnableVpnLocalDns, defaults.enableVpnLocalDns),
            localProxyPort = preferences.getString(KeyLocalProxyPort, defaults.localProxyPort) ?: defaults.localProxyPort,
            enableDynamicLocalProxyPort = preferences.getBoolean(
                KeyEnableDynamicLocalProxyPort,
                defaults.enableDynamicLocalProxyPort,
            ),
            localProxyListenAllInterfaces = preferences.getBoolean(
                KeyLocalProxyListenAllInterfaces,
                defaults.localProxyListenAllInterfaces,
            ),
            localProxyUsername = preferences.getString(
                KeyLocalProxyUsername,
                defaults.localProxyUsername,
            ) ?: defaults.localProxyUsername,
            localProxyPassword = preferences.getString(
                KeyLocalProxyPassword,
                defaults.localProxyPassword,
            ) ?: defaults.localProxyPassword,
            enableVpnAppendHttpProxy = preferences.getBoolean(
                KeyEnableVpnAppendHttpProxy,
                defaults.enableVpnAppendHttpProxy,
            ),
            enableVpnHevTun = preferences.getBoolean(
                KeyEnableVpnHevTun,
                defaults.enableVpnHevTun,
            ),
            tunMtu = preferences.getString(KeyTunMtu, defaults.tunMtu) ?: defaults.tunMtu,
            tunVpnDns = preferences.getString(KeyTunVpnDns, defaults.tunVpnDns) ?: defaults.tunVpnDns,
            tunIpv4Cidr = preferences.getString(KeyTunIpv4Cidr, defaults.tunIpv4Cidr) ?: defaults.tunIpv4Cidr,
            tunIpv6Cidr = preferences.getString(KeyTunIpv6Cidr, defaults.tunIpv6Cidr) ?: defaults.tunIpv6Cidr,
            enableWakeLock = preferences.getBoolean(KeyEnableWakeLock, defaults.enableWakeLock),
            enableSeamlessNetworkSwitching = preferences.getBoolean(
                KeyEnableSeamlessNetworkSwitching,
                defaults.enableSeamlessNetworkSwitching,
            ),
            enableNetworkAutomation = preferences.getBoolean(
                KeyEnableNetworkAutomation,
                defaults.enableNetworkAutomation,
            ),
            enableOnDemandVpn = preferences.getBoolean(
                KeyEnableOnDemandVpn,
                defaults.enableOnDemandVpn,
            ),
            networkAutomationRules = preferences.getNetworkAutomationRuleList(
                KeyNetworkAutomationRules,
                defaults.networkAutomationRules,
            ),
            tunTcpKeepAliveInterval = preferences.getString(
                KeyTunTcpKeepAliveInterval,
                defaults.tunTcpKeepAliveInterval,
            ) ?: defaults.tunTcpKeepAliveInterval,
            tunTcpUserTimeout = preferences.getString(
                KeyTunTcpUserTimeout,
                defaults.tunTcpUserTimeout,
            ) ?: defaults.tunTcpUserTimeout,
            nextProxyServerId = preferences.getInt(KeyNextProxyServerId, defaults.nextProxyServerId),
            selectedProxyServerId = preferences.getInt(KeySelectedProxyServerId, defaults.selectedProxyServerId),
            proxyServerListLayout = preferences.getInt(
                KeyProxyServerListLayout,
                defaults.proxyServerListLayout,
            ),
            proxyServerListSort = preferences.getInt(KeyProxyServerListSort, defaults.proxyServerListSort),
            subscriptionPingMode = preferences.getInt(KeySubscriptionPingMode, defaults.subscriptionPingMode),
            subscriptionPingUrl = preferences.getString(KeySubscriptionPingUrl, defaults.subscriptionPingUrl)
                ?: defaults.subscriptionPingUrl,
            subscriptionPingTimeoutMillis = preferences.getString(
                KeySubscriptionPingTimeoutMillis,
                defaults.subscriptionPingTimeoutMillis,
            ) ?: defaults.subscriptionPingTimeoutMillis,
            subscriptionPingConcurrency = preferences.getInt(
                KeySubscriptionPingConcurrency,
                defaults.subscriptionPingConcurrency,
            ).coerceIn(1, 64),
            subscriptionUserAgents = normalizeSkipiUserAgents(
                preferences.getStringList(
                    KeySubscriptionUserAgents,
                    defaults.subscriptionUserAgents,
                ).ifEmpty { defaults.subscriptionUserAgents },
            ),
            enableSubscriptionDeviceHeaders = preferences.getBoolean(
                KeyEnableSubscriptionDeviceHeaders,
                defaults.enableSubscriptionDeviceHeaders,
            ),
            subscriptionFetchTimeoutSeconds = preferences.getInt(
                KeySubscriptionFetchTimeoutSeconds,
                defaults.subscriptionFetchTimeoutSeconds,
            ),
            enableSubscriptionExpiryNotifications = preferences.getBoolean(
                KeyEnableSubscriptionExpiryNotifications,
                defaults.enableSubscriptionExpiryNotifications,
            ),
            subscriptionExpiryReminders = preferences.getStringList(
                KeySubscriptionExpiryReminders,
                defaults.subscriptionExpiryReminders.map { it.toSerializedString() },
            ).mapNotNull { app.SubscriptionExpiryReminder.fromSerializedStringOrNull(it) }
                .ifEmpty { defaults.subscriptionExpiryReminders },
            trafficConfigs = trafficConfigs,
            nextTrafficConfigId = nextTrafficConfigId,
            activeTrafficConfigId = activeTrafficConfigId,
            routeDomainStrategy = preferences.getInt(KeyRouteDomainStrategy, defaults.routeDomainStrategy),
            defaultRouteOutboundTag = preferences.getString(
                KeyDefaultRouteOutboundTag,
                defaults.defaultRouteOutboundTag,
            ) ?: defaults.defaultRouteOutboundTag,
            nextRouteRuleId = preferences.getInt(KeyNextRouteRuleId, defaults.nextRouteRuleId),
            coreLogLevel = preferences.getInt(KeyCoreLogLevel, defaults.coreLogLevel),
            enableAccessLog = preferences.getBoolean(KeyEnableAccessLog, defaults.enableAccessLog),
            logRetentionDays = preferences.getInt(KeyLogRetentionDays, defaults.logRetentionDays),
            resourceFileSource = preferences.getInt(KeyResourceFileSource, defaults.resourceFileSource),
            customResourceFileGeoIpUrl = preferences.getString(
                KeyCustomResourceFileGeoIpUrl,
                defaults.customResourceFileGeoIpUrl,
            ) ?: defaults.customResourceFileGeoIpUrl,
            customResourceFileGeoSiteUrl = preferences.getString(
                KeyCustomResourceFileGeoSiteUrl,
                defaults.customResourceFileGeoSiteUrl,
            ) ?: defaults.customResourceFileGeoSiteUrl,
            customResourceFileGeoIpOnlyCnPrivateUrl = preferences.getString(
                KeyCustomResourceFileGeoIpOnlyCnPrivateUrl,
                defaults.customResourceFileGeoIpOnlyCnPrivateUrl,
            ) ?: defaults.customResourceFileGeoIpOnlyCnPrivateUrl,
            customResourceFileDirectCidrIpv4Url = preferences.getString(
                KeyCustomResourceFileDirectCidrIpv4Url,
                defaults.customResourceFileDirectCidrIpv4Url,
            ) ?: defaults.customResourceFileDirectCidrIpv4Url,
            customResourceFileDirectCidrIpv6Url = preferences.getString(
                KeyCustomResourceFileDirectCidrIpv6Url,
                defaults.customResourceFileDirectCidrIpv6Url,
            ) ?: defaults.customResourceFileDirectCidrIpv6Url,
            customResourceFiles = customResourceFiles,
            nextCustomResourceFileId = nextCustomResourceFileId,
            resourceFileUserAgent = normalizeSkipiUserAgent(
                preferences.getString(
                    KeyResourceFileUserAgent,
                    defaults.resourceFileUserAgent,
                ) ?: defaults.resourceFileUserAgent,
            ),
            enableSniffing = preferences.getBoolean(KeyEnableSniffing, defaults.enableSniffing),
            enableSniffingRouteOnly = preferences.getBoolean(
                KeyEnableSniffingRouteOnly,
                defaults.enableSniffingRouteOnly,
            ),
            enableMux = preferences.getBoolean(KeyEnableMux, defaults.enableMux),
            muxConcurrency = preferences.getString(KeyMuxConcurrency, defaults.muxConcurrency) ?: defaults.muxConcurrency,
            muxXudpConcurrency = preferences.getString(
                KeyMuxXudpConcurrency,
                defaults.muxXudpConcurrency,
            ) ?: defaults.muxXudpConcurrency,
            muxXudpProxyUdp443 = preferences.getInt(KeyMuxXudpProxyUdp443, defaults.muxXudpProxyUdp443),
            enableFragment = preferences.getBoolean(KeyEnableFragment, defaults.enableFragment),
            fragmentPackets = preferences.getString(
                KeyFragmentPackets,
                defaults.fragmentPackets,
            ) ?: defaults.fragmentPackets,
            fragmentLength = preferences.getString(
                KeyFragmentLength,
                defaults.fragmentLength,
            ) ?: defaults.fragmentLength,
            fragmentInterval = preferences.getString(
                KeyFragmentInterval,
                defaults.fragmentInterval,
            ) ?: defaults.fragmentInterval,
            enableTrafficStatsNotification = preferences.getBoolean(
                KeyEnableTrafficStatsNotification,
                defaults.enableTrafficStatsNotification,
            ),
            showServerSearch = preferences.getBoolean(
                KeyShowServerSearch,
                defaults.showServerSearch,
            ),
            connectionDisplayMode = preferences.getInt(
                KeyConnectionDisplayMode,
                defaults.connectionDisplayMode,
            ),
            backgroundStyle = preferences.getInt(
                KeyBackgroundStyle,
                defaults.backgroundStyle,
            ),
            backgroundPhotoDimPercent = preferences.getInt(
                KeyBackgroundPhotoDimPercent,
                defaults.backgroundPhotoDimPercent,
            ),
            bottomBarSize = preferences.getInt(
                KeyBottomBarSize,
                defaults.bottomBarSize,
            ),
            hasCompletedOnboarding = preferences.getBoolean(
                KeyHasCompletedOnboarding,
                defaults.hasCompletedOnboarding,
            ),
            classicShowFloatingPowerButton = preferences.getBoolean(
                KeyClassicShowFloatingPowerButton,
                defaults.classicShowFloatingPowerButton,
            ),
            showTunnelMemoryOnHome = preferences.getBoolean(
                KeyShowTunnelMemoryOnHome,
                defaults.showTunnelMemoryOnHome,
            ),
            enableBroadcastControl = preferences.getBoolean(
                KeyEnableBroadcastControl,
                defaults.enableBroadcastControl,
            ),
            enableIpv6 = preferences.getBoolean(KeyEnableIpv6, defaults.enableIpv6),
            enableIpv6Prefer = preferences.getBoolean(KeyEnableIpv6Prefer, defaults.enableIpv6Prefer),
            enableFakeDns = preferences.getBoolean(KeyEnableFakeDns, defaults.enableFakeDns),
            proxyDns = preferences.getStringList(KeyProxyDns, defaults.proxyDns),
            directDns = preferences.getStringList(KeyDirectDns, defaults.directDns),
            directDnsDomains = preferences.getStringList(KeyDirectDnsDomains, defaults.directDnsDomains),
            enableDirectDnsForProxyServerDomains = preferences.getBoolean(
                KeyEnableDirectDnsForProxyServerDomains,
                defaults.enableDirectDnsForProxyServerDomains,
            ),
            dnsHosts = preferences.getStringList(KeyDnsHosts, defaults.dnsHosts),
            serviceControl = preferences.getServiceControl(defaults.serviceControl),
            proxyAppListMode = preferences.getInt(KeyProxyAppListMode, defaults.proxyAppListMode),
            autoCheckAppUpdates = preferences.getBoolean(KeyAutoCheckAppUpdates, defaults.autoCheckAppUpdates),
            autoInstallAppUpdatesAtNight = preferences.getBoolean(KeyAutoInstallAppUpdatesAtNight, defaults.autoInstallAppUpdatesAtNight),
            dismissedUpdateVersion = preferences.getString(KeyDismissedUpdateVersion, defaults.dismissedUpdateVersion) ?: defaults.dismissedUpdateVersion,
        )
    }

    fun save(state: AppState) {
        preferences.edit { putAppState(state) }
    }

    /** Clears every persisted application preference, including the local HWID seed. */
    fun clear() {
        check(preferences.edit().clear().commit()) {
            "Failed to clear application preferences"
        }
    }

    private fun SharedPreferences.Editor.putAppState(state: AppState): SharedPreferences.Editor {
        return putInt(KeyAppIcon, state.appIcon)
            .putInt(KeyColorMode, state.colorMode)
            .putInt(KeyLanguageMode, state.languageMode)
            .putBoolean(KeyEnableMaterialYou, state.enableMaterialYou)
            .putInt(KeySeedIndex, state.seedIndex)
            .apply {
                if (state.customMaterialYouSeed != null) putLong(KeyCustomMaterialYouSeed, state.customMaterialYouSeed) else remove(KeyCustomMaterialYouSeed)
                putBoolean(KeyEnableCustomColors, state.enableCustomColors)
                if (state.customAccentColor != null) putLong(KeyCustomAccentColor, state.customAccentColor) else remove(KeyCustomAccentColor)
                if (state.customBackgroundColor != null) putLong(KeyCustomBackgroundColor, state.customBackgroundColor) else remove(KeyCustomBackgroundColor)
                if (state.customSurfaceColor != null) putLong(KeyCustomSurfaceColor, state.customSurfaceColor) else remove(KeyCustomSurfaceColor)
                if (state.customSurfaceVariantColor != null) putLong(KeyCustomSurfaceVariantColor, state.customSurfaceVariantColor) else remove(KeyCustomSurfaceVariantColor)
                if (state.customTextColor != null) putLong(KeyCustomTextColor, state.customTextColor) else remove(KeyCustomTextColor)
                if (state.customTextSecondaryColor != null) putLong(KeyCustomTextSecondaryColor, state.customTextSecondaryColor) else remove(KeyCustomTextSecondaryColor)
                if (state.customStatusRunningColor != null) putLong(KeyCustomStatusRunningColor, state.customStatusRunningColor) else remove(KeyCustomStatusRunningColor)
                if (state.customStatusStoppedColor != null) putLong(KeyCustomStatusStoppedColor, state.customStatusStoppedColor) else remove(KeyCustomStatusStoppedColor)
                if (state.customPingFastColor != null) putLong(KeyCustomPingFastColor, state.customPingFastColor) else remove(KeyCustomPingFastColor)
                if (state.customPingMediumColor != null) putLong(KeyCustomPingMediumColor, state.customPingMediumColor) else remove(KeyCustomPingMediumColor)
                if (state.customPingSlowColor != null) putLong(KeyCustomPingSlowColor, state.customPingSlowColor) else remove(KeyCustomPingSlowColor)
                if (state.customCategoryAppearanceColor != null) putLong(KeyCustomCategoryAppearanceColor, state.customCategoryAppearanceColor) else remove(KeyCustomCategoryAppearanceColor)
                if (state.customCategoryVpnColor != null) putLong(KeyCustomCategoryVpnColor, state.customCategoryVpnColor) else remove(KeyCustomCategoryVpnColor)
                if (state.customCategoryProxyColor != null) putLong(KeyCustomCategoryProxyColor, state.customCategoryProxyColor) else remove(KeyCustomCategoryProxyColor)
                if (state.customCategorySubscriptionsColor != null) putLong(KeyCustomCategorySubscriptionsColor, state.customCategorySubscriptionsColor) else remove(KeyCustomCategorySubscriptionsColor)
                if (state.customCategoryIntegrationColor != null) putLong(KeyCustomCategoryIntegrationColor, state.customCategoryIntegrationColor) else remove(KeyCustomCategoryIntegrationColor)
                if (state.customCategoryLogsColor != null) putLong(KeyCustomCategoryLogsColor, state.customCategoryLogsColor) else remove(KeyCustomCategoryLogsColor)
                if (state.customCategoryBackupColor != null) putLong(KeyCustomCategoryBackupColor, state.customCategoryBackupColor) else remove(KeyCustomCategoryBackupColor)
                if (state.customCategoryAboutColor != null) putLong(KeyCustomCategoryAboutColor, state.customCategoryAboutColor) else remove(KeyCustomCategoryAboutColor)
                if (state.customProtocolVlessColor != null) putLong(KeyCustomProtocolVlessColor, state.customProtocolVlessColor) else remove(KeyCustomProtocolVlessColor)
                if (state.customProtocolVmessColor != null) putLong(KeyCustomProtocolVmessColor, state.customProtocolVmessColor) else remove(KeyCustomProtocolVmessColor)
                if (state.customProtocolHysteria2Color != null) putLong(KeyCustomProtocolHysteria2Color, state.customProtocolHysteria2Color) else remove(KeyCustomProtocolHysteria2Color)
                if (state.customProtocolTrojanColor != null) putLong(KeyCustomProtocolTrojanColor, state.customProtocolTrojanColor) else remove(KeyCustomProtocolTrojanColor)
                if (state.customProtocolShadowsocksColor != null) putLong(KeyCustomProtocolShadowsocksColor, state.customProtocolShadowsocksColor) else remove(KeyCustomProtocolShadowsocksColor)
                if (state.customProtocolWireguardColor != null) putLong(KeyCustomProtocolWireguardColor, state.customProtocolWireguardColor) else remove(KeyCustomProtocolWireguardColor)
                if (state.customProtocolSocksColor != null) putLong(KeyCustomProtocolSocksColor, state.customProtocolSocksColor) else remove(KeyCustomProtocolSocksColor)
                if (state.customProtocolHttpColor != null) putLong(KeyCustomProtocolHttpColor, state.customProtocolHttpColor) else remove(KeyCustomProtocolHttpColor)
                if (state.customProtocolStrategyColor != null) putLong(KeyCustomProtocolStrategyColor, state.customProtocolStrategyColor) else remove(KeyCustomProtocolStrategyColor)
                if (state.customProtocolChainColor != null) putLong(KeyCustomProtocolChainColor, state.customProtocolChainColor) else remove(KeyCustomProtocolChainColor)
                if (state.customProtocolJsonColor != null) putLong(KeyCustomProtocolJsonColor, state.customProtocolJsonColor) else remove(KeyCustomProtocolJsonColor)
            }
            .putInt(KeyNextSubscriptionGroupId, state.nextSubscriptionGroupId)
            .putBoolean(KeyEnableAllProxyGroup, state.enableAllProxyGroup)
            .putBoolean(KeyEnableDeletionConfirmation, state.enableDeletionConfirmation)
            .putInt(KeyRunMode, state.runMode)
            .putBoolean(KeyEnableResolveProxyServerDomain, state.enableResolveProxyServerDomain)
            .putBoolean(KeyEnableVpnLocalDns, state.enableVpnLocalDns)
            .putString(KeyLocalProxyPort, state.localProxyPort)
            .putBoolean(KeyEnableDynamicLocalProxyPort, state.enableDynamicLocalProxyPort)
            .putBoolean(KeyLocalProxyListenAllInterfaces, state.localProxyListenAllInterfaces)
            .putString(KeyLocalProxyUsername, state.localProxyUsername)
            .putString(KeyLocalProxyPassword, state.localProxyPassword)
            .putBoolean(KeyEnableVpnAppendHttpProxy, state.enableVpnAppendHttpProxy)
            .putBoolean(KeyEnableVpnHevTun, state.enableVpnHevTun)
            .putString(KeyTunMtu, state.tunMtu)
            .putString(KeyTunVpnDns, state.tunVpnDns)
            .putString(KeyTunIpv4Cidr, state.tunIpv4Cidr)
            .putString(KeyTunIpv6Cidr, state.tunIpv6Cidr)
            .putBoolean(KeyEnableWakeLock, state.enableWakeLock)
            .putBoolean(KeyEnableSeamlessNetworkSwitching, state.enableSeamlessNetworkSwitching)
            .putBoolean(KeyEnableNetworkAutomation, state.enableNetworkAutomation)
            .putBoolean(KeyEnableOnDemandVpn, state.enableOnDemandVpn)
            .putNetworkAutomationRuleList(KeyNetworkAutomationRules, state.networkAutomationRules)
            .putString(KeyTunTcpKeepAliveInterval, state.tunTcpKeepAliveInterval)
            .putString(KeyTunTcpUserTimeout, state.tunTcpUserTimeout)
            .putInt(KeyNextProxyServerId, state.nextProxyServerId)
            .putInt(KeySelectedProxyServerId, state.selectedProxyServerId)
            .putInt(KeyProxyServerListLayout, state.proxyServerListLayout)
            .putInt(KeyProxyServerListSort, state.proxyServerListSort)
            .putInt(KeySubscriptionPingMode, state.subscriptionPingMode)
            .putString(KeySubscriptionPingUrl, state.subscriptionPingUrl)
            .putString(KeySubscriptionPingTimeoutMillis, state.subscriptionPingTimeoutMillis)
            .putInt(KeySubscriptionPingConcurrency, state.subscriptionPingConcurrency)
            .putStringList(KeySubscriptionUserAgents, state.subscriptionUserAgents)
            .putBoolean(KeyEnableSubscriptionDeviceHeaders, state.enableSubscriptionDeviceHeaders)
            .putInt(KeySubscriptionFetchTimeoutSeconds, state.subscriptionFetchTimeoutSeconds)
            .putTrafficConfigList(KeyTrafficConfigs, state.trafficConfigs)
            .putInt(KeyNextTrafficConfigId, state.nextTrafficConfigId)
            .putInt(KeyActiveTrafficConfigId, state.activeTrafficConfigId)
            .putInt(KeyRouteDomainStrategy, state.routeDomainStrategy)
            .putString(KeyDefaultRouteOutboundTag, state.defaultRouteOutboundTag)
            .putInt(KeyNextRouteRuleId, state.nextRouteRuleId)
            .putInt(KeyCoreLogLevel, state.coreLogLevel)
            .putBoolean(KeyEnableAccessLog, state.enableAccessLog)
            .putInt(KeyLogRetentionDays, state.logRetentionDays)
            .putInt(KeyResourceFileSource, state.resourceFileSource)
            .putString(KeyCustomResourceFileGeoIpUrl, state.customResourceFileGeoIpUrl)
            .putString(KeyCustomResourceFileGeoSiteUrl, state.customResourceFileGeoSiteUrl)
            .putString(KeyCustomResourceFileGeoIpOnlyCnPrivateUrl, state.customResourceFileGeoIpOnlyCnPrivateUrl)
            .putString(KeyCustomResourceFileDirectCidrIpv4Url, state.customResourceFileDirectCidrIpv4Url)
            .putString(KeyCustomResourceFileDirectCidrIpv6Url, state.customResourceFileDirectCidrIpv6Url)
            .putCustomResourceFileList(KeyCustomResourceFiles, state.customResourceFiles)
            .putInt(KeyNextCustomResourceFileId, state.nextCustomResourceFileId)
            .putString(KeyResourceFileUserAgent, state.resourceFileUserAgent)
            .putBoolean(KeyEnableSniffing, state.enableSniffing)
            .putBoolean(KeyEnableSniffingRouteOnly, state.enableSniffingRouteOnly)
            .putBoolean(KeyEnableMux, state.enableMux)
            .putString(KeyMuxConcurrency, state.muxConcurrency)
            .putString(KeyMuxXudpConcurrency, state.muxXudpConcurrency)
            .putInt(KeyMuxXudpProxyUdp443, state.muxXudpProxyUdp443)
            .putBoolean(KeyEnableFragment, state.enableFragment)
            .putString(KeyFragmentPackets, state.fragmentPackets)
            .putString(KeyFragmentLength, state.fragmentLength)
            .putString(KeyFragmentInterval, state.fragmentInterval)
            .putBoolean(KeyEnableTrafficStatsNotification, state.enableTrafficStatsNotification)
            .putBoolean(KeyShowServerSearch, state.showServerSearch)
            .putInt(KeyConnectionDisplayMode, state.connectionDisplayMode)
            .putInt(KeyBottomBarSize, state.bottomBarSize)
            .putBoolean(KeyHasCompletedOnboarding, state.hasCompletedOnboarding)
            .putBoolean(KeyClassicShowFloatingPowerButton, state.classicShowFloatingPowerButton)
            .putBoolean(KeyShowTunnelMemoryOnHome, state.showTunnelMemoryOnHome)
            .putBoolean(KeyEnableBroadcastControl, state.enableBroadcastControl)
            .putBoolean(KeyEnableIpv6, state.enableIpv6)
            .putBoolean(KeyEnableIpv6Prefer, state.enableIpv6Prefer)
            .putBoolean(KeyEnableFakeDns, state.enableFakeDns)
            .putStringList(KeyProxyDns, state.proxyDns)
            .putStringList(KeyDirectDns, state.directDns)
            .putStringList(KeyDirectDnsDomains, state.directDnsDomains)
            .putBoolean(KeyEnableDirectDnsForProxyServerDomains, state.enableDirectDnsForProxyServerDomains)
            .putStringList(KeyDnsHosts, state.dnsHosts)
            .putServiceControl(state.serviceControl)
            .putInt(KeyProxyAppListMode, state.proxyAppListMode)
            .putBoolean(KeyAutoCheckAppUpdates, state.autoCheckAppUpdates)
            .putBoolean(KeyAutoInstallAppUpdatesAtNight, state.autoInstallAppUpdatesAtNight)
            .putInt(KeyBackgroundStyle, state.backgroundStyle)
            .putInt(KeyBackgroundPhotoDimPercent, state.backgroundPhotoDimPercent)
            .putBoolean(KeyEnableSubscriptionExpiryNotifications, state.enableSubscriptionExpiryNotifications)
            .putStringList(
                KeySubscriptionExpiryReminders,
                state.subscriptionExpiryReminders.map { it.toSerializedString() },
            )
            .putString(KeyDismissedUpdateVersion, state.dismissedUpdateVersion)
    }

    private fun SharedPreferences.getServiceControl(
        defaults: ServiceControlSettings,
    ): ServiceControlSettings = normalizeServiceControlSettings(
        ServiceControlSettings(
            enabled = getBoolean(KeyServiceControlEnabled, defaults.enabled),
            schedule = ServiceControlSchedule(
                enabled = getBoolean(KeyServiceControlScheduleEnabled, defaults.schedule.enabled),
                startCron = getString(KeyServiceControlScheduleStartCron, defaults.schedule.startCron)
                    ?: defaults.schedule.startCron,
                stopCron = getString(KeyServiceControlScheduleStopCron, defaults.schedule.stopCron)
                    ?: defaults.schedule.stopCron,
            ),
            wifi = ServiceControlWifi(
                enabled = getBoolean(KeyServiceControlWifiEnabled, defaults.wifi.enabled),
                connectStart = getServiceControlWifiRule(
                    defaults.wifi.connectStart,
                    KeyServiceControlWifiConnectStartEnabled,
                    KeyServiceControlWifiConnectStartSsids,
                    KeyServiceControlWifiConnectStartBssids,
                ),
                connectStop = getServiceControlWifiRule(
                    defaults.wifi.connectStop,
                    KeyServiceControlWifiConnectStopEnabled,
                    KeyServiceControlWifiConnectStopSsids,
                    KeyServiceControlWifiConnectStopBssids,
                ),
                disconnectStart = getServiceControlWifiRule(
                    defaults.wifi.disconnectStart,
                    KeyServiceControlWifiDisconnectStartEnabled,
                    KeyServiceControlWifiDisconnectStartSsids,
                    KeyServiceControlWifiDisconnectStartBssids,
                ),
                disconnectStop = getServiceControlWifiRule(
                    defaults.wifi.disconnectStop,
                    KeyServiceControlWifiDisconnectStopEnabled,
                    KeyServiceControlWifiDisconnectStopSsids,
                    KeyServiceControlWifiDisconnectStopBssids,
                ),
            ),
        ),
    )

    private fun SharedPreferences.getServiceControlWifiRule(
        defaults: ServiceControlWifiRule,
        enabledKey: String,
        ssidsKey: String,
        bssidsKey: String,
    ): ServiceControlWifiRule = ServiceControlWifiRule(
        enabled = getBoolean(enabledKey, defaults.enabled),
        ssids = getStringList(ssidsKey, defaults.ssids),
        bssids = getStringList(bssidsKey, defaults.bssids),
    )

    private fun SharedPreferences.Editor.putServiceControl(
        value: ServiceControlSettings,
    ): SharedPreferences.Editor =
        putBoolean(KeyServiceControlEnabled, value.enabled)
            .putBoolean(KeyServiceControlScheduleEnabled, value.schedule.enabled)
            .putString(KeyServiceControlScheduleStartCron, value.schedule.startCron)
            .putString(KeyServiceControlScheduleStopCron, value.schedule.stopCron)
            .putBoolean(KeyServiceControlWifiEnabled, value.wifi.enabled)
            .putServiceControlWifiRule(
                value.wifi.connectStart,
                KeyServiceControlWifiConnectStartEnabled,
                KeyServiceControlWifiConnectStartSsids,
                KeyServiceControlWifiConnectStartBssids,
            )
            .putServiceControlWifiRule(
                value.wifi.connectStop,
                KeyServiceControlWifiConnectStopEnabled,
                KeyServiceControlWifiConnectStopSsids,
                KeyServiceControlWifiConnectStopBssids,
            )
            .putServiceControlWifiRule(
                value.wifi.disconnectStart,
                KeyServiceControlWifiDisconnectStartEnabled,
                KeyServiceControlWifiDisconnectStartSsids,
                KeyServiceControlWifiDisconnectStartBssids,
            )
            .putServiceControlWifiRule(
                value.wifi.disconnectStop,
                KeyServiceControlWifiDisconnectStopEnabled,
                KeyServiceControlWifiDisconnectStopSsids,
                KeyServiceControlWifiDisconnectStopBssids,
            )

    private fun SharedPreferences.Editor.putServiceControlWifiRule(
        value: ServiceControlWifiRule,
        enabledKey: String,
        ssidsKey: String,
        bssidsKey: String,
    ): SharedPreferences.Editor =
        putBoolean(enabledKey, value.enabled)
            .putStringList(ssidsKey, value.ssids)
            .putStringList(bssidsKey, value.bssids)

    private fun SharedPreferences.getStringList(key: String, defaultValue: List<String>): List<String> {
        return getString(key, null)?.let(StringListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putStringList(
        key: String,
        values: List<String>,
    ): SharedPreferences.Editor {
        return putString(key, StringListJson.encode(values))
    }

    private fun SharedPreferences.getCustomResourceFileList(
        key: String,
        defaultValue: List<CustomResourceFileState>,
    ): List<CustomResourceFileState> {
        return getString(key, null)?.let(CustomResourceFileListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putCustomResourceFileList(
        key: String,
        values: List<CustomResourceFileState>,
    ): SharedPreferences.Editor {
        return putString(key, CustomResourceFileListJson.encode(values))
    }

    private fun SharedPreferences.getTrafficConfigList(
        key: String,
        defaultValue: List<TrafficConfigState>,
    ): List<TrafficConfigState> {
        return getString(key, null)?.let(TrafficConfigListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putTrafficConfigList(
        key: String,
        values: List<TrafficConfigState>,
    ): SharedPreferences.Editor {
        return putString(key, TrafficConfigListJson.encode(values))
    }

    private fun SharedPreferences.getNetworkAutomationRuleList(
        key: String,
        defaultValue: List<NetworkAutomationRule>,
    ): List<NetworkAutomationRule> {
        return getString(key, null)?.let(NetworkAutomationRuleListJson::decode) ?: defaultValue
    }

    private fun SharedPreferences.Editor.putNetworkAutomationRuleList(
        key: String,
        values: List<NetworkAutomationRule>,
    ): SharedPreferences.Editor {
        return putString(key, NetworkAutomationRuleListJson.encode(values))
    }
}

private fun encryptSubscriptionInstallationUuid(value: String): String {
    val cipher = Cipher.getInstance(SubscriptionHwidCipherTransformation)
    cipher.init(Cipher.ENCRYPT_MODE, subscriptionHwidEncryptionKey())
    val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
    return listOf(cipher.iv, encrypted)
        .joinToString(SubscriptionHwidEncryptedValueSeparator) { bytes ->
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
}

private fun decryptSubscriptionInstallationUuid(value: String): String? {
    val encodedParts = value.split(SubscriptionHwidEncryptedValueSeparator, limit = 2)
    if (encodedParts.size != 2) return null
    return runCatching {
        val initializationVector = Base64.decode(encodedParts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(encodedParts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(SubscriptionHwidCipherTransformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            subscriptionHwidEncryptionKey(),
            GCMParameterSpec(SubscriptionHwidGcmTagLengthBits, initializationVector),
        )
        cipher.doFinal(encrypted).toString(StandardCharsets.UTF_8)
    }.getOrNull()?.takeIf(String::isNotBlank)
}

private fun subscriptionHwidEncryptionKey(): SecretKey {
    val keyStore = KeyStore.getInstance(SubscriptionHwidKeyStoreProvider).apply { load(null) }
    if (!keyStore.containsAlias(SubscriptionHwidKeyAlias)) {
        KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            SubscriptionHwidKeyStoreProvider,
        ).apply {
            init(
                KeyGenParameterSpec.Builder(
                    SubscriptionHwidKeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }
    return keyStore.getKey(SubscriptionHwidKeyAlias, null) as? SecretKey
        ?: error("Subscription identifier key is unavailable")
}

@SuppressLint("HardwareIds")
private fun Context.subscriptionHwid(localUuid: String): String {
    val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        .orEmpty()
        .ifBlank { packageName }
    val payload = listOf(
        androidId,
        Build.MANUFACTURER.orEmpty(),
        Build.MODEL.orEmpty(),
        localUuid,
    ).joinToString(SubscriptionHwidPayloadSeparator)
    val mac = Mac.getInstance(SubscriptionHwidMacAlgorithm).apply {
        init(
            SecretKeySpec(
                SubscriptionHwidSalt.toByteArray(StandardCharsets.UTF_8),
                SubscriptionHwidMacAlgorithm,
            ),
        )
    }
    return mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val PreferencesName = "skipi_settings"
private const val SubscriptionHwidKeyStoreProvider = "AndroidKeyStore"
private const val SubscriptionHwidKeyAlias = "skipi.subscription.installation.uuid"
private const val SubscriptionHwidCipherTransformation = "AES/GCM/NoPadding"
private const val SubscriptionHwidMacAlgorithm = "HmacSHA256"
private const val SubscriptionHwidSalt = "SKIPI::subscription-hwid::v1::Radetski"
private const val SubscriptionHwidEncryptedValueSeparator = ":"
private const val SubscriptionHwidPayloadSeparator = ":"
private const val SubscriptionHwidGcmTagLengthBits = 128
private const val KeyAppIcon = "app_icon"
private const val KeyColorMode = "color_mode"
private const val KeyLanguageMode = "language_mode"
private const val KeyEnableMaterialYou = "enable_material_you"
private const val KeySeedIndex = "seed_index"
private const val KeyCustomMaterialYouSeed = "custom_material_you_seed"
private const val KeyEnableCustomColors = "enable_custom_colors"
private const val KeyCustomAccentColor = "custom_accent_color"
private const val KeyCustomBackgroundColor = "custom_background_color"
private const val KeyCustomSurfaceColor = "custom_surface_color"
private const val KeyCustomSurfaceVariantColor = "custom_surface_variant_color"
private const val KeyCustomTextColor = "custom_text_color"
private const val KeyCustomTextSecondaryColor = "custom_text_secondary_color"
private const val KeyCustomStatusRunningColor = "custom_status_running_color"
private const val KeyCustomStatusStoppedColor = "custom_status_stopped_color"
private const val KeyCustomPingFastColor = "custom_ping_fast_color"
private const val KeyCustomPingMediumColor = "custom_ping_medium_color"
private const val KeyCustomPingSlowColor = "custom_ping_slow_color"
private const val KeyCustomCategoryAppearanceColor = "custom_category_appearance_color"
private const val KeyCustomCategoryVpnColor = "custom_category_vpn_color"
private const val KeyCustomCategoryProxyColor = "custom_category_proxy_color"
private const val KeyCustomCategorySubscriptionsColor = "custom_category_subscriptions_color"
private const val KeyCustomCategoryIntegrationColor = "custom_category_integration_color"
private const val KeyCustomCategoryLogsColor = "custom_category_logs_color"
private const val KeyCustomCategoryBackupColor = "custom_category_backup_color"
private const val KeyCustomCategoryAboutColor = "custom_category_about_color"
private const val KeyCustomProtocolVlessColor = "custom_protocol_vless_color"
private const val KeyCustomProtocolVmessColor = "custom_protocol_vmess_color"
private const val KeyCustomProtocolHysteria2Color = "custom_protocol_hysteria2_color"
private const val KeyCustomProtocolTrojanColor = "custom_protocol_trojan_color"
private const val KeyCustomProtocolShadowsocksColor = "custom_protocol_shadowsocks_color"
private const val KeyCustomProtocolWireguardColor = "custom_protocol_wireguard_color"
private const val KeyCustomProtocolSocksColor = "custom_protocol_socks_color"
private const val KeyCustomProtocolHttpColor = "custom_protocol_http_color"
private const val KeyCustomProtocolStrategyColor = "custom_protocol_strategy_color"
private const val KeyCustomProtocolChainColor = "custom_protocol_chain_color"
private const val KeyCustomProtocolJsonColor = "custom_protocol_json_color"
private const val KeySubscriptionInstallationUuid = "subscription_installation_uuid"
private const val KeyNextSubscriptionGroupId = "next_subscription_group_id"
private const val KeyEnableAllProxyGroup = "enable_all_proxy_group"
private const val KeyEnableDeletionConfirmation = "enable_deletion_confirmation"
private const val KeyRunMode = "run_mode"
private const val KeyEnableResolveProxyServerDomain = "enable_resolve_proxy_server_domain"
private const val KeyEnableVpnLocalDns = "enable_vpn_local_dns"
private const val KeyLocalProxyPort = "local_proxy_port"
private const val KeyEnableDynamicLocalProxyPort = "enable_dynamic_local_proxy_port"
private const val KeyLocalProxyListenAllInterfaces = "local_proxy_listen_all_interfaces"
private const val KeyLocalProxyUsername = "local_proxy_username"
private const val KeyLocalProxyPassword = "local_proxy_password"
private const val KeyEnableVpnAppendHttpProxy = "enable_vpn_append_http_proxy"
private const val KeyEnableVpnHevTun = "enable_vpn_hev_tun"
private const val KeyTunMtu = "tun_mtu"
private const val KeyTunVpnDns = "tun_vpn_dns"
private const val KeyTunIpv4Cidr = "tun_ipv4_cidr"
private const val KeyTunIpv6Cidr = "tun_ipv6_cidr"
private const val KeyEnableWakeLock = "enable_wake_lock"
private const val KeyEnableSeamlessNetworkSwitching = "enable_seamless_network_switching"
private const val KeyEnableNetworkAutomation = "enable_network_automation"
private const val KeyEnableOnDemandVpn = "enable_on_demand_vpn"
private const val KeyNetworkAutomationRules = "network_automation_rules"
private const val KeyTunTcpKeepAliveInterval = "tun_tcp_keep_alive_interval"
private const val KeyTunTcpUserTimeout = "tun_tcp_user_timeout"
private const val KeyNextProxyServerId = "next_proxy_server_id"
private const val KeySelectedProxyServerId = "selected_proxy_server_id"
private const val KeyProxyServerListLayout = "proxy_server_list_layout"
private const val KeyProxyServerListSort = "proxy_server_list_sort"
private const val KeySubscriptionPingMode = "subscription_ping_mode"
private const val KeySubscriptionPingUrl = "subscription_ping_url"
private const val KeySubscriptionPingTimeoutMillis = "subscription_ping_timeout_millis"
private const val KeySubscriptionPingConcurrency = "subscription_ping_concurrency"
private const val KeySubscriptionUserAgents = "subscription_user_agents"
private const val KeyEnableSubscriptionDeviceHeaders = "enable_subscription_device_headers"
private const val KeySubscriptionFetchTimeoutSeconds = "subscription_fetch_timeout_seconds"
private const val KeyTrafficConfigs = "traffic_configs"
private const val KeyNextTrafficConfigId = "next_traffic_config_id"
private const val KeyActiveTrafficConfigId = "active_traffic_config_id"
private const val KeyRouteDomainStrategy = "route_domain_strategy"
private const val KeyDefaultRouteOutboundTag = "default_route_outbound_tag"
private const val KeyNextRouteRuleId = "next_route_rule_id"
private const val KeyCoreLogLevel = "core_log_level"
private const val KeyEnableAccessLog = "enable_access_log"
private const val KeyLogRetentionDays = "log_retention_days"
private const val KeyResourceFileSource = "resource_file_source"
private const val KeyCustomResourceFileGeoIpUrl = "custom_resource_file_geoip_url"
private const val KeyCustomResourceFileGeoSiteUrl = "custom_resource_file_geosite_url"
private const val KeyCustomResourceFileGeoIpOnlyCnPrivateUrl = "custom_resource_file_geoip_only_cn_private_url"
private const val KeyCustomResourceFileDirectCidrIpv4Url = "custom_resource_file_direct_cidr_ipv4_url"
private const val KeyCustomResourceFileDirectCidrIpv6Url = "custom_resource_file_direct_cidr_ipv6_url"
private const val KeyCustomResourceFiles = "custom_resource_files"
private const val KeyNextCustomResourceFileId = "next_custom_resource_file_id"
private const val KeyResourceFileUserAgent = "resource_file_user_agent"
private const val KeyEnableSniffing = "enable_sniffing"
private const val KeyEnableSniffingRouteOnly = "enable_sniffing_route_only"
private const val KeyEnableMux = "enable_mux"
private const val KeyMuxConcurrency = "mux_concurrency"
private const val KeyMuxXudpConcurrency = "mux_xudp_concurrency"
private const val KeyMuxXudpProxyUdp443 = "mux_xudp_proxy_udp_443"
private const val KeyEnableFragment = "enable_fragment"
private const val KeyFragmentPackets = "fragment_packets"
private const val KeyFragmentLength = "fragment_length"
private const val KeyFragmentInterval = "fragment_interval"
private const val KeyEnableTrafficStatsNotification = "enable_traffic_stats_notification"
private const val KeyShowServerSearch = "show_server_search"
private const val KeyConnectionDisplayMode = "connection_display_mode"
private const val KeyBackgroundStyle = "background_style"
private const val KeyBackgroundPhotoDimPercent = "background_photo_dim_percent"
private const val KeyBottomBarSize = "bottom_bar_size"
private const val KeyClassicShowFloatingPowerButton = "classic_show_floating_power_button"
private const val KeyShowTunnelMemoryOnHome = "show_tunnel_memory_on_home"
private const val KeyEnableBroadcastControl = "enable_broadcast_control"
private const val KeyEnableIpv6 = "enable_ipv6"
private const val KeyEnableIpv6Prefer = "enable_ipv6_prefer"
private const val KeyEnableFakeDns = "enable_fake_dns"
private const val KeyProxyDns = "proxy_dns"
private const val KeyDirectDns = "direct_dns"
private const val KeyDirectDnsDomains = "direct_dns_domains"
private const val KeyEnableDirectDnsForProxyServerDomains = "enable_direct_dns_for_proxy_server_domains"
private const val KeyDnsHosts = "dns_hosts"
private const val KeyServiceControlEnabled = "service_control_enabled"
private const val KeyServiceControlScheduleEnabled = "service_control_schedule_enabled"
private const val KeyServiceControlScheduleStartCron = "service_control_schedule_start_cron"
private const val KeyServiceControlScheduleStopCron = "service_control_schedule_stop_cron"
private const val KeyServiceControlWifiEnabled = "service_control_wifi_enabled"
private const val KeyServiceControlWifiConnectStartEnabled = "service_control_wifi_connect_start_enabled"
private const val KeyServiceControlWifiConnectStartSsids = "service_control_wifi_connect_start_ssids"
private const val KeyServiceControlWifiConnectStartBssids = "service_control_wifi_connect_start_bssids"
private const val KeyServiceControlWifiConnectStopEnabled = "service_control_wifi_connect_stop_enabled"
private const val KeyServiceControlWifiConnectStopSsids = "service_control_wifi_connect_stop_ssids"
private const val KeyServiceControlWifiConnectStopBssids = "service_control_wifi_connect_stop_bssids"
private const val KeyServiceControlWifiDisconnectStartEnabled = "service_control_wifi_disconnect_start_enabled"
private const val KeyServiceControlWifiDisconnectStartSsids = "service_control_wifi_disconnect_start_ssids"
private const val KeyServiceControlWifiDisconnectStartBssids = "service_control_wifi_disconnect_start_bssids"
private const val KeyServiceControlWifiDisconnectStopEnabled = "service_control_wifi_disconnect_stop_enabled"
private const val KeyServiceControlWifiDisconnectStopSsids = "service_control_wifi_disconnect_stop_ssids"
private const val KeyServiceControlWifiDisconnectStopBssids = "service_control_wifi_disconnect_stop_bssids"
private const val KeyProxyAppListMode = "proxy_app_list_mode"
private const val KeyAutoCheckAppUpdates = "auto_check_app_updates"
private const val KeyAutoInstallAppUpdatesAtNight = "auto_install_app_updates_at_night"
private const val KeyEnableSubscriptionExpiryNotifications = "enable_subscription_expiry_notifications"
private const val KeySubscriptionExpiryReminders = "subscription_expiry_reminders"
private const val KeyDismissedUpdateVersion = "dismissed_update_version"
private const val KeyHasCompletedOnboarding = "has_completed_onboarding"

private val SubscriptionHwidLock = Any()
