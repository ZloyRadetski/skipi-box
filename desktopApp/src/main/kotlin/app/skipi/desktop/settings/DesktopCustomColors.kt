// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.CustomColors

/** The colors of [DesktopAppSettings]. */
fun DesktopAppSettings.customColors(): CustomColors = CustomColors(
    accent = customAccentColor,
    background = customBackgroundColor,
    surface = customSurfaceColor,
    surfaceVariant = customSurfaceVariantColor,
    text = customTextColor,
    textSecondary = customTextSecondaryColor,
    statusRunning = customStatusRunningColor,
    statusStopped = customStatusStoppedColor,
    pingFast = customPingFastColor,
    pingMedium = customPingMediumColor,
    pingSlow = customPingSlowColor,
    categoryIcon = customCategoryIconColor,
    categoryIconGlyph = customCategoryIconGlyphColor,
    protocolVless = customProtocolVlessColor,
    protocolVmess = customProtocolVmessColor,
    protocolHysteria2 = customProtocolHysteria2Color,
    protocolTrojan = customProtocolTrojanColor,
    protocolShadowsocks = customProtocolShadowsocksColor,
    protocolWireguard = customProtocolWireguardColor,
    protocolSocks = customProtocolSocksColor,
    protocolHttp = customProtocolHttpColor,
    protocolStrategy = customProtocolStrategyColor,
    protocolChain = customProtocolChainColor,
    protocolJson = customProtocolJsonColor,
    protocolMasque = customProtocolMasqueColor,
)

fun DesktopAppSettings.withCustomColors(colors: CustomColors): DesktopAppSettings = copy(
    customAccentColor = colors.accent,
    customBackgroundColor = colors.background,
    customSurfaceColor = colors.surface,
    customSurfaceVariantColor = colors.surfaceVariant,
    customTextColor = colors.text,
    customTextSecondaryColor = colors.textSecondary,
    customStatusRunningColor = colors.statusRunning,
    customStatusStoppedColor = colors.statusStopped,
    customPingFastColor = colors.pingFast,
    customPingMediumColor = colors.pingMedium,
    customPingSlowColor = colors.pingSlow,
    customCategoryIconColor = colors.categoryIcon,
    customCategoryIconGlyphColor = colors.categoryIconGlyph,
    customProtocolVlessColor = colors.protocolVless,
    customProtocolVmessColor = colors.protocolVmess,
    customProtocolHysteria2Color = colors.protocolHysteria2,
    customProtocolTrojanColor = colors.protocolTrojan,
    customProtocolShadowsocksColor = colors.protocolShadowsocks,
    customProtocolWireguardColor = colors.protocolWireguard,
    customProtocolSocksColor = colors.protocolSocks,
    customProtocolHttpColor = colors.protocolHttp,
    customProtocolStrategyColor = colors.protocolStrategy,
    customProtocolChainColor = colors.protocolChain,
    customProtocolJsonColor = colors.protocolJson,
    customProtocolMasqueColor = colors.protocolMasque,
)
