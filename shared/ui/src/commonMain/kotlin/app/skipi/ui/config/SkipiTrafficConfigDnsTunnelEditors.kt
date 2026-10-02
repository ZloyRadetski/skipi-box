// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowDialog
import app.skipi.ui.components.AppWindowDropdownPreference
import app.skipi.ui.components.StringListEditor
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_add
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_delete
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.color_picker_quick_presets
import app.skipi.ui.resources.configs_dns_add_server
import app.skipi.ui.resources.configs_dns_direct_domains_empty
import app.skipi.ui.resources.configs_dns_direct_domains_title
import app.skipi.ui.resources.configs_dns_direct_domains_summary
import app.skipi.ui.resources.configs_dns_direct_fallback_proxy
import app.skipi.ui.resources.configs_dns_direct_section
import app.skipi.ui.resources.configs_dns_custom
import app.skipi.ui.resources.configs_dns_empty_servers
import app.skipi.ui.resources.configs_dns_hosts_empty
import app.skipi.ui.resources.configs_dns_hosts_invalid
import app.skipi.ui.resources.configs_dns_hosts_summary
import app.skipi.ui.resources.configs_dns_hosts_title
import app.skipi.ui.resources.configs_dns_master_switches
import app.skipi.ui.resources.configs_dns_proxy_section
import app.skipi.ui.resources.configs_dns_server_address
import app.skipi.ui.resources.configs_dns_server_invalid
import app.skipi.ui.resources.configs_dns_servers
import app.skipi.ui.resources.configs_dns_tun_dns
import app.skipi.ui.resources.configs_dns_tun_dns_summary
import app.skipi.ui.resources.configs_dns_edit_server
import app.skipi.ui.resources.configs_dns_domain_invalid
import app.skipi.ui.resources.configs_fake_dns
import app.skipi.ui.resources.configs_fake_dns_summary
import app.skipi.ui.resources.configs_local_dns
import app.skipi.ui.resources.configs_local_dns_summary
import app.skipi.ui.resources.configs_sniffing
import app.skipi.ui.resources.configs_sniffing_route_only
import app.skipi.ui.resources.configs_sniffing_route_only_summary
import app.skipi.ui.resources.configs_sniffing_summary
import app.skipi.ui.resources.configs_mux
import app.skipi.ui.resources.configs_mux_summary
import app.skipi.ui.resources.configs_mux_concurrency
import app.skipi.ui.resources.configs_mux_concurrency_summary
import app.skipi.ui.resources.configs_fragment
import app.skipi.ui.resources.configs_fragment_summary
import app.skipi.ui.resources.settings_resolve_proxy_server_domain
import app.skipi.ui.resources.settings_resolve_proxy_server_domain_summary
import engine.network.isIpv4Address
import engine.xray.isSupportedXrayDnsServer
import features.config.TrafficConfigAndroidSettings
import features.config.isValidTrafficConfigDnsDomainRule
import features.config.isValidTrafficConfigDnsHost
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val ProxyDnsPresets = listOf(
    "https://1.1.1.1/dns-query,https://1.0.0.1/dns-query" to "Cloudflare DoH",
    "https://8.8.8.8/dns-query,https://8.8.4.4/dns-query" to "Google DoH",
    "https://dns.adguard-dns.com/dns-query" to "AdGuard DoH",
    "https://dns.quad9.net/dns-query" to "Quad9 DoH",
    "https://dns.nullsproxy.com/dns-query" to "Nulls Proxy DoH",
    "tcp://8.8.8.8:53,tcp://8.8.4.4:53" to "Google TCP",
    "8.8.8.8,8.8.4.4" to "Google DoU",
    "1.1.1.1,1.0.0.1" to "Cloudflare DoU",
    "localhost" to "System DNS (Private DNS)",
)
private val DirectDnsPresets = listOf(
    "https://77.88.8.8/dns-query" to "Yandex DoH",
    "https://1.1.1.1/dns-query" to "Cloudflare DoH",
    "https://8.8.8.8/dns-query" to "Google DoH",
    "77.88.8.8,77.88.8.1" to "Yandex DoU",
    "1.1.1.1,8.8.8.8" to "Cloudflare + Google DoU",
    "localhost" to "System DNS (Private DNS)",
)
private val ProxyDnsSuggestions = listOf(
    "DoH (1.1.1.1)" to "https://1.1.1.1/dns-query",
    "DoH (8.8.8.8)" to "https://8.8.8.8/dns-query",
    "DoH (Nulls Proxy)" to "https://dns.nullsproxy.com/dns-query",
    "TCP (8.8.8.8)" to "tcp://8.8.8.8:53",
    "DoU (1.1.1.1)" to "1.1.1.1",
    "DoU (8.8.8.8)" to "8.8.8.8",
    "System DNS (Private DNS)" to "localhost",
)
private val DirectDnsSuggestions = listOf(
    "DoH (Yandex)" to "https://77.88.8.8/dns-query",
    "DoU (Yandex)" to "77.88.8.8",
    "DoH (Cloudflare)" to "https://1.1.1.1/dns-query",
    "DoU (Google)" to "8.8.8.8",
    "System DNS (Private DNS)" to "localhost",
)

/** Shared DNS editor; the host owns profile state and persistence. */
@Composable
fun SkipiTrafficConfigDnsEditor(
    settings: TrafficConfigAndroidSettings,
    onSettingsChange: (TrafficConfigAndroidSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tunDnsDialog by remember { mutableStateOf(false) }
    var addProxy by remember { mutableStateOf(false) }
    var editProxy by remember { mutableStateOf(-1) }
    var addDirect by remember { mutableStateOf(false) }
    var editDirect by remember { mutableStateOf(-1) }
    var domainsDialog by remember { mutableStateOf(false) }
    var hostsDialog by remember { mutableStateOf(false) }
    var proxyPresetIndex by remember(settings.proxyDns) {
        mutableStateOf(ProxyDnsPresets.indexOfFirst { it.first.split(',') == settings.proxyDns }.let { if (it < 0) ProxyDnsPresets.size else it })
    }
    var directPresetIndex by remember(settings.directDns) {
        mutableStateOf(DirectDnsPresets.indexOfFirst { it.first.split(',') == settings.directDns }.let { if (it < 0) DirectDnsPresets.size else it })
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
            Text(stringResource(Res.string.configs_dns_master_switches), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MiuixTheme.textStyles.title3)
            SwitchPreference(title = stringResource(Res.string.configs_local_dns), summary = stringResource(Res.string.configs_local_dns_summary), checked = settings.enableVpnLocalDns, onCheckedChange = { onSettingsChange(settings.copy(enableVpnLocalDns = it)) })
            SwitchPreference(title = stringResource(Res.string.configs_fake_dns), summary = stringResource(Res.string.configs_fake_dns_summary), checked = settings.enableFakeDns, enabled = settings.enableVpnLocalDns, onCheckedChange = { onSettingsChange(settings.copy(enableFakeDns = it)) })
            SwitchPreference(title = stringResource(Res.string.settings_resolve_proxy_server_domain), summary = stringResource(Res.string.settings_resolve_proxy_server_domain_summary), checked = settings.enableResolveProxyServerDomain, onCheckedChange = { onSettingsChange(settings.copy(enableResolveProxyServerDomain = it)) })
            SwitchPreference(title = stringResource(Res.string.configs_dns_direct_fallback_proxy), checked = settings.enableDirectDnsForProxyServerDomains, onCheckedChange = { onSettingsChange(settings.copy(enableDirectDnsForProxyServerDomains = it)) })
            ArrowPreference(stringResource(Res.string.configs_dns_tun_dns), summary = settings.tunVpnDns, onClick = { tunDnsDialog = true })
        }
        DnsServerListCard(
            title = stringResource(Res.string.configs_dns_proxy_section), settings.proxyDns, ProxyDnsPresets,
            ProxyDnsSuggestions, proxyPresetIndex, { proxyPresetIndex = it; ProxyDnsPresets.getOrNull(it)?.let { preset -> onSettingsChange(settings.copy(proxyDns = preset.first.split(','))) } },
            onAdd = { addProxy = true }, onQuickAdd = { value -> if (value !in settings.proxyDns) onSettingsChange(settings.copy(proxyDns = settings.proxyDns + value)) }, onEdit = { editProxy = it },
            onDelete = { index -> onSettingsChange(settings.copy(proxyDns = settings.proxyDns.filterIndexed { i, _ -> i != index })) },
        )
        DnsServerListCard(
            title = stringResource(Res.string.configs_dns_direct_section), settings.directDns, DirectDnsPresets,
            DirectDnsSuggestions, directPresetIndex, { directPresetIndex = it; DirectDnsPresets.getOrNull(it)?.let { preset -> onSettingsChange(settings.copy(directDns = preset.first.split(','))) } },
            onAdd = { addDirect = true }, onQuickAdd = { value -> if (value !in settings.directDns) onSettingsChange(settings.copy(directDns = settings.directDns + value)) }, onEdit = { editDirect = it },
            onDelete = { index -> onSettingsChange(settings.copy(directDns = settings.directDns.filterIndexed { i, _ -> i != index })) },
        )
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
            ArrowPreference(stringResource(Res.string.configs_dns_direct_domains_title), summary = settings.directDnsDomains.takeIf(List<String>::isNotEmpty)?.joinToString().orEmpty().ifBlank { stringResource(Res.string.configs_dns_direct_domains_empty) }, onClick = { domainsDialog = true })
            ArrowPreference(stringResource(Res.string.configs_dns_hosts_title), summary = settings.dnsHosts.takeIf(List<String>::isNotEmpty)?.joinToString().orEmpty().ifBlank { stringResource(Res.string.configs_dns_hosts_empty) }, onClick = { hostsDialog = true })
        }
    }

    TunDnsEditorDialog(tunDnsDialog, settings.tunVpnDns, { tunDnsDialog = false }) { onSettingsChange(settings.copy(tunVpnDns = it)) }
    DnsServerEditorDialog(addProxy, stringResource(Res.string.configs_dns_add_server), "", ProxyDnsSuggestions, { addProxy = false }) { value -> if (value !in settings.proxyDns) onSettingsChange(settings.copy(proxyDns = settings.proxyDns + value)) }
    DnsServerEditorDialog(editProxy in settings.proxyDns.indices, stringResource(Res.string.configs_dns_edit_server), settings.proxyDns.getOrElse(editProxy) { "" }, ProxyDnsSuggestions, { editProxy = -1 }) { value -> onSettingsChange(settings.copy(proxyDns = settings.proxyDns.toMutableList().also { it[editProxy] = value })) }
    DnsServerEditorDialog(addDirect, stringResource(Res.string.configs_dns_add_server), "", DirectDnsSuggestions, { addDirect = false }) { value -> if (value !in settings.directDns) onSettingsChange(settings.copy(directDns = settings.directDns + value)) }
    DnsServerEditorDialog(editDirect in settings.directDns.indices, stringResource(Res.string.configs_dns_edit_server), settings.directDns.getOrElse(editDirect) { "" }, DirectDnsSuggestions, { editDirect = -1 }) { value -> onSettingsChange(settings.copy(directDns = settings.directDns.toMutableList().also { it[editDirect] = value })) }
    DnsStringListDialog(domainsDialog, stringResource(Res.string.configs_dns_direct_domains_title), settings.directDnsDomains, stringResource(Res.string.configs_dns_direct_domains_empty), stringResource(Res.string.configs_dns_domain_invalid), ::isValidTrafficConfigDnsDomainRule, { domainsDialog = false }) { onSettingsChange(settings.copy(directDnsDomains = it)) }
    DnsStringListDialog(hostsDialog, stringResource(Res.string.configs_dns_hosts_title), settings.dnsHosts, stringResource(Res.string.configs_dns_hosts_empty), stringResource(Res.string.configs_dns_hosts_invalid), ::isValidTrafficConfigDnsHost, { hostsDialog = false }) { onSettingsChange(settings.copy(dnsHosts = it)) }
}

/** Shared controls for portable sniffing and Xray connection options. */
@Composable
fun SkipiTrafficConfigTunnelEditor(
    settings: TrafficConfigAndroidSettings,
    muxConcurrency: String,
    onSettingsChange: (TrafficConfigAndroidSettings) -> Unit,
    onMuxConcurrencyChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
            SwitchPreference(title = stringResource(Res.string.configs_sniffing), summary = stringResource(Res.string.configs_sniffing_summary), checked = settings.enableSniffing, onCheckedChange = { onSettingsChange(settings.copy(enableSniffing = it)) })
            SwitchPreference(title = stringResource(Res.string.configs_sniffing_route_only), summary = stringResource(Res.string.configs_sniffing_route_only_summary), checked = settings.enableSniffingRouteOnly, enabled = settings.enableSniffing, onCheckedChange = { onSettingsChange(settings.copy(enableSniffingRouteOnly = it)) })
            SwitchPreference(title = stringResource(Res.string.configs_mux), summary = stringResource(Res.string.configs_mux_summary), checked = settings.enableMux, onCheckedChange = { onSettingsChange(settings.copy(enableMux = it)) })
            SwitchPreference(title = stringResource(Res.string.configs_fragment), summary = stringResource(Res.string.configs_fragment_summary), checked = settings.enableFragment, onCheckedChange = { onSettingsChange(settings.copy(enableFragment = it)) })
        }
        if (settings.enableMux) {
            TextField(state = rememberTextFieldState(muxConcurrency), label = stringResource(Res.string.configs_mux_concurrency), lineLimits = TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth(), inputTransformation = { onMuxConcurrencyChange(asCharSequence().toString()) })
            Text(stringResource(Res.string.configs_mux_concurrency_summary), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun DnsServerListCard(
    title: String,
    servers: List<String>,
    presets: List<Pair<String, String>>,
    suggestions: List<Pair<String, String>>,
    selectedPreset: Int,
    onPreset: (Int) -> Unit,
    onAdd: () -> Unit,
    onQuickAdd: (String) -> Unit,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
        Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), style = MiuixTheme.textStyles.title3)
        val labels = presets.map(Pair<String, String>::second) + stringResource(Res.string.configs_dns_custom)
        AppWindowDropdownPreference(items = labels, selectedIndex = selectedPreset.coerceIn(labels.indices), title = stringResource(Res.string.configs_dns_servers), onSelectedIndexChange = onPreset)
        DnsQuickChips(suggestions) { value -> if (value !in servers) onQuickAdd(value) }
        if (servers.isEmpty()) Text(stringResource(Res.string.configs_dns_empty_servers), modifier = Modifier.padding(16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        servers.forEachIndexed { index, value ->
            Row(Modifier.fillMaxWidth().clickable { onEdit(index) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dnsProtocol(value), fontSize = 11.sp, color = MiuixTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(value, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.body1)
                IconButton(onClick = { onEdit(index) }, modifier = Modifier.size(32.dp)) { Icon(MiuixIcons.Edit, stringResource(Res.string.configs_dns_edit_server), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { onDelete(index) }, modifier = Modifier.size(32.dp)) { Icon(MiuixIcons.Delete, stringResource(Res.string.common_delete), modifier = Modifier.size(18.dp)) }
            }
        }
        Row(Modifier.fillMaxWidth().clickable(onClick = onAdd).padding(14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(MiuixIcons.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(Res.string.configs_dns_add_server), color = MiuixTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DnsQuickChips(items: List<Pair<String, String>>, onClick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            Row(Modifier.clip(RoundedCornerShape(16.dp)).background(MiuixTheme.colorScheme.surfaceVariant).clickable { onClick(value) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(MiuixIcons.Add, null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(4.dp)); Text(label, style = MiuixTheme.textStyles.body2, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private fun dnsProtocol(value: String): String = when {
    value.equals("localhost", true) -> "System"
    value.startsWith("https://", true) || value.startsWith("h2c", true) -> "DoH"
    value.startsWith("quic+local://", true) -> "DoQ"
    value.startsWith("tcp", true) -> "TCP"
    else -> "DoU"
}

@Composable
private fun DnsServerEditorDialog(show: Boolean, title: String, initial: String, suggestions: List<Pair<String, String>>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    if (!show) return
    val state = remember(show, initial) { TextFieldState(initial) }
    val value = state.text.toString().trim()
    val error = stringResource(Res.string.configs_dns_server_invalid)
    AppWindowDialog(true, title = title, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            TextField(state, label = stringResource(Res.string.configs_dns_server_address), lineLimits = TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth())
            if (value.isNotEmpty() && !isSupportedXrayDnsServer(value)) Text(error, color = MiuixTheme.colorScheme.error)
            Text(stringResource(Res.string.color_picker_quick_presets), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp))
            DnsQuickChips(suggestions, state::setTextAndPlaceCursorAtEnd)
            DialogActions(stringResource(Res.string.common_cancel), stringResource(Res.string.common_save), onDismiss) { if (value.isNotEmpty() && isSupportedXrayDnsServer(value)) onConfirm(value) }
        }
    }
}

@Composable
private fun TunDnsEditorDialog(show: Boolean, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    if (!show) return
    var value by remember(show, initial) { mutableStateOf(initial) }
    val valid = isIpv4Address(value.trim())
    AppWindowDialog(true, title = stringResource(Res.string.configs_dns_tun_dns), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            TextField(state = rememberTextFieldState(value), label = stringResource(Res.string.configs_dns_tun_dns), lineLimits = TextFieldLineLimits.SingleLine, modifier = Modifier.fillMaxWidth(), inputTransformation = { value = asCharSequence().toString() })
            Text(stringResource(Res.string.configs_dns_tun_dns_summary), style = MiuixTheme.textStyles.body2, modifier = Modifier.padding(vertical = 8.dp))
            DialogActions(stringResource(Res.string.common_cancel), stringResource(Res.string.common_save), onDismiss) { if (valid) onConfirm(value.trim()) }
        }
    }
}

@Composable
private fun DnsStringListDialog(show: Boolean, title: String, values: List<String>, emptyText: String, invalidText: String, validate: (String) -> Boolean, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    if (!show) return
    var list by remember(show, values) { mutableStateOf(values) }
    AppWindowDialog(true, title = title, onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            StringListEditor(editorKey = title, title = title, description = title, values = list, onValuesChange = { list = it }, emptyText = emptyText, validateInput = { if (validate(it)) null else invalidText }, suggestionContent = if (title == stringResource(Res.string.configs_dns_direct_domains_title)) ({ apply -> Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("geosite:cn", "geosite:category-gov-ru", "domain:ru").forEach { TextButton(it, onClick = { apply(it, true) }) } } }) else null)
            DialogActions(stringResource(Res.string.common_cancel), stringResource(Res.string.common_save), onDismiss) { onSave(list) }
        }
    }
}

@Composable
private fun DialogActions(cancel: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(cancel, onClick = onCancel); Spacer(Modifier.width(8.dp)); TextButton(confirm, onClick = { onConfirm(); onCancel() })
    }
}
