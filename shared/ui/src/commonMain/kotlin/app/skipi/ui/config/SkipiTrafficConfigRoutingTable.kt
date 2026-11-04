// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.ui.components.ImportModeDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_close
import app.skipi.ui.resources.common_merge_import
import app.skipi.ui.resources.common_replace_existing
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.routing_import_clipboard_message
import app.skipi.ui.resources.routing_import_clipboard_title
import app.skipi.ui.resources.routing_outbound_block
import app.skipi.ui.resources.routing_outbound_direct
import app.skipi.ui.resources.routing_outbound_proxy
import app.skipi.ui.resources.routing_table_col_name
import app.skipi.ui.resources.routing_table_col_network
import app.skipi.ui.resources.routing_table_col_port
import app.skipi.ui.resources.routing_table_col_protocol
import app.skipi.ui.resources.routing_table_col_route
import app.skipi.ui.resources.routing_table_col_targets
import app.skipi.ui.resources.routing_table_complex_label
import app.skipi.ui.resources.routing_table_complex_open
import app.skipi.ui.resources.routing_table_complex_text
import app.skipi.ui.resources.routing_table_complex_title
import app.skipi.ui.resources.routing_table_delete
import app.skipi.ui.resources.routing_table_domains
import app.skipi.ui.resources.routing_table_domains_help
import app.skipi.ui.resources.routing_table_empty
import app.skipi.ui.resources.routing_table_final
import app.skipi.ui.resources.routing_table_final_hint
import app.skipi.ui.resources.routing_table_import_clipboard
import app.skipi.ui.resources.routing_table_import_counts
import app.skipi.ui.resources.routing_table_import_file
import app.skipi.ui.resources.routing_table_import_final
import app.skipi.ui.resources.routing_table_import_skipped
import app.skipi.ui.resources.routing_table_import_url
import app.skipi.ui.resources.routing_table_ips
import app.skipi.ui.resources.routing_table_ips_help
import app.skipi.ui.resources.routing_table_move_down
import app.skipi.ui.resources.routing_table_move_up
import app.skipi.ui.resources.routing_table_name
import app.skipi.ui.resources.routing_table_needs_condition
import app.skipi.ui.resources.routing_table_network_any
import app.skipi.ui.resources.routing_table_port_hint
import app.skipi.ui.resources.routing_table_processes
import app.skipi.ui.resources.routing_table_processes_help
import app.skipi.ui.resources.routing_table_rule
import app.skipi.ui.resources.routing_table_search
import app.skipi.ui.resources.routing_table_send_to
import app.skipi.ui.resources.routing_table_server_or_group
import app.skipi.ui.resources.routing_table_skip_dns
import app.skipi.ui.resources.routing_table_skip_inbound
import app.skipi.ui.resources.routing_table_skip_outbound
import app.skipi.ui.resources.routing_table_toggle
import app.skipi.ui.resources.routing_table_unwritable
import app.skipi.ui.resources.routing_unnamed_rule
import app.skipi.ui.server.display.ProtocolColorUtils
import app.skipi.ui.text.formatTemplate
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.StatusColorDefaults
import features.clipboard.ClipboardImportMode
import features.config.RoutingTable
import features.config.RoutingTableEntry
import features.config.RoutingTableRow
import features.config.ShadowrocketRule
import features.config.TrafficConfigState
import features.config.V2rayRoutingImport
import features.config.V2rayRoutingSkipReason
import features.config.toRoutingTable
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Where a v2rayN rule export comes from; the host reads it, since files, clipboard and links are platform work. */
enum class RoutingImportSource { File, Clipboard, Url }

/** How long an edit rests before it is written, so a burst of typing is one write. */
private const val EditPauseMillis = 450L

private val KnownProtocols = listOf("http", "tls", "quic", "bittorrent")

private const val RoutingTableDesignWidth = 900f
private const val RoutingTableMaxZoom = 1.5f

/**
 * What the routing table has open: nothing, one rule, a rule that is not saved yet, or the FINAL row. It lives apart
 * from the table so that it survives the host showing the server picker in place of the table.
 */
@Stable
class SkipiRoutingTableState internal constructor() {
    var selectedLine: Int? by mutableStateOf(null)
        private set
    var isNew: Boolean by mutableStateOf(false)
        private set
    var isFinalSelected: Boolean by mutableStateOf(false)
        private set
    /** The open rule does not fit the table, so it is edited as a rule by the host. */
    var isComplex: Boolean by mutableStateOf(false)
        private set
    var draft: RoutingTableRow by mutableStateOf(RoutingTableRow())
        private set
    var query: String by mutableStateOf("")
    /** Values the last save had to leave out. */
    var unwritable: List<String> by mutableStateOf(emptyList())
        private set

    /** Identity of the open editor: its text fields start again only when this changes. */
    internal var editorKey by mutableIntStateOf(0)
        private set
    internal var version by mutableIntStateOf(0)
        private set
    private var pending = false
    private var immediate = false

    val hasEditor: Boolean
        get() = isNew || selectedLine != null || isFinalSelected

    fun select(entry: RoutingTableEntry) {
        open(selectedLine = entry.lineNumber, row = entry.row ?: RoutingTableRow(policy = entry.rule.policy), complex = entry.row == null)
    }

    fun selectFinal(rule: ShadowrocketRule?) {
        open(selectedLine = null, row = RoutingTableRow(policy = rule?.policy ?: "PROXY"), final = true)
    }

    fun startNew() {
        open(selectedLine = null, row = RoutingTableRow(), isNew = true)
    }

    fun close() {
        open(selectedLine = null, row = RoutingTableRow())
    }

    /** The policy chosen in the host's server picker for the open rule. */
    fun applyPolicy(policy: String) = change(immediate = true) { it.copy(policy = policy) }

    internal fun change(immediate: Boolean = false, transform: (RoutingTableRow) -> RoutingTableRow) {
        if (isComplex) return
        draft = transform(draft)
        pending = true
        this.immediate = immediate
        version++
    }

    internal fun delayMillis(): Long = if (immediate) 0L else EditPauseMillis

    internal fun hasPendingEdit(): Boolean = pending

    internal fun moved(toLine: Int) {
        selectedLine = toLine
    }

    internal fun reconcile(table: RoutingTable) {
        val line = selectedLine ?: return
        if (table.entries.none { it.lineNumber == line }) close()
    }

    internal fun flush(profileId: Int, controller: TrafficConfigEditorController) {
        if (!pending) return
        pending = false
        if (isFinalSelected) {
            controller.setRoutingFinalPolicy(profileId, draft.policy)
            return
        }
        // A rule without any condition cannot be written; it stays open until it has one.
        if (!draft.hasConditions) return
        val saved = controller.saveRoutingRow(profileId, if (isNew) null else selectedLine, draft)
        if (saved.result == TrafficConfigEditorResult.Saved) {
            saved.lineNumber?.let { selectedLine = it }
            isNew = false
            unwritable = saved.unwritable
        }
    }

    private fun open(
        selectedLine: Int?,
        row: RoutingTableRow,
        complex: Boolean = false,
        final: Boolean = false,
        isNew: Boolean = false,
    ) {
        this.selectedLine = selectedLine
        this.isNew = isNew
        isFinalSelected = final
        isComplex = complex
        draft = row
        unwritable = emptyList()
        pending = false
        editorKey++
    }
}

@Composable
fun rememberSkipiRoutingTableState(profileId: Int): SkipiRoutingTableState = remember(profileId) { SkipiRoutingTableState() }

/**
 * The rules of a profile as one table with the editor of the open rule beneath it. Edits are written to the profile
 * through [controller] shortly after they are made. The host supplies the server picker, the editor of rules that do
 * not fit the table, and the reading of imports.
 */
@Composable
fun SkipiTrafficConfigRoutingTable(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    state: SkipiRoutingTableState,
    contentPadding: PaddingValues,
    onPickPolicy: (current: String) -> Unit,
    onEditRule: (ShadowrocketRule) -> Unit,
    onImport: (RoutingImportSource) -> Unit,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    val table = remember(profile.rawConfig) { profile.rawConfig.toRoutingTable() }
    val currentController by rememberUpdatedState(controller)

    LaunchedEffect(state.version) {
        if (state.hasPendingEdit()) {
            delay(state.delayMillis())
            state.flush(profile.id, currentController)
        }
    }
    DisposableEffect(state, profile.id) {
        onDispose { state.flush(profile.id, currentController) }
    }
    LaunchedEffect(table) { state.reconcile(table) }

    val query = state.query.trim().lowercase()
    val visible = remember(table, query) {
        if (query.isEmpty()) table.entries else table.entries.filter { it.matches(query) }
    }

    // The table is laid out for about 900 dp and zoomed to the width it is given, so on a big screen it fills the
    // window instead of sitting small in a corner. Wider than the cap, the flexible columns take the extra room.
    BoxWithConstraints(modifier.fillMaxSize().padding(contentPadding)) {
        val density = LocalDensity.current
        val zoom = (maxWidth.value / RoutingTableDesignWidth).coerceIn(1f, RoutingTableMaxZoom)
        // The editor takes at most 42% of the height (in the zoomed dp), so a low window still shows several rules.
        val editorHeight = (maxHeight.value / zoom * 0.42f).coerceIn(260f, 340f).dp
        CompositionLocalProvider(LocalDensity provides Density(density.density * zoom, density.fontScale)) {
    Column(Modifier.fillMaxSize()) {
        RoutingTableToolbar(state, onAdd = state::startNew, onImport = onImport)
        val message = notice ?: state.unwritable.takeIf { it.isNotEmpty() }?.let {
            stringResource(Res.string.routing_table_unwritable).formatTemplate("values" to it.joinToString(", "))
        }
        if (message != null) {
            Text(message, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        RoutingTableHeader()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (table.entries.isEmpty() && table.finalRule == null) {
                Text(stringResource(Res.string.routing_table_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(12.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = RoutingTableEntry::lineNumber) { entry ->
                    RoutingTableRowView(entry, selected = state.selectedLine == entry.lineNumber && !state.isNew, state = state, profileId = profile.id, controller = controller)
                }
            }
        }
        RoutingTableFinalRow(table, selected = state.isFinalSelected, onClick = { state.selectFinal(table.finalRule) })
        if (state.hasEditor) {
            Spacer(Modifier.height(8.dp))
            RoutingTableEditor(profile, table, state, controller, editorHeight, onPickPolicy, onEditRule)
        }
    }
        }
    }
}

private fun RoutingTableEntry.matches(query: String): Boolean {
    val row = row
    val haystack = if (row == null) {
        rule.listSummaryText()
    } else {
        (listOf(row.name) + row.domains + row.ips + row.processes).joinToString("\n")
    }
    return haystack.lowercase().contains(query) || rule.remarks.lowercase().contains(query)
}

private fun ShadowrocketRule.listSummaryText(): String = "$type $value"

private val CheckColumn = 36.dp
private val NameColumn = 190.dp
private val RouteColumn = 84.dp
private val PortColumn = 96.dp
private val ProtocolColumn = 110.dp
private val NetworkColumn = 56.dp

@Composable
private fun RoutingTableToolbar(state: SkipiRoutingTableState, onAdd: () -> Unit, onImport: (RoutingImportSource) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolbarButton(stringResource(Res.string.configs_rules_add), primary = true, onClick = onAdd)
        ToolbarButton(stringResource(Res.string.routing_table_import_file)) { onImport(RoutingImportSource.File) }
        ToolbarButton(stringResource(Res.string.routing_table_import_clipboard)) { onImport(RoutingImportSource.Clipboard) }
        ToolbarButton(stringResource(Res.string.routing_table_import_url)) { onImport(RoutingImportSource.Url) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(260.dp)) {
            TextField(
                state = remember { TextFieldState(state.query) },
                inputTransformation = { state.query = asCharSequence().toString() },
                label = stringResource(Res.string.routing_table_search),
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth().testTag("routing-search"),
            )
        }
    }
}

@Composable
private fun ToolbarButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (primary) SkipiTheme.colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (primary) SkipiTheme.colors.onAccent else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun RoutingTableHeader() {
    Row(Modifier.fillMaxWidth().height(28.dp).padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(CheckColumn))
        HeaderCell(stringResource(Res.string.routing_table_col_name), NameColumn)
        HeaderCell(stringResource(Res.string.routing_table_col_route), RouteColumn)
        HeaderCell(stringResource(Res.string.routing_table_col_port), PortColumn)
        HeaderCell(stringResource(Res.string.routing_table_col_protocol), ProtocolColumn)
        HeaderCell(stringResource(Res.string.routing_table_col_network), NetworkColumn)
        Text(stringResource(Res.string.routing_table_col_targets), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(width), maxLines = 1)
}

@Composable
private fun RoutingTableRowView(
    entry: RoutingTableEntry,
    selected: Boolean,
    state: SkipiRoutingTableState,
    profileId: Int,
    controller: TrafficConfigEditorController,
) {
    val rule = entry.rule
    val row = entry.row
    val enabled = !rule.isDisabled
    val name = rule.remarks.ifBlank { stringResource(Res.string.routing_unnamed_rule) }
    val toggleLabel = stringResource(Res.string.routing_table_toggle).formatTemplate("name" to name)
    Row(
        Modifier.fillMaxWidth().height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) SkipiTheme.colors.surfaceVariant else Color.Transparent)
            .clickable { state.select(entry) }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(CheckColumn), contentAlignment = Alignment.Center) {
            Checkbox(
                state = ToggleableState(enabled),
                onClick = { controller.setRoutingRuleEnabled(profileId, entry.lineNumber, !enabled) },
                modifier = Modifier.testTag("routing-toggle-${entry.lineNumber}"),
            )
        }
        val dim = Modifier.alpha(if (enabled) 1f else 0.45f)
        RowCell(name, NameColumn, dim, weight = FontWeight.Medium)
        Text(
            rule.policy,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = routeColor(rule.policy),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = dim.width(RouteColumn),
        )
        RowCell(row?.port.orEmpty(), PortColumn, dim)
        RowCell(row?.protocols.orEmpty().joinToString(", "), ProtocolColumn, dim)
        RowCell(row?.network.orEmpty(), NetworkColumn, dim)
        Text(
            text = if (row == null) stringResource(Res.string.routing_table_complex_label) else (row.domains + row.ips + row.processes).joinToString(", "),
            fontSize = 11.5.sp,
            fontFamily = if (row == null) null else FontFamily.Monospace,
            color = if (row == null) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = dim.weight(1f),
        )
    }
}

@Composable
private fun RowCell(text: String, width: Dp, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Normal) {
    Text(text, fontSize = 12.5.sp, fontWeight = weight, color = MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier.width(width).padding(end = 8.dp))
}

@Composable
private fun RoutingTableFinalRow(table: RoutingTable, selected: Boolean, onClick: () -> Unit) {
    val policy = table.finalRule?.policy ?: "PROXY"
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(SkipiTheme.colors.surfaceVariant))
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).height(36.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) SkipiTheme.colors.surfaceVariant else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(CheckColumn))
            RowCell(stringResource(Res.string.routing_table_final), NameColumn, weight = FontWeight.Medium)
            Text(policy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = routeColor(policy), maxLines = 1, modifier = Modifier.width(RouteColumn))
            Spacer(Modifier.width(PortColumn + ProtocolColumn + NetworkColumn))
            Text(stringResource(Res.string.routing_table_final_hint), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun routeColor(policy: String): Color {
    val isDark = SkipiTheme.colors.isDark
    return when {
        policy.equals("DIRECT", ignoreCase = true) -> StatusColorDefaults.pingFast(isDark)
        policy.startsWith("REJECT", ignoreCase = true) -> StatusColorDefaults.statusStopped(isDark)
        else -> ProtocolColorUtils.resolveProtocolColor(protocol = "VLESS", isDark = isDark)
    }
}

@Composable
private fun RoutingTableEditor(
    profile: TrafficConfigState,
    table: RoutingTable,
    state: SkipiRoutingTableState,
    controller: TrafficConfigEditorController,
    height: Dp,
    onPickPolicy: (current: String) -> Unit,
    onEditRule: (ShadowrocketRule) -> Unit,
) {
    val draft = state.draft
    val index = table.entries.indexOfFirst { it.lineNumber == state.selectedLine }
    val savedRule = table.entries.getOrNull(index)?.rule
    Row(
        Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(12.dp)).background(SkipiTheme.colors.surface).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.width(470.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        state.isFinalSelected -> stringResource(Res.string.routing_table_final)
                        state.isComplex -> stringResource(Res.string.routing_table_complex_title)
                        else -> stringResource(Res.string.routing_table_rule)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (state.selectedLine != null && !state.isFinalSelected) {
                    SmallAction(stringResource(Res.string.routing_table_move_up), enabled = index > 0) {
                        val to = table.entries[index - 1].lineNumber
                        controller.moveRoutingRule(profile.id, index, index - 1)
                        state.moved(to)
                    }
                    SmallAction(stringResource(Res.string.routing_table_move_down), enabled = index in 0 until table.entries.lastIndex) {
                        val to = table.entries[index + 1].lineNumber
                        controller.moveRoutingRule(profile.id, index, index + 1)
                        state.moved(to)
                    }
                    SmallAction(stringResource(Res.string.routing_table_delete)) {
                        state.selectedLine?.let { controller.deleteRoutingRule(profile.id, it) }
                        state.close()
                    }
                }
                SmallAction(stringResource(Res.string.common_close)) { state.close() }
            }

            if (state.isComplex) {
                Text(stringResource(Res.string.routing_table_complex_text), fontSize = 12.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                savedRule?.let { rule ->
                    Text("${rule.type} ${rule.value}", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurface, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    ToolbarButton(stringResource(Res.string.routing_table_complex_open), primary = true) { onEditRule(rule) }
                }
            } else {
                if (!state.isFinalSelected) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            state = remember(state.editorKey) { TextFieldState(draft.name) },
                            inputTransformation = { val value = asCharSequence().toString(); state.change { it.copy(name = value) } },
                            label = stringResource(Res.string.routing_table_name),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.weight(1.3f).testTag("routing-name"),
                        )
                        TextField(
                            state = remember(state.editorKey) { TextFieldState(draft.port) },
                            inputTransformation = { val value = asCharSequence().toString(); state.change { it.copy(port = value) } },
                            label = stringResource(Res.string.routing_table_col_port),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            modifier = Modifier.weight(1f).testTag("routing-port"),
                        )
                    }
                    Text(stringResource(Res.string.routing_table_port_hint), fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FieldLabel(stringResource(Res.string.routing_table_send_to))
                        Segmented(
                            options = listOf(
                                "proxy" to stringResource(Res.string.routing_outbound_proxy),
                                "direct" to stringResource(Res.string.routing_outbound_direct),
                                "block" to stringResource(Res.string.routing_outbound_block),
                            ),
                            selected = when {
                                draft.policy.equals("PROXY", ignoreCase = true) -> "proxy"
                                draft.policy.equals("DIRECT", ignoreCase = true) -> "direct"
                                draft.policy.equals("REJECT", ignoreCase = true) -> "block"
                                else -> ""
                            },
                            tagPrefix = "routing-route-",
                        ) { key -> state.change(immediate = true) { it.copy(policy = when (key) { "direct" -> "DIRECT"; "block" -> "REJECT"; else -> "PROXY" }) } }
                    }
                    if (!state.isFinalSelected) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            FieldLabel(stringResource(Res.string.routing_table_col_network))
                            Segmented(
                                options = listOf("" to stringResource(Res.string.routing_table_network_any), "tcp" to "tcp", "udp" to "udp"),
                                selected = draft.network,
                                tagPrefix = "routing-network-",
                            ) { key -> state.change(immediate = true) { it.copy(network = key) } }
                        }
                    }
                }
                SmallAction(
                    text = stringResource(Res.string.routing_table_server_or_group) + draft.policy.takeUnless { it.uppercase() in setOf("PROXY", "DIRECT", "REJECT") }?.let { " ($it)" }.orEmpty(),
                ) { onPickPolicy(draft.policy) }

                if (!state.isFinalSelected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FieldLabel(stringResource(Res.string.routing_table_col_protocol))
                        KnownProtocols.forEach { protocol ->
                            val checked = protocol in draft.protocols
                            Row(
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    state.change(immediate = true) { it.copy(protocols = if (checked) it.protocols - protocol else it.protocols + protocol) }
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    state = ToggleableState(checked),
                                    onClick = { state.change(immediate = true) { it.copy(protocols = if (checked) it.protocols - protocol else it.protocols + protocol) } },
                                    modifier = Modifier.testTag("routing-protocol-$protocol"),
                                )
                                Text(protocol, fontSize = 12.5.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 4.dp))
                            }
                        }
                    }
                    if (!draft.hasConditions) {
                        Text(stringResource(Res.string.routing_table_needs_condition), fontSize = 11.5.sp, color = MiuixTheme.colorScheme.error)
                    }
                }
            }
        }

        if (!state.isComplex && !state.isFinalSelected) {
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ValuesBox(Modifier.weight(2f), stringResource(Res.string.routing_table_domains), stringResource(Res.string.routing_table_domains_help), "routing-domains", state, draft.domains) { values -> state.change { it.copy(domains = values) } }
                ValuesBox(Modifier.weight(1f), stringResource(Res.string.routing_table_ips), stringResource(Res.string.routing_table_ips_help), "routing-ips", state, draft.ips) { values -> state.change { it.copy(ips = values) } }
                ValuesBox(Modifier.weight(1f), stringResource(Res.string.routing_table_processes), stringResource(Res.string.routing_table_processes_help), "routing-processes", state, draft.processes) { values -> state.change { it.copy(processes = values) } }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun SmallAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, tagPrefix: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SkipiTheme.colors.surfaceVariant).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (on) SkipiTheme.colors.surface else Color.Transparent)
                    .clickable { onSelect(key) }
                    .testTag(tagPrefix + key.ifEmpty { "any" }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (on) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
            }
        }
    }
}

/** One value per line; commas also separate, so a list pasted from v2rayN splits as it should. */
@Composable
private fun ValuesBox(
    modifier: Modifier,
    label: String,
    help: String,
    tag: String,
    state: SkipiRoutingTableState,
    values: List<String>,
    onValues: (List<String>) -> Unit,
) {
    val text = remember(state.editorKey) { TextFieldState(values.joinToString("\n")) }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            FieldLabel(label)
            Text("  $help", fontSize = 10.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SkipiTheme.colors.surfaceVariant).padding(8.dp)) {
            BasicTextField(
                state = text,
                lineLimits = TextFieldLineLimits.MultiLine(),
                inputTransformation = InputTransformation { onValues(asCharSequence().toString().split('\n', ',').map(String::trim).filter(String::isNotEmpty)) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp, color = MiuixTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxSize().testTag(tag),
            )
        }
    }
}

/** Asks how to bring in the rules of a v2rayN export: [onMode] merges them with the rules here or replaces those. */
@Composable
fun SkipiRoutingImportDialog(
    import: V2rayRoutingImport?,
    onDismissRequest: () -> Unit,
    onMode: (V2rayRoutingImport, ClipboardImportMode) -> Unit,
) {
    val skippedNames = import?.skipped.orEmpty().take(3).map { skipped ->
        when (skipped.reason) {
            V2rayRoutingSkipReason.DnsOnly -> stringResource(Res.string.routing_table_skip_dns).formatTemplate("name" to skipped.name.ifBlank { "?" })
            V2rayRoutingSkipReason.UnknownOutbound -> stringResource(Res.string.routing_table_skip_outbound).formatTemplate("name" to skipped.name.ifBlank { "?" }, "tag" to skipped.detail)
            V2rayRoutingSkipReason.InboundTag -> stringResource(Res.string.routing_table_skip_inbound).formatTemplate("name" to skipped.name.ifBlank { "?" })
        }
    }
    val message = buildString {
        if (import != null) {
            append(stringResource(Res.string.routing_import_clipboard_message).formatTemplate("count" to import.rows.size))
            append('\n')
            append(stringResource(Res.string.routing_table_import_counts).formatTemplate("on" to import.rows.count { it.enabled }, "off" to import.rows.count { !it.enabled }))
            import.finalPolicy?.let { append('\n').append(stringResource(Res.string.routing_table_import_final).formatTemplate("policy" to it)) }
            if (import.skipped.isNotEmpty()) {
                val more = import.skipped.size - skippedNames.size
                append('\n').append(stringResource(Res.string.routing_table_import_skipped).formatTemplate("names" to skippedNames.joinToString("; ") + if (more > 0) " +$more" else ""))
            }
        }
    }
    ImportModeDialog(
        show = import != null,
        title = stringResource(Res.string.routing_import_clipboard_title),
        message = message,
        mergeText = stringResource(Res.string.common_merge_import),
        replaceText = stringResource(Res.string.common_replace_existing),
        onDismissRequest = onDismissRequest,
        onModeSelected = { mode -> import?.let { onMode(it, mode) } },
    )
}
