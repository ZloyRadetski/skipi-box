// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var outboundMenu by remember { mutableStateOf(false) }

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
        OutlinedTextField(
            value = remarks,
            onValueChange = { remarks = it },
            label = { Text(stringResource(Res.string.routing_rule_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Column {
            TextButton(onClick = { outboundMenu = true }) {
                Text("${stringResource(Res.string.routing_outbound_tag_label)}: ${outboundOptions.firstOrNull { it.tag == outbound }?.label ?: outbound}")
            }
            DropdownMenu(expanded = outboundMenu, onDismissRequest = { outboundMenu = false }) {
                outboundOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = { outbound = option.tag; outboundMenu = false },
                    )
                }
            }
        }
        RuleListField(stringResource(Res.string.routing_domain_label), domains, { domains = it }, domainsValid, stringResource(Res.string.routing_domain_invalid))
        if (onRequestGeoSitePicker != null) TextButton(onClick = {
            onRequestGeoSitePicker { selected -> domains = (splitLines(domains) + selected).distinct().joinToString("\n") }
        }) { Text(geoSitePickerLabel) }
        RuleListField(stringResource(Res.string.routing_ip_label), ips, { ips = it }, ipsValid, stringResource(Res.string.routing_ip_invalid))
        if (onRequestGeoIpPicker != null) TextButton(onClick = {
            onRequestGeoIpPicker { selected -> ips = (splitLines(ips) + selected).distinct().joinToString("\n") }
        }) { Text(geoIpPickerLabel) }
        RuleListField(stringResource(Res.string.routing_process_label), processes, { processes = it }, processesValid, stringResource(Res.string.routing_process_invalid))
        if (onRequestInstalledAppPicker != null) {
            TextButton(onClick = {
                onRequestInstalledAppPicker { selected ->
                    processes = (splitLines(processes) + selected).distinct().joinToString("\n")
                }
            }) { Text(installedAppsLabel) }
        }
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text(stringResource(Res.string.routing_port_label)) },
            isError = !portsValid,
            supportingText = if (portsValid) null else ({ Text(stringResource(Res.string.routing_port_invalid)) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = protocol,
            onValueChange = { protocol = it },
            label = { Text(stringResource(Res.string.routing_protocol_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = network,
            onValueChange = { network = it },
            label = { Text(stringResource(Res.string.routing_network_label)) },
            isError = !networkValid,
            supportingText = if (networkValid) null else ({ Text(stringResource(Res.string.routing_network_invalid)) }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.common_cancel)) }
            Button(
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
                enabled = canSave,
            ) { Text(stringResource(Res.string.common_save)) }
        }
    }
}

@Composable
private fun RuleListField(label: String, value: String, onValueChange: (String) -> Unit, valid: Boolean, error: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = !valid,
        supportingText = if (valid) null else ({ Text(error) }),
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
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
