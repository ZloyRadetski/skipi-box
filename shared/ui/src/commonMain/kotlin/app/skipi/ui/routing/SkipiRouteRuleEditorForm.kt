// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.SkipiDropdownPreference
import app.skipi.ui.components.StringListStatusText
import app.skipi.ui.settings.SettingsTextField
import app.skipi.ui.settings.SheetTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.common_save
import app.skipi.ui.resources.routing_domain_invalid
import app.skipi.ui.resources.routing_domain_label
import app.skipi.ui.resources.routing_ip_invalid
import app.skipi.ui.resources.routing_ip_label
import app.skipi.ui.resources.routing_network_invalid
import app.skipi.ui.resources.routing_network_label
import app.skipi.ui.resources.routing_new_rule
import app.skipi.ui.resources.routing_outbound_tag_label
import app.skipi.ui.resources.routing_port_invalid
import app.skipi.ui.resources.routing_port_label
import app.skipi.ui.resources.routing_process_invalid
import app.skipi.ui.resources.routing_process_label
import app.skipi.ui.resources.routing_protocol_label
import app.skipi.ui.resources.routing_rule_name
import app.skipi.ui.resources.routing_unnamed_rule
import engine.network.isIpOrCidrAddress
import engine.network.isPortList
import engine.xray.isValidXrayExternalDomainRule
import engine.xray.isXrayExternalDomainRuleCandidate
import features.routing.model.DefaultRouteOutboundTag
import features.routing.model.RouteRule
import org.jetbrains.compose.resources.stringResource

data class RouteRuleOutboundItem(val tag: String, val label: String)

/** Portable rule editor. Platform pickers and navigation are supplied by the host. */
@Composable
fun SkipiRouteRuleEditorForm(
    initialRule: RouteRule?,
    nextRuleId: Int,
    outboundOptions: List<RouteRuleOutboundItem>,
    onSave: (RouteRule) -> Unit,
    onCancel: () -> Unit,
    onRequestInstalledAppPicker: (((String) -> Unit) -> Unit)? = null,
    installedAppsLabel: String = "Select installed app",
    onRequestGeoSitePicker: (((String) -> Unit) -> Unit)? = null,
    geoSitePickerLabel: String = "GeoSite suggestions",
    onRequestGeoIpPicker: (((String) -> Unit) -> Unit)? = null,
    geoIpPickerLabel: String = "GeoIP suggestions",
    modifier: Modifier = Modifier,
) {
    val identity = initialRule?.id ?: nextRuleId
    val newRuleName = stringResource(Res.string.routing_new_rule)
    val unnamedRuleName = stringResource(Res.string.routing_unnamed_rule)
    var remarks by rememberSaveable(identity, newRuleName) { mutableStateOf(initialRule?.remarks ?: newRuleName) }
    var domains by rememberSaveable(identity) { mutableStateOf((initialRule?.domain?.takeIf { it.isNotEmpty() } ?: listOf("geosite:category")).joinToString("\n")) }
    var ips by rememberSaveable(identity) { mutableStateOf(initialRule?.ip.orEmpty().joinToString("\n")) }
    var processes by rememberSaveable(identity) { mutableStateOf(initialRule?.process.orEmpty().joinToString("\n")) }
    var port by rememberSaveable(identity) { mutableStateOf(initialRule?.port.orEmpty()) }
    var protocol by rememberSaveable(identity) { mutableStateOf(initialRule?.protocol.orEmpty()) }
    var network by rememberSaveable(identity) { mutableStateOf(initialRule?.network.orEmpty()) }
    var outbound by rememberSaveable(identity) {
        mutableStateOf(initialRule?.outboundTag?.takeIf(String::isNotBlank) ?: DefaultRouteOutboundTag)
    }

    val domainsValid = splitLines(domains).all(::validDomain)
    val ipsValid = splitLines(ips).all(::validIp)
    val processesValid = splitLines(processes).all { it.isNotBlank() && it.none(Char::isWhitespace) }
    val portsValid = port.isBlank() || isPortList(port)
    val networkValid = network.isBlank() || network.split(',').all { it.trim().lowercase() in setOf("tcp", "udp") }
    val canSave = domainsValid && ipsValid && processesValid && portsValid && networkValid

    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SheetTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = stringResource(Res.string.routing_rule_name),
        )
        val outboundChoice = RouteRuleOutboundChoice(outboundOptions, outbound)
        SkipiDropdownPreference(
            title = stringResource(Res.string.routing_outbound_tag_label),
            items = outboundChoice.labels,
            selectedIndex = outboundChoice.selectedIndex,
            onSelectedIndexChange = { index -> outboundChoice.tagAt(index)?.let { outbound = it } },
            insideMargin = PaddingValues(0.dp, 8.dp),
        )
        RuleListField(stringResource(Res.string.routing_domain_label), domains, { domains = it }, domainsValid, stringResource(Res.string.routing_domain_invalid))
        if (onRequestGeoSitePicker != null) {
            TextButton(
                text = geoSitePickerLabel,
                onClick = {
                    onRequestGeoSitePicker { selected -> domains = (splitLines(domains) + selected).distinct().joinToString("\n") }
                },
            )
        }
        RuleListField(stringResource(Res.string.routing_ip_label), ips, { ips = it }, ipsValid, stringResource(Res.string.routing_ip_invalid))
        if (onRequestGeoIpPicker != null) {
            TextButton(
                text = geoIpPickerLabel,
                onClick = {
                    onRequestGeoIpPicker { selected -> ips = (splitLines(ips) + selected).distinct().joinToString("\n") }
                },
            )
        }
        RuleListField(stringResource(Res.string.routing_process_label), processes, { processes = it }, processesValid, stringResource(Res.string.routing_process_invalid))
        if (onRequestInstalledAppPicker != null) {
            TextButton(
                text = installedAppsLabel,
                onClick = {
                    onRequestInstalledAppPicker { selected ->
                        processes = (splitLines(processes) + selected).distinct().joinToString("\n")
                    }
                },
            )
        }
        SettingsTextField(
            value = port,
            onValueChange = { port = it },
            label = stringResource(Res.string.routing_port_label),
            errorText = if (portsValid) null else stringResource(Res.string.routing_port_invalid),
        )
        SheetTextField(
            value = protocol,
            onValueChange = { protocol = it },
            label = stringResource(Res.string.routing_protocol_label),
        )
        SettingsTextField(
            value = network,
            onValueChange = { network = it },
            label = stringResource(Res.string.routing_network_label),
            errorText = if (networkValid) null else stringResource(Res.string.routing_network_invalid),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(text = stringResource(Res.string.common_cancel), onClick = onCancel)
            TextButton(
                text = stringResource(Res.string.common_save),
                enabled = canSave,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    if (canSave) onSave(
                        RouteRule(
                            id = initialRule?.id ?: nextRuleId,
                            remarks = remarks.trim().ifBlank { unnamedRuleName },
                            outboundTag = outbound,
                            domain = splitLines(domains),
                            ip = splitLines(ips),
                            process = splitLines(processes),
                            port = port.trim(),
                            protocol = protocol.trim(),
                            network = network.trim(),
                            enabled = initialRule?.enabled ?: true,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun RuleListField(label: String, value: String, onValueChange: (String) -> Unit, valid: Boolean, error: String) {
    SheetTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2),
    )
    if (!valid) StringListStatusText(text = error, error = true)
}

private fun splitLines(value: String): List<String> = value.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()

private fun validDomain(value: String): Boolean {
    if (value.any(Char::isWhitespace)) return false
    if (value.startsWith("regexp:", ignoreCase = true)) return value.substringAfter(':').isNotBlank()
    if (isXrayExternalDomainRuleCandidate(value)) return isValidXrayExternalDomainRule(value)
    val prefix = value.substringBefore(':', missingDelimiterValue = "").lowercase()
    if (prefix in setOf("domain", "full", "keyword", "geosite")) return value.substringAfter(':').isNotBlank()
    return !value.contains("://") && !value.contains('/')
}

private fun validIp(value: String): Boolean {
    val normalized = value.lowercase()
    if (normalized.startsWith("geoip:") || normalized.startsWith("ext:")) return value.substringAfter(':').isNotBlank()
    return isIpOrCidrAddress(value)
}
