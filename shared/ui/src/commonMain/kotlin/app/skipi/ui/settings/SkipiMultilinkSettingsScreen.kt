// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.SkipiContentIcon
import app.skipi.ui.layout.fadeUnderTopBar
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.resources.*
import app.skipi.ui.settings.search.SettingsSearchAnchor
import app.skipi.ui.settings.search.SettingsSearchFocusEffect
import app.skipi.ui.settings.search.SettingsSearchPage
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.theme.SkipiTheme
import features.multilink.MultilinkLink
import features.multilink.MultilinkLinkDraft
import features.multilink.MultilinkLinkKind
import features.multilink.MultilinkOverview
import features.multilink.MultilinkPhase
import features.multilink.MultilinkSettings
import features.multilink.MultilinkStatus
import features.multilink.formatMbps
import features.multilink.multilinkOverview
import features.multilink.parseProbeTargets
import features.multilink.probeTargetsText
import features.multilink.withDraft
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val overview = multilinkOverview(settings, state.status, state.tunnelRunning, state.coreSupported)
    val linkColors = linkPalette(state.status.links.map { it.id })
    val listState = rememberLazyListState()
    SettingsSearchFocusEffect(SettingsSearchPage.Multilink, listState)
    var editing by remember { mutableStateOf<MultilinkLink?>(null) }
    var editingCoolRate by remember { mutableStateOf(false) }
    var editingProbeTargets by remember { mutableStateOf(false) }
    var skippedOpen by remember { mutableStateOf(false) }
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
                item(key = "multilink_hero") {
                    SettingsSearchAnchor("multilink_enabled") {
                        MultilinkHeroCard(
                            settings = settings,
                            overview = overview,
                            links = state.status.links,
                            linkColors = linkColors,
                            onEnabledChange = { onSettingsChange(settings.copy(enabled = it)) },
                        )
                    }
                }
                item(key = "multilink_links_title") { SmallTitle(text = stringResource(Res.string.multilink_links)) }
                item(key = "multilink_links") {
                    SkipiSettingsSectionCard {
                        if (state.status.links.isEmpty()) {
                            MultilinkEmptyLinks()
                        } else {
                            state.status.links.forEachIndexed { index, link ->
                                key(link.id) {
                                    if (index > 0) HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                                    MultilinkLinkRow(
                                        link = link,
                                        settings = settings,
                                        color = linkColors.getValue(link.id),
                                        onOpen = { editing = link },
                                        onUsedChange = { used -> onSettingsChange(settings.withLinkUsed(link.id, used)) },
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
                                    .formatTemplate("rate" to formatMbps(settings.coolRate())),
                                startAction = settingIcon(Icons.Rounded.PhoneAndroid),
                                checked = settings.phoneCooling,
                                onCheckedChange = { onSettingsChange(settings.copy(phoneCooling = it)) },
                            )
                        }
                        AnimatedVisibility(
                            visible = settings.phoneCooling,
                            enter = fadeIn() + expandVertically(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            SettingsSearchAnchor("multilink_cool_rate") {
                                ArrowPreference(
                                    title = stringResource(Res.string.multilink_cool_rate),
                                    summary = formatMbps(settings.coolRate()),
                                    startAction = settingIcon(Icons.Rounded.Speed),
                                    onClick = { editingCoolRate = true },
                                )
                            }
                        }
                    }
                }
                item(key = "multilink_more") {
                    SmallTitle(text = stringResource(Res.string.multilink_more_section))
                    SkipiSettingsSectionCard {
                        SettingsSearchAnchor("multilink_probe_targets") {
                            ArrowPreference(
                                title = stringResource(Res.string.multilink_probe_targets),
                                summary = settings.probeTargetsText()
                                    .ifEmpty { stringResource(Res.string.multilink_probe_targets_default) },
                                startAction = settingIcon(Icons.Rounded.NetworkCheck),
                                onClick = { editingProbeTargets = true },
                            )
                        }
                        if (state.status.skipped.isNotEmpty()) {
                            ArrowPreference(
                                title = stringResource(Res.string.multilink_skipped_section)
                                    .formatTemplate("count" to state.status.skipped.size.toString()),
                                summary = stringResource(Res.string.multilink_skipped_summary),
                                startAction = settingIcon(Icons.Rounded.VisibilityOff),
                                onClick = { skippedOpen = !skippedOpen },
                            )
                            AnimatedVisibility(
                                visible = skippedOpen,
                                enter = fadeIn() + expandVertically(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                Column {
                                    state.status.skipped.forEach { skipped ->
                                        BasicComponent(title = skipped.name, summary = skipped.reason)
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "multilink_credit") {
                    Text(
                        text = stringResource(Res.string.multilink_credit),
                        color = SkipiTheme.colors.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    )
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

/** The switch, where merging stands, and while it merges the speed and each link's part of the traffic. */
@Composable
private fun MultilinkHeroCard(
    settings: MultilinkSettings,
    overview: MultilinkOverview,
    links: List<MultilinkLink>,
    linkColors: Map<String, Color>,
    onEnabledChange: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SkipiTheme.spacing.medium)
            .padding(bottom = SkipiTheme.spacing.medium),
        shape = SkipiTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SkipiTheme.colors.surface, contentColor = SkipiTheme.colors.onSurface),
    ) {
        Column(Modifier.fillMaxWidth().padding(SkipiTheme.spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkipiSettingIcon(Icons.Rounded.Lan, Modifier.padding(start = 6.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.multilink_enabled),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.SemiBold,
                        color = SkipiTheme.colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    PhasePill(overview)
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = overview.phase != MultilinkPhase.Unsupported,
                )
            }
            AnimatedVisibility(
                visible = overview.phase == MultilinkPhase.Merging,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(top = SkipiTheme.spacing.medium)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.large)) {
                        HeroFigure(stringResource(Res.string.multilink_download), formatMbps(overview.downMbps), Res.string.multilink_mbps)
                        HeroFigure(stringResource(Res.string.multilink_upload), formatMbps(overview.upMbps), Res.string.multilink_mbps)
                        HeroFigure(stringResource(Res.string.multilink_connections), overview.flows.toString(), null)
                    }
                    Spacer(Modifier.height(SkipiTheme.spacing.medium))
                    ShareBar(overview, linkColors)
                    Spacer(Modifier.height(10.dp))
                    ShareLegend(overview, links, settings, linkColors)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.multilink_enabled_summary),
                style = MiuixTheme.textStyles.body2,
                color = SkipiTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PhasePill(overview: MultilinkOverview) {
    val (text, color) = when (overview.phase) {
        MultilinkPhase.Unsupported -> stringResource(Res.string.multilink_core_unsupported) to SkipiTheme.colors.error
        MultilinkPhase.Off -> stringResource(Res.string.multilink_phase_off) to SkipiTheme.colors.onSurfaceVariant
        MultilinkPhase.Waiting -> stringResource(Res.string.multilink_phase_waiting) to SkipiTheme.colors.warning
        MultilinkPhase.OneLink -> stringResource(Res.string.multilink_phase_one_link) to SkipiTheme.colors.warning
        MultilinkPhase.Merging -> stringResource(Res.string.multilink_phase_merging)
            .formatTemplate("count" to overview.usedLinks.toString()) to SkipiTheme.colors.success
    }
    val shown by animateColorAsState(color)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(shown.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(shown))
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MiuixTheme.textStyles.body2, fontWeight = FontWeight.Medium, color = shown, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HeroFigure(label: String, value: String, unit: StringResource?) {
    Column {
        Text(text = label, style = MiuixTheme.textStyles.body2, color = SkipiTheme.colors.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = SkipiTheme.colors.onSurface)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(unit),
                    style = MiuixTheme.textStyles.body2,
                    color = SkipiTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

/** One bar split by the links' parts of the traffic. */
@Composable
private fun ShareBar(overview: MultilinkOverview, linkColors: Map<String, Color>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(50))
            .background(SkipiTheme.colors.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        overview.shares.forEach { share ->
            key(share.id) {
                val weight by animateFloatAsState(share.fraction.toFloat().coerceAtLeast(0.02f))
                Box(Modifier.weight(weight).fillMaxHeight().background(linkColors[share.id] ?: SkipiTheme.colors.accent))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShareLegend(
    overview: MultilinkOverview,
    links: List<MultilinkLink>,
    settings: MultilinkSettings,
    linkColors: Map<String, Color>,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SkipiTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        overview.shares.forEach { share ->
            val link = links.firstOrNull { it.id == share.id } ?: return@forEach
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(linkColors[share.id] ?: SkipiTheme.colors.accent))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${settings.displayName(link.id, link.name)} · ${kotlin.math.round(share.fraction * 100).toInt()}%",
                    style = MiuixTheme.textStyles.body2,
                    color = SkipiTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MultilinkEmptyLinks() {
    Text(
        text = stringResource(Res.string.multilink_links_empty),
        style = MiuixTheme.textStyles.body1,
        color = SkipiTheme.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
    )
}

/** A link: what it is and where it stands on the left, its live speed and the switch that lets merging use it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultilinkLinkRow(
    link: MultilinkLink,
    settings: MultilinkSettings,
    color: Color,
    onOpen: () -> Unit,
    onUsedChange: (Boolean) -> Unit,
) {
    val used = !link.excluded && link.id !in settings.excluded
    val dim by animateFloatAsState(if (used) 1f else 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.alpha(dim)) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                SkipiContentIcon(imageVector = link.kind.icon(), contentDescription = null, tint = color.brighter(), modifier = Modifier.size(22.dp))
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(SkipiTheme.colors.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(link.stateColor()),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(dim)) {
            Text(
                text = settings.displayName(link.id, link.name),
                style = MiuixTheme.textStyles.headline1,
                fontWeight = FontWeight.Medium,
                color = SkipiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(link.kind.label(), link.ipv4.ifEmpty { link.ipv6 }, linkStateLabel(link))
                    .filter(String::isNotEmpty).joinToString(" · "),
                style = MiuixTheme.textStyles.body2,
                color = SkipiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val chips = linkChips(link, settings, used)
            if (chips.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { chips.forEach { LinkChip(it) } }
            }
        }
        if (used) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = "↓ " + formatMbps(link.mbpsDown()) + " " + stringResource(Res.string.multilink_mbps),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.SemiBold,
                    color = SkipiTheme.colors.onSurface,
                )
                Text(
                    text = "↑ " + formatMbps(link.mbpsUp()) + " · " +
                        stringResource(Res.string.multilink_link_flows).formatTemplate("count" to link.flows.toString()),
                    style = MiuixTheme.textStyles.body2,
                    color = SkipiTheme.colors.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Switch(checked = used, onCheckedChange = onUsedChange)
    }
}

@Composable
private fun linkChips(link: MultilinkLink, settings: MultilinkSettings, used: Boolean): List<String> = buildList {
    if (!used) add(stringResource(Res.string.multilink_chip_excluded))
    settings.limits[link.id]?.let { add(stringResource(Res.string.multilink_chip_limit).formatTemplate("limit" to formatMbps(it))) }
    settings.weights[link.id]?.let { add(stringResource(Res.string.multilink_chip_weight).formatTemplate("weight" to formatMbps(it))) }
    if (link.phone || link.id in settings.phones) add(stringResource(Res.string.multilink_kind_phone))
    if (link.coolMbps > 0) add(stringResource(Res.string.multilink_chip_cooling).formatTemplate("rate" to formatMbps(link.coolMbps)))
}

@Composable
private fun LinkChip(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = SkipiTheme.colors.onSurface.copy(alpha = 0.8f),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SkipiTheme.colors.onSurface.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun linkStateLabel(link: MultilinkLink): String = when (if (link.state != "-") link.state else link.state6) {
    "up" -> stringResource(Res.string.multilink_state_up)
    "no internet" -> stringResource(Res.string.multilink_state_down)
    "-" -> ""
    else -> stringResource(Res.string.multilink_state_checking)
}

@Composable
private fun MultilinkLink.stateColor(): Color = when {
    online -> SkipiTheme.colors.success
    state == "no internet" || state6 == "no internet" -> SkipiTheme.colors.error
    else -> SkipiTheme.colors.warning
}

@Composable
private fun MultilinkLinkKind.label(): String = when (this) {
    MultilinkLinkKind.WiFi -> stringResource(Res.string.multilink_kind_wifi)
    MultilinkLinkKind.Ethernet -> stringResource(Res.string.multilink_kind_ethernet)
    MultilinkLinkKind.Phone -> stringResource(Res.string.multilink_kind_phone)
    MultilinkLinkKind.Other -> stringResource(Res.string.multilink_kind_other)
}

private fun MultilinkLinkKind.icon(): ImageVector = when (this) {
    MultilinkLinkKind.WiFi -> Icons.Rounded.Wifi
    MultilinkLinkKind.Ethernet -> Icons.Rounded.Cable
    MultilinkLinkKind.Phone -> Icons.Rounded.PhoneAndroid
    MultilinkLinkKind.Other -> Icons.Rounded.Lan
}

/** A color per link, the accent first, kept stable by the order Core lists the links in. */
@Composable
private fun linkPalette(ids: List<String>): Map<String, Color> {
    val colors = listOf(SkipiTheme.colors.accent) + LinkColors
    return ids.withIndex().associate { (index, id) -> id to colors[index % colors.size] }
}

private val LinkColors = listOf(
    Color(0xFF4FC3F7),
    Color(0xFF81C784),
    Color(0xFFBA68C8),
    Color(0xFFFFD54F),
    Color(0xFFE57373),
)

/** The link's color lifted toward white, so a dark accent still reads on its own tinted tile. */
private fun Color.brighter(): Color = Color(
    red = red + (1f - red) * 0.35f,
    green = green + (1f - green) * 0.35f,
    blue = blue + (1f - blue) * 0.35f,
    alpha = alpha,
)

private fun MultilinkSettings.coolRate(): Double = coolMbps.takeIf { it > 0 } ?: DefaultCoolMbps

private const val DefaultCoolMbps = 20.0
private const val CoolRateKey = "cool-rate"
