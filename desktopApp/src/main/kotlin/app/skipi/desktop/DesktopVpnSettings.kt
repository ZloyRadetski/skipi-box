// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.settings_launch_on_login_summary
import app.skipi.ui.resources.settings_launch_on_login_title
import app.skipi.ui.resources.settings_tun_mode_summary
import app.skipi.ui.resources.settings_tun_mode_title
import app.skipi.ui.resources.settings_tun_mode_unavailable
import app.skipi.ui.settings.SkipiHostSwitchSection
import app.skipi.ui.settings.SkipiVpnSettingsScreen
import app.skipi.ui.settings.TunSettingsBottomSheet
import app.skipi.ui.settings.VpnSettingsState
import app.skipi.ui.settings.rememberVpnSettingsLabels
import app.skipi.ui.settings.tunSettingsSummary
import org.jetbrains.compose.resources.stringResource

private data class TunDraft(
    val mtu: String,
    val vpnDns: String,
    val ipv4Cidr: String,
    val ipv6Cidr: String,
    val tcpKeepAlive: String,
    val tcpUserTimeout: String,
)

private fun DesktopAppSettings.toTunDraft() =
    TunDraft(tunMtu, tunVpnDns, tunIpv4Cidr, tunIpv6Cidr, tunTcpKeepAliveInterval, tunTcpUserTimeout)

/**
 * Desktop host of the shared "VPN service and TUN" page. [tunUnavailableReason] is null when this
 * machine can run TUN mode; otherwise the mode switch stays off and says why.
 */
@Composable
internal fun DesktopVpnSettings(
    settings: DesktopAppSettings,
    tunUnavailableReason: String?,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    onApplyLaunchOnLogin: (Boolean) -> Result<Unit>,
    onOpenNetworkAutomation: () -> Unit,
    contentPadding: PaddingValues,
) {
    var showTunSheet by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(settings.toTunDraft()) }
    val saved = "Параметры VPN сохранены."
    val tunAvailable = tunUnavailableReason == null
    val labels = rememberVpnSettingsLabels().let { shared ->
        shared.copy(
            autoConnectOnBootTitle = stringResource(Res.string.settings_launch_on_login_title),
            autoConnectOnBootSummary = stringResource(Res.string.settings_launch_on_login_summary),
        )
    }
    val tunModeSummary = if (tunAvailable) {
        stringResource(Res.string.settings_tun_mode_summary)
    } else {
        stringResource(Res.string.settings_tun_mode_unavailable, tunUnavailableReason.orEmpty())
    }
    val state = VpnSettingsState(
        tunSummary = tunSettingsSummary(
            mtu = settings.tunMtu,
            vpnDns = settings.tunVpnDns,
            ipv4Cidr = settings.tunIpv4Cidr,
            ipv6Cidr = settings.tunIpv6Cidr,
            showVpnDns = false,
        ),
        hevTunEnabled = true,
        appendHttpProxyEnabled = false,
        strictFullTunnelEnabled = settings.enableStrictFullTunnel,
        autoConnectOnAppOpen = settings.autoConnectOnAppOpen,
        autoConnectOnBoot = settings.launchOnLogin,
        killSwitchEnabled = false,
        batteryOptimizationUnrestricted = true,
        wakeLockEnabled = false,
        seamlessNetworkSwitchingEnabled = false,
    )

    SkipiVpnSettingsScreen(
        state = state,
        labels = labels,
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onOpenTunSettings = {
            draft = settings.toTunDraft()
            showTunSheet = true
        },
        onHevTunChange = {},
        onAppendHttpProxyChange = {},
        onStrictFullTunnelChange = { enabled -> onPersist(settings.copy(enableStrictFullTunnel = enabled), saved) },
        onAutoConnectOnOpenChange = { enabled -> onPersist(settings.copy(autoConnectOnAppOpen = enabled), saved) },
        onAutoConnectOnBootChange = { enabled ->
            onApplyLaunchOnLogin(enabled).fold(
                onSuccess = { onPersist(settings.copy(launchOnLogin = enabled), saved) },
                onFailure = { error ->
                    onPersist(settings, "Не удалось изменить автозапуск: ${error.message.orEmpty()}")
                },
            )
        },
        onKillSwitchChange = {},
        onBatteryOptimizationChange = {},
        onWakeLockChange = {},
        onSeamlessSwitchingChange = {},
        onOpenSystemVpnSettings = {},
        onOpenNetworkAutomation = onOpenNetworkAutomation,
        capabilities = desktopVpnSettingsCapabilities(),
        hostContent = {
            val tunModeTitle = stringResource(Res.string.settings_tun_mode_title)
            SkipiHostSwitchSection(
                sectionTitle = tunModeTitle,
                title = tunModeTitle,
                summary = tunModeSummary,
                checked = settings.enableTunMode && tunAvailable,
                enabled = tunAvailable || settings.enableTunMode,
                onCheckedChange = { enabled -> onPersist(settings.copy(enableTunMode = enabled), saved) },
            )
        },
    )
    TunSettingsBottomSheet(
        show = showTunSheet,
        mtu = draft.mtu,
        vpnDns = draft.vpnDns,
        ipv4Cidr = draft.ipv4Cidr,
        ipv6Cidr = draft.ipv6Cidr,
        tcpKeepAliveInterval = draft.tcpKeepAlive,
        tcpUserTimeout = draft.tcpUserTimeout,
        showVpnDns = false,
        onMtuChange = { draft = draft.copy(mtu = it) },
        onVpnDnsChange = { draft = draft.copy(vpnDns = it) },
        onIpv4CidrChange = { draft = draft.copy(ipv4Cidr = it) },
        onIpv6CidrChange = { draft = draft.copy(ipv6Cidr = it) },
        onTcpKeepAliveIntervalChange = { draft = draft.copy(tcpKeepAlive = it) },
        onTcpUserTimeoutChange = { draft = draft.copy(tcpUserTimeout = it) },
        onDismissRequest = { showTunSheet = false },
        onSave = { mtu, vpnDns, ipv4Cidr, ipv6Cidr, tcpKeepAlive, tcpUserTimeout ->
            onPersist(
                settings.copy(
                    tunMtu = mtu,
                    tunVpnDns = vpnDns,
                    tunIpv4Cidr = ipv4Cidr,
                    tunIpv6Cidr = ipv6Cidr,
                    tunTcpKeepAliveInterval = tcpKeepAlive,
                    tunTcpUserTimeout = tcpUserTimeout,
                ),
                saved,
            )
            showTunSheet = false
        },
    )
}
