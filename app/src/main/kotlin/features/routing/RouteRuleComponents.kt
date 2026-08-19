// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.DefaultRouteOutboundTag
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.navigation.Route
import app.navigation.RouteOutboundSelectionResult
import engine.network.isIpOrCidrAddress
import engine.network.isPortList
import engine.xray.isValidXrayExternalDomainRule
import engine.xray.isXrayExternalDomainRuleCandidate
import features.routing.model.RouteRule
import features.routing.ui.GeoAssetPickerDialog
import features.routing.ui.ProcessAppPickerDialog
import features.routing.ui.SuggestionChipsRow
import features.routing.usecase.RoutingSuggestionsProvider
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import ui.AppTheme
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import ui.components.BackNavigationIcon
import ui.components.NavigationIcon
import ui.components.StringListEditor
import ui.components.StringListStatusText
import ui.components.draggedCardShadow
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import utils.toTrimmedNonEmptyDistinctList

internal data class RouteRuleOutboundOption(
    val tag: String,
    val label: String,
)

@Composable
internal fun RoutingPolicyCard(
    domainStrategyOptions: List<String>,
    selectedDomainStrategy: Int,
    onDomainStrategyChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = AppTheme.colors.surface,
            contentColor = AppTheme.colors.onSurface,
        ),
    ) {
        OverlayDropdownPreference(
            title = stringResource(R.string.routing_domain_strategy),
            items = domainStrategyOptions,
            selectedIndex = selectedDomainStrategy,
            onSelectedIndexChange = onDomainStrategyChange,
        )
    }
}

@Composable
internal fun RouteRuleCard(
    rule: RouteRule,
    outboundLabel: String,
    onToggle: (Boolean) -> Unit,
    isDragging: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.025f else 1f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "routeRuleDragScale",
    )
    val animatedShadowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = folmeSpring(damping = 0.9f, response = 0.38f),
        label = "routeRuleDragShadowAlpha",
    )
    val shadowColor = AppTheme.colors.onSurface.copy(alpha = 0.20f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 8.dp)
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .draggedCardShadow(
                alpha = animatedShadowAlpha,
                color = shadowColor,
            )
            .then(dragModifier),
        colors = CardDefaults.defaultColors(
            color = AppTheme.colors.surface,
            contentColor = AppTheme.colors.onSurface,
        ),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.remarks,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${stringResource(R.string.routing_outbound_tag_label)}: $outboundLabel",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                )
            }
            Spacer(Modifier.height(4.dp))
            RouteRuleLine(label = stringResource(R.string.routing_domain_label), values = rule.domain)
            RouteRuleLine(label = stringResource(R.string.routing_ip_label), values = rule.ip)
            RouteRuleLine(label = stringResource(R.string.routing_process_label), values = rule.process)
            RouteRuleLine(label = stringResource(R.string.routing_port_label), value = rule.port)
            RouteRuleLine(label = stringResource(R.string.routing_protocol_label), value = rule.protocol)
            RouteRuleLine(label = stringResource(R.string.routing_network_label), value = rule.network)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Edit,
                        contentDescription = stringResource(R.string.routing_edit),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = MiuixIcons.Delete,
                        contentDescription = stringResource(R.string.routing_delete),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * A route is edited on its own destination so opening the full-screen
 * Send-through selector cannot dismiss or recreate the unfinished draft.
 */
@Composable
fun RouteRuleEditorPage(
    padding: PaddingValues,
    ruleId: Int?,
) {
    val appState by LocalAppStateStore.current.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val navigator = LocalNavigator.current
    val isWideScreen = LocalIsWideScreen.current
    val initialRule = ruleId?.let { id -> appState.routeRules.firstOrNull { rule -> rule.id == id } }
    if (ruleId != null && initialRule == null) {
        navigator.pop()
        return
    }
    val newRuleName = stringResource(R.string.routing_new_rule)
    val unnamedRuleName = stringResource(R.string.routing_unnamed_rule)
    val editorIdentity = ruleId ?: appState.nextRouteRuleId
    var remarks by rememberSaveable(editorIdentity, newRuleName) {
        mutableStateOf(initialRule?.remarks ?: newRuleName)
    }
    var domains by rememberSaveable(editorIdentity) {
        mutableStateOf(initialRule?.domain ?: listOf("geosite:category"))
    }
    var ips by rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.ip ?: emptyList()) }
    var ports by rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.port ?: "") }
    var process by rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.process ?: emptyList()) }
    var protocol by rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.protocol ?: "") }
    var network by rememberSaveable(editorIdentity) { mutableStateOf(initialRule?.network ?: "") }
    var selectedOutboundTag by rememberSaveable(editorIdentity) {
        mutableStateOf(initialRule?.outboundTag?.takeIf(String::isNotBlank) ?: DefaultRouteOutboundTag)
    }
    val outboundSelectorResultKey = remember(editorIdentity) { "route-rule-outbound-editor-$editorIdentity" }
    LaunchedEffect(navigator, outboundSelectorResultKey) {
        navigator.observeResult<RouteOutboundSelectionResult>(outboundSelectorResultKey).collect { result ->
            selectedOutboundTag = result.tag
            navigator.clearResult(outboundSelectorResultKey)
        }
    }
    val canSave = isPortList(ports) && isRouteNetworkList(network)
    fun save() {
        if (!canSave) return
        val savedRule = RouteRule(
            id = initialRule?.id ?: appState.nextRouteRuleId,
            remarks = remarks.ifBlank { unnamedRuleName },
            outboundTag = selectedOutboundTag,
            domain = domains.toTrimmedNonEmptyDistinctList(),
            ip = ips.toTrimmedNonEmptyDistinctList(),
            port = ports.trim(),
            process = process.toTrimmedNonEmptyDistinctList(),
            protocol = protocol.trim(),
            network = network.trim(),
            enabled = initialRule?.enabled ?: true,
        )
        updateAppState { state ->
            val exists = state.routeRules.any { rule -> rule.id == savedRule.id }
            state.copy(
                routeRules = if (exists) {
                    state.routeRules.map { rule -> if (rule.id == savedRule.id) savedRule else rule }
                } else {
                    state.routeRules + savedRule
                },
                nextRouteRuleId = if (exists) state.nextRouteRuleId else maxOf(state.nextRouteRuleId, savedRule.id + 1),
            )
        }
        navigator.pop()
    }

    Scaffold(
        topBar = {
            AdaptiveTopAppBar(
                title = stringResource(if (initialRule == null) R.string.routing_add_rule else R.string.routing_edit_rule),
                isWideScreen = isWideScreen,
                scrollBehavior = MiuixScrollBehavior(),
                navigationIcon = { BackNavigationIcon(onClick = navigator::pop) },
                actions = {
                    NavigationIcon(
                        onClick = ::save,
                        imageVector = MiuixIcons.Ok,
                        contentDescription = stringResource(R.string.common_save),
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = pageListPadding(pageContentPaddingWithCutout(innerPadding, padding, isWideScreen)),
        ) {
            item {
                RouteRuleEditorContent(
                    editorKey = "fullscreen:$editorIdentity",
                    remarks = remarks,
                    domains = domains,
                    ips = ips,
                    process = process,
                    ports = ports,
                    protocol = protocol,
                    network = network,
                    selectedOutboundLabel = selectedOutboundTag,
                    onRemarksChange = { remarks = it },
                    onDomainsChange = { domains = it },
                    onIpsChange = { ips = it },
                    onProcessChange = { process = it },
                    onPortsChange = { ports = it },
                    onProtocolChange = { protocol = it },
                    onNetworkChange = { network = it },
                    onOpenOutboundSelector = {
                        navigator.navigateForResult(
                            route = Route.RouteOutboundSelector(
                                selectedTag = selectedOutboundTag,
                                resultKey = outboundSelectorResultKey,
                            ),
                            requestKey = outboundSelectorResultKey,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun RouteRuleEditorBottomSheet(
    show: Boolean,
    initialRule: RouteRule?,
    nextRuleId: Int,
    outboundOptions: List<RouteRuleOutboundOption>,
    onDismissRequest: () -> Unit,
    onSave: (RouteRule) -> Unit,
) {
    val navigator = LocalNavigator.current
    val newRuleName = stringResource(R.string.routing_new_rule)
    val unnamedRuleName = stringResource(R.string.routing_unnamed_rule)
    var remarks by rememberSaveable(initialRule?.id, show, newRuleName) {
        mutableStateOf(initialRule?.remarks ?: newRuleName)
    }
    var domains by rememberSaveable(initialRule?.id, show) {
        mutableStateOf(initialRule?.domain ?: listOf("geosite:category"))
    }
    var ips by rememberSaveable(initialRule?.id, show) { mutableStateOf(initialRule?.ip ?: emptyList()) }
    var ports by rememberSaveable(initialRule?.id, show) { mutableStateOf(initialRule?.port ?: "") }
    var process by rememberSaveable(initialRule?.id, show) { mutableStateOf(initialRule?.process ?: emptyList()) }
    var protocol by rememberSaveable(initialRule?.id, show) { mutableStateOf(initialRule?.protocol ?: "") }
    var network by rememberSaveable(initialRule?.id, show) { mutableStateOf(initialRule?.network ?: "") }
    val canSave = isPortList(ports) && isRouteNetworkList(network)
    val currentOutbound = initialRule?.outboundTag?.takeIf { it.isNotBlank() }
    val effectiveOutboundOptions = remember(outboundOptions, currentOutbound) {
        if (currentOutbound != null && outboundOptions.none { it.tag == currentOutbound }) {
            outboundOptions + RouteRuleOutboundOption(tag = currentOutbound, label = currentOutbound)
        } else {
            outboundOptions
        }
    }
    var selectedOutboundTag by rememberSaveable(initialRule?.id, show) {
        mutableStateOf(currentOutbound ?: DefaultRouteOutboundTag)
    }
    val outboundSelectorResultKey = remember(initialRule?.id, nextRuleId) {
        "route-rule-outbound-${initialRule?.id ?: nextRuleId}"
    }
    LaunchedEffect(navigator, outboundSelectorResultKey) {
        navigator.observeResult<RouteOutboundSelectionResult>(outboundSelectorResultKey).collect { result ->
            selectedOutboundTag = result.tag
            navigator.clearResult(outboundSelectorResultKey)
        }
    }
    val selectedOutbound = effectiveOutboundOptions.firstOrNull { option ->
        option.tag == selectedOutboundTag
    } ?: effectiveOutboundOptions.first()
    val saveRule = {
        if (canSave) {
            onSave(
                RouteRule(
                    id = initialRule?.id ?: nextRuleId,
                    remarks = remarks.ifBlank { unnamedRuleName },
                    outboundTag = selectedOutbound.tag,
                    domain = domains.toTrimmedNonEmptyDistinctList(),
                    ip = ips.toTrimmedNonEmptyDistinctList(),
                    port = ports.trim(),
                    process = process.toTrimmedNonEmptyDistinctList(),
                    protocol = protocol.trim(),
                    network = network.trim(),
                    enabled = initialRule?.enabled ?: true,
                ),
            )
        }
    }

    WindowBottomSheet(
        show = show,
        title = if (initialRule == null) {
            stringResource(R.string.routing_add_rule)
        } else {
            stringResource(R.string.routing_edit_rule)
        },
        startAction = {
            TextButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismissRequest,
            )
        },
        endAction = {
            TextButton(
                text = stringResource(R.string.common_save),
                onClick = saveRule,
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            item {
                RouteRuleEditorContent(
                    editorKey = "${initialRule?.id ?: nextRuleId}:$show",
                    remarks = remarks,
                    domains = domains,
                    ips = ips,
                    process = process,
                    ports = ports,
                    protocol = protocol,
                    network = network,
                    selectedOutboundLabel = selectedOutbound.label,
                    onRemarksChange = { remarks = it },
                    onDomainsChange = { domains = it },
                    onIpsChange = { ips = it },
                    onProcessChange = { process = it },
                    onPortsChange = { ports = it },
                    onProtocolChange = { protocol = it },
                    onNetworkChange = { network = it },
                    onOpenOutboundSelector = {
                        navigator.navigateForResult(
                            route = Route.RouteOutboundSelector(
                                selectedTag = selectedOutbound.tag,
                                resultKey = outboundSelectorResultKey,
                            ),
                            requestKey = outboundSelectorResultKey,
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun RoutingEmptyCard(
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(
            color = AppTheme.colors.surface,
            contentColor = AppTheme.colors.onSurface,
        ),
        insideMargin = PaddingValues(18.dp),
    ) {
        Text(
            text = stringResource(R.string.routing_empty),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun RouteRuleLine(
    label: String,
    values: List<String>,
) {
    RouteRuleLine(label, values.toTrimmedNonEmptyDistinctList().joinToString(", "))
}

@Composable
private fun RouteRuleLine(
    label: String,
    value: String,
) {
    if (value.isBlank()) return
    Text(
        text = "$label: $value",
        fontSize = 12.sp,
        lineHeight = 15.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 2.dp),
    )
}

@Composable
private fun RouteRuleEditorContent(
    editorKey: String,
    remarks: String,
    domains: List<String>,
    ips: List<String>,
    process: List<String>,
    ports: String,
    protocol: String,
    network: String,
    selectedOutboundLabel: String,
    onRemarksChange: (String) -> Unit,
    onDomainsChange: (List<String>) -> Unit,
    onIpsChange: (List<String>) -> Unit,
    onProcessChange: (List<String>) -> Unit,
    onPortsChange: (String) -> Unit,
    onProtocolChange: (String) -> Unit,
    onNetworkChange: (String) -> Unit,
    onOpenOutboundSelector: () -> Unit,
) {
    val context = LocalContext.current
    val appState by LocalAppStateStore.current.collectAppState()
    var showGeoSitePicker by rememberSaveable(editorKey) { mutableStateOf(false) }
    var showGeoIpPicker by rememberSaveable(editorKey) { mutableStateOf(false) }
    var showAppPicker by rememberSaveable(editorKey) { mutableStateOf(false) }

    val geoSiteSuggestions = remember(appState) {
        RoutingSuggestionsProvider.resolveGeoSiteSuggestions(context, appState)
    }
    val geoIpSuggestions = remember(appState) {
        RoutingSuggestionsProvider.resolveGeoIpSuggestions(context, appState)
    }

    val emptyMessage = stringResource(R.string.routing_list_empty)
    val domainInvalidMessage = stringResource(R.string.routing_domain_invalid)
    val ipInvalidMessage = stringResource(R.string.routing_ip_invalid)
    val processInvalidMessage = stringResource(R.string.routing_process_invalid)
    val portInvalidMessage = stringResource(R.string.routing_port_invalid)
    val networkInvalidMessage = stringResource(R.string.routing_network_invalid)
    val portError = if (ports.isBlank() || isPortList(ports)) null else portInvalidMessage
    val networkError = if (network.isBlank() || isRouteNetworkList(network)) null else networkInvalidMessage

    val networkDisplayOptions = listOf(
        stringResource(R.string.routing_network_any),
        "tcp",
        "udp",
        "tcp,udp",
    )
    val currentNetworkIndex = remember(network) {
        when (network.trim().lowercase()) {
            "tcp" -> 1
            "udp" -> 2
            "tcp,udp", "udp,tcp" -> 3
            else -> 0
        }
    }

    key(editorKey) {
    Column {
        TextField(
            state = rememberTextFieldState(initialText = remarks),
            inputTransformation = InputTransformation {
                onRemarksChange(asCharSequence().toString())
            },
            label = stringResource(R.string.routing_rule_name),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )
        StringListEditor(
            editorKey = editorKey,
            title = stringResource(R.string.routing_domain_label),
            values = domains,
            onValuesChange = onDomainsChange,
            emptyText = emptyMessage,
            validateInput = { routeDomainInputError(it, domainInvalidMessage) },
            suggestionContent = { applySuggestion ->
                SuggestionChipsRow(
                    chips = RoutingSuggestionsProvider.DomainPrefixes,
                    onChipClick = { prefix -> applySuggestion(prefix, false) },
                    actionButtonText = stringResource(R.string.routing_suggestions_geosite),
                    onActionClick = { showGeoSitePicker = true },
                )
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        StringListEditor(
            editorKey = editorKey,
            title = stringResource(R.string.routing_ip_label),
            values = ips,
            onValuesChange = onIpsChange,
            emptyText = emptyMessage,
            validateInput = { routeIpInputError(it, ipInvalidMessage) },
            suggestionContent = { applySuggestion ->
                SuggestionChipsRow(
                    chips = listOf("geoip:") + RoutingSuggestionsProvider.PrivateIpPresets,
                    onChipClick = { chip ->
                        if (chip == "geoip:") applySuggestion(chip, false) else applySuggestion(chip, true)
                    },
                    actionButtonText = stringResource(R.string.routing_suggestions_geoip),
                    onActionClick = { showGeoIpPicker = true },
                )
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        StringListEditor(
            editorKey = editorKey,
            title = stringResource(R.string.routing_process_label),
            values = process,
            onValuesChange = onProcessChange,
            emptyText = emptyMessage,
            validateInput = { routeProcessInputError(it, processInvalidMessage) },
            suggestionContent = {
                SuggestionChipsRow(
                    chips = emptyList(),
                    onChipClick = {},
                    actionButtonText = stringResource(R.string.routing_suggestions_apps),
                    onActionClick = { showAppPicker = true },
                )
            },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        TextField(
            state = rememberTextFieldState(initialText = ports),
            inputTransformation = InputTransformation {
                onPortsChange(asCharSequence().toString())
            },
            label = stringResource(R.string.routing_port_label),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
        SuggestionChipsRow(
            chips = RoutingSuggestionsProvider.PortPresets,
            onChipClick = { portChip ->
                val currentPorts = ports.split(",").map(String::trim).filter(String::isNotBlank)
                val newPort = if (ports.isBlank()) {
                    portChip
                } else if (currentPorts.contains(portChip)) {
                    ports
                } else {
                    "$ports,$portChip"
                }
                onPortsChange(newPort)
            },
            modifier = Modifier.padding(bottom = if (portError == null) 12.dp else 4.dp),
        )
        portError?.let {
            StringListStatusText(
                text = it,
                error = true,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        TextField(
            state = rememberTextFieldState(initialText = protocol),
            inputTransformation = InputTransformation {
                onProtocolChange(asCharSequence().toString())
            },
            label = stringResource(R.string.routing_protocol_label),
            lineLimits = TextFieldLineLimits.SingleLine,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
        SuggestionChipsRow(
            chips = RoutingSuggestionsProvider.ProtocolOptions,
            onChipClick = { protoChip ->
                val currentList = protocol.split(",").map(String::trim).filter(String::isNotBlank)
                val nextList = if (currentList.contains(protoChip)) {
                    currentList.filter { it != protoChip }
                } else {
                    currentList + protoChip
                }
                onProtocolChange(nextList.joinToString(","))
            },
            selectedChip = protocol.takeIf { it in RoutingSuggestionsProvider.ProtocolOptions },
            modifier = Modifier.padding(bottom = 12.dp),
        )
        WindowDropdownPreference(
            title = stringResource(R.string.routing_network_label),
            items = networkDisplayOptions,
            selectedIndex = currentNetworkIndex,
            onSelectedIndexChange = { index ->
                val newNetwork = when (index) {
                    1 -> "tcp"
                    2 -> "udp"
                    3 -> "tcp,udp"
                    else -> ""
                }
                onNetworkChange(newNetwork)
            },
            modifier = Modifier.padding(bottom = if (networkError == null) 12.dp else 4.dp),
        )
        networkError?.let {
            StringListStatusText(
                text = it,
                error = true,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        ArrowPreference(
            title = stringResource(R.string.routing_outbound_tag_label),
            summary = selectedOutboundLabel,
            onClick = onOpenOutboundSelector,
        )
    }

    GeoAssetPickerDialog(
        show = showGeoSitePicker,
        title = stringResource(R.string.routing_suggestions_geosite),
        items = geoSiteSuggestions,
        onSelect = { item ->
            onDomainsChange((domains + item.fullRule).toTrimmedNonEmptyDistinctList())
        },
        onDismissRequest = { showGeoSitePicker = false },
    )

    GeoAssetPickerDialog(
        show = showGeoIpPicker,
        title = stringResource(R.string.routing_suggestions_geoip),
        items = geoIpSuggestions,
        onSelect = { item ->
            onIpsChange((ips + item.fullRule).toTrimmedNonEmptyDistinctList())
        },
        onDismissRequest = { showGeoIpPicker = false },
    )

    ProcessAppPickerDialog(
        show = showAppPicker,
        onSelect = { pkg ->
            onProcessChange((process + pkg).toTrimmedNonEmptyDistinctList())
        },
        onDismissRequest = { showAppPicker = false },
    )
    }
}

private fun routeDomainInputError(input: String, invalidMessage: String): String? {
    if (input.any(Char::isWhitespace)) return invalidMessage
    if (input.startsWith("regexp:", ignoreCase = true)) {
        return if (input.substringAfter(":").isBlank()) invalidMessage else null
    }
    if (isXrayExternalDomainRuleCandidate(input)) {
        return if (isValidXrayExternalDomainRule(input)) null else invalidMessage
    }

    val supportedPrefix = input.substringBefore(":", missingDelimiterValue = "")
        .lowercase()
        .takeIf { it in setOf("domain", "full", "keyword", "geosite") }
    if (supportedPrefix != null) {
        return if (input.substringAfter(":").isBlank()) invalidMessage else null
    }

    return if (input.contains("://") || input.contains("/")) invalidMessage else null
}

private fun routeIpInputError(input: String, invalidMessage: String): String? {
    val lowerInput = input.lowercase()
    if (lowerInput.startsWith("geoip:") || lowerInput.startsWith("ext:")) {
        return if (input.substringAfter(":").isBlank()) invalidMessage else null
    }
    return if (isIpOrCidrAddress(input)) null else invalidMessage
}

private fun routeProcessInputError(input: String, invalidMessage: String): String? {
    return if (input.any(Char::isWhitespace)) invalidMessage else null
}

private fun isRouteNetworkList(value: String): Boolean {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return true

    val allowedNetworks = setOf("tcp", "udp")
    return trimmed.split(",")
        .map { it.trim().lowercase() }
        .all { it in allowedNetworks }
}
