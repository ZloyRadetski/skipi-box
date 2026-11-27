// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.AppWindowBottomSheet
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.resources.*
import app.skipi.ui.settings.search.SettingsSearchAnchor
import app.skipi.ui.settings.search.SettingsSearchFocusEffect
import app.skipi.ui.settings.search.SettingsSearchPage
import app.skipi.ui.text.formatTemplate
import features.multilink.MultilinkDraftError
import features.multilink.MultilinkDraftException
import features.multilink.MultilinkLink
import features.multilink.MultilinkLinkDraft
import features.multilink.MultilinkLinkKind
import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import features.multilink.formatMbps
import features.multilink.parseProbeTargets
import features.multilink.probeTargetsText
import features.multilink.withDraft
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/** What the link merging page shows: the saved settings and Core's live view of the links. */
data class MultilinkSettingsState(
    val settings: MultilinkSettings,
    val status: MultilinkStatus,
    val tunnelRunning: Boolean,
    /** False for a Core library from before link merging. */
    val coreSupported: Boolean = true,
)

/** Link merging settings. Desktop only: the host owns persistence and talks to Core. */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SkipiMultilinkSettingsScreen(
    state: MultilinkSettingsState,
    padding: PaddingValues,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onSettingsChange: (MultilinkSettings) -> Unit,
) {
    val settings = state.settings
    val listState = rememberLazyListState()
    SettingsSearchFocusEffect(SettingsSearchPage.Multilink, listState)
    var editing by remember { mutableStateOf<MultilinkLink?>(null) }
    var editingCoolRate by remember { mutableStateOf(false) }
    var editingProbeTargets by remember { mutableStateOf(false) }
    val title = stringResource(Res.string.settings_category_multilink)
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { key(title) { SettingsBackTopBar(title, isWideScreen, onBack) } },
    ) { innerPadding ->
        val contentPadding = settingsPageContentPadding(innerPadding, padding, isWideScreen)
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fadeUnderTopBar(contentPadding.calculateTopPadding()).fillMaxSize(),
                contentPadding = contentPadding.withTopBarGap(),
            ) {
                item(key = "multilink_switch") {
                    SkipiSettingsSectionCard {
                        SettingsSearchAnchor("multilink_enabled") {
                            SwitchPreference(
                                title = stringResource(Res.string.multilink_enabled),
                                summary = stringResource(Res.string.multilink_enabled_summary),
                                startAction = settingIcon(Icons.Rounded.Lan),
                                checked = settings.enabled,
                                onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) },
                            )
                        }
                    }
                    multilinkStateText(state)?.let { SettingsSheetStatusText(it) }
                }
                item(key = "multilink_links_title") { SmallTitle(text = stringResource(Res.string.multilink_links)) }
                if (state.status.links.isEmpty()) {
                    item(key = "multilink_links_empty") {
                        SettingsSheetStatusText(stringResource(Res.string.multilink_links_empty))
                    }
                } else {
                    item(key = "multilink_links") {
                        SkipiSettingsSectionCard {
                            state.status.links.forEach { link ->
                                key(link.id) {
                                    ArrowPreference(
                                        title = settings.displayName(link.id, link.name),
                                        summary = multilinkLinkSummary(settings, link),
                                        startAction = settingIcon(link.kind.icon()),
                                        onClick = { editing = link },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "multilink_phones") {
                    SmallTitle(text = stringResource(Res.string.multilink_phone_section))
                    SkipiSettingsSectionCard {
                        SettingsSearchAnchor("multilink_phone_cooling") {
                            SwitchPreference(
                                title = stringResource(Res.string.multilink_phone_cooling),
                                summary = stringResource(Res.string.multilink_phone_cooling_summary)
                                    .formatTemplate("rate" to formatMbps(settings.coolMbps.takeIf { it > 0 } ?: DefaultCoolMbps)),
                                startAction = settingIcon(Icons.Rounded.PhoneAndroid),
                                checked = settings.phoneCooling,
                                onCheckedChange = { onSettingsChange(settings.copy(phoneCooling = it)) },
                            )
                        }
                        SettingsSearchAnchor("multilink_cool_rate") {
                            ArrowPreference(
                                title = stringResource(Res.string.multilink_cool_rate),
                                summary = formatMbps(settings.coolMbps.takeIf { it > 0 } ?: DefaultCoolMbps),
                                onClick = { editingCoolRate = true },
                            )
                        }
                    }
                }
                item(key = "multilink_more") {
                    SmallTitle(text = stringResource(Res.string.multilink_more_section))
                    SkipiSettingsSectionCard {
                        SettingsSearchAnchor("multilink_probe_targets") {
                            ArrowPreference(
                                title = stringResource(Res.string.multilink_probe_targets),
                                summary = settings.probeTargetsText().ifEmpty { stringResource(Res.string.multilink_probe_targets_default) },
                                startAction = settingIcon(Icons.Rounded.NetworkCheck),
                                onClick = { editingProbeTargets = true },
                            )
                        }
                    }
                }
                if (state.status.skipped.isNotEmpty()) {
                    item(key = "multilink_skipped_title") { SmallTitle(text = stringResource(Res.string.multilink_skipped_section)) }
                    items(state.status.skipped, key = { "skipped:" + it.name }) { skipped ->
                        SettingsSheetStatusText("${skipped.name}: ${skipped.reason}")
                    }
                }
                item(key = "multilink_credit") {
                    SettingsSheetStatusText(stringResource(Res.string.multilink_credit))
                }
            }
            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = contentPadding,
            )
        }
    }
    MultilinkLinkSheet(
        link = editing,
        settings = settings,
        onDismiss = { editing = null },
        onSave = { updated ->
            onSettingsChange(updated)
            editing = null
        },
    )
    MultilinkTextSheet(
        show = editingCoolRate,
        title = stringResource(Res.string.multilink_cool_rate),
        initial = settings.coolMbps.takeIf { it > 0 }?.let(::formatMbps).orEmpty(),
        help = stringResource(Res.string.multilink_cool_rate_help),
        error = stringResource(Res.string.multilink_link_limit_error),
        parse = { text ->
            val draft = settings.withDraft(CoolRateKey, MultilinkLinkDraft(limitMbps = text)).getOrNull()
            draft?.let { settings.copy(coolMbps = it.limits[CoolRateKey] ?: 0.0) }
        },
        onDismiss = { editingCoolRate = false },
        onSave = { updated ->
            onSettingsChange(updated)
            editingCoolRate = false
        },
    )
    MultilinkTextSheet(
        show = editingProbeTargets,
        title = stringResource(Res.string.multilink_probe_targets),
        initial = settings.probeTargetsText(),
        help = stringResource(Res.string.multilink_probe_targets_help),
        error = stringResource(Res.string.multilink_probe_targets_error),
        parse = { text -> parseProbeTargets(text).getOrNull()?.let { settings.copy(probeTargets = it) } },
        onDismiss = { editingProbeTargets = false },
        onSave = { updated ->
            onSettingsChange(updated)
            editingProbeTargets = false
        },
    )
}

@Composable
private fun multilinkStateText(state: MultilinkSettingsState): String? = when {
    !state.coreSupported -> stringResource(Res.string.multilink_core_unsupported)
    !state.settings.enabled -> null
    !state.tunnelRunning || !state.status.enabled -> stringResource(Res.string.multilink_state_waiting)
    !state.status.merging -> stringResource(Res.string.multilink_state_one_link)
    else -> {
        val used = state.status.links.filter { !it.excluded && it.online }
        stringResource(Res.string.multilink_state_merging).formatTemplate(
            "count" to used.size.toString(),
            "down" to formatMbps(used.sumOf { it.mbpsDown() }),
        )
    }
}

@Composable
private fun multilinkLinkSummary(settings: MultilinkSettings, link: MultilinkLink): String {
    if (link.excluded || link.id in settings.excluded) return stringResource(Res.string.multilink_link_excluded)
    val summary = stringResource(Res.string.multilink_link_summary).formatTemplate(
        "kind" to link.kind.label(),
        "address" to link.ipv4.ifEmpty { link.ipv6 },
        "state" to linkStateLabel(if (link.state != "-") link.state else link.state6),
        "down" to formatMbps(link.mbpsDown()),
        "up" to formatMbps(link.mbpsUp()),
        "flows" to link.flows.toString(),
    )
    val limit = settings.limits[link.id] ?: return summary
    return summary + " · " + stringResource(Res.string.multilink_link_limit).formatTemplate("limit" to formatMbps(limit))
}

@Composable
private fun linkStateLabel(state: String): String = when (state) {
    "up" -> stringResource(Res.string.multilink_state_up)
    "no internet" -> stringResource(Res.string.multilink_state_down)
    else -> stringResource(Res.string.multilink_state_checking)
}

@Composable
private fun MultilinkLinkKind.label(): String = when (this) {
    MultilinkLinkKind.WiFi -> stringResource(Res.string.multilink_kind_wifi)
    MultilinkLinkKind.Ethernet -> stringResource(Res.string.multilink_kind_ethernet)
    MultilinkLinkKind.Phone -> stringResource(Res.string.multilink_kind_phone)
    MultilinkLinkKind.Other -> stringResource(Res.string.multilink_kind_other)
}

private fun MultilinkLinkKind.icon() = when (this) {
    MultilinkLinkKind.WiFi -> Icons.Rounded.Wifi
    MultilinkLinkKind.Ethernet -> Icons.Rounded.Cable
    MultilinkLinkKind.Phone -> Icons.Rounded.PhoneAndroid
    MultilinkLinkKind.Other -> Icons.Rounded.Lan
}

private const val DefaultCoolMbps = 20.0
private const val CoolRateKey = "cool-rate"
