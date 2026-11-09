// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.runtime.Composable
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.proxy_server_copy_full_json
import app.skipi.ui.resources.proxy_server_copy_qr_code
import app.skipi.ui.resources.proxy_server_copy_url
import app.skipi.ui.resources.proxy_server_list_add_amnezia_wg
import app.skipi.ui.resources.proxy_server_list_add_chain_proxy
import app.skipi.ui.resources.proxy_server_list_add_custom
import app.skipi.ui.resources.proxy_server_list_add_hysteria2
import app.skipi.ui.resources.proxy_server_list_add_http
import app.skipi.ui.resources.proxy_server_list_add_shadowsocks
import app.skipi.ui.resources.proxy_server_list_add_socks
import app.skipi.ui.resources.proxy_server_list_add_strategy_group
import app.skipi.ui.resources.proxy_server_list_add_trojan
import app.skipi.ui.resources.proxy_server_list_add_vless
import app.skipi.ui.resources.proxy_server_list_add_vmess
import app.skipi.ui.resources.proxy_server_list_add_wireguard
import app.skipi.ui.resources.proxy_server_list_delete_all
import app.skipi.ui.resources.proxy_server_list_delete_duplicates
import app.skipi.ui.resources.proxy_server_list_delete_invalid
import app.skipi.ui.resources.proxy_server_list_import_clipboard
import app.skipi.ui.resources.proxy_server_list_import_file
import app.skipi.ui.resources.proxy_server_list_manual_input
import app.skipi.ui.resources.proxy_server_list_option_sort_default
import app.skipi.ui.resources.proxy_server_list_option_sort_latency
import app.skipi.ui.resources.proxy_server_list_option_sort_name
import app.skipi.ui.resources.proxy_server_list_restart_service
import app.skipi.ui.resources.proxy_server_list_scan_qr_code
import app.skipi.ui.resources.proxy_server_list_update_subscriptions
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun serverKindTitle(kind: ProxyHomeServerKind): String = stringResource(
    when (kind) {
        ProxyHomeServerKind.Http -> Res.string.proxy_server_list_add_http
        ProxyHomeServerKind.Vmess -> Res.string.proxy_server_list_add_vmess
        ProxyHomeServerKind.Vless -> Res.string.proxy_server_list_add_vless
        ProxyHomeServerKind.Trojan -> Res.string.proxy_server_list_add_trojan
        ProxyHomeServerKind.Shadowsocks -> Res.string.proxy_server_list_add_shadowsocks
        ProxyHomeServerKind.Socks -> Res.string.proxy_server_list_add_socks
        ProxyHomeServerKind.Hysteria2 -> Res.string.proxy_server_list_add_hysteria2
        ProxyHomeServerKind.Wireguard -> Res.string.proxy_server_list_add_wireguard
        ProxyHomeServerKind.AmneziaWg -> Res.string.proxy_server_list_add_amnezia_wg
        ProxyHomeServerKind.StrategyGroup -> Res.string.proxy_server_list_add_strategy_group
        ProxyHomeServerKind.ChainProxy -> Res.string.proxy_server_list_add_chain_proxy
        ProxyHomeServerKind.Custom -> Res.string.proxy_server_list_add_custom
    },
)

@Composable
internal fun importSourceTitle(source: ProxyHomeImportSource): String = stringResource(
    when (source) {
        ProxyHomeImportSource.ManualInput -> Res.string.proxy_server_list_manual_input
        ProxyHomeImportSource.QrCode -> Res.string.proxy_server_list_scan_qr_code
        ProxyHomeImportSource.Clipboard -> Res.string.proxy_server_list_import_clipboard
        ProxyHomeImportSource.File -> Res.string.proxy_server_list_import_file
    },
)

@Composable
internal fun copyFormatTitle(format: ProxyHomeCopyFormat): String = stringResource(
    when (format) {
        ProxyHomeCopyFormat.Url -> Res.string.proxy_server_copy_url
        ProxyHomeCopyFormat.FullJson -> Res.string.proxy_server_copy_full_json
        ProxyHomeCopyFormat.QrCode -> Res.string.proxy_server_copy_qr_code
    },
)

@Composable
internal fun sortModeTitle(mode: ProxyHomeSortMode): String = stringResource(
    when (mode) {
        ProxyHomeSortMode.Default -> Res.string.proxy_server_list_option_sort_default
        ProxyHomeSortMode.Name -> Res.string.proxy_server_list_option_sort_name
        ProxyHomeSortMode.Latency -> Res.string.proxy_server_list_option_sort_latency
    },
)

@Composable
internal fun serverToolTitle(tool: ProxyHomeServerTool): String = stringResource(
    when (tool) {
        ProxyHomeServerTool.RestartService -> Res.string.proxy_server_list_restart_service
        ProxyHomeServerTool.UpdateSubscriptions -> Res.string.proxy_server_list_update_subscriptions
        ProxyHomeServerTool.DeleteDuplicateServers -> Res.string.proxy_server_list_delete_duplicates
        ProxyHomeServerTool.DeleteInvalidServers -> Res.string.proxy_server_list_delete_invalid
        ProxyHomeServerTool.DeleteAllServers -> Res.string.proxy_server_list_delete_all
    },
)

/**
 * Entries of the "+" menu, in display order: subscription, clipboard, QR code, then the server
 * submenu. Files and manual input are not offered here, and there is no separate group entry
 * (it opened the same dialog as adding a subscription).
 */

internal fun formatSummaryBytes(value: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var amount = value.coerceAtLeast(0).toDouble()
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024
        unit++
    }
    return if (unit == 0) "${amount.toLong()} ${units[unit]}" else "%.2f %s".format(amount, units[unit])
}
