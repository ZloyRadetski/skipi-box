// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import app.skipi.ui.components.AppWindowBottomSheet
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import app.skipi.app.settings.DnsSettingsDraft
import app.skipi.app.settings.toSavedSettings
import app.skipi.ui.resources.*
import engine.network.isIpAddress
import engine.xray.isSupportedXrayDnsServer
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.preference.SwitchPreference
import app.skipi.ui.components.StringListEditor
import utils.toTrimmedNonEmptyDistinctList


private const val DnsHostSeparator = ':'


@Composable
fun DnsSettingsBottomSheet(
    show: Boolean,
    enableVpnLocalDns: Boolean,
    enableFakeDns: Boolean,
    enableResolveProxyServerDomain: Boolean,
    onEnableVpnLocalDnsChange: (Boolean) -> Unit,
    proxyDns: List<String>,
    directDns: List<String>,
    directDnsDomains: List<String>,
    enableDirectDnsForProxyServerDomains: Boolean,
    dnsHosts: List<String>,
    onEnableFakeDnsChange: (Boolean) -> Unit,
    onEnableResolveProxyServerDomainChange: (Boolean) -> Unit,
    onProxyDnsChange: (List<String>) -> Unit,
    onDirectDnsChange: (List<String>) -> Unit,
    onDirectDnsDomainsChange: (List<String>) -> Unit,
    onEnableDirectDnsForProxyServerDomainsChange: (Boolean) -> Unit,
    onDnsHostsChange: (List<String>) -> Unit,
    onDismissRequest: () -> Unit,
    onSave: (Boolean, Boolean, Boolean, List<String>, List<String>, List<String>, Boolean, List<String>) -> Unit,
) {
    val proxyDnsEntries = proxyDns.toTrimmedNonEmptyDistinctList()
    val directDnsEntries = directDns.toTrimmedNonEmptyDistinctList()
    val directDnsDomainEntries = directDnsDomains.toTrimmedNonEmptyDistinctList()
    val dnsHostsInvalidMessage = stringResource(Res.string.settings_dns_hosts_invalid)
    val dnsHostEntries = dnsHosts.toTrimmedNonEmptyDistinctList()
    val dnsServerInvalidMessage = stringResource(Res.string.settings_dns_server_invalid)
    val dnsDomainInvalidMessage = stringResource(Res.string.settings_dns_domain_invalid)
    val effectiveLocalDnsEnabled = enableVpnLocalDns
    val effectiveFakeDnsEnabled = effectiveLocalDnsEnabled && enableFakeDns
    AppWindowBottomSheet(
        show = show,
        title = stringResource(Res.string.settings_dns),
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
                    val saved = DnsSettingsDraft(
                        enableVpnLocalDns,
                        effectiveFakeDnsEnabled,
                        enableResolveProxyServerDomain,
                        proxyDnsEntries,
                        directDnsEntries,
                        directDnsDomainEntries,
                        enableDirectDnsForProxyServerDomains,
                        dnsHostEntries,
                    ).toSavedSettings()
                    onSave(saved.enableVpnLocalDns, saved.enableFakeDns, saved.enableResolveProxyServerDomain,
                        saved.proxyDns, saved.directDns, saved.directDnsDomains,
                        saved.enableDirectDnsForProxyServerDomains, saved.dnsHosts)
                },
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        SettingsSheetContent {
            SwitchPreference(
                title = stringResource(Res.string.settings_vpn_local_dns),
                summary = stringResource(Res.string.settings_vpn_local_dns_summary),
                checked = effectiveLocalDnsEnabled,
                onCheckedChange = onEnableVpnLocalDnsChange,
            )
            AnimatedVisibility(
                visible = effectiveLocalDnsEnabled,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SwitchPreference(
                    title = "FakeDNS",
                    summary = stringResource(Res.string.settings_fake_dns_summary),
                    checked = effectiveFakeDnsEnabled,
                    onCheckedChange = onEnableFakeDnsChange,
                )
            }
            SwitchPreference(
                title = stringResource(Res.string.settings_resolve_proxy_server_domain),
                summary = stringResource(Res.string.settings_resolve_proxy_server_domain_summary),
                checked = enableResolveProxyServerDomain,
                onCheckedChange = onEnableResolveProxyServerDomainChange,
            )
            SwitchPreference(
                title = stringResource(Res.string.settings_direct_dns_resolve_proxy_server_domains),
                summary = stringResource(Res.string.settings_direct_dns_resolve_proxy_server_domains_summary),
                checked = enableDirectDnsForProxyServerDomains,
                onCheckedChange = onEnableDirectDnsForProxyServerDomainsChange,
            )
            Spacer(Modifier.height(12.dp))
            StringListEditor(
                editorKey = "direct-dns:$show",
                title = stringResource(Res.string.settings_direct_dns),
                values = directDnsEntries,
                onValuesChange = { onDirectDnsChange(it.toTrimmedNonEmptyDistinctList()) },
                emptyText = stringResource(Res.string.settings_direct_dns_empty),
                description = stringResource(Res.string.settings_dns_server_system_hint),
                validateInput = { dnsServerInputError(it, dnsServerInvalidMessage) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            StringListEditor(
                editorKey = "direct-dns-domains:$show",
                title = stringResource(Res.string.settings_direct_dns_domains),
                values = directDnsDomainEntries,
                onValuesChange = { onDirectDnsDomainsChange(it.toTrimmedNonEmptyDistinctList()) },
                emptyText = stringResource(Res.string.settings_direct_dns_domains_empty),
                validateInput = { dnsDomainInputError(it, dnsDomainInvalidMessage) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            StringListEditor(
                editorKey = "proxy-dns:$show",
                title = stringResource(Res.string.settings_proxy_dns),
                values = proxyDnsEntries,
                onValuesChange = { onProxyDnsChange(it.toTrimmedNonEmptyDistinctList()) },
                emptyText = stringResource(Res.string.settings_proxy_dns_empty),
                description = stringResource(Res.string.settings_dns_server_system_hint),
                validateInput = { dnsServerInputError(it, dnsServerInvalidMessage) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            StringListEditor(
                editorKey = "dns-hosts:$show",
                title = stringResource(Res.string.settings_dns_hosts),
                description = stringResource(Res.string.settings_dns_hosts_format),
                values = dnsHostEntries,
                onValuesChange = { onDnsHostsChange(it.toTrimmedNonEmptyDistinctList()) },
                emptyText = stringResource(Res.string.settings_dns_hosts_empty),
                validateInput = { dnsHostInputError(it, dnsHostsInvalidMessage) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun dnsServerInputError(input: String, invalidMessage: String): String? {
    return if (isSupportedXrayDnsServer(input)) null else invalidMessage
}

private fun dnsDomainInputError(input: String, invalidMessage: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return invalidMessage
    if (trimmed.startsWith("regexp:", ignoreCase = true)) {
        return if (trimmed.substringAfter(":").isBlank()) invalidMessage else null
    }

    val supportedPrefix = trimmed.substringBefore(":", missingDelimiterValue = "")
        .lowercase()
        .takeIf { it in setOf("domain", "full", "keyword", "geosite", "ext") }
    if (supportedPrefix != null) {
        return if (trimmed.substringAfter(":").isBlank()) invalidMessage else null
    }

    return if (trimmed.contains("://") || trimmed.contains("/")) invalidMessage else null
}

private fun dnsHostInputError(input: String, invalidMessage: String): String? {
    val separatorIndex = input.indexOf(DnsHostSeparator)
    if (separatorIndex <= 0 || separatorIndex == input.lastIndex) return invalidMessage

    val domain = input.substring(0, separatorIndex).trim()
    val addresses = input.substring(separatorIndex + 1)
        .split(",")
        .map { it.trim().trim('[', ']') }

    if (!isDnsHostDomain(domain)) return invalidMessage
    if (addresses.isEmpty() || addresses.any { it.isEmpty() || !isIpAddress(it) }) return invalidMessage
    return null
}

private fun isDnsHostDomain(domain: String): Boolean {
    val normalized = domain.removeSuffix(".")
    if (normalized.isEmpty() || normalized.length > 253) return false
    if (normalized.any { it.isWhitespace() || it == '/' || it == DnsHostSeparator }) return false

    return normalized.split(".").all { label ->
        label.isNotEmpty() &&
            label.length <= 63 &&
            label.first() != '-' &&
            label.last() != '-' &&
            label.all { it.isLetterOrDigit() || it == '-' }
    }
}
