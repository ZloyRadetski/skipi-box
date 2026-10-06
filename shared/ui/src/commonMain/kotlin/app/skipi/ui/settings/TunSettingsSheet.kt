// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.runtime.Composable
import app.skipi.ui.components.AppWindowBottomSheet
import androidx.compose.runtime.key
import app.skipi.app.settings.isValidTunMtu
import app.skipi.app.settings.isValidTunVpnDns
import app.skipi.app.settings.isValidTunIpv4Cidr
import app.skipi.app.settings.isValidTunIpv6Cidr
import app.skipi.app.settings.isValidTunTcpKeepAliveInterval
import app.skipi.app.settings.isValidTunTcpUserTimeout
import org.jetbrains.compose.resources.stringResource
import app.skipi.ui.resources.*
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import app.skipi.ui.text.formatTemplate


@Composable
fun tunSettingsSummary(
    mtu: String,
    vpnDns: String,
    ipv4Cidr: String,
    ipv6Cidr: String,
    showVpnDns: Boolean,
): String {
    val template = stringResource(
        if (showVpnDns) Res.string.settings_tun_summary else Res.string.settings_tun_summary_without_dns,
    )
    return template.formatTemplate(
        "mtu" to mtu,
        "vpnDns" to vpnDns,
        "ipv4" to ipv4Cidr,
        "ipv6" to ipv6Cidr,
    )
}

@Composable
fun TunSettingsBottomSheet(
    show: Boolean,
    mtu: String,
    vpnDns: String,
    ipv4Cidr: String,
    ipv6Cidr: String,
    tcpKeepAliveInterval: String,
    tcpUserTimeout: String,
    showVpnDns: Boolean,
    onMtuChange: (String) -> Unit,
    onVpnDnsChange: (String) -> Unit,
    onIpv4CidrChange: (String) -> Unit,
    onIpv6CidrChange: (String) -> Unit,
    onTcpKeepAliveIntervalChange: (String) -> Unit,
    onTcpUserTimeoutChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    val mtuError = if (isValidTunMtu(mtu)) null else stringResource(Res.string.settings_tun_mtu_invalid)
    val vpnDnsError = if (!showVpnDns || isValidTunVpnDns(vpnDns)) {
        null
    } else {
        stringResource(Res.string.settings_tun_dns_invalid)
    }
    val ipv4CidrError = if (isValidTunIpv4Cidr(ipv4Cidr)) {
        null
    } else {
        stringResource(Res.string.settings_tun_ipv4_cidr_invalid)
    }
    val ipv6CidrError = if (isValidTunIpv6Cidr(ipv6Cidr)) {
        null
    } else {
        stringResource(Res.string.settings_tun_ipv6_cidr_invalid)
    }
    val tcpKeepAliveError = if (isValidTunTcpKeepAliveInterval(tcpKeepAliveInterval)) {
        null
    } else {
        stringResource(Res.string.settings_tun_tcp_keep_alive_interval_invalid)
    }
    val tcpUserTimeoutError = if (isValidTunTcpUserTimeout(tcpUserTimeout)) {
        null
    } else {
        stringResource(Res.string.settings_tun_tcp_user_timeout_invalid)
    }
    val canSave = listOf(
        mtuError,
        vpnDnsError,
        ipv4CidrError,
        ipv6CidrError,
        tcpKeepAliveError,
        tcpUserTimeoutError,
    ).all { it == null }

    AppWindowBottomSheet(
        show = show,
        title = stringResource(Res.string.tun),
        startAction = {
            TextButton(
                text = stringResource(Res.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(Res.string.common_save),
                onClick = {
                    if (canSave) {
                        onSave(
                            mtu.trim(),
                            vpnDns.trim(),
                            ipv4Cidr.trim(),
                            ipv6Cidr.trim(),
                            tcpKeepAliveInterval.trim(),
                            tcpUserTimeout.trim(),
                        )
                    }
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        key(show) {
            SettingsSheetContent {
                SettingsTextField(
                    value = mtu,
                    onValueChange = onMtuChange,
                    label = stringResource(Res.string.settings_tun_mtu),
                    errorText = mtuError,
                    keyboardOptions = fiveDigitKeyboardOptions(),
                    sanitizeInput = ::sanitizeFiveDigitInput,
                )
                if (showVpnDns) {
                    SettingsTextField(
                        value = vpnDns,
                        onValueChange = onVpnDnsChange,
                        label = stringResource(Res.string.settings_tun_vpn_dns),
                        errorText = vpnDnsError,
                    )
                }
                SettingsTextField(
                    value = ipv4Cidr,
                    onValueChange = onIpv4CidrChange,
                    label = stringResource(Res.string.settings_tun_ipv4_cidr),
                    errorText = ipv4CidrError,
                )
                SettingsTextField(
                    value = ipv6Cidr,
                    onValueChange = onIpv6CidrChange,
                    label = stringResource(Res.string.settings_tun_ipv6_cidr),
                    errorText = ipv6CidrError,
                )
                SettingsTextField(
                    value = tcpKeepAliveInterval,
                    onValueChange = onTcpKeepAliveIntervalChange,
                    label = stringResource(Res.string.settings_tun_tcp_keep_alive_interval),
                    errorText = tcpKeepAliveError,
                    keyboardOptions = fiveDigitKeyboardOptions(),
                    sanitizeInput = ::sanitizeFiveDigitInput,
                )
                SettingsTextField(
                    value = tcpUserTimeout,
                    onValueChange = onTcpUserTimeoutChange,
                    label = stringResource(Res.string.settings_tun_tcp_user_timeout),
                    errorText = tcpUserTimeoutError,
                    keyboardOptions = fiveDigitKeyboardOptions(),
                    sanitizeInput = ::sanitizeFiveDigitInput,
                )
            }
        }
    }
}
