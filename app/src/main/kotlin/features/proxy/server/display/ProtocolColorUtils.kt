// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.display

import androidx.compose.ui.graphics.Color
import app.AppState

object ProtocolColorUtils {
    // Default colors for light theme
    val DefaultVlessLight = Color(0xFF3F51B5)       // Indigo
    val DefaultVmessLight = Color(0xFF00897B)       // Teal
    val DefaultHysteria2Light = Color(0xFFE64A19)   // Deep Orange
    val DefaultTrojanLight = Color(0xFFD87A00)      // Amber
    val DefaultShadowsocksLight = Color(0xFF7B1FA2) // Purple
    val DefaultWireguardLight = Color(0xFFC2185B)   // Pink / Crimson
    val DefaultSocksLight = Color(0xFF455A64)       // Blue Grey
    val DefaultHttpLight = Color(0xFF37474F)        // Dark Blue Grey
    val DefaultStrategyLight = Color(0xFF2E7D32)    // Emerald Green
    val DefaultChainLight = Color(0xFF00838F)       // Cyan
    val DefaultJsonLight = Color(0xFF8E24AA)        // Magenta

    // Default colors for dark theme
    val DefaultVlessDark = Color(0xFF7986CB)
    val DefaultVmessDark = Color(0xFF4DB6AC)
    val DefaultHysteria2Dark = Color(0xFFFF7043)
    val DefaultTrojanDark = Color(0xFFFFB74D)
    val DefaultShadowsocksDark = Color(0xFFBA68C8)
    val DefaultWireguardDark = Color(0xFFF06292)
    val DefaultSocksDark = Color(0xFF90A4AE)
    val DefaultHttpDark = Color(0xFFB0BEC5)
    val DefaultStrategyDark = Color(0xFF66BB6A)
    val DefaultChainDark = Color(0xFF4DD0E1)
    val DefaultJsonDark = Color(0xFFCE93D8)

    fun resolveProtocolColor(
        protocol: String,
        appState: AppState,
        isDark: Boolean,
    ): Color {
        val normalized = protocol.trim().lowercase()
        return when {
            normalized.contains("vless") -> {
                appState.customProtocolVlessColor?.let { Color(it) }
                    ?: if (isDark) DefaultVlessDark else DefaultVlessLight
            }
            normalized.contains("vmess") -> {
                appState.customProtocolVmessColor?.let { Color(it) }
                    ?: if (isDark) DefaultVmessDark else DefaultVmessLight
            }
            normalized.contains("hysteria") || normalized.contains("hy2") -> {
                appState.customProtocolHysteria2Color?.let { Color(it) }
                    ?: if (isDark) DefaultHysteria2Dark else DefaultHysteria2Light
            }
            normalized.contains("trojan") -> {
                appState.customProtocolTrojanColor?.let { Color(it) }
                    ?: if (isDark) DefaultTrojanDark else DefaultTrojanLight
            }
            normalized.contains("shadowsocks") || normalized == "ss" -> {
                appState.customProtocolShadowsocksColor?.let { Color(it) }
                    ?: if (isDark) DefaultShadowsocksDark else DefaultShadowsocksLight
            }
            normalized.contains("wireguard") -> {
                appState.customProtocolWireguardColor?.let { Color(it) }
                    ?: if (isDark) DefaultWireguardDark else DefaultWireguardLight
            }
            normalized.contains("socks") -> {
                appState.customProtocolSocksColor?.let { Color(it) }
                    ?: if (isDark) DefaultSocksDark else DefaultSocksLight
            }
            normalized.contains("http") -> {
                appState.customProtocolHttpColor?.let { Color(it) }
                    ?: if (isDark) DefaultHttpDark else DefaultHttpLight
            }
            normalized.contains("strategy") || normalized.contains("balancer") -> {
                appState.customProtocolStrategyColor?.let { Color(it) }
                    ?: if (isDark) DefaultStrategyDark else DefaultStrategyLight
            }
            normalized.contains("chain") -> {
                appState.customProtocolChainColor?.let { Color(it) }
                    ?: if (isDark) DefaultChainDark else DefaultChainLight
            }
            normalized.contains("json") || normalized.contains("custom") -> {
                appState.customProtocolJsonColor?.let { Color(it) }
                    ?: if (isDark) DefaultJsonDark else DefaultJsonLight
            }
            else -> {
                if (isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A)
            }
        }
    }
}
