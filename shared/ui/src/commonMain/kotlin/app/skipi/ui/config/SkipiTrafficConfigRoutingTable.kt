// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.layout.layout
import app.skipi.ui.home.SkipiHorizontalPaneDivider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.app.config.TrafficConfigEditorController
import app.skipi.app.config.TrafficConfigEditorProfileStore
import app.skipi.app.config.TrafficConfigEditorResult
import app.skipi.ui.components.ImportModeDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_close
import app.skipi.ui.resources.common_merge_import
import app.skipi.ui.resources.common_replace_existing
import app.skipi.ui.resources.configs_rules_add
import app.skipi.ui.resources.routing_import_clipboard_message
import app.skipi.ui.resources.routing_import_clipboard_title
import app.skipi.ui.resources.routing_table_apply
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
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
    private var pending by mutableStateOf(false)
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

/**
 * The working copy of a profile behind the routing table. Edits land here, and the profile is written once by [apply],
 * because every write of a profile restarts a running tunnel.
 */
@Stable
class SkipiRoutingTableDraft internal constructor(
    initial: TrafficConfigState,
    internal var target: TrafficConfigEditorController,
) {
    /** The profile with the edits that are not applied yet. */
    var profile: TrafficConfigState by mutableStateOf(initial)
        private set
    private var applied by mutableStateOf(initial.rawConfig)

    /** Runs the operations of the table against the working copy. */
    val controller = TrafficConfigEditorController(
        TrafficConfigEditorProfileStore { profileId, transform ->
            if (profileId == profile.id) profile = transform(profile)
            profileId == profile.id
        },
    )

    val hasChanges: Boolean
        get() = profile.rawConfig != applied

    /** Writes the open edit and the working copy to the profile; does nothing when there is nothing new. */
    fun apply(state: SkipiRoutingTableState) {
        state.flush(profile.id, controller)
        if (!hasChanges) return
        val document = profile.rawConfig
        if (target.saveRuleDocument(profile.id, document) == TrafficConfigEditorResult.Saved) applied = document
    }

    /** Follows the profile; edits that are not applied yet stay on top of it. */
    internal fun sync(latest: TrafficConfigState) {
        profile = if (hasChanges) latest.copy(rawConfig = profile.rawConfig) else latest
        applied = latest.rawConfig
    }
}

@Composable
fun rememberSkipiRoutingTableDraft(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
): SkipiRoutingTableDraft {
    val draft = remember(profile.id) { SkipiRoutingTableDraft(profile, controller) }
    SideEffect { draft.target = controller }
    LaunchedEffect(draft, profile) { draft.sync(profile) }
    return draft
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
    canApply: Boolean = false,
    onApply: (() -> Unit)? = null,
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
        // The editor takes at most half of the height (in the zoomed dp), so a low window still shows several rules.
        val editorHeight = (maxHeight.value / zoom * 0.53f).coerceIn(260f, 352f).dp
        // Dragging the handle above the editor changes its height; the list keeps at least a fifth of the window.
        val editorMaxHeight = maxOf(editorHeight, (maxHeight.value / zoom * 0.8f).dp)
        val editorExtra = remember { mutableStateOf(0.dp) }
        CompositionLocalProvider(LocalDensity provides Density(density.density * zoom, density.fontScale)) {
    Column(Modifier.fillMaxSize()) {
        RoutingTableToolbar(
            state = state,
            canApply = canApply || state.hasPendingEdit(),
            onApply = onApply,
            onAdd = state::startNew,
            onImport = onImport,
        )
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
            // Read only while measuring, so a drag relayouts the editor and never recomposes the table.
            SkipiHorizontalPaneDivider(
                onDrag = { delta ->
                    editorExtra.value = (editorExtra.value - delta)
                        .coerceIn(EditorMinHeight - editorHeight, editorMaxHeight - editorHeight)
                },
                height = 8.dp,
            )
            RoutingTableEditor(
                profile, table, state, controller,
                height = { (editorHeight + editorExtra.value).coerceIn(EditorMinHeight, editorMaxHeight) },
                onPickPolicy, onEditRule,
            )
        }
    }
        }
    }
}

private val EditorMinHeight = 160.dp

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
private val RowLabelWidth = 86.dp

@Composable
private fun RoutingTableToolbar(
    state: SkipiRoutingTableState,
    canApply: Boolean,
    onApply: (() -> Unit)?,
    onAdd: () -> Unit,
    onImport: (RoutingImportSource) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ToolbarButton(stringResource(Res.string.configs_rules_add), onClick = onAdd)
        ToolbarButton(stringResource(Res.string.routing_table_import_file)) { onImport(RoutingImportSource.File) }
        ToolbarButton(stringResource(Res.string.routing_table_import_clipboard)) { onImport(RoutingImportSource.Clipboard) }
        ToolbarButton(stringResource(Res.string.routing_table_import_url)) { onImport(RoutingImportSource.Url) }
        Spacer(Modifier.weight(1f))
        if (onApply != null) {
            ToolbarButton(
                text = stringResource(Res.string.routing_table_apply),
                primary = canApply,
                enabled = canApply,
                modifier = Modifier.testTag("routing-apply"),
                onClick = onApply,
            )
            Spacer(Modifier.width(8.dp))
        }
        RoutingTableSearch(state)
    }
}

@Composable
private fun RoutingTableSearch(state: SkipiRoutingTableState) {
    Box(Modifier.width(240.dp)) {
        CompactField(
            initial = state.query,
            key = Unit,
            tag = "routing-search",
            placeholder = stringResource(Res.string.routing_table_search),
        ) { value -> state.query = value }
    }
}

/** A one-line field as low as the buttons: its label sits above it, unlike the standard field's floating label. */
@Composable
private fun CompactField(
    initial: String,
    key: Any,
    tag: String,
    placeholder: String? = null,
    onValue: (String) -> Unit,
) {
    val text = remember(key) { TextFieldState(initial) }
    Box(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(SkipiTheme.colors.surfaceVariant).padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (placeholder != null && text.text.isEmpty()) {
            Text(placeholder, fontSize = 12.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BasicTextField(
            state = text,
            lineLimits = TextFieldLineLimits.SingleLine,
            inputTransformation = InputTransformation { onValue(asCharSequence().toString()) },
            textStyle = MiuixTheme.textStyles.body2.copy(fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MiuixTheme.colorScheme.onSurface),
            modifier = Modifier.fillMaxWidth().testTag(tag),
        )
    }
}

/** A small square mark as on the table rows of the design; the row or box around it takes the click. */
@Composable
private fun CheckMark(checked: Boolean) {
    val accent = SkipiTheme.colors.accent
    val tick = SkipiTheme.colors.onAccent
    val border = MiuixTheme.colorScheme.onSurfaceVariantSummary
    Canvas(Modifier.size(15.dp)) {
        val corner = CornerRadius(3.dp.toPx())
        if (checked) {
            drawRoundRect(accent, cornerRadius = corner)
            val stroke = 1.8.dp.toPx()
            drawLine(tick, Offset(size.width * 0.24f, size.height * 0.52f), Offset(size.width * 0.43f, size.height * 0.71f), stroke, StrokeCap.Round)
            drawLine(tick, Offset(size.width * 0.43f, size.height * 0.71f), Offset(size.width * 0.77f, size.height * 0.31f), stroke, StrokeCap.Round)
        } else {
            drawRoundRect(border, cornerRadius = corner, style = Stroke(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp))
            .alpha(if (enabled) 1f else 0.45f)
            .background(if (primary) SkipiTheme.colors.accent else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
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
        Box(
            Modifier.width(CheckColumn).height(30.dp).clip(RoundedCornerShape(6.dp))
                .toggleable(value = enabled, role = Role.Checkbox) {
                    // The open rule is written from its draft, which would otherwise undo the switch.
                    if (selected && row != null) {
                        state.change(immediate = true) { draft -> draft.copy(enabled = !draft.enabled) }
                    } else {
                        controller.setRoutingRuleEnabled(profileId, entry.lineNumber, !enabled)
                    }
                }
                .testTag("routing-toggle-${entry.lineNumber}"),
            contentAlignment = Alignment.Center,
        ) {
            CheckMark(enabled)
        }
        val dim = Modifier.alpha(if (enabled) 1f else 0.45f)
        RowCell(name, NameColumn, dim, weight = FontWeight.Medium)
        Text(
            routeLabel(rule.policy),
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
            Text(routeLabel(policy), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = routeColor(policy), maxLines = 1, modifier = Modifier.width(RouteColumn))
            Spacer(Modifier.width(PortColumn + ProtocolColumn + NetworkColumn))
            Text(stringResource(Res.string.routing_table_final_hint), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, modifier = Modifier.weight(1f))
        }
    }
}

/** The three built-in routes read the same everywhere, as in the connection log; a server or group keeps its name. */
private fun routeLabel(policy: String): String = when {
    policy.equals("PROXY", ignoreCase = true) -> "proxy"
    policy.equals("DIRECT", ignoreCase = true) -> "direct"
    policy.startsWith("REJECT", ignoreCase = true) -> "block"
    else -> policy
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
    height: () -> Dp,
    onPickPolicy: (current: String) -> Unit,
    onEditRule: (ShadowrocketRule) -> Unit,
) {
    val draft = state.draft
    val index = table.entries.indexOfFirst { it.lineNumber == state.selectedLine }
    val savedRule = table.entries.getOrNull(index)?.rule
    Row(
        Modifier.fillMaxWidth()
            .layout { measurable, constraints ->
                val fixed = height().roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = fixed, maxHeight = fixed))
                layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .clip(RoundedCornerShape(12.dp)).background(SkipiTheme.colors.surface).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.width(400.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).clickable { state.close() }.testTag("routing-close"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MiuixIcons.Close, contentDescription = stringResource(Res.string.common_close), tint = MiuixTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                }
            }

            if (state.isComplex) {
                Text(stringResource(Res.string.routing_table_complex_text), fontSize = 12.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                savedRule?.let { rule ->
                    Text("${rule.type} ${rule.value}", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurface, maxLines = 6, overflow = TextOverflow.Ellipsis)
                    ToolbarButton(stringResource(Res.string.routing_table_complex_open), primary = true) { onEditRule(rule) }
                }
            } else {
                if (!state.isFinalSelected) {
                    Column(Modifier.fillMaxWidth()) {
                        FieldLabel(stringResource(Res.string.routing_table_name), Modifier.padding(bottom = 5.dp))
                        CompactField(draft.name, state.editorKey, "routing-name") { value -> state.change { it.copy(name = value) } }
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    FieldLabel(stringResource(Res.string.routing_table_send_to), Modifier.padding(bottom = 5.dp))
                    Segmented(
                        options = listOf("proxy" to "proxy", "direct" to "direct", "block" to "block"),
                        selected = when {
                            draft.policy.equals("PROXY", ignoreCase = true) -> "proxy"
                            draft.policy.equals("DIRECT", ignoreCase = true) -> "direct"
                            draft.policy.equals("REJECT", ignoreCase = true) -> "block"
                            else -> ""
                        },
                        selectedColor = routeColor(draft.policy),
                        tagPrefix = "routing-route-",
                    ) { key -> state.change(immediate = true) { it.copy(policy = when (key) { "direct" -> "DIRECT"; "block" -> "REJECT"; else -> "PROXY" }) } }
                    Row(Modifier.padding(top = 4.dp)) {
                        SmallAction(
                            text = stringResource(Res.string.routing_table_server_or_group) + draft.policy.takeUnless { it.uppercase() in setOf("PROXY", "DIRECT", "REJECT") }?.let { " ($it)" }.orEmpty(),
                        ) { onPickPolicy(draft.policy) }
                    }
                }

                if (!state.isFinalSelected) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            FieldLabel(stringResource(Res.string.routing_table_col_port), Modifier.padding(bottom = 5.dp))
                            CompactField(draft.port, state.editorKey, "routing-port", stringResource(Res.string.routing_table_port_hint)) { value -> state.change { it.copy(port = value) } }
                        }
                        Column(Modifier.weight(1.25f)) {
                            FieldLabel(stringResource(Res.string.routing_table_col_network), Modifier.padding(bottom = 5.dp))
                            Segmented(
                                options = listOf("" to stringResource(Res.string.routing_table_network_any), "tcp" to "tcp", "udp" to "udp"),
                                selected = draft.network,
                                selectedColor = routeColor(draft.policy),
                                tagPrefix = "routing-network-",
                            ) { key -> state.change(immediate = true) { it.copy(network = key) } }
                        }
                    }
                    Column(Modifier.fillMaxWidth()) {
                        FieldLabel(stringResource(Res.string.routing_table_col_protocol), Modifier.padding(bottom = 5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            KnownProtocols.forEach { protocol ->
                                val checked = protocol in draft.protocols
                                Row(
                                    Modifier.clip(RoundedCornerShape(4.dp))
                                        .toggleable(value = checked, role = Role.Checkbox) {
                                            state.change(immediate = true) { it.copy(protocols = if (checked) it.protocols - protocol else it.protocols + protocol) }
                                        }
                                        .testTag("routing-protocol-$protocol"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    CheckMark(checked)
                                    Text(protocol, fontSize = 12.5.sp, color = MiuixTheme.colorScheme.onSurface, maxLines = 1)
                                }
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
            Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ValuesBox(Modifier.weight(1f), stringResource(Res.string.routing_table_domains), stringResource(Res.string.routing_table_domains_help), "routing-domains", state, draft.domains) { values -> state.change { it.copy(domains = values) } }
                ValuesBox(Modifier.weight(1f), stringResource(Res.string.routing_table_ips), stringResource(Res.string.routing_table_ips_help), "routing-ips", state, draft.ips) { values -> state.change { it.copy(ips = values) } }
                ValuesBox(Modifier.weight(1f), stringResource(Res.string.routing_table_processes), stringResource(Res.string.routing_table_processes_help), "routing-processes", state, draft.processes) { values -> state.change { it.copy(processes = values) } }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, modifier = modifier)
}

@Composable
private fun SmallAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(6.dp)).alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Segmented(options: List<Pair<String, String>>, selected: String, selectedColor: Color, tagPrefix: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SkipiTheme.colors.surfaceVariant).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEach { (key, label) ->
            val on = key == selected
            Box(
                Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(6.dp))
                    .background(if (on) SkipiTheme.colors.background else Color.Transparent)
                    .clickable { onSelect(key) }
                    .testTag(tagPrefix + key.ifEmpty { "any" }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = if (on) selectedColor else MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp))
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
    Column(modifier.fillMaxHeight()) {
        FieldLabel(label)
        // Two lines are reserved so that the three boxes start level whichever help wraps.
        Text(help, fontSize = 10.5.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(30.dp))
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
            import.finalPolicy?.let { append('\n').append(stringResource(Res.string.routing_table_import_final).formatTemplate("policy" to routeLabel(it))) }
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
